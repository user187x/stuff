package com.device;

import static com.device.protocol.Protocol.*;

import com.device.audio.ImaAdpcm;
import com.device.protocol.KissDecoder;
import com.device.protocol.KissEncoder;
import com.device.protocol.Structs.DeviceState;
import com.device.protocol.Structs.Hello;
import com.device.protocol.Structs.HostDesiredState;
import com.device.protocol.Structs.Version;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Headless validation of the Java port against the firmware's wire format. Emulates the ESP32 side
 * using the exact packed-struct layouts from protocol.h.
 */
public final class FirmwareTest {

  private static int passed = 0;
  private static int failed = 0;

  public static void main(String[] args) {
    testStructSizes();
    testKissEscaping();
    testVendorFrameLayoutMatchesReadmeExample();
    testBootBannerNoiseTolerance();
    testHelloDecodeFromEmulatedFirmware();
    testDesiredStateEncodeDecodedByEmulatedFirmware();
    testAdpcmRoundTrip();
    testAdpcmBlockGeometry();

    System.out.printf("%n%d passed, %d failed%n", passed, failed);
    if (failed > 0) System.exit(1);
  }

  // ---- 1. Struct sizes match [[gnu::packed]] C structs ----
  static void testStructSizes() {
    check(
        "sizeof(HostDesiredState) == 22",
        HostDesiredState.SIZE == 22
            && new HostDesiredState(1, -1, 0, 1, 146.52f, 146.52f, 0, 2, 0).toBytes().length == 22);
    check("sizeof(DeviceState) == 26", DeviceState.SIZE == 26);
    check("sizeof(Version) == 17", Version.SIZE == 17);
    check("sizeof(Hello) == 43", Hello.SIZE == 43);
  }

  // ---- 2. KISS escaping round-trips FEND/FESC bytes ----
  static void testKissEscaping() {
    byte[] payload = {0x42, (byte) 0xC0, (byte) 0xDB, 0x00, (byte) 0xC0, 0x7F};
    byte[] frame = KissEncoder.vendorFrame(COMMAND_HOST_TX_AUDIO, payload);
    var received = new ArrayList<byte[]>();
    var decoder =
        new KissDecoder(
            new KissDecoder.Listener() {
              @Override
              public void onAx25(byte[] ax25) {}

              @Override
              public void onVendorCommand(int cmd, byte[] p) {
                if (cmd == COMMAND_HOST_TX_AUDIO) received.add(p);
              }
            });
    // Feed one byte at a time to exercise the streaming state machine.
    for (byte b : frame) decoder.feed(new byte[] {b});
    check(
        "KISS escape round-trip",
        received.size() == 1 && java.util.Arrays.equals(received.get(0), payload));
  }

  // ---- 3. Vendor frame layout matches the protocol readme example ----
  static void testVendorFrameLayoutMatchesReadmeExample() {
    // readme: [ 0xC0, 0x06, 'K','V','4','P', 0x01, 0x01, 'E','r','r','o','r', 0xC0 ]
    byte[] frame =
        KissEncoder.vendorFrame(COMMAND_DEBUG_INFO, "Error".getBytes(StandardCharsets.US_ASCII));
    byte[] expected = {
      (byte) 0xC0, 0x06, 'K', 'V', '4', 'P', 0x01, 0x01, 'E', 'r', 'r', 'o', 'r', (byte) 0xC0
    };
    check("vendor frame byte layout == readme example", java.util.Arrays.equals(frame, expected));
  }

  // ---- 4. ASCII boot banner before first FEND must be ignored ----
  static void testBootBannerNoiseTolerance() {
    var got = new int[] {0};
    var decoder =
        new KissDecoder(
            new KissDecoder.Listener() {
              @Override
              public void onAx25(byte[] ax25) {}

              @Override
              public void onVendorCommand(int cmd, byte[] p) {
                got[0] = cmd;
              }
            });
    decoder.feed(
        "===== kv4p serial output =====\r\nThis port will emit binary data...\r\n"
            .getBytes(StandardCharsets.US_ASCII));
    decoder.feed(KissEncoder.vendorFrame(COMMAND_HELLO, emulateHelloPayload()));
    check("boot banner ignored, HELLO decoded", got[0] == COMMAND_HELLO);
  }

