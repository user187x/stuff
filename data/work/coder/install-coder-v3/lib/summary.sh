# lib/summary.sh — final "you're done" message with copy-paste next steps.

print_summary() {
 step "🎉 Done"
 cat <<EOF
${GRN}${BLD}Coder is ready at:${RST}  ${BLD}https://${DOMAIN}${RST}

${BLD}First-time setup${RST}
  1. Open https://${DOMAIN} in your browser
  2. Login with the Admin credentials listed above!

${BLD}Coder CLI quickstart${RST}
EOF
 if [[ -n "$CLI_PATH" && $CLI_NEEDS_PATH -eq 0 ]]; then
  cat <<EOF
  ${GRN}✓ Installed:${RST} ${CLI_PATH}
  coder login https://${DOMAIN}
  coder templates push my-template -d /path/to/template
EOF
 elif [[ -n "$CLI_PATH" && $CLI_NEEDS_PATH -eq 1 ]]; then
  local shell_rc
  case "${SHELL##*/}" in
  zsh) shell_rc="~/.zshrc" ;;
  bash) shell_rc="~/.bashrc" ;;
  fish) shell_rc="~/.config/fish/config.fish" ;;
  *) shell_rc="your shell rc" ;;
  esac
  cat <<EOF
  ${GRN}✓ Installed:${RST} ${CLI_PATH}
  ${YLW}!${RST} ${CLI_INSTALL_DIR} is not on your PATH.
  Add it:
       echo 'export PATH="${CLI_INSTALL_DIR}:\$PATH"' >> ${shell_rc}
       source ${shell_rc}
  Or run with the full path:
       ${CLI_PATH} login https://${DOMAIN}
EOF
 else
  cat <<EOF
  ${YLW}CLI not auto-installed${RST} — install it manually:
       curl -L https://coder.com/install.sh | sh
  Then:
       coder login https://${DOMAIN}
EOF
 fi

 # Filebrowser hint (only when the host was actually deployed).
 if ! $SKIP_FILEBROWSER && [[ -f "$FILEBROWSER_BINARY" ]]; then
  cat <<EOF

${BLD}Filebrowser (air-gap)${RST}
  In your workspace template (main.tf), fetch the binary from cluster DNS:
       wget -qO /tmp/filebrowser \\
           http://filebrowser-host.${NAMESPACE}.svc.cluster.local/filebrowser
       chmod +x /tmp/filebrowser
EOF
 fi

 cat <<EOF

${BLD}Useful commands${RST}
  $0 --status                                       show status
  $0 --uninstall                                    tear it all down
  kubectl logs -n ${NAMESPACE} -l app.kubernetes.io/name=coder -f
  kubectl get pods,svc,ingress -n ${NAMESPACE}

${DIM}Note: your main.tf references the secret '${CA_SECRET}' for workspace
CA trust — keep --domain consistent with that name.${RST}
EOF
}
