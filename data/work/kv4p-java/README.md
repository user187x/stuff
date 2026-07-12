# kv4p HT — Desktop (Java) Host

A Java desktop host for the [kv4p HT](https://github.com/VanceVagell/kv4p-ht) open-source VHF/UHF radio, replacing the Android app with a cross-platform Java baseline plus a Swing GUI. Targets JDK 26 (sources build on any JDK ≥ 21).

## Firmware changes required: none

The ESP32 firmware is already host-agnostic. It speaks the documented KV4P KISS protocol (v2.2) over plain USB-CDC serial at 115200 baud — nothing in `protocol.h`, the `.ino`, or the audio pipeline knows or cares that the peer is Android. The "Android" references in the source comments are labels, not dependencies. This port therefore leaves the C/C++ firmware untouched and reimplements the *host* side (the Android app's `RadioAudioService` + protocol stack) in Java:

| Firmware side (unchanged C++)        | Desktop side (new Java)                     |
|--------------------------------------|---------------------------------------------|
| `KissParser` / `KissBufferedWriter`  | `KissDecoder` / `KissEncoder`               |
| packed structs in `protocol.h`       | `Structs` records (little-endian, same sizes: 22/26/17/43 bytes) |
| `ADPCMEncoder/Decoder` (IMA WAV, 128 B block) | `ImaAdpcm` (128 B → 249 samples/block) |
| USB `Serial` @ 115200                | `SerialLink` (jSerialComm; Win/macOS/Linux) |
| Android `AudioTrack` / `AudioRecord` | `RxAudioPlayer` / `TxAudioCapture` (`javax.sound.sampled`, 16 kHz mono) |
| Android desired-state/ACK logic      | `Kv4pClient` (sequence, retries, window flow control) |

One practical note instead of a code change: the firmware sends `COMMAND_HELLO` once at boot, so the desktop host pulses DTR/RTS on connect (`SerialLink.resetDevice()`) to reboot the ESP32 and receive a fresh HELLO — the same effect the Android USB attach cycle has.

## Layout

```
src/main/java/com/kv4p/desktop/
  protocol/Kv4pProtocol.java   Wire constants (1:1 with protocol.h)
  protocol/KissEncoder.java    Outgoing KISS DATA / KV4P vendor frames
  protocol/KissDecoder.java    Streaming parser (port of firmware KissParser)
  protocol/Structs.java        HostDesiredState / DeviceState / Version / Hello / WindowUpdate
  audio/ImaAdpcm.java          IMA WAV ADPCM codec (128-byte / 249-sample mono blocks)
  audio/RxAudioPlayer.java     16 kHz playback of COMMAND_RX_AUDIO
  audio/TxAudioCapture.java    Mic capture → ADPCM → COMMAND_HOST_TX_AUDIO
  serial/SerialLink.java       USB-CDC transport + ESP32 auto-reset
  Kv4pClient.java              Protocol v2.2 session logic (HELLO, ACK/retry, flow control)
  ui/Kv4pApp.java              Swing GUI (main class)
  SelfTest.java                Headless wire-format validation vs. emulated firmware
```

## Build & run

With Maven (produces a runnable shaded jar):

```
mvn package
java -jar target/kv4p-desktop-0.1.0.jar
```

Without Maven:

```
javac -cp lib/jSerialComm-2.11.0.jar -d target/classes $(find src -name '*.java')
java -cp target/classes:lib/jSerialComm-2.11.0.jar com.kv4p.desktop.ui.Kv4pApp
```

Headless self-test (no hardware needed):

```
java -cp target/classes:lib/jSerialComm-2.11.0.jar com.kv4p.desktop.SelfTest
```

## Using the GUI

1. Plug in the kv4p HT, pick its serial port, click **Connect**. The app resets the ESP32 and waits for `COMMAND_HELLO`; on receipt it seeds the UI from the firmware's NVS-restored `DeviceState` and enables status reports.
2. Set RX/TX frequency, bandwidth, squelch and CTCSS indices, click **Tune** — this sends a `COMMAND_HOST_DESIRED_STATE` snapshot; the firmware ACKs via `DeviceState.appliedSequence` (unACKed snapshots are retried up to 3× with the same sequence, per protocol).
3. RX audio (16 kHz ADPCM) plays through the default output device; squelch state, mode, and RSSI update live from the 500 ms `COMMAND_DEVICE_STATE` heartbeat.
4. Tick **TX allowed** (the firmware's persisted safety flag — transmit is refused without it), then press-and-hold **PUSH TO TALK** to key up and stream the microphone.

## Amateur-radio notice

Transmitting requires an amateur radio license appropriate to the frequency in use. The **TX allowed** flag exists in the firmware precisely so hosts default to receive-only; keep it off unless you are licensed and on an authorized frequency.

## Roadmap to 1:1 Android parity

The baseline covers connect/tune/RX/TX/PTT/status. Remaining Android features to mirror next: memory channels & groups (host-owned, `memoryId` already plumbed), scanning (advance on `DEVICE_STATE_SQUELCHED`), APRS chat UI (AX.25 send/receive hooks already exposed via `Kv4pClient.sendAx25` / `onAx25Received`; the AFSK modem runs in firmware — `FEATURE_HAS_ESP32_AFSK`), repeater offsets/tone tables, settings persistence, and firmware flashing.

Licensed GPL-3.0, matching the upstream project.
