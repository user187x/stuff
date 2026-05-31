# lib/admin.sh — create the Coder first-user (admin) via the CLI.
# Called by the orchestrator only when install_cli succeeded.

create_admin() {
 step "First-user (admin) account"
 local coder_bin="${CLI_PATH:-coder}"

 if ! { [[ -x "$coder_bin" ]] || command -v "$coder_bin" >/dev/null 2>&1; }; then
  warn "Coder CLI not found; skipping admin creation."
  return
 fi

 log "Creating admin account..."

 ADMIN_USER="admin"
 ADMIN_EMAIL="admin@${DOMAIN}"
 ADMIN_PASSWORD="Changeit1!"

 local tmp_log
 tmp_log=$(mktemp)

 # 1. CODER_TLS_NO_VERIFY=1 in case the local CA isn't fully trusted by Go yet.
 # 2. --first-user-trial=false skips the enterprise trial prompt.
 # 3. </dev/null so it fails fast instead of hanging on unforeseen prompts.
 if env CODER_TLS_NO_VERIFY=1 "$coder_bin" login "https://${DOMAIN}" \
  --first-user-username "$ADMIN_USER" \
  --first-user-email "$ADMIN_EMAIL" \
  --first-user-password "$ADMIN_PASSWORD" \
  --first-user-trial=false \
  </dev/null >"$tmp_log" 2>&1; then

  ok "Admin account created!"
  echo
  echo "    Username  : $ADMIN_USER"
  echo "    Email     : $ADMIN_EMAIL"
  echo "    Password  : $ADMIN_PASSWORD"
  echo
 else
  warn "Failed to create admin account automatically (it might already exist)."
  warn "Logs from the attempt:"
  sed 's/^/    /' "$tmp_log" >&2
 fi

 rm -f "$tmp_log"
}
