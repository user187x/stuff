package xxx.simple;

import com.fazecast.jSerialComm.SerialPort;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/**
 * High-level facade that wires the individual single-responsibility classes
 * into one usable radio. It does not duplicate their work; it coordinates them:
 *
 *   DeviceManager  - opened the port (passed in here)
 *   Listener       - the one and only receive path
 *   Sender         - transmit, muting the Listener during TX
 *   RadioConfig    - AT-command configuration (frequency, address, mode, ...)
 *   SignalScanner  - channel sweep for activity
 *   Beacon         - presence broadcast + peer discovery
 *   FileTransfer   - chunked file send, with a Reassembler on the receive side
 *
 * Self-echo suppression (feature #3) is enforced in three independent layers,
 * all coordinated from here:
 *   1. the radio is half-duplex and cannot physically hear its own packet;
 *   2. {@link Sender} mutes the {@link Listener} for the transmit window; and
 *   3. {@link #onFrame} drops any frame whose SRC equals our own address, so
 *      even a relayed or looped-back copy is discarded.
 *
 * Inbound frames are decoded once by the Listener's decoder and dispatched here
 * by type: BEACON -> peer table, DATA -> message callback, FILE_* -> reassembler.
 */
public class Radio {

    private final SerialPort port;
    private final int myAddr;

    private final Listener listener;
    private final Sender sender;
    private final RadioConfig config;
    private final SignalScanner scanner;
    private final Beacon beacon;
    private final FileTransfer fileTransfer;
    private final FileTransfer.Reassembler reassembler;

    /** Diagnostic log sink; defaults to stdout, override with {@link #setLog}. */
    private Consumer<String> log = System.out::println;

    /** Inbound text-message callback: (srcAddr, text). */
    private MessageHandler onMessage = (src, text) ->
            log.accept(String.format("MSG from 0x%04X: %s", src, text));

    public interface MessageHandler { void onMessage(int src, String text); }

    /** Live receive-activity feed for visualizers: (channel, byteCount, timestampMs). */
    public interface ActivitySink { void onActivity(int channel, int byteCount, long timestampMs); }
    /** Per-step scan progress for visualizers. */
    public interface ScanStepSink { void onStep(int channel, double frequencyMHz); }
    /** Completed-inbound-file notifications for the UI. */
    public interface FileReceiveSink { void onFile(int src, String name, int bytes, boolean crcOk); }

    private volatile ActivitySink activitySink;
    private volatile ScanStepSink scanStepSink;
    private volatile FileReceiveSink fileReceiveSink;
    private volatile int currentChannel = -1; // unknown until first tune/scan in this session

    /**
     * @param port     an already-open serial port (from {@link DeviceManager})
     * @param myAddr   this node's application address (0..0xFFFE; 0xFFFF is broadcast)
     * @param band     HF or LF, for channel<->frequency math
     * @param nodeName short name advertised in beacons
     * @param outputDir where received files are written (null = don't persist)
     */
    public Radio(SerialPort port, int myAddr, RadioConfig.Band band,
                 String nodeName, Path outputDir) {
        this.port = port;
        this.myAddr = myAddr & 0xFFFF;

        this.listener = new Listener(port);
        // 50ms guard keeps the receiver muted just past the end of a write.
        this.sender = new Sender(port, listener, 50);
        this.config = new RadioConfig(port, listener, band);
        this.scanner = new SignalScanner(config, listener);
        this.beacon = new Beacon(sender, this.myAddr, nodeName, 5000);
        this.fileTransfer = new FileTransfer(sender, this.myAddr, 200);
        this.reassembler = new FileTransfer.Reassembler(outputDir, this::onFileComplete);

        // One decoder, one frame handler, on the single receive path.
        listener.setDecoder(new Protocol.Decoder());
        listener.setFrameHandler(this::onFrame);
        // Measure raw receive activity for the frequency visualizer. This runs
        // alongside (not instead of) frame decoding. SignalScanner saves and
        // restores this tap around a sweep, so it resumes afterwards.
        listener.setRawTap(bytes -> {
            ActivitySink s = activitySink;
            if (s != null) s.onActivity(currentChannel, bytes.length, System.currentTimeMillis());
        });
        listener.start();
    }

    // --- configuration ------------------------------------------------------

