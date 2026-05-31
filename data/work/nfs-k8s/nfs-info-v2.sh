#!/usr/bin/env bash
#
# Display comprehensive information about the NFS setup:
# cluster nodes, pod, service, network, storage, physical data location,
# workloads (with replica counts and node distribution), and filesystem stats.
#
# Usage:
#   ./nfs-info.sh
#
# Env overrides:
#   NAMESPACE       Namespace where the NFS server runs (default: nfs-demo)
#   PVC             PVC name to inspect filesystem through (default: nfs-shared-pvc)
#   CLUSTER_DOMAIN  Cluster DNS suffix (default: cluster.local)

set -euo pipefail

NAMESPACE="${NAMESPACE:-nfs-demo}"
PVC="${PVC:-nfs-shared-pvc}"
CLUSTER_DOMAIN="${CLUSTER_DOMAIN:-cluster.local}"

# Dependency check
for cmd in kubectl jq; do
 if ! command -v "$cmd" >/dev/null 2>&1; then
  echo "Error: '$cmd' is required but not installed."
  exit 1
 fi
done

# Color only when stdout is a TTY
if [[ -t 1 ]]; then
 BOLD=$'\033[1;36m'
 DIM=$'\033[2m'
 YELLOW=$'\033[1;33m'
 RESET=$'\033[0m'
else
 BOLD=''
 DIM=''
 YELLOW=''
 RESET=''
fi

section() {
 printf "\n%s═══ %s ═══%s\n" "$BOLD" "$1" "$RESET"
}

# Verify NFS server exists
if ! kubectl get svc -n "$NAMESPACE" nfs-server >/dev/null 2>&1; then
 echo "Error: nfs-server service not found in namespace '$NAMESPACE'."
 echo "Did you run setup-nfs-minikube.sh?"
 exit 1
fi

# ─── Cluster nodes ─────────────────────────────────────────────────────
section "Cluster Nodes"
kubectl get nodes -o custom-columns='NAME:.metadata.name,STATUS:.status.conditions[-1:].type,VERSION:.status.nodeInfo.kubeletVersion,OS:.status.nodeInfo.osImage' |
 sed 's/^/  /'

# ─── NFS server pod & deployment ───────────────────────────────────────
section "NFS Server Pod"
DEP_INFO=$(kubectl get deployment -n "$NAMESPACE" nfs-server -o json 2>/dev/null || echo "{}")
DESIRED=$(echo "$DEP_INFO" | jq -r '.spec.replicas // 0')
READY=$(echo "$DEP_INFO" | jq -r '.status.readyReplicas // 0')
AVAILABLE=$(echo "$DEP_INFO" | jq -r '.status.availableReplicas // 0')

POD_NAME=$(kubectl get pods -n "$NAMESPACE" -l app=nfs-server -o jsonpath='{.items[0].metadata.name}')
POD_INFO=$(kubectl get pod -n "$NAMESPACE" "$POD_NAME" -o json)
NFS_NODE=$(echo "$POD_INFO" | jq -r '.spec.nodeName')

printf "  %-14s %s%s   (replicas %s/%s ready, %s available)%s\n" \
 "Deployment:" "nfs-server" "$DIM" "$READY" "$DESIRED" "$AVAILABLE" "$RESET"
printf "  %-14s %s\n" "Pod:" "$POD_NAME"
printf "  %-14s %s\n" "Status:" "$(echo "$POD_INFO" | jq -r '.status.phase')"
printf "  %-14s %s   %s← files physically live here%s\n" \
 "Node:" "$NFS_NODE" "$YELLOW" "$RESET"
printf "  %-14s %s\n" "Pod IP:" "$(echo "$POD_INFO" | jq -r '.status.podIP')"
printf "  %-14s %s\n" "Image:" "$(echo "$POD_INFO" | jq -r '.spec.containers[0].image')"
printf "  %-14s %s\n" "Restarts:" "$(echo "$POD_INFO" | jq -r '.status.containerStatuses[0].restartCount')"
printf "  %-14s %s\n" "Started:" "$(echo "$POD_INFO" | jq -r '.status.startTime')"

if [[ "$DESIRED" != "1" ]]; then
 printf "\n  %s⚠ NFS server has %s desired replicas. This image is not designed for HA;%s\n" \
  "$YELLOW" "$DESIRED" "$RESET"
 printf "  %s  multiple replicas can corrupt the export. Set replicas: 1.%s\n" "$YELLOW" "$RESET"
fi

# ─── Service / Network ─────────────────────────────────────────────────
section "Service / Network"
NFS_IP=$(kubectl get svc -n "$NAMESPACE" nfs-server -o jsonpath='{.spec.clusterIP}')
NFS_DNS="nfs-server.${NAMESPACE}.svc.${CLUSTER_DOMAIN}"

