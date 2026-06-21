package xxx;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Application-layer protocol that rides on top of the module's transparent
 * "stream mode". The dongle itself just moves opaque bytes between units that
 * share the same RF address + channel; this class gives those bytes structure
 * so we can do addressing, message typing, de-duplication and file transfer.
 *
 * Wire frame (all multi-byte fields big-endian):
 *
 *   off  field   size  notes
 *   0    SYNC0   1     0xA5  framing marker
 *   1    SYNC1   1     0x5A  framing marker
 *   2    VER     1     protocol version (currently 1)
 *   3    TYPE    1     {@link Type} code
 *   4    SRC     2     source app address (unsigned 16-bit)
 *   6    DST     2     dest app address (0xFFFF = broadcast)
 *   8    MSGID   2     message / transfer id
 *   10   SEQ     2     chunk index (file) or generic sequence
 *   12   TOTAL   2     total chunks (file) or generic
 *   14   LEN     1     payload length (0..223)
 *   15   PAYLOAD LEN
 *   ..   CRC16   2     CRC-16/CCITT-FALSE over bytes [VER .. end-of-payload]
 *
 * Header is 15 bytes, CRC adds 2, so overhead is 17 bytes. The module
 * auto-packetises anything over 240 bytes, so we cap a single frame at 240 to
 * keep one frame == one over-the-air LoRa packet (important for file chunks).
 */
public final class Protocol {

    public static final int VERSION       = 1;
    public static final byte SYNC0         = (byte) 0xA5;
    public static final byte SYNC1         = (byte) 0x5A;
    public static final int HEADER_LEN     = 15;
    public static final int CRC_LEN        = 2;
    public static final int MAX_FRAME      = 240;                         // single LoRa packet
    public static final int MAX_PAYLOAD    = MAX_FRAME - HEADER_LEN - CRC_LEN; // 223
    public static final int BROADCAST_ADDR = 0xFFFF;

    private Protocol() { }

    /** Frame type codes. */
    public enum Type {
        DATA(0x01),
        BEACON(0x02),
        ACK(0x03),
        FILE_BEGIN(0x10),
        FILE_DATA(0x11),
        FILE_END(0x12);

        public final int code;
        Type(int code) { this.code = code; }

        public static Type fromCode(int c) {
            for (Type t : values()) if (t.code == c) return t;
            return null;
        }
    }

    /** A decoded (or to-be-encoded) protocol frame. */
    public static final class Frame {
        public final int version;
        public final Type type;
        public final int src;     // unsigned 16-bit
        public final int dst;     // unsigned 16-bit
        public final int msgId;   // unsigned 16-bit
        public final int seq;     // unsigned 16-bit
        public final int total;   // unsigned 16-bit
        public final byte[] payload;

        public Frame(Type type, int src, int dst, int msgId, int seq, int total, byte[] payload) {
            this(VERSION, type, src, dst, msgId, seq, total, payload);
        }

        public Frame(int version, Type type, int src, int dst, int msgId, int seq, int total, byte[] payload) {
            if (type == null) throw new IllegalArgumentException("type");
            byte[] p = (payload == null) ? new byte[0] : payload;
            if (p.length > MAX_PAYLOAD) {
                throw new IllegalArgumentException("payload " + p.length + " > " + MAX_PAYLOAD);
            }
            this.version = version;
            this.type = type;
            this.src = src & 0xFFFF;
            this.dst = dst & 0xFFFF;
            this.msgId = msgId & 0xFFFF;
            this.seq = seq & 0xFFFF;
            this.total = total & 0xFFFF;
            this.payload = p;
        }

        /** Convenience: payload interpreted as UTF-8 text. */
        public String text() {
            return new String(payload, StandardCharsets.UTF_8);
        }

        /** Serialise this frame to its on-wire byte form. */
        public byte[] encode() {
            int len = payload.length;
            byte[] out = new byte[HEADER_LEN + len + CRC_LEN];
            out[0] = SYNC0;
            out[1] = SYNC1;
            out[2] = (byte) version;
            out[3] = (byte) type.code;
            putU16(out, 4, src);
            putU16(out, 6, dst);
            putU16(out, 8, msgId);
            putU16(out, 10, seq);
            putU16(out, 12, total);
            out[14] = (byte) len;
            System.arraycopy(payload, 0, out, HEADER_LEN, len);
            int crc = crc16(out, 2, (HEADER_LEN - 2) + len); // VER .. last payload byte
            putU16(out, HEADER_LEN + len, crc);
            return out;
        }

        @Override
        public String toString() {
            return "Frame{" + type + " src=" + src + " dst=" + dst
                    + " id=" + msgId + " seq=" + seq + "/" + total
                    + " len=" + payload.length + "}";
        }
    }

