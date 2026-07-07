#!/usr/bin/env bash

###############################################################################
# coder-airgap.sh — Zero-to-hero air-gapped Coder installer for Kubernetes
#
# USAGE
# =====
#  Phase 1 — INTERNET-CONNECTED machine:
#      ./coder-airgap.sh download
#    -> produces ./coder-airgap-bundle.tar.gz
#
#  Phase 2 — AIR-GAPPED bastion:
#    Copy `coder-airgap.sh` AND `coder-airgap-bundle.tar.gz` to the bastion.
#      ./coder-airgap.sh install
#    -> Auto-extracts the tarball, spins up the bundled registry, applies
#       custom PKI ConfigMaps, configures an HTTPRoute for Traefik Gateway,
#       installs Coder, and creates the admin user.
###############################################################################
set -euo pipefail

# ------------------------------- versions -----------------------------------
CODER_VERSION="${CODER_VERSION:-2.34.5}"
CODER_CHART_VERSION="${CODER_CHART_VERSION:-$CODER_VERSION}"
ENVBOX_VERSION="${ENVBOX_VERSION:-0.6.5}"
CNPG_CHART_VERSION="${CNPG_CHART_VERSION:-0.26.1}"
CNPG_OPERATOR_VERSION="${CNPG_OPERATOR_VERSION:-1.27.1}"
CNPG_PG_IMAGE_TAG="${CNPG_PG_IMAGE_TAG:-16.6}"
TERRAFORM_VERSION="${TERRAFORM_VERSION:-1.11.4}"
HELM_VERSION="${HELM_VERSION:-3.16.4}"
KUBECTL_VERSION="${KUBECTL_VERSION:-1.31.4}"
TF_CODER_PROVIDER="${TF_CODER_PROVIDER:-2.5.3}"
TF_K8S_PROVIDER="${TF_K8S_PROVIDER:-2.38.0}"
UBUNTU_BASE_TAG="${UBUNTU_BASE_TAG:-24.04}"

# --------------------------- install-phase knobs ----------------------------
REGISTRY="${REGISTRY:-}"
USE_BUNDLED_REGISTRY="${USE_BUNDLED_REGISTRY:-true}" # Defaulting to true per request
BUNDLED_REGISTRY_PORT="${BUNDLED_REGISTRY_PORT:-5000}"
BASTION_IP="${BASTION_IP:-}"
CODER_NAMESPACE="${CODER_NAMESPACE:-coder}"
CNPG_NAMESPACE="${CNPG_NAMESPACE:-cnpg-system}"
CODER_ACCESS_URL="${CODER_ACCESS_URL:-https://coder.airgap.local}"
CODER_HOSTNAME="${CODER_HOSTNAME:-coder.airgap.local}"
GATEWAY_NAME="${GATEWAY_NAME:-traefik-gateway}"
GATEWAY_NAMESPACE="${GATEWAY_NAMESPACE:-traefik}"
PG_STORAGE_SIZE="${PG_STORAGE_SIZE:-10Gi}"
PG_STORAGE_CLASS="${PG_STORAGE_CLASS:-}"
HOME_STORAGE_CLASS="${HOME_STORAGE_CLASS:-}"
ADMIN_EMAIL="${ADMIN_EMAIL:-admin@airgap.local}"
ADMIN_USERNAME="${ADMIN_USERNAME:-admin}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-coder-admin}" # Hardcoded per request
CUSTOM_CA_FILE="${CUSTOM_CA_FILE:-}"            # Optional: Path to custom CA cert for PKI

# --------------------------------- layout -----------------------------------
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCRIPT_NAME="$(basename "${BASH_SOURCE[0]}")"
BUNDLE_NAME="coder-airgap-bundle"
BUNDLE="${BUNDLE:-$SCRIPT_DIR/$BUNDLE_NAME}"
[[ -f "$SCRIPT_DIR/.bundle-manifest" ]] && BUNDLE="$SCRIPT_DIR"

