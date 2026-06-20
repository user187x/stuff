package xxx.simple;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Presence beaconing so units on the same channel can discover one another
 * (feature #4). Its single responsibility is the discovery layer: it periodically
 * transmits a small BEACON frame announcing this node, and it maintains a table
 * of peers heard recently.
 *
 * It deliberately does <em>not</em> read the serial port itself. {@link Radio}
 * owns the single receive path and forwards any inbound BEACON frame here via
 * {@link #onBeaconReceived}. This keeps a single decoder/listener on the wire
 * and preserves the separation between "who is out there" (this class) and
 * "move the bytes" (Listener/Sender).
 *
 * A BEACON frame carries the node's human-readable name as its UTF-8 payload,
 * its address in the protocol SRC field, and is sent to the broadcast address
 * so every unit on the channel hears it.
 */
public class Beacon {

    /** A peer we have heard a beacon from. */
    public static final class Peer {
        public final int address;
        public final String name;
        public final long lastSeenMs;

        Peer(int address, String name, long lastSeenMs) {
            this.address = address;
            this.name = name;
            this.lastSeenMs = lastSeenMs;
        }

        @Override public String toString() {
            return String.format("0x%04X \"%s\"", address, name);
        }
    }

    private final Sender sender;
    private final int myAddr;
    private final String nodeName;
    private final long intervalMs;

    /** Peers are pruned once unheard for this multiple of the beacon interval. */
    private final long staleAfterMs;

    private final Map<Integer, Peer> peers = new ConcurrentHashMap<>();

    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> task;

    /**
     * @param sender     transmit channel (already configured to suppress self-RX)
     * @param myAddr     this node's application address
     * @param nodeName   short human-readable name advertised to peers
     * @param intervalMs how often to transmit a beacon
     */
    public Beacon(Sender sender, int myAddr, String nodeName, long intervalMs) {
        this.sender = sender;
        this.myAddr = myAddr & 0xFFFF;
        this.nodeName = (nodeName == null) ? "" : nodeName;
        this.intervalMs = Math.max(200, intervalMs);
        this.staleAfterMs = this.intervalMs * 3;
    }

    /** Begin transmitting beacons on a background thread. */
    public synchronized void start() {
        if (task != null && !task.isCancelled()) return;
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "lora-beacon");
            t.setDaemon(true); // never block JVM shutdown
            return t;
        });
        task = scheduler.scheduleAtFixedRate(
                this::transmitOnce, 0, intervalMs, TimeUnit.MILLISECONDS);
    }

    /** Stop transmitting beacons. Does not clear the known-peer table. */
    public synchronized void stop() {
        if (task != null) { task.cancel(false); task = null; }
        if (scheduler != null) { scheduler.shutdownNow(); scheduler = null; }
    }

    public boolean isRunning() {
        return task != null && !task.isCancelled();
    }

    /** Build the BEACON frame this node would currently transmit. */
    public Protocol.Frame buildBeacon() {
        byte[] payload = nodeName.getBytes(StandardCharsets.UTF_8);
        if (payload.length > Protocol.MAX_PAYLOAD) {
            byte[] trimmed = new byte[Protocol.MAX_PAYLOAD];
            System.arraycopy(payload, 0, trimmed, 0, Protocol.MAX_PAYLOAD);
            payload = trimmed;
        }
        return new Protocol.Frame(
                Protocol.Type.BEACON, myAddr, Protocol.BROADCAST_ADDR,
                0, 0, 0, payload);
    }

    private void transmitOnce() {
        try {
            sender.sendFrame(buildBeacon());
        } catch (Exception e) {
            // Never let a transmit error kill the scheduled task.
        }
    }

    /**
     * Record a beacon heard from another node. Called by {@link Radio} when a
     * BEACON frame arrives. Frames originating from this node are ignored by the
     * Radio's self-filter before they get here.
     *
     * @param f     the received BEACON frame
     * @param nowMs current time in millis (injected so this stays testable)
     */
    public void onBeaconReceived(Protocol.Frame f, long nowMs) {
        if (f == null || f.type != Protocol.Type.BEACON) return;
        if (f.src == myAddr) return; // defensive: ignore our own
        String name = new String(f.payload, StandardCharsets.UTF_8);
        peers.put(f.src, new Peer(f.src, name, nowMs));
    }

    /**
     * Snapshot of peers heard within the staleness window, freshest first.
     * Prunes entries that have gone silent.
     */
    public List<Peer> getPeers() {
        return getPeers(System.currentTimeMillis());
    }

    /**
     * Clock-injectable variant of {@link #getPeers()} so the staleness/pruning
     * logic can be tested deterministically.
     */
    List<Peer> getPeers(long nowMs) {
        peers.values().removeIf(p -> nowMs - p.lastSeenMs > staleAfterMs);
        List<Peer> list = new ArrayList<>(peers.values());
        list.sort((a, b) -> Long.compare(b.lastSeenMs, a.lastSeenMs));
        return list;
    }

    /** Forget all known peers. */
    public void clearPeers() { peers.clear(); }

    public int address()    { return myAddr; }
    public String name()    { return nodeName; }
    public long intervalMs(){ return intervalMs; }
}
