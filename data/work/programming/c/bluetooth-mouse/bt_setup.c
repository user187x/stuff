/*
 * bt_setup.c  -  Implementation of the btmouse runtime-environment helper.
 * See bt_setup.h for the overview. Target OS: Ubuntu 26 (systemd + BlueZ).
 */
#define _GNU_SOURCE
#include "bt_setup.h"

#include <stdio.h>
#include <stdlib.h>
#include <stdarg.h>
#include <string.h>
#include <unistd.h>
#include <errno.h>
#include <limits.h>

#include <sys/socket.h>
#include <sys/ioctl.h>
#include <sys/stat.h>
#include <sys/wait.h>

#include <bluetooth/bluetooth.h>
#include <bluetooth/hci.h>

/* ------------------------------------------------------------------ *
 *  Small internal utilities
 * ------------------------------------------------------------------ */

/* Is a command available on PATH? */
static int have_cmd(const char *name)
{
    char buf[256];
    snprintf(buf, sizeof(buf), "command -v %s >/dev/null 2>&1", name);
    return system(buf) == 0;
}

/* Run a shell command (printf-style). Returns the command's exit status,
 * or -1 if it could not be launched. */
static int run(const char *fmt, ...)
{
    char cmd[512];
    va_list ap;
    va_start(ap, fmt);
    vsnprintf(cmd, sizeof(cmd), fmt, ap);
    va_end(ap);

    int rc = system(cmd);
    if (rc == -1) return -1;
    return WEXITSTATUS(rc);
}

static int path_exists(const char *p)
{
    struct stat st;
    return stat(p, &st) == 0;
}

/* The compat SDP socket lives at /run/sdp (older layouts: /var/run/sdp). */
static int sdp_socket_present(void)
{
    return path_exists("/run/sdp") || path_exists("/var/run/sdp");
}

/* ------------------------------------------------------------------ *
 *  Public methods
 * ------------------------------------------------------------------ */

void bt_setup_init(BtSetup *s, const char *adapter)
{
    memset(s, 0, sizeof(*s));
    s->verbose = true;

    if (adapter == NULL) {
        s->dev_id = 0;
    } else if (strncmp(adapter, "hci", 3) == 0) {
        s->dev_id = atoi(adapter + 3);
    } else {
        s->dev_id = atoi(adapter);
    }
    if (s->dev_id < 0 || s->dev_id > 999) s->dev_id = 0;

    snprintf(s->adapter, sizeof(s->adapter), "hci%d", s->dev_id);
}

void bt_setup_require_root(BtSetup *s, int argc, char **argv)
{
    (void)s;
    if (geteuid() == 0) return;   /* already root */

    fprintf(stderr,
        "[setup] Root privileges are required.\n"
        "[setup] Re-launching under sudo (you may be prompted for your "
        "password)...\n");

    if (!have_cmd("sudo")) {
        fprintf(stderr,
            "[setup] 'sudo' is not installed. Please run this program as "
            "root, e.g.:\n"
            "        su -c '%s'\n", argv[0]);
        exit(1);
    }

    /* Resolve our own absolute path so sudo can find us regardless of cwd. */
    char exe[PATH_MAX];
    ssize_t n = readlink("/proc/self/exe", exe, sizeof(exe) - 1);
    if (n < 0) {
        strncpy(exe, argv[0], sizeof(exe) - 1);
        exe[sizeof(exe) - 1] = '\0';
    } else {
        exe[n] = '\0';
    }

    /* Build:  sudo <exe> <original args...> */
    char **nv = malloc((size_t)(argc + 2) * sizeof(char *));
    if (!nv) { perror("[setup] malloc"); exit(1); }
    nv[0] = "sudo";
    nv[1] = exe;
    for (int i = 1; i < argc; i++) nv[i + 1] = argv[i];
    nv[argc + 1] = NULL;

    execvp("sudo", nv);
    perror("[setup] execvp(sudo)");   /* only reached on failure */
    exit(1);
}

int bt_setup_unblock_rfkill(BtSetup *s)
{
    (void)s;
    if (have_cmd("rfkill")) {
        run("rfkill unblock bluetooth >/dev/null 2>&1");
        if (s->verbose) printf("[setup] rfkill: bluetooth unblocked.\n");
        return 0;
    }
    fprintf(stderr, "[setup] rfkill not found; skipping soft-block check.\n");
    return 0;   /* non-fatal */
}