    public void setLog(Consumer<String> log) {
        this.log = (log != null) ? log : (s -> { });
    }

    public void setMessageHandler(MessageHandler handler) {
        this.onMessage = (handler != null) ? handler
                : (src, text) -> { };
    }

    public void setActivitySink(ActivitySink sink)       { this.activitySink = sink; }
    public void setScanStepSink(ScanStepSink sink)       { this.scanStepSink = sink; }
    public void setFileReceiveSink(FileReceiveSink sink) { this.fileReceiveSink = sink; }

    /**
     * Watch the raw AT dialog with the dongle (every command sent and reply
     * received). Optional - tuning works whether or not anything is attached.
     */
    public void setAtTrace(java.util.function.Consumer<String> trace) {
        config.commander().setTrace(trace);
    }

    /** Whether the module acknowledged (with OK) the most recent AT operation. */
    public boolean lastCommandAcknowledged() { return config.lastOk(); }

    /** Currently tuned channel, or -1 if not tuned via this session yet. */
    public int currentChannel() { return currentChannel; }

    /** Currently tuned frequency in MHz, or -1 if unknown. */
    public double currentFrequencyMHz() {
        return currentChannel < 0 ? -1 : config.frequencyForChannel(currentChannel);
    }

    /** Tune both TX and RX to a frequency in MHz (rounded to nearest channel). */
    public int setFrequency(double mhz) {
        int ch = config.tuneFrequency(mhz);
        currentChannel = ch;
        log.accept(String.format("tune %.0f MHz -> channel %d: module %s",
                config.frequencyForChannel(ch), ch,
                config.lastOk() ? "acknowledged (OK)" : "did NOT acknowledge"));
        return ch;
    }

    /** Tune both TX and RX to a channel number. */
    public int setChannel(int channel) {
        int ch = config.tuneChannel(channel);
        currentChannel = ch;
        log.accept(String.format("tune channel %d (%.0f MHz): module %s",
                ch, config.frequencyForChannel(ch),
                config.lastOk() ? "acknowledged (OK)" : "did NOT acknowledge"));
        return ch;
    }

    /**
     * Configure the module so a group of dongles share one "net": same module
     * RF address and channel means they hear each other in stream mode. Peers
     * agree on these out of band (or use the broadcast address 0xFFFF to listen
     * to everything on the channel). Stream mode is set here too, because that is
     * the mode under which TX/RX channel actually governs the operating frequency.
     */
    public void joinNet(int moduleAddr, int channel) {
        config.beginSession();
        try {
            config.setMode(RadioConfig.Mode.STREAM);
            config.setAddress(moduleAddr);
            config.setTransmitChannel(channel);
            config.setReceiveChannel(channel);
        } finally {
            config.endSession();
        }
        currentChannel = Math.max(RadioConfig.MIN_CHANNEL, Math.min(RadioConfig.MAX_CHANNEL, channel));
        log.accept(String.format("joined net: module addr 0x%04X on channel %d (%.0f MHz), stream mode",
                moduleAddr & 0xFFFF, channel, config.frequencyForChannel(channel)));
    }

    public RadioConfig config() { return config; }

    // --- scanning (feature #2) ---------------------------------------------

    public SignalScanner.ScanResult scan(int startChannel, int endChannel, int step, long dwellMs) {
        log.accept(String.format("scanning channels %d..%d step %d, dwell %dms",
                startChannel, endChannel, step, dwellMs));
        SignalScanner.ScanResult r = scanner.scan(startChannel, endChannel, step, dwellMs,
                (ch, mhz) -> {
                    currentChannel = ch;
                    ScanStepSink s = scanStepSink;
                    if (s != null) s.onStep(ch, mhz);
                    log.accept(String.format("  ch %d (%.0f MHz)...", ch, mhz));
                });
        currentChannel = r.channel;
        log.accept("scan result: " + r);
        return r;
    }

    public SignalScanner.ScanResult scanBand(long dwellMs) {
        return scan(RadioConfig.MIN_CHANNEL, RadioConfig.MAX_CHANNEL, 1, dwellMs);
    }

