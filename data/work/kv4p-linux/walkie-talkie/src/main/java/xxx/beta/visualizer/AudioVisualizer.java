package xxx.beta.visualizer;

import xxx.beta.audio.AudioBridge;

import javax.sound.sampled.AudioFormat;
import javax.swing.*;
import java.awt.*;

/**
 * Dual-mode visualizer for live audio streams.
 * * Mode 0: Oscilloscope (Time Domain)
 * Mode 1: Spectrum Analyzer (Frequency Domain via FFT)
 */
public class AudioVisualizer extends JPanel implements AudioBridge.AudioSampleListener {

    public enum Mode { OSCILLOSCOPE, SPECTRUM }
    private volatile Mode currentMode = Mode.OSCILLOSCOPE;

    private volatile float[] waveform = new float[0];
    private volatile float[] spectrum = new float[0];
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
    private static final Color SPECTRUM_COLOR = new Color(220, 180, 50);
    private static final Color IDLE     = new Color(35, 85, 35);
    private static final Color TEXT     = new Color(120, 230, 120);

    public AudioVisualizer() {
        setBackground(BG);
        setPreferredSize(new Dimension(700, 240));
        new Timer(33, e -> repaint()).start();
    }

    public void toggleMode() {
        currentMode = (currentMode == Mode.OSCILLOSCOPE) ? Mode.SPECTRUM : Mode.OSCILLOSCOPE;
    }

    public Mode getMode() {
        return currentMode;
    }

    @Override
    public void onAudioSamples(byte[] data, int length, AudioFormat format) {
        int channels = Math.max(1, format.getChannels());
        boolean bigEndian = format.isBigEndian();
        int step = 2 * channels;
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

        float f = 0f;
        if (Math.sqrt(r / idx) > NOISE_FLOOR) {
            int crossings = 0;
            for (int i = 1; i < idx; i++) {
                if ((wf[i - 1] < 0) != (wf[i] < 0)) crossings++;
            }
            float duration = idx / format.getSampleRate();
            if (duration > 0) f = (crossings / 2f) / duration;
        }

        this.waveform = wf;
        if (currentMode == Mode.SPECTRUM && wf.length > 0) {
            this.spectrum = computeFFTMagnitudes(wf);
        }

        this.sampleRate = format.getSampleRate();
        this.rms = (float) Math.sqrt(r / idx);
        this.peak = pk;
        this.freq = f;
        this.lastUpdate = System.currentTimeMillis();
    }

