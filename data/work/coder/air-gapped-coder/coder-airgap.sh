#!/usr/bin/env bash
###############################################################################
# coder-airgap.sh — Zero-to-hero air-gapped Coder installer for Kubernetes/EKS
#
# Guidance sources:
#   https://coder.com/docs/install/airgap   (air-gap image, .tfrc, provider
#                                            mirror, telemetry/STUN hardening,
#                                            external Postgres)
#   https://github.com/coder/envbox         (Docker-in-workspace via sysbox)
#
# USAGE
# =====
#  Phase 1 — INTERNET-CONNECTED machine (needs: docker, curl, tar, unzip):
#      ./coder-airgap.sh download
#    -> builds ./coder-airgap-bundle/ AND automatically packs it into
#       ./coder-airgap-bundle.tgz (script included inside).
#
#  Copy coder-airgap-bundle.tgz (and optionally this script) to the
#  air-gapped bastion — a Linux host with a docker daemon and kubectl access
#  to the target EKS cluster — then:
#
#  Phase 2 — AIR-GAPPED bastion:
#      CODER_HOSTNAME=coder.corp.internal ./coder-airgap.sh install
#
#    The install command automatically:
#      * extracts coder-airgap-bundle.tgz if the bundle dir isn't present
#      * starts the BUNDLED registry (TLS-enabled) — no external registry
#      * creates all PKI secrets / configmaps / CA bundles needed
#      * runs a node image-pull smoke test (fail-fast, with guidance)
#      * installs CloudNativePG + PostgreSQL + Coder
#      * exposes Coder through your pre-provisioned Traefik ingress
#        (Ingress object by default; Gateway API HTTPRoute via GATEWAY_API=true)
#      * creates admin user  admin / coder-admin  (override via env)
#      * pushes both workspace templates, ready to launch
#
# TEMPLATES (both "Kubernetes (Deployment)" + envbox => Docker works inside):
#   1. ubuntu-base  : terminal (native), VS Code Desktop, code-server,
#                     File Browser
#   2. ubuntu-novnc : terminal (native), File Browser, noVNC (XFCE desktop)
#
# TLS / PKI MODEL (EKS + Traefik with TLS provisioned in advance):
#   * Coder service is ClusterIP; Traefik terminates TLS in front of it.
#   * CODER_ACCESS_URL = https://$CODER_HOSTNAME (what browsers use).
#   * Workspace AGENTS connect via the internal http://coder.<ns>.svc URL,
#     so agent traffic never depends on org-CA trust inside containers.
#   * Bundled registry serves HTTPS. Two modes:
#       a) BYO cert: REGISTRY_TLS_CRT_FILE/REGISTRY_TLS_KEY_FILE signed by an
#          org CA your EKS nodes already trust  -> zero node configuration.
#       b) auto-generated CA (default)          -> node-trust materials are
#          emitted (pki/registry-ca.crt, manifests/hosts.toml, SSM snippet)
#          and the pull smoke test verifies nodes can pull before proceeding.
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
# Registry (bundled by default; TLS always on)
REGISTRY="${REGISTRY:-}"                          # leave empty => bundled registry
BUNDLED_REGISTRY_PORT="${BUNDLED_REGISTRY_PORT:-5000}"
BASTION_IP="${BASTION_IP:-}"                      # auto-detected if empty
REGISTRY_TLS_CRT_FILE="${REGISTRY_TLS_CRT_FILE:-}"  # org-signed cert (recommended)
REGISTRY_TLS_KEY_FILE="${REGISTRY_TLS_KEY_FILE:-}"
REGISTRY_TLS_CA_FILE="${REGISTRY_TLS_CA_FILE:-}"    # CA chain for bastion docker trust
SKIP_PULL_TEST="${SKIP_PULL_TEST:-false}"

# Namespaces / access
CODER_NAMESPACE="${CODER_NAMESPACE:-coder}"
CNPG_NAMESPACE="${CNPG_NAMESPACE:-cnpg-system}"
CODER_HOSTNAME="${CODER_HOSTNAME:-}"              # REQUIRED for ingress (e.g. coder.corp.internal)
CODER_WILDCARD_HOSTNAME="${CODER_WILDCARD_HOSTNAME:-}"  # optional, e.g. *.coder.corp.internal
CODER_SERVICE_TYPE="${CODER_SERVICE_TYPE:-ClusterIP}"

