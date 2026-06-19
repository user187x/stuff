package com;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.zip.CRC32;

/**
 * LoRaTransferProtocol — a small reliable, ordered file-transfer protocol
 * layered on top of the line-based LoRa serial link.
 *
 * ---------------------------------------------------------------------------
 *  WHY IT LOOKS THE WAY IT DOES
 * ---------------------------------------------------------------------------
 *  A LoRa radio is HALF-DUPLEX (it can transmit OR receive, not both at once)
 *  and every over-the-air packet is small (raw LoRa tops out at 255 bytes).
 *  So we use a classic "stop-and-wait ARQ":
 *
 *      sender ->  one frame  -> receiver
 *      sender <-  one ACK    <- receiver      (then the next frame)
 *
 *  Only one side is talking at any instant, which avoids collisions, and every
 *  frame must be positively acknowledged before the next is sent. Each frame
 *  carries a SEQUENCE NUMBER and a CRC so the receiver can detect loss/
 *  corruption and the sender knows exactly what to retransmit. When the last
 *  chunk is acknowledged the sender sends FIN; the receiver reassembles every
 *  chunk in order, checks the whole-file CRC, and only then ACKs the FIN.
 *
 * ---------------------------------------------------------------------------
 *  WIRE FORMAT  (one line per frame, '|'-delimited, exactly 5 fields)
 * ---------------------------------------------------------------------------
 *      NODE_ID | TYPE | SEQ | CRC | DATA
 *
 *    NODE_ID  hex id of the SENDER of this frame (used to drop our own echoes)
 *    TYPE     SYN | DAT | ACK | NAK | FIN
 *    SEQ      sequence number (0 = handshake, 1..N = data, N+1 = finish)
 *    CRC      CRC-32 (hex) of this chunk (DAT) or of the whole file (SYN/FIN)
 *    DATA     base64 chunk (DAT), or "totalChunks;fileSize;base64(name)" (SYN)
 *
 *  Binary file bytes are base64-encoded so they never contain '|', CR or LF and
 *  therefore never break the line framing. The transport layer (e.g. the serial
 *  port) is responsible only for delivering whole lines; it appends/strips the
 *  CR/LF. That keeps this class transport-agnostic and unit-testable.
 * ---------------------------------------------------------------------------
 */
public class LoRaTransferProtocol {

    // ===================== Tunables =====================

    /**
     * Max number of bytes allowed in a single on-air frame. SET THIS FROM YOUR
     * MODULE'S DATASHEET — it must be &lt;= the module's max packet size. Raw
     * LoRa (SX126x/SX127x) caps at 255; 200 is a safe default with margin.
     * The usable binary chunk size is derived from this automatically.
     */
    public static final int  DEFAULT_MAX_LORA_PAYLOAD = 200;
    /** How long to wait for an ACK of a SYN/DATA frame before retransmitting. */
    public static final long DEFAULT_ACK_TIMEOUT_MS   = 5000;
    /** Longer wait for the FIN ACK, since the receiver assembles+verifies first. */
    public static final long DEFAULT_FIN_TIMEOUT_MS    = 10000;
    /** Max transmit attempts for any single frame before the transfer fails. */
    public static final int  DEFAULT_MAX_RETRIES       = 6;

    // ===================== Frame types =====================
    private static final String SYN = "SYN"; // begin transfer (carries metadata)
    private static final String DAT = "DAT"; // a data chunk
    private static final String ACK = "ACK"; // positive acknowledgment of a seq
    private static final String NAK = "NAK"; // negative ack -> please retransmit
    private static final String FIN = "FIN"; // end of transfer
    private static final String DELIM = "|";
    private static final long   SEQ_HANDSHAKE = 0;

    /** Transport hook: hand a finished line to the radio (it adds CR/LF). */
    public interface LineSender { void sendLine(String line); }

    /** Progress / event callbacks. All methods are optional (defaults are no-ops). */
    public interface Listener {
        default void onLog(String msg) {}
        default void onSendProgress(int done, int total) {}
        default void onSendComplete(String filename) {}
        default void onReceiveStart(String filename, long size, int totalChunks) {}
        default void onReceiveProgress(int done, int total) {}
        default void onReceiveComplete(String filename, byte[] data) {}
        default void onError(String msg) {}
    }

