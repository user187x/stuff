# lib/preflight.sh — verify required tools are installed and detect driver.

preflight() {
 step "Pre-flight checks"

 local missing=()
 # envsubst needed to render manifests/*.tpl files.
 for cmd in minikube kubectl helm openssl curl tar envsubst; do
  command -v "$cmd" >/dev/null 2>&1 || missing+=("$cmd")
 done
 if ((${#missing[@]} > 0)); then
  err "Missing required commands: ${missing[*]}"
  case "$OS" in
  Darwin) warn "Install with: brew install ${missing[*]}" ;;
  Linux) warn "Install via your package manager (apt/dnf/pacman). 'envsubst' is in the gettext package." ;;
  esac
  exit 1
 fi
 ok "All required tools present (minikube, kubectl, helm, openssl, curl, envsubst)"

 # Auto-detect driver if not specified.
 if [[ -z "$MINIKUBE_DRIVER" ]]; then
  if command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
   MINIKUBE_DRIVER="docker"
  fi
 fi
 if [[ "$MINIKUBE_DRIVER" == "docker" ]] && ! docker info >/dev/null 2>&1; then
  die "docker driver selected but Docker is not running. Start Docker Desktop and retry."
 fi
 ok "Driver: ${MINIKUBE_DRIVER:-(minikube default)}"

 if [[ "$OS" == "Darwin" && "$MINIKUBE_DRIVER" == "docker" ]]; then
  warn "macOS + docker driver: traffic uses an internal Docker network."
  warn "If the final health check fails, either:"
  warn "  • run 'minikube tunnel' in another terminal, or"
  warn "  • re-create with --driver=qemu2 (recommended for Apple Silicon)"
 fi
}