# Ingress (Traefik) — TLS is terminated by Traefik, provisioned in advance
INGRESS_ENABLE="${INGRESS_ENABLE:-true}"
INGRESS_CLASS="${INGRESS_CLASS:-traefik}"
TLS_SECRET_NAME="${TLS_SECRET_NAME:-}"            # existing tls secret for the host (optional)
TLS_CRT_FILE="${TLS_CRT_FILE:-}"                  # or provide files -> secret 'coder-tls' is created
TLS_KEY_FILE="${TLS_KEY_FILE:-}"
CA_BUNDLE_FILE="${CA_BUNDLE_FILE:-}"              # org CA bundle -> mounted into Coder + configmap

# Gateway API alternative (Traefik Gateway) — instead of an Ingress object
GATEWAY_API="${GATEWAY_API:-false}"
GATEWAY_NAME="${GATEWAY_NAME:-traefik-gateway}"
GATEWAY_NAMESPACE="${GATEWAY_NAMESPACE:-traefik}"
GATEWAY_SECTION_NAME="${GATEWAY_SECTION_NAME:-}"  # optional listener sectionName

# Storage
PG_STORAGE_SIZE="${PG_STORAGE_SIZE:-10Gi}"
PG_STORAGE_CLASS="${PG_STORAGE_CLASS:-}"          # empty = cluster default
HOME_STORAGE_CLASS="${HOME_STORAGE_CLASS:-}"

# Admin (per request: admin / coder-admin by default)
ADMIN_USERNAME="${ADMIN_USERNAME:-admin}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-coder-admin}"
ADMIN_EMAIL="${ADMIN_EMAIL:-admin@airgap.local}"

# --------------------------------- layout -----------------------------------
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCRIPT_NAME="$(basename "${BASH_SOURCE[0]}")"
BUNDLE_NAME="coder-airgap-bundle"
BUNDLE="${BUNDLE:-$SCRIPT_DIR/$BUNDLE_NAME}"
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
  mkdir -p "$BUNDLE"/{images,charts,bin,tf-providers,config,templates,build,manifests,pki}

  # ---------------------------------------------------------------- binaries
  log "Downloading CLI binaries (coder, terraform, helm, kubectl)"
  curl -fL --retry 3 -o "$BUNDLE/bin/coder.tar.gz" \
    "https://github.com/coder/coder/releases/download/v${CODER_VERSION}/coder_${CODER_VERSION}_linux_amd64.tar.gz"
  # Sanity-check we actually got a gzip archive (not an HTML error page)
  gzip -t "$BUNDLE/bin/coder.tar.gz" \
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
  "$BUNDLE/bin/coder" version >/dev/null 2>&1 \
    || warn "coder binary won't run on THIS host (fine if you're only bundling), continuing"

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

  # -------------------------------------------- auto-pack the whole bundle
  log "Packing bundle into ${BUNDLE_NAME}.tgz"
  tar -czf "$SCRIPT_DIR/${BUNDLE_NAME}.tgz" -C "$(dirname "$BUNDLE")" "$(basename "$BUNDLE")"

  echo
  log "DONE."
  log "  Bundle dir : $BUNDLE"
  log "  Tarball    : $SCRIPT_DIR/${BUNDLE_NAME}.tgz  ($(du -h "$SCRIPT_DIR/${BUNDLE_NAME}.tgz" | cut -f1))"
  echo
  echo "  Copy ${BUNDLE_NAME}.tgz (and this script) to the air-gapped bastion, then:"
  echo "      CODER_HOSTNAME=coder.corp.internal ./coder-airgap.sh install"
  echo "  (it auto-extracts the tarball and uses the bundled TLS registry)"
}

###############################################################################
# Template generation — everything below is written verbatim (quoted heredocs)
# so all \${...} inside is Terraform interpolation, not shell.
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
variable "agent_url" {
  type        = string
  description = "URL agents use to reach Coder. Set to the internal cluster URL (http://coder.<ns>.svc.cluster.local) so agent traffic bypasses external TLS entirely. Empty = workspace access URL."
  default     = ""
}
variable "home_storage_class" {
  type        = string
  description = "StorageClass for home PVCs (empty = cluster default)"
  default     = ""
}

data "coder_workspace" "me" {}
data "coder_workspace_owner" "me" {}

