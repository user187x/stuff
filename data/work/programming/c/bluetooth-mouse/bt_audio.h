/*
 * bt_audio.h  -  Play audio from the connected device on this laptop.
 *
 * WHAT THIS IS (and isn't)
 * ------------------------
 * The HID mouse/keyboard side of btmouse hand-rolls its own L2CAP sockets
 * because HID is a tiny, well-defined report protocol. Bluetooth *audio* is
 * not like that: it rides A2DP / AVDTP with codec negotiation, media
 * transport and timing, and on Linux that whole stack already lives in
 * BlueZ + PipeWire. Re-implementing it over raw sockets would be thousands
 * of lines and would fight the system stack for the adapter.
 *
 * So this module does the correct, supported thing: it drives the existing
 * stack. With the device acting as an A2DP *sink* (like headphones) and the
 * connected device as the A2DP *source* (like a phone), it:
 *
 * 1. connects the A2DP profile over the already-paired link,
 * 2. finds the resulting PipeWire/PulseAudio Bluetooth input (source),
 * 3. loads module-loopback to route the incoming audio to the laptop's speakers,
 *
 * so whatever the connected device plays comes out on this laptop.
 *
 * IMPORTANT CAVEATS
 * - PipeWire/PulseAudio run in the *user* session, but this program runs
 * as root (via sudo). We therefore run the routing commands back as the
 * invoking user ($SUDO_USER) against their session bus. If there is no
 * user session (pure-root boot, no PipeWire), audio can't be routed and
 * these calls fail gracefully.
 * - The connected device must accept the A2DP source role from us while
 * it uses us as a HID device. Most head units and phones do.
 * - Requires: pipewire + pipewire-pulse (or pulseaudio), wireplumber, and
 * libspa-0.2-bluez5 (or pulseaudio-module-bluetooth). `make gui` / the
 * README note lists these.
 *
 * Every call is best-effort and non-fatal: audio problems never stop the
 * HID device from working.
 */
#ifndef BT_AUDIO_H
#define BT_AUDIO_H

/* True (1) if the tools needed to route audio appear to be present. */
int  bt_audio_available(void);

/* Begin redirecting the connected device's audio to this laptop.
 * host_mac is the connected device ("AA:BB:CC:DD:EE:FF").
 * Returns 0 on success, -1 if audio could not be set up (non-fatal). */
int  bt_audio_start(const char *host_mac);

/* Stop redirecting: unloads the loopback module. Does NOT drop the
 * Bluetooth link (that would also kill the HID channels). Safe to call even
 * if start was never called or failed. */
void bt_audio_stop(void);

#endif /* BT_AUDIO_H */