    /**
     * Stateful, byte-stream decoder. Serial data arrives in arbitrary chunks
     * (a frame may be split across reads, or several frames may share one read),
     * so feed() buffers internally, recovers framing on the SYNC marker, and
     * returns every complete, CRC-valid frame it can extract.
     */
    public static final class Decoder {
        private static final int MAX_BUFFER = 4096; // guard against garbage floods
        private byte[] buf = new byte[256];
        private int size = 0;

        public List<Frame> feed(byte[] data, int len) {
            List<Frame> out = new ArrayList<>();
            if (len <= 0) return out;
            ensureCapacity(size + len);
            System.arraycopy(data, 0, buf, size, len);
            size += len;

            int i = 0;
            while (true) {
                // scan to a SYNC pair
                while (i + 1 < size && !(buf[i] == SYNC0 && buf[i + 1] == SYNC1)) i++;
                if (i + 1 >= size) break;                     // need more bytes to confirm sync
                if (size - i < HEADER_LEN) break;             // wait for full header
                int payLen = buf[i + 14] & 0xFF;
                if (payLen > MAX_PAYLOAD) { i++; continue; }  // impossible length -> resync
                int frameLen = HEADER_LEN + payLen + CRC_LEN;
                if (size - i < frameLen) break;               // wait for full frame
                int crcCalc = crc16(buf, i + 2, (HEADER_LEN - 2) + payLen);
                int crcRecv = getU16(buf, i + HEADER_LEN + payLen);
                if (crcCalc != crcRecv) { i++; continue; }    // corrupt -> resync past this sync
                Frame f = parse(buf, i, payLen);
                if (f != null) out.add(f);
                i += frameLen;                                // consume whole frame
            }

            // compact: drop everything before i (already consumed or unmatched)
            if (i > 0) {
                System.arraycopy(buf, i, buf, 0, size - i);
                size -= i;
            }
            if (size > MAX_BUFFER) size = 0; // give up on pathological garbage
            return out;
        }

        public void reset() { size = 0; }

        private static Frame parse(byte[] b, int off, int payLen) {
            int ver = b[off + 2] & 0xFF;
            Type type = Type.fromCode(b[off + 3] & 0xFF);
            if (type == null) return null;
            int src = getU16(b, off + 4);
            int dst = getU16(b, off + 6);
            int id = getU16(b, off + 8);
            int seq = getU16(b, off + 10);
            int total = getU16(b, off + 12);
            byte[] payload = new byte[payLen];
            System.arraycopy(b, off + HEADER_LEN, payload, 0, payLen);
            return new Frame(ver, type, src, dst, id, seq, total, payload);
        }

        private void ensureCapacity(int needed) {
            if (needed <= buf.length) return;
            int cap = buf.length;
            while (cap < needed) cap <<= 1;
            byte[] bigger = new byte[cap];
            System.arraycopy(buf, 0, bigger, 0, size);
            buf = bigger;
        }
    }

    // --- helpers ---------------------------------------------------------

    static void putU16(byte[] b, int off, int v) {
        b[off]     = (byte) ((v >> 8) & 0xFF);
        b[off + 1] = (byte) (v & 0xFF);
    }

    static int getU16(byte[] b, int off) {
        return ((b[off] & 0xFF) << 8) | (b[off + 1] & 0xFF);
    }

    static void putU32(byte[] b, int off, long v) {
        b[off]     = (byte) ((v >> 24) & 0xFF);
        b[off + 1] = (byte) ((v >> 16) & 0xFF);
        b[off + 2] = (byte) ((v >> 8) & 0xFF);
        b[off + 3] = (byte) (v & 0xFF);
    }

    static long getU32(byte[] b, int off) {
        return ((long) (b[off] & 0xFF) << 24)
                | ((b[off + 1] & 0xFF) << 16)
                | ((b[off + 2] & 0xFF) << 8)
                | (b[off + 3] & 0xFF);
    }

    /** CRC-16/CCITT-FALSE (poly 0x1021, init 0xFFFF, no reflection, no xorout). */
    public static int crc16(byte[] data, int off, int len) {
        int crc = 0xFFFF;
        for (int i = off; i < off + len; i++) {
            crc ^= (data[i] & 0xFF) << 8;
            for (int b = 0; b < 8; b++) {
                if ((crc & 0x8000) != 0) crc = (crc << 1) ^ 0x1021;
                else crc <<= 1;
                crc &= 0xFFFF;
            }
        }
        return crc & 0xFFFF;
    }

    /** Small utility used by callers that just need a UTF-8 byte slice. */
    public static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    /** Concatenate helper for building composite payloads. */
    public static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        for (byte[] p : parts) bos.write(p, 0, p.length);
        return bos.toByteArray();
    }
}