locals {
  agent_url = var.agent_url != "" ? var.agent_url : data.coder_workspace.me.access_url
}

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
      "com.coder.resource"      = "true"
      "com.coder.workspace.id"  = data.coder_workspace.me.id
      "com.coder.user.username" = data.coder_workspace_owner.me.name
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
            value = local.agent_url
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
  write_template_common       "$t1"
  write_module_filebrowser    "$t1"
  write_module_code_server    "$t1"
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
VS Code Desktop button, File Browser. Docker works inside the workspace
(dockerd via envbox/sysbox — outer container privileged, inner unprivileged).
Fully offline: binaries baked into the image, modules vendored locally,
providers from the local filesystem mirror.
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
Apps: Terminal (built-in), File Browser, noVNC Desktop. Docker works inside
the workspace (envbox/sysbox). Fully offline.
EOF
}

write_readme() {
  cat > "$BUNDLE/README.md" <<EOF
# Coder air-gap bundle (Coder v${CODER_VERSION}) — EKS + Traefik edition

    bin/            coder, terraform, helm, kubectl (linux/amd64)
    charts/         coder-${CODER_CHART_VERSION}.tgz, cloudnative-pg-${CNPG_CHART_VERSION}.tgz (CRDs bundled)
    images/         images.tar — every container image (docker save)
    tf-providers/   Terraform provider filesystem mirror (coder, kubernetes)
    config/         terraformrc (.tfrc) baked into the custom Coder image
    build/          Dockerfiles used to build the custom images
    templates/      ubuntu-base/ and ubuntu-novnc/ (+ vendored local modules)
    pki/            (created at install) registry CA/cert, node-trust materials
    manifests/      (created at install) rendered manifests incl. hosts.toml
    coder-airgap.sh this installer

## Install (air-gapped bastion — needs docker + kubectl access to EKS)

    CODER_HOSTNAME=coder.corp.internal ./coder-airgap.sh install

Everything is automatic: bundle auto-extracts, the bundled TLS registry
starts on the bastion, images push, a node pull smoke test runs, CNPG +
PostgreSQL + Coder install, Traefik ingress is configured, admin user
(admin / coder-admin) is created, and both templates are pushed.

## Zero-node-config registry TLS (recommended)
Provide a cert signed by an org CA your EKS nodes already trust:
    REGISTRY_TLS_CRT_FILE=... REGISTRY_TLS_KEY_FILE=... [REGISTRY_TLS_CA_FILE=...]
Otherwise a CA is generated and node-trust materials are written to pki/
and manifests/hosts.toml with instructions (SSM / launch-template user data).

## Traefik
Default: Ingress object with ingressClassName=${INGRESS_CLASS}.
Gateway API instead: GATEWAY_API=true GATEWAY_NAME=... GATEWAY_NAMESPACE=...
TLS terminates at Traefik (pre-provisioned). Optionally create/use a per-host
secret: TLS_SECRET_NAME=... or TLS_CRT_FILE=/TLS_KEY_FILE=.
Org CA bundle for Coder itself: CA_BUNDLE_FILE=... (creates secret+configmap
'coder-ca-bundle' and mounts it into the Coder pods).

## Cluster prerequisites
* Privileged pods allowed in the workspaces namespace (envbox OUTER container
  only; inner workspaces are unprivileged).
* Nodes expose /usr/src and /lib/modules (sysbox requirement — true on EKS AMIs).
* EBS CSI driver / a default StorageClass (or set PG_STORAGE_CLASS /
  HOME_STORAGE_CLASS).
* Bastion port ${BUNDLED_REGISTRY_PORT} reachable from all nodes (security groups).

## Air-gap hardening applied (per coder.com/docs/install/airgap)
* CODER_TELEMETRY_ENABLE=false, CODER_UPDATE_CHECK=false
* CODER_DERP_SERVER_STUN_ADDRESSES=disable, CODER_BLOCK_DIRECT=true
* External PostgreSQL via CODER_PG_CONNECTION_URL (CloudNativePG)
* Terraform providers resolved ONLY from the baked-in filesystem mirror
* Workspace agents use the internal http://coder.<ns>.svc URL
EOF
}

###############################################################################
# PHASE 2 — INSTALL  (air-gapped bastion)
###############################################################################
kctl() { "$BUNDLE/bin/kubectl" "$@"; }
hlm()  { "$BUNDLE/bin/helm" "$@"; }

