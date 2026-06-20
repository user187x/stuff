package xxx.simple;

import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;

/**
 * Live visualizer for the radio's band. It has a single responsibility:
 * rendering activity, not talking to hardware. {@link RadioDashboard} feeds it
 * data on the Swing event thread.
 *
 * Two honest views of what this transparent-mode module can actually observe:
 *   - a <b>spectrum</b> bar per channel, where height = bytes received while the
 *     receiver dwelled on that channel during a band survey; and
 *   - a scrolling <b>waterfall</b> built from successive survey passes, newest
 *     row on top.
 * A vertical marker shows the currently tuned channel (it sweeps across during a
 * survey), and a small meter shows live RX activity on the tuned channel.
 *
 * This is not an SDR spectrogram (the dongle has no I/Q path); it is a survey of
 * per-channel traffic, which is the meaningful signal this hardware exposes.
 *
 * All mutators must be called on the EDT.
 */
public class SpectrumPanel extends JPanel {

    private static final int WATERFALL_ROWS = 160;

    private int minCh = RadioConfig.MIN_CHANNEL;
    private int maxCh = RadioConfig.MAX_CHANNEL;
    private double baseMHz = RadioConfig.Band.HF.baseMHz;

    private int nCh = maxCh - minCh + 1;
    private int[] activity = new int[nCh];     // raw bytes per channel, latest pass
    private double maxObserved = 1;            // auto-scaling reference (decays)

    private BufferedImage waterfall;           // nCh wide x WATERFALL_ROWS tall
    private int[] waterfallPx;

    private int currentChannel = -1;           // tuned-channel marker
    private double liveLevel = 0;              // 0..1 live RX meter (decays)

    public SpectrumPanel() {
        setBackground(new Color(0x0B, 0x0F, 0x14));
        setPreferredSize(new java.awt.Dimension(760, 340));
        rebuildBuffers();
        // ~30 fps: decay the live meter and repaint so activity pulses smoothly.
        Timer ticker = new Timer(33, e -> {
            liveLevel *= 0.85;
            if (liveLevel < 0.003) liveLevel = 0;
            maxObserved = Math.max(1, maxObserved * 0.999);
            repaint();
        });
        ticker.setRepeats(true);
        ticker.start();
    }

    /** Configure the band so frequency labels and channel count are correct. */
    public void setBand(double baseMHz, int minChannel, int maxChannel) {
        this.baseMHz = baseMHz;
        this.minCh = minChannel;
        this.maxCh = maxChannel;
        this.nCh = maxCh - minCh + 1;
        rebuildBuffers();
        repaint();
    }

    private void rebuildBuffers() {
        activity = new int[Math.max(1, nCh)];
        waterfall = new BufferedImage(Math.max(1, nCh), WATERFALL_ROWS, BufferedImage.TYPE_INT_RGB);
        waterfallPx = ((DataBufferInt) waterfall.getRaster().getDataBuffer()).getData();
        int empty = new Color(0x05, 0x08, 0x0C).getRGB();
        java.util.Arrays.fill(waterfallPx, empty);
        maxObserved = 1;
        currentChannel = -1;
    }

    /** Set one channel's activity (bytes observed) for the current spectrum. */
    public void setChannelActivity(int channel, int bytes) {
        int i = channel - minCh;
        if (i < 0 || i >= activity.length) return;
        activity[i] = bytes;
        if (bytes > maxObserved) maxObserved = bytes;
    }

    /** Snapshot the current spectrum into a new top waterfall row. */
    public void commitWaterfallRow() {
        if (waterfallPx == null || nCh <= 0) return;
        // scroll everything down by one row
        System.arraycopy(waterfallPx, 0, waterfallPx, nCh, nCh * (WATERFALL_ROWS - 1));
        // paint the new row 0 from current activity
        for (int i = 0; i < nCh; i++) {
            double norm = norm(activity[i]);
            waterfallPx[i] = waterfallColor(norm).getRGB();
        }
        repaint();
    }

    /** Mark the currently tuned channel (drawn as a vertical cursor). */
    public void setCurrentChannel(int channel) {
        this.currentChannel = channel;
    }

    /** Feed live RX bytes seen on the tuned channel; spikes the RX meter. */
    public void pushLiveActivity(int bytes) {
        liveLevel = Math.min(1.0, liveLevel + bytes / 160.0);
        if (bytes > maxObserved) maxObserved = bytes;
    }

    /** Reset all visuals. */
    public void clear() {
        rebuildBuffers();
        liveLevel = 0;
        repaint();
    }

