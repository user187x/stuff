# lib/health.sh — probe https://DOMAIN/healthz from outside until it returns 200.

health_check() {
 step "Health check"
 local target_ip url status
 target_ip=$(minikube ip -p "$MINIKUBE_PROFILE")
 url="https://${DOMAIN}/healthz"
 log "Probing ${url} …"

 for _ in {1..30}; do
  status=$(curl --cacert "$CERT_DIR/ca-cert.pem" \
   --resolve "${DOMAIN}:443:${target_ip}" \
   -s -o /dev/null -w '%{http_code}' \
   --max-time 5 "$url" || true)
  if [[ "$status" == "200" ]]; then
   ok "Coder is responding 200 on ${url}"
   return
  fi
  sleep 2
 done
 warn "Coder did not return 200 within ~60s (last status: ${status:-no-response})"
 warn "Check pods:  kubectl get pods -n ${NAMESPACE}"
 warn "Tail logs:   kubectl logs -n ${NAMESPACE} -l app.kubernetes.io/name=coder --tail=100"
}