  // ---- 5. HELLO from an emulated firmware decodes field-for-field ----
  static void testHelloDecodeFromEmulatedFirmware() {
    var holder = new Hello[1];
    var decoder =
        new KissDecoder(
            new KissDecoder.Listener() {
              @Override
              public void onAx25(byte[] ax25) {}

              @Override
              public void onVendorCommand(int cmd, byte[] p) {
                if (cmd == COMMAND_HELLO) holder[0] = Hello.fromBytes(p);
              }
            });
    decoder.feed(KissEncoder.vendorFrame(COMMAND_HELLO, emulateHelloPayload()));
    Hello h = holder[0];
    check("HELLO decoded", h != null);
    if (h == null) return;
    check("HELLO.ver == 17", h.version().ver() == 17);
    check("HELLO.radioModuleStatus == 'f'", h.version().radioModuleStatus() == 'f');
    check("HELLO.windowSize == 2048", h.version().windowSize() == 2048);
    check("HELLO.minRadioFreq == 134.0", h.version().minRadioFreq() == 134.0f);
    check("HELLO.maxRadioFreq == 174.0", h.version().maxRadioFreq() == 174.0f);
    check("HELLO.features has ESP32 AFSK", (h.version().features() & FEATURE_HAS_ESP32_AFSK) != 0);
    check(
        "HELLO.deviceState.freq_rx == 146.52", Math.abs(h.deviceState().freqRx() - 146.52f) < 1e-4);
    check("HELLO.deviceState.mode == STOPPED", h.deviceState().mode() == DEVICE_MODE_STOPPED);
    check("HELLO.deviceState.appliedSequence == 0", h.deviceState().appliedSequence() == 0);
  }

  // ---- 6. HostDesiredState encoded by Java parses correctly on the "firmware" side ----
  static void testDesiredStateEncodeDecodedByEmulatedFirmware() {
    var desired =
        new HostDesiredState(
            7,
            -1,
            HOST_STATE_RADIO_CONFIG_VALID
                | HOST_STATE_ENABLE_STATUS_REPORTS
                | HOST_STATE_RX_AUDIO_OPEN
                | HOST_STATE_HIGH_POWER
                | HOST_STATE_TX_ALLOWED,
            BW_25K,
            146.52f,
            147.345f,
            13,
            3,
            0);
    byte[] frame = KissEncoder.vendorFrame(COMMAND_HOST_DESIRED_STATE, desired.toBytes());

    // Emulate firmware KissParser + memcpy into packed struct.
    byte[] structBytes = firmwareSideExtractVendorPayload(frame);
    check(
        "firmware sees 22-byte HostDesiredState", structBytes != null && structBytes.length == 22);
    if (structBytes == null) return;
    ByteBuffer b = ByteBuffer.wrap(structBytes).order(ByteOrder.LITTLE_ENDIAN);
    check("sequence == 7", (b.getInt() & 0xFFFFFFFFL) == 7);
    check("memoryId == -1", b.getInt() == -1);
    int flags = b.getShort() & 0xFFFF;
    check(
        "flags carry TX_ALLOWED | STATUS_REPORTS",
        (flags & HOST_STATE_TX_ALLOWED) != 0 && (flags & HOST_STATE_ENABLE_STATUS_REPORTS) != 0);
    check("bw == 25k", (b.get() & 0xFF) == BW_25K);
    check("freq_tx == 146.52", Math.abs(b.getFloat() - 146.52f) < 1e-4);
    check("freq_rx == 147.345", Math.abs(b.getFloat() - 147.345f) < 1e-4);
    check("ctcss_tx == 13", (b.get() & 0xFF) == 13);
    check("squelch == 3", (b.get() & 0xFF) == 3);
    check("ctcss_rx == 0", (b.get() & 0xFF) == 0);
  }

  // ---- 7. ADPCM codec: encode -> decode reproduces a 1 kHz tone accurately ----
  static void testAdpcmRoundTrip() {
    int blocks = 20;
    short[] tone = new short[ImaAdpcm.BLOCK_SAMPLES * blocks];
    for (int i = 0; i < tone.length; i++) {
      tone[i] = (short) (12000 * Math.sin(2 * Math.PI * 1000 * i / AUDIO_WIRE_SAMPLE_RATE));
    }
    var enc = new ImaAdpcm.Encoder();
    var out = new ByteArrayOutputStream();
    for (int blk = 0; blk < blocks; blk++) {
      out.writeBytes(enc.encodeBlock(tone, blk * ImaAdpcm.BLOCK_SAMPLES));
    }
    byte[] adpcm = out.toByteArray();
    check("ADPCM compresses 4:1 (+headers)", adpcm.length == blocks * ImaAdpcm.BLOCK_BYTES);

    short[] decoded = ImaAdpcm.decodePayload(adpcm);
    check("decoded sample count", decoded.length == tone.length);

    // Skip the first block (encoder adaptation) and compute SNR.
    double sig = 0, noise = 0;
    for (int i = ImaAdpcm.BLOCK_SAMPLES; i < tone.length; i++) {
      sig += (double) tone[i] * tone[i];
      double e = tone[i] - decoded[i];
      noise += e * e;
    }
    double snrDb = 10 * Math.log10(sig / Math.max(noise, 1));
    check(String.format("ADPCM round-trip SNR > 20 dB (got %.1f dB)", snrDb), snrDb > 20);
  }

