package xxx.beta.audio;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.sound.sampled.*;

/**
 * Routes raw PCM audio from one device (input) to another (output).
 *
 * The "immediate reverb" you were hearing was NOT radio audio. It is what you get
 * when more than one capture->playback loop is alive at the same time, or when the
 * previous route is not torn down before a new one starts. Every press stacked
 * another reader/writer on the same lines, so each chunk of audio was played
 * multiple times slightly out of phase -> echo / reverb.
 *
 * The fixes here:
 *   1. startRouting() ALWAYS calls stopRouting() first, guaranteeing exactly one
 *      active route at any moment.
 *   2. A small, fixed buffer keeps latency low so audio cannot pile up.
 *   3. Devices are chosen by name from the real Mixer (not the system default),
 *      so capture and playback can never collide on the same default device.
 *   4. stopRouting() unblocks, joins and fully closes both lines.
 */
public class AudioBridge {

    // Try these in order; first one both devices support wins.
    private static final float[] CANDIDATE_RATES = {48000f, 44100f, 22050f, 16000f, 8000f};
    private static final int BUFFER_MILLIS = 40; // small => low latency, no echo build-up
    private volatile boolean running = false;
    private Thread routingThread;
    private TargetDataLine inputLine;
    private SourceDataLine outputLine;
    private volatile AudioSampleListener listener;

    public static List<String> getAvailableInputs() {
        return getDevices(true);
    }

    public static List<String> getAvailableOutputs() {
        return getDevices(false);
    }

    // ---------- Device discovery ----------

    private static List<String> getDevices(boolean inputs) {
        Set<String> names = new LinkedHashSet<>();
        for (Mixer.Info info : AudioSystem.getMixerInfo()) {
            Mixer mixer = AudioSystem.getMixer(info);
            Line.Info[] lines = inputs ? mixer.getTargetLineInfo() : mixer.getSourceLineInfo();
            for (Line.Info li : lines) {
                if (li instanceof DataLine.Info) {
                    names.add(info.getName());
                    break;
                }
            }
        }
        if (names.isEmpty()) names.add("Default");
        return new ArrayList<>(names);
    }

    private static Mixer findMixer(String name) {
        if (name == null) return null;
        for (Mixer.Info info : AudioSystem.getMixerInfo()) {
            if (info.getName().equals(name)) {
                return AudioSystem.getMixer(info);
            }
        }
        return null;
    }

    public void setSampleListener(AudioSampleListener l) {
        this.listener = l;
    }

    public synchronized void startRouting(String inputName) throws LineUnavailableException {
        // CRITICAL: never let two routes run at once. This single line is the core
        // of the reverb fix.
        stopRouting();

        final AudioFormat fmt = openLines(inputName);

        running = true;
        routingThread = new Thread(() -> routeLoop(fmt), "AudioRouting");
        routingThread.setDaemon(true);
        routingThread.start();
    }

    // ---------- Routing ----------

    private AudioFormat openLines(String inputName) throws LineUnavailableException {
        Mixer inMixer = findMixer(inputName);

        LineUnavailableException last = null;
        for (float rate : CANDIDATE_RATES) {
            AudioFormat fmt = new AudioFormat(rate, 16, 1, true, false); // mono, 16-bit, signed, little-endian
            DataLine.Info inInfo = new DataLine.Info(TargetDataLine.class, fmt);
            DataLine.Info outInfo = new DataLine.Info(SourceDataLine.class, fmt);

            boolean inOk = (inMixer != null) ? inMixer.isLineSupported(inInfo) : AudioSystem.isLineSupported(inInfo);
            if (!inOk) continue;

            try {
                inputLine = (TargetDataLine) ((inMixer != null) ? inMixer.getLine(inInfo) : AudioSystem.getLine(inInfo));
                int bufBytes = (int) (rate * (BUFFER_MILLIS / 1000.0)) * 2; // 16-bit = 2 bytes/sample
                inputLine.open(fmt, bufBytes);
                outputLine.open(fmt, bufBytes);
                return fmt;
            } catch (LineUnavailableException e) {
                last = e;
                closeLines();
            }
        }
        throw (last != null) ? last
                : new LineUnavailableException("No shared audio format for the selected devices.");
    }

    private void routeLoop(AudioFormat fmt) {
        // Read in small chunks for a responsive, low-latency stream.
        byte[] buf = new byte[Math.max(512, inputLine.getBufferSize() / 4)];
        try {
            inputLine.start();
            outputLine.start();
            while (running && !Thread.currentThread().isInterrupted()) {
                int n = inputLine.read(buf, 0, buf.length);
                if (n > 0) {
                    outputLine.write(buf, 0, n);
                    AudioSampleListener l = listener;
                    if (l != null) {
                        l.onAudioSamples(buf, n, fmt);
                    }
                }
            }
        } catch (Exception e) {
            // Lines were closed during shutdown -> expected, ignore.
        }
    }

    public synchronized void stopRouting() {
        running = false;

        // Unblock a thread that may be parked inside inputLine.read().
        if (inputLine != null) {
            try { inputLine.stop(); inputLine.flush(); } catch (Exception ignored) {}
        }
        if (routingThread != null) {
            routingThread.interrupt();
            try {
                routingThread.join(500);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            routingThread = null;
        }
        closeLines();
    }

    public boolean isRunning() {
        return running;
    }

    private void closeLines() {
        if (inputLine != null) {
            try { inputLine.stop(); inputLine.flush(); inputLine.close(); } catch (Exception ignored) {}
            inputLine = null;
        }
        if (outputLine != null) {
            try { outputLine.drain(); } catch (Exception ignored) {}
            try { outputLine.stop(); outputLine.flush(); outputLine.close(); } catch (Exception ignored) {}
            outputLine = null;
        }
    }

    /** Implemented by the visualizer so it can see whatever audio is flowing. */
    public interface AudioSampleListener {
        void onAudioSamples(byte[] data, int length, AudioFormat format);
    }
}