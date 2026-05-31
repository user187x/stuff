package xxx.com.audio;

import com.formdev.flatlaf.FlatDarkLaf;

import javax.swing.*;
import javax.swing.plaf.basic.BasicSliderUI;
import javax.sound.sampled.*;
import java.awt.*;
import java.awt.event.MouseWheelEvent;

public class FrequencyGenerator extends JFrame {

  // A high, but standard, sample rate. More likely to be supported by hardware.
  private static final int SAMPLE_RATE = 192000;
  private static final int MIN_FREQ = 0;
  // Updated max frequency as requested
  private static final int MAX_FREQ = 75000;
  // Larger buffer size for efficient audio playback (e.g., ~10ms at 192kHz, mono, 16-bit)
  private static final int BUFFER_SIZE_BYTES = 4096; // Must be even (2 bytes per sample)

  private JSlider frequencySlider;
  private JButton playButton;
  private JButton stopButton;
  private JLabel frequencyLabel;
  private JComboBox<String> waveTypeComboBox;

  private volatile boolean isPlaying = false;
  private Thread audioThread;
  private SourceDataLine line;

  // --- Custom Slider UI for Colored Track ---
  private static class ColorSliderUI extends BasicSliderUI {
    // Updated color range boundaries
    private static final int GREEN_END_FREQ = 15000;
    private static final int YELLOW_END_FREQ = 18000;
    private static final int ORANGE_END_FREQ = 22000;
    private static final int RED_END_FREQ = 50000;

    public ColorSliderUI(JSlider b) {
      super(b);
    }

    @Override
    public void paintTrack(Graphics g) {
      Graphics2D g2d = (Graphics2D) g;
      Rectangle trackBounds = trackRect;

      // Calculate the pixel positions for the color divisions
      int greenEnd = xPositionForValue(GREEN_END_FREQ);
      int yellowEnd = xPositionForValue(YELLOW_END_FREQ);
      int orangeEnd = xPositionForValue(ORANGE_END_FREQ);
      int redEnd = xPositionForValue(RED_END_FREQ);

      // Draw Green part
      g2d.setColor(Color.GREEN);
      g2d.fillRect(trackBounds.x, trackBounds.y, greenEnd - trackBounds.x, trackBounds.height);

      // Draw Yellow part
      g2d.setColor(Color.YELLOW);
      g2d.fillRect(greenEnd, trackBounds.y, yellowEnd - greenEnd, trackBounds.height);

      // Draw Orange part
      g2d.setColor(Color.ORANGE);
      g2d.fillRect(yellowEnd, trackBounds.y, orangeEnd - yellowEnd, trackBounds.height);

      // Draw Red part
      g2d.setColor(Color.RED);
      g2d.fillRect(orangeEnd, trackBounds.y, redEnd - orangeEnd, trackBounds.height);

      // Draw Magenta part (beyond 50kHz)
      g2d.setColor(Color.MAGENTA);
      g2d.fillRect(
          redEnd, trackBounds.y, trackBounds.width - (redEnd - trackBounds.x), trackBounds.height);
    }
  }

  public FrequencyGenerator() {
    setTitle("Animal Hearing Frequency Generator");
    setSize(550, 350); // Slightly increased height for legend
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    setLocationRelativeTo(null); // Center the window

    // --- UI Components ---
    frequencySlider = new JSlider(MIN_FREQ, MAX_FREQ, 440);
    playButton = new JButton("Play");
    stopButton = new JButton("Stop");
    frequencyLabel = new JLabel("Frequency: 440 Hz");
    frequencyLabel.setHorizontalAlignment(SwingConstants.CENTER);

    // --- Waveform Selection Dropdown ---
    String[] waveTypes = {"Sine", "Square", "Sawtooth", "Triangle"};
    waveTypeComboBox = new JComboBox<>(waveTypes);

    // --- Apply Custom Slider UI ---
    frequencySlider.setUI(new ColorSliderUI(frequencySlider));

    // --- Configure Slider Ticks ---
    frequencySlider.setMajorTickSpacing(10000);
    frequencySlider.setMinorTickSpacing(5000);
    frequencySlider.setPaintTicks(true);
    frequencySlider.setPaintLabels(true);

    // --- Layout ---
    setLayout(new BorderLayout(10, 10));
    JPanel controlPanel = new JPanel(new FlowLayout());
    JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
    mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

    controlPanel.add(new JLabel("Wave Type:"));
    controlPanel.add(waveTypeComboBox);
    controlPanel.add(playButton);
    controlPanel.add(stopButton);

    mainPanel.add(frequencyLabel, BorderLayout.NORTH);
    mainPanel.add(frequencySlider, BorderLayout.CENTER);

    // --- Add Color Legend ---
    JPanel legendPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
    legendPanel.add(createLegendItem(Color.GREEN, "0-15kHz (Human)"));
    legendPanel.add(createLegendItem(Color.YELLOW, "15-18kHz"));
    legendPanel.add(createLegendItem(Color.ORANGE, "18-22kHz"));
    legendPanel.add(createLegendItem(Color.RED, "22-50kHz (Dogs/Cats)"));
    legendPanel.add(createLegendItem(Color.MAGENTA, "50-75kHz (Bats)"));
    mainPanel.add(legendPanel, BorderLayout.SOUTH);

    // Move controls to a new south panel
    JPanel southPanel = new JPanel(new BorderLayout());
    southPanel.add(controlPanel, BorderLayout.NORTH);
    mainPanel.add(southPanel, BorderLayout.SOUTH); // Override previous south

    add(mainPanel);

    // --- Initial State ---
    stopButton.setEnabled(false);

    // --- Event Listeners ---
    playButton.addActionListener(e -> startPlayback());
    stopButton.addActionListener(e -> stopPlayback());
    frequencySlider.addChangeListener(
        e -> {
          int frequency = frequencySlider.getValue();
          frequencyLabel.setText("Frequency: " + frequency + " Hz");
        });

    // --- Add Mouse Wheel Listener for Slider Control with Fine Adjustment ---
    frequencySlider.addMouseWheelListener(
        e -> {
          int rotation = e.getWheelRotation();
          int currentValue = frequencySlider.getValue();
          int scrollAmount = e.isShiftDown() ? 10 : 100; // Fine control with Shift

          if (rotation < 0) {
            frequencySlider.setValue(Math.min(MAX_FREQ, currentValue + scrollAmount));
          } else {
            frequencySlider.setValue(Math.max(MIN_FREQ, currentValue - scrollAmount));
          }
        });

    setVisible(true);
  }

