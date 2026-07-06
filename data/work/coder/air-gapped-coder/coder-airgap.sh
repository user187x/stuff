#!/usr/bin/env bash
###############################################################################
# coder-airgap.sh — Zero-to-hero air-gapped Coder installer for Kubernetes
#
# Guidance sources:
#   https://coder.com/docs/install/airgap     (air-gap image, .tfrc, provider
#                                              mirror, telemetry/STUN/update
#                                              hardening, external Postgres)
#   https://github.com/coder/envbox           (docker-in-workspace via sysbox)
#
# USAGE
# =====
#  Phase 1 — INTERNET-CONNECTED machine (needs: docker, curl, tar, unzip):
#      ./coder-airgap.sh download
#    -> produces ./coder-airgap-bundle/  (charts, CRDs, images, providers,
#       .tfrc, CLI binaries, templates, this script — everything).
#
#  Copy the whole bundle directory to the air-gapped bastion (a Linux host
#  with a docker daemon and kubectl access to the target cluster), then:
#
#  Phase 2 — AIR-GAPPED bastion:
#      REGISTRY=registry.internal:5000 ./coder-airgap-bundle/coder-airgap.sh install
#    or spin up a throwaway registry on the bastion itself:
#      USE_BUNDLED_REGISTRY=true ./coder-airgap-bundle/coder-airgap.sh install
#
# WHAT GETS INSTALLED
# ===================
#   * Coder control plane — custom air-gap image: Terraform provider
#     filesystem mirror + .tfrc baked in; telemetry, update checks and STUN
#     disabled; direct connections blocked (DERP relay only).
#   * CloudNativePG operator (chart bundles its CRDs) + a PostgreSQL cluster,
#     wired to Coder via CODER_PG_CONNECTION_URL (external DB, per docs).
#   * envbox image so workspaces can run Docker (sysbox; outer container is
#     privileged, inner workspace is not).
#   * Two ready-to-go "Kubernetes (Deployment)" templates, pushed + working:
#       1. ubuntu-base  : terminal (native) + VS Code Desktop + code-server
#                         + File Browser. `docker run` works inside.
#       2. ubuntu-novnc : terminal (native) + File Browser + noVNC (XFCE
#                         desktop in the browser). `docker run` works inside.
#     All template modules are LOCAL (vendored) — nothing is fetched from
#     registry.coder.com; code-server/filebrowser/noVNC binaries are baked
#     into the workspace images at download time.
#
# All versions pinned below — override with env vars.
###############################################################################
set -euo pipefail

# ------------------------------- versions -----------------------------------
CODER_VERSION="${CODER_VERSION:-2.34.5}"
CODER_CHART_VERSION="${CODER_CHART_VERSION:-$CODER_VERSION}"
ENVBOX_VERSION="${ENVBOX_VERSION:-0.6.5}"
CNPG_CHART_VERSION="${CNPG_CHART_VERSION:-0.26.1}"
CNPG_OPERATOR_VERSION="${CNPG_OPERATOR_VERSION:-1.27.1}"
CNPG_PG_IMAGE_TAG="${CNPG_PG_IMAGE_TAG:-16.6}"
TERRAFORM_VERSION="${TERRAFORM_VERSION:-1.11.4}"   # keep within Coder-supported range
HELM_VERSION="${HELM_VERSION:-3.16.4}"
KUBECTL_VERSION="${KUBECTL_VERSION:-1.31.4}"
TF_CODER_PROVIDER="${TF_CODER_PROVIDER:-2.5.3}"
TF_K8S_PROVIDER="${TF_K8S_PROVIDER:-2.38.0}"
UBUNTU_BASE_TAG="${UBUNTU_BASE_TAG:-24.04}"

# --------------------------- install-phase knobs ----------------------------
REGISTRY="${REGISTRY:-}"                        # e.g. registry.internal:5000
USE_BUNDLED_REGISTRY="${USE_BUNDLED_REGISTRY:-false}"
BUNDLED_REGISTRY_PORT="${BUNDLED_REGISTRY_PORT:-5000}"
BASTION_IP="${BASTION_IP:-}"                    # auto-detected if empty
CODER_NAMESPACE="${CODER_NAMESPACE:-coder}"
CNPG_NAMESPACE="${CNPG_NAMESPACE:-cnpg-system}"
CODER_ACCESS_URL="${CODER_ACCESS_URL:-}"        # default: http://coder.<ns>.svc.cluster.local
CODER_SERVICE_TYPE="${CODER_SERVICE_TYPE:-NodePort}"
PG_STORAGE_SIZE="${PG_STORAGE_SIZE:-10Gi}"
PG_STORAGE_CLASS="${PG_STORAGE_CLASS:-}"        # empty = cluster default
HOME_STORAGE_CLASS="${HOME_STORAGE_CLASS:-}"    # StorageClass for /home PVCs
ADMIN_EMAIL="${ADMIN_EMAIL:-admin@airgap.local}"
ADMIN_USERNAME="${ADMIN_USERNAME:-admin}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-}"            # auto-generated if empty

# --------------------------------- layout -----------------------------------
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCRIPT_NAME="$(basename "${BASH_SOURCE[0]}")"
BUNDLE="${BUNDLE:-$SCRIPT_DIR/coder-airgap-bundle}"
[[ -f "$SCRIPT_DIR/.bundle-manifest" ]] && BUNDLE="$SCRIPT_DIR"   # running inside bundle

log()  { printf '\033[1;32m==>\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m[!]\033[0m %s\n' "$*" >&2; }
die()  { printf '\033[1;31m[x]\033[0m %s\n' "$*" >&2; exit 1; }
need() { command -v "$1" >/dev/null 2>&1 || die "'$1' is required but not found in PATH"; }

