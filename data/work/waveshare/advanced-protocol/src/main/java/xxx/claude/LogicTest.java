package xxx.claudewaveshare;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Pure-logic tests with no serial dependency. Exercises the protocol codec, the
 * stream decoder under fragmentation/concatenation/corruption, the full file
 * transfer build -> wire -> reassemble path, and the beacon peer table.
 *
 * Run: java -cp out xxx.simple.LogicTest
 */
public class LogicTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) throws Exception {
        testFrameRoundTrip();
        testCrcRejectsCorruption();
        testDecoderConcatenated();
        testDecoderFragmented();
        testDecoderResyncAfterGarbage();
        testFileTransferRoundTrip();
        testFileTransferEmpty();
        testFileTransferCrcMismatch();
        testBeaconPeerTable();
        testBeaconIgnoresSelf();

        System.out.println();
        System.out.println("==================================");
        System.out.printf("RESULT: %d passed, %d failed%n", passed, failed);
        System.out.println("==================================");
        if (failed > 0) System.exit(1);
    }

    // --- protocol codec -----------------------------------------------------

    private static void testFrameRoundTrip() {
        byte[] payload = "hello lora".getBytes(StandardCharsets.UTF_8);
        Protocol.Frame f = new Protocol.Frame(
                Protocol.Type.DATA, 0x1234, 0xFFFF, 0x00AB, 3, 9, payload);
        byte[] wire = f.encode();

        Protocol.Decoder dec = new Protocol.Decoder();
        List<Protocol.Frame> got = dec.feed(wire, wire.length);

        check("round-trip: exactly one frame", got.size() == 1);
        if (got.size() == 1) {
            Protocol.Frame r = got.get(0);
            check("round-trip: type", r.type == Protocol.Type.DATA);
            check("round-trip: src", r.src == 0x1234);
            check("round-trip: dst", r.dst == 0xFFFF);
            check("round-trip: msgId", r.msgId == 0x00AB);
            check("round-trip: seq", r.seq == 3);
            check("round-trip: total", r.total == 9);
            check("round-trip: payload text", "hello lora".equals(r.text()));
        }
    }

    private static void testCrcRejectsCorruption() {
        Protocol.Frame f = new Protocol.Frame(
                Protocol.Type.DATA, 1, 2, 5, 0, 0, Protocol.utf8("payload"));
        byte[] wire = f.encode();
        // Flip a bit in the payload region.
        wire[Protocol.HEADER_LEN + 2] ^= 0x40;

        Protocol.Decoder dec = new Protocol.Decoder();
        List<Protocol.Frame> got = dec.feed(wire, wire.length);
        check("crc: corrupted frame is rejected", got.isEmpty());
    }

    private static void testDecoderConcatenated() {
        Protocol.Frame a = new Protocol.Frame(Protocol.Type.DATA, 1, 2, 1, 0, 0, Protocol.utf8("AAA"));
        Protocol.Frame b = new Protocol.Frame(Protocol.Type.BEACON, 3, 0xFFFF, 0, 0, 0, Protocol.utf8("node-b"));
        Protocol.Frame c = new Protocol.Frame(Protocol.Type.DATA, 4, 2, 2, 0, 0, Protocol.utf8("CCC"));
        byte[] wire = Protocol.concat(a.encode(), b.encode(), c.encode());

        Protocol.Decoder dec = new Protocol.Decoder();
        List<Protocol.Frame> got = dec.feed(wire, wire.length);
        check("concatenated: three frames decoded", got.size() == 3);
        if (got.size() == 3) {
            check("concatenated: order/text 1", "AAA".equals(got.get(0).text()));
            check("concatenated: order/text 2", "node-b".equals(got.get(1).text()));
            check("concatenated: order/text 3", "CCC".equals(got.get(2).text()));
        }
    }

    private static void testDecoderFragmented() {
        Protocol.Frame f = new Protocol.Frame(
                Protocol.Type.DATA, 7, 8, 9, 0, 0, Protocol.utf8("fragmented payload here"));
        byte[] wire = f.encode();

        Protocol.Decoder dec = new Protocol.Decoder();
        List<Protocol.Frame> all = new ArrayList<>();
        // Feed one byte at a time; nothing should emit until the final byte.
        for (int i = 0; i < wire.length; i++) {
            List<Protocol.Frame> got = dec.feed(new byte[]{wire[i]}, 1);
            if (i < wire.length - 1) {
                if (!got.isEmpty()) { check("fragmented: no early emit", false); }
            }
            all.addAll(got);
        }
        check("fragmented: exactly one frame after last byte", all.size() == 1);
        if (all.size() == 1) check("fragmented: payload intact",
                "fragmented payload here".equals(all.get(0).text()));
    }

    private static void testDecoderResyncAfterGarbage() {
        Protocol.Frame f = new Protocol.Frame(Protocol.Type.DATA, 1, 2, 3, 0, 0, Protocol.utf8("good"));
        byte[] good = f.encode();
        byte[] garbage = {0x00, 0x11, (byte) 0xA5, 0x22, 0x33, (byte) 0xFF, 0x5A, 0x01};
        byte[] wire = Protocol.concat(garbage, good);

        Protocol.Decoder dec = new Protocol.Decoder();
        List<Protocol.Frame> got = dec.feed(wire, wire.length);
        check("resync: recovers the good frame after garbage", got.size() == 1);
        if (got.size() == 1) check("resync: correct payload", "good".equals(got.get(0).text()));
    }

    // --- file transfer ------------------------------------------------------

    private static void testFileTransferRoundTrip() throws Exception {
        // A payload several chunks long, with non-trivial bytes.
        byte[] data = new byte[5000];
        new Random(42).nextBytes(data);
        String name = "photo.bin";

        FileTransfer.Plan plan = FileTransfer.buildPlan(name, data, 0x0001, 0x0002, FileTransfer.DEFAULT_CHUNK);

        // Serialise begin + chunks + end onto a single wire byte stream, exactly
        // as the Sender would emit them.
        List<byte[]> parts = new ArrayList<>();
        parts.add(plan.begin.encode());
        for (Protocol.Frame ch : plan.chunks) parts.add(ch.encode());
        parts.add(plan.end.encode());
        byte[] wire = Protocol.concat(parts.toArray(new byte[0][]));

        // Feed it through a decoder in awkwardly-sized reads, into a reassembler.
        Path outDir = Files.createTempDirectory("lora-test-out");
        final byte[][] result = new byte[1][];
        final boolean[] crcFlag = new boolean[1];
        final String[] gotName = new String[1];
        FileTransfer.Reassembler ra = new FileTransfer.Reassembler(outDir, (src, n, d, ok) -> {
            gotName[0] = n; result[0] = d; crcFlag[0] = ok;
        });

        Protocol.Decoder dec = new Protocol.Decoder();
        int pos = 0, readSize = 73; // deliberately not aligned to frame size
        while (pos < wire.length) {
            int len = Math.min(readSize, wire.length - pos);
            byte[] slice = new byte[len];
            System.arraycopy(wire, pos, slice, 0, len);
            for (Protocol.Frame fr : dec.feed(slice, len)) ra.onFrame(fr);
            pos += len;
        }

        check("file: completed with CRC ok", crcFlag[0]);
        check("file: name preserved", name.equals(gotName[0]));
        check("file: byte-exact reassembly", result[0] != null && java.util.Arrays.equals(data, result[0]));

        Path written = outDir.resolve(name);
        check("file: written to disk", Files.exists(written));
        if (Files.exists(written)) {
            check("file: on-disk bytes match", java.util.Arrays.equals(data, Files.readAllBytes(written)));
        }
    }

    private static void testFileTransferEmpty() {
        byte[] data = new byte[0];
        FileTransfer.Plan plan = FileTransfer.buildPlan("empty.txt", data, 1, 2, FileTransfer.DEFAULT_CHUNK);
        check("empty file: no data chunks", plan.chunks.length == 0);

        byte[] wire = Protocol.concat(plan.begin.encode(), plan.end.encode());
        final boolean[] ok = new boolean[1];
        final byte[][] out = new byte[1][];
        FileTransfer.Reassembler ra = new FileTransfer.Reassembler(null, (s, n, d, c) -> { ok[0] = c; out[0] = d; });
        Protocol.Decoder dec = new Protocol.Decoder();
        for (Protocol.Frame fr : dec.feed(wire, wire.length)) ra.onFrame(fr);
        check("empty file: completes with CRC ok", ok[0]);
        check("empty file: zero-length result", out[0] != null && out[0].length == 0);
    }

    private static void testFileTransferCrcMismatch() {
        byte[] data = new byte[1000];
        new Random(7).nextBytes(data);
        FileTransfer.Plan plan = FileTransfer.buildPlan("x.bin", data, 1, 2, 256);

        // Drop one data chunk: reassembly must report failure, not a bad file.
        List<byte[]> parts = new ArrayList<>();
        parts.add(plan.begin.encode());
        for (int i = 0; i < plan.chunks.length; i++) {
            if (i == 1) continue; // missing chunk
            parts.add(plan.chunks[i].encode());
        }
        parts.add(plan.end.encode());
        byte[] wire = Protocol.concat(parts.toArray(new byte[0][]));

        final boolean[] ok = new boolean[]{true};
        FileTransfer.Reassembler ra = new FileTransfer.Reassembler(null, (s, n, d, c) -> ok[0] = c);
        Protocol.Decoder dec = new Protocol.Decoder();
        for (Protocol.Frame fr : dec.feed(wire, wire.length)) ra.onFrame(fr);
        check("missing chunk: reported as failure", !ok[0]);
    }

    // --- beacon -------------------------------------------------------------

    private static void testBeaconPeerTable() {
        Beacon b = new Beacon(new NullSender(), 0x0001, "me", 1000);
        // staleAfterMs = 3 * interval = 3000; keep all reads within that window.
        // Two distinct peers beacon in.
        b.onBeaconReceived(new Protocol.Frame(
                Protocol.Type.BEACON, 0x0002, 0xFFFF, 0, 0, 0, Protocol.utf8("alice")), 10_000);
        b.onBeaconReceived(new Protocol.Frame(
                Protocol.Type.BEACON, 0x0003, 0xFFFF, 0, 0, 0, Protocol.utf8("bob")), 10_500);
        List<Beacon.Peer> peers = b.getPeers(10_600);
        check("beacon: two peers recorded", peers.size() == 2);

        boolean foundAlice = peers.stream().anyMatch(p -> p.address == 0x0002 && "alice".equals(p.name));
        boolean foundBob   = peers.stream().anyMatch(p -> p.address == 0x0003 && "bob".equals(p.name));
        check("beacon: alice present", foundAlice);
        check("beacon: bob present", foundBob);

        // Re-beacon from alice updates rather than duplicates.
        b.onBeaconReceived(new Protocol.Frame(
                Protocol.Type.BEACON, 0x0002, 0xFFFF, 0, 0, 0, Protocol.utf8("alice2")), 11_000);
        peers = b.getPeers(11_100);
        check("beacon: still two peers after update", peers.size() == 2);
        boolean updated = peers.stream().anyMatch(p -> p.address == 0x0002 && "alice2".equals(p.name));
        check("beacon: peer name updated", updated);

        // Advance well past the staleness window: bob (last seen 10_500) drops,
        // alice (last seen 11_000) survives.
        peers = b.getPeers(13_800);
        check("beacon: stale peer pruned", peers.size() == 1
                && peers.get(0).address == 0x0002);
    }

    private static void testBeaconIgnoresSelf() {
        Beacon b = new Beacon(new NullSender(), 0x0001, "me", 1000);
        b.onBeaconReceived(new Protocol.Frame(
                Protocol.Type.BEACON, 0x0001, 0xFFFF, 0, 0, 0, Protocol.utf8("me")), 10_000);
        check("beacon: ignores own beacon", b.getPeers().isEmpty());
    }

    // --- a Sender that writes nowhere (no serial port) ----------------------

    private static final class NullSender extends Sender {
        NullSender() { super(null); }
        @Override public void send(String m) { }
        @Override public void sendFrame(Protocol.Frame f) { }
        @Override public void sendRaw(byte[] d) { }
    }

    // --- tiny assert --------------------------------------------------------

    private static void check(String label, boolean cond) {
        if (cond) { passed++; System.out.println("  PASS  " + label); }
        else      { failed++; System.out.println("  FAIL  " + label); }
    }
}
