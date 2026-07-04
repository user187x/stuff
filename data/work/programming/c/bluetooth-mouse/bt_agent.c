/*
 * bt_agent.c  -  Implementation of the zero-interaction BlueZ pairing agent.
 * See bt_agent.h. Target OS: Ubuntu 26 (systemd + BlueZ), uses sd-bus.
 */
#define _GNU_SOURCE
#include "bt_agent.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <errno.h>
#include <pthread.h>

#include <systemd/sd-bus.h>

#define AGENT_PATH        "/org/btmouse/agent"
#define AGENT_CAPABILITY  "NoInputNoOutput"

#define BLUEZ_SERVICE     "org.bluez"
#define BLUEZ_MANAGER_PATH "/org/bluez"
#define AGENT_MANAGER_IFACE "org.bluez.AgentManager1"
#define AGENT_IFACE       "org.bluez.Agent1"
#define PROPS_IFACE       "org.freedesktop.DBus.Properties"
#define DEVICE_IFACE      "org.bluez.Device1"

/* ------------------------------------------------------------------ *
 *  State
 * ------------------------------------------------------------------ */
static sd_bus       *g_bus      = NULL;
static sd_bus_slot  *g_slot     = NULL;
static pthread_t     g_thread;
static volatile int  g_running  = 0;
static int           g_started  = 0;   /* thread actually created */

/* ------------------------------------------------------------------ *
 *  Mark a device Trusted (async, non-blocking) so future reconnects
 *  need no authorization. Called from within method handlers, so we use
 *  the async form to avoid reentrant blocking on the bus.
 * ------------------------------------------------------------------ */
static void trust_device(sd_bus *bus, const char *path)
{
    if (!bus || !path) return;
    int r = sd_bus_call_method_async(
                bus, NULL,
                BLUEZ_SERVICE, path, PROPS_IFACE, "Set",
                NULL, NULL,
                "ssv", DEVICE_IFACE, "Trusted", "b", 1);
    if (r < 0)
        fprintf(stderr, "[agent] warn: could not queue Trusted for %s: %s\n",
                path, strerror(-r));
    else
        fprintf(stderr, "[agent] device %s marked trusted (auto-reconnect enabled)\n",
               path);
}

/* ------------------------------------------------------------------ *
 *  Agent1 method handlers. All auto-accept.
 * ------------------------------------------------------------------ */

static int m_release(sd_bus_message *m, void *ud, sd_bus_error *e)
{
    (void)ud; (void)e;
    fprintf(stderr, "[agent] Release\n");
    return sd_bus_reply_method_return(m, "");
}

/* Legacy pairing: supply the conventional headless default PIN. */
static int m_request_pincode(sd_bus_message *m, void *ud, sd_bus_error *e)
{
    (void)ud; (void)e;
    const char *path = NULL;
    sd_bus_message_read(m, "o", &path);
    trust_device(sd_bus_message_get_bus(m), path);
    fprintf(stderr, "[agent] RequestPinCode(%s) -> \"0000\"\n", path ? path : "?");
    return sd_bus_reply_method_return(m, "s", "0000");
}

static int m_display_pincode(sd_bus_message *m, void *ud, sd_bus_error *e)
{
    (void)ud; (void)e;
    return sd_bus_reply_method_return(m, "");
}

/* Legacy pairing: supply a fixed passkey (0). */
static int m_request_passkey(sd_bus_message *m, void *ud, sd_bus_error *e)
{
    (void)ud; (void)e;
    const char *path = NULL;
    sd_bus_message_read(m, "o", &path);
    trust_device(sd_bus_message_get_bus(m), path);
    fprintf(stderr, "[agent] RequestPasskey(%s) -> 0\n", path ? path : "?");
    uint32_t passkey = 0;
    return sd_bus_reply_method_return(m, "u", passkey);
}

static int m_display_passkey(sd_bus_message *m, void *ud, sd_bus_error *e)
{
    (void)ud; (void)e;
    return sd_bus_reply_method_return(m, "");
}