    private final String nodeId;
    private final LineSender sender;
    private final Listener listener;
    private final int  maxLoraPayload;
    private final long ackTimeoutMs;
    private final long finTimeoutMs;
    private final int  maxRetries;
    private final int  chunkSize;

    // ACK/NAK frames are received on the transport (reader) thread and handed
    // to the sender thread, which is blocked waiting for them.
    private final BlockingQueue<Frame> controlFrames = new LinkedBlockingQueue<>();

    // Receive-side reassembly state.
    private volatile boolean receiving = false;
    private String   rxFilename;
    private long     rxFileSize;
    private int      rxTotalChunks;
    private String   rxFileCrc;
    private byte[][] rxChunks;   // 1-indexed: rxChunks[1..rxTotalChunks]
    private int      rxCount;

    public LoRaTransferProtocol(String nodeId, LineSender sender, Listener listener) {
        this(nodeId, sender, listener,
             DEFAULT_MAX_LORA_PAYLOAD, DEFAULT_ACK_TIMEOUT_MS, DEFAULT_FIN_TIMEOUT_MS, DEFAULT_MAX_RETRIES);
    }

    public LoRaTransferProtocol(String nodeId, LineSender sender, Listener listener,
                                int maxLoraPayload, long ackTimeoutMs, long finTimeoutMs, int maxRetries) {
        this.nodeId         = nodeId;
        this.sender         = sender;
        this.listener       = (listener != null) ? listener : new Listener() {};
        this.maxLoraPayload = maxLoraPayload;
        this.ackTimeoutMs   = ackTimeoutMs;
        this.finTimeoutMs   = finTimeoutMs;
        this.maxRetries     = maxRetries;
        this.chunkSize      = computeChunkSize(maxLoraPayload);
    }

    /**
     * Work out how many raw bytes fit in one frame once we account for the
     * textual framing overhead AND base64 expansion (4 chars per 3 bytes).
     */
    private static int computeChunkSize(int maxLoraPayload) {
        // worst-case overhead: id(6)+type(3)+seq(~10)+crc(8)+4 delimiters + CR/LF & margin(7)
        int overhead = 6 + 3 + 10 + 8 + 4 + 7;
        int b64Capacity = maxLoraPayload - overhead;
        if (b64Capacity < 8) b64Capacity = 8;
        int raw = (b64Capacity / 4) * 3;
        return Math.max(raw, 12);
    }

    public String getNodeId()      { return nodeId; }
    public int    getChunkSize()   { return chunkSize; }
    public int    getMaxLoraPayload() { return maxLoraPayload; }

    // ===================== SENDING =====================
    // NOTE: blocking. Call this on a background/worker thread, never the UI thread.

    /** Convenience: send the bytes of a file. */
    public boolean sendFile(java.io.File file) throws java.io.IOException {
        byte[] data = java.nio.file.Files.readAllBytes(file.toPath());
        return sendData(data, file.getName());
    }