  // ---- 8. Block geometry matches firmware constants ----
  static void testAdpcmBlockGeometry() {
    check("BLOCK_BYTES == AUDIO_FRAME_BYTES (128)", ImaAdpcm.BLOCK_BYTES == AUDIO_FRAME_BYTES);
    check(
        "BLOCK_SAMPLES == AUDIO_FRAME_SAMPLES (249)",
        ImaAdpcm.BLOCK_SAMPLES == AUDIO_FRAME_SAMPLES);
  }

  // ---------------- Firmware-side emulation helpers ----------------

  /** Build a HELLO payload exactly as sendHello() memcpy's the packed Hello struct. */
  private static byte[] emulateHelloPayload() {
    ByteBuffer b = ByteBuffer.allocate(Hello.SIZE).order(ByteOrder.LITTLE_ENDIAN);
    // Version
    b.putShort((short) 17); // FIRMWARE_VER
    b.put((byte) 'f'); // RADIO_MODULE_FOUND
    b.putInt(2048); // USB_BUFFER_SIZE window
    b.put((byte) 0); // RF_SA818_VHF
    b.putFloat(134.0f); // min freq
    b.putFloat(174.0f); // max freq
    b.put((byte) (FEATURE_HAS_PHY_PTT | FEATURE_HAS_ESP32_AFSK));
    // DeviceState
    b.putInt(0); // appliedSequence
    b.putInt(-1); // memoryId
    b.putShort(
        (short) (HOST_STATE_RADIO_CONFIG_VALID | HOST_STATE_HIGH_POWER | HOST_STATE_RSSI_ENABLED));
    b.put((byte) BW_25K);
    b.putFloat(146.52f); // freq_tx
    b.putFloat(146.52f); // freq_rx
    b.put((byte) 0); // ctcss_tx
    b.put((byte) 2); // squelch
    b.put((byte) 0); // ctcss_rx
    b.put((byte) 'f'); // radioModuleStatus
    b.put((byte) DEVICE_MODE_STOPPED);
    b.put((byte) DEVICE_STATE_ERROR_NONE);
    b.put((byte) 0); // latestRssi
    return b.array();
  }

  /** Re-implements the firmware KissParser unescape + vendor-header check for one frame. */
  private static byte[] firmwareSideExtractVendorPayload(byte[] wire) {
    List<Byte> frame = new ArrayList<>();
    boolean escape = false, inFrame = false;
    for (byte raw : wire) {
      int x = raw & 0xFF;
      if (x == KISS_FEND) {
        if (!frame.isEmpty()) break;
        inFrame = true;
      } else if (inFrame) {
        if (escape) {
          frame.add((byte) (x == KISS_TFEND ? KISS_FEND : KISS_FESC));
          escape = false;
        } else if (x == KISS_FESC) {
          escape = true;
        } else {
          frame.add(raw);
        }
      }
    }
    if (frame.size() < 1 + KV4P_VENDOR_HEADER_LEN) return null;
    if ((frame.get(0) & 0x0F) != KISS_CMD_SETHARDWARE) return null;
    if (frame.get(1) != 'K' || frame.get(2) != 'V' || frame.get(3) != '4' || frame.get(4) != 'P')
      return null;
    if ((frame.get(5) & 0xFF) != KV4P_PROTOCOL_VERSION) return null;
    byte[] out = new byte[frame.size() - 1 - KV4P_VENDOR_HEADER_LEN];
    for (int i = 0; i < out.length; i++) out[i] = frame.get(1 + KV4P_VENDOR_HEADER_LEN + i);
    return out;
  }

  private static void check(String name, boolean ok) {
    System.out.printf("  [%s] %s%n", ok ? "PASS" : "FAIL", name);
    if (ok) passed++;
    else failed++;
  }

  private FirmwareTest() {}
}