maybe_extract_bundle() {
  [[ -f "$BUNDLE/.bundle-manifest" ]] && return 0
  local cand
  for cand in "$SCRIPT_DIR/${BUNDLE_NAME}.tgz" "$PWD/${BUNDLE_NAME}.tgz"; do
    if [[ -f "$cand" ]]; then
      log "Auto-extracting bundle tarball: $cand"
      tar -xzf "$cand" -C "$(dirname "$cand")"
      BUNDLE="$(dirname "$cand")/${BUNDLE_NAME}"
      [[ -f "$BUNDLE/.bundle-manifest" ]] && return 0
    fi
  done
  [[ -f "$BUNDLE/.bundle-manifest" ]] \
    || die "Bundle not found. Place ${BUNDLE_NAME}.tgz next to this script (or run the script inside an extracted bundle)."
}

detect_bastion_ip() {
  local ip="${BASTION_IP:-}"
  [[ -z "$ip" ]] && ip="$(hostname -I 2>/dev/null | awk '{print $1}')"
  [[ -z "$ip" ]] && ip="$(ip -4 route get 10.0.0.1 2>/dev/null | awk '{for(i=1;i<=NF;i++) if($i=="src"){print $(i+1); exit}}')"
  [[ -n "$ip" ]] || die "Could not detect bastion IP; set BASTION_IP=<ip reachable from EKS nodes>"
  echo "$ip"
}

setup_bundled_registry() {
  need openssl
  local host_ip; host_ip="$(detect_bastion_ip)"
  REGISTRY="${host_ip}:${BUNDLED_REGISTRY_PORT}"
  local pki="$BUNDLE/pki"
  mkdir -p "$pki" "$BUNDLE/registry-data"

  if [[ -n "$REGISTRY_TLS_CRT_FILE" && -n "$REGISTRY_TLS_KEY_FILE" ]]; then
    log "Using provided (org-signed) registry TLS cert — zero node configuration needed"
    cp -f "$REGISTRY_TLS_CRT_FILE" "$pki/registry.crt"
    cp -f "$REGISTRY_TLS_KEY_FILE" "$pki/registry.key"
    if [[ -n "$REGISTRY_TLS_CA_FILE" ]]; then
      cp -f "$REGISTRY_TLS_CA_FILE" "$pki/registry-ca.crt"
    fi
    REGISTRY_SELF_SIGNED="false"
  else
    log "Generating registry PKI (self-signed CA + server cert, SAN: IP:${host_ip})"
    if [[ ! -f "$pki/registry-ca.crt" ]]; then
      openssl req -x509 -newkey rsa:4096 -sha256 -days 3650 -nodes \
        -keyout "$pki/registry-ca.key" -out "$pki/registry-ca.crt" \
        -subj "/CN=coder-airgap-registry-ca" >/dev/null 2>&1
    fi
    openssl req -newkey rsa:4096 -sha256 -nodes \
      -keyout "$pki/registry.key" -out "$pki/registry.csr" \
      -subj "/CN=coder-airgap-registry" >/dev/null 2>&1
    cat > "$pki/registry-san.cnf" <<EOF
subjectAltName = IP:${host_ip}, IP:127.0.0.1, DNS:localhost, DNS:$(hostname -f 2>/dev/null || hostname)
EOF
    openssl x509 -req -in "$pki/registry.csr" -sha256 -days 3650 \
      -CA "$pki/registry-ca.crt" -CAkey "$pki/registry-ca.key" -CAcreateserial \
      -extfile "$pki/registry-san.cnf" -out "$pki/registry.crt" >/dev/null 2>&1
    rm -f "$pki/registry.csr" "$pki/registry-san.cnf"
    REGISTRY_SELF_SIGNED="true"
  fi

  # Trust the registry cert on the BASTION's docker daemon (for pushes)
  if [[ -f "$pki/registry-ca.crt" ]]; then
    log "Installing registry CA into bastion docker trust (/etc/docker/certs.d/${REGISTRY}/)"
    local certs_d="/etc/docker/certs.d/${REGISTRY}"
    if [[ -w "$(dirname "$certs_d")" || $EUID -eq 0 ]]; then
      mkdir -p "$certs_d" && cp -f "$pki/registry-ca.crt" "$certs_d/ca.crt"
    else
      sudo mkdir -p "$certs_d" && sudo cp -f "$pki/registry-ca.crt" "$certs_d/ca.crt" \
        || die "Could not install registry CA to $certs_d — run: sudo mkdir -p $certs_d && sudo cp $pki/registry-ca.crt $certs_d/ca.crt, then re-run"
    fi
  fi

  log "Starting bundled TLS registry at https://${REGISTRY}"
  docker rm -f coder-airgap-registry >/dev/null 2>&1 || true
  docker run -d --restart=always --name coder-airgap-registry \
    -p "${BUNDLED_REGISTRY_PORT}:5000" \
    -v "$pki:/certs:ro" \
    -v "$BUNDLE/registry-data:/var/lib/registry" \
    -e REGISTRY_HTTP_TLS_CERTIFICATE=/certs/registry.crt \
    -e REGISTRY_HTTP_TLS_KEY=/certs/registry.key \
    registry:2 >/dev/null
  # Wait until the registry answers
  local i
  for i in $(seq 1 30); do
    curl -fsk "https://127.0.0.1:${BUNDLED_REGISTRY_PORT}/v2/" >/dev/null 2>&1 && break
    sleep 1
  done
  curl -fsk "https://127.0.0.1:${BUNDLED_REGISTRY_PORT}/v2/" >/dev/null 2>&1 \
    || die "Bundled registry failed to start (docker logs coder-airgap-registry)"

  # Node-trust materials for the self-signed case
  if [[ "$REGISTRY_SELF_SIGNED" == "true" ]]; then
    mkdir -p "$BUNDLE/manifests"
    cat > "$BUNDLE/manifests/hosts.toml" <<EOF
# Place on every EKS node at: /etc/containerd/certs.d/${REGISTRY}/hosts.toml
# together with the CA at:    /etc/containerd/certs.d/${REGISTRY}/ca.crt
server = "https://${REGISTRY}"

[host."https://${REGISTRY}"]
  capabilities = ["pull", "resolve"]
  ca = "/etc/containerd/certs.d/${REGISTRY}/ca.crt"
EOF
    cat > "$BUNDLE/manifests/node-trust-userdata.sh" <<EOF
#!/usr/bin/env bash
# Add to EKS launch-template user data, or run on every node via SSM:
#   aws ssm send-command --document-name AWS-RunShellScript \\
#     --targets Key=tag:eks:cluster-name,Values=<cluster> \\
#     --parameters commands="\$(cat this-file)"
mkdir -p /etc/containerd/certs.d/${REGISTRY}
cat > /etc/containerd/certs.d/${REGISTRY}/ca.crt <<'CACRT'
$(cat "$BUNDLE/pki/registry-ca.crt")
CACRT
cat > /etc/containerd/certs.d/${REGISTRY}/hosts.toml <<'HOSTS'
server = "https://${REGISTRY}"

[host."https://${REGISTRY}"]
  capabilities = ["pull", "resolve"]
  ca = "/etc/containerd/certs.d/${REGISTRY}/ca.crt"
HOSTS
# Newer EKS AMIs read certs.d dynamically (containerd config_path). If pulls
# still fail, ensure config_path is set and restart containerd:
grep -q 'config_path' /etc/containerd/config.toml || {
  echo 'Add: [plugins."io.containerd.grpc.v1.cri".registry] config_path = "/etc/containerd/certs.d"'
}
EOF
    chmod +x "$BUNDLE/manifests/node-trust-userdata.sh"
    warn "Self-signed registry CA in use. If the pull smoke test fails, distribute"
    warn "node trust with: $BUNDLE/manifests/node-trust-userdata.sh (SSM / user data),"
    warn "or re-run with an org-signed cert: REGISTRY_TLS_CRT_FILE=/KEY_FILE=."
  fi
}