int bt_setup_power_adapter(BtSetup *s)
{
    int ctl = socket(AF_BLUETOOTH, SOCK_RAW, BTPROTO_HCI);
    if (ctl < 0) {
        perror("[setup] socket(HCI)");
        return -1;
    }

    if (ioctl(ctl, HCIDEVUP, s->dev_id) < 0) {
        if (errno == EALREADY) {
            if (s->verbose) printf("[setup] %s already up.\n", s->adapter);
            close(ctl);
            return 0;
        }
        fprintf(stderr, "[setup] Could not bring %s up: %s\n",
                s->adapter, strerror(errno));
        fprintf(stderr, "[setup]   (Is a Bluetooth adapter present? "
                "Check `ls /sys/class/bluetooth`.)\n");
        close(ctl);
        return -1;
    }

    if (s->verbose) printf("[setup] %s powered up.\n", s->adapter);
    close(ctl);
    return 0;
}

int bt_setup_ensure_compat(BtSetup *s)
{
    (void)s;

    if (sdp_socket_present()) {
        if (s->verbose)
            printf("[setup] bluetoothd compat mode already active.\n");
        return 0;
    }

    if (!have_cmd("systemctl")) {
        fprintf(stderr,
            "[setup] systemctl not found; cannot auto-enable compat mode.\n"
            "[setup] Start bluetoothd manually with --compat.\n");
        return -1;
    }

    /* Locate the bluetoothd binary (path varies by distro/version). */
    const char *candidates[] = {
        "/usr/libexec/bluetooth/bluetoothd",
        "/usr/lib/bluetooth/bluetoothd",
        "/usr/sbin/bluetoothd",
        NULL
    };
    const char *daemon = NULL;
    for (int i = 0; candidates[i]; i++) {
        if (path_exists(candidates[i])) { daemon = candidates[i]; break; }
    }
    if (!daemon) {
        fprintf(stderr, "[setup] Could not locate the bluetoothd binary.\n");
        return -1;
    }

    printf("[setup] Enabling bluetoothd --compat via systemd drop-in...\n");
    run("mkdir -p /etc/systemd/system/bluetooth.service.d");

    const char *override =
        "/etc/systemd/system/bluetooth.service.d/10-compat.conf";
    FILE *f = fopen(override, "w");
    if (!f) {
        perror("[setup] writing systemd override");
        return -1;
    }
    fprintf(f,
        "# Added automatically by btmouse bt_setup helper.\n"
        "# Enables the legacy SDP socket (/run/sdp) needed for HID device\n"
        "# registration. Remove this file to revert.\n"
        "[Service]\n"
        "ExecStart=\n"
        "ExecStart=%s --compat\n", daemon);
    fclose(f);

    run("systemctl daemon-reload");
    if (run("systemctl restart bluetooth") != 0) {
        fprintf(stderr, "[setup] Failed to restart the bluetooth service.\n");
        return -1;
    }

    /* Wait (up to ~10s) for the compat SDP socket to appear. */
    for (int i = 0; i < 50; i++) {
        if (sdp_socket_present()) {
            printf("[setup] Compat SDP socket is up.\n");
            return 0;
        }
        usleep(200 * 1000);   /* 0.2s */
    }

    fprintf(stderr, "[setup] Timed out waiting for the SDP socket (/run/sdp).\n");
    return -1;
}

int bt_setup_make_pairable(BtSetup *s)
{
    if (!have_cmd("bluetoothctl")) {
        fprintf(stderr,
            "[setup] bluetoothctl not found; enable pairing manually.\n");
        return -1;
    }

    /* Non-interactive one-shot commands. Errors here are non-fatal. */
    run("bluetoothctl -- power on            >/dev/null 2>&1");
    run("bluetoothctl -- pairable on         >/dev/null 2>&1");
    run("bluetoothctl -- discoverable-timeout 0 >/dev/null 2>&1");
    run("bluetoothctl -- discoverable on     >/dev/null 2>&1");

    if (s->verbose)
        printf("[setup] Adapter is pairable + discoverable (no timeout).\n");
    return 0;
}

int bt_setup_run_all(BtSetup *s, int argc, char **argv)
{
    /* Step 0: guarantee root. May re-exec under sudo and never return. */
    bt_setup_require_root(s, argc, argv);

    printf("[setup] Preparing Bluetooth environment on %s...\n", s->adapter);

    int ok = 1;

    /* Compat mode first: it restarts bluetoothd, which can reset the
     * adapter, so power/pairable steps must follow it. */
    if (bt_setup_ensure_compat(s) != 0) ok = 0;

    bt_setup_unblock_rfkill(s);           /* non-fatal */

    if (bt_setup_power_adapter(s) != 0) ok = 0;

    bt_setup_make_pairable(s);            /* non-fatal */

    if (!ok) {
        fprintf(stderr,
            "[setup] One or more required steps failed (see above).\n");
        return -1;
    }

    printf("[setup] Environment ready.\n\n");
    return 0;
}
