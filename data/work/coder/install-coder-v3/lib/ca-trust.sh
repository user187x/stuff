# lib/ca-trust.sh — install the local CA into the OS trust store and the
# user's NSS DB (Chrome/Chromium/Firefox) so https://DOMAIN works without warnings.

trust_local_ca() {
 step "Trusting Local CA"
 local cert="$CERT_DIR/ca-cert.pem"

 if [[ ! -f "$cert" ]]; then
  warn "CA cert not found at $cert"
  return
 fi

 log "Adding CA certificate to system trust stores (sudo prompt incoming)…"

 case "$OS" in
 Darwin)
  if sudo security add-trusted-cert -d -r trustRoot -k /Library/Keychains/System.keychain "$cert" >/dev/null 2>&1; then
   ok "Added to macOS System Keychain"
  else
   warn "Failed to add to macOS Keychain"
  fi
  ;;
 Linux)
  if command -v update-ca-certificates >/dev/null 2>&1; then
   sudo cp "$cert" /usr/local/share/ca-certificates/coder-local-ca.crt
   sudo update-ca-certificates >/dev/null 2>&1
   ok "Added to Debian/Ubuntu CA trust"
  elif command -v update-ca-trust >/dev/null 2>&1; then
   sudo cp "$cert" /etc/pki/ca-trust/source/anchors/coder-local-ca.crt
   sudo update-ca-trust >/dev/null 2>&1
   ok "Added to RHEL/Fedora CA trust"
  fi
  ;;
 esac

 log "Adding new CA certificate to web browsers to trust..."
 if [[ -d "$HOME/.pki/nssdb" ]]; then
  if ! command -v certutil >/dev/null 2>&1; then
   log "Installing libnss3-tools for certutil…"
   if command -v apt >/dev/null 2>&1; then
    sudo apt update >/dev/null && sudo apt install -y libnss3-tools >/dev/null
   fi
  fi

  if command -v certutil >/dev/null 2>&1; then
   if certutil -d "sql:$HOME/.pki/nssdb" -A -t "C,," -n "CoderLocalCA" -i "$cert" >/dev/null 2>&1; then
    ok "Successfully added new CA certifcate to nssdb"
   else
    warn "Failed adding CA certificate to nssdb"
   fi
  fi
 fi
}
