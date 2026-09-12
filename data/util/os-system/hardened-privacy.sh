#!/bin/bash

# -----------------------------------------------------------------------------
# Ubuntu 26 Hardening & Telemetry Removal Script (Advanced Privacy Edition)
# -----------------------------------------------------------------------------
# Safely disables multi-layered tracking, MOTD remote advertising, crash dumps,
# and system telemetry without breaking core desktop features (printing, search).
# -----------------------------------------------------------------------------

set -e

# Ensure the script is run as root
if [ "$EUID" -ne 0 ]; then
 echo "[-] Error: This script must be run as root (using sudo)." >&2
 exit 1
fi

echo "[+] Initializing advanced privacy and telemetry cleanup..."

# -----------------------------------------------------------------------------
# 1. GNOME Privacy Hardening (Location, Analytics, Web Search)
# -----------------------------------------------------------------------------
echo "[+] Securing GNOME privacy settings..."
GLIB_PRIVACY_DIR="/usr/share/glib-2.0/schemas"

cat <<EOF >"${GLIB_PRIVACY_DIR}/99_ubuntu_privacy_hardening.gschema.override"
[org.gnome.desktop.privacy]
remember-recent-files=false
remember-app-usage=false
send-software-usage-stats=false
report-technical-problems=false
show-full-name-in-top-bar=false

[org.gnome.system.location]
enabled=false

[org.gnome.desktop.search-providers]
disable-external=true

[org.freedesktop.Tracker3.Miner.Files]
crawler-interval-days=-1
index-on-battery=false
index-removable-devices=false
EOF

# Compile the new schema overrides
glib-compile-schemas "${GLIB_PRIVACY_DIR}"

# Note: We do NOT mask tracker-miner-fs-3.service. Masking it breaks local file
# search in Nautilus and the Activities overview. We only mask the RSS miner.
echo "[+] Masking unnecessary GNOME Tracker backend services..."
systemctl --global mask tracker-miner-rss-3.service || true

# -----------------------------------------------------------------------------
# 2. Purge MOTD News / Remote Marketing Advertisements / ESM Nagging
# -----------------------------------------------------------------------------
echo "[+] Removing dynamic Message-of-the-Day (MOTD) remote trackers..."
if [ -d "/etc/update-motd.d" ]; then
 [ -f "/etc/update-motd.d/50-motd-news" ] && chmod -x /etc/update-motd.d/50-motd-news
 [ -f "/etc/update-motd.d/88-esm-announce" ] && chmod -x /etc/update-motd.d/88-esm-announce
 [ -f "/etc/update-motd.d/91-contract-ua-esm-status" ] && chmod -x /etc/update-motd.d/91-contract-ua-esm-status
fi

# Edit config to permanently enforce motd-news disabling
if [ -f "/etc/default/motd-news" ]; then
 sed -i 's/ENABLED=1/ENABLED=0/g' /etc/default/motd-news
fi

# Disable APT hooks that phone home for Ubuntu Pro/ESM status
if [ -f "/etc/apt/apt.conf.d/20apt-esm-hook.conf" ]; then
 mv /etc/apt/apt.conf.d/20apt-esm-hook.conf /etc/apt/apt.conf.d/20apt-esm-hook.conf.disabled
fi

# -----------------------------------------------------------------------------
# 3. Secure Snapd & Disable App Telemetry Logs
# -----------------------------------------------------------------------------
echo "[+] Restricting Snap Store telemetry channels..."
if command -v snap &>/dev/null; then
 # Snap does not have a direct telemetry toggle, but managing the timer
 # prevents unexpected background updates during sensitive times.
 snap set system refresh.timer=managed || true
fi