pull_smoke_test() {
  [[ "$SKIP_PULL_TEST" == "true" ]] && { warn "Skipping node pull smoke test (SKIP_PULL_TEST=true)"; return 0; }
  log "Running node image-pull smoke test (${REGISTRY}/airgap/ubuntu-base:latest)"
  kctl -n "$CODER_NAMESPACE" delete pod airgap-pull-test --ignore-not-found >/dev/null 2>&1
  kctl -n "$CODER_NAMESPACE" run airgap-pull-test \
    --image="${REGISTRY}/airgap/ubuntu-base:latest" \
    --image-pull-policy=Always --restart=Never \
    --command -- /bin/true >/dev/null
  local i phase reason
  for i in $(seq 1 120); do   # up to 10 min — workspace images are large
    phase="$(kctl -n "$CODER_NAMESPACE" get pod airgap-pull-test -o jsonpath='{.status.phase}' 2>/dev/null || true)"
    [[ "$phase" == "Succeeded" ]] && break
    reason="$(kctl -n "$CODER_NAMESPACE" get pod airgap-pull-test \
      -o jsonpath='{.status.containerStatuses[0].state.waiting.reason}' 2>/dev/null || true)"
    if [[ "$reason" == "ImagePullBackOff" || "$reason" == "ErrImagePull" ]]; then
      kctl -n "$CODER_NAMESPACE" describe pod airgap-pull-test | sed -n '/Events:/,$p' >&2
      kctl -n "$CODER_NAMESPACE" delete pod airgap-pull-test --ignore-not-found >/dev/null 2>&1
      die "Nodes cannot pull from ${REGISTRY}. Fixes: (1) security group must allow nodes -> bastion:${BUNDLED_REGISTRY_PORT}; (2) if using the generated CA, install node trust via $BUNDLE/manifests/node-trust-userdata.sh; (3) best: re-run with an org-signed registry cert (REGISTRY_TLS_CRT_FILE/KEY_FILE)."
    fi
    sleep 5
  done
  kctl -n "$CODER_NAMESPACE" delete pod airgap-pull-test --ignore-not-found >/dev/null 2>&1
  [[ "$phase" == "Succeeded" ]] || die "Pull smoke test did not complete in time — investigate 'kubectl -n $CODER_NAMESPACE describe pod airgap-pull-test' scheduling/pull events, then re-run."
  log "Smoke test passed — nodes can pull from the bundled registry."
}

