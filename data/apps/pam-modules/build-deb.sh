#!/usr/bin/env bash
# =============================================================================
#  build-deb.sh — package cert-manager + pam_certmanager into a .deb
#
#  Expects the sources next to this script, either flat or with the PAM
#  files in a ./pam-certmanager/ subdirectory (both layouts auto-detected):
#      ./cert-manager                       the CLI (bash, sourceable)
#      ./[pam-certmanager/]pam_certmanager.c
#      ./[pam-certmanager/]cert-manager-pam-hook
#      ./[pam-certmanager/]cert-manager.pam-config
#
#  Produces:  dist/cert-manager_<version>_<arch>.deb
#
#  Usage:  ./build-deb.sh [-v VERSION] [-o OUTDIR] [--no-lintian] [--install]
#          VERSION defaults to the CM_VERSION inside ./cert-manager
#
#  What ends up in the package
#      /usr/bin/cert-manager                        CLI (exec or `source`)
#      /etc/profile.d/cert-manager.sh               sources it in interactive bash
#      /usr/share/bash-completion/completions/cert-manager  (+ /etc/bash_completion.d copy)
#      /usr/lib/<multiarch>/security/pam_certmanager.so
#      /usr/lib/cert-manager/cert-manager-pam-hook  helper run as the user
#      /usr/share/pam-configs/cert-manager          pam-auth-update profile
#      /usr/share/man/man1/cert-manager.1.gz
#      /usr/share/doc/cert-manager/{README.md,changelog.gz,copyright}
#  postinst enables the PAM profile (pam-auth-update); prerm removes it.
# =============================================================================
set -euo pipefail

PKG=cert-manager
MAINTAINER="${DEBEMAIL:-DevOps <devops@example.com>}"
SRC_DIR=$(cd "$(dirname "$(readlink -f "$0")")" && pwd)
OUT_DIR="$SRC_DIR/dist"
VERSION=""
RUN_LINTIAN=1
DO_INSTALL=0

usage() {
 sed -n '2,/^# ====/p' "$0" | sed 's/^# \{0,2\}//' | sed '$d'
 exit "${1:-0}"
}
say() { printf '\e[34m•\e[0m %s\n' "$*"; }
ok() { printf '\e[32m✔\e[0m %s\n' "$*"; }
die() {
 printf '\e[31m✘ %s\e[0m\n' "$*" >&2
 exit 1
}

while (($#)); do
 case $1 in
  -v | --version)
   VERSION=$2
   shift
   ;;
  -o | --out)
   OUT_DIR=$2
   shift
   ;;
  --no-lintian) RUN_LINTIAN=0 ;;
  --install) DO_INSTALL=1 ;;
  -h | --help) usage 0 ;;
  *) die "unknown option $1" ;;
 esac
 shift
done

# ------------------------------------------------------------- sanity checks
CLI="$SRC_DIR/cert-manager"
# PAM sources may live in ./pam-certmanager/ or flat next to this script
if [[ -f $SRC_DIR/pam-certmanager/pam_certmanager.c ]]; then
 PAMSRC="$SRC_DIR/pam-certmanager"
elif [[ -f $SRC_DIR/pam_certmanager.c ]]; then
 PAMSRC="$SRC_DIR"
else die "cannot find pam_certmanager.c (looked in $SRC_DIR and $SRC_DIR/pam-certmanager)"; fi
[[ -f $CLI ]] || die "missing $CLI"
[[ -f $PAMSRC/pam_certmanager.c ]] || die "missing $PAMSRC/pam_certmanager.c"
[[ -f $PAMSRC/cert-manager-pam-hook ]] || die "missing $PAMSRC/cert-manager-pam-hook"
[[ -f $PAMSRC/cert-manager.pam-config ]] || die "missing $PAMSRC/cert-manager.pam-config"
bash -n "$CLI" || die "cert-manager has syntax errors"
bash -n "$PAMSRC/cert-manager-pam-hook" || die "cert-manager-pam-hook has syntax errors"

[[ -n $VERSION ]] || VERSION=$(sed -n 's/^CM_VERSION="\([^"]*\)".*/\1/p' "$CLI" | head -1)
[[ -n $VERSION ]] || VERSION="1.0.0"
[[ $VERSION =~ ^[0-9][A-Za-z0-9.+~-]*$ ]] || die "bad version '$VERSION'"

