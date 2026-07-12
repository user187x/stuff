package com.kv4p.desktop.protocol;

import java.util.Arrays;

import static com.kv4p.desktop.protocol.Kv4pProtocol.*;

/**
 * Streaming KISS parser — a faithful Java port of the firmware's {@code KissParser}
 * state machine. Bytes may arrive in arbitrary chunks; the parser syncs on FEND,
 * so the firmware's ASCII boot banner before the first frame is ignored naturally.
 */
public final class KissDecoder {

    /** Callbacks for decoded frames. */
    public interface Listener {
        /** A standard KISS DATA frame carrying a raw AX.25 packet. */
        void onAx25(byte[] ax25);
        /** A KV4P vendor frame: FEND 0x06 "KV4P" 0x01 cmd payload FEND. */
        void onVendorCommand(int kv4pCommand, byte[] payload);
    }

    private final Listener listener;
    private final byte[] frame = new byte[KISS_MAX_FRAME_SIZE];
    private int frameLen = 0;
    private boolean escape = false;
    private boolean dropFrame = false;
    private boolean inFrame = false;

    public KissDecoder(Listener listener) {
        this.listener = listener;
    }

    public void feed(byte[] data, int off, int len) {
        for (int i = off; i < off + len; i++) {
            processByte(data[i] & 0xFF);
        }
    }

    public void feed(byte[] data) {
        feed(data, 0, data.length);
    }

    public synchronized void reset() {
        frameLen = 0;
        escape = false;
        dropFrame = false;
        inFrame = false;
    }

    private void processByte(int b) {
        if (b == KISS_FEND) {
            if (frameLen > 0 && !dropFrame) {
                processFrame();
            }
            frameLen = 0;
            escape = false;
            dropFrame = false;
            inFrame = true;
        } else if (!inFrame) {
            // Noise between frames (e.g. firmware boot banner) — ignore.
        } else if (dropFrame) {
            // Wait for next FEND.
        } else if (escape) {
            if (b == KISS_TFEND) {
                append(KISS_FEND);
            } else if (b == KISS_TFESC) {
                append(KISS_FESC);
            } else {
                dropFrame = true; // Unknown escape: drop frame, resync on FEND.
            }
            escape = false;
        } else if (b == KISS_FESC) {
            escape = true;
        } else {
            append(b);
        }
    }

    private void append(int b) {
        if (frameLen >= KISS_MAX_FRAME_SIZE) {
            dropFrame = true;
            return;
        }
        frame[frameLen++] = (byte) b;
    }

    private void processFrame() {
        int kissCommandByte = frame[0] & 0xFF;
        int kissPort = kissCommandByte >> 4;
        int kissCommand = kissCommandByte & 0x0F;
        if (kissPort != KISS_PORT_0) {
            return;
        }
        int payloadLen = frameLen - 1;
        if (kissCommand == KISS_CMD_DATA) {
            if (payloadLen > 0 && payloadLen <= PROTO_MTU) {
                listener.onAx25(Arrays.copyOfRange(frame, 1, 1 + payloadLen));
            }
        } else if (kissCommand == KISS_CMD_SETHARDWARE) {
            processVendorFrame(payloadLen);
        }
    }

    private void processVendorFrame(int payloadLen) {
        if (payloadLen < KV4P_VENDOR_HEADER_LEN) {
            return;
        }
        for (int i = 0; i < KV4P_VENDOR_PREFIX.length; i++) {
            if (frame[1 + i] != KV4P_VENDOR_PREFIX[i]) {
                return;
            }
        }
        if ((frame[5] & 0xFF) != KV4P_PROTOCOL_VERSION) {
            return;
        }
        int command = frame[6] & 0xFF;
        byte[] payload = Arrays.copyOfRange(frame, 1 + KV4P_VENDOR_HEADER_LEN, 1 + payloadLen);
        listener.onVendorCommand(command, payload);
    }
}