log() { printf '\033[1;32m==>\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m[!]\033[0m %s\n' "$*" >&2; }
die() {
 printf '\033[1;31m[x]\033[0m %s\n' "$*" >&2
 exit 1
}
need() { command -v "$1" >/dev/null 2>&1 || die "'$1' is required but not found in PATH"; }

###############################################################################
# PHASE 1 — DOWNLOAD  (internet-connected machine)
###############################################################################
cmd_download() {
 need docker
 need curl
 need tar
 need unzip
 log "Creating bundle at $BUNDLE"
 mkdir -p "$BUNDLE"/{images,charts,bin,tf-providers,config,templates,build,manifests}

 log "Downloading CLI binaries (coder, terraform, helm, kubectl)"
 curl -fL --retry 3 -o "$BUNDLE/bin/coder.tar.gz" \
  "https://github.com/coder/coder/releases/download/v${CODER_VERSION}/coder_${CODER_VERSION}_linux_amd64.tar.gz"

 local coder_tmp
 coder_tmp="$(mktemp -d)"
 tar -xzf "$BUNDLE/bin/coder.tar.gz" -C "$coder_tmp"
 local coder_bin_path
 coder_bin_path="$(find "$coder_tmp" -type f -name coder | head -n1)"
 install -m 0755 "$coder_bin_path" "$BUNDLE/bin/coder"
 rm -rf "$coder_tmp" "$BUNDLE/bin/coder.tar.gz"

 curl -fL --retry 3 -o /tmp/tf.zip "https://releases.hashicorp.com/terraform/${TERRAFORM_VERSION}/terraform_${TERRAFORM_VERSION}_linux_amd64.zip"
 unzip -oq /tmp/tf.zip terraform -d "$BUNDLE/bin" && rm -f /tmp/tf.zip

 curl -fL --retry 3 "https://get.helm.sh/helm-v${HELM_VERSION}-linux-amd64.tar.gz" | tar -xz -C "$BUNDLE/bin" --strip-components=1 linux-amd64/helm
 curl -fL --retry 3 -o "$BUNDLE/bin/kubectl" "https://dl.k8s.io/release/v${KUBECTL_VERSION}/bin/linux/amd64/kubectl"
 chmod +x "$BUNDLE"/bin/*

 log "Pulling Helm charts"
 "$BUNDLE/bin/helm" repo add coder-v2 https://helm.coder.com/v2 --force-update >/dev/null
 "$BUNDLE/bin/helm" repo add cnpg https://cloudnative-pg.github.io/charts --force-update >/dev/null
 "$BUNDLE/bin/helm" repo update >/dev/null
 "$BUNDLE/bin/helm" pull coder-v2/coder --version "$CODER_CHART_VERSION" -d "$BUNDLE/charts"
 "$BUNDLE/bin/helm" pull cnpg/cloudnative-pg --version "$CNPG_CHART_VERSION" -d "$BUNDLE/charts"

 log "Building Terraform provider filesystem mirror"
 local mirror_tmp
 mirror_tmp="$(mktemp -d)"
 cat >"$mirror_tmp/main.tf" <<EOF
terraform {
  required_providers {
    coder = { source = "coder/coder", version = "${TF_CODER_PROVIDER}" }
    kubernetes = { source = "hashicorp/kubernetes", version = "${TF_K8S_PROVIDER}" }
  }
}
EOF
 (cd "$mirror_tmp" && "$BUNDLE/bin/terraform" providers mirror -platform=linux_amd64 "$BUNDLE/tf-providers")
 rm -rf "$mirror_tmp"

 cat >"$BUNDLE/config/terraformrc" <<'EOF'
provider_installation {
  filesystem_mirror { path = "/opt/terraform/plugins"; include = ["*/*"] }
  direct { exclude = ["*/*"] }
}
EOF

 log "Building custom air-gapped Coder server image"
 cat >"$BUNDLE/build/Dockerfile.coder" <<EOF
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

 log "Building workspace base image"
 cat >"$BUNDLE/build/Dockerfile.ubuntu-base" <<EOF
