package xxx.simple;

import com.fazecast.jSerialComm.SerialPort;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Radio-communication layer: the single place that actually talks to the USB
 * dongle over its serial AT-command interface. Higher layers ({@link RadioConfig}
 * and, through it, the GUI) ask for a new frequency, mode, address, etc., and
 * this class performs the byte-level handshake on their behalf, so nothing above
 * has to know the modem protocol.
 *
 * The dongle's data path is transparent: bytes written to the port are
 * transmitted over the air. To configure it you must first switch it into
 * command mode. The handshake performed here is exactly what the Waveshare
 * USB-TO-LoRa wiki documents:
 *
 *   1. {@link #enter()} - pause RX, make sure the line is briefly idle, then send
 *      "+++". The module leaves transparent mode and starts accepting commands.
 *   2. {@link #send(String)} - write "AT+...\r\n", read the textual reply, and
 *      classify it OK / ERROR.
 *   3. {@link #exit()} - send "AT+EXIT\r\n". The firmware *applies* the new
 *      settings on exit (and may briefly reboot), after which RX resumes.
 *
 * Every command carries the CR+LF terminator the firmware requires. An optional
 * {@link #setTrace trace sink} lets a UI watch the exact dialog if it wants to;
 * by default the whole exchange is invisible to the user.
 */
public class AtCommander {

    /** Outcome of a single AT command. */
    public static final class Result {
        public final String command;
        public final String response;
        public final boolean ok;

        Result(String command, String response, boolean ok) {
            this.command = command;
            this.response = response;
            this.ok = ok;
        }

        @Override public String toString() {
            return command + " -> " + (ok ? "OK" : "FAIL")
                    + (response.isEmpty() ? "" : " [" + response + "]");
        }
    }

    private final SerialPort port;
    private final Listener listener; // paused while in command mode (may be null)

    /** Escape that enters command mode. Vendor docs use "+++"; CR+LF is appended. */
    private String escape = "+++";
    /** Idle gap enforced before the escape so it is not mistaken for payload. */
    private long guardMs = 150;
    /** Wait after AT+EXIT for the module to apply settings / reboot. */
    private long exitSettleMs = 500;
    /** Per-command read timeout. */
    private long responseTimeoutMs = 800;

    private boolean inSession = false;
    private boolean listenerWasRunning = false;
    private boolean lastOk = true;
    private Consumer<String> trace = s -> { };

    public AtCommander(SerialPort port, Listener listener) {
        this.port = port;
        this.listener = listener;
    }

    public void setEscape(String escape)      { this.escape = (escape == null) ? "+++" : escape; }
    public void setGuardMs(long ms)           { this.guardMs = ms; }
    public void setExitSettleMs(long ms)      { this.exitSettleMs = ms; }
    public void setResponseTimeoutMs(long ms) { this.responseTimeoutMs = ms; }

    /** Attach a sink that receives every line of the AT dialog (sent and received). */
    public void setTrace(Consumer<String> t)  { this.trace = (t != null) ? t : s -> { }; }

    public synchronized boolean inSession() { return inSession; }

    /** Whether every command in the most recent send/run was acknowledged with OK. */
    public synchronized boolean lastOk() { return lastOk; }

    /** Enter command mode, taking the port from the listener. Idempotent. */
    public synchronized void enter() {
        if (inSession) return;
        listenerWasRunning = (listener != null && listener.isRunning());
        if (listenerWasRunning) listener.stop();

        port.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, (int) responseTimeoutMs, 0);
        drain();             // discard pending RX so it is not read back as a reply
        sleep(guardMs);      // brief silence so the module recognises the escape
        trace.accept(">> " + escape);
        writeRaw(escape + "\r\n");
        String greeting = read(responseTimeoutMs);
        trace.accept("<< " + (greeting.isEmpty()
                ? "(no reply to escape; may already be in command mode)" : greeting));
        inSession = true;
    }

    /** Apply settings and leave command mode, returning the port to the listener. */
    public synchronized void exit() {
        if (!inSession) return;
        sendInternal("AT+EXIT");
        sleep(exitSettleMs);  // allow the module to apply / reboot
        port.setComPortTimeouts(SerialPort.TIMEOUT_NONBLOCKING, 0, 0);
        inSession = false;
        if (listenerWasRunning && listener != null) listener.start();
    }

    /**
     * Send one AT command. If not already in a session this opens one just for
     * this command and closes it afterwards, so the setting is applied.
     */
    public synchronized Result send(String command) {
        boolean standalone = !inSession;
        if (standalone) enter();
        try {
            Result r = sendInternal(command);
            lastOk = r.ok;
            return r;
        } finally {
            if (standalone) exit();
        }
    }

    /** Send several commands inside a single command-mode session (one apply). */
    public synchronized List<Result> run(String... commands) {
        boolean standalone = !inSession;
        if (standalone) enter();
        List<Result> out = new ArrayList<>();
        boolean all = true;
        try {
            for (String c : commands) {
                Result r = sendInternal(c);
                out.add(r);
                all &= r.ok;
            }
        } finally {
            lastOk = all;
            if (standalone) exit();
        }
        return out;
    }

    // --- internals ----------------------------------------------------------

    private Result sendInternal(String command) {
        trace.accept(">> " + command);
        writeRaw(command + "\r\n");
        String resp = read(responseTimeoutMs);
        boolean ok = resp.contains("OK") && !resp.contains("ERROR");
        trace.accept("<< " + (resp.isEmpty() ? "(no reply)" : resp));
        return new Result(command, resp, ok);
    }

    private void writeRaw(String s) {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        port.writeBytes(b, b.length);
    }

    private void drain() {
        try {
            int avail = port.bytesAvailable();
            if (avail > 0) {
                byte[] junk = new byte[Math.min(avail, 1024)];
                port.readBytes(junk, junk.length);
            }
        } catch (Exception ignored) { }
    }

    private String read(long timeoutMs) {
        StringBuilder sb = new StringBuilder();
        long deadline = System.currentTimeMillis() + timeoutMs;
        byte[] buf = new byte[256];
        while (System.currentTimeMillis() < deadline) {
            int avail = port.bytesAvailable();
            if (avail > 0) {
                int n = port.readBytes(buf, Math.min(avail, buf.length));
                if (n > 0) {
                    sb.append(new String(buf, 0, n, StandardCharsets.UTF_8));
                    String s = sb.toString();
                    if (s.contains("OK") || s.contains("ERROR")) break;
                    deadline = System.currentTimeMillis() + 150; // tail wait for stragglers
                }
            } else {
                sleep(10);
            }
        }
        return sb.toString().trim();
    }

    private static void sleep(long ms) {
        if (ms <= 0) return;
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
