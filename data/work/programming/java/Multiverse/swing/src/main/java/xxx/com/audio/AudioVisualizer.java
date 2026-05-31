package xxx.com.audio;

import com.formdev.flatlaf.FlatDarkLaf;
import edu.emory.mathcs.jtransforms.fft.DoubleFFT_1D;

import javax.sound.sampled.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Path2D;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

/**
 * An advanced audio visualizer that captures microphone input and displays a frequency spectrum,
 * with a comprehensive UI for controlling audio and display parameters.
 */
public class AudioVisualizer extends JFrame {

  private volatile boolean isRunning = true;
  private Thread audioThread;
  private final VisualizationPanel visualizationPanel;
  private final WaterfallPanel waterfallPanel;
  private final Settings settings = new Settings();
  private JSplitPane splitPane;
  private JSplitPane mainSplit;
  private boolean isSplit = true;
  private int dividerPos = -1;
  private final Object resizeLock = new Object();
  private long lastResizeTime = 0;
  private static final long RESIZE_DEBOUNCE_MS = 100;
  private boolean isAudioPanelVisible = false;

  // --- Configuration Class ---
  private static class Settings {
    volatile int sampleRate = 48000;
    volatile int fftSize = 512;
    volatile String windowFunction = "Blackman-Harris";
    volatile int decimation = 0;
    volatile double smoothingFactor = 0.15;
    volatile boolean logarithmicScale = false;
  }

  // --- Windowing Utility Class ---
  private static class Windowing {
    public static void applyWindow(String function, double[] buffer) {
      int n = buffer.length;
      switch (function) {
        case "Cosine":
          for (int i = 0; i < n; i++) buffer[i] *= Math.sin(Math.PI * i / (n - 1));
          break;
        case "von Hann":
          for (int i = 0; i < n; i++) buffer[i] *= 0.5 * (1 - Math.cos(2 * Math.PI * i / (n - 1)));
          break;
        case "Hamming":
          for (int i = 0; i < n; i++)
            buffer[i] *= 0.54 - 0.46 * Math.cos(2 * Math.PI * i / (n - 1));
          break;
        case "Blackman":
          for (int i = 0; i < n; i++) {
            buffer[i] *=
                0.42
                    - 0.5 * Math.cos(2 * Math.PI * i / (n - 1))
                    + 0.08 * Math.cos(4 * Math.PI * i / (n - 1));
          }
          break;
        case "Blackman-Harris":
          for (int i = 0; i < n; i++) {
            buffer[i] *=
                0.35875
                    - 0.48829 * Math.cos(2 * Math.PI * i / (n - 1))
                    + 0.14128 * Math.cos(4 * Math.PI * i / (n - 1))
                    - 0.01168 * Math.cos(6 * Math.PI * i / (n - 1));
          }
          break;
        case "Flat top":
          for (int i = 0; i < n; i++) {
            buffer[i] *=
                1
                    - 1.93 * Math.cos(2 * Math.PI * i / (n - 1))
                    + 1.29 * Math.cos(4 * Math.PI * i / (n - 1))
                    - 0.388 * Math.cos(6 * Math.PI * i / (n - 1))
                    + 0.0322 * Math.cos(8 * Math.PI * i / (n - 1));
          }
          break;
        case "Triangular":
          for (int i = 0; i < n; i++) {
            buffer[i] *= (2.0 / (n - 1)) * (((n - 1) / 2.0) - Math.abs(i - ((n - 1) / 2.0)));
          }
          break;
        case "Welch":
          for (int i = 0; i < n; i++) {
            buffer[i] *= 1 - Math.pow((i - (n - 1) / 2.0) / ((n - 1) / 2.0), 2);
          }
          break;
      }
    }
  }

  public AudioVisualizer() {
    setTitle("Live Audio Spectrum Visualizer");
    setSize(1024, 600);
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    setLocationRelativeTo(null);
    setLayout(new BorderLayout());

    visualizationPanel = new VisualizationPanel(settings);
    waterfallPanel = new WaterfallPanel(settings);

    splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, true, visualizationPanel, waterfallPanel);
    splitPane.setResizeWeight(0.5);
    splitPane.setOneTouchExpandable(true);

