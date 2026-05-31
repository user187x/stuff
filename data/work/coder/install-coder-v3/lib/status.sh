# lib/status.sh — print the current state of every moving part.

do_status() {
 step "Cluster"
 minikube status -p "$MINIKUBE_PROFILE" || true

 step "Pods (${NAMESPACE})"
 kubectl get pods -n "$NAMESPACE" 2>/dev/null || warn "namespace ${NAMESPACE} not found"

 step "Services & Ingress (${NAMESPACE})"
 kubectl get svc,ingress -n "$NAMESPACE" 2>/dev/null || true

 step "Ingress controller"
 if kubectl get deploy -n ingress-nginx ingress-nginx-controller >/dev/null 2>&1; then
  echo "  detected   : ingress-nginx (in use if --ingress-controller=auto/nginx)"
  kubectl get pods,svc -n ingress-nginx 2>/dev/null || true
 fi
 if kubectl get deploy -n "$TRAEFIK_NAMESPACE" traefik >/dev/null 2>&1; then
  echo "  detected   : traefik (namespace=${TRAEFIK_NAMESPACE})"
  kubectl get pods,svc -n "$TRAEFIK_NAMESPACE" 2>/dev/null || true
 fi

 step "Filebrowser host"
 if kubectl get deploy -n "$NAMESPACE" filebrowser-host >/dev/null 2>&1; then
  kubectl get pods,svc -n "$NAMESPACE" -l app=filebrowser-host 2>/dev/null || true
  echo "  in-cluster URL: http://filebrowser-host.${NAMESPACE}.svc.cluster.local/filebrowser"
 else
  echo "  not deployed"
 fi

 step "Reachability"
 local target_ip
 target_ip=$(minikube ip -p "$MINIKUBE_PROFILE" 2>/dev/null || echo unknown)
 echo "  minikube IP : ${target_ip}"
 echo "  domain      : https://${DOMAIN}"
 if [[ -f "$CERT_DIR/ca-cert.pem" && "$target_ip" != "unknown" ]]; then
  local s
  s=$(curl --cacert "$CERT_DIR/ca-cert.pem" \
   --resolve "${DOMAIN}:443:${target_ip}" \
   -s -o /dev/null -w '%{http_code}' \
   --max-time 5 "https://${DOMAIN}/healthz" || true)
  echo "  /healthz    : ${s:-no response}"
 fi
}
