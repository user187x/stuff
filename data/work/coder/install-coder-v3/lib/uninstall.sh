# lib/uninstall.sh — tear it all down.

do_uninstall() {
 step "Uninstall"
 local extra_ns_msg=""
 kubectl get ns "$TRAEFIK_NAMESPACE" >/dev/null 2>&1 &&
  extra_ns_msg=" + '${TRAEFIK_NAMESPACE}'"
 if ! confirm "Delete '${NAMESPACE}'${extra_ns_msg} namespace(s) and the /etc/hosts entry for ${DOMAIN}? (existing ingress-nginx will NOT be touched)"; then
  log "Aborted"
  exit 0
 fi

 helm uninstall coder -n "$NAMESPACE" 2>/dev/null || true
 while IFS= read -r ns; do
  [[ -z "$ns" ]] && continue
  helm uninstall traefik -n "$ns" 2>/dev/null || true
 done < <(helm list -A 2>/dev/null | awk 'NR>1 && $1=="traefik" {print $2}')

 # The filebrowser-host deployment lives in $NAMESPACE so it goes away with the namespace.
 kubectl delete namespace "$NAMESPACE" --ignore-not-found --wait=false
 kubectl delete namespace "$TRAEFIK_NAMESPACE" --ignore-not-found --wait=false 2>/dev/null || true

 if grep -qF "# install-coder:${DOMAIN}" /etc/hosts 2>/dev/null; then
  log "Removing /etc/hosts entry…"
  local tmp
  tmp=$(mktemp)
  grep -vF "# install-coder:${DOMAIN}" /etc/hosts >"$tmp"
  sudo cp "$tmp" /etc/hosts
  rm -f "$tmp"
 fi

 ok "Uninstall complete."
 warn "Minikube cluster left running. Delete with: minikube delete -p ${MINIKUBE_PROFILE}"
 warn "Cert directory left in place: ${CERT_DIR} (rm -rf to remove)"
}
