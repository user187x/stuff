package xxx;

import javax.sound.sampled.AudioFormat;
import javax.swing.*;
import java.awt.*;

/**
 * Oscilloscope-style display for the live audio stream.
 *
 * Green trace  = the raw waveform (the "carrier" the radio is picking up).
 * Cyan trace   = the smoothed amplitude envelope (top + bottom), so silence vs.
 *                signal is obvious at a glance.
 * Bottom bar   = RMS, Peak and an estimated dominant frequency.
 *
 * It plugs straight into AudioBridge as an AudioSampleListener, so it shows
 * whatever is currently being routed (RX audio while listening, mic while on PTT).
 * When no audio arrives for a moment it falls back to a flat baseline.
 */
public class AudioVisualizer extends JPanel implements AudioBridge.AudioSampleListener {

    private volatile float[] waveform = new float[0];
    private volatile float sampleRate = 48000f;
    private volatile long lastUpdate = 0L;
    private volatile float rms = 0f;
    private volatile float peak = 0f;
    private volatile float freq = 0f;

    private static final long STALE_MS = 250;
    private static final float NOISE_FLOOR = 0.004f;

    private static final Color BG       = new Color(6, 16, 10);
    private static final Color GRID_DIM = new Color(0, 55, 0);
    private static final Color GRID     = new Color(0, 95, 0);
    private static final Color WAVE     = new Color(80, 235, 90);
    private static final Color ENVELOPE = new Color(95, 200, 235);
    private static final Color IDLE     = new Color(35, 85, 35);
    private static final Color TEXT     = new Color(120, 230, 120);

    public AudioVisualizer() {
        setBackground(BG);
        setPreferredSize(new Dimension(700, 240));
        // ~30 FPS refresh, independent of audio callback rate.
        new Timer(33, e -> repaint()).start();
    }

    @Override
    public void onAudioSamples(byte[] data, int length, AudioFormat format) {
        int channels = Math.max(1, format.getChannels());
        boolean bigEndian = format.isBigEndian();
        int step = 2 * channels; // 16-bit frames
        int count = length / step;
        if (count <= 0) return;

        float[] wf = new float[count];
        float r = 0f, pk = 0f;
        int idx = 0;
        for (int i = 0; i + 1 < length; i += step) {
            int sample;
            if (bigEndian) {
                sample = (short) (((data[i] & 0xff) << 8) | (data[i + 1] & 0xff));
            } else {
                sample = (short) (((data[i + 1] & 0xff) << 8) | (data[i] & 0xff));
            }
            float v = sample / 32768f;
            wf[idx++] = v;
            r += v * v;
            float a = Math.abs(v);
            if (a > pk) pk = a;
        }

        // Estimate dominant frequency from zero crossings (cheap, good enough for a meter).
        float f = 0f;
        if (Math.sqrt(r / idx) > NOISE_FLOOR) {
            int crossings = 0;
            for (int i = 1; i < idx; i++) {
                boolean prevNeg = wf[i - 1] < 0;
                boolean curNeg = wf[i] < 0;
                if (prevNeg != curNeg) crossings++;
            }
            float duration = idx / format.getSampleRate();
            if (duration > 0) f = (crossings / 2f) / duration;
        }

        this.waveform = wf;
        this.sampleRate = format.getSampleRate();
        this.rms = (float) Math.sqrt(r / idx);
        this.peak = pk;
        this.freq = f;
        this.lastUpdate = System.currentTimeMillis();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();
        int barH = 24;
        int scopeH = h - barH;
        int mid = scopeH / 2;

        g2.setColor(BG);
        g2.fillRect(0, 0, w, h);

        // --- Grid (10 x 8 divisions, like a scope) ---
        g2.setColor(GRID_DIM);
        for (int i = 1; i < 10; i++) {
            int x = i * w / 10;
            g2.drawLine(x, 0, x, scopeH);
        }
        for (int i = 1; i < 8; i++) {
            int y = i * scopeH / 8;
            g2.drawLine(0, y, w, y);
        }
        g2.setColor(GRID);
        g2.drawLine(0, mid, w, mid);        // center horizontal
        g2.drawLine(w / 2, 0, w / 2, scopeH); // center vertical

        float[] wf = waveform;
        boolean active = (System.currentTimeMillis() - lastUpdate) < STALE_MS && wf.length > 1;
        int amp = scopeH / 2 - 4;

        if (active) {
            // --- Cyan amplitude envelope (top + bottom) ---
            g2.setColor(ENVELOPE);
            g2.setStroke(new BasicStroke(1.2f));
            int win = Math.max(1, wf.length / w);
            int prevTop = mid, prevBot = mid, prevX = 0;
            for (int x = 0; x < w; x++) {
                int center = (int) ((long) x * (wf.length - 1) / Math.max(1, w - 1));
                float localPeak = 0f;
                for (int k = -win; k <= win; k++) {
                    int si = center + k;
                    if (si >= 0 && si < wf.length) {
                        localPeak = Math.max(localPeak, Math.abs(wf[si]));
                    }
                }
                int top = mid - (int) (localPeak * amp);
                int bot = mid + (int) (localPeak * amp);
                if (x > 0) {
                    g2.drawLine(prevX, prevTop, x, top);
                    g2.drawLine(prevX, prevBot, x, bot);
                }
                prevX = x; prevTop = top; prevBot = bot;
            }

            // --- Green waveform trace ---
            g2.setColor(WAVE);
            g2.setStroke(new BasicStroke(1.6f));
            int pX = 0, pY = mid;
            for (int x = 0; x < w; x++) {
                int si = (int) ((long) x * (wf.length - 1) / Math.max(1, w - 1));
                int y = mid - (int) (wf[si] * amp);
                if (x > 0) g2.drawLine(pX, pY, x, y);
                pX = x; pY = y;
            }
        } else {
            g2.setColor(IDLE);
            g2.setStroke(new BasicStroke(1.4f));
            g2.drawLine(0, mid, w, mid);
        }

        // --- Measurements bar ---
        g2.setColor(BG.brighter());
        g2.fillRect(0, scopeH, w, barH);
        g2.setColor(TEXT);
        g2.setFont(new Font("Monospaced", Font.PLAIN, 12));
        String freqStr = (active && freq > 1f) ? String.format("%.0f Hz", freq) : "—";
        String label = active ? "RX Meas:" : "RX Meas:  (idle)";
        String meas = String.format("%-16s RMS: %.3f     Peak: %.3f     Freq: %s",
                label, rms, peak, freqStr);
        g2.drawString(meas, 8, scopeH + 17);
    }
}