    // A lightweight Radix-2 FFT for the spectrum analyzer
    private float[] computeFFTMagnitudes(float[] input) {
        int n = 1;
        while (n <= input.length && n <= 1024) n *= 2;
        n /= 2; // Keep window bounded for UI speed

        float[] real = new float[n];
        float[] imag = new float[n];

        // Apply Hamming window
        for (int i = 0; i < n; i++) {
            double window = 0.54 - 0.46 * Math.cos(2 * Math.PI * i / (n - 1));
            real[i] = (float) (input[i] * window);
        }

        // Bit-reversal permutation
        int j = 0;
        for (int i = 0; i < n - 1; i++) {
            if (i < j) {
                float temp = real[i];
                real[i] = real[j];
                real[j] = temp;
            }
            int k = n / 2;
            while (k <= j) {
                j -= k;
                k /= 2;
            }
            j += k;
        }

        // Cooley-Tukey decimation-in-time
        for (int size = 2; size <= n; size *= 2) {
            int halfsize = size / 2;
            float tablestep = (float) (-2 * Math.PI / size);
            for (int i = 0; i < n; i += size) {
                for (j = i; j < i + halfsize; j++) {
                    int k = j + halfsize;
                    float tcos = (float) Math.cos(tablestep * (j - i));
                    float tsin = (float) Math.sin(tablestep * (j - i));
                    float tempR = tcos * real[k] - tsin * imag[k];
                    float tempI = tsin * real[k] + tcos * imag[k];
                    real[k] = real[j] - tempR;
                    imag[k] = imag[j] - tempI;
                    real[j] += tempR;
                    imag[j] += tempI;
                }
            }
        }

        float[] mags = new float[n / 2];
        for (int i = 0; i < mags.length; i++) {
            mags[i] = (float) Math.sqrt(real[i] * real[i] + imag[i] * imag[i]);
        }
        return mags;
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

        // Grid
        g2.setColor(GRID_DIM);
        for (int i = 1; i < 10; i++) g2.drawLine(i * w / 10, 0, i * w / 10, scopeH);
        for (int i = 1; i < 8; i++) g2.drawLine(0, i * scopeH / 8, w, i * scopeH / 8);
        g2.setColor(GRID);
        g2.drawLine(0, mid, w, mid);
        g2.drawLine(w / 2, 0, w / 2, scopeH);

        boolean active = (System.currentTimeMillis() - lastUpdate) < STALE_MS;

        if (active && waveform.length > 1) {
            if (currentMode == Mode.OSCILLOSCOPE) {
                drawOscilloscope(g2, w, scopeH, mid);
            } else {
                drawSpectrum(g2, w, scopeH);
            }
        } else {
            g2.setColor(IDLE);
            g2.setStroke(new BasicStroke(1.4f));
            g2.drawLine(0, currentMode == Mode.OSCILLOSCOPE ? mid : scopeH, w, currentMode == Mode.OSCILLOSCOPE ? mid : scopeH);
        }

        // Measurements bar
        g2.setColor(BG.brighter());
        g2.fillRect(0, scopeH, w, barH);
        g2.setColor(TEXT);
        g2.setFont(new Font("Monospaced", Font.PLAIN, 12));
        String freqStr = (active && freq > 1f) ? String.format("%.0f Hz", freq) : "—";
        String modeStr = currentMode == Mode.OSCILLOSCOPE ? "[OSC]" : "[FFT]";
        String meas = String.format("%s RX Meas:  RMS: %.3f   Peak: %.3f   Freq: %s", modeStr, rms, peak, freqStr);
        g2.drawString(meas, 8, scopeH + 17);
    }

    private void drawOscilloscope(Graphics2D g2, int w, int scopeH, int mid) {
        float[] wf = waveform;
        int amp = scopeH / 2 - 4;

        g2.setColor(ENVELOPE);
        g2.setStroke(new BasicStroke(1.2f));
        int win = Math.max(1, wf.length / w);
        int prevTop = mid, prevBot = mid, prevX = 0;
        for (int x = 0; x < w; x++) {
            int center = (int) ((long) x * (wf.length - 1) / Math.max(1, w - 1));
            float localPeak = 0f;
            for (int k = -win; k <= win; k++) {
                int si = center + k;
                if (si >= 0 && si < wf.length) localPeak = Math.max(localPeak, Math.abs(wf[si]));
            }
            int top = mid - (int) (localPeak * amp);
            int bot = mid + (int) (localPeak * amp);
            if (x > 0) {
                g2.drawLine(prevX, prevTop, x, top);
                g2.drawLine(prevX, prevBot, x, bot);
            }
            prevX = x; prevTop = top; prevBot = bot;
        }

        g2.setColor(WAVE);
        g2.setStroke(new BasicStroke(1.6f));
        int pX = 0, pY = mid;
        for (int x = 0; x < w; x++) {
            int si = (int) ((long) x * (wf.length - 1) / Math.max(1, w - 1));
            int y = mid - (int) (wf[si] * amp);
            if (x > 0) g2.drawLine(pX, pY, x, y);
            pX = x; pY = y;
        }
    }

    private void drawSpectrum(Graphics2D g2, int w, int scopeH) {
        float[] spec = spectrum;
        if (spec == null || spec.length == 0) return;

        g2.setColor(SPECTRUM_COLOR);
        g2.setStroke(new BasicStroke(2.0f));
        int pX = 0, pY = scopeH;
        float maxMagnitude = 2.0f; // Scale factor

        for (int x = 0; x < w; x++) {
            int bin = (int) ((long) x * (spec.length - 1) / Math.max(1, w - 1));
            float mag = spec[bin] * maxMagnitude;
            int y = scopeH - (int) (Math.min(mag, 1.0f) * scopeH);
            if (x > 0) g2.drawLine(pX, pY, x, y);
            pX = x; pY = y;
        }
    }
}