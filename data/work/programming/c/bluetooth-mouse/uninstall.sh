#!/usr/bin/env bash
#
# uninstall.sh  -  Fully remove btmouse and revert every system change it made.
#
# Reverts:
#   - stops any running btmouse process
#   - removes the bluetoothd "--compat" systemd drop-in and restarts bluetooth
#   - deletes the installed binary (/usr/local/bin) and the local build
#
# Paired devices are left in place (that's your data); instructions to remove
# them are printed at the end.
#
# Run it however you like; it will re-launch itself under sudo if needed.

set -u

# --- ensure root (self-escalate) -------------------------------------------
if [ "$(id -u)" -ne 0 ]; then
    echo "[uninstall] Root required; re-running under sudo..."
    exec sudo -- "$0" "$@"
fi

echo "[uninstall] Stopping any running btmouse process..."
if pkill -INT -x btmouse 2>/dev/null; then
    # give it a moment to unregister the agent + SDP record cleanly
    sleep 1
fi
# force-kill anything that didn't exit
pkill -KILL -x btmouse 2>/dev/null || true

echo "[uninstall] Stopping any running btmouse-gui process..."
pkill -TERM -x btmouse-gui 2>/dev/null || true
pkill -KILL -x btmouse-gui 2>/dev/null || true

# --- remove the bluetoothd compat drop-in ----------------------------------
DROPIN_DIR="/etc/systemd/system/bluetooth.service.d"
DROPIN="$DROPIN_DIR/10-compat.conf"

if [ -f "$DROPIN" ]; then
    echo "[uninstall] Removing bluetoothd --compat drop-in ($DROPIN)..."
    rm -f "$DROPIN"
    # remove the directory if it's now empty
    rmdir "$DROPIN_DIR" 2>/dev/null || true

    if command -v systemctl >/dev/null 2>&1; then
        echo "[uninstall] Reloading systemd and restarting bluetooth..."
        systemctl daemon-reload
        systemctl restart bluetooth 2>/dev/null || true
    fi
else
    echo "[uninstall] No compat drop-in found (nothing to revert there)."
fi

# --- remove binaries -------------------------------------------------------
for p in /usr/local/bin/btmouse /usr/bin/btmouse \
         /usr/local/bin/btmouse-gui /usr/bin/btmouse-gui; do
    if [ -f "$p" ]; then
        echo "[uninstall] Removing $p"
        rm -f "$p"
    fi
done

# local build artifacts (in the directory the script is run from)
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
for p in "$SCRIPT_DIR/btmouse" "$SCRIPT_DIR/btmouse-gui"; do
    if [ -f "$p" ]; then
        echo "[uninstall] Removing $p"
        rm -f "$p"
    fi
done

echo
echo "[uninstall] Done. Bluetooth has been restored to normal (non-compat) mode."
echo "[uninstall] Paired devices were left untouched. To forget one:"
echo "             bluetoothctl -- devices          # list paired devices"
echo "             bluetoothctl -- remove <MAC>     # forget a device"