create_pki_resources() {
  # Ingress TLS secret (only if cert files provided; Traefik may already hold
  # the cert cluster-wide, in which case nothing is needed here)
  if [[ -n "$TLS_CRT_FILE" && -n "$TLS_KEY_FILE" ]]; then
    TLS_SECRET_NAME="${TLS_SECRET_NAME:-coder-tls}"
    log "Creating ingress TLS secret '$TLS_SECRET_NAME' from provided cert/key"
    kctl -n "$CODER_NAMESPACE" create secret tls "$TLS_SECRET_NAME" \
      --cert="$TLS_CRT_FILE" --key="$TLS_KEY_FILE" \
      --dry-run=client -o yaml | kctl apply -f -
  fi

  # Org CA bundle — mounted into Coder pods (chart 'coder.certs') and also
  # published as a ConfigMap for workspaces/templates to consume if needed.
  if [[ -n "$CA_BUNDLE_FILE" ]]; then
    log "Creating CA bundle secret + configmap 'coder-ca-bundle'"
    kctl -n "$CODER_NAMESPACE" create secret generic coder-ca-bundle \
      --from-file=ca.crt="$CA_BUNDLE_FILE" \
      --dry-run=client -o yaml | kctl apply -f -
    kctl -n "$CODER_NAMESPACE" create configmap coder-ca-bundle \
      --from-file=ca.crt="$CA_BUNDLE_FILE" \
      --dry-run=client -o yaml | kctl apply -f -
  fi
}