    /** Reliably transfer {@code data} as {@code filename}. Returns true on success. */
    public boolean sendData(byte[] data, String filename) {
        try {
            controlFrames.clear(); // drop any stale ACKs from a previous transfer

            final int total = (int) Math.ceil(data.length / (double) chunkSize);
            final String fileCrc = crc32Hex(data, 0, data.length);
            final String meta = total + ";" + data.length + ";" + b64(filename.getBytes(StandardCharsets.UTF_8));

            listener.onLog("TX start: " + filename + " — " + data.length + " bytes in "
                    + total + " chunk(s) of <=" + chunkSize + "B");

            // 1) Handshake: announce the transfer and wait for the receiver to be ready.
            if (!sendAndWait(SYN, SEQ_HANDSHAKE, fileCrc, meta, ackTimeoutMs, "handshake")) {
                listener.onError("Handshake failed — no response from a receiver.");
                return false;
            }

            // 2) Stream the data chunks, one acknowledged frame at a time.
            for (int seq = 1; seq <= total; seq++) {
                int start = (seq - 1) * chunkSize;
                int len   = Math.min(chunkSize, data.length - start);
                String chunkCrc = crc32Hex(data, start, len);
                String payload  = b64(data, start, len);
                if (!sendAndWait(DAT, seq, chunkCrc, payload, ackTimeoutMs, "chunk " + seq + "/" + total)) {
                    listener.onError("Gave up at chunk " + seq + "/" + total + " after " + maxRetries + " attempts.");
                    return false;
                }
                listener.onSendProgress(seq, total);
            }

            // 3) Finish: receiver reassembles, verifies the whole-file CRC, then ACKs.
            long finSeq = total + 1;
            if (!sendAndWait(FIN, finSeq, fileCrc, "", finTimeoutMs, "finish")) {
                listener.onError("Receiver never confirmed the completed file (timeout or checksum mismatch).");
                return false;
            }

            listener.onLog("TX complete & confirmed: " + filename);
            listener.onSendComplete(filename);
            return true;

        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            listener.onError("Transfer interrupted.");
            return false;
        } catch (Exception e) {
            listener.onError("Transfer error: " + e.getMessage());
            return false;
        }
    }

