/*
 * bt_audio.c  -  Implementation of the audio-to-host redirector.
 * See bt_audio.h. Target OS: Ubuntu 26 (BlueZ + PipeWire). Style mirrors
 * bt_setup.c: small helpers that shell out to the system tools.
 */
#define _GNU_SOURCE
#include "bt_audio.h"

#include <stdio.h>
#include <stdlib.h>
#include <stdarg.h>
#include <string.h>
#include <unistd.h>
#include <ctype.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <pwd.h>

/* Saved so bt_audio_stop() can put the user's default sink back. */
static char g_prev_sink[256] = "";
static char g_bt_sink[256]   = "";

/* ------------------------------------------------------------------ *
 *  Reaching the user's audio session from a root process.
 *
 *  PipeWire/PulseAudio live in the invoking user's session, not root's.
 *  We resolve that user (via $SUDO_USER) and run audio tools as them with
 *  XDG_RUNTIME_DIR pointed at their runtime dir, so pactl talks to the
 *  right session bus.
 * ------------------------------------------------------------------ */
static const char *audio_user(void)
{
    const char *u = getenv("SUDO_USER");
    if (!u || !*u) u = getenv("USER");
    if (u && *u && strcmp(u, "root") != 0) return u;
    return NULL;                     /* no non-root session available */
}

static int have_cmd(const char *name)
{
    char buf[256];
    snprintf(buf, sizeof(buf), "command -v %s >/dev/null 2>&1", name);
    return system(buf) == 0;
}

/* Build the "run as the session user" prefix into buf. Returns 0 on success. */
static int user_prefix(char *buf, size_t buflen)
{
    const char *user = audio_user();
    if (!user) return -1;

    struct passwd *pw = getpwnam(user);
    if (!pw) return -1;

    snprintf(buf, buflen,
             "sudo -u %s XDG_RUNTIME_DIR=/run/user/%u DBUS_SESSION_BUS_ADDRESS="
             "unix:path=/run/user/%u/bus ",
             user, (unsigned)pw->pw_uid, (unsigned)pw->pw_uid);
    return 0;
}

/* Run `pactl <args>` as the session user; return exit status or -1. */
static int user_pactl(const char *fmt, ...)
{
    char pre[256];
    if (user_prefix(pre, sizeof(pre)) != 0) return -1;

    char args[512];
    va_list ap;
    va_start(ap, fmt);
    vsnprintf(args, sizeof(args), fmt, ap);
    va_end(ap);

    char cmd[900];
    snprintf(cmd, sizeof(cmd), "%s pactl %s", pre, args);
    int rc = system(cmd);
    return (rc == -1) ? -1 : WEXITSTATUS(rc);
}

/* Capture the first line of `pactl <args>` output into out. 0 on success. */
static int user_pactl_capture(char *out, size_t outlen, const char *fmt, ...)
{
    char pre[256];
    if (user_prefix(pre, sizeof(pre)) != 0) return -1;

    char args[512];
    va_list ap;
    va_start(ap, fmt);
    vsnprintf(args, sizeof(args), fmt, ap);
    va_end(ap);

    char cmd[900];
    snprintf(cmd, sizeof(cmd), "%s pactl %s 2>/dev/null", pre, args);

    FILE *p = popen(cmd, "r");
    if (!p) return -1;
    out[0] = '\0';
    if (fgets(out, (int)outlen, p)) out[strcspn(out, "\n")] = '\0';
    pclose(p);
    return out[0] ? 0 : -1;
}

/* Run a system-bus bluetoothctl command (runs fine as root). */
static void btctl(const char *fmt, ...)
{
    char args[256];
    va_list ap;
    va_start(ap, fmt);
    vsnprintf(args, sizeof(args), fmt, ap);
    va_end(ap);

    char cmd[400];
    snprintf(cmd, sizeof(cmd), "bluetoothctl -- %s >/dev/null 2>&1", args);
    int rc = system(cmd);
    (void)rc;
}

