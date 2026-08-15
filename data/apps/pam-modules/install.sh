#!/usr/bin/env bash
# One-shot installer for pam_certmanager on Ubuntu/Debian.
#   sudo ./install.sh          build + install + enable via pam-auth-update
#   sudo ./install.sh remove   disable + uninstall
set -euo pipefail
cd "$(dirname "$(readlink -f "$0")")"
[[ $EUID -eq 0 ]] || {
 echo "run as root (sudo)"
 exit 1
}

if [[ ${1:-} == remove ]]; then
 pam-auth-update --package --remove cert-manager 2>/dev/null || true
 make uninstall
 echo "pam_certmanager removed."
 exit 0
fi

# build deps
need=()
command -v gcc >/dev/null || need+=(gcc)
command -v make >/dev/null || need+=(make)
[[ -f /usr/include/security/pam_modules.h ]] || need+=(libpam0g-dev)
if ((${#need[@]})); then
 apt-get update -qq && apt-get install -y -qq "${need[@]}"
fi

make clean all install
echo "installed module to: $(make -s -f - \
 print <<<'include Makefile
print: ; @echo $(PAM_DIR)')"

# enable in the common-session stack (after pam_systemd, priority -10)
pam-auth-update --package --enable cert-manager
grep -q pam_certmanager /etc/pam.d/common-session && echo "enabled in /etc/pam.d/common-session:" &&
 grep pam_certmanager /etc/pam.d/common-session

cat <<'MSG'

Done. Verify with:
  sudo -u <user> XDG_RUNTIME_DIR=/run/user/$(id -u <user>) CM_PAM_ALWAYS=1 \
       /usr/local/libexec/cert-manager-pam-hook close <user>
  journalctl -t cert-manager-pam -f     # or: grep cert-manager-pam /var/log/auth.log
MSG
