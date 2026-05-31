# lib/ingress.sh — pick (or install) an ingress controller, then apply
# the Coder Ingress object. The controller-specific manifests live in
# manifests/coder-ingress-{nginx,traefik}.yaml.tpl.

INGRESS_MODE=""

install_ingress() {
 step "Ingress controller"
 resolve_ingress_mode
 case "$INGRESS_MODE" in
 nginx) use_existing_nginx ;;
 traefik) install_traefik ;;
 esac
 apply_coder_ingress
}

resolve_ingress_mode() {
 local nginx_present=false
 if kubectl get deploy -n ingress-nginx ingress-nginx-controller >/dev/null 2>&1; then
  nginx_present=true
 fi

 case "$INGRESS_CONTROLLER" in
 auto)
  if $nginx_present; then
   INGRESS_MODE=nginx
   ok "Detected existing ingress-nginx — reusing it"
  else
   INGRESS_MODE=traefik
   ok "No existing ingress controller — will install Traefik"
  fi
  ;;
 nginx)
  $nginx_present || die "--ingress-controller=nginx but ingress-nginx-controller is not running."
  INGRESS_MODE=nginx
  ok "Using existing ingress-nginx (forced by --ingress-controller=nginx)"
  ;;
 traefik)
  INGRESS_MODE=traefik
  ok "Installing Traefik (forced by --ingress-controller=traefik)"
  ;;
 esac
}

use_existing_nginx() {
 log "Verifying ingress-nginx is ready…"
 kubectl rollout status deployment/ingress-nginx-controller \
  -n ingress-nginx --timeout=120s >/dev/null ||
  warn "ingress-nginx didn't report Ready in 120s — continuing anyway"

 if ! kubectl get ingressclass nginx >/dev/null 2>&1; then
  warn "No IngressClass named 'nginx' found. The Ingress we apply uses ingressClassName: nginx."
 fi
 ok "ingress-nginx looks ready"
}

install_traefik() {
 helm repo add traefik https://traefik.github.io/charts >/dev/null 2>&1 || true
 helm repo update traefik >/dev/null

 # Remove stale Traefik releases in other namespaces so they free up hostPort 80/443.
 local stale_ns
 stale_ns=$(helm list -A 2>/dev/null | awk -v want="$TRAEFIK_NAMESPACE" 'NR>1 && $1=="traefik" && $2!=want {print $2}')
 if [[ -n "$stale_ns" ]]; then
  while IFS= read -r ns; do
   [[ -z "$ns" ]] && continue
   warn "Found existing traefik release in '${ns}' — removing to free hostPort 80/443"
   helm uninstall traefik -n "$ns" --wait 2>/dev/null || true
  done <<<"$stale_ns"
  for _ in {1..30}; do
   kubectl get pods -A -l app.kubernetes.io/name=traefik 2>/dev/null | grep -q traefik || break
   sleep 2
  done
 fi

 # Bail early if some other workload is already binding 80/443 — Traefik won't schedule.
 local port_holder
 port_holder=$(kubectl get pods -A -o jsonpath='{range .items[*]}{.metadata.namespace}/{.metadata.name}{"\t"}{.spec.containers[*].ports[*].hostPort}{"\n"}{end}' 2>/dev/null |
  awk -F'\t' '$2 ~ /(^| )(80|443)( |$)/ {print $1}' |
  grep -v '^traefik/' || true)
 if [[ -n "$port_holder" ]]; then
  err "Pods are already binding hostPort 80/443 — Traefik will not schedule:"
  printf '  %s\n' $port_holder >&2
  die "Either remove those, or re-run with --ingress-controller=nginx to use the existing controller."
 fi

 kubectl get ns "$TRAEFIK_NAMESPACE" >/dev/null 2>&1 || kubectl create namespace "$TRAEFIK_NAMESPACE"

 log "helm upgrade --install traefik…"
 helm upgrade --install traefik traefik/traefik \
  --namespace="$TRAEFIK_NAMESPACE" \
  --set ports.web.hostPort=80 \
  --set ports.websecure.hostPort=443 \
  --set service.type=ClusterIP \
  --set ingressClass.enabled=true \
  --set ingressClass.isDefaultClass=true \
  --wait --timeout=5m
 ok "Traefik ready"
}

apply_coder_ingress() {
 local tpl="$MANIFESTS_DIR/coder-ingress-${INGRESS_MODE}.yaml.tpl"
 [[ -f "$tpl" ]] || die "Ingress template not found: $tpl"

 render_template "$tpl" | kubectl apply -n "$NAMESPACE" -f - >/dev/null
 ok "Ingress applied (class=${INGRESS_MODE}, with wildcard for *.${DOMAIN})"
}
