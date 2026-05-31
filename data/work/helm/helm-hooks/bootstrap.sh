#!/usr/bin/env bash
# =============================================================================
# bootstrap.sh — provision the auth-shim + Traefik mTLS stack end-to-end.
#
# Idempotent: safe to re-run. Each `kubectl create secret` is piped through
# `apply` so re-runs update in place rather than failing on AlreadyExists.
# Each `helm install` is actually `helm upgrade --install`.
#
# Usage:
#   ./bootstrap.sh                # uses defaults below
#   TRAEFIK_NAMESPACE=edge ./bootstrap.sh
#   ./bootstrap.sh --skip-verify  # don't curl the test endpoint at the end
#
# All inputs can be overridden via env var. See the CONFIG block below.
# =============================================================================
set -euo pipefail

# ---------- CONFIG (override via env) ----------------------------------------
TRAEFIK_NAMESPACE="${TRAEFIK_NAMESPACE:-traefik-auth}"
AUTH_SHIM_NAMESPACE="${AUTH_SHIM_NAMESPACE:-auth-shim}"

# Cert material
CERT_DIR="${CERT_DIR:-./certs}"
SERVER_CRT="${SERVER_CRT:-${CERT_DIR}/server.crt}"
SERVER_KEY="${SERVER_KEY:-${CERT_DIR}/server.key}"
CLIENT_CA="${CLIENT_CA:-${CERT_DIR}/client-ca.crt}"
VALIDATOR_CA="${VALIDATOR_CA:-${CERT_DIR}/validator-ca.crt}"

# Test client cert (optional — used only for the final curl check)
CLIENT_CRT="${CLIENT_CRT:-${CERT_DIR}/client.crt}"
CLIENT_KEY="${CLIENT_KEY:-${CERT_DIR}/client.key}"
INGRESS_HOST="${INGRESS_HOST:-api.example.com}"

# Inputs
TRAEFIK_VALUES="${TRAEFIK_VALUES:-./traefik-values.yaml}"
AUTH_SHIM_CHART="${AUTH_SHIM_CHART:-./auth-shim}"
TLSSTORE_YAML="${TLSSTORE_YAML:-./tlsstore.yaml}"
INGRESSROUTE_YAML="${INGRESSROUTE_YAML:-./ingressroute.yaml}"

# Helm
TRAEFIK_CHART_VERSION="${TRAEFIK_CHART_VERSION:-}" # empty = latest
HELM_TIMEOUT="${HELM_TIMEOUT:-5m}"

SKIP_VERIFY=false
while [[ $# -gt 0 ]]; do
 case "$1" in
  --skip-verify)
   SKIP_VERIFY=true
   shift
   ;;
  -h | --help)
   sed -n '2,20p' "$0"
   exit 0
   ;;
  *)
   echo "Unknown arg: $1" >&2
   exit 2
   ;;
 esac
done

# ---------- helpers ----------------------------------------------------------
if [[ -t 1 ]]; then
 C_INFO='\e[36m'
 C_OK='\e[32m'
 C_ERR='\e[31m'
 C_OFF='\e[0m'
else
 C_INFO=''
 C_OK=''
 C_ERR=''
 C_OFF=''
fi

log() { printf "${C_INFO}▶ %s${C_OFF}\n" "$*"; }
ok() { printf "${C_OK}✓ %s${C_OFF}\n" "$*"; }
die() {
 printf "${C_ERR}✗ %s${C_OFF}\n" "$*" >&2
 exit 1
}

require_cmd() { command -v "$1" >/dev/null 2>&1 || die "missing dependency: $1"; }
require_file() { [[ -f "$1" ]] || die "file not found: $1"; }

ensure_ns() {
 kubectl create namespace "$1" --dry-run=client -o yaml | kubectl apply -f - >/dev/null
}

apply_tls_secret() {
 local name="$1" ns="$2" cert="$3" key="$4"
 kubectl create secret tls "$name" \
  --cert="$cert" --key="$key" -n "$ns" \
  --dry-run=client -o yaml | kubectl apply -f - >/dev/null
}

apply_generic_secret_from_file() {
 local name="$1" ns="$2" key="$3" file="$4"
 kubectl create secret generic "$name" \
  --from-file="${key}=${file}" -n "$ns" \
  --dry-run=client -o yaml | kubectl apply -f - >/dev/null
}

wait_for_crd() {
 local crd="$1"
 # The CRD itself may not yet exist at the moment we ask — wait briefly.
 local i=0
 until kubectl get "crd/${crd}" >/dev/null 2>&1; do
  ((i++))
  [[ $i -gt 60 ]] && die "CRD ${crd} did not appear within 60s"
  sleep 1
 done
 kubectl wait --for=condition=Established --timeout=60s "crd/${crd}" >/dev/null
}