printf "  %-14s %s\n" "ClusterIP:" "$NFS_IP"
printf "  %-14s %s %s(in-cluster pods only)%s\n" "DNS:" "$NFS_DNS" "$DIM" "$RESET"
echo "  Ports:"
kubectl get svc -n "$NAMESPACE" nfs-server -o json |
 jq -r '.spec.ports[] | "    \(.name):\t\(.port)/\(.protocol)"'

# ─── Backing storage (NFS server's own PVC) ────────────────────────────
section "NFS Server Backing Storage"
BACKING=$(kubectl get pvc -n "$NAMESPACE" nfs-server-storage -o json 2>/dev/null || echo "")
if [[ -n "$BACKING" ]]; then
 BACKING_PV=$(echo "$BACKING" | jq -r '.spec.volumeName')
 printf "  %-14s %s\n" "PVC:" "nfs-server-storage"
 printf "  %-14s %s\n" "Capacity:" "$(echo "$BACKING" | jq -r '.status.capacity.storage')"
 printf "  %-14s %s\n" "Status:" "$(echo "$BACKING" | jq -r '.status.phase')"
 printf "  %-14s %s\n" "Volume:" "$BACKING_PV"
 printf "  %-14s %s\n" "StorageClass:" "$(echo "$BACKING" | jq -r '.spec.storageClassName // "(default)"')"
else
 BACKING_PV=""
 echo "  (not found)"
fi

# ─── Physical data location ────────────────────────────────────────────
section "Physical Data Location"
echo "  Files are stored on the node hosting the nfs-server pod, inside that"
echo "  pod's backing PV. Other pods access them remotely over NFS."
echo ""

if [[ -n "$BACKING_PV" ]]; then
 PV_INFO=$(kubectl get pv "$BACKING_PV" -o json 2>/dev/null || echo "{}")
 HOST_PATH=$(echo "$PV_INFO" | jq -r '.spec.hostPath.path // .spec.local.path // empty')
 PV_NODES=$(echo "$PV_INFO" | jq -r '.spec.nodeAffinity.required.nodeSelectorTerms[]?.matchExpressions[]?.values[]?' 2>/dev/null | sort -u | tr '\n' ',' | sed 's/,$//')
 PROVISIONER=$(echo "$PV_INFO" | jq -r '.metadata.annotations."pv.kubernetes.io/provisioned-by" // "unknown"')

 printf "  %-14s %s\n" "Node:" "$NFS_NODE"
 printf "  %-14s %s\n" "Provisioner:" "$PROVISIONER"
 [[ -n "$HOST_PATH" ]] && printf "  %-14s %s %s(path on node %s)%s\n" \
  "On-disk path:" "$HOST_PATH" "$DIM" "$NFS_NODE" "$RESET"
 [[ -n "$PV_NODES" ]] && printf "  %-14s %s\n" "PV affinity:" "$PV_NODES"

 echo ""
 if [[ -n "$HOST_PATH" || -n "$PV_NODES" ]]; then
  printf "  %s⚠ Backing storage is node-local. If the nfs-server pod is rescheduled%s\n" "$YELLOW" "$RESET"
  printf "  %s  to a different node, the existing data will NOT follow it.%s\n" "$YELLOW" "$RESET"
 fi
fi