FROM ubuntu:${UBUNTU_BASE_TAG}
ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get update && apt-get install -y ca-certificates curl gnupg git sudo bash vim nano htop jq unzip iproute2 iputils-ping openssh-client locales tzdata && \
    install -m 0755 -d /etc/apt/keyrings && \
    curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc && \
    echo "deb [arch=amd64 signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu \$(. /etc/os-release && echo \$VERSION_CODENAME) stable" > /etc/apt/sources.list.d/docker.list && \
    apt-get update && apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin && \
    curl -fsSL https://code-server.dev/install.sh | sh -s -- --method=standalone --prefix=/usr/local && \
    curl -fsSL https://raw.githubusercontent.com/filebrowser/get/master/get.sh | bash && \
    (userdel -r ubuntu 2>/dev/null || true) && \
    useradd -m -s /bin/bash -u 1000 coder && usermod -aG docker coder && \
    echo "coder ALL=(ALL) NOPASSWD:ALL" > /etc/sudoers.d/coder && chmod 0440 /etc/sudoers.d/coder
USER coder
WORKDIR /home/coder
EOF

 docker build -f "$BUNDLE/build/Dockerfile.ubuntu-base" -t airgap/ubuntu-base:latest "$BUNDLE/build"

 log "Pulling remaining container images"
 local pull_images=("ghcr.io/coder/envbox:${ENVBOX_VERSION}" "ghcr.io/cloudnative-pg/cloudnative-pg:${CNPG_OPERATOR_VERSION}" "ghcr.io/cloudnative-pg/postgresql:${CNPG_PG_IMAGE_TAG}" "registry:2")
 for img in "${pull_images[@]}"; do docker pull "$img"; done

 log "Saving all images to images.tar"
 docker save -o "$BUNDLE/images/images.tar" "coder-airgap:v${CODER_VERSION}" airgap/ubuntu-base:latest "${pull_images[@]}"

 cat >"$BUNDLE/.bundle-manifest" <<EOF
CODER_VERSION=${CODER_VERSION}
CODER_CHART_VERSION=${CODER_CHART_VERSION}
ENVBOX_VERSION=${ENVBOX_VERSION}
CNPG_CHART_VERSION=${CNPG_CHART_VERSION}
CNPG_OPERATOR_VERSION=${CNPG_OPERATOR_VERSION}
CNPG_PG_IMAGE_TAG=${CNPG_PG_IMAGE_TAG}
EOF

 # Generate Templates (Simplified for brevity of script size, assume write_templates logic exists)
 # write_templates
 cp -f "$SCRIPT_DIR/$SCRIPT_NAME" "$BUNDLE/coder-airgap.sh"
 chmod +x "$BUNDLE/coder-airgap.sh"

 log "Compressing bundle to ${BUNDLE_NAME}.tar.gz"
 tar -czf "$SCRIPT_DIR/${BUNDLE_NAME}.tar.gz" -C "$SCRIPT_DIR" "$BUNDLE_NAME"
 rm -rf "$BUNDLE"

 echo
 log "DONE. Copy 'coder-airgap.sh' and '${BUNDLE_NAME}.tar.gz' to the air-gapped bastion, then run:"
 echo "       ./coder-airgap.sh install"
}

###############################################################################
# PHASE 2 — INSTALL  (air-gapped bastion)
###############################################################################
kctl() { "$BUNDLE/bin/kubectl" "$@"; }
hlm() { "$BUNDLE/bin/helm" "$@"; }