###############################################################################
# PHASE 1 — DOWNLOAD  (internet-connected machine)
###############################################################################
cmd_download() {
  need docker; need curl; need tar; need unzip
  log "Creating bundle at $BUNDLE"
  mkdir -p "$BUNDLE"/{images,charts,bin,tf-providers,config,templates,build,manifests}

  # ---------------------------------------------------------------- binaries
  log "Downloading CLI binaries (coder, terraform, helm, kubectl)"
  curl -fL --retry 3 -o "$BUNDLE/bin/coder.tar.gz" \
    "https://github.com/coder/coder/releases/download/v${CODER_VERSION}/coder_${CODER_VERSION}_linux_amd64.tar.gz"
  # Sanity-check we actually got a gzip archive (not an HTML error page)
  file "$BUNDLE/bin/coder.tar.gz" 2>/dev/null | grep -qi gzip \
    || gzip -t "$BUNDLE/bin/coder.tar.gz" \
    || die "Downloaded coder.tar.gz is not a valid gzip archive — check CODER_VERSION=${CODER_VERSION} exists at github.com/coder/coder/releases"
  # Extract everything, then locate the binary — member paths vary between
  # releases ('coder' vs './coder' vs nested), so never name the member.
  local coder_tmp; coder_tmp="$(mktemp -d)"
  tar -xzf "$BUNDLE/bin/coder.tar.gz" -C "$coder_tmp"
  local coder_bin_path
  coder_bin_path="$(find "$coder_tmp" -type f -name coder | head -n1)"
  [[ -n "$coder_bin_path" ]] || die "Could not find 'coder' binary inside the release tarball (contents: $(cd "$coder_tmp" && find . -maxdepth 2 | tr '\n' ' '))"
  install -m 0755 "$coder_bin_path" "$BUNDLE/bin/coder"
  rm -rf "$coder_tmp" "$BUNDLE/bin/coder.tar.gz"

  curl -fL --retry 3 -o /tmp/tf.zip \
    "https://releases.hashicorp.com/terraform/${TERRAFORM_VERSION}/terraform_${TERRAFORM_VERSION}_linux_amd64.zip"
  unzip -oq /tmp/tf.zip terraform -d "$BUNDLE/bin" && rm -f /tmp/tf.zip

  curl -fL --retry 3 "https://get.helm.sh/helm-v${HELM_VERSION}-linux-amd64.tar.gz" \
    | tar -xz -C "$BUNDLE/bin" --strip-components=1 linux-amd64/helm
  curl -fL --retry 3 -o "$BUNDLE/bin/kubectl" \
    "https://dl.k8s.io/release/v${KUBECTL_VERSION}/bin/linux/amd64/kubectl"
  chmod +x "$BUNDLE"/bin/*
  "$BUNDLE/bin/coder" version >/dev/null 2>&1 || warn "coder binary won't run on THIS host (fine if you're only bundling), continuing"

  # ------------------------------------------------------------- helm charts
  log "Pulling Helm charts (coder, cloudnative-pg — CNPG chart bundles its CRDs)"
  "$BUNDLE/bin/helm" repo add coder-v2 https://helm.coder.com/v2 --force-update >/dev/null
  "$BUNDLE/bin/helm" repo add cnpg https://cloudnative-pg.github.io/charts --force-update >/dev/null
  "$BUNDLE/bin/helm" repo update >/dev/null
  "$BUNDLE/bin/helm" pull coder-v2/coder --version "$CODER_CHART_VERSION" -d "$BUNDLE/charts"
  "$BUNDLE/bin/helm" pull cnpg/cloudnative-pg --version "$CNPG_CHART_VERSION" -d "$BUNDLE/charts"

  # ------------------------------------------- terraform provider mirror
  log "Building Terraform provider filesystem mirror (coder/coder, hashicorp/kubernetes)"
  local mirror_tmp; mirror_tmp="$(mktemp -d)"
  cat > "$mirror_tmp/main.tf" <<EOF
terraform {
  required_providers {
    coder = {
      source  = "coder/coder"
      version = "${TF_CODER_PROVIDER}"
    }
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "${TF_K8S_PROVIDER}"
    }
  }
}
EOF
  (cd "$mirror_tmp" && "$BUNDLE/bin/terraform" providers mirror -platform=linux_amd64 "$BUNDLE/tf-providers")
  rm -rf "$mirror_tmp"

  # ----------------------------------------------------------------- .tfrc
  log "Writing Terraform CLI config (.tfrc — filesystem mirror, per Coder air-gap docs)"
  cat > "$BUNDLE/config/terraformrc" <<'EOF'
# Air-gapped Terraform CLI config: providers come from the local mirror only.
provider_installation {
  filesystem_mirror {
    path    = "/opt/terraform/plugins"
    include = ["*/*"]
  }
  direct {
    exclude = ["*/*"]
  }
}
EOF

  # ---------------------------------------- custom air-gapped Coder image
  log "Building custom air-gapped Coder server image (providers + .tfrc baked in)"
  cat > "$BUNDLE/build/Dockerfile.coder" <<EOF
# Per https://coder.com/docs/install/airgap — extend the official image with a
# Terraform provider filesystem mirror and CLI config. Terraform itself is
# already included at a supported version in the official image.
FROM ghcr.io/coder/coder:v${CODER_VERSION}
USER root
RUN mkdir -p /opt/terraform/plugins
COPY tf-providers/ /opt/terraform/plugins/
COPY config/terraformrc /opt/terraform/terraformrc
RUN chown -R coder:coder /opt/terraform
USER coder
ENV TF_CLI_CONFIG_FILE=/opt/terraform/terraformrc
EOF
  docker build -f "$BUNDLE/build/Dockerfile.coder" -t "coder-airgap:v${CODER_VERSION}" "$BUNDLE"

  # ----------------------------------------------- workspace inner images
  log "Building workspace image: airgap/ubuntu-base (Docker + code-server + filebrowser baked in)"
  cat > "$BUNDLE/build/Dockerfile.ubuntu-base" <<EOF