/* SSP numeric comparison: accept without user confirmation (Just Works). */
static int m_request_confirmation(sd_bus_message *m, void *ud, sd_bus_error *e)
{
    (void)ud; (void)e;
    const char *path = NULL;
    uint32_t passkey = 0;
    sd_bus_message_read(m, "ou", &path, &passkey);
    trust_device(sd_bus_message_get_bus(m), path);
    fprintf(stderr, "[agent] RequestConfirmation(%s, %06u) -> accept\n",
           path ? path : "?", passkey);
    return sd_bus_reply_method_return(m, "");
}

/* Incoming device authorization: accept + trust. */
static int m_request_authorization(sd_bus_message *m, void *ud, sd_bus_error *e)
{
    (void)ud; (void)e;
    const char *path = NULL;
    sd_bus_message_read(m, "o", &path);
    trust_device(sd_bus_message_get_bus(m), path);
    fprintf(stderr, "[agent] RequestAuthorization(%s) -> accept\n", path ? path : "?");
    return sd_bus_reply_method_return(m, "");
}

/* Service/profile connection (our HID channels): accept. */
static int m_authorize_service(sd_bus_message *m, void *ud, sd_bus_error *e)
{
    (void)ud; (void)e;
    const char *path = NULL, *uuid = NULL;
    sd_bus_message_read(m, "os", &path, &uuid);
    fprintf(stderr, "[agent] AuthorizeService(%s, %s) -> accept\n",
           path ? path : "?", uuid ? uuid : "?");
    return sd_bus_reply_method_return(m, "");
}

static int m_cancel(sd_bus_message *m, void *ud, sd_bus_error *e)
{
    (void)ud; (void)e;
    fprintf(stderr, "[agent] Cancel\n");
    return sd_bus_reply_method_return(m, "");
}

static const sd_bus_vtable agent_vtable[] = {
    SD_BUS_VTABLE_START(0),
    SD_BUS_METHOD("Release",              "",   "",  m_release,              SD_BUS_VTABLE_UNPRIVILEGED),
    SD_BUS_METHOD("RequestPinCode",       "o",  "s", m_request_pincode,      SD_BUS_VTABLE_UNPRIVILEGED),
    SD_BUS_METHOD("DisplayPinCode",       "os", "",  m_display_pincode,      SD_BUS_VTABLE_UNPRIVILEGED),
    SD_BUS_METHOD("RequestPasskey",       "o",  "u", m_request_passkey,      SD_BUS_VTABLE_UNPRIVILEGED),
    SD_BUS_METHOD("DisplayPasskey",       "ouq","",  m_display_passkey,      SD_BUS_VTABLE_UNPRIVILEGED),
    SD_BUS_METHOD("RequestConfirmation",  "ou", "",  m_request_confirmation, SD_BUS_VTABLE_UNPRIVILEGED),
    SD_BUS_METHOD("RequestAuthorization", "o",  "",  m_request_authorization,SD_BUS_VTABLE_UNPRIVILEGED),
    SD_BUS_METHOD("AuthorizeService",     "os", "",  m_authorize_service,    SD_BUS_VTABLE_UNPRIVILEGED),
    SD_BUS_METHOD("Cancel",               "",   "",  m_cancel,               SD_BUS_VTABLE_UNPRIVILEGED),
    SD_BUS_VTABLE_END
};

/* ------------------------------------------------------------------ *
 *  Background thread: service the bus so handlers fire.
 * ------------------------------------------------------------------ */
static void *agent_thread(void *arg)
{
    (void)arg;
    while (g_running) {
        int r = sd_bus_process(g_bus, NULL);
        if (r < 0) {
            fprintf(stderr, "[agent] bus process error: %s\n", strerror(-r));
            break;
        }
        if (r > 0) continue;                 /* more to process */
        r = sd_bus_wait(g_bus, 1000000);     /* 1s, so stop is responsive */
        if (r < 0 && r != -EINTR) {
            fprintf(stderr, "[agent] bus wait error: %s\n", strerror(-r));
            break;
        }
    }
    return NULL;
}

/* ------------------------------------------------------------------ *
 *  RegisterAgent with retry (bluetoothd may have just restarted).
 *  Treats "AlreadyExists" as success.
 * ------------------------------------------------------------------ */
