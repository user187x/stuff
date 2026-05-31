# lib/hosts.sh — add (or refresh) a /etc/hosts entry pointing DOMAIN at minikube.

update_hosts() {
 step "Local DNS (/etc/hosts)"
 if $SKIP_HOSTS; then
  warn "Skipping /etc/hosts (--skip-hosts)"
  return
 fi

 local target_ip marker hosts_file
 target_ip=$(minikube ip -p "$MINIKUBE_PROFILE")
 marker="# install-coder:${DOMAIN}"
 hosts_file="/etc/hosts"

 if grep -qF "$marker" "$hosts_file" 2>/dev/null; then
  local existing_ip
  existing_ip=$(awk -v m="$marker" 'index($0,m){print $1; exit}' "$hosts_file")
  if [[ "$existing_ip" == "$target_ip" ]]; then
   ok "/etc/hosts already maps ${DOMAIN} → ${target_ip}"
   return
  fi
  warn "Replacing stale entry: ${existing_ip} → ${target_ip}"
  local tmp
  tmp=$(mktemp)
  grep -vF "$marker" "$hosts_file" >"$tmp"
  echo "${target_ip} ${DOMAIN} ${marker}" >>"$tmp"
  sudo cp "$tmp" "$hosts_file"
  rm -f "$tmp"
 else
  log "Adding ${DOMAIN} → ${target_ip} (sudo prompt incoming)…"
  echo "${target_ip} ${DOMAIN} ${marker}" | sudo tee -a "$hosts_file" >/dev/null
 fi
 ok "/etc/hosts updated"
}
