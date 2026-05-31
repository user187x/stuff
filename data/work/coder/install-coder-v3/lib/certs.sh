# lib/certs.sh — generate self-signed Root CA + server cert (wildcard SAN).

generate_certs() {
 step "TLS PKI for ${DOMAIN}"
 mkdir -p "$CERT_DIR"

 if [[ -f "$CERT_DIR/ca-cert.pem" && -f "$CERT_DIR/server.crt" && -f "$CERT_DIR/server.key" ]]; then
  if openssl x509 -in "$CERT_DIR/server.crt" -text -noout 2>/dev/null | grep -q "DNS:${DOMAIN}\b"; then
   ok "Reusing existing certs in ${CERT_DIR}"
   return
  fi
  warn "Existing certs don't cover ${DOMAIN}; regenerating"
 fi

 log "Generating self-signed Root CA + server cert (with wildcard SAN)…"

 # Render the OpenSSL extension config from a template (uses ${DOMAIN}).
 render_template "$MANIFESTS_DIR/server-ext.cnf.tpl" >"$CERT_DIR/server-ext.cnf"

 (
  cd "$CERT_DIR"
  openssl genrsa -out ca.key 4096 >/dev/null 2>&1
  openssl req -x509 -new -nodes -key ca.key -sha256 -days 3650 \
   -out ca-cert.pem -subj "/O=Coder Local CA/CN=Coder Local Root CA" >/dev/null 2>&1

  openssl genrsa -out server.key 4096 >/dev/null 2>&1
  openssl req -new -key server.key -out server.csr \
   -subj "/CN=${DOMAIN}" >/dev/null 2>&1

  openssl x509 -req -in server.csr -CA ca-cert.pem -CAkey ca.key \
   -CAcreateserial -out server.crt -days 825 -sha256 \
   -extfile server-ext.cnf >/dev/null 2>&1
 )
 ok "Certs written to ${CERT_DIR}"
}
