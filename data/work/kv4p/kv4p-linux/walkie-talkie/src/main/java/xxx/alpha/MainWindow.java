package xxx.alpha;

import java.awt.*;
import java.util.Arrays;
import javax.swing.*;
import javax.swing.event.ChangeEvent;

public class MainWindow extends JFrame {

    // Theme Colors
    private static final Color BG_COLOR = new Color(40, 42, 54);        // #282a36
    private static final Color ACCENT_COLOR = new Color(139, 233, 253); // #8be9fd
    private static final Color TEXT_COLOR = new Color(248, 248, 242);   // #f8f8f2
    private static final Color PTT_ACTIVE = new Color(255, 85, 85);     // #ff5555

    private final DspVisualizerPanel dspPanel;
    boolean isPttActive = false;
    private int squelchLevel = 5;

    public MainWindow() {
        setTitle("kv4p HT Desktop");
        setSize(500, 700);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        getContentPane().setBackground(BG_COLOR);
        setLayout(new BorderLayout());

        // --- Top: Frequency & Status ---
        JPanel topPanel = new JPanel(new GridLayout(2, 1));
        topPanel.setBackground(BG_COLOR);

        JLabel freqLabel = new JLabel("146.520 MHz", SwingConstants.CENTER);
        freqLabel.setFont(new Font("Monospaced", Font.BOLD, 48));
        freqLabel.setForeground(ACCENT_COLOR);

        JLabel statusLabel = new JLabel("Status: RX | Squelch: " + squelchLevel, SwingConstants.CENTER);
        statusLabel.setForeground(TEXT_COLOR);
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 14));

        topPanel.add(freqLabel);
        topPanel.add(statusLabel);
        add(topPanel, BorderLayout.NORTH);

        // --- Center: DSP Visualizer ---
        dspPanel = new DspVisualizerPanel();
        add(dspPanel, BorderLayout.CENTER);

        // --- Bottom: Controls ---
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBackground(BG_COLOR);

        // Squelch Slider
        JPanel squelchPanel = new JPanel(new BorderLayout());
        squelchPanel.setBackground(BG_COLOR);
        JLabel sqLabel = new JLabel(" SQ ");
        sqLabel.setForeground(TEXT_COLOR);
        JSlider squelchSlider = new JSlider(0, 9, squelchLevel);
        squelchSlider.setBackground(BG_COLOR);
        squelchSlider.addChangeListener((ChangeEvent e) -> {
            squelchLevel = squelchSlider.getValue();
            statusLabel.setText("Status: " + (isPttActive ? "TX" : "RX") + " | Squelch: " + squelchLevel);
            // Fire command to SerialManager to update ESP32 squelch
        });
        squelchPanel.add(sqLabel, BorderLayout.WEST);
        squelchPanel.add(squelchSlider, BorderLayout.CENTER);

        // PTT Button
        JButton pttButton = new JButton("PTT");
        pttButton.setFont(new Font("SansSerif", Font.BOLD, 24));
        pttButton.setBackground(BG_COLOR.brighter());
        pttButton.setForeground(TEXT_COLOR);
        pttButton.setFocusPainted(false);
        pttButton.addActionListener(e -> {
            isPttActive = !isPttActive;
            if (isPttActive) {
                pttButton.setBackground(PTT_ACTIVE);
                statusLabel.setText("Status: TX | Squelch: " + squelchLevel);
                // Trigger SerialManager to assert PTT and start routing mic buffer
            } else {
                pttButton.setBackground(BG_COLOR.brighter());
                statusLabel.setText("Status: RX | Squelch: " + squelchLevel);
                // Trigger SerialManager to release PTT
            }
        });

        bottomPanel.add(squelchPanel, BorderLayout.NORTH);
        bottomPanel.add(pttButton, BorderLayout.CENTER);
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        add(bottomPanel, BorderLayout.SOUTH);
    }

    /**
     * Passes incoming audio data to the visualizer to draw the waveform.
     */
    public void updateVisualizer(byte[] audioBuffer) {
        dspPanel.updateWaveform(audioBuffer);
    }

    public boolean isPttActive() {
        return isPttActive;
    }

    // Custom Canvas for DSP Visualization
    private static class DspVisualizerPanel extends JPanel {
        private byte[] waveform = new byte[0];

        public DspVisualizerPanel() {
            setBackground(BG_COLOR);
        }

        public void updateWaveform(byte[] buffer) {
            this.waveform = Arrays.copyOf(buffer, buffer.length);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g;
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int width = getWidth();
            int height = getHeight();
            int centerY = height / 2;

            // Draw baseline
            g2d.setColor(new Color(98, 114, 164)); // Comment color for baseline
            g2d.drawLine(0, centerY, width, centerY);

            if (waveform == null || waveform.length == 0) return;

            g2d.setColor(ACCENT_COLOR);
            int step = Math.max(1, waveform.length / width);

            for (int i = 0; i < width - 1; i++) {
                int dataIdx = i * step;
                if (dataIdx >= waveform.length - step) break;

                // Scale 8-bit PCM to panel height
                int y1 = centerY - (waveform[dataIdx] * height / 256);
                int y2 = centerY - (waveform[dataIdx + step] * height / 256);

                g2d.drawLine(i, y1, i + 1, y2);
            }
        }
    }
}