# build dependencies
need=()
command -v dpkg-deb >/dev/null || need+=(dpkg-dev)
command -v gcc >/dev/null || need+=(gcc)
command -v gzip >/dev/null || need+=(gzip)
[[ -f /usr/include/security/pam_modules.h ]] || need+=(libpam0g-dev)
command -v dpkg-architecture >/dev/null || need+=(dpkg-dev)
if ((${#need[@]})); then
 say "installing build deps: ${need[*]}"
 SUDO=""
 [[ $EUID -ne 0 ]] && SUDO=sudo
 $SUDO apt-get update -qq && $SUDO apt-get install -y -qq "${need[@]}"
fi

ARCH=$(dpkg --print-architecture)
MULTIARCH=$(dpkg-architecture -qDEB_HOST_MULTIARCH)
PAM_DIR="/usr/lib/$MULTIARCH/security"
LIBEXEC="/usr/lib/$PKG"
HOOK="$LIBEXEC/cert-manager-pam-hook"

WORK=$(mktemp -d /tmp/${PKG}-build.XXXXXX)
ROOT="$WORK/root"
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$OUT_DIR"
say "building $PKG $VERSION for $ARCH  (work dir $WORK)"

# ------------------------------------------------------------ compile module
say "compiling pam_certmanager.so (hook path baked in: $HOOK)"
gcc -O2 -Wall -Wextra -fPIC -D_GNU_SOURCE -DDEFAULT_HOOK="\"$HOOK\"" -shared \
 -Wl,-z,relro,-z,now -Wl,--as-needed \
 -o "$WORK/pam_certmanager.so" "$PAMSRC/pam_certmanager.c" -lpam
strip --strip-unneeded "$WORK/pam_certmanager.so" 2>/dev/null || true

# ------------------------------------------------------------ stage the tree
inst() { install -D -m "$1" "$2" "$ROOT$3"; } # mode src dest

inst 0755 "$CLI" /usr/bin/cert-manager
inst 0644 "$WORK/pam_certmanager.so" "$PAM_DIR/pam_certmanager.so"
inst 0755 "$PAMSRC/cert-manager-pam-hook" "$HOOK"
inst 0644 "$PAMSRC/cert-manager.pam-config" /usr/share/pam-configs/cert-manager
[[ -f $PAMSRC/README.md ]] && inst 0644 "$PAMSRC/README.md" /usr/share/doc/$PKG/README.pam.md || true

# make the CLI a shell resource in every interactive bash session
mkdir -p "$ROOT/etc/profile.d"
cat >"$ROOT/etc/profile.d/cert-manager.sh" <<'EOF'
# cert-manager: load the CLI as a shell function (needed for `env`/`unenv`/completion)
if [ -n "$BASH_VERSION" ] && [ -r /usr/bin/cert-manager ]; then
  case $- in *i*) . /usr/bin/cert-manager ;; esac
fi
EOF
chmod 0644 "$ROOT/etc/profile.d/cert-manager.sh"

# bash-completion (also loads the functions on demand)
mkdir -p "$ROOT/usr/share/bash-completion/completions"
cat >"$ROOT/usr/share/bash-completion/completions/cert-manager" <<'EOF'
# bash completion for cert-manager — sourcing the CLI registers its completer
[ -r /usr/bin/cert-manager ] && . /usr/bin/cert-manager
EOF
chmod 0644 "$ROOT/usr/share/bash-completion/completions/cert-manager"
# eager load for interactive non-login shells (bash-completion sources this dir at startup)
mkdir -p "$ROOT/etc/bash_completion.d"
cp "$ROOT/usr/share/bash-completion/completions/cert-manager" "$ROOT/etc/bash_completion.d/cert-manager"
chmod 0644 "$ROOT/etc/bash_completion.d/cert-manager"

# ------------------------------------------------------------ man page
mkdir -p "$ROOT/usr/share/man/man1"
cat >"$WORK/cert-manager.1" <<EOF
.TH CERT-MANAGER 1 "$(date +%Y-%m-%d)" "cert-manager $VERSION" "User Commands"
.SH NAME
cert-manager \- timed unlock for password-protected TLS client certificates
.SH SYNOPSIS
.B cert-manager
[\fB\-e\fR \fIentry\fR] \fIcommand\fR [\fIargs\fR]
.SH DESCRIPTION
Stores a PEM (certificate + encrypted private key) together with its key password
in \fBpass\fR(1), and lets you unlock it once for a limited window (default 8h) so
repeated \fBcurl\fR(1) TLS calls need no password. Runtime secrets live in
\$XDG_RUNTIME_DIR and are wiped when the window expires, on \fBlock\fR, or on logout
via \fBpam_certmanager\fR(8).
.SH COMMANDS
.TP
\fBsetup\fR  ensure pass, gpg, openssl, curl are installed and initialised
.TP
\fBadd\fR \fIname\fR \fIfile.pem\fR  store PEM and its password
.TP
\fBlist\fR, \fBuse\fR \fIname\fR, \fBremove\fR \fIname\fR, \fBtimeout\fR [\fIduration\fR]
.TP
\fBunlock\fR [\fIname\fR] [\fIduration\fR], \fBlock\fR [\fIname\fR|\fB\-\-all\fR], \fBextend\fR [\fIname\fR] [\fIduration\fR]
.TP
\fBstatus\fR [\fIname\fR]  lock details, remaining time, file and certificate info
.TP
\fBremaining\fR [\fIname\fR]  live countdown with progress bar
.TP
\fBcurl\fR \fIargs\fR..., \fBpassword\fR [\fB\-\-copy\fR], \fBenv\fR [\fB\-\-print\fR], \fBunenv\fR, \fBexport\fR [\fIname\fR] [\fIout\fR] [\fB\-\-cert\-only\fR|\fB\-\-decrypt\-key\fR]
.SH DURATIONS
8h, 45m, 1h30m, 2d, 90s (a bare number means hours).
.SH FILES
~/.config/cert-manager, \$XDG_RUNTIME_DIR/cert-manager-<uid>, ~/.password-store/cert-manager/
.SH SEE ALSO
pass(1), gpg(1), curl(1), openssl(1)
EOF
gzip -9n -c "$WORK/cert-manager.1" >"$ROOT/usr/share/man/man1/cert-manager.1.gz"

cat >"$WORK/pam_certmanager.8" <<EOF
.TH PAM_CERTMANAGER 8 "$(date +%Y-%m-%d)" "cert-manager $VERSION" "System Administration"
.SH NAME
pam_certmanager \- relock cert-manager entries when a user logs out
.SH SYNOPSIS
session optional pam_certmanager.so [hook=\fIpath\fR] [lock_on_open=yes|no] [always] [min_uid=\fIN\fR] [debug]
.SH DESCRIPTION
On session close (last session of the user) and on session open, drops privileges to the
user and runs $HOOK, which wipes unlocked runtime secrets, kills auto-relock timers and
stops gpg-agent. Never fails a login. Enabled automatically via pam-auth-update.
EOF
mkdir -p "$ROOT/usr/share/man/man8"
gzip -9n -c "$WORK/pam_certmanager.8" >"$ROOT/usr/share/man/man8/pam_certmanager.8.gz"

# ------------------------------------------------------------ doc files
mkdir -p "$ROOT/usr/share/doc/$PKG"
cat >"$WORK/changelog" <<EOF
$PKG ($VERSION) stable; urgency=medium

  * Packaged build of cert-manager CLI and pam_certmanager PAM module.

 -- $MAINTAINER  $(date -R)
EOF
gzip -9n -c "$WORK/changelog" >"$ROOT/usr/share/doc/$PKG/changelog.gz"
cat >"$ROOT/usr/share/doc/$PKG/copyright" <<EOF
Format: https://www.debian.org/doc/packaging-manuals/copyright-format/1.0/
Upstream-Name: $PKG

Files: *
Copyright: $(date +%Y) $MAINTAINER
License: MIT
 Permission is hereby granted, free of charge, to any person obtaining a copy of
 this software and associated documentation files (the "Software"), to deal in
 the Software without restriction, subject to the standard MIT conditions.
 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND.
EOF
cat >"$ROOT/usr/share/doc/$PKG/README.md" <<EOF
# $PKG $VERSION

Timed unlock for password-protected TLS client certificates.

* CLI: \`cert-manager help\` — sourced automatically in interactive bash via
  /etc/profile.d/cert-manager.sh (open a new shell after install).
* PAM: pam_certmanager.so is enabled through pam-auth-update and relocks all
  entries when the user logs out and on login (stale state).
* Per-user first run: \`cert-manager setup\` then \`cert-manager add <name> <file.pem>\`.
EOF

# ------------------------------------------------------------ DEBIAN/ control
mkdir -p "$ROOT/DEBIAN"
INSTALLED_SIZE=$(du -sk --exclude=DEBIAN "$ROOT" | cut -f1)
cat >"$ROOT/DEBIAN/control" <<EOF
Package: $PKG
Version: $VERSION
Section: utils
Priority: optional
Architecture: $ARCH
Maintainer: $MAINTAINER
Installed-Size: $INSTALLED_SIZE
Depends: bash (>= 4.4), pass, gnupg, openssl, curl, coreutils, libpam0g, libpam-runtime (>= 1.0.1-6)
Recommends: gpg-agent, bash-completion, systemd
Suggests: xclip, wl-clipboard
Homepage: https://example.com/cert-manager
Description: timed unlock for password-protected TLS client certificates
 Stores a PEM certificate together with its private-key password in pass(1)
 and unlocks it for a limited window (default 8 hours) so repeated TLS curl
 calls need no password prompt. Includes lock/unlock/extend/status commands,
 a live countdown, PEM export, and a PAM session module (pam_certmanager)
 that relocks every entry when the user logs out and wipes stale state on
 login, so an entry is never open while its owner is not logged in.
EOF

cat >"$ROOT/DEBIAN/conffiles" <<EOF
/etc/profile.d/cert-manager.sh
/etc/bash_completion.d/cert-manager
EOF

cat >"$ROOT/DEBIAN/postinst" <<'EOF'
#!/bin/sh
set -e
case "$1" in
  configure)
    if command -v pam-auth-update >/dev/null 2>&1; then
      pam-auth-update --package --enable cert-manager || true
    fi
    ;;
esac
exit 0
EOF

cat >"$ROOT/DEBIAN/prerm" <<'EOF'
#!/bin/sh
set -e
case "$1" in
  remove|deconfigure)
    if command -v pam-auth-update >/dev/null 2>&1; then
      pam-auth-update --package --remove cert-manager || true
    fi
    ;;
esac
exit 0
EOF

cat >"$ROOT/DEBIAN/postrm" <<'EOF'
#!/bin/sh
set -e
# per-user data (~/.config/cert-manager, pass entries) is deliberately left alone
exit 0
EOF
chmod 0755 "$ROOT/DEBIAN/postinst" "$ROOT/DEBIAN/prerm" "$ROOT/DEBIAN/postrm"

# md5sums
(cd "$ROOT" && find . -type f -not -path './DEBIAN/*' -printf '%P\n' | sort | xargs md5sum >DEBIAN/md5sums)

# ------------------------------------------------------------ build
DEB="$OUT_DIR/${PKG}_${VERSION}_${ARCH}.deb"
say "assembling $DEB"
dpkg-deb --root-owner-group --build "$ROOT" "$DEB" >/dev/null
ok "built $(du -h "$DEB" | cut -f1)  $DEB"

# ------------------------------------------------------------ verify
say "package contents:"
dpkg-deb -c "$DEB" | awk '{print "   " $NF}' | grep -v '/$'
if ((RUN_LINTIAN)) && command -v lintian >/dev/null 2>&1; then
 say "lintian:"
 lintian --no-tag-display-limit "$DEB" || true
fi

if ((DO_INSTALL)); then
 SUDO=""
 [[ $EUID -ne 0 ]] && SUDO=sudo
 say "installing"
 $SUDO apt-get install -y "$DEB"
 ok "installed — open a new shell and run: cert-manager help"
fi

echo
echo "Install with:   sudo apt install ./$(realpath --relative-to="$PWD" "$DEB")"
echo "Remove with:    sudo apt remove $PKG"