/* Turn "AA:BB:CC:DD:EE:FF" into "AA_BB_CC_DD_EE_FF" for sink-name matching. */
static void mac_underscored(const char *mac, char *out, size_t n)
{
    size_t j = 0;
    for (size_t i = 0; mac[i] && j + 1 < n; i++)
        out[j++] = (mac[i] == ':') ? '_' : (char)toupper((unsigned char)mac[i]);
    out[j] = '\0';
}

/* ------------------------------------------------------------------ *
 *  Public API
 * ------------------------------------------------------------------ */
int bt_audio_available(void)
{
    return have_cmd("bluetoothctl") && have_cmd("pactl") && audio_user() != NULL;
}

int bt_audio_start(const char *host_mac)
{
    if (!host_mac || !*host_mac) return -1;

    if (!audio_user()) {
        fprintf(stderr, "[audio] no user session found ($SUDO_USER unset); "
                        "cannot route audio.\n");
        return -1;
    }
    if (!have_cmd("pactl")) {
        fprintf(stderr, "[audio] 'pactl' not found. Install pipewire-pulse "
                        "(or pulseaudio) plus the bluez5 audio module.\n");
        return -1;
    }

    printf("[audio] Connecting A2DP audio to %s...\n", host_mac);
    /* Ask BlueZ to bring up the audio profile on the already-paired link.
     * (The HID channels are unaffected; this just adds A2DP.) */
    btctl("connect %s", host_mac);

    char macu[32];
    mac_underscored(host_mac, macu, sizeof(macu));

    /* Poll for the Bluetooth output sink to appear (PipeWire names it
     * bluez_output.<MAC>.*; PulseAudio uses bluez_sink.<MAC>.*). */
    char find[256];
    snprintf(find, sizeof(find),
             "list short sinks | awk '/bluez_(output|sink)\\.%s/{print $2; exit}'",
             macu);

    g_bt_sink[0] = '\0';
    for (int i = 0; i < 40; i++) {                 /* up to ~8s */
        if (user_pactl_capture(g_bt_sink, sizeof(g_bt_sink), "%s", find) == 0
            && g_bt_sink[0])
            break;
        usleep(200 * 1000);
    }

    if (!g_bt_sink[0]) {
        fprintf(stderr,
            "[audio] No Bluetooth audio sink appeared for %s.\n"
            "[audio]   - Does the host accept the A2DP sink role?\n"
            "[audio]   - Are pipewire + wireplumber + libspa-0.2-bluez5 "
            "running in your session?\n", host_mac);
        return -1;
    }

    /* Remember the current default so we can restore it later. */
    user_pactl_capture(g_prev_sink, sizeof(g_prev_sink), "get-default-sink");

    /* Route audio to the host: make it default and move active streams. */
    user_pactl("set-default-sink %s", g_bt_sink);

    char pre[256];
    if (user_prefix(pre, sizeof(pre)) == 0) {
        char cmd[700];
        snprintf(cmd, sizeof(cmd),
            "%s pactl list short sink-inputs 2>/dev/null | while read id rest; "
            "do %s pactl move-sink-input \"$id\" %s >/dev/null 2>&1; done",
            pre, pre, g_bt_sink);
        int rc = system(cmd);
        (void)rc;
    }

    printf("[audio] Audio now routed to host via sink '%s'.\n", g_bt_sink);
    return 0;
}

void bt_audio_stop(void)
{
    if (!g_bt_sink[0]) return;        /* nothing was routed */

    if (g_prev_sink[0]) {
        user_pactl("set-default-sink %s", g_prev_sink);
        printf("[audio] Restored default sink '%s'.\n", g_prev_sink);
    }
    /* Deliberately do NOT disconnect the BT link: that would also drop the
     * HID mouse/keyboard channels. The audio profile idles until the host
     * disconnects. */
    g_bt_sink[0]   = '\0';
    g_prev_sink[0] = '\0';
}
