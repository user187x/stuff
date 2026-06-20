package xxx.simple;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.zip.CRC32;

/**
 * File transfer built on top of {@link Protocol}.
 *
 * A transfer is three kinds of frame:
 *   FILE_BEGIN  payload = [nameLen:1][name][size:4][crc32:4][chunkSize:2]
 *   FILE_DATA   payload = raw chunk bytes, seq = chunk index, total = chunk count
 *   FILE_END    payload = [crc32:4]            (seq = chunk count, sanity marker)
 *
 * Chunk size is kept small so every FILE_DATA frame fits in one over-the-air
 * LoRa packet (240-byte cap including protocol overhead). The framing logic is
 * separated from the actual sending so it can be unit-tested without hardware.
 */
public final class FileTransfer {

    /** Default chunk payload. 192 + 17 bytes of protocol overhead = 209 < 240. */
    public static final int DEFAULT_CHUNK = 192;

    private static final Random RND = new Random();

    private final Sender sender;
    private final int srcAddr;
    private final long interFrameDelayMs;

    /**
     * @param sender            transmit channel (must already suppress self-RX)
     * @param srcAddr           our application address
     * @param interFrameDelayMs pacing between frames; LoRa is slow, so give the
     *                          air time to clear (200ms is a safe starting point)
     */
    public FileTransfer(Sender sender, int srcAddr, long interFrameDelayMs) {
        this.sender = sender;
        this.srcAddr = srcAddr;
        this.interFrameDelayMs = interFrameDelayMs;
    }

    public interface Progress {
        void update(String name, int chunksSent, int chunksTotal);
    }

    /** Send a file to {@code dstAddr} (use {@link Protocol#BROADCAST_ADDR} for all). */
    public void sendFile(File file, int dstAddr, int chunkSize, Progress progress) throws IOException {
        byte[] data = Files.readAllBytes(file.toPath());
        Plan plan = buildPlan(file.getName(), data, srcAddr, dstAddr, chunkSize);

        sender.sendFrame(plan.begin);
        sleep(interFrameDelayMs);

        for (int i = 0; i < plan.chunks.length; i++) {
            sender.sendFrame(plan.chunks[i]);
            if (progress != null) progress.update(file.getName(), i + 1, plan.chunks.length);
            sleep(interFrameDelayMs);
        }

        sender.sendFrame(plan.end);
    }

    // --- pure framing (no serial), exposed for testing ----------------------

    /** The ordered set of frames that make up one file transfer. */
    public static final class Plan {
        public final Protocol.Frame begin;
        public final Protocol.Frame[] chunks;
        public final Protocol.Frame end;
        public final int msgId;

        Plan(Protocol.Frame begin, Protocol.Frame[] chunks, Protocol.Frame end, int msgId) {
            this.begin = begin;
            this.chunks = chunks;
            this.end = end;
            this.msgId = msgId;
        }
    }

    public static Plan buildPlan(String name, byte[] data, int src, int dst, int chunkSize) {
        if (chunkSize <= 0 || chunkSize > Protocol.MAX_PAYLOAD) chunkSize = DEFAULT_CHUNK;
        int msgId = RND.nextInt(0x10000);

        CRC32 crc = new CRC32();
        crc.update(data);
        long fileCrc = crc.getValue();

        int totalChunks = (data.length + chunkSize - 1) / chunkSize;
        if (totalChunks == 0) totalChunks = 0; // empty file => just BEGIN + END

        byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
        if (nameBytes.length > 200) { // keep BEGIN payload within one packet
            byte[] trimmed = new byte[200];
            System.arraycopy(nameBytes, 0, trimmed, 0, 200);
            nameBytes = trimmed;
        }
        byte[] beginPayload = new byte[1 + nameBytes.length + 4 + 4 + 2];
        int p = 0;
        beginPayload[p++] = (byte) nameBytes.length;
        System.arraycopy(nameBytes, 0, beginPayload, p, nameBytes.length); p += nameBytes.length;
        Protocol.putU32(beginPayload, p, data.length); p += 4;
        Protocol.putU32(beginPayload, p, fileCrc);     p += 4;
        Protocol.putU16(beginPayload, p, chunkSize);

        Protocol.Frame begin = new Protocol.Frame(
                Protocol.Type.FILE_BEGIN, src, dst, msgId, 0, totalChunks, beginPayload);

        Protocol.Frame[] chunks = new Protocol.Frame[totalChunks];
        for (int i = 0; i < totalChunks; i++) {
            int start = i * chunkSize;
            int len = Math.min(chunkSize, data.length - start);
            byte[] chunk = new byte[len];
            System.arraycopy(data, start, chunk, 0, len);
            chunks[i] = new Protocol.Frame(
                    Protocol.Type.FILE_DATA, src, dst, msgId, i, totalChunks, chunk);
        }

        byte[] endPayload = new byte[4];
        Protocol.putU32(endPayload, 0, fileCrc);
        Protocol.Frame end = new Protocol.Frame(
                Protocol.Type.FILE_END, src, dst, msgId, totalChunks, totalChunks, endPayload);

        return new Plan(begin, chunks, end, msgId);
    }

