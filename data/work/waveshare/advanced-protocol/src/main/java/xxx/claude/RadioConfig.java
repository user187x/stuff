package xxx.claudewaveshare;

import com.fazecast.jSerialComm.SerialPort;

import java.nio.charset.StandardCharsets;

/**
 * Owns radio configuration via the module's AT command set. The data path is
 * transparent, so configuration is a distinct mode: you send {@code +++} to
 * enter command mode, issue {@code AT+...} commands, then {@code AT+EXIT} to
 * apply and leave (the firmware applies parameters on exit, and may briefly
 * reboot).
 *
 * Channel <-> frequency (from the Waveshare USB-TO-LoRa wiki):
 *   - channels 0..80 map linearly across the band, 1 MHz per channel
 *   - HF band: freqMHz = 850 + channel   (channel 18 = 868 MHz default,
 *                                          channel 65 = 915 MHz)
 *   - LF band: freqMHz = 410 + channel   (channel 23 = 433 MHz default)
 *
 * This class takes exclusive control of the port while in command mode by
 * stopping the {@link Listener} (so AT responses are not parsed as data and the
 * two do not fight over the same bytes), then restarts it on exit.
 */
public class RadioConfig {

    public enum Band {
        HF(850), LF(410);
        public final int baseMHz;
        Band(int baseMHz) { this.baseMHz = baseMHz; }
    }

    public enum Mode {
        STREAM(1), PACKET(2), RELAY(3);
        public final int code;
        Mode(int code) { this.code = code; }
    }

    public static final int MIN_CHANNEL = 0;
    public static final int MAX_CHANNEL = 80;

    private final SerialPort port;
    private final Listener listener;
    private final Band band;

    /** Time to wait after AT+EXIT for the module to apply settings / reboot. */
    private long exitSettleMs = 500;
    /** Per-command response timeout. */
    private long responseTimeoutMs = 800;

    private boolean inSession = false;
    private boolean listenerWasRunning = false;

    public RadioConfig(SerialPort port, Listener listener, Band band) {
        this.port = port;
        this.listener = listener;
        this.band = band;
    }

    public void setExitSettleMs(long ms)      { this.exitSettleMs = ms; }
    public void setResponseTimeoutMs(long ms) { this.responseTimeoutMs = ms; }

    public int channelForFrequency(double mhz) {
        return (int) Math.round(mhz - band.baseMHz);
    }

    public double frequencyForChannel(int channel) {
        return band.baseMHz + channel;
    }

    // --- command-mode session ----------------------------------------------

    /** Enter AT command mode, taking the port from the listener. */
    public synchronized void beginSession() {
        if (inSession) return;
        listenerWasRunning = (listener != null && listener.isRunning());
        if (listenerWasRunning) listener.stop();

        port.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, (int) responseTimeoutMs, 0);
        writeLine("+++");
        String resp = readResponse(responseTimeoutMs);
        // Some firmwares answer "+OK"/"OK", others "Entry AT". Proceed regardless,
        // but surface a hint if nothing came back.
        if (resp.isBlank()) {
            System.out.println("[RadioConfig] warning: no response to '+++' (already in command mode?)");
        }
        inSession = true;
    }

    /** Apply and leave command mode, returning the port to the listener. */
    public synchronized void endSession() {
        if (!inSession) return;
        writeLine("AT+EXIT");
        readResponse(responseTimeoutMs);
        sleep(exitSettleMs);                 // allow apply / reboot
        port.setComPortTimeouts(SerialPort.TIMEOUT_NONBLOCKING, 0, 0);
        inSession = false;
        if (listenerWasRunning && listener != null) listener.start();
    }

    /** Send one AT command (CRLF added) and return the textual response. */
    public synchronized String sendAt(String command) {
        boolean standalone = !inSession;
        if (standalone) beginSession();
        try {
            writeLine(command);
            return readResponse(responseTimeoutMs);
        } finally {
            if (standalone) endSession();
        }
    }

    // --- high-level configuration -------------------------------------------

    /** Set both TX and RX to a channel within one session. Returns the channel. */
    public synchronized int tuneChannel(int channel) {
        int ch = clampChannel(channel);
        boolean standalone = !inSession;
        if (standalone) beginSession();
        try {
            writeLine("AT+TXCH=" + ch); readResponse(responseTimeoutMs);
            writeLine("AT+RXCH=" + ch); readResponse(responseTimeoutMs);
        } finally {
            if (standalone) endSession();
        }
        return ch;
    }

    /** Tune by frequency in MHz (rounded to the nearest channel). Returns channel used. */
    public synchronized int tuneFrequency(double mhz) {
        return tuneChannel(channelForFrequency(mhz));
    }

    public void setTransmitChannel(int channel) { sendAt("AT+TXCH=" + clampChannel(channel)); }
    public void setReceiveChannel(int channel)  { sendAt("AT+RXCH=" + clampChannel(channel)); }
    public void setAddress(int addr)            { sendAt("AT+ADDR=" + (addr & 0xFFFF)); }
    public void setNetworkId(int id)            { sendAt("AT+NETID=" + (id & 0xFFFF)); }
    public void setMode(Mode mode)              { sendAt("AT+MODE=" + mode.code); }
    public void setRssiOutput(boolean on)       { sendAt("AT+RSSI=" + (on ? 1 : 0)); }
    public void setLbt(boolean on)              { sendAt("AT+LBT=" + (on ? 1 : 0)); }
    public void setPower(int dbm)               { sendAt("AT+PWR=" + clamp(dbm, 10, 22)); }
    public void setSpreadingFactor(int sf)      { sendAt("AT+SF=" + clamp(sf, 7, 12)); }
    public void setBandwidth(int code)          { sendAt("AT+BW=" + clamp(code, 0, 2)); }
    public void setCodingRate(int code)         { sendAt("AT+CR=" + clamp(code, 1, 4)); }

    /**
     * Set the AES key value (0 = disabled). NOTE: a standalone AT+KEY command is
     * not listed in the public AT table for every firmware revision; if your
     * unit rejects it, run {@link #help()} to find the exact command (the key is
     * also settable through the AT+AllP multi-parameter command).
     */
    public void setKey(int key)                 { sendAt("AT+KEY=" + (key & 0xFFFF)); }

    public String version() { return sendAt("AT+VER"); }
    public String help()    { return sendAt("AT+HELP"); }

    public Band band() { return band; }
    public boolean inSession() { return inSession; }

    // --- low-level I/O -------------------------------------------------------

    private void writeLine(String s) {
        byte[] b = (s + "\r\n").getBytes(StandardCharsets.UTF_8);
        port.writeBytes(b, b.length);
    }

    private String readResponse(long timeoutMs) {
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
                    deadline = System.currentTimeMillis() + 150; // small tail wait
                }
            } else {
                sleep(10);
            }
        }
        return sb.toString().trim();
    }

    private static int clampChannel(int c) { return clamp(c, MIN_CHANNEL, MAX_CHANNEL); }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static void sleep(long ms) {
        if (ms <= 0) return;
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
