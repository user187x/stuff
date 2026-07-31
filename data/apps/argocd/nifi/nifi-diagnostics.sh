#!/usr/bin/env bash
# =============================================================================
# nifi-diagnostics.sh
# Collects cluster status for the NiFiKop / NiFi / ZooKeeper / Traefik setup
# into a single text file you can share back.
#
# Usage:
#   chmod +x nifi-diagnostics.sh
#   ./nifi-diagnostics.sh
#   -> writes nifi-diagnostics-<timestamp>.txt in the current directory
# =============================================================================
set -uo pipefail   # no "-e": keep going even if individual commands fail

OUT="nifi-diagnostics-$(date +%Y%m%d-%H%M%S).txt"

# Adjust these if your namespaces differ
NS_OPERATOR="nifikop"
NS_NIFI="nifi"
NS_ZK="zookeeper"
NS_ARGO="argocd"
NS_TRAEFIK="traefik"

section() {
  {
    echo ""
    echo "================================================================="
    echo "### $1"
    echo "================================================================="
  } >> "$OUT"
}

run() {
  # Log the command, then its output (stdout+stderr) so failures are visible too
  {
    echo ""
    echo "--- \$ $*"
  } >> "$OUT"
  "$@" >> "$OUT" 2>&1
}

: > "$OUT"
{
  echo "NiFi cluster diagnostics"
  echo "Generated: $(date -u +'%Y-%m-%dT%H:%M:%SZ')"
} >> "$OUT"

section "CLUSTER / CONTEXT"
run kubectl version
run kubectl config current-context
run kubectl get nodes -o wide

section "OVERVIEW: ALL RELEVANT PODS"
run bash -c "kubectl get pods -A -o wide | grep -Ei 'nifi|zookeeper|traefik|argocd' || true"

section "ARGOCD APPLICATIONS"
run kubectl get applications -n "$NS_ARGO" -o wide
run bash -c "kubectl get applications -n $NS_ARGO -o custom-columns='NAME:.metadata.name,SYNC:.status.sync.status,HEALTH:.status.health.status,MESSAGE:.status.conditions[*].message'"

section "NIFIKOP OPERATOR — namespace $NS_OPERATOR"
run kubectl get all -n "$NS_OPERATOR"
run kubectl describe deploy -n "$NS_OPERATOR"
run kubectl logs -n "$NS_OPERATOR" deploy/nifikop --tail=100

section "NIFIKOP OPERATOR — RBAC CHECKS"
# Detect the operator's service account automatically, fall back to "nifikop"
SA=$(kubectl get deploy -n "$NS_OPERATOR" -o jsonpath='{.items[0].spec.template.spec.serviceAccountName}' 2>/dev/null)
SA="${SA:-nifikop}"
echo "Detected operator ServiceAccount: $SA" >> "$OUT"
run kubectl auth can-i get leases.coordination.k8s.io --as="system:serviceaccount:${NS_OPERATOR}:${SA}" -n "$NS_OPERATOR"
run kubectl auth can-i create leases.coordination.k8s.io --as="system:serviceaccount:${NS_OPERATOR}:${SA}" -n "$NS_OPERATOR"
run kubectl auth can-i get nificlusters.nifi.konpyutaika.com --as="system:serviceaccount:${NS_OPERATOR}:${SA}" -n "$NS_NIFI"
run kubectl get role,rolebinding -n "$NS_OPERATOR"
run kubectl get role,rolebinding -n "$NS_NIFI"
run bash -c "kubectl get clusterrole,clusterrolebinding | grep -i nifi || true"

section "NIFIKOP CRDS"
run bash -c "kubectl get crd | grep konpyutaika || true"

section "NIFI CLUSTER — namespace $NS_NIFI"
run kubectl get nificluster -n "$NS_NIFI" -o wide
run kubectl describe nificluster -n "$NS_NIFI"
run kubectl get pods,svc,pvc,cm -n "$NS_NIFI" -o wide
run bash -c "kubectl get events -n $NS_NIFI --sort-by=.lastTimestamp | tail -40"
# Logs from any NiFi pods that exist (may be none if operator never reconciled)
for p in $(kubectl get pods -n "$NS_NIFI" -o name 2>/dev/null); do
  run kubectl logs -n "$NS_NIFI" "$p" --tail=50 --all-containers
done

section "ZOOKEEPER — namespace $NS_ZK"
run kubectl get pods,svc,pvc -n "$NS_ZK" -o wide
run bash -c "kubectl get events -n $NS_ZK --sort-by=.lastTimestamp | tail -20"
# Quick health probe against the first ZK pod
ZK_POD=$(kubectl get pods -n "$NS_ZK" -o name 2>/dev/null | head -1)
if [ -n "${ZK_POD:-}" ]; then
  run kubectl exec -n "$NS_ZK" "$ZK_POD" -- bash -c "echo srvr | nc 127.0.0.1 2181"
fi

section "TRAEFIK GATEWAY / HTTPROUTE"
run kubectl get gatewayclass
run kubectl get gateway -A
run kubectl describe gateway -n "$NS_TRAEFIK"
run kubectl get httproute -n "$NS_NIFI" -o yaml
run bash -c "kubectl get pods -n $NS_TRAEFIK -o wide || true"

section "DONE"
echo "" >> "$OUT"
echo "Diagnostics written to: $OUT"
