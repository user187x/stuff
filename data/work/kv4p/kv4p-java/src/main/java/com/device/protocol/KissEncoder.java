package com.device.protocol;

import java.io.ByteArrayOutputStream;

import static com.device.protocol.Protocol.*;

/**
 * Builds outgoing KISS frames — the Java counterpart of the firmware's
 * {@code KissBufferedWriter}, {@code sendKissDataFrame} and {@code sendKv4pVendorFrame}.
 */
public final class KissEncoder {
    private KissEncoder() {}

    /** FEND 0x00 &lt;escaped AX.25 bytes&gt; FEND */
    public static byte[] dataFrame(byte[] ax25) {
        var out = begin(KISS_CMD_DATA);
        writeEscaped(out, ax25, 0, Math.min(ax25.length, PROTO_MTU));
        return end(out);
    }

    /** FEND 0x06 "KV4P" 0x01 &lt;kv4pCommand&gt; &lt;escaped payload&gt; FEND */
    public static byte[] vendorFrame(int kv4pCommand, byte[] payload) {
        var out = begin(KISS_CMD_SETHARDWARE);
        writeEscaped(out, KV4P_VENDOR_PREFIX, 0, KV4P_VENDOR_PREFIX.length);
        writeEscapedByte(out, KV4P_PROTOCOL_VERSION);
        writeEscapedByte(out, kv4pCommand);
        if (payload != null) {
            writeEscaped(out, payload, 0, Math.min(payload.length, PROTO_MTU));
        }
        return end(out);
    }

    private static ByteArrayOutputStream begin(int kissCommand) {
        var out = new ByteArrayOutputStream(64);
        out.write(KISS_FEND);
        out.write(kissCommand);
        return out;
    }

    private static byte[] end(ByteArrayOutputStream out) {
        out.write(KISS_FEND);
        return out.toByteArray();
    }

    private static void writeEscaped(ByteArrayOutputStream out, byte[] data, int off, int len) {
        for (int i = off; i < off + len; i++) {
            writeEscapedByte(out, data[i] & 0xFF);
        }
    }

    private static void writeEscapedByte(ByteArrayOutputStream out, int b) {
        if (b == KISS_FEND) {
            out.write(KISS_FESC);
            out.write(KISS_TFEND);
        } else if (b == KISS_FESC) {
            out.write(KISS_FESC);
            out.write(KISS_TFESC);
        } else {
            out.write(b);
        }
    }
}