# ---------- 0. pre-flight ----------------------------------------------------
log "pre-flight"
require_cmd kubectl
require_cmd helm
require_file "$SERVER_CRT"
require_file "$SERVER_KEY"
require_file "$CLIENT_CA"
require_file "$VALIDATOR_CA"
require_file "$TRAEFIK_VALUES"
require_file "$TLSSTORE_YAML"
require_file "$INGRESSROUTE_YAML"

[[ -d "$AUTH_SHIM_CHART" ]] || die "chart dir not found: $AUTH_SHIM_CHART"
kubectl version --client >/dev/null || die "kubectl can't reach a cluster (check kubeconfig)"
kubectl auth can-i create namespaces >/dev/null ||
 die "current context lacks permission to create namespaces"
ok "pre-flight passed"

# ---------- 1. namespaces ----------------------------------------------------
log "ensuring namespaces: $TRAEFIK_NAMESPACE, $AUTH_SHIM_NAMESPACE"
ensure_ns "$TRAEFIK_NAMESPACE"
ensure_ns "$AUTH_SHIM_NAMESPACE"

# ---------- 2. secrets -------------------------------------------------------
log "applying secret traefik-ingress-tls (-n $TRAEFIK_NAMESPACE)"
apply_tls_secret traefik-ingress-tls "$TRAEFIK_NAMESPACE" "$SERVER_CRT" "$SERVER_KEY"

log "applying secret client-ca-bundle (-n $AUTH_SHIM_NAMESPACE)"
apply_generic_secret_from_file client-ca-bundle "$AUTH_SHIM_NAMESPACE" tls.crt "$CLIENT_CA"

log "applying secret validator-tls-secret (-n $AUTH_SHIM_NAMESPACE)"
apply_generic_secret_from_file validator-tls-secret "$AUTH_SHIM_NAMESPACE" tls.crt "$VALIDATOR_CA"
ok "secrets in place"

# ---------- 3. Traefik -------------------------------------------------------
log "ensuring traefik helm repo"
helm repo add traefik https://traefik.github.io/charts >/dev/null 2>&1 || true
helm repo update traefik >/dev/null

log "installing/upgrading Traefik"
traefik_args=(
 upgrade --install traefik traefik/traefik
 -n "$TRAEFIK_NAMESPACE"
 -f "$TRAEFIK_VALUES"
 --wait --timeout "$HELM_TIMEOUT"
)
[[ -n "$TRAEFIK_CHART_VERSION" ]] && traefik_args+=(--version "$TRAEFIK_CHART_VERSION")
helm "${traefik_args[@]}"

log "waiting for Traefik CRDs"
for crd in middlewares.traefik.io \
 tlsstores.traefik.io \
 tlsoptions.traefik.io \
 ingressroutes.traefik.io \
 serverstransports.traefik.io; do
 wait_for_crd "$crd"
done
ok "Traefik ready"

# ---------- 4. auth-shim -----------------------------------------------------
log "installing/upgrading auth-shim"
helm upgrade --install auth-shim "$AUTH_SHIM_CHART" \
 -n "$AUTH_SHIM_NAMESPACE" \
 --set traefik.tlsOption.caSecretNames='{client-ca-bundle}' \
 --wait --timeout "$HELM_TIMEOUT"
ok "auth-shim ready"

# ---------- 5. TLSStore + IngressRoute --------------------------------------
log "applying TLSStore and IngressRoute"
kubectl apply -f "$TLSSTORE_YAML"
kubectl apply -f "$INGRESSROUTE_YAML"

# ---------- 6. report --------------------------------------------------------
log "resources in $AUTH_SHIM_NAMESPACE"
kubectl get tlsoption,middleware -n "$AUTH_SHIM_NAMESPACE"

log "resources in $TRAEFIK_NAMESPACE"
kubectl get ingressroute,tlsstore -n "$TRAEFIK_NAMESPACE"

# ---------- 7. verify --------------------------------------------------------
if [[ "$SKIP_VERIFY" == "true" ]]; then
 ok "bootstrap complete (verify skipped)"
 exit 0
fi

if [[ -f "$CLIENT_CRT" && -f "$CLIENT_KEY" ]]; then
 log "test request to https://${INGRESS_HOST}/"
 if curl --fail --silent --show-error \
  --connect-timeout 5 --max-time 15 \
  --cert "$CLIENT_CRT" --key "$CLIENT_KEY" \
  "https://${INGRESS_HOST}/" >/dev/null; then
  ok "test request succeeded"
 else
  die "test request failed — check 'kubectl logs -n ${AUTH_SHIM_NAMESPACE} deploy/auth-shim'"
 fi
else
 log "no client cert at $CLIENT_CRT — skipping test request"
fi

ok "bootstrap complete"
