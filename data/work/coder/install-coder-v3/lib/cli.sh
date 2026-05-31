# lib/cli.sh — download the Coder CLI from the freshly installed Coder server.
# Sets two globals consumed by summary.sh:
#   CLI_PATH        — full path to the installed binary, or empty if skipped/failed
#   CLI_NEEDS_PATH  — 1 if CLI_INSTALL_DIR is not on $PATH

CLI_PATH=""
CLI_NEEDS_PATH=0

install_cli() {
 step "Coder CLI"
 if $SKIP_CLI; then
  warn "Skipping CLI install (--skip-cli)"
  return
 fi

 local arch_raw os_lc arch target_ip url
 arch_raw="$(uname -m)"
 os_lc="$(echo "$OS" | tr '[:upper:]' '[:lower:]')"

 case "$arch_raw" in
 x86_64 | amd64) arch=amd64 ;;
 aarch64 | arm64) arch=arm64 ;;
 armv7l) arch=armv7 ;;
 *)
  warn "Unknown CPU arch '${arch_raw}' — skipping CLI install"
  return
  ;;
 esac
 case "$os_lc" in
 linux | darwin) ;;
 *)
  warn "Unsupported OS '${OS}' for auto CLI install — skipping"
  return
  ;;
 esac

 target_ip=$(minikube ip -p "$MINIKUBE_PROFILE")
 url="https://${DOMAIN}/bin/coder-${os_lc}-${arch}"

 mkdir -p "$CLI_INSTALL_DIR"
 local tmpdir
 tmpdir=$(mktemp -d)
 trap 'rm -rf "$tmpdir"' RETURN

 log "Downloading Coder CLI from its newly provisioned installation at ${url}…"

 # Coder's /healthz passes before the binary cache finishes extracting,
 # so retry a few times with backoff.
 local success=false
 for i in {1..6}; do
  if curl --cacert "$CERT_DIR/ca-cert.pem" --resolve "${DOMAIN}:443:${target_ip}" \
   -fsSL --max-time 60 "$url" -o "$tmpdir/coder"; then
   success=true
   break
  fi
  warn "Download failed (server may still be unpacking binaries). Retrying in 10s... ($i/6)"
  sleep 10
 done

 if ! $success; then
  err "Download completely failed from ${url}"
  warn "Install manually:  curl -L https://coder.com/install.sh | sh"
  return
 fi

 chmod +x "$tmpdir/coder"
 install -m 0755 "$tmpdir/coder" "${CLI_INSTALL_DIR}/coder"
 CLI_PATH="${CLI_INSTALL_DIR}/coder"
 ok "Installed to ${CLI_PATH}"

 case ":${PATH}:" in
 *":${CLI_INSTALL_DIR}:"*) CLI_NEEDS_PATH=0 ;;
 *) CLI_NEEDS_PATH=1 ;;
 esac
}