  // Helper to create legend items
  private JLabel createLegendItem(Color color, String text) {
    JLabel label = new JLabel(text);
    label.setOpaque(true);
    label.setBackground(color);
    label.setForeground(Color.BLACK); // For readability on light colors
    label.setBorder(BorderFactory.createLineBorder(Color.GRAY));
    return label;
  }

  /** Starts the audio generation and playback in a new thread. */
  private void startPlayback() {
    if (isPlaying) return;

    isPlaying = true;
    playButton.setEnabled(false);
    stopButton.setEnabled(true);

    audioThread =
        new Thread(
            () -> {
              try {
                AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
                line = AudioSystem.getSourceDataLine(format);
                line.open(format);
                line.start();

                byte[] buffer = new byte[BUFFER_SIZE_BYTES];
                long wavePosition = 0;

                while (isPlaying) {
                  for (int i = 0; i < buffer.length; i += 2) {
                    short sample = getSample(wavePosition);

                    buffer[i] = (byte) (sample & 0xFF);
                    buffer[i + 1] = (byte) ((sample >> 8) & 0xFF);

                    wavePosition++;
                  }
                  line.write(buffer, 0, buffer.length);
                }
              } catch (LineUnavailableException ex) {
                SwingUtilities.invokeLater(
                    () -> {
                      JOptionPane.showMessageDialog(
                          this,
                          "Audio line is unavailable. Your sound hardware may not support the requested format.\n"
                              + ex.getMessage(),
                          "Audio Error",
                          JOptionPane.ERROR_MESSAGE);
                      isPlaying = false;
                      playButton.setEnabled(true);
                      stopButton.setEnabled(false);
                    });
              } finally {
                if (line != null) {
                  line.drain();
                  line.stop();
                  line.close();
                }
              }
            });

    audioThread.start();
  }

  private short getSample(long wavePosition) {
    int frequency = frequencySlider.getValue();
    if (frequency <= 0) {
      return 0; // Handle zero or negative frequency to avoid division by zero
    }
    String selectedWave = (String) waveTypeComboBox.getSelectedItem();

    double samplesPerCycle = (double) SAMPLE_RATE / frequency;
    double angle = (2.0 * Math.PI * wavePosition) / samplesPerCycle;
    double rawValue = 0;

    // --- Waveform Generation Logic ---
    switch (selectedWave) {
      case "Square":
        rawValue = Math.signum(Math.sin(angle));
        break;
      case "Sawtooth":
        // Formula for a sawtooth wave
        rawValue =
            2.0
                * ((wavePosition / samplesPerCycle)
                - Math.floor(0.5 + (wavePosition / samplesPerCycle)));
        break;
      case "Triangle":
        // Formula for a triangle wave
        rawValue = (2.0 / Math.PI) * Math.asin(Math.sin(angle));
        break;
      case "Sine":
      default:
        rawValue = Math.sin(angle);
        break;
    }

    // Clamp to [-1, 1] for safety, though formulas should already be in range
    rawValue = Math.max(-1.0, Math.min(1.0, rawValue));

    short sample = (short) (rawValue * Short.MAX_VALUE);
    return sample;
  }

  /** Stops the audio playback thread. */
  private void stopPlayback() {
    isPlaying = false;
    playButton.setEnabled(true);
    stopButton.setEnabled(false);
  }

  public static void main(String[] args) {
    FlatDarkLaf.setup();
    SwingUtilities.invokeLater(FrequencyGenerator::new);
  }
}
