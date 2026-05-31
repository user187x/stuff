# lib/coder.sh — install Coder via Helm with values rendered from a template.

install_coder() {
 step "Coder (Helm)"
 helm repo add coder-v2 https://helm.coder.com/v2 >/dev/null 2>&1 || true
 helm repo update coder-v2 >/dev/null

 # Render the values template into VALUES_FILE so it's easy to inspect/diff.
 render_template "$MANIFESTS_DIR/coder-values.yaml.tpl" >"$VALUES_FILE"
 log "Rendered Helm values → ${VALUES_FILE}"

 log "helm upgrade --install coder…"
 helm upgrade --install coder coder-v2/coder \
  --namespace="$NAMESPACE" \
  -f "$VALUES_FILE" \
  --wait --timeout=10m
 ok "Coder deployment ready"
}
