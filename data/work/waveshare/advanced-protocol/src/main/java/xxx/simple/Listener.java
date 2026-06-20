package xxx.simple;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListener;
import com.fazecast.jSerialComm.SerialPortEvent;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;

/**
 * Owns the receive path for a serial port. Its single responsibility is reading
 * incoming bytes and handing them onward; it does not interpret radio commands
 * or transmit.
 *
 * Three taps are supported, all optional:
 *   - a raw byte consumer  (used by {@link SignalScanner} to detect activity)
 *   - a {@link Protocol.Decoder} + frame consumer (used for messaging / files)
 *
 * It can be muted (bytes are read and discarded) so a transmitter can avoid
 * hearing its own traffic, and stopped/started so the configurator can take
 * exclusive control of the port while in AT-command mode.
 */
public class Listener {

    private final SerialPort serialPort;

    private volatile boolean running = false;
    private volatile boolean muted = false;

    private volatile Consumer<byte[]> rawTap;
    private volatile Protocol.Decoder decoder;
    private volatile Consumer<Protocol.Frame> frameHandler;

    private SerialPortDataListener dataListener;

    public Listener(SerialPort serialPort) {
        this.serialPort = serialPort;
    }

    public void setRawTap(Consumer<byte[]> tap)              { this.rawTap = tap; }
    public Consumer<byte[]> getRawTap()                       { return rawTap; }
    public void setDecoder(Protocol.Decoder d)                { this.decoder = d; }
    public void setFrameHandler(Consumer<Protocol.Frame> h)   { this.frameHandler = h; }

    public boolean isRunning() { return running; }

    /** Drop incoming data (used during our own transmit window). */
    public void mute()   { muted = true; }
    public void unmute() { muted = false; }

    /** Attach to the port and begin delivering received bytes. */
    public synchronized void start() {
        if (running) return;
        dataListener = new SerialPortDataListener() {
            @Override public int getListeningEvents() {
                return SerialPort.LISTENING_EVENT_DATA_AVAILABLE;
            }
            @Override public void serialEvent(SerialPortEvent event) {
                if (event.getEventType() != SerialPort.LISTENING_EVENT_DATA_AVAILABLE) return;
                int avail = serialPort.bytesAvailable();
                if (avail <= 0) return;
                byte[] data = new byte[avail];
                int n = serialPort.readBytes(data, data.length);
                if (n <= 0) return;
                if (muted) return; // drained but ignored

                byte[] slice = data;
                if (n != data.length) {
                    slice = new byte[n];
                    System.arraycopy(data, 0, slice, 0, n);
                }

                Consumer<byte[]> tap = rawTap;
                if (tap != null) tap.accept(slice);

                Protocol.Decoder dec = decoder;
                Consumer<Protocol.Frame> fh = frameHandler;
                if (dec != null && fh != null) {
                    List<Protocol.Frame> frames = dec.feed(slice, slice.length);
                    for (Protocol.Frame f : frames) fh.accept(f);
                }
            }
        };
        serialPort.addDataListener(dataListener);
        running = true;
    }

    /** Detach from the port so another component can use it (e.g. AT mode). */
    public synchronized void stop() {
        if (!running) return;
        serialPort.removeDataListener();
        dataListener = null;
        running = false;
    }

    /**
     * Backward-compatible convenience matching the original API: prints any
     * received bytes to stdout as text. Prefer the instance API above.
     */
    public static Listener listen(SerialPort serialPort) {
        Listener l = new Listener(serialPort);
        l.setRawTap(bytes -> System.out.print(new String(bytes, StandardCharsets.UTF_8)));
        l.start();
        return l;
    }
}