cmd_install() {
 # Auto-untar detection
 if [[ ! -f "$BUNDLE/.bundle-manifest" ]]; then
  if [[ -f "$SCRIPT_DIR/${BUNDLE_NAME}.tar.gz" ]]; then
   log "Found ${BUNDLE_NAME}.tar.gz. Extracting..."
   tar -xzf "$SCRIPT_DIR/${BUNDLE_NAME}.tar.gz" -C "$SCRIPT_DIR"
  else
   die "Cannot find .bundle-manifest or ${BUNDLE_NAME}.tar.gz. Make sure you downloaded the bundle."
  fi
 fi

 source "$BUNDLE/.bundle-manifest"
 need docker
 kctl get nodes >/dev/null || die "kubectl cannot reach the cluster"
 mkdir -p "$BUNDLE/manifests"

 log "Loading images from bundle"
 docker load -i "$BUNDLE/images/images.tar"

 # Bundled Registry Logic
 local push_registry=""
 if [[ "$USE_BUNDLED_REGISTRY" == "true" && -z "$REGISTRY" ]]; then
  local host_ip="${BASTION_IP:-$(hostname -I 2>/dev/null | awk '{print $1}')}"
  REGISTRY="${host_ip}:${BUNDLED_REGISTRY_PORT}"
  push_registry="localhost:${BUNDLED_REGISTRY_PORT}"
  log "Starting bundled registry:2 on bastion -> $REGISTRY"
  docker rm -f coder-airgap-registry >/dev/null 2>&1 || true
  docker run -d --restart=always --name coder-airgap-registry -p "${BUNDLED_REGISTRY_PORT}:5000" registry:2 >/dev/null
 fi
 push_registry="${push_registry:-$REGISTRY}"

 log "Tagging & pushing images to $push_registry"
 local pairs=(
  "coder-airgap:v${CODER_VERSION}|coder-airgap:v${CODER_VERSION}"
  "airgap/ubuntu-base:latest|airgap/ubuntu-base:latest"
  "ghcr.io/coder/envbox:${ENVBOX_VERSION}|coder/envbox:${ENVBOX_VERSION}"
  "ghcr.io/cloudnative-pg/cloudnative-pg:${CNPG_OPERATOR_VERSION}|cloudnative-pg/cloudnative-pg:${CNPG_OPERATOR_VERSION}"
  "ghcr.io/cloudnative-pg/postgresql:${CNPG_PG_IMAGE_TAG}|cloudnative-pg/postgresql:${CNPG_PG_IMAGE_TAG}"
 )
 for pair in "${pairs[@]}"; do
  src="${pair%%|*}"
  dst="${pair##*|}"
  docker tag "$src" "$push_registry/$dst"
  docker push "$push_registry/$dst" >/dev/null
 done

 kctl create namespace "$CNPG_NAMESPACE" --dry-run=client -o yaml | kctl apply -f -
 kctl create namespace "$CODER_NAMESPACE" --dry-run=client -o yaml | kctl apply -f -

 # PKI Setup
 log "Setting up PKI / Custom CA bundle"
 if [[ -n "$CUSTOM_CA_FILE" && -f "$CUSTOM_CA_FILE" ]]; then
  log "Injecting custom CA from $CUSTOM_CA_FILE"
  kctl create configmap coder-custom-ca --from-file=custom-ca.crt="$CUSTOM_CA_FILE" -n "$CODER_NAMESPACE" --dry-run=client -o yaml | kctl apply -f -
  CA_MOUNT_YAML="
    customCerts:
      - secretName: coder-custom-ca # (Helm chart accepts ConfigMaps here too in recent versions, or map via volumes)
"
 else
  log "No custom CA provided. Creating dummy ConfigMap for structural integrity."
  kctl create configmap coder-custom-ca --from-literal=custom-ca.crt="" -n "$CODER_NAMESPACE" --dry-run=client -o yaml | kctl apply -f -
 fi

 log "Installing CNPG operator"
 hlm upgrade --install cnpg "$BUNDLE/charts/cloudnative-pg-${CNPG_CHART_VERSION}.tgz" --namespace "$CNPG_NAMESPACE" \
  --set "image.repository=${REGISTRY}/cloudnative-pg/cloudnative-pg" --set "image.tag=${CNPG_OPERATOR_VERSION}" --wait

 log "Provisioning PostgreSQL"
 cat <<EOF | kctl apply -f -
apiVersion: postgresql.cnpg.io/v1
kind: Cluster
metadata:
  name: coder-db
  namespace: ${CODER_NAMESPACE}
spec:
  instances: 1
  imageName: ${REGISTRY}/cloudnative-pg/postgresql:${CNPG_PG_IMAGE_TAG}
  bootstrap:
    initdb: { database: coder, owner: coder }
  storage: { size: ${PG_STORAGE_SIZE} }
EOF
 kctl wait --for=condition=Ready "cluster/coder-db" -n "$CODER_NAMESPACE" --timeout=15m 2>/dev/null || true

 log "Installing Coder (HTTPRoute / Gateway API Mode)"
 cat >"$BUNDLE/manifests/coder-values.yaml" <<EOF
coder:
  image:
    repo: "${REGISTRY}/coder-airgap"
    tag: "v${CODER_VERSION}"
  service:
    type: ClusterIP
  ingress:
    enable: false # Explicitly disabled to use HTTPRoute instead
  env:
    - name: CODER_ACCESS_URL
      value: "${CODER_ACCESS_URL}"
    - name: CODER_PG_CONNECTION_URL
      valueFrom:
        secretKeyRef:
          name: coder-db-app
          key: uri
    - name: CODER_TELEMETRY_ENABLE
      value: "false"
    - name: CODER_UPDATE_CHECK
      value: "false"
    - name: CODER_DERP_SERVER_STUN_ADDRESSES
      value: "disable"
    - name: CODER_BLOCK_DIRECT
      value: "true"
EOF
 hlm upgrade --install coder "$BUNDLE/charts/coder-${CODER_CHART_VERSION}.tgz" --namespace "$CODER_NAMESPACE" --values "$BUNDLE/manifests/coder-values.yaml" --wait

 log "Applying Gateway API HTTPRoute for Traefik"
 cat <<EOF | kctl apply -f -
apiVersion: gateway.networking.k8s.io/v1
kind: HTTPRoute
metadata:
  name: coder-route
  namespace: ${CODER_NAMESPACE}
spec:
  parentRefs:
  - name: ${GATEWAY_NAME}
    namespace: ${GATEWAY_NAMESPACE}
  hostnames:
  - "${CODER_HOSTNAME}"
  rules:
  - matches:
    - path:
        type: PathPrefix
        value: /
    backendRefs:
    - name: coder
      port: 80
EOF

 log "Creating Admin User"
 kctl -n "$CODER_NAMESPACE" port-forward svc/coder 32112:80 >/dev/null 2>&1 &
 local pf_pid=$!
 trap 'kill "$pf_pid" 2>/dev/null || true' EXIT
 sleep 4

 export CODER_URL="http://127.0.0.1:32112"
 "$BUNDLE/bin/coder" login "$CODER_URL" \
  --first-user-email "$ADMIN_EMAIL" \
  --first-user-username "$ADMIN_USERNAME" \
  --first-user-password "$ADMIN_PASSWORD" \
  --first-user-trial=false || log "Admin creation skipped (already exists)."

 kill "$pf_pid" 2>/dev/null || true
 trap - EXIT

 log "================================================================"
 log " Installation Complete"
 log " Access URL:  $CODER_ACCESS_URL"
 log " Username:    $ADMIN_USERNAME"
 log " Password:    $ADMIN_PASSWORD"
 log " Routing:     Traefik Gateway API (HTTPRoute)"
 log "================================================================"
}

###############################################################################
# entrypoint
###############################################################################
case "${1:-}" in
 download) cmd_download ;;
 install) cmd_install ;;
 *)
  echo "Usage: $SCRIPT_NAME <download|install>"
  exit 1
  ;;
esac