    private double norm(int v) {
        if (v <= 0) return 0;
        double n = Math.log1p(v) / Math.log1p(Math.max(1, maxObserved));
        return n < 0 ? 0 : (n > 1 ? 1 : n);
    }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        int w = getWidth(), h = getHeight();
        int headerH = 20;
        int axisH = 16;
        int gap = 4;
        int top = headerH + 2;
        int usable = h - top - axisH;
        int specH = (int) (usable * 0.42);
        int wfY = top + specH + gap;
        int wfH = h - axisH - wfY;
        float barW = w / (float) Math.max(1, nCh);

        // --- header: live RX meter + tuned frequency label ---
        drawMeter(g, 6, 4, 150, headerH - 8);
        g.setFont(getFont().deriveFont(Font.BOLD, 11f));
        g.setColor(new Color(0xB8, 0xC4, 0xD0));
        String tuned = (currentChannel >= 0)
                ? String.format("Tuned: %.0f MHz  (ch %d)", baseMHz + currentChannel, currentChannel)
                : "Tuned: --";
        int tw = g.getFontMetrics().stringWidth(tuned);
        g.drawString(tuned, w - tw - 8, 15);

        // --- spectrum bars ---
        int specBase = top + specH;
        for (int i = 0; i < nCh; i++) {
            double n = norm(activity[i]);
            int bh = (int) Math.round(n * specH);
            int x = Math.round(i * barW);
            int bw = Math.max(1, Math.round(barW) - 1);
            if (bh > 0) {
                g.setColor(intensityColor(n));
                g.fillRect(x, specBase - bh, bw, bh);
            }
        }
        // faint spectrum baseline
        g.setColor(new Color(0x22, 0x2A, 0x33));
        g.drawLine(0, specBase, w, specBase);

        // --- waterfall ---
        if (waterfall != null) {
            g.drawImage(waterfall, 0, wfY, w, wfH, null);
        }

        // --- tuned-channel marker spanning spectrum + waterfall ---
        if (currentChannel >= minCh && currentChannel <= maxCh) {
            int mx = Math.round((currentChannel - minCh + 0.5f) * barW);
            g.setColor(new Color(0x3D, 0xE0, 0xE0));
            g.drawLine(mx, top, mx, wfY + wfH);
            // little triangle pointer at the top of the spectrum
            int[] xs = {mx - 4, mx + 4, mx};
            int[] ys = {top, top, top + 6};
            g.fillPolygon(xs, ys, 3);
        }

        // --- frequency axis labels ---
        g.setColor(new Color(0x6B, 0x76, 0x82));
        g.setFont(getFont().deriveFont(10f));
        int labelStep = Math.max(1, nCh / 8);
        for (int ch = minCh; ch <= maxCh; ch += labelStep) {
            int x = Math.round((ch - minCh + 0.5f) * barW);
            String lbl = String.format("%.0f", baseMHz + ch);
            int lw = g.getFontMetrics().stringWidth(lbl);
            g.drawString(lbl, Math.min(Math.max(0, x - lw / 2), w - lw), h - 4);
            g.setColor(new Color(0x33, 0x3C, 0x46));
            g.drawLine(x, wfY + wfH, x, wfY + wfH + 3);
            g.setColor(new Color(0x6B, 0x76, 0x82));
        }
    }

    private void drawMeter(Graphics2D g, int x, int y, int w, int h) {
        g.setColor(new Color(0x16, 0x1C, 0x24));
        g.fillRect(x, y, w, h);
        int fill = (int) Math.round(liveLevel * (w - 2));
        if (fill > 0) {
            g.setColor(intensityColor(liveLevel));
            g.fillRect(x + 1, y + 1, fill, h - 2);
        }
        g.setColor(new Color(0x3A, 0x44, 0x50));
        g.drawRect(x, y, w, h);
        g.setColor(new Color(0x9A, 0xA6, 0xB2));
        g.setFont(getFont().deriveFont(Font.BOLD, 10f));
        g.drawString("RX", x + 4, y + h - 3);
    }

    /** Green -> yellow -> red ramp for bar/meter intensity. */
    private static Color intensityColor(double n) {
        float t = (float) Math.max(0, Math.min(1, n));
        float hue = 0.33f * (1 - t);          // 0.33=green down to 0.0=red
        float bright = 0.55f + 0.45f * t;
        return Color.getHSBColor(hue, 1f, bright);
    }

    /** Classic waterfall ramp: dark -> blue -> green -> yellow -> red. */
    private static Color waterfallColor(double n) {
        if (n <= 0) return new Color(0x05, 0x08, 0x0C);
        float t = (float) Math.min(1, n);
        float hue = 0.66f * (1 - t);          // 0.66=blue down to 0.0=red
        float bright = 0.25f + 0.75f * t;
        return Color.getHSBColor(hue, 1f, bright);
    }
}
