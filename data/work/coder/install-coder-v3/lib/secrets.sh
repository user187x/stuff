# lib/secrets.sh — namespace + TLS secret + CA secret + Postgres credentials.

apply_secrets() {
 step "Namespace + secrets"
 kubectl get ns "$NAMESPACE" >/dev/null 2>&1 || kubectl create namespace "$NAMESPACE"
 ok "Namespace: $NAMESPACE"

 kubectl create secret tls "$TLS_SECRET" \
  --cert="$CERT_DIR/server.crt" --key="$CERT_DIR/server.key" \
  --namespace="$NAMESPACE" --dry-run=client -o yaml | kubectl apply -f - >/dev/null
 kubectl create secret generic "$CA_SECRET" \
  --from-file=ca.crt="$CERT_DIR/ca-cert.pem" \
  --namespace="$NAMESPACE" --dry-run=client -o yaml | kubectl apply -f - >/dev/null
 ok "TLS secret: $TLS_SECRET"
 ok "CA secret:  $CA_SECRET   ${DIM}(referenced by your main.tf)${RST}"

 if ! kubectl get secret coder-pg-credentials -n "$NAMESPACE" >/dev/null 2>&1; then
  local pwd
  pwd="$(openssl rand -hex 24)"
  kubectl create secret generic coder-pg-credentials -n "$NAMESPACE" \
   --from-literal=username=coder \
   --from-literal=password="$pwd" \
   --from-literal=database=coder >/dev/null
  ok "Generated random Postgres credentials"
 else
  ok "Reusing existing Postgres credentials"
 fi
}
