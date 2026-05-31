# lib/minikube.sh — start the cluster and align kubectl context.

ensure_minikube() {
 step "Minikube cluster"

 if minikube status -p "$MINIKUBE_PROFILE" 2>/dev/null | grep -q "host: Running"; then
  ok "Minikube profile '${MINIKUBE_PROFILE}' already running"
 else
  log "Starting minikube (profile=${MINIKUBE_PROFILE} cpus=${MINIKUBE_CPUS} mem=${MINIKUBE_MEMORY}MB disk=${MINIKUBE_DISK})…"
  local args=(start
   --profile="$MINIKUBE_PROFILE"
   --cpus="$MINIKUBE_CPUS"
   --memory="$MINIKUBE_MEMORY"
   --disk-size="$MINIKUBE_DISK"
  )
  [[ -n "$MINIKUBE_DRIVER" ]] && args+=(--driver="$MINIKUBE_DRIVER")
  minikube "${args[@]}"
  ok "Minikube started"
 fi

 local ctx
 ctx="$(kubectl config current-context 2>/dev/null || true)"
 if [[ "$ctx" != "$MINIKUBE_PROFILE" ]]; then
  warn "kubectl context is '${ctx:-unset}', switching to '${MINIKUBE_PROFILE}'"
  kubectl config use-context "$MINIKUBE_PROFILE" >/dev/null
 fi
 kubectl cluster-info >/dev/null 2>&1 || die "Cannot reach Kubernetes API. Run 'minikube logs --problems' to debug."
 ok "kubectl context: $(kubectl config current-context)"
}