cmd_install() {
  need docker; need curl
  maybe_extract_bundle
  # shellcheck disable=SC1091
  source "$BUNDLE/.bundle-manifest"
  [[ -x "$BUNDLE/bin/kubectl" && -x "$BUNDLE/bin/helm" && -x "$BUNDLE/bin/coder" ]] || die "bundle bin/ is incomplete"
  kctl get nodes >/dev/null || die "kubectl cannot reach the cluster (check KUBECONFIG)"
  mkdir -p "$BUNDLE/manifests"

  # ------------------------------------------------------------- preflight
  if [[ "$INGRESS_ENABLE" == "true" || "$GATEWAY_API" == "true" ]]; then
    [[ -n "$CODER_HOSTNAME" ]] || die "CODER_HOSTNAME is required (e.g. CODER_HOSTNAME=coder.corp.internal) — it becomes the https:// access URL behind Traefik. To skip ingress entirely: INGRESS_ENABLE=false CODER_SERVICE_TYPE=NodePort."
  fi
  if [[ -z "$PG_STORAGE_CLASS" ]]; then
    kctl get storageclass -o jsonpath='{range .items[*]}{.metadata.annotations.storageclass\.kubernetes\.io/is-default-class}{"\n"}{end}' 2>/dev/null | grep -q true \
      || warn "No default StorageClass detected — PVCs may hang. Ensure the EBS CSI addon is installed, or set PG_STORAGE_CLASS/HOME_STORAGE_CLASS."
  fi

  # ------------------------------------------------------------ load images
  log "Loading images from bundle (docker load)"
  docker load -i "$BUNDLE/images/images.tar"

  # --------------------------------------------------------------- registry
  local REGISTRY_SELF_SIGNED="false"
  if [[ -z "$REGISTRY" ]]; then
    setup_bundled_registry
  else
    log "Using external registry: $REGISTRY (bundled registry skipped)"
  fi

  log "Tagging & pushing images to $REGISTRY"
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
    docker tag "$src" "$REGISTRY/$dst"
    docker push "$REGISTRY/$dst"
  done

  # -------------------------------------------------------------- namespaces
  kctl create namespace "$CNPG_NAMESPACE"  --dry-run=client -o yaml | kctl apply -f -
  kctl create namespace "$CODER_NAMESPACE" --dry-run=client -o yaml | kctl apply -f -

  # -------------------------------------------------- PKI secrets/configmaps
  create_pki_resources

  # ------------------------------------------------- node pull smoke test
  pull_smoke_test

  # ------------------------------------------------------------ CNPG operator
  log "Installing CloudNativePG operator (chart bundles its CRDs)"
  hlm upgrade --install cnpg "$BUNDLE/charts/cloudnative-pg-${CNPG_CHART_VERSION}.tgz" \
    --namespace "$CNPG_NAMESPACE" \
    --set "image.repository=${REGISTRY}/cloudnative-pg/cloudnative-pg" \
    --set "image.tag=${CNPG_OPERATOR_VERSION}" \
    --wait --timeout 10m
  kctl wait --for=condition=Established "crd/clusters.postgresql.cnpg.io" --timeout=180s

  # ---------------------------------------------------------------- postgres
  # NOTE: CloudNativePG auto-provisions its own internal PKI (CA, server and
  # replication certs) as Kubernetes secrets — nothing extra to configure.
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
  local access_url internal_url
  internal_url="http://coder.${CODER_NAMESPACE}.svc.cluster.local"
  if [[ -n "$CODER_HOSTNAME" ]]; then
    access_url="https://${CODER_HOSTNAME}"
  else
    access_url="$internal_url"
  fi

  log "Installing Coder (image: ${REGISTRY}/coder-airgap:v${CODER_VERSION}, access URL: ${access_url})"
  {
    cat <<EOF
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
    if [[ -n "$CODER_WILDCARD_HOSTNAME" ]]; then
      cat <<EOF
    - name: CODER_WILDCARD_ACCESS_URL
      value: "${CODER_WILDCARD_HOSTNAME}"
EOF
    fi
    # Org CA bundle -> mounted into Coder pods (SSL trust)
    if [[ -n "$CA_BUNDLE_FILE" ]]; then
      cat <<EOF
  certs:
    secrets:
      - name: coder-ca-bundle
        key: ca.crt
EOF
    fi
    # Traefik Ingress object (unless using Gateway API)
    if [[ "$INGRESS_ENABLE" == "true" && "$GATEWAY_API" != "true" && -n "$CODER_HOSTNAME" ]]; then
      cat <<EOF
  ingress:
    enable: true
    className: "${INGRESS_CLASS}"
    host: "${CODER_HOSTNAME}"
EOF
      [[ -n "$CODER_WILDCARD_HOSTNAME" ]] && cat <<EOF
    wildcardHost: "${CODER_WILDCARD_HOSTNAME}"
EOF
      cat <<EOF
    tls:
      enable: true
EOF
      [[ -n "$TLS_SECRET_NAME" ]] && cat <<EOF
      secretName: "${TLS_SECRET_NAME}"
EOF
    fi
  } > "$BUNDLE/manifests/coder-values.yaml"

  hlm upgrade --install coder "$BUNDLE/charts/coder-${CODER_CHART_VERSION}.tgz" \
    --namespace "$CODER_NAMESPACE" \
    --values "$BUNDLE/manifests/coder-values.yaml" \
    --wait --timeout 10m
  kctl rollout status deployment/coder -n "$CODER_NAMESPACE" --timeout=10m

  # ------------------------------------------- Gateway API HTTPRoute (opt-in)
  if [[ "$GATEWAY_API" == "true" ]]; then
    log "Creating Gateway API HTTPRoute -> gateway '${GATEWAY_NAME}' (ns: ${GATEWAY_NAMESPACE})"
    {
      cat <<EOF
apiVersion: gateway.networking.k8s.io/v1
kind: HTTPRoute
metadata:
  name: coder
  namespace: ${CODER_NAMESPACE}
spec:
  parentRefs:
    - name: ${GATEWAY_NAME}
      namespace: ${GATEWAY_NAMESPACE}
EOF
      [[ -n "$GATEWAY_SECTION_NAME" ]] && echo "      sectionName: ${GATEWAY_SECTION_NAME}"
      cat <<EOF
  hostnames:
    - "${CODER_HOSTNAME}"
  rules:
    - backendRefs:
        - name: coder
          port: 80
EOF
    } > "$BUNDLE/manifests/coder-httproute.yaml"
    kctl apply -f "$BUNDLE/manifests/coder-httproute.yaml"
    warn "Ensure the Gateway's listener allowedRoutes permits namespace '${CODER_NAMESPACE}'."
  fi

  # ------------------------------------------------ first user + templates
  log "Creating admin user '${ADMIN_USERNAME}' and pushing templates (via temporary port-forward)"
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
  log "   Namespace:      $CODER_NAMESPACE"
  log "   URL:            $access_url   (via Traefik, TLS pre-provisioned)"
  log "   Agent URL:      $internal_url (internal, no external TLS dependency)"
  log "   Registry:       https://$REGISTRY (bundled, TLS)"
  log "   Admin login:    $ADMIN_USERNAME / $ADMIN_PASSWORD"
  log "   Templates:      ubuntu-base, ubuntu-novnc  (Kubernetes Deployment + envbox)"
  log "================================================================"
  warn "CHANGE the admin password after first login (Account -> Security)."
  warn "Keep the bundled registry container running — workspace pods pull from it."
}