    /** Send one frame and wait for its ACK, retransmitting on NAK or timeout. */
    private boolean sendAndWait(String type, long seq, String f3, String f4, long timeout, String label)
            throws InterruptedException {
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            sender.sendLine(frame(type, seq, f3, f4));
            Frame resp = awaitControl(seq, timeout);
            if (resp == null) {
                listener.onLog("Timeout on " + label + " (try " + attempt + "/" + maxRetries + ") — retransmitting");
                continue;
            }
            if (ACK.equals(resp.type)) return true;
            // NAK: receiver got it but the CRC failed — resend right away (brief backoff).
            listener.onLog("NAK on " + label + " (try " + attempt + "/" + maxRetries + ") — retransmitting");
            Thread.sleep(50);
        }
        return false;
    }

    /** Pull the ACK/NAK that matches {@code expectedSeq}, ignoring stale ones, until timeout. */
    private Frame awaitControl(long expectedSeq, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (true) {
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) return null;
            Frame f = controlFrames.poll(remaining, TimeUnit.MILLISECONDS);
            if (f == null) return null;
            if (f.seq == expectedSeq && (ACK.equals(f.type) || NAK.equals(f.type))) return f;
            // else: a duplicate/late control frame for some other seq — discard and keep waiting
        }
    }

    // ===================== RECEIVING =====================

    /** Feed every complete line that arrives from the transport into here. */
    public void feed(String line) {
        if (line == null) return;
        Frame f = parse(line.trim());
        if (f == null) {
            if (!line.trim().isEmpty()) listener.onLog("RX (ignored, not a protocol frame): " + line.trim());
            return;
        }
        // The cornerstone guard: a frame stamped with OUR id is just our own
        // transmission echoed back over the radio. Never act on it.
        if (nodeId.equals(f.senderId)) return;

        switch (f.type) {
            case ACK:
            case NAK: controlFrames.offer(f); break;  // wake the sender thread
            case SYN: handleSyn(f);  break;
            case DAT: handleData(f); break;
            case FIN: handleFin(f);  break;
            default:  listener.onLog("RX (unknown frame type: " + f.type + ")");
        }
    }

    private void handleSyn(Frame f) {
        try {
            String[] meta = f.data.split(";", 3);
            int  total = Integer.parseInt(meta[0]);
            long size  = Long.parseLong(meta[1]);
            String name = new String(Base64.getDecoder().decode(meta[2]), StandardCharsets.UTF_8);

            rxFilename    = sanitize(name);
            rxFileSize    = size;
            rxTotalChunks = total;
            rxFileCrc     = f.crc;
            rxChunks      = new byte[total + 1][];
            rxCount       = 0;
            receiving     = true;

            listener.onLog("RX start: " + name + " — " + size + " bytes, " + total + " chunk(s)");
            listener.onReceiveStart(rxFilename, size, total);
            sender.sendLine(frame(ACK, SEQ_HANDSHAKE, "", "")); // "ready"
        } catch (Exception e) {
            listener.onError("Malformed SYN: " + e.getMessage());
        }
    }

    private void handleData(Frame f) {
        if (!receiving || rxChunks == null) return; // no active session; sender will time out
        int seq = (int) f.seq;
        if (seq < 1 || seq >= rxChunks.length) { listener.onLog("RX chunk seq out of range: " + seq); return; }

        byte[] chunk;
        try {
            chunk = Base64.getDecoder().decode(f.data);
        } catch (IllegalArgumentException bad) {
            sender.sendLine(frame(NAK, seq, "", "")); // corrupt base64 -> ask again
            return;
        }
        if (!crc32Hex(chunk, 0, chunk.length).equalsIgnoreCase(f.crc)) {
            sender.sendLine(frame(NAK, seq, "", "")); // CRC mismatch -> ask again
            return;
        }
        if (rxChunks[seq] == null) {        // new chunk
            rxChunks[seq] = chunk;
            rxCount++;
            listener.onReceiveProgress(rxCount, rxTotalChunks);
        }
        // ACK even duplicates: a re-sent chunk usually means our previous ACK was lost.
        sender.sendLine(frame(ACK, seq, "", ""));
    }

    private void handleFin(Frame f) {
        long finSeq = f.seq;
        if (!receiving || rxChunks == null) { sender.sendLine(frame(NAK, finSeq, "", "")); return; }
        if (rxCount < rxTotalChunks) {
            listener.onLog("RX FIN but chunks are missing (" + rxCount + "/" + rxTotalChunks + ")");
            sender.sendLine(frame(NAK, finSeq, "", ""));
            return;
        }

        ByteArrayOutputStream bos = new ByteArrayOutputStream(rxFileSize > 0 ? (int) rxFileSize : 32);
        for (int i = 1; i <= rxTotalChunks; i++) bos.write(rxChunks[i], 0, rxChunks[i].length);
        byte[] full = bos.toByteArray();

        String crc = crc32Hex(full, 0, full.length);
        if (crc.equalsIgnoreCase(rxFileCrc)) {
            listener.onLog("RX complete & verified: " + rxFilename + " — " + full.length + " bytes");
            sender.sendLine(frame(ACK, finSeq, "", ""));
            receiving = false;
            listener.onReceiveComplete(rxFilename, full);
        } else {
            listener.onError("RX checksum mismatch on " + rxFilename + " (got " + crc + ", expected " + rxFileCrc + ")");
            sender.sendLine(frame(NAK, finSeq, "", ""));
            receiving = false;
        }
    }

    // ===================== helpers =====================

    private String frame(String type, long seq, String f3, String f4) {
        return nodeId + DELIM + type + DELIM + seq + DELIM
                + (f3 == null ? "" : f3) + DELIM + (f4 == null ? "" : f4);
    }

    private static Frame parse(String line) {
        String[] p = line.split("\\|", 5);
        if (p.length < 5) return null;            // not one of our 5-field frames
        Frame f = new Frame();
        f.senderId = p[0];
        f.type     = p[1];
        try { f.seq = Long.parseLong(p[2]); } catch (NumberFormatException e) { return null; }
        f.crc  = p[3];
        f.data = p[4];
        return f;
    }

    private static String b64(byte[] data) { return Base64.getEncoder().encodeToString(data); }

    private static String b64(byte[] data, int off, int len) {
        byte[] slice = new byte[len];
        System.arraycopy(data, off, slice, 0, len);
        return Base64.getEncoder().encodeToString(slice);
    }

    private static String crc32Hex(byte[] data, int off, int len) {
        CRC32 c = new CRC32();
        c.update(data, off, len);
        return String.format("%08x", c.getValue());
    }

    /** Strip path separators / illegal filename chars so we can safely save the file. */
    private static String sanitize(String name) {
        String n = name.replaceAll("[\\\\/:*?\"<>|\\r\\n]", "_").trim();
        return n.isEmpty() ? "received.bin" : n;
    }

    private static class Frame {
        String senderId, type, crc, data;
        long seq;
    }
}
