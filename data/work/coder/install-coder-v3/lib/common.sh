# lib/common.sh — defaults, colors, logging, arg parsing, ERR trap.
# Sourced by install-coder before any other lib.

# ─── Defaults (env-overridable) ──────────────────────────────────────────────
DOMAIN="${CODER_DOMAIN:-coder.xxx}"
NAMESPACE="${CODER_NAMESPACE:-coder}"
TRAEFIK_NAMESPACE="${TRAEFIK_NAMESPACE:-traefik}"
CERT_DIR="${CERT_DIR:-$PWD/certs}"
VALUES_FILE="${VALUES_FILE:-$PWD/coder-values.yaml}"

MINIKUBE_PROFILE="${MINIKUBE_PROFILE:-minikube}"
MINIKUBE_CPUS="${MINIKUBE_CPUS:-4}"
MINIKUBE_MEMORY="${MINIKUBE_MEMORY:-8192}"
MINIKUBE_DISK="${MINIKUBE_DISK:-40g}"
MINIKUBE_DRIVER="${MINIKUBE_DRIVER:-}" # auto-detect if blank

# auto | nginx | traefik
INGRESS_CONTROLLER="${INGRESS_CONTROLLER:-auto}"

SKIP_HOSTS="${SKIP_HOSTS:-false}"
SKIP_CLI="${SKIP_CLI:-false}"
SKIP_FILEBROWSER="${SKIP_FILEBROWSER:-false}"
CLI_INSTALL_DIR="${CLI_INSTALL_DIR:-$HOME/.local/bin}"
ASSUME_YES="${ASSUME_YES:-false}"
ACTION="install"

# Image used by the in-cluster filebrowser binary host (air-gap clusters
# must have this image pre-loaded, e.g. via `minikube image load busybox:1.36`).
FILEBROWSER_HOST_IMAGE="${FILEBROWSER_HOST_IMAGE:-busybox:1.36}"

OS="$(uname -s)"

# ─── Output helpers ──────────────────────────────────────────────────────────
if [[ -t 1 ]]; then
 RED=$'\e[31m'
 GRN=$'\e[32m'
 YLW=$'\e[33m'
 BLU=$'\e[34m'
 CYN=$'\e[36m'
 BLD=$'\e[1m'
 DIM=$'\e[2m'
 RST=$'\e[0m'
else
 RED=''
 GRN=''
 YLW=''
 BLU=''
 CYN=''
 BLD=''
 DIM=''
 RST=''
fi

log() { printf "%s[*]%s %s\n" "$BLU" "$RST" "$*"; }
ok() { printf "%s[✓]%s %s\n" "$GRN" "$RST" "$*"; }
warn() { printf "%s[!]%s %s\n" "$YLW" "$RST" "$*" >&2; }
err() { printf "%s[x]%s %s\n" "$RED" "$RST" "$*" >&2; }
step() { printf "\n%s━━━ %s ━━━%s\n" "$BLD" "$*" "$RST"; }
die() {
 err "$*"
 exit 1
}

confirm() {
 $ASSUME_YES && return 0
 if [[ ! -t 0 ]]; then
  warn "Non-interactive shell; refusing destructive action without --yes"
  return 1
 fi
 read -r -p "$1 [y/N] " ans
 [[ "$ans" =~ ^[Yy]$ ]]
}

# render_template — substitute the listed vars in a template file.
# Only named vars are expanded so anything like Helm's {{ }} or shell
# command substitutions in the template stay untouched.
render_template() {
 local tpl="$1"
 [[ -f "$tpl" ]] || die "Template not found: $tpl"
 envsubst '${DOMAIN} ${NAMESPACE} ${SAFE_NAME} ${TLS_SECRET} ${CA_SECRET} ${FILEBROWSER_HOST_IMAGE}' <"$tpl"
}

# ─── Usage ───────────────────────────────────────────────────────────────────
usage() {
 cat <<EOF
${BLD}install-coder${RST} — Coder installer for Minikube.
${BLD}USAGE${RST}
    install-coder [OPTIONS]

${BLD}OPTIONS${RST}
    --domain <name>             Domain to use for Coder           (default: ${DOMAIN})
    --namespace <ns>            Kubernetes namespace              (default: ${NAMESPACE})
    --cpus <n>                  Minikube CPUs                     (default: ${MINIKUBE_CPUS})
    --memory <mb>               Minikube memory in MB             (default: ${MINIKUBE_MEMORY})
    --disk <size>               Minikube disk size, e.g. 40g      (default: ${MINIKUBE_DISK})
    --driver <drv>              Minikube driver (docker, qemu2, …; auto-detected)
    --cert-dir <path>           Where to store certs              (default: ${CERT_DIR})
    --ingress-controller <mode> auto | nginx | traefik            (default: ${INGRESS_CONTROLLER})
                                  auto    — use ingress-nginx if it's already running,
                                            otherwise install Traefik
                                  nginx   — use existing ingress-nginx (fail if absent)
                                  traefik — install Traefik (fails if 80/443 are taken)
    --skip-hosts                Don't modify /etc/hosts
    --skip-cli                  Don't install the coder CLI
    --skip-filebrowser          Don't deploy the in-cluster filebrowser host
    --cli-install-dir <path>    Where to drop the coder CLI       (default: ${CLI_INSTALL_DIR})
    --filebrowser-binary <path> Path to filebrowser binary        (default: ${FILEBROWSER_BINARY})
    -y, --yes                   Skip confirmation prompts (for CI)
    --status                    Show component status and exit
    --uninstall                 Remove everything (releases, namespaces, /etc/hosts entry)
    -h, --help                  Show this help

${BLD}LAYOUT${RST}
    install-coder       this orchestrator
    lib/                step-by-step shell modules
    manifests/          Kubernetes manifests / templates
    filebrowser         the filebrowser binary (air-gap; place alongside this script)

${BLD}EXAMPLES${RST}
    install-coder
    install-coder --domain coder.local --cpus 6 --memory 12288
    CODER_DOMAIN=dev.coder.test install-coder
    install-coder --uninstall -y
EOF
 exit 0
}

