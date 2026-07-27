/*
 * bt_audio.c  -  Implementation of the audio-from-device redirector.
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
#include <pthread.h>

/* Saved so bt_audio_stop() can unload the loopback module later. */
static char g_loopback_id[32] = "";
static char g_bt_source[256]  = "";

static pthread_t g_audio_thread;
static volatile int g_audio_thread_running = 0;

struct audio_ctx {
    char host_mac[32];
};

/* ------------------------------------------------------------------ *
 * Reaching the user's audio session from a root process.
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

/* Turn "AA:BB:CC:DD:EE:FF" into "AA_BB_CC_DD_EE_FF" for sink-name matching. */
static void mac_underscored(const char *mac, char *out, size_t n)
{
    size_t j = 0;
    for (size_t i = 0; mac[i] && j + 1 < n; i++)
        out[j++] = (mac[i] == ':') ? '_' : (char)toupper((unsigned char)mac[i]);
    out[j] = '\0';
}

/* Bring up the A2DP link to the peer so BlueZ establishes the audio
 * transport and PipeWire/PulseAudio creates the corresponding source node.
 * The device is already bonded and auto-trusted by bt_agent, so this needs
 * no user interaction. bluetoothctl talks to the system bus, so it runs fine
 * as root (no session prefix needed). Returns the command's exit status. */
static int connect_a2dp(const char *mac)
{
    char cmd[128];
    snprintf(cmd, sizeof(cmd),
             "bluetoothctl -- connect %s >/dev/null 2>&1", mac);
    int rc = system(cmd);
    return (rc == -1) ? -1 : WEXITSTATUS(rc);
}

/* ------------------------------------------------------------------ *
 * Audio Thread Worker
 * ------------------------------------------------------------------ */
static void *audio_thread_func(void *arg)
{
    struct audio_ctx *ctx = arg;

    printf("[audio] Waiting for Android to initialize A2DP audio stream...\n");

    char macu[32];
    mac_underscored(ctx->host_mac, macu, sizeof(macu));

    /* Step 1: connect the A2DP profile over the already-paired link. Without
     * this, BlueZ never brings up the audio transport, so no source node is
     * ever created and the poll below would just time out. Retry a few times
     * in case the link setup races with the just-completed HID connection. */
    for (int i = 0; i < 5 && g_audio_thread_running; i++) {
        if (connect_a2dp(ctx->host_mac) == 0) break;
        usleep(500 * 1000);
    }

    /* Poll for the Bluetooth input source to appear. (PipeWire names it
     * bluez_input.<MAC>.*; PulseAudio uses bluez_source.<MAC>.*) */
    char find[256];
    snprintf(find, sizeof(find),
             "list short sources | awk '/bluez_(input|source)\\.%s/{print $2; exit}'",
             macu);

    g_bt_source[0] = '\0';
    for (int i = 0; i < 40 && g_audio_thread_running; i++) { /* up to ~8s */
        if (user_pactl_capture(g_bt_source, sizeof(g_bt_source), "%s", find) == 0
            && g_bt_source[0])
            break;
        /* Re-issue the connect periodically; the first attempt can land
         * before the peer is ready to accept the A2DP stream. */
        if (i == 15 || i == 30) connect_a2dp(ctx->host_mac);
        usleep(200 * 1000);
    }

    if (!g_bt_source[0]) {
        if (g_audio_thread_running) {
            fprintf(stderr,
                "[audio] No Bluetooth audio source appeared for %s.\n"
                "[audio]   - Ensure media audio is enabled in the phone's Bluetooth settings.\n",
                ctx->host_mac);
        }
        free(ctx);
        g_audio_thread_running = 0;
        return NULL;
    }

    if (g_audio_thread_running) {
        /* Route incoming audio to the laptop's speakers via loopback. */
        if (user_pactl_capture(g_loopback_id, sizeof(g_loopback_id),
                               "load-module module-loopback source=\"%s\"", g_bt_source) != 0) {
            fprintf(stderr, "[audio] Failed to load module-loopback.\n");
        } else {
            printf("[audio] Audio from '%s' now playing on this laptop.\n", g_bt_source);
            
            /* Race condition guard: if stop was requested while pactl was blocking */
            if (!g_audio_thread_running) {
                user_pactl("unload-module %s", g_loopback_id);
                g_loopback_id[0] = '\0';
                g_bt_source[0] = '\0';
            }
        }
    }

    free(ctx);
    if (g_audio_thread_running) {
        g_audio_thread_running = 0;
    }
    return NULL;
}

/* ------------------------------------------------------------------ *
 * Public API
 * ------------------------------------------------------------------ */
int bt_audio_available(void)
{
    return have_cmd("bluetoothctl") && have_cmd("pactl") && audio_user() != NULL;
}

int bt_audio_start(const char *host_mac)
{
    if (!host_mac || !*host_mac) return -1;

    if (!audio_user()) {
        fprintf(stderr, "[audio] no user session found ($SUDO_USER unset); cannot route audio.\n");
        return -1;
    }
    if (!have_cmd("pactl")) {
        fprintf(stderr, "[audio] 'pactl' not found. Install pipewire-pulse (or pulseaudio) plus the bluez5 audio module.\n");
        return -1;
    }

    if (g_audio_thread_running) return 0; /* Already starting/running */

    /* Stop any existing loopback route first if called multiple times */
    if (g_loopback_id[0]) bt_audio_stop();

    struct audio_ctx *ctx = malloc(sizeof(*ctx));
    if (!ctx) return -1;
    strncpy(ctx->host_mac, host_mac, sizeof(ctx->host_mac) - 1);
    ctx->host_mac[sizeof(ctx->host_mac) - 1] = '\0';

    g_audio_thread_running = 1;
    
    pthread_attr_t attr;
    pthread_attr_init(&attr);
    pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_DETACHED);
    pthread_create(&g_audio_thread, &attr, audio_thread_func, ctx);
    pthread_attr_destroy(&attr);

    return 0;
}

void bt_audio_stop(void)
{
    g_audio_thread_running = 0; /* Signals the worker thread to stop polling/loading */

    if (!g_loopback_id[0]) return; /* nothing was routed */

    user_pactl("unload-module %s", g_loopback_id);
    printf("[audio] Stopped playing Bluetooth audio (unloaded loopback %s).\n", g_loopback_id);
    
    g_loopback_id[0] = '\0';
    g_bt_source[0]   = '\0';
}