static int register_agent(void)
{
    for (int attempt = 0; attempt < 20; attempt++) {
        sd_bus_error err = SD_BUS_ERROR_NULL;
        int r = sd_bus_call_method(
                    g_bus, BLUEZ_SERVICE, BLUEZ_MANAGER_PATH,
                    AGENT_MANAGER_IFACE, "RegisterAgent",
                    &err, NULL,
                    "os", AGENT_PATH, AGENT_CAPABILITY);

        if (r >= 0) {
            sd_bus_error_free(&err);
            return 0;
        }
        if (sd_bus_error_has_name(&err, "org.bluez.Error.AlreadyExists")) {
            sd_bus_error_free(&err);
            return 0;   /* fine: we (or a prior run) already registered */
        }
        /* bluetoothd not on the bus yet, or transient: back off and retry */
        if (attempt == 0)
            fprintf(stderr, "[agent] waiting for bluetoothd to be ready...\n");
        sd_bus_error_free(&err);
        usleep(500 * 1000);   /* 0.5s */
    }
    fprintf(stderr, "[agent] RegisterAgent failed after retries.\n");
    return -1;
}

/* ------------------------------------------------------------------ *
 *  Public API
 * ------------------------------------------------------------------ */
int bt_agent_start(void)
{
    int r = sd_bus_open_system(&g_bus);
    if (r < 0) {
        fprintf(stderr, "[agent] cannot connect to system bus: %s\n",
                strerror(-r));
        fprintf(stderr, "[agent] pairing will NOT be automated.\n");
        g_bus = NULL;
        return -1;
    }

    r = sd_bus_add_object_vtable(g_bus, &g_slot, AGENT_PATH,
                                 AGENT_IFACE, agent_vtable, NULL);
    if (r < 0) {
        fprintf(stderr, "[agent] failed to install agent object: %s\n",
                strerror(-r));
        goto fail;
    }

    if (register_agent() < 0)
        goto fail;

    /* Become the default agent so we handle system-wide pairing. Non-fatal
     * if it fails (we still handle authorizations as a registered agent). */
    {
        sd_bus_error err = SD_BUS_ERROR_NULL;
        r = sd_bus_call_method(g_bus, BLUEZ_SERVICE, BLUEZ_MANAGER_PATH,
                               AGENT_MANAGER_IFACE, "RequestDefaultAgent",
                               &err, NULL, "o", AGENT_PATH);
        if (r < 0)
            fprintf(stderr, "[agent] warn: RequestDefaultAgent: %s\n",
                    err.message ? err.message : strerror(-r));
        sd_bus_error_free(&err);
    }

    g_running = 1;
    r = pthread_create(&g_thread, NULL, agent_thread, NULL);
    if (r != 0) {
        fprintf(stderr, "[agent] pthread_create failed: %s\n", strerror(r));
        g_running = 0;
        goto fail;
    }
    g_started = 1;

    fprintf(stderr, "[agent] Auto-pairing agent active (%s). "
           "Devices will pair and reconnect with no prompts.\n",
           AGENT_CAPABILITY);
    return 0;

fail:
    if (g_slot) { sd_bus_slot_unref(g_slot); g_slot = NULL; }
    if (g_bus)  { sd_bus_unref(g_bus);  g_bus  = NULL; }
    return -1;
}

void bt_agent_stop(void)
{
    if (g_started) {
        g_running = 0;
        pthread_join(g_thread, NULL);
        g_started = 0;
    }

    if (g_bus) {
        /* Best-effort unregister so bluetoothd forgets us cleanly. */
        sd_bus_error err = SD_BUS_ERROR_NULL;
        sd_bus_call_method(g_bus, BLUEZ_SERVICE, BLUEZ_MANAGER_PATH,
                           AGENT_MANAGER_IFACE, "UnregisterAgent",
                           &err, NULL, "o", AGENT_PATH);
        sd_bus_error_free(&err);
    }

    if (g_slot) { sd_bus_slot_unref(g_slot); g_slot = NULL; }
    if (g_bus)  { sd_bus_unref(g_bus);  g_bus  = NULL; }
}