# ─── Arg parsing ─────────────────────────────────────────────────────────────
parse_args() {
 while [[ $# -gt 0 ]]; do
  case "$1" in
  --domain)
   DOMAIN="$2"
   shift 2
   ;;
  --namespace)
   NAMESPACE="$2"
   shift 2
   ;;
  --cpus)
   MINIKUBE_CPUS="$2"
   shift 2
   ;;
  --memory)
   MINIKUBE_MEMORY="$2"
   shift 2
   ;;
  --disk)
   MINIKUBE_DISK="$2"
   shift 2
   ;;
  --driver)
   MINIKUBE_DRIVER="$2"
   shift 2
   ;;
  --cert-dir)
   CERT_DIR="$2"
   shift 2
   ;;
  --ingress-controller)
   INGRESS_CONTROLLER="$2"
   shift 2
   ;;
  --skip-hosts)
   SKIP_HOSTS=true
   shift
   ;;
  --skip-cli)
   SKIP_CLI=true
   shift
   ;;
  --skip-filebrowser)
   SKIP_FILEBROWSER=true
   shift
   ;;
  --cli-install-dir)
   CLI_INSTALL_DIR="$2"
   shift 2
   ;;
  --filebrowser-binary)
   FILEBROWSER_BINARY="$2"
   shift 2
   ;;
  -y | --yes)
   ASSUME_YES=true
   shift
   ;;
  --status)
   ACTION="status"
   shift
   ;;
  --uninstall)
   ACTION="uninstall"
   shift
   ;;
  -h | --help) usage ;;
  *) die "Unknown option: $1 (try --help)" ;;
  esac
 done

 # Derived values (export so envsubst sees them in render_template)
 SAFE_NAME="${DOMAIN//./-}"
 TLS_SECRET="${SAFE_NAME}-tls"
 CA_SECRET="${SAFE_NAME}-ca"
 export DOMAIN NAMESPACE SAFE_NAME TLS_SECRET CA_SECRET FILEBROWSER_HOST_IMAGE

 case "$INGRESS_CONTROLLER" in
 auto | nginx | traefik) ;;
 *) die "--ingress-controller must be one of: auto, nginx, traefik (got: ${INGRESS_CONTROLLER})" ;;
 esac
}

# ─── Banner ──────────────────────────────────────────────────────────────────
banner() {
 cat <<EOF
${CYN}${BLD}
╔══════════════════════════════════════════════════════════╗
║         Coder Zero-to-Hero • Minikube Installer          ║
╚══════════════════════════════════════════════════════════╝${RST}
  domain     : ${BLD}${DOMAIN}${RST}
  namespace  : ${NAMESPACE}
  ingress    : ${INGRESS_CONTROLLER}  ${DIM}(auto = reuse nginx if present, else install Traefik)${RST}
  cert dir   : ${CERT_DIR}
  manifests  : ${MANIFESTS_DIR}
  minikube   : profile=${MINIKUBE_PROFILE} cpus=${MINIKUBE_CPUS} mem=${MINIKUBE_MEMORY}MB driver=${MINIKUBE_DRIVER:-auto}
EOF
}

# ─── Error trap ──────────────────────────────────────────────────────────────
on_error() {
 local rc=$? line="$1"
 err "Failed at line ${line} (exit ${rc})"
 printf "\n%sQuick diagnostics:%s\n" "$YLW" "$RST"
 cat <<EOF >&2
    kubectl get pods -n ${NAMESPACE}
    kubectl describe pods -n ${NAMESPACE}
    kubectl logs -n ${NAMESPACE} -l app.kubernetes.io/name=coder --tail=100
    kubectl get events -n ${NAMESPACE} --sort-by=.lastTimestamp | tail -20
    minikube logs --problems
EOF
 warn "After fixing, just re-run this script — every step is idempotent."
 exit "$rc"
}
trap 'on_error $LINENO' ERR
