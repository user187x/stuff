package xxx;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses KISS protocol frames from serial data.
 * Handles both standard KISS DATA frames and KV4P vendor frames.
 *
 * Frame format:
 *   FEND [command] [escaped payload] FEND
 *
 * Escaping rules:
 *   0xC0 (FEND) -> 0xDB 0xDC
 *   0xDB (FESC) -> 0xDB 0xDD
 */
public class KissParser {
    private static final byte KISS_FEND = (byte) 0xC0;
    private static final byte KISS_FESC = (byte) 0xDB;
    private static final byte KISS_TFEND = (byte) 0xDC;
    private static final byte KISS_TFESC = (byte) 0xDD;
    private static final byte KISS_CMD_DATA = 0x00;
    private static final byte KISS_CMD_SETHARDWARE = 0x06;

    private static final String KV4P_VENDOR_PREFIX = "KV4P";
    private static final byte KV4P_PROTOCOL_VERSION = 0x01;

    // Command codes from protocol.h
    public static final byte COMMAND_RX_AUDIO = 0x07;
    public static final byte COMMAND_DEVICE_STATE = 0x0B;
    public static final byte COMMAND_HELLO = 0x06;
    public static final byte COMMAND_WINDOW_UPDATE = 0x09;

    private byte[] frameBuffer = new byte[4096];
    private int frameLen = 0;
    private boolean inFrame = false;
    private boolean escape = false;
    private List<FrameListener> listeners = new ArrayList<>();

    public interface FrameListener {
        void onKissDataFrame(byte[] data, int length);
        void onKv4pCommand(byte command, byte[] payload, int payloadLen);
    }

    public void addListener(FrameListener listener) {
        listeners.add(listener);
    }

    public void processByte(byte b) {
        if (b == KISS_FEND) {
            if (frameLen > 0 && inFrame) {
                processCompleteFrame();
            }
            frameLen = 0;
            inFrame = true;
            escape = false;
        } else if (!inFrame) {
            // Ignore data outside frames
        } else if (escape) {
            // Handle escaped byte
            if (b == KISS_TFEND) {
                appendByte(KISS_FEND);
            } else if (b == KISS_TFESC) {
                appendByte(KISS_FESC);
            } else {
                // Unknown escape sequence, drop frame
                frameLen = 0;
            }
            escape = false;
        } else if (b == KISS_FESC) {
            escape = true;
        } else {
            appendByte(b);
        }
    }

    public void processData(byte[] data, int length) {
        for (int i = 0; i < length; i++) {
            processByte(data[i]);
        }
    }

    private void appendByte(byte b) {
        if (frameLen < frameBuffer.length) {
            frameBuffer[frameLen++] = b;
        }
    }

    private void processCompleteFrame() {
        if (frameLen < 1) return;

        byte kissCommandByte = frameBuffer[0];
        byte kissCommand = (byte) (kissCommandByte & 0x0F);
        byte[] payload = new byte[frameLen - 1];
        System.arraycopy(frameBuffer, 1, payload, 0, frameLen - 1);

        if (kissCommand == KISS_CMD_DATA) {
            // Standard AX.25 KISS DATA frame
            for (FrameListener listener : listeners) {
                listener.onKissDataFrame(payload, payload.length);
            }
        } else if (kissCommand == KISS_CMD_SETHARDWARE) {
            // KV4P vendor frame
            processVendorFrame(payload);
        }
    }

    private void processVendorFrame(byte[] payload) {
        if (payload.length < 6) return; // Need "KV4P" + version + command

        // Check for "KV4P" prefix
        String prefix = new String(payload, 0, 4);
        if (!prefix.equals(KV4P_VENDOR_PREFIX)) return;

        byte version = payload[4];
        if (version != KV4P_PROTOCOL_VERSION) return;

        byte command = payload[5];
        byte[] commandPayload = new byte[payload.length - 6];
        System.arraycopy(payload, 6, commandPayload, 0, commandPayload.length);

        for (FrameListener listener : listeners) {
            listener.onKv4pCommand(command, commandPayload, commandPayload.length);
        }
    }
}