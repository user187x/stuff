/*
 * bt_setup.h  -  Runtime-environment helper for the btmouse HID emulator.
 *
 * A small "class" (a struct + associated methods) that prepares everything
 * the HID device program needs before it can advertise itself:
 *
 *   - ensures the process is running as root (re-execs under sudo if not)
 *   - enables bluetoothd's --compat mode so the legacy SDP socket exists
 *   - unblocks rfkill and powers the adapter up
 *   - marks the adapter pairable + discoverable (no timeout)
 *
 * Target: Ubuntu 26 (systemd + BlueZ). The user only ever has to permit
 * sudo once; everything else is automatic.
 */
#ifndef BT_SETUP_H
#define BT_SETUP_H

#include <stdbool.h>

typedef struct {
    int  dev_id;        /* adapter index, e.g. 0 for hci0            */
    char adapter[16];   /* human name, e.g. "hci0"                   */
    bool verbose;       /* print progress lines (default true)       */
} BtSetup;

/* Initialize the helper. `adapter` may be "hci0", "0", or NULL (=> hci0). */
void bt_setup_init(BtSetup *s, const char *adapter);

/* If not root, re-exec the whole program under sudo (does not return),
 * or exit with instructions if sudo is unavailable. No-op when root. */
void bt_setup_require_root(BtSetup *s, int argc, char **argv);

/* Individual steps. Return 0 on success, -1 on failure. */
int bt_setup_unblock_rfkill(BtSetup *s);
int bt_setup_power_adapter(BtSetup *s);
int bt_setup_ensure_compat(BtSetup *s);
int bt_setup_make_pairable(BtSetup *s);

/* Run every step needed so the HID program can start.
 * Re-execs under sudo first if required. Returns 0 when the environment
 * is ready, -1 if a required step failed. */
int bt_setup_run_all(BtSetup *s, int argc, char **argv);

#endif /* BT_SETUP_H */