    /**
     * Non-stopping band survey for the live visualizer: sweeps every channel in
     * the range, reporting per-channel byte activity. The tuned channel follows
     * the sweep so a UI marker can track where the receiver currently is. Stop a
     * continuous monitor with {@link #cancelScan()}.
     */
    public List<SignalScanner.ChannelActivity> survey(int startChannel, int endChannel,
                                                      int step, long dwellMs,
                                                      SignalScanner.SurveyCallback cb) {
        return scanner.survey(startChannel, endChannel, step, dwellMs, (ch, mhz, bytes) -> {
            currentChannel = ch;
            if (cb != null) cb.onChannel(ch, mhz, bytes);
        });
    }

    public void cancelScan() { scanner.cancel(); }

    // --- messaging (features #3, #5) ---------------------------------------

    /** Send a text message to a specific peer address. */
    public void sendMessage(int dstAddr, String text) {
        Protocol.Frame f = new Protocol.Frame(
                Protocol.Type.DATA, myAddr, dstAddr, nextMsgId(), 0, 0,
                Protocol.utf8(text));
        sender.sendFrame(f);
        log.accept(String.format("sent to 0x%04X: %s", dstAddr & 0xFFFF, text));
    }

    /** Send a text message to every unit on the channel. */
    public void sendBroadcast(String text) {
        sendMessage(Protocol.BROADCAST_ADDR, text);
    }

    // --- file transfer (feature #5) ----------------------------------------

    public void sendFile(int dstAddr, File file) throws IOException {
        sendFile(dstAddr, file, (name, sent, total) ->
                log.accept(String.format("  %s: %d/%d chunks", name, sent, total)));
    }

    /** Send a file, reporting progress to the supplied callback (used by the GUI). */
    public void sendFile(int dstAddr, File file, FileTransfer.Progress progress) throws IOException {
        log.accept("sending file " + file.getName() + " (" + file.length() + " bytes)");
        fileTransfer.sendFile(file, dstAddr, FileTransfer.DEFAULT_CHUNK, progress);
        log.accept("file send complete: " + file.getName());
    }

    // --- beaconing / discovery (feature #4) --------------------------------

    public void startBeacon() {
        beacon.start();
        log.accept("beacon started (every " + beacon.intervalMs() + "ms as \""
                + beacon.name() + "\")");
    }

    public void stopBeacon() {
        beacon.stop();
        log.accept("beacon stopped");
    }

    public List<Beacon.Peer> peers() { return beacon.getPeers(); }

    // --- inbound dispatch ---------------------------------------------------

    /**
     * Single inbound frame handler for the whole stack. Enforces the SRC==self
     * drop (third self-echo guard) then routes by type.
     */
    private void onFrame(Protocol.Frame f) {
        if (f.src == myAddr) return; // self-echo guard

        // Accept frames addressed to us or broadcast; ignore traffic for others.
        boolean forMe = (f.dst == myAddr) || (f.dst == Protocol.BROADCAST_ADDR);
        if (!forMe) return;

        switch (f.type) {
            case BEACON -> beacon.onBeaconReceived(f, System.currentTimeMillis());
            case DATA   -> onMessage.onMessage(f.src, f.text());
            case FILE_BEGIN, FILE_DATA, FILE_END -> reassembler.onFrame(f);
            case ACK    -> { /* reserved for future reliable delivery */ }
        }
    }

    private void onFileComplete(int src, String name, byte[] data, boolean crcOk) {
        if (crcOk) {
            log.accept(String.format("received file \"%s\" from 0x%04X (%d bytes, CRC OK)",
                    name, src & 0xFFFF, data == null ? 0 : data.length));
        } else {
            log.accept(String.format("file \"%s\" from 0x%04X FAILED (incomplete or CRC mismatch)",
                    name, src & 0xFFFF));
        }
        FileReceiveSink s = fileReceiveSink;
        if (s != null) s.onFile(src & 0xFFFF, name, data == null ? 0 : data.length, crcOk);
    }

    // --- lifecycle ----------------------------------------------------------

    /** Stop background activity and detach from the port (port stays open). */
    public void shutdown() {
        beacon.stop();
        scanner.cancel();
        listener.stop();
    }

    public int address() { return myAddr; }

    // MSGIDs only need to be locally unique enough to disambiguate interleaved
    // transfers/messages; a rolling counter is sufficient.
    private int msgCounter = 0;
    private synchronized int nextMsgId() {
        msgCounter = (msgCounter + 1) & 0xFFFF;
        return msgCounter;
    }
}