    // --- receive side -------------------------------------------------------

    /**
     * Collects incoming FILE_* frames and reassembles complete files. Keyed by
     * (source address, message id) so several transfers can interleave.
     */
    public static final class Reassembler {
        public interface Completed {
            void onFile(int src, String name, byte[] data, boolean crcOk);
        }

        private static final class InProgress {
            String name;
            int size;
            long crc;
            int chunkSize;
            int totalChunks;
            byte[][] chunks;
            boolean[] have;
            int received;
        }

        private final Map<Long, InProgress> active = new HashMap<>();
        private final Path outputDir;
        private final Completed callback;

        /** @param outputDir where completed files are written (null = don't write to disk). */
        public Reassembler(Path outputDir, Completed callback) {
            this.outputDir = outputDir;
            this.callback = callback;
        }

        public void onFrame(Protocol.Frame f) {
            long key = ((long) f.src << 16) | f.msgId;
            switch (f.type) {
                case FILE_BEGIN -> begin(key, f);
                case FILE_DATA  -> data(key, f);
                case FILE_END   -> end(key, f);
                default         -> { /* not ours */ }
            }
        }

        private void begin(long key, Protocol.Frame f) {
            byte[] b = f.payload;
            int p = 0;
            int nameLen = b[p++] & 0xFF;
            String name = new String(b, p, nameLen, StandardCharsets.UTF_8); p += nameLen;
            int size = (int) Protocol.getU32(b, p); p += 4;
            long crc = Protocol.getU32(b, p); p += 4;
            int chunkSize = Protocol.getU16(b, p);

            InProgress t = new InProgress();
            t.name = name;
            t.size = size;
            t.crc = crc;
            t.chunkSize = chunkSize;
            t.totalChunks = f.total;
            t.chunks = new byte[Math.max(f.total, 0)][];
            t.have = new boolean[Math.max(f.total, 0)];
            active.put(key, t);
        }

        private void data(long key, Protocol.Frame f) {
            InProgress t = active.get(key);
            if (t == null) return;                  // missed BEGIN
            if (f.seq < 0 || f.seq >= t.chunks.length) return;
            if (!t.have[f.seq]) {
                t.chunks[f.seq] = f.payload;
                t.have[f.seq] = true;
                t.received++;
            }
        }

        private void end(long key, Protocol.Frame f) {
            InProgress t = active.remove(key);
            if (t == null) return;

            byte[] data = new byte[t.size];
            int pos = 0;
            boolean complete = (t.received == t.totalChunks);
            for (int i = 0; i < t.totalChunks && complete; i++) {
                if (!t.have[i]) { complete = false; break; }
                byte[] c = t.chunks[i];
                System.arraycopy(c, 0, data, pos, c.length);
                pos += c.length;
            }

            boolean crcOk = false;
            if (complete && pos == t.size) {
                CRC32 crc = new CRC32();
                crc.update(data);
                crcOk = (crc.getValue() == t.crc);
            }

            if (crcOk && outputDir != null) {
                try {
                    Files.createDirectories(outputDir);
                    Files.write(outputDir.resolve(safeName(t.name)), data);
                } catch (IOException e) {
                    // surface via callback flag only; caller logs
                }
            }
            if (callback != null) callback.onFile(f.src, t.name, complete ? data : null, crcOk);
        }

        /** 0..1 progress for a given source/message id, or -1 if unknown. */
        public double progress(int src, int msgId) {
            InProgress t = active.get(((long) src << 16) | (msgId & 0xFFFF));
            if (t == null || t.totalChunks == 0) return -1;
            return (double) t.received / t.totalChunks;
        }

        private static String safeName(String name) {
            String n = name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
            return n.isEmpty() ? "received.bin" : n;
        }
    }

    private static void sleep(long ms) {
        if (ms <= 0) return;
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