push_templates() {
  # shellcheck disable=SC1091
  source "$BUNDLE/.bundle-manifest"
  local coder_bin="$BUNDLE/bin/coder"
  local internal_url="http://coder.${CODER_NAMESPACE}.svc.cluster.local"
  local t inner_image
  for t in ubuntu-base ubuntu-novnc; do
    inner_image="$REGISTRY/airgap/$t:latest"
    log "Pushing template '$t' (inner image: $inner_image)"
    "$coder_bin" templates push "$t" \
      --directory "$BUNDLE/templates/$t" \
      --variable "namespace=${CODER_NAMESPACE}" \
      --variable "envbox_image=${REGISTRY}/coder/envbox:${ENVBOX_VERSION}" \
      --variable "inner_image=${inner_image}" \
      --variable "agent_url=${internal_url}" \
      --variable "home_storage_class=${HOME_STORAGE_CLASS}" \
      --message "air-gap zero-to-hero install" \
      --yes
  done
}

cmd_push_templates() {
  maybe_extract_bundle
  [[ -n "$REGISTRY" ]] || REGISTRY="$(detect_bastion_ip):${BUNDLED_REGISTRY_PORT}"
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

  download        (connected machine)   Build the offline bundle AND pack it
                                        into ${BUNDLE_NAME}.tgz automatically.
  install         (air-gapped bastion)  Auto-extract the tarball, start the
                                        bundled TLS registry, push images, run
                                        a node pull smoke test, create PKI
                                        secrets/CA bundles, install CNPG +
                                        PostgreSQL + Coder behind Traefik,
                                        create admin (admin/coder-admin), and
                                        push both workspace templates.
  push-templates  (air-gapped bastion)  Re-push templates only (requires an
                                        active 'coder' CLI session).

Key environment variables
  download : CODER_VERSION ENVBOX_VERSION CNPG_CHART_VERSION CNPG_PG_IMAGE_TAG
             TERRAFORM_VERSION TF_CODER_PROVIDER TF_K8S_PROVIDER
  install  : CODER_HOSTNAME=coder.corp.internal        (required for ingress)
             CODER_WILDCARD_HOSTNAME='*.coder.corp.internal'   (optional)
             INGRESS_CLASS=traefik | GATEWAY_API=true GATEWAY_NAME=.. GATEWAY_NAMESPACE=..
             TLS_SECRET_NAME=.. | TLS_CRT_FILE=.. TLS_KEY_FILE=..   (optional)
             CA_BUNDLE_FILE=/path/org-ca.pem                        (optional)
             REGISTRY_TLS_CRT_FILE/KEY_FILE/CA_FILE   (org-signed registry TLS
                                                       => zero node config)
             BASTION_IP BUNDLED_REGISTRY_PORT SKIP_PULL_TEST
             CODER_NAMESPACE PG_STORAGE_SIZE PG_STORAGE_CLASS HOME_STORAGE_CLASS
             ADMIN_USERNAME ADMIN_PASSWORD ADMIN_EMAIL   (default admin/coder-admin)
             REGISTRY=host:port   (only to BYPASS the bundled registry)
EOF
    exit 1
    ;;
esac