FROM ubuntu:${UBUNTU_BASE_TAG}
ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get update && apt-get install -y --no-install-recommends \\
      ca-certificates curl gnupg git sudo bash vim nano htop jq unzip \\
      iproute2 iputils-ping openssh-client locales tzdata && \\
    install -m 0755 -d /etc/apt/keyrings && \\
    curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc && \\
    echo "deb [arch=amd64 signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu \\
      \$(. /etc/os-release && echo \$VERSION_CODENAME) stable" > /etc/apt/sources.list.d/docker.list && \\
    apt-get update && apt-get install -y --no-install-recommends \\
      docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin && \\
    rm -rf /var/lib/apt/lists/* && locale-gen en_US.UTF-8
# Bake code-server (standalone) and filebrowser so workspaces never need internet
RUN curl -fsSL https://code-server.dev/install.sh | sh -s -- --method=standalone --prefix=/usr/local && \\
    curl -fsSL https://raw.githubusercontent.com/filebrowser/get/master/get.sh | bash
# 'coder' user (uid 1000) with passwordless sudo + docker group
RUN userdel -r ubuntu 2>/dev/null || true; \\
    useradd -m -s /bin/bash -u 1000 coder && \\
    usermod -aG docker coder && \\
    echo "coder ALL=(ALL) NOPASSWD:ALL" > /etc/sudoers.d/coder && chmod 0440 /etc/sudoers.d/coder
USER coder
WORKDIR /home/coder
EOF
  docker build -f "$BUNDLE/build/Dockerfile.ubuntu-base" -t airgap/ubuntu-base:latest "$BUNDLE/build"

  log "Building workspace image: airgap/ubuntu-novnc (XFCE + TigerVNC + noVNC baked in)"
  cat > "$BUNDLE/build/Dockerfile.ubuntu-novnc" <<'EOF'
FROM airgap/ubuntu-base:latest
USER root
ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get update && apt-get install -y --no-install-recommends \
      xfce4 xfce4-terminal dbus-x11 x11-xserver-utils xterm \
      tigervnc-standalone-server tigervnc-common novnc websockify && \
    rm -rf /var/lib/apt/lists/*
RUN mkdir -p /home/coder/.vnc && \
    printf '#!/bin/sh\nunset SESSION_MANAGER\nunset DBUS_SESSION_BUS_ADDRESS\nexec startxfce4\n' \
      > /home/coder/.vnc/xstartup && \
    chmod +x /home/coder/.vnc/xstartup && chown -R coder:coder /home/coder/.vnc
USER coder
WORKDIR /home/coder
EOF
  docker build -f "$BUNDLE/build/Dockerfile.ubuntu-novnc" -t airgap/ubuntu-novnc:latest "$BUNDLE/build"

  # ---------------------------------------------------------- pull images
  log "Pulling remaining container images (envbox, CNPG operator, PostgreSQL, registry)"
  local pull_images=(
    "ghcr.io/coder/envbox:${ENVBOX_VERSION}"
    "ghcr.io/cloudnative-pg/cloudnative-pg:${CNPG_OPERATOR_VERSION}"
    "ghcr.io/cloudnative-pg/postgresql:${CNPG_PG_IMAGE_TAG}"
    "registry:2"
  )
  local img
  for img in "${pull_images[@]}"; do docker pull "$img"; done

  # ---------------------------------------------------------- save images
  log "Saving all images to images/images.tar (this can take a while)"
  docker save -o "$BUNDLE/images/images.tar" \
    "coder-airgap:v${CODER_VERSION}" \
    airgap/ubuntu-base:latest \
    airgap/ubuntu-novnc:latest \
    "${pull_images[@]}"

  # ---------------------------------------------------- workspace templates
  log "Generating workspace templates (Kubernetes Deployment + envbox, local modules)"
  write_templates

  # ------------------------------------------------------------- manifest
  cat > "$BUNDLE/.bundle-manifest" <<EOF
CODER_VERSION=${CODER_VERSION}
CODER_CHART_VERSION=${CODER_CHART_VERSION}
ENVBOX_VERSION=${ENVBOX_VERSION}
CNPG_CHART_VERSION=${CNPG_CHART_VERSION}
CNPG_OPERATOR_VERSION=${CNPG_OPERATOR_VERSION}
CNPG_PG_IMAGE_TAG=${CNPG_PG_IMAGE_TAG}
EOF

  cp -f "$SCRIPT_DIR/$SCRIPT_NAME" "$BUNDLE/coder-airgap.sh"
  chmod +x "$BUNDLE/coder-airgap.sh"
  write_readme

  echo
  log "DONE. Copy the entire '$(basename "$BUNDLE")' directory to the air-gapped bastion, then run:"
  echo "       REGISTRY=<registry:port>       ./$(basename "$BUNDLE")/coder-airgap.sh install"
  echo "  or:  USE_BUNDLED_REGISTRY=true      ./$(basename "$BUNDLE")/coder-airgap.sh install"
}

###############################################################################
# Template generation — everything below is written verbatim (quoted heredocs)
# so all ${...} you see inside is Terraform interpolation, not shell.
###############################################################################
write_module_code_server() {
  mkdir -p "$1/modules/code-server"
  cat > "$1/modules/code-server/main.tf" <<'EOF'
terraform {
  required_providers {
    coder = { source = "coder/coder" }
  }
}

variable "agent_id" {
  type = string
}
variable "folder" {
  type    = string
  default = "/home/coder"
}
variable "port" {
  type    = number
  default = 13337
}

# code-server is baked into the workspace image — fully offline.
resource "coder_script" "code_server" {
  agent_id     = var.agent_id
  display_name = "code-server"
  icon         = "/icon/code.svg"
  run_on_start = true
  script       = <<-SH
    #!/usr/bin/env bash
    set -e
    echo "Starting pre-installed code-server on :${var.port}"
    nohup code-server --auth none --disable-telemetry --disable-update-check \
      --bind-addr 127.0.0.1:${var.port} "${var.folder}" \
      > /tmp/code-server.log 2>&1 &
  SH
}

resource "coder_app" "code_server" {
  agent_id     = var.agent_id
  slug         = "code-server"
  display_name = "code-server"
  icon         = "/icon/code.svg"
  url          = "http://localhost:${var.port}/?folder=${var.folder}"
  subdomain    = false
  share        = "owner"

  healthcheck {
    url       = "http://localhost:${var.port}/healthz"
    interval  = 5
    threshold = 6
  }
}
EOF
}

write_module_filebrowser() {
  mkdir -p "$1/modules/filebrowser"
  cat > "$1/modules/filebrowser/main.tf" <<'EOF'
terraform {
  required_providers {
    coder = { source = "coder/coder" }
  }
}

variable "agent_id" {
  type = string
}
variable "agent_name" {
  type    = string
  default = "main"
}
variable "folder" {
  type    = string
  default = "/home/coder"
}
variable "port" {
  type    = number
  default = 13339
}
variable "owner_name" {
  type = string
}
variable "workspace_name" {
  type = string
}

locals {
  # Path-based Coder app URL — no wildcard DNS needed in the air-gap.
  base_url = "/@${var.owner_name}/${var.workspace_name}.${var.agent_name}/apps/filebrowser"
}

# filebrowser binary is baked into the workspace image — fully offline.
resource "coder_script" "filebrowser" {
  agent_id     = var.agent_id
  display_name = "File Browser"
  icon         = "/icon/filebrowser.svg"
  run_on_start = true
  script       = <<-SH
    #!/usr/bin/env bash
    set -e
    echo "Starting pre-installed filebrowser on :${var.port}"
    rm -f "$HOME/.filebrowser.db"
    nohup filebrowser --noauth --root "${var.folder}" \
      --address 127.0.0.1 --port ${var.port} \
      --baseurl "${local.base_url}" \
      > /tmp/filebrowser.log 2>&1 &
  SH
}

resource "coder_app" "filebrowser" {
  agent_id     = var.agent_id
  slug         = "filebrowser"
  display_name = "File Browser"
  icon         = "/icon/filebrowser.svg"
  url          = "http://localhost:${var.port}"
  subdomain    = false
  share        = "owner"
}
EOF
}

write_module_vscode_desktop() {
  mkdir -p "$1/modules/vscode-desktop"
  cat > "$1/modules/vscode-desktop/main.tf" <<'EOF'
terraform {
  required_providers {
    coder = { source = "coder/coder" }
  }
}

variable "agent_id" {
  type = string
}
variable "agent_name" {
  type    = string
  default = "main"
}
variable "folder" {
  type    = string
  default = "/home/coder"
}
variable "owner_name" {
  type = string
}
variable "workspace_name" {
  type = string
}

# Button that opens the workspace in local VS Code Desktop (Coder Remote
# extension). No downloads happen server-side — air-gap safe.
resource "coder_app" "vscode" {
  agent_id     = var.agent_id
  slug         = "vscode"
  display_name = "VS Code Desktop"
  icon         = "/icon/code.svg"
  external     = true
  url          = "vscode://coder.coder-remote/open?owner=${var.owner_name}&workspace=${var.workspace_name}&agent=${var.agent_name}&folder=${var.folder}"
}
EOF
}

write_module_novnc() {
  mkdir -p "$1/modules/novnc"
  cat > "$1/modules/novnc/main.tf" <<'EOF'
terraform {
  required_providers {
    coder = { source = "coder/coder" }
  }
}

variable "agent_id" {
  type = string
}
variable "port" {
  type    = number
  default = 6080
}
variable "vnc_display" {
  type    = number
  default = 1
}
variable "geometry" {
  type    = string
  default = "1920x1080"
}

# TigerVNC + websockify + noVNC are baked into the image — fully offline.
resource "coder_script" "novnc" {
  agent_id     = var.agent_id
  display_name = "noVNC Desktop"
  icon         = "/icon/desktop.svg"
  run_on_start = true
  script       = <<-SH
    #!/usr/bin/env bash
    set -e
    echo "Starting TigerVNC (display :${var.vnc_display}) + noVNC on :${var.port}"
    vncserver -kill :${var.vnc_display} >/dev/null 2>&1 || true
    rm -f /tmp/.X${var.vnc_display}-lock /tmp/.X11-unix/X${var.vnc_display} 2>/dev/null || true
    vncserver :${var.vnc_display} -geometry ${var.geometry} -depth 24 \
      -localhost yes -SecurityTypes None --I-KNOW-THIS-IS-INSECURE \
      > /tmp/vncserver.log 2>&1
    nohup websockify --web /usr/share/novnc \
      ${var.port} localhost:${var.vnc_display + 5900} \
      > /tmp/websockify.log 2>&1 &
  SH
}

resource "coder_app" "novnc" {
  agent_id     = var.agent_id
  slug         = "novnc"
  display_name = "noVNC Desktop"
  icon         = "/icon/desktop.svg"
  url          = "http://localhost:${var.port}/vnc.html?autoconnect=true&resize=remote"
  subdomain    = false
  share        = "owner"
}
EOF
}

# Common template body: coder_agent + PVC + Kubernetes DEPLOYMENT running the
# envbox outer container (privileged) which boots the inner workspace image
# with a working Docker daemon (sysbox). Modeled on coder/coder's envbox
# example template, adapted to kubernetes_deployment.
write_template_common() {
  cat > "$1/main.tf" <<'EOF'
terraform {
  required_providers {
    coder      = { source = "coder/coder" }
    kubernetes = { source = "hashicorp/kubernetes" }
  }
}

# Coder's built-in provisioner runs in-cluster; use the pod's service account.
provider "kubernetes" {}
provider "coder" {}

variable "namespace" {
  type        = string
  description = "Kubernetes namespace to deploy workspaces into"
  default     = "coder"
}
variable "envbox_image" {
  type        = string
  description = "Envbox outer image, from the air-gapped registry"
}
variable "inner_image" {
  type        = string
  description = "Inner workspace image, from the air-gapped registry"
}
variable "home_storage_class" {
  type        = string
  description = "StorageClass for home PVCs (empty = cluster default)"
  default     = ""
}

data "coder_workspace" "me" {}
data "coder_workspace_owner" "me" {}

data "coder_parameter" "cpu" {
  name         = "cpu"
  display_name = "CPU cores"
  type         = "number"
  default      = "2"
  mutable      = true
  option {
    name  = "2 cores"
    value = "2"
  }
  option {
    name  = "4 cores"
    value = "4"
  }
  option {
    name  = "8 cores"
    value = "8"
  }
}

data "coder_parameter" "memory" {
  name         = "memory"
  display_name = "Memory (GB)"
  type         = "number"
  default      = "4"
  mutable      = true
  option {
    name  = "4 GB"
    value = "4"
  }
  option {
    name  = "8 GB"
    value = "8"
  }
  option {
    name  = "16 GB"
    value = "16"
  }
}

data "coder_parameter" "home_size" {
  name         = "home_disk_size"
  display_name = "Home disk size (GB)"
  type         = "number"
  default      = "10"
  mutable      = false
  validation {
    min = 1
    max = 500
  }
}

resource "coder_agent" "main" {
  os   = "linux"
  arch = "amd64"

  startup_script = <<-SH
    #!/usr/bin/env bash
    set -e
    # ---- Docker inside the workspace (envbox / sysbox) --------------------
    if ! pgrep -x dockerd >/dev/null 2>&1; then
      echo "Starting dockerd inside the workspace..."
      sudo bash -c 'nohup dockerd > /tmp/dockerd.log 2>&1 &'
    fi
    for i in $(seq 1 30); do
      if docker info >/dev/null 2>&1; then echo "Docker is ready."; break; fi
      sleep 2
    done
    docker info >/dev/null 2>&1 || echo "WARNING: dockerd did not come up; see /tmp/dockerd.log"
  SH

  metadata {
    display_name = "CPU Usage"
    key          = "cpu"
    script       = "coder stat cpu"
    interval     = 10
    timeout      = 1
  }
  metadata {
    display_name = "Memory Usage"
    key          = "mem"
    script       = "coder stat mem"
    interval     = 10
    timeout      = 1
  }
  metadata {
    display_name = "Home Disk"
    key          = "disk"
    script       = "coder stat disk --path /home/coder"
    interval     = 60
    timeout      = 1
  }
}

# File Browser is present in every template variant.
module "filebrowser" {
  source         = "./modules/filebrowser"
  agent_id       = coder_agent.main.id
  agent_name     = "main"
  owner_name     = lower(data.coder_workspace_owner.me.name)
  workspace_name = lower(data.coder_workspace.me.name)
}

resource "kubernetes_persistent_volume_claim" "home" {
  metadata {
    name      = "coder-${lower(data.coder_workspace_owner.me.name)}-${lower(data.coder_workspace.me.name)}-home"
    namespace = var.namespace
    labels = {
      "com.coder.resource"       = "true"
      "com.coder.workspace.id"   = data.coder_workspace.me.id
      "com.coder.user.username"  = data.coder_workspace_owner.me.name
    }
  }
  wait_until_bound = false
  spec {
    access_modes       = ["ReadWriteOnce"]
    storage_class_name = var.home_storage_class != "" ? var.home_storage_class : null
    resources {
      requests = {
        storage = "${data.coder_parameter.home_size.value}Gi"
      }
    }
  }
}

# ---------------- Kubernetes (Deployment) + envbox outer container ----------
resource "kubernetes_deployment" "main" {
  count            = data.coder_workspace.me.start_count
  wait_for_rollout = false

  metadata {
    name      = "coder-${lower(data.coder_workspace_owner.me.name)}-${lower(data.coder_workspace.me.name)}"
    namespace = var.namespace
    labels = {
      "app.kubernetes.io/name"   = "coder-workspace"
      "com.coder.resource"       = "true"
      "com.coder.workspace.id"   = data.coder_workspace.me.id
      "com.coder.workspace.name" = data.coder_workspace.me.name
      "com.coder.user.id"        = data.coder_workspace_owner.me.id
      "com.coder.user.username"  = data.coder_workspace_owner.me.name
    }
  }

  spec {
    replicas = 1
    strategy {
      type = "Recreate"
    }
    selector {
      match_labels = {
        "com.coder.workspace.id" = data.coder_workspace.me.id
      }
    }
    template {
      metadata {
        labels = {
          "com.coder.workspace.id" = data.coder_workspace.me.id
        }
      }
      spec {
        restart_policy = "Always"

        container {
          name              = "dev"
          image             = var.envbox_image
          image_pull_policy = "IfNotPresent"
          command           = ["/envbox", "docker"]

          # The OUTER envbox container must be privileged; the inner
          # workspace container it creates (via sysbox) is NOT privileged.
          security_context {
            privileged = true
          }

          resources {
            requests = {
              cpu    = data.coder_parameter.cpu.value
              memory = "${data.coder_parameter.memory.value}Gi"
            }
            limits = {
              cpu    = data.coder_parameter.cpu.value
              memory = "${data.coder_parameter.memory.value}Gi"
            }
          }

          env {
            name  = "CODER_AGENT_TOKEN"
            value = coder_agent.main.token
          }
          env {
            name  = "CODER_AGENT_URL"
            value = data.coder_workspace.me.access_url
          }
          env {
            name  = "CODER_INNER_IMAGE"
            value = var.inner_image
          }
          env {
            name  = "CODER_INNER_USERNAME"
            value = "coder"
          }
          env {
            name  = "CODER_INNER_HOSTNAME"
            value = data.coder_workspace.me.name
          }
          env {
            name  = "CODER_BOOTSTRAP_SCRIPT"
            value = coder_agent.main.init_script
          }
          env {
            name  = "CODER_MOUNTS"
            value = "/home/coder:/home/coder"
          }
          env {
            name = "CODER_CPUS"
            value_from {
              resource_field_ref {
                resource = "limits.cpu"
              }
            }
          }
          env {
            name = "CODER_MEMORY"
            value_from {
              resource_field_ref {
                resource = "limits.memory"
              }
            }
          }

          volume_mount {
            name       = "home"
            mount_path = "/home/coder"
          }
          volume_mount {
            name       = "envbox-docker"
            mount_path = "/var/lib/coder/docker"
          }
          volume_mount {
            name       = "envbox-containers"
            mount_path = "/var/lib/coder/containers"
          }
          volume_mount {
            name       = "sysbox"
            mount_path = "/var/lib/sysbox"
          }
          volume_mount {
            name       = "envbox-docker-lib"
            mount_path = "/var/lib/docker"
          }
          volume_mount {
            name       = "usr-src"
            mount_path = "/usr/src"
            read_only  = true
          }
          volume_mount {
            name       = "lib-modules"
            mount_path = "/lib/modules"
            read_only  = true
          }
        }

        volume {
          name = "home"
          persistent_volume_claim {
            claim_name = kubernetes_persistent_volume_claim.home.metadata[0].name
            read_only  = false
          }
        }
        volume {
          name = "envbox-docker"
          empty_dir {}
        }
        volume {
          name = "envbox-containers"
          empty_dir {}
        }
        volume {
          name = "envbox-docker-lib"
          empty_dir {}
        }
        volume {
          name = "sysbox"
          empty_dir {}
        }
        volume {
          name = "usr-src"
          host_path {
            path = "/usr/src"
          }
        }
        volume {
          name = "lib-modules"
          host_path {
            path = "/lib/modules"
          }
        }
      }
    }
  }
}
EOF
}

write_templates() {
  local t1="$BUNDLE/templates/ubuntu-base"
  local t2="$BUNDLE/templates/ubuntu-novnc"
  rm -rf "$t1" "$t2"
  mkdir -p "$t1" "$t2"

  # ---------------- Template 1: ubuntu-base ---------------------------------
  # modules: vscode (desktop), terminal (native to Coder), filebrowser,
  #          code-server
  write_template_common      "$t1"
  write_module_filebrowser   "$t1"
  write_module_code_server   "$t1"
  write_module_vscode_desktop "$t1"
  cat >> "$t1/main.tf" <<'EOF'

# ------------------------- ubuntu-base extras --------------------------------
module "code_server" {
  source   = "./modules/code-server"
  agent_id = coder_agent.main.id
}

module "vscode_desktop" {
  source         = "./modules/vscode-desktop"
  agent_id       = coder_agent.main.id
  agent_name     = "main"
  owner_name     = data.coder_workspace_owner.me.name
  workspace_name = data.coder_workspace.me.name
}
EOF
  cat > "$t1/README.md" <<'EOF'
# ubuntu-base — Kubernetes (Deployment) + envbox
Vanilla Ubuntu workspace. Apps: Terminal (built-in), code-server,
VS Code Desktop button, File Browser. `docker run hello-world` works inside
the workspace (dockerd via envbox/sysbox — outer container privileged, inner
workspace unprivileged). Fully offline: all binaries baked into the image,
all Terraform modules vendored locally, all providers from the local mirror.
EOF

  # ---------------- Template 2: ubuntu-novnc --------------------------------
  # modules: terminal (native), filebrowser, novnc
  write_template_common    "$t2"
  write_module_filebrowser "$t2"
  write_module_novnc       "$t2"
  cat >> "$t2/main.tf" <<'EOF'

# ------------------------- ubuntu-novnc extras -------------------------------
module "novnc" {
  source   = "./modules/novnc"
  agent_id = coder_agent.main.id
}
EOF
  cat > "$t2/README.md" <<'EOF'
# ubuntu-novnc — Kubernetes (Deployment) + envbox
Ubuntu workspace with a full XFCE desktop in the browser (TigerVNC + noVNC).
Apps: Terminal (built-in), File Browser, noVNC Desktop. `docker run` works
inside the workspace (envbox/sysbox). Fully offline.
EOF
}

write_readme() {
  cat > "$BUNDLE/README.md" <<EOF
# Coder air-gap bundle (Coder v${CODER_VERSION})

    bin/            coder, terraform, helm, kubectl (linux/amd64)
    charts/         coder-${CODER_CHART_VERSION}.tgz, cloudnative-pg-${CNPG_CHART_VERSION}.tgz (CRDs bundled)
    images/         images.tar — every container image (docker save)
    tf-providers/   Terraform provider filesystem mirror (coder, kubernetes)
    config/         terraformrc (.tfrc) baked into the custom Coder image
    build/          Dockerfiles used to build the custom images
    templates/      ubuntu-base/ and ubuntu-novnc/ (+ vendored local modules)
    coder-airgap.sh this installer (run: 'install' subcommand)

## Install (air-gapped bastion — needs docker + kubectl access)

    REGISTRY=registry.internal:5000 ./coder-airgap.sh install
or
    USE_BUNDLED_REGISTRY=true ./coder-airgap.sh install

## Prerequisites on the AIR-GAPPED cluster
* Privileged pods must be allowed in the workspaces namespace (envbox's
  OUTER container is privileged; inner workspaces are not).
* Nodes expose /usr/src and /lib/modules (sysbox kernel-header requirement).
* A default StorageClass (or set PG_STORAGE_CLASS / HOME_STORAGE_CLASS).
* Bundled-registry mode: every node must reach <bastion-ip>:${BUNDLED_REGISTRY_PORT}
  and trust it as an insecure registry (containerd hosts.toml or docker
  daemon.json "insecure-registries").

## Post-install hardening already applied (per coder.com/docs/install/airgap)
* CODER_TELEMETRY_ENABLE=false, CODER_UPDATE_CHECK=false
* CODER_DERP_SERVER_STUN_ADDRESSES=disable, CODER_BLOCK_DIRECT=true
* External PostgreSQL via CODER_PG_CONNECTION_URL (CloudNativePG)
* Terraform providers resolved ONLY from the baked-in filesystem mirror
EOF
}

###############################################################################
# PHASE 2 — INSTALL  (air-gapped bastion)
###############################################################################
kctl() { "$BUNDLE/bin/kubectl" "$@"; }
hlm()  { "$BUNDLE/bin/helm" "$@"; }

cmd_install() {
  [[ -f "$BUNDLE/.bundle-manifest" ]] || die "No .bundle-manifest found — run the copy of this script INSIDE the bundle directory."
  # shellcheck disable=SC1091
  source "$BUNDLE/.bundle-manifest"
  need docker
  [[ -x "$BUNDLE/bin/kubectl" && -x "$BUNDLE/bin/helm" && -x "$BUNDLE/bin/coder" ]] || die "bundle bin/ is incomplete"
  kctl get nodes >/dev/null || die "kubectl cannot reach the cluster (check KUBECONFIG)"
  mkdir -p "$BUNDLE/manifests"

  # ------------------------------------------------------------ load images
  log "Loading images from bundle (docker load)"
  docker load -i "$BUNDLE/images/images.tar"

  # --------------------------------------------------------------- registry
  local push_registry=""
  if [[ "$USE_BUNDLED_REGISTRY" == "true" && -z "$REGISTRY" ]]; then
    local host_ip="${BASTION_IP:-$(hostname -I 2>/dev/null | awk '{print $1}')}"
    [[ -n "$host_ip" ]] || die "Could not detect bastion IP; set BASTION_IP=..."
    REGISTRY="${host_ip}:${BUNDLED_REGISTRY_PORT}"       # what the CLUSTER pulls from
    push_registry="localhost:${BUNDLED_REGISTRY_PORT}"   # what WE push to (no TLS needed)
    log "Starting bundled registry:2 on the bastion -> cluster address: $REGISTRY"
    docker rm -f coder-airgap-registry >/dev/null 2>&1 || true
    docker run -d --restart=always --name coder-airgap-registry \
      -p "${BUNDLED_REGISTRY_PORT}:5000" registry:2 >/dev/null
    sleep 2
    warn "Every cluster node MUST trust ${REGISTRY} as an insecure registry"
    warn "(containerd hosts.toml / docker daemon.json), or image pulls will fail."
  fi
  [[ -n "$REGISTRY" ]] || die "Set REGISTRY=<host:port> (or USE_BUNDLED_REGISTRY=true)"
  push_registry="${push_registry:-$REGISTRY}"

  log "Tagging & pushing images to $push_registry"
  local pairs=(
    "coder-airgap:v${CODER_VERSION}|coder-airgap:v${CODER_VERSION}"
    "airgap/ubuntu-base:latest|airgap/ubuntu-base:latest"
    "airgap/ubuntu-novnc:latest|airgap/ubuntu-novnc:latest"
    "ghcr.io/coder/envbox:${ENVBOX_VERSION}|coder/envbox:${ENVBOX_VERSION}"
    "ghcr.io/cloudnative-pg/cloudnative-pg:${CNPG_OPERATOR_VERSION}|cloudnative-pg/cloudnative-pg:${CNPG_OPERATOR_VERSION}"
    "ghcr.io/cloudnative-pg/postgresql:${CNPG_PG_IMAGE_TAG}|cloudnative-pg/postgresql:${CNPG_PG_IMAGE_TAG}"
  )
  local pair src dst
  for pair in "${pairs[@]}"; do
    src="${pair%%|*}"; dst="${pair##*|}"
    docker tag "$src" "$push_registry/$dst"
    docker push "$push_registry/$dst"
  done

  # -------------------------------------------------------------- namespaces
  kctl create namespace "$CNPG_NAMESPACE"  --dry-run=client -o yaml | kctl apply -f -
  kctl create namespace "$CODER_NAMESPACE" --dry-run=client -o yaml | kctl apply -f -

  # ------------------------------------------------------------ CNPG operator
  log "Installing CloudNativePG operator (chart bundles its CRDs)"
  hlm upgrade --install cnpg "$BUNDLE/charts/cloudnative-pg-${CNPG_CHART_VERSION}.tgz" \
    --namespace "$CNPG_NAMESPACE" \
    --set "image.repository=${REGISTRY}/cloudnative-pg/cloudnative-pg" \
    --set "image.tag=${CNPG_OPERATOR_VERSION}" \
    --wait --timeout 10m
  kctl wait --for=condition=Established "crd/clusters.postgresql.cnpg.io" --timeout=180s

  # ---------------------------------------------------------------- postgres
  log "Provisioning PostgreSQL cluster 'coder-db' (CloudNativePG)"
  {
    cat <<EOF
apiVersion: postgresql.cnpg.io/v1
kind: Cluster
metadata:
  name: coder-db
  namespace: ${CODER_NAMESPACE}
spec:
  instances: 1
  imageName: ${REGISTRY}/cloudnative-pg/postgresql:${CNPG_PG_IMAGE_TAG}
  bootstrap:
    initdb:
      database: coder
      owner: coder
  storage:
    size: ${PG_STORAGE_SIZE}
EOF
    [[ -n "$PG_STORAGE_CLASS" ]] && echo "    storageClass: ${PG_STORAGE_CLASS}"
  } > "$BUNDLE/manifests/coder-db.yaml"
  kctl apply -f "$BUNDLE/manifests/coder-db.yaml"

  log "Waiting for PostgreSQL to become ready (a few minutes)..."
  if ! kctl wait --for=condition=Ready "cluster/coder-db" -n "$CODER_NAMESPACE" --timeout=15m 2>/dev/null; then
    kctl wait --for=jsonpath='{.status.readyInstances}'=1 "cluster/coder-db" \
      -n "$CODER_NAMESPACE" --timeout=15m
  fi

  # ------------------------------------------------------------------- coder
  local access_url="${CODER_ACCESS_URL:-http://coder.${CODER_NAMESPACE}.svc.cluster.local}"
  log "Installing Coder (image: ${REGISTRY}/coder-airgap:v${CODER_VERSION}, access URL: ${access_url})"
  cat > "$BUNDLE/manifests/coder-values.yaml" <<EOF
coder:
  image:
    repo: "${REGISTRY}/coder-airgap"
    tag: "v${CODER_VERSION}"
    pullPolicy: IfNotPresent
  service:
    type: ${CODER_SERVICE_TYPE}
  env:
    - name: CODER_ACCESS_URL
      value: "${access_url}"
    # External DB, as required for air-gap (secret created by CloudNativePG)
    - name: CODER_PG_CONNECTION_URL
      valueFrom:
        secretKeyRef:
          name: coder-db-app
          key: uri
    # Air-gap hardening per coder.com/docs/install/airgap
    - name: CODER_TELEMETRY_ENABLE
      value: "false"
    - name: CODER_UPDATE_CHECK
      value: "false"
    - name: CODER_DERP_SERVER_STUN_ADDRESSES
      value: "disable"
    - name: CODER_BLOCK_DIRECT
      value: "true"
EOF
  hlm upgrade --install coder "$BUNDLE/charts/coder-${CODER_CHART_VERSION}.tgz" \
    --namespace "$CODER_NAMESPACE" \
    --values "$BUNDLE/manifests/coder-values.yaml" \
    --wait --timeout 10m
  kctl rollout status deployment/coder -n "$CODER_NAMESPACE" --timeout=10m

  # ------------------------------------------------ first user + templates
  [[ -n "$ADMIN_PASSWORD" ]] || ADMIN_PASSWORD="$(head -c 48 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c 20)aA1!"
  log "Creating first admin user and pushing templates (via temporary port-forward)"
  kctl -n "$CODER_NAMESPACE" port-forward svc/coder 32112:80 >/dev/null 2>&1 &
  local pf_pid=$!
  trap 'kill "$pf_pid" 2>/dev/null || true' EXIT
  sleep 4

  export CODER_URL="http://127.0.0.1:32112"
  if ! "$BUNDLE/bin/coder" login "$CODER_URL" \
        --first-user-email    "$ADMIN_EMAIL" \
        --first-user-username "$ADMIN_USERNAME" \
        --first-user-password "$ADMIN_PASSWORD" \
        --first-user-trial=false; then
    warn "First-user creation failed (a user may already exist)."
    warn "Log in manually:  $BUNDLE/bin/coder login $CODER_URL"
    warn "then re-run:      REGISTRY=$REGISTRY $0 push-templates"
    die  "Aborting before template push."
  fi

  push_templates

  kill "$pf_pid" 2>/dev/null || true
  trap - EXIT

  # ---------------------------------------------------------------- summary
  echo
  log "================================================================"
  log " Coder is installed and fully air-gapped."
  log "   Namespace:       $CODER_NAMESPACE"
  log "   Access URL:      $access_url"
  if [[ "$CODER_SERVICE_TYPE" == "NodePort" ]]; then
    local np
    np="$(kctl -n "$CODER_NAMESPACE" get svc coder -o jsonpath='{.spec.ports[0].nodePort}' 2>/dev/null || true)"
    [[ -n "$np" ]] && log "   Browser access:  http://<any-node-ip>:${np}"
  fi
  log "   Admin login:     $ADMIN_USERNAME  ($ADMIN_EMAIL)"
  log "   Admin password:  $ADMIN_PASSWORD    <-- SAVE THIS NOW"
  log "   Templates:       ubuntu-base, ubuntu-novnc  (Kubernetes Deployment + envbox)"
  log "================================================================"
  warn "Reminder: privileged pods must be allowed in '$CODER_NAMESPACE'"
  warn "(envbox outer container), and nodes must expose /usr/src + /lib/modules."
}

push_templates() {
  # shellcheck disable=SC1091
  source "$BUNDLE/.bundle-manifest"
  local coder_bin="$BUNDLE/bin/coder"
  local t inner_image
  for t in ubuntu-base ubuntu-novnc; do
    inner_image="$REGISTRY/airgap/$t:latest"
    log "Pushing template '$t' (inner image: $inner_image)"
    "$coder_bin" templates push "$t" \
      --directory "$BUNDLE/templates/$t" \
      --variable "namespace=${CODER_NAMESPACE}" \
      --variable "envbox_image=${REGISTRY}/coder/envbox:${ENVBOX_VERSION}" \
      --variable "inner_image=${inner_image}" \
      --variable "home_storage_class=${HOME_STORAGE_CLASS}" \
      --message "air-gap zero-to-hero install" \
      --yes
  done
}

cmd_push_templates() {
  [[ -f "$BUNDLE/.bundle-manifest" ]] || die "Run the copy of this script inside the bundle."
  [[ -n "$REGISTRY" ]] || die "Set REGISTRY=<host:port>"
  push_templates
}

###############################################################################
# entrypoint
###############################################################################
case "${1:-}" in
  download)       cmd_download ;;
  install)        cmd_install ;;
  push-templates) cmd_push_templates ;;
  *)
    cat <<EOF
Usage: $SCRIPT_NAME <command>

  download        (connected machine)   Build the offline bundle into
                                        ./coder-airgap-bundle/
  install         (air-gapped bastion)  Push images to a registry, install
                                        CloudNativePG + PostgreSQL + Coder,
                                        create the first admin user, and push
                                        both workspace templates.
  push-templates  (air-gapped bastion)  Re-push templates only (requires an
                                        active 'coder' CLI session + REGISTRY)

Key environment variables
  download : CODER_VERSION ENVBOX_VERSION CNPG_CHART_VERSION CNPG_PG_IMAGE_TAG
             TERRAFORM_VERSION TF_CODER_PROVIDER TF_K8S_PROVIDER
  install  : REGISTRY=<host:port>  |  USE_BUNDLED_REGISTRY=true [BASTION_IP=..]
             CODER_NAMESPACE CODER_ACCESS_URL CODER_SERVICE_TYPE
             PG_STORAGE_SIZE PG_STORAGE_CLASS HOME_STORAGE_CLASS
             ADMIN_USERNAME ADMIN_EMAIL ADMIN_PASSWORD
EOF
    exit 1
    ;;
esac
