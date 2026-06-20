package xxx.simple;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Sweeps the receive channel looking for activity. It uses {@link RadioConfig}
 * to retune and the {@link Listener}'s raw tap to notice incoming bytes,
 * stopping on the first channel where traffic appears.
 *
 * Stepping: channels are 1 MHz apart, which is wider than the default 125 kHz
 * LoRa bandwidth, so a step of 1 is appropriate and will not skip a signal that
 * shares the configured spreading factor / bandwidth. A coarser step scans
 * faster at the cost of possibly stepping over a channel.
 *
 * Speed note: this module applies channel changes on exit from command mode and
 * may briefly reboot, so each step costs the command-mode round trip plus the
 * dwell. This is a deliberate survey, not fast frequency hopping; choose a
 * sub-range and dwell that suit your need.
 */
public class SignalScanner {

    private final RadioConfig config;
    private final Listener listener;
    private volatile boolean cancelled = false;

    public SignalScanner(RadioConfig config, Listener listener) {
        this.config = config;
        this.listener = listener;
    }

    public static final class ScanResult {
        public final boolean found;
        public final int channel;
        public final double frequencyMHz;
        public final int bytesObserved;

        ScanResult(boolean found, int channel, double frequencyMHz, int bytesObserved) {
            this.found = found;
            this.channel = channel;
            this.frequencyMHz = frequencyMHz;
            this.bytesObserved = bytesObserved;
        }

        @Override public String toString() {
            return found
                    ? String.format("signal on channel %d (%.0f MHz), %d bytes", channel, frequencyMHz, bytesObserved)
                    : "no signal found";
        }
    }

    /** Per-channel activity sample produced by {@link #survey}. */
    public static final class ChannelActivity {
        public final int channel;
        public final double frequencyMHz;
        public final int bytes;
        ChannelActivity(int channel, double frequencyMHz, int bytes) {
            this.channel = channel;
            this.frequencyMHz = frequencyMHz;
            this.bytes = bytes;
        }
    }

    /** Callback invoked once per channel during a {@link #survey}. */
    public interface SurveyCallback { void onChannel(int channel, double mhz, int bytes); }

    public interface Listener2 { void onChannel(int channel, double mhz); }

    public void cancel() { cancelled = true; }

    /**
     * Sweep [startChannel, endChannel] inclusive in steps of {@code step},
     * dwelling {@code dwellMs} on each. Stops and returns on the first channel
     * with received bytes. On no detection, the receiver is restored to
     * {@code startChannel}; on detection it is left tuned to the found channel.
     *
     * @param onStep optional progress callback per channel (may be null)
     */
    public ScanResult scan(int startChannel, int endChannel, int step, long dwellMs, Listener2 onStep) {
        cancelled = false;
        if (step <= 0) step = 1;
        int lo = Math.max(RadioConfig.MIN_CHANNEL, Math.min(startChannel, endChannel));
        int hi = Math.min(RadioConfig.MAX_CHANNEL, Math.max(startChannel, endChannel));

        // Install a counting tap, remembering the existing one to restore later.
        AtomicInteger counter = new AtomicInteger(0);
        Consumer<byte[]> previousTap = listener.getRawTap();
        listener.setRawTap(bytes -> counter.addAndGet(bytes.length));

        try {
            for (int ch = lo; ch <= hi && !cancelled; ch += step) {
                counter.set(0);
                config.tuneChannel(ch);                 // retune (enter/set/exit)
                double mhz = config.frequencyForChannel(ch);
                if (onStep != null) onStep.onChannel(ch, mhz);

                long deadline = System.currentTimeMillis() + dwellMs;
                while (System.currentTimeMillis() < deadline && !cancelled) {
                    if (counter.get() > 0) {
                        return new ScanResult(true, ch, mhz, counter.get()); // leave tuned here
                    }
                    sleep(20);
                }
            }
            // nothing found: restore starting channel
            config.tuneChannel(lo);
            return new ScanResult(false, lo, config.frequencyForChannel(lo), 0);
        } finally {
            listener.setRawTap(previousTap);            // restore normal RX behaviour
        }
    }

    /** Convenience: scan the whole band with a 1-channel step. */
    public ScanResult scanBand(long dwellMs, Listener2 onStep) {
        return scan(RadioConfig.MIN_CHANNEL, RadioConfig.MAX_CHANNEL, 1, dwellMs, onStep);
    }

    /**
     * Survey a channel range without stopping: dwell on each channel, record how
     * many bytes arrived, and report it. Unlike {@link #scan}, this visits every
     * channel in the range so a UI can paint a spectrum / waterfall of band
     * activity. Honours {@link #cancel()} so a continuous monitor can be stopped
     * promptly. The receiver is left tuned to the last channel swept.
     *
     * @param cb per-channel callback (may be null)
     * @return the activity samples, in sweep order
     */
    public java.util.List<ChannelActivity> survey(int startChannel, int endChannel,
                                                  int step, long dwellMs, SurveyCallback cb) {
        cancelled = false;
        if (step <= 0) step = 1;
        int lo = Math.max(RadioConfig.MIN_CHANNEL, Math.min(startChannel, endChannel));
        int hi = Math.min(RadioConfig.MAX_CHANNEL, Math.max(startChannel, endChannel));

        AtomicInteger counter = new AtomicInteger(0);
        Consumer<byte[]> previousTap = listener.getRawTap();
        listener.setRawTap(bytes -> counter.addAndGet(bytes.length));

        java.util.List<ChannelActivity> out = new java.util.ArrayList<>();
        try {
            for (int ch = lo; ch <= hi && !cancelled; ch += step) {
                counter.set(0);
                config.tuneChannel(ch);
                double mhz = config.frequencyForChannel(ch);

                long deadline = System.currentTimeMillis() + dwellMs;
                while (System.currentTimeMillis() < deadline && !cancelled) sleep(20);

                int bytes = counter.get();
                ChannelActivity ca = new ChannelActivity(ch, mhz, bytes);
                out.add(ca);
                if (cb != null) cb.onChannel(ch, mhz, bytes);
            }
        } finally {
            listener.setRawTap(previousTap);
        }
        return out;
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