# -----------------------------------------------------------------------------
# 4. Silence Network Broadcasting (Avahi Daemon) - MODIFIED FOR SAFETY
# -----------------------------------------------------------------------------
# WARNING: Disabling Avahi completely breaks modern driverless printing (CUPS/IPP)
# and local network casting. It is commented out by default to preserve features.
# Uncomment the following three lines ONLY if you never use local network printers.
#
# echo "[+] Disabling Avahi Daemon (mDNS local network tracking/broadcasting)..."
# systemctl stop avahi-daemon.socket avahi-daemon.service || true
# systemctl disable avahi-daemon.socket avahi-daemon.service || true
# systemctl mask avahi-daemon.socket avahi-daemon.service || true

# -----------------------------------------------------------------------------
# 5. Disable Systemd Connectivity Checking & MAC Randomization
# -----------------------------------------------------------------------------
echo "[+] Hardening NetworkManager (MAC Randomization & Connectivity Checks)..."

# Randomize Wi-Fi MAC address during scanning to prevent physical location tracking
NM_PRIVACY_CONF="/etc/NetworkManager/conf.d/30-mac-randomization.conf"
cat <<EOF >"$NM_PRIVACY_CONF"
[device]
wifi.scan-rand-mac-address=yes
EOF

# Note: Disabling connectivity checking stops automatic Captive Portal popups
# (e.g., hotel/airport WiFi). You will have to navigate to a router IP manually.
NM_CONN_CONF="/etc/NetworkManager/conf.d/20-connectivity-ubuntu.conf"
if [ -f "$NM_CONN_CONF" ]; then
 cat <<EOF >"$NM_CONN_CONF"
[connectivity]
enabled=false
EOF
fi
systemctl restart NetworkManager || true

# -----------------------------------------------------------------------------
# 6. Kernel & Network Fingerprint Hardening (Sysctl)
# -----------------------------------------------------------------------------
echo "[+] Applying Kernel and Network anti-fingerprinting rules..."
cat <<EOF >/etc/sysctl.d/99-privacy-hardening.conf
# Restrict access to kernel logs
kernel.dmesg_restrict = 1
# Disable TCP timestamps to reduce remote system fingerprinting
net.ipv4.tcp_timestamps = 0
EOF
sysctl --system >/dev/null

# -----------------------------------------------------------------------------
# 7. Stop Kernel Crash Dumps (Apport Daemon)
# -----------------------------------------------------------------------------
echo "[+] Disabling Apport Error Reporting..."
if [ -f "/etc/default/apport" ]; then
 sed -i 's/enabled=1/enabled=0/g' /etc/default/apport
fi
systemctl stop apport.service || true
systemctl disable apport.service || true
systemctl mask apport.service || true

echo "========================================="
echo "🛡️  Starting Ubuntu Telemetry Removal 🛡️"
echo "========================================="

# Send explicit opt-out signal via ubuntu-report (if active)
if command -v ubuntu-report &>/dev/null; then
 echo "➡️  Sending explicit opt-out signal to Canonical..."
 ubuntu-report -f send no 2>/dev/null || true
fi

# Purge metric gathering, system reporting, and popularity tracking utilities
echo "➡️  Purging tracking, reporting, and insights packages..."
DEBIAN_FRONTEND=noninteractive apt-get purge -y \
 ubuntu-report \
 popularity-contest \
 whoopsie \
 ubuntu-insights 2>/dev/null || true

# Clean up unneeded dependencies left behind
echo "➡️  Cleaning up unneeded dependencies..."
apt-get autoremove -y >/dev/null

# Stop, disable, and mask any surviving telemetry services
echo "➡️  Disabling and masking reporting background services..."
SERVICES=("whoopsie.service" "ubuntu-report.service" "ubuntu-insights.service")

for service in "${SERVICES[@]}"; do
 if systemctl list-unit-files | grep -q "^$service"; then
  systemctl stop "$service" 2>/dev/null || true
  systemctl disable "$service" 2>/dev/null || true
  systemctl mask "$service" 2>/dev/null || true
  echo "✅ $service has been stopped, disabled, and masked."
 fi
done

echo "========================================="
echo "🎉 Telemetry successfully neutralized! 🎉"
echo "========================================="
