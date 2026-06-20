package xxx.simple;

import com.fazecast.jSerialComm.SerialPort;

import java.util.List;

/**
 * Radio configuration policy: <em>what</em> to set on the module (frequency,
 * channel, mode, address, power, ...). The <em>how</em> - entering command mode,
 * the CR+LF framing, reading replies, exiting and letting the module apply - is
 * delegated entirely to {@link AtCommander}, the radio-communication layer.
 *
 * The GUI never issues AT commands itself: it asks {@link Radio} for a frequency,
 * Radio asks this class, and this class drives {@link AtCommander}. So tuning
 * "just works" from a control without the user touching the modem protocol.
 *
 * Channel <-> frequency (from the Waveshare USB-TO-LoRa wiki):
 *   - channels 0..80 map linearly across the band, 1 MHz per channel
 *   - HF band: freqMHz = 850 + channel   (channel 18 = 868 MHz default,
 *                                          channel 65 = 915 MHz)
 *   - LF band: freqMHz = 410 + channel   (channel 23 = 433 MHz default)
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

    private final AtCommander at;
    private final Band band;

    public RadioConfig(SerialPort port, Listener listener, Band band) {
        this.band = band;
        this.at = new AtCommander(port, listener);
    }

    /** The underlying radio-communication layer (for timings and the trace hook). */
    public AtCommander commander() { return at; }

    public void setExitSettleMs(long ms)      { at.setExitSettleMs(ms); }
    public void setResponseTimeoutMs(long ms) { at.setResponseTimeoutMs(ms); }

    public int channelForFrequency(double mhz) {
        return (int) Math.round(mhz - band.baseMHz);
    }

    public double frequencyForChannel(int channel) {
        return band.baseMHz + channel;
    }

    // --- command-mode session (delegated) ----------------------------------

    /** Enter AT command mode, taking the port from the listener. */
    public void beginSession() { at.enter(); }

    /** Apply and leave command mode, returning the port to the listener. */
    public void endSession() { at.exit(); }

    /** Send one AT command (CRLF added) and return the textual response. */
    public String sendAt(String command) { return at.send(command).response; }

    /** Whether the most recent operation was acknowledged with OK by the module. */
    public boolean lastOk() { return at.lastOk(); }

    // --- high-level configuration -------------------------------------------

    /**
     * Set both TX and RX to a channel within one command-mode session, so a
     * single AT+EXIT applies both. Returns the (clamped) channel used.
     */
    public int tuneChannel(int channel) {
        int ch = clampChannel(channel);
        at.run("AT+TXCH=" + ch, "AT+RXCH=" + ch);
        return ch;
    }

    /** Tune by frequency in MHz (rounded to the nearest channel). Returns channel used. */
    public int tuneFrequency(double mhz) {
        return tuneChannel(channelForFrequency(mhz));
    }

    public void setTransmitChannel(int channel) { at.send("AT+TXCH=" + clampChannel(channel)); }
    public void setReceiveChannel(int channel)  { at.send("AT+RXCH=" + clampChannel(channel)); }
    public void setAddress(int addr)            { at.send("AT+ADDR=" + (addr & 0xFFFF)); }
    public void setNetworkId(int id)            { at.send("AT+NETID=" + (id & 0xFFFF)); }
    public void setMode(Mode mode)              { at.send("AT+MODE=" + mode.code); }
    public void setRssiOutput(boolean on)       { at.send("AT+RSSI=" + (on ? 1 : 0)); }
    public void setLbt(boolean on)              { at.send("AT+LBT=" + (on ? 1 : 0)); }
    public void setPower(int dbm)               { at.send("AT+PWR=" + clamp(dbm, 10, 22)); }
    public void setSpreadingFactor(int sf)      { at.send("AT+SF=" + clamp(sf, 7, 12)); }
    public void setBandwidth(int code)          { at.send("AT+BW=" + clamp(code, 0, 2)); }
    public void setCodingRate(int code)         { at.send("AT+CR=" + clamp(code, 1, 4)); }

    /**
     * Set the AES key value (0 = disabled). NOTE: a standalone AT+KEY command is
     * not listed in the public AT table for every firmware revision; if your unit
     * rejects it, run {@link #help()} to find the exact command (the key is also
     * settable through the AT+AllP multi-parameter command).
     */
    public void setKey(int key)                 { at.send("AT+KEY=" + (key & 0xFFFF)); }

    /** Restore factory defaults (also doable by holding the dongle's KEY button). */
    public void restoreFactory()                { at.send("AT+RESTORE=1"); }

    public String version() { return at.send("AT+VER").response; }
    public String help()    { return at.send("AT+HELP").response; }

    public Band band() { return band; }
    public boolean inSession() { return at.inSession(); }

    // --- helpers ------------------------------------------------------------

    private static int clampChannel(int c) { return clamp(c, MIN_CHANNEL, MAX_CHANNEL); }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