# ─── Shared PVs (NFS-backed, RWX) ──────────────────────────────────────
section "Shared PVs (backed by this NFS server)"
PVS=$(kubectl get pv -o json | jq -r --arg ip "$NFS_IP" '
    .items[] |
    select(.spec.nfs != null and .spec.nfs.server == $ip) |
    .metadata.name')

if [[ -z "$PVS" ]]; then
 echo "  None found."
else
 printf "  %-32s %-10s %-10s %s\n" "NAME" "CAPACITY" "STATUS" "BOUND TO"
 printf "  %-32s %-10s %-10s %s\n" "────" "────────" "──────" "────────"
 while IFS= read -r pv; do
  info=$(kubectl get pv "$pv" -o json)
  cap=$(echo "$info" | jq -r '.spec.capacity.storage')
  status=$(echo "$info" | jq -r '.status.phase')
  ns=$(echo "$info" | jq -r '.spec.claimRef.namespace // "-"')
  claim=$(echo "$info" | jq -r '.spec.claimRef.name // "-"')
  printf "  %-32s %-10s %-10s %s/%s\n" "$pv" "$cap" "$status" "$ns" "$claim"
 done <<<"$PVS"
fi

# ─── Workloads using NFS ───────────────────────────────────────────────
section "Workloads Using NFS"
if [[ -z "$PVS" ]]; then
 echo "  No NFS-backed PVs found."
else
 found_any=false
 while IFS= read -r pv; do
  info=$(kubectl get pv "$pv" -o json)
  ns=$(echo "$info" | jq -r '.spec.claimRef.namespace // empty')
  claim=$(echo "$info" | jq -r '.spec.claimRef.name // empty')
  [[ -z "$ns" || -z "$claim" ]] && continue

  # Find Deployments and StatefulSets in this namespace using the PVC
  WORKLOADS=$(
   {
    kubectl get deployments -n "$ns" -o json | jq -r --arg c "$claim" '
                    .items[] |
                    select(.spec.template.spec.volumes[]?.persistentVolumeClaim.claimName == $c) |
                    "Deployment|\(.metadata.name)|\(.spec.replicas)|\(.status.readyReplicas // 0)|\(.status.availableReplicas // 0)|\(.spec.selector.matchLabels | to_entries | map("\(.key)=\(.value)") | join(","))"'
    kubectl get statefulsets -n "$ns" -o json | jq -r --arg c "$claim" '
                    .items[] |
                    select(.spec.template.spec.volumes[]?.persistentVolumeClaim.claimName == $c) |
                    "StatefulSet|\(.metadata.name)|\(.spec.replicas)|\(.status.readyReplicas // 0)|\(.status.availableReplicas // 0)|\(.spec.selector.matchLabels | to_entries | map("\(.key)=\(.value)") | join(","))"'
   } 2>/dev/null
  )

  if [[ -z "$WORKLOADS" ]]; then
   continue
  fi

  found_any=true
  echo "  PVC ${ns}/${claim}:"

  while IFS='|' read -r kind name desired ready available selector; do
   [[ -z "$kind" ]] && continue
   printf "    %s %s   %sreplicas %s/%s ready, %s available%s\n" \
    "$kind" "$name" "$DIM" "$ready" "$desired" "$available" "$RESET"

   # List pods + nodes
   pods_info=$(kubectl get pods -n "$ns" -l "$selector" -o json 2>/dev/null | jq -r '
                .items[] |
                "\(.metadata.name)|\(.status.phase)|\(.spec.nodeName // "<pending>")"')

   if [[ -n "$pods_info" ]]; then
    while IFS='|' read -r pod_name phase node; do
     printf "      %-50s [%s] on %s\n" "$pod_name" "$phase" "$node"
    done <<<"$pods_info"

    # Node distribution summary
    node_dist=$(echo "$pods_info" | cut -d'|' -f3 | sort | uniq -c | awk '{printf "%s(%s) ", $2, $1}')
    printf "      %sNode distribution:%s %s\n" "$DIM" "$RESET" "$node_dist"
   fi
   echo ""
  done <<<"$WORKLOADS"
 done <<<"$PVS"
 [[ "$found_any" == "false" ]] && echo "  No workloads currently using NFS PVCs."
fi

# ─── Filesystem stats (via ephemeral pod) ──────────────────────────────
section "Filesystem Stats"

POD="nfs-info-$$"
cleanup() { kubectl delete pod -n "$NAMESPACE" "$POD" --ignore-not-found --wait=false >/dev/null 2>&1 || true; }
trap cleanup EXIT

echo "  Launching ephemeral pod..."
kubectl run "$POD" -n "$NAMESPACE" --restart=Never --image=busybox \
 --overrides="{
        \"spec\": {
            \"containers\": [{
                \"name\": \"$POD\",
                \"image\": \"busybox\",
                \"command\": [\"sleep\", \"3600\"],
                \"volumeMounts\": [{\"name\": \"shared\", \"mountPath\": \"/data\"}]
            }],
            \"volumes\": [{
                \"name\": \"shared\",
                \"persistentVolumeClaim\": {\"claimName\": \"$PVC\"}
            }]
        }
    }" >/dev/null

kubectl wait -n "$NAMESPACE" --for=condition=ready "pod/$POD" --timeout=60s >/dev/null
INSPECT_NODE=$(kubectl get pod -n "$NAMESPACE" "$POD" -o jsonpath='{.spec.nodeName}')

printf "  Inspector pod scheduled on: %s %s(reads files over NFS from %s)%s\n" \
 "$INSPECT_NODE" "$DIM" "$NFS_NODE" "$RESET"

echo ""
echo "  Disk usage:"
kubectl exec -n "$NAMESPACE" "$POD" -- df -h /data | sed 's/^/    /'

echo ""
FILES=$(kubectl exec -n "$NAMESPACE" "$POD" -- sh -c 'find /data -type f 2>/dev/null | wc -l' | tr -d ' ')
DIRS=$(kubectl exec -n "$NAMESPACE" "$POD" -- sh -c 'find /data -mindepth 1 -type d 2>/dev/null | wc -l' | tr -d ' ')
SYMLINKS=$(kubectl exec -n "$NAMESPACE" "$POD" -- sh -c 'find /data -type l 2>/dev/null | wc -l' | tr -d ' ')
USED=$(kubectl exec -n "$NAMESPACE" "$POD" -- sh -c 'du -sh /data 2>/dev/null | cut -f1')

printf "  %-14s %s\n" "Files:" "$FILES"
printf "  %-14s %s\n" "Directories:" "$DIRS"
printf "  %-14s %s\n" "Symlinks:" "$SYMLINKS"
printf "  %-14s %s\n" "Used:" "$USED"

echo ""
echo "  Top-level entries:"
kubectl exec -n "$NAMESPACE" "$POD" -- ls -lah /data | sed 's/^/    /'

echo ""
