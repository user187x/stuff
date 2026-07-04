/*
 * bt_agent.h  -  Zero-interaction Bluetooth pairing agent for btmouse.
 *
 * Registers an in-process BlueZ pairing agent (via D-Bus / sd-bus) with
 * "NoInputNoOutput" capability so that:
 *
 *   - incoming pairing from a host (the infotainment unit) completes with
 *     no prompts (Just Works),
 *   - each paired device is marked Trusted, so it reconnects automatically
 *     forever after without re-authorization,
 *   - service/profile connections (the HID channels) are auto-authorized.
 *
 * The agent runs on its own background thread, so it answers pairing
 * requests while the main program is blocked waiting for a connection.
 * No external tools (bt-agent, a live bluetoothctl session) are required.
 *
 * All failures are non-fatal: if the agent can't start, the program still
 * runs; pairing would then just require a manual agent.
 */
#ifndef BT_AGENT_H
#define BT_AGENT_H

/* Start the background pairing agent. Returns 0 on success, -1 on failure
 * (in which case pairing is not automated but the program still works). */
int bt_agent_start(void);

/* Stop and unregister the agent (best effort). Safe to call once, even if
 * bt_agent_start failed. */
void bt_agent_stop(void);

#endif /* BT_AGENT_H */
