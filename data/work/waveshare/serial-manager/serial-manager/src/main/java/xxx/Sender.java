package xxx;

import com.fazecast.jSerialComm.SerialPort;

import java.nio.charset.StandardCharsets;

/**
 * Owns the transmit path. Its single responsibility is writing bytes to the
 * serial port (which the module transmits over the air).
 *
 * To satisfy "send a message without listening to itself", the sender mutes the
 * (optional) {@link Listener} for the duration of the transmit window plus a
 * short guard. This is belt-and-braces: the radio is half-duplex so it can't
 * physically receive its own packet, but muting also discards any module echo
 * or relayed copy, and the application protocol additionally drops frames whose
 * source address equals our own (see {@link Radio}).
 */
public class Sender {

    private final SerialPort serialPort;
    private final Listener listener;     // may be null (no coordination)
    private final long guardMs;

    /** Original constructor: no self-RX coordination. */
    public Sender(SerialPort serialPort) {
        this(serialPort, null, 0);
    }

    /**
     * @param listener listener to mute during transmit (null to skip)
     * @param guardMs  extra time to stay muted after the write completes
     */
    public Sender(SerialPort serialPort, Listener listener, long guardMs) {
        this.serialPort = serialPort;
        this.listener = listener;
        this.guardMs = guardMs;
    }

    /** Send text in transparent mode (kept for backward compatibility). */
    public void send(String message) {
        sendRaw(message.getBytes(StandardCharsets.UTF_8));
    }

    /** Send an encoded protocol frame. */
    public void sendFrame(Protocol.Frame frame) {
        sendRaw(frame.encode());
    }

    /** Write raw bytes, muting the listener across the transmit window. */
    public synchronized void sendRaw(byte[] data) {
        if (serialPort == null || !serialPort.isOpen() || data == null || data.length == 0) return;

        boolean coordinate = (listener != null);
        if (coordinate) listener.mute();
        try {
            int written = 0;
            while (written < data.length) {
                byte[] slice;
                if (written == 0) {
                    slice = data;
                } else {
                    slice = new byte[data.length - written];
                    System.arraycopy(data, written, slice, 0, slice.length);
                }
                int n = serialPort.writeBytes(slice, slice.length);
                if (n <= 0) break;          // port error; avoid spinning
                written += n;
            }
            if (guardMs > 0) sleep(guardMs);
        } finally {
            if (coordinate) listener.unmute();
        }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
