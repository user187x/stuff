# lib/filebrowser.sh — air-gap support for the filebrowser workspace addon.
#
# In a normal install, Coder's filebrowser module fetches the binary from
# the public internet (https://github.com/filebrowser/get). That doesn't
# work air-gapped, so we ship the binary alongside this script and host
# it inside the cluster on a tiny HTTP server.
#
# After this step, workspace templates can fetch it via cluster DNS:
#
#   wget -qO /tmp/filebrowser \
#     http://filebrowser-host.${NAMESPACE}.svc.cluster.local/filebrowser
#
# The transport is plain HTTP because the traffic never leaves the cluster.

install_filebrowser() {
 step "Filebrowser binary host (air-gap)"

 if $SKIP_FILEBROWSER; then
  warn "Skipping filebrowser host (--skip-filebrowser)"
  return
 fi

 if [[ ! -f "$FILEBROWSER_BINARY" ]]; then
  warn "filebrowser binary not found at: ${FILEBROWSER_BINARY}"
  warn "Place the binary alongside this script (or pass --filebrowser-binary)."
  warn "Skipping in-cluster host. Workspaces relying on filebrowser will fail until this is done."
  return
 fi

 log "Applying manifests/filebrowser-host.yaml…"
 render_template "$MANIFESTS_DIR/filebrowser-host.yaml.tpl" |
  kubectl apply -n "$NAMESPACE" -f - >/dev/null

 log "Waiting for filebrowser-host rollout…"
 kubectl rollout status deployment/filebrowser-host -n "$NAMESPACE" --timeout=120s

 local pod
 pod=$(kubectl get pod -n "$NAMESPACE" -l app=filebrowser-host \
  -o jsonpath='{.items[?(@.status.phase=="Running")].metadata.name}' |
  awk '{print $1}')
 [[ -n "$pod" ]] || die "filebrowser-host pod did not reach Running state"

 # Idempotency: skip the upload if the in-pod binary already matches in size.
 local local_size remote_size
 if [[ "$OS" == "Darwin" ]]; then
  local_size=$(stat -f%z "$FILEBROWSER_BINARY")
 else
  local_size=$(stat -c%s "$FILEBROWSER_BINARY")
 fi
 remote_size=$(kubectl exec -n "$NAMESPACE" "$pod" -- \
  sh -c 'stat -c%s /www/filebrowser 2>/dev/null || echo 0' | tr -d '[:space:]')

 if [[ "$local_size" == "$remote_size" ]]; then
  ok "Binary already uploaded (size=${local_size} bytes) — skipping copy"
 else
  log "Uploading filebrowser binary (${local_size} bytes)…"
  # `kubectl cp` needs `tar` on the target; busybox has it built-in.
  kubectl cp "$FILEBROWSER_BINARY" "$NAMESPACE/$pod:/www/filebrowser"
  kubectl exec -n "$NAMESPACE" "$pod" -- chmod +x /www/filebrowser
  ok "Uploaded and made executable"
 fi

 ok "In-cluster URL: http://filebrowser-host.${NAMESPACE}.svc.cluster.local/filebrowser"
}