    ControlsPanel controlsPanel = new ControlsPanel(this::restartAudioProcessing);
    controlsPanel.setPreferredSize(new Dimension(300, 0));

    // --- MODIFICATION START ---
    // This section has been refactored to correctly layer the button over the graphs.

    // 1. Create the floating hamburger button
    JButton menuButton = new JButton("☰");
    menuButton.setFont(new Font("SansSerif", Font.BOLD, 16));
    menuButton.setBorder(BorderFactory.createEmptyBorder());
    menuButton.setMargin(new Insets(0,0,0,0));
    menuButton.setContentAreaFilled(false);
    menuButton.setFocusPainted(false);
    menuButton.setForeground(Color.WHITE);
    menuButton.addActionListener(e -> toggleControls());

    // 2. Create a JLayeredPane to hold the graphs and the button
    JLayeredPane graphLayeredPane = new JLayeredPane();
    // Add the splitPane (containing the graphs) to the default (bottom) layer
    graphLayeredPane.add(splitPane, JLayeredPane.DEFAULT_LAYER);
    // Add the button to the palette (top) layer
    graphLayeredPane.add(menuButton, JLayeredPane.PALETTE_LAYER);

    // 3. Add a component listener to resize children and position the button
    graphLayeredPane.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        // Make the graph split pane fill the entire layered pane
        splitPane.setBounds(0, 0, graphLayeredPane.getWidth(), graphLayeredPane.getHeight());
        // Position the button at the top-left with 10x10 padding
        menuButton.setBounds(10, 10, 40, 40);
      }
    });

    // 4. Create the main split pane with the layered pane (graphs+button) and controls
    mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, graphLayeredPane, controlsPanel);
    mainSplit.setResizeWeight(1.0);
    mainSplit.setOneTouchExpandable(false); // We handle this with the button
    mainSplit.setBorder(null); // Cleaner look

    add(mainSplit, BorderLayout.CENTER);
    // --- MODIFICATION END ---


    SwingUtilities.invokeLater(() -> {
      // Hide the control panel by default
      mainSplit.setDividerLocation(1.0);
      // Set the vertical divider for the graphs
      splitPane.setDividerLocation(0.5);
    });

    addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        synchronized (resizeLock) {
          long currentTime = System.currentTimeMillis();
          if (currentTime - lastResizeTime > RESIZE_DEBOUNCE_MS) {
            lastResizeTime = currentTime;
            if (isSplit) {
              splitPane.setDividerLocation(0.5);
            }
            visualizationPanel.repaint();
            waterfallPanel.repaint();
          }
        }
      }
    });

    visualizationPanel.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() == 2) {
          toggleSplit(true);
        }
      }
    });

    waterfallPanel.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() == 2) {
          toggleSplit(false);
        }
      }
    });

    startAudioProcessing();

    setVisible(true);
  }

  private void toggleControls() {
    isAudioPanelVisible = !isAudioPanelVisible;
    // Calculate the target position for the divider
    int target = isAudioPanelVisible ? mainSplit.getWidth() - 300 : mainSplit.getWidth();

    // Use a Swing Timer for a smooth animation
    final int animationDuration = 200; // ms
    final int frameRate = 60; // fps
    final int delay = 1000 / frameRate;
    final int totalSteps = animationDuration / delay;
    final int initialLocation = mainSplit.getDividerLocation();
    final int distance = target - initialLocation;

    javax.swing.Timer timer = new javax.swing.Timer(delay, null);
    timer.addActionListener(new ActionListener() {
      private int step = 0;
      @Override
      public void actionPerformed(ActionEvent e) {
        if (step >= totalSteps) {
          mainSplit.setDividerLocation(target);
          ((javax.swing.Timer) e.getSource()).stop();
          // Revalidate to ensure layout is correct after animation
          revalidate();
          repaint();
          return;
        }
        // Use an easing function for smoother animation (ease-out)
        double t = (double) step / totalSteps;
        double easedT = 1 - Math.pow(1 - t, 3);
        int newLocation = initialLocation + (int) (distance * easedT);
        mainSplit.setDividerLocation(newLocation);
        step++;
      }
    });
    timer.start();
  }


  private void toggleSplit(boolean maximizeTop) {
    if (isSplit) {
      dividerPos = splitPane.getDividerLocation();
      if (maximizeTop) {
        splitPane.setDividerLocation(1.0);
      } else {
        splitPane.setDividerLocation(0.0);
      }
      isSplit = false;
    } else {
      splitPane.setDividerLocation(dividerPos != -1 ? dividerPos : getHeight() / 2);
      isSplit = true;
    }
    visualizationPanel.repaint();
    waterfallPanel.repaint();
  }

  private void startAudioProcessing() {
    isRunning = true;
    audioThread = new Thread(this::captureAndProcessAudio);
    audioThread.setDaemon(true);
    audioThread.start();
  }

  private void stopAudioProcessing() {
    isRunning = false;
    if (audioThread != null) {
      try {
        audioThread.join();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }

  public void restartAudioProcessing() {
    stopAudioProcessing();
    startAudioProcessing();
  }

  private void captureAndProcessAudio() {
    try {
      AudioFormat format = new AudioFormat(settings.sampleRate, 16, 1, true, false);
      DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);

      if (!AudioSystem.isLineSupported(info)) {
        System.err.println("Audio line not supported with format: " + format);
        SwingUtilities.invokeLater(
            () ->
                JOptionPane.showMessageDialog(
                    this,
                    "Audio format not supported by any microphone:\n"
                        + format
                        + "\nTry a different sample rate.",
                    "Audio Error",
                    JOptionPane.ERROR_MESSAGE));
        return;
      }

      TargetDataLine line = (TargetDataLine) AudioSystem.getLine(info);
      line.open(format, settings.fftSize * 2);
      line.start();

      byte[] buffer = new byte[settings.fftSize * 2];
      double[] audioData = new double[settings.fftSize];

      int decimationFactor = 1 << settings.decimation;
      int decimatedFftSize = settings.fftSize / decimationFactor;

      double[] decimatedAudioData = new double[decimatedFftSize];
      DoubleFFT_1D fft = new DoubleFFT_1D(decimatedFftSize);

      while (isRunning) {
        int bytesRead = line.read(buffer, 0, buffer.length);
        if (bytesRead > 0) {
          int samplesRead = bytesRead / 2;
          Arrays.fill(audioData, 0.0);

          for (int i = 0; i < samplesRead; i++) {
            int sample = (buffer[2 * i + 1] << 8) | (buffer[2 * i] & 0xFF);
            audioData[i] = sample / 32768.0;
          }

          for (int i = 0; i < decimatedFftSize; i++) {
            double sum = 0;
            for (int j = 0; j < decimationFactor; j++) {
              sum += audioData[i * decimationFactor + j];
            }
            decimatedAudioData[i] = sum / decimationFactor;
          }
          Windowing.applyWindow(settings.windowFunction, decimatedAudioData);
          fft.realForward(decimatedAudioData);

          int numBins = decimatedFftSize / 2;
          double[] magnitudes = new double[numBins + 1];
          magnitudes[0] = Math.abs(decimatedAudioData[0]) / decimatedFftSize;
          for (int i = 1; i < numBins; i++) {
            double real = decimatedAudioData[2 * i];
            double imag = decimatedAudioData[2 * i + 1];
            magnitudes[i] = 2 * Math.sqrt(real * real + imag * imag) / decimatedFftSize;
          }
          magnitudes[numBins] = Math.abs(decimatedAudioData[1]) / decimatedFftSize;

          visualizationPanel.setMagnitudes(magnitudes);
          waterfallPanel.addSpectrum(magnitudes);
        }
      }
      line.stop();
      line.close();

    } catch (LineUnavailableException e) {
      e.printStackTrace();
      SwingUtilities.invokeLater(
          () ->
              JOptionPane.showMessageDialog(
                  this,
                  "Could not open microphone line. Is it in use?\nTry a different sample rate.",
                  "Audio Error",
                  JOptionPane.ERROR_MESSAGE));
    }
  }

  // --- Visualization Panel (Inner Class) ---
  private static class VisualizationPanel extends JPanel {
    private final Settings settings;
    private double[] magnitudes;
    private double[] instantDb;
    private double[] peakDb;
    private static final double MIN_DB = -120.0;
    private static final double MAX_DB = 0.0;

    public VisualizationPanel(Settings settings) {
      this.settings = settings;
      setBackground(Color.BLACK);
      // --- MODIFICATION START ---
      // Add padding to make space for the axis labels.
      setBorder(new EmptyBorder(10, 50, 30, 10)); // top, left, bottom, right
      // --- MODIFICATION END ---
      addComponentListener(new ComponentAdapter() {
        @Override
        public void componentResized(ComponentEvent e) {
          repaint();
        }
      });
    }

    public void setMagnitudes(double[] magnitudes) {
      int requiredSize = magnitudes.length;

      if (this.magnitudes == null || this.magnitudes.length != requiredSize) {
        this.magnitudes = new double[requiredSize];
        this.instantDb = new double[requiredSize];
        this.peakDb = new double[requiredSize];
        Arrays.fill(peakDb, MIN_DB);
      }

      System.arraycopy(magnitudes, 0, this.magnitudes, 0, requiredSize);

      for (int i = 0; i < requiredSize; i++) {
        double mag = magnitudes[i];
        double db = (mag > 1e-10) ? 20 * Math.log10(mag) : MIN_DB;
        instantDb[i] = db;
        if (db > peakDb[i]) {
          peakDb[i] = db;
        }
      }
      SwingUtilities.invokeLater(this::repaint);
    }

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      if (magnitudes == null) return;

      Graphics2D g2d = (Graphics2D) g;

      // --- MODIFICATION START ---
      // Account for the border insets to ensure drawing happens in the correct area.
      Insets insets = getInsets();
      int graphWidth = getWidth() - insets.left - insets.right;
      int graphHeight = getHeight() - insets.top - insets.bottom;

      // Translate the graphics context to the graph's origin
      g2d.translate(insets.left, insets.top);
      int numBars = magnitudes.length - 1;
      // --- MODIFICATION END ---

      g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

      // Draw grid first
      drawGrid(g2d, graphWidth, graphHeight, numBars);

      // Draw axes and ticks
      drawAxesAndTicks(g2d, graphWidth, graphHeight, numBars);

      // Draw instant line (yellow, jagged)
      g2d.setColor(Color.YELLOW);
      g2d.setStroke(new BasicStroke(1.5f));
      Path2D instantPath = new Path2D.Double();
      boolean first = true;
      for (int i = 0; i < numBars; i++) {
        double x = getX(i, numBars, graphWidth, settings.logarithmicScale);
        int y = getY(instantDb[i], graphHeight);
        if (first) {
          instantPath.moveTo(x, y);
          first = false;
        } else {
          instantPath.lineTo(x, y);
        }
      }
      g2d.draw(instantPath);

      // Draw peak line (red, high-water mark)
      g2d.setColor(Color.RED);
      g2d.setStroke(new BasicStroke(2.0f));
      Path2D peakPath = new Path2D.Double();
      first = true;
      for (int i = 0; i < numBars; i++) {
        double x = getX(i, numBars, graphWidth, settings.logarithmicScale);
        int y = getY(peakDb[i], graphHeight);
        if (first) {
          peakPath.moveTo(x, y);
          first = false;
        } else {
          peakPath.lineTo(x, y);
        }
      }
      g2d.draw(peakPath);

      // Translate back to avoid affecting other components
      g2d.translate(-insets.left, -insets.top);
    }

    private double getX(int i, int numBars, int panelWidth, boolean logScale) {
      if (logScale) {
        if (i == 0) return 0;
        double logBase = Math.log(numBars);
        double freqRatio = (double) i / numBars;
        return panelWidth * Math.log(1 + freqRatio * (Math.exp(logBase) - 1)) / logBase;
      } else {
        return (double) panelWidth * i / numBars;
      }
    }

    private int getY(double db, int panelHeight) {
      double clamped = Math.max(MIN_DB, Math.min(MAX_DB, db));
      return (int) (panelHeight * (MAX_DB - clamped) / (MAX_DB - MIN_DB));
    }

    private void drawGrid(Graphics g, int panelWidth, int panelHeight, int numBars) {
      g.setColor(new Color(64, 64, 64));

      // Horizontal grid lines (dB)
      for (double db = MAX_DB; db >= MIN_DB; db -= 25) {
        int y = getY(db, panelHeight);
        g.drawLine(0, y, panelWidth, y);
      }

      // Vertical grid lines (Hz)
      double freqRes = (double) settings.sampleRate / settings.fftSize;
      double nyquist = settings.sampleRate / 2.0 / (1 << settings.decimation);
      double[] tickFreqs = {10, 20, 50, 100, 200, 500, 1000, 2000, 5000, 10000, 20000};
      for (double tickFreq : tickFreqs) {
        if (tickFreq >= nyquist) continue;
        double binD = tickFreq / freqRes;
        int bin = (int) Math.round(binD);
        if (bin > 0 && bin < numBars) {
          double x = getX(bin, numBars, panelWidth, settings.logarithmicScale);
          g.drawLine((int) x, 0, (int) x, panelHeight);
        }
      }
    }

    private void drawAxesAndTicks(Graphics g, int panelWidth, int panelHeight, int numBars) {
      g.setColor(Color.WHITE);
      // --- MODIFICATION START ---
      // Make ticks longer and labels clearer.
      int tickLength = 8;
      Font font = new Font("SansSerif", Font.PLAIN, 11);
      g.setFont(font);
      FontMetrics fm = g.getFontMetrics();

      // Y-axis ticks (dB) - Drawn outside the graph area to the left
      for (double db = MAX_DB; db >= MIN_DB; db -= 25) {
        int y = getY(db, panelHeight);
        g.drawLine(0, y, -tickLength, y);
        String label = String.format("%.0f dB", db);
        // Align text to the right of the tick mark
        g.drawString(label, -tickLength - fm.stringWidth(label) - 4, y + (fm.getAscent() / 2) - 2);
      }

      // X-axis ticks (frequency) - Drawn outside the graph area at the bottom
      g.drawLine(0, panelHeight, panelWidth, panelHeight);
      double freqRes = (double) settings.sampleRate / settings.fftSize;
      double nyquist = settings.sampleRate / 2.0 / (1 << settings.decimation);
      double[] tickFreqs = {10, 20, 50, 100, 200, 500, 1000, 2000, 5000, 10000, 20000};
      for (double tickFreq : tickFreqs) {
        if (tickFreq >= nyquist) continue;
        double binD = tickFreq / freqRes;
        int bin = (int) Math.round(binD);
        if (bin > 0 && bin < numBars) {
          double x = getX(bin, numBars, panelWidth, settings.logarithmicScale);
          g.drawLine((int) x, panelHeight, (int) x, panelHeight + tickLength);
          String label = (tickFreq >= 1000) ? ((int)(tickFreq/1000)) + " kHz" : String.format("%d Hz", (int) tickFreq);
          // Center text below the tick mark
          g.drawString(label, (int) x - fm.stringWidth(label) / 2, panelHeight + tickLength + fm.getAscent());
        }
      }
      // --- MODIFICATION END ---
    }
  }

  // --- Waterfall Panel (Inner Class) ---
  private static class WaterfallPanel extends JPanel {
    private final Settings settings;
    private final LinkedList<double[]> history = new LinkedList<>();
    private final int HISTORY_DEPTH = 200;
    private static final double MIN_DB = -120.0;
    private static final double MAX_DB = 0.0;

    public WaterfallPanel(Settings settings) {
      this.settings = settings;
      setBackground(Color.BLACK);
      // --- MODIFICATION START ---
      // Add padding for consistency with the top panel.
      setBorder(new EmptyBorder(10, 50, 30, 10)); // top, left, bottom, right
      // --- MODIFICATION END ---
      addComponentListener(new ComponentAdapter() {
        @Override
        public void componentResized(ComponentEvent e) {
          repaint();
        }
      });
    }

    public void addSpectrum(double[] magnitudes) {
      double[] dbSpectrum = new double[magnitudes.length];
      for (int i = 0; i < magnitudes.length; i++) {
        double db = (magnitudes[i] > 1e-10) ? 20 * Math.log10(magnitudes[i]) : MIN_DB;
        dbSpectrum[i] = Math.max(MIN_DB, Math.min(MAX_DB, db));
      }
      synchronized (history) {
        history.addFirst(dbSpectrum);
        if (history.size() > HISTORY_DEPTH) {
          history.removeLast();
        }
      }
      SwingUtilities.invokeLater(this::repaint);
    }

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      Graphics2D g2d = (Graphics2D) g;

      // --- MODIFICATION START ---
      // Account for the border insets.
      Insets insets = getInsets();
      int panelWidth = getWidth() - insets.left - insets.right;
      int panelHeight = getHeight() - insets.top - insets.bottom;
      g2d.translate(insets.left, insets.top);
      // --- MODIFICATION END ---

      synchronized (history) {
        if (history.isEmpty()) return;
        int numRows = Math.min(history.size(), panelHeight);
        int index = 0;
        for (double[] spectrum : history) {
          if (index >= numRows) break;
          int y = (panelHeight * index) / numRows;
          int nextY = (panelHeight * (index + 1)) / numRows;
          int rowH = Math.max(1, nextY - y);
          drawRow(g2d, spectrum, y, rowH, panelWidth);
          index++;
        }
      }

      // Draw axes and ticks
      drawAxesAndTicks(g2d, panelWidth, panelHeight, history.getFirst().length - 1);

      // Translate back
      g2d.translate(-insets.left, -insets.top);
    }

    private void drawRow(Graphics2D g2d, double[] spectrum, int y, int rowH, int panelWidth) {
      int numBars = spectrum.length - 1;
      for (int i = 0; i < numBars; i++) {
        double dbValue = spectrum[i];
        float ratio = (float) ((dbValue - MIN_DB) / (MAX_DB - MIN_DB));
        float hue = (1 - ratio) * (2f / 3f);
        g2d.setColor(Color.getHSBColor(hue, 1f, 1f));

        double startX = getX(i, numBars, panelWidth, settings.logarithmicScale);
        double endX = getX(i + 1, numBars, panelWidth, settings.logarithmicScale);
        int barWidth = (int) Math.max(1, endX - startX);

        g2d.fillRect((int) startX, y, barWidth, rowH);
      }
    }

    private double getX(int i, int numBars, int panelWidth, boolean logScale) {
      if (logScale) {
        if (i == 0) return 0;
        double logBase = Math.log(numBars);
        double freqRatio = (double) i / numBars;
        return panelWidth * Math.log(1 + freqRatio * (Math.exp(logBase) - 1)) / logBase;
      } else {
        return (double) panelWidth * i / numBars;
      }
    }

    private void drawAxesAndTicks(Graphics g, int panelWidth, int panelHeight, int numBars) {
      g.setColor(Color.WHITE);
      // --- MODIFICATION START ---
      // Make ticks longer and labels clearer.
      int tickLength = 8;
      Font font = new Font("SansSerif", Font.PLAIN, 11);
      g.setFont(font);
      FontMetrics fm = g.getFontMetrics();

      // X-axis
      g.drawLine(0, panelHeight, panelWidth, panelHeight);

      // X-axis ticks (frequency)
      double freqRes = (double) settings.sampleRate / settings.fftSize;
      double nyquist = settings.sampleRate / 2.0 / (1 << settings.decimation);
      double[] tickFreqs = {10, 20, 50, 100, 200, 500, 1000, 2000, 5000, 10000, 20000};
      for (double tickFreq : tickFreqs) {
        if (tickFreq >= nyquist) continue;
        double binD = tickFreq / freqRes;
        int bin = (int) Math.round(binD);
        if (bin > 0 && bin < numBars) {
          double x = getX(bin, numBars, panelWidth, settings.logarithmicScale);
          g.drawLine((int) x, panelHeight, (int) x, panelHeight + tickLength);
          String label = (tickFreq >= 1000) ? ((int)(tickFreq/1000)) + " kHz" : String.format("%d Hz", (int) tickFreq);
          g.drawString(label, (int) x - fm.stringWidth(label) / 2, panelHeight + tickLength + fm.getAscent());
        }
      }
      // --- MODIFICATION END ---
    }
  }

  // --- Controls Panel (Inner Class) ---
  private class ControlsPanel extends JPanel {
    public ControlsPanel(Runnable restartCallback) {
      setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

      JPanel audioPanel = new JPanel();
      audioPanel.setLayout(new GridBagLayout());
      audioPanel.setBorder(BorderFactory.createTitledBorder("Audio Settings"));

      GridBagConstraints gbc = new GridBagConstraints();
      gbc.insets = new Insets(5, 5, 5, 5);
      gbc.anchor = GridBagConstraints.WEST;
      gbc.fill = GridBagConstraints.HORIZONTAL;
      gbc.weightx = 1.0;

      gbc.gridx = 0;
      gbc.gridy = 0;
      audioPanel.add(new JLabel("Sample Rate:"), gbc);

      gbc.gridx = 1;
      JComboBox<String> sampleRateCombo =
          new JComboBox<>(
              new String[] {
                  "96000 Hz", "48000 Hz", "44100 Hz", "22050 Hz", "16000 Hz", "11025 Hz", "8000 Hz"
              });
      sampleRateCombo.setSelectedItem("48000 Hz");
      sampleRateCombo.addActionListener(
          e -> {
            String selected = (String) Objects.requireNonNull(sampleRateCombo.getSelectedItem());
            settings.sampleRate = Integer.parseInt(selected.replaceAll("[^\\d]", ""));
            restartCallback.run();
          });
      audioPanel.add(sampleRateCombo, gbc);

      gbc.gridx = 0;
      gbc.gridy = 1;
      audioPanel.add(new JLabel("Downsampling:"), gbc);

      gbc.gridx = 1;
      JComboBox<String> decimationCombo =
          new JComboBox<>(
              new String[] {
                  "0 (94 Hz/bin @ DC)", "1 (47 Hz/bin @ DC)", "2 (23 Hz/bin @ DC)",
                  "3 (12 Hz/bin @ DC)", "4 (5.9 Hz/bin @ DC)", "5 (2.9 Hz/bin @ DC)",
                  "6 (1.5 Hz/bin @ DC)", "7 (0.73 Hz/bin @ DC)", "8 (0.37 Hz/bin @ DC)"
              });
      decimationCombo.setSelectedItem("0 (94 Hz/bin @ DC)");
      decimationCombo.addActionListener(
          e -> {
            String selected = (String) Objects.requireNonNull(decimationCombo.getSelectedItem());
            settings.decimation = Integer.parseInt(selected.split(" ")[0]);
            restartCallback.run();
          });
      audioPanel.add(decimationCombo, gbc);

      add(audioPanel);

      JPanel analysisPanel = new JPanel();
      analysisPanel.setLayout(new GridBagLayout());
      analysisPanel.setBorder(BorderFactory.createTitledBorder("Analysis Settings"));

      gbc = new GridBagConstraints();
      gbc.insets = new Insets(5, 5, 5, 5);
      gbc.anchor = GridBagConstraints.WEST;
      gbc.fill = GridBagConstraints.HORIZONTAL;
      gbc.weightx = 1.0;

      gbc.gridx = 0;
      gbc.gridy = 0;
      analysisPanel.add(new JLabel("FFT Size:"), gbc);

      gbc.gridx = 1;
      JComboBox<String> fftSizeCombo =
          new JComboBox<>(
              new String[] {
                  "64 bins (750 Hz/bin)", "128 bins (375 Hz/bin)", "256 bins (188 Hz/bin)",
                  "512 bins (94 Hz/bin)", "1024 bins (47 Hz/bin)", "2048 bins (23 Hz/bin)",
                  "4096 bins (12 Hz/bin)", "8192 bins (5.9 Hz/bin)"
              });
      fftSizeCombo.setSelectedItem("4096 bins (12 Hz/bin)");
      fftSizeCombo.addActionListener(
          e -> {
            String selected = (String) Objects.requireNonNull(fftSizeCombo.getSelectedItem());
            settings.fftSize = Integer.parseInt(selected.split(" ")[0]);
            restartCallback.run();
          });
      analysisPanel.add(fftSizeCombo, gbc);

      gbc.gridx = 0;
      gbc.gridy = 1;
      analysisPanel.add(new JLabel("Transform Interval:"), gbc);

      gbc.gridx = 1;
      JComboBox<String> intervalCombo =
          new JComboBox<>(
              new String[] {
                  "10 ms (100 Hz)",
                  "15 ms (~67 Hz)",
                  "20 ms (50 Hz)",
                  "25 ms (40 Hz)",
                  "30 ms (~33 Hz)",
                  "50 ms (20 Hz)",
                  "100 ms (10 Hz)"
              });
      intervalCombo.setSelectedItem("100 ms (10 Hz)");
      intervalCombo.addActionListener(
          e -> {
            String selected = (String) Objects.requireNonNull(intervalCombo.getSelectedItem());
            String intervalStr = selected.split(" ")[0];
            double intervalMs = Double.parseDouble(intervalStr);
            double intervalS = intervalMs / 1000.0;

            double idealFftSize = intervalS * settings.sampleRate;

            Integer[] fftSizes = {64, 128, 256, 512, 1024, 2048, 4096, 8192};
            int closestFftSize = fftSizes[0];
            double minDiff = Double.MAX_VALUE;

            for (int size : fftSizes) {
              double diff = Math.abs(idealFftSize - size);
              if (diff < minDiff) {
                minDiff = diff;
                closestFftSize = size;
              }
            }

            for (int i = 0; i < fftSizeCombo.getItemCount(); i++) {
              if (fftSizeCombo.getItemAt(i).startsWith(String.valueOf(closestFftSize))) {
                fftSizeCombo.setSelectedIndex(i);
                break;
              }
            }
          });
      analysisPanel.add(intervalCombo, gbc);

      gbc.gridx = 0;
      gbc.gridy = 2;
      analysisPanel.add(new JLabel("Window:"), gbc);

      gbc.gridx = 1;
      JComboBox<String> windowCombo =
          new JComboBox<>(
              new String[] {
                  "Cosine",
                  "von Hann",
                  "Hamming",
                  "Blackman",
                  "Blackman-Harris",
                  "Flat top",
                  "Rectangular",
                  "Triangular",
                  "Welch"
              });
      windowCombo.setSelectedItem(settings.windowFunction);
      windowCombo.addActionListener(
          e ->
              settings.windowFunction =
                  (String) Objects.requireNonNull(windowCombo.getSelectedItem()));
      analysisPanel.add(windowCombo, gbc);

      add(analysisPanel);

      JPanel displayPanel = new JPanel();
      displayPanel.setLayout(new GridBagLayout());
      displayPanel.setBorder(BorderFactory.createTitledBorder("Display Settings"));

      gbc = new GridBagConstraints();
      gbc.insets = new Insets(5, 5, 5, 5);
      gbc.anchor = GridBagConstraints.WEST;
      gbc.fill = GridBagConstraints.HORIZONTAL;
      gbc.weightx = 1.0;

      gbc.gridx = 0;
      gbc.gridy = 0;
      displayPanel.add(new JLabel("Smoothing:"), gbc);

      gbc.gridx = 1;
      JSlider smoothingSlider = new JSlider(0, 99, (int) (settings.smoothingFactor * 100));
      smoothingSlider.addChangeListener(
          e -> settings.smoothingFactor = smoothingSlider.getValue() / 100.0);
      displayPanel.add(smoothingSlider, gbc);

      gbc.gridx = 0;
      gbc.gridy = 1;
      gbc.gridwidth = 2;
      JCheckBox logScaleCheckbox = new JCheckBox("Logarithmic Scale", settings.logarithmicScale);
      logScaleCheckbox.addActionListener(
          e -> {
            settings.logarithmicScale = logScaleCheckbox.isSelected();
            visualizationPanel.repaint();
            waterfallPanel.repaint();
          });
      displayPanel.add(logScaleCheckbox, gbc);
      add(displayPanel);
    }
  }

  public static void main(String[] args) {
    FlatDarkLaf.setup();
    SwingUtilities.invokeLater(AudioVisualizer::new);
  }
}
