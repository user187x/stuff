package xxx.com.image.generators;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.Random;

public class TrippyPixelArt extends JPanel implements Runnable {
  private static final int PIXEL_SIZE = 10; // Fixed size of each pixel square in color mode
  private static final int SUB_PIXEL_SIZE = 2; // Smaller rendering size for grayscale mode
  private static final int DEFAULT_WINDOW_WIDTH = 640;
  private static final int DEFAULT_WINDOW_HEIGHT = 360;
  private static final int DELAY_MS = 50; // Animation speed
  private static final double HUE_SPEED = 0.02; // Speed of hue shift
  private static final double WAVE_SPEED = 0.1; // Speed of wave distortion for colors
  private static final double PULSE_SPEED = 0.05; // Speed of pulsing effect
  private static final double PULSE_AMPLITUDE = 2.0; // Amplitude of pulsing offset in pixels
  private static final double GLITCH_CHANCE = 0.01; // Probability of glitch burst per frame

  private Color[][] pixelGrid; // Used for color mode
  private Random random;
  private boolean isFullScreen;
  private boolean isGrayscale; // Tracks grayscale mode
  private GraphicsDevice graphicsDevice;
  private DisplayMode originalDisplayMode;
  private JFrame frame;
  private double time; // Tracks animation time for effects

  public TrippyPixelArt(JFrame frame) {
    this.frame = frame;
    setPreferredSize(new Dimension(DEFAULT_WINDOW_WIDTH, DEFAULT_WINDOW_HEIGHT));
    random = new Random();
    isFullScreen = false;
    isGrayscale = false; // Start in color mode
    time = 0;
    graphicsDevice = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
    originalDisplayMode = graphicsDevice.getDisplayMode();
    initializeGrid();
    setupKeyListener();
    // Start animation thread
    new Thread(this).start();
  }

  private void initializeGrid() {
    // Initialize grid based on current window size for color mode
    int gridWidth = getWidth() / PIXEL_SIZE;
    int gridHeight = getHeight() / PIXEL_SIZE;
    gridWidth = Math.max(1, gridWidth);
    gridHeight = Math.max(1, gridHeight);
    pixelGrid = new Color[gridWidth][gridHeight];
    updateGridColors();
  }

  private double getValueAt(double x, double y, double t) {
    // Calculate base value with wave distortion and time-based shift
    double value = (Math.sin(x * 0.1 + t * WAVE_SPEED) + Math.cos(y * 0.1 + t * WAVE_SPEED)) * 0.5;
    value = (value + t * HUE_SPEED) % 1.0;
    if (value < 0) value += 1.0;
    return value;
  }

  private Color getPsychedelicColor(double x, double y, double t) {
    double value = getValueAt(x, y, t);
    if (isGrayscale) {
      // Convert to grayscale: use value as brightness
      int gray = (int) (value * 255);
      return new Color(gray, gray, gray);
    } else {
      // Convert HSL to RGB for vibrant colors
      return Color.getHSBColor((float) value, 0.8f, 0.9f);
    }
  }

  private void updateGridColors() {
    // Update pixel grid for color mode
    if (!isGrayscale) {
      int gridWidth = pixelGrid.length;
      int gridHeight = pixelGrid.length > 0 ? pixelGrid[0].length : 0;
      for (int x = 0; x < gridWidth; x++) {
        for (int y = 0; y < gridHeight; y++) {
          pixelGrid[x][y] = getPsychedelicColor(x * PIXEL_SIZE, y * PIXEL_SIZE, time);
        }
      }
    }
  }

  private void applyGlitch(Graphics2D g) {
    // Randomly apply a glitch burst to a section of the grid
    int gridWidth = getWidth() / (isGrayscale ? SUB_PIXEL_SIZE : PIXEL_SIZE);
    int gridHeight = getHeight() / (isGrayscale ? SUB_PIXEL_SIZE : PIXEL_SIZE);
    if (random.nextDouble() < GLITCH_CHANCE) {
      int centerX = random.nextInt(gridWidth);
      int centerY = random.nextInt(gridHeight);
      int radius = random.nextInt(Math.min(gridWidth, gridHeight) / 4) + 5;
      if (isGrayscale) {
        // Use bright gray for glitch in grayscale mode
        int gray = random.nextInt(128) + 127; // Bright grays
        Color glitchColor = new Color(gray, gray, gray);
        g.setColor(glitchColor);
        int drawX = (int) (centerX * SUB_PIXEL_SIZE - radius * SUB_PIXEL_SIZE + getPulseOffset(centerX * SUB_PIXEL_SIZE, centerY * SUB_PIXEL_SIZE, time).getX());
        int drawY = (int) (centerY * SUB_PIXEL_SIZE - radius * SUB_PIXEL_SIZE + getPulseOffset(centerX * SUB_PIXEL_SIZE, centerY * SUB_PIXEL_SIZE, time).getY());
        int drawSize = radius * SUB_PIXEL_SIZE * 2;
        g.fillRect(drawX, drawY, drawSize, drawSize);
      } else {
        // Use bright color for glitch in color mode
        float glitchHue = random.nextFloat();
        Color glitchColor = Color.getHSBColor(glitchHue, 1.0f, 1.0f);
        for (int x = Math.max(0, centerX - radius); x < Math.min(gridWidth, centerX + radius); x++) {
          for (int y = Math.max(0, centerY - radius); y < Math.min(gridHeight, centerY + radius); y++) {
            pixelGrid[x][y] = glitchColor;
          }
        }
      }
    }
  }

  private Point.Double getPulseOffset(double x, double y, double t) {
    // Calculate subtle pulsing offset for grayscale mode
    double offsetX = Math.sin(y * 0.05 + t * PULSE_SPEED) * PULSE_AMPLITUDE;
    double offsetY = Math.cos(x * 0.05 + t * PULSE_SPEED) * PULSE_AMPLITUDE;
    return new Point.Double(offsetX, offsetY);
  }

  private void setupKeyListener() {
    // Add key listener for full-screen and grayscale toggle
    addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_F) {
          toggleFullScreen();
        } else if (e.getKeyCode() == KeyEvent.VK_G) {
          isGrayscale = !isGrayscale; // Toggle grayscale mode
          updateGridColors(); // Update colors immediately
        }
      }
    });
    setFocusable(true);
    requestFocusInWindow();
  }

  private void toggleFullScreen() {
    isFullScreen = !isFullScreen;
    frame.dispose(); // Dispose the frame to change its state

    if (isFullScreen) {
      frame.setUndecorated(true);
      graphicsDevice.setFullScreenWindow(frame);
    } else {
      frame.setUndecorated(false);
      graphicsDevice.setFullScreenWindow(null);
      frame.setSize(DEFAULT_WINDOW_WIDTH, DEFAULT_WINDOW_HEIGHT);
      frame.setLocationRelativeTo(null);
    }

    frame.setVisible(true);
    initializeGrid(); // Reinitialize grid for new window size
    requestFocusInWindow();
  }

  @Override
  public void paintComponent(Graphics g) {
    super.paintComponent(g);
    Graphics2D g2d = (Graphics2D) g;
    // Enable anti-aliasing for grayscale mode
    if (isGrayscale) {
      g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    } else {
      g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
    }

    // Recalculate grid size based on current window size
    int gridWidth = getWidth() / (isGrayscale ? SUB_PIXEL_SIZE : PIXEL_SIZE);
    int gridHeight = getHeight() / (isGrayscale ? SUB_PIXEL_SIZE : PIXEL_SIZE);
    gridWidth = Math.max(1, gridWidth);
    gridHeight = Math.max(1, gridHeight);

    // Resize pixelGrid for color mode if dimensions have changed
    if (!isGrayscale && (pixelGrid == null || pixelGrid.length != gridWidth || (pixelGrid.length > 0 && pixelGrid[0].length != gridHeight))) {
      pixelGrid = new Color[gridWidth][gridHeight];
      updateGridColors();
    }

    if (isGrayscale) {
      // Draw smooth grayscale gradient with pulsing effect
      for (int x = 0; x < gridWidth; x++) {
        for (int y = 0; y < gridHeight; y++) {
          double drawX = x * SUB_PIXEL_SIZE;
          double drawY = y * SUB_PIXEL_SIZE;
          Point.Double offset = getPulseOffset(drawX, drawY, time);
          g2d.setColor(getPsychedelicColor(drawX, drawY, time));
          g2d.fillRect((int) (drawX + offset.x), (int) (drawY + offset.y), SUB_PIXEL_SIZE, SUB_PIXEL_SIZE);
        }
      }
    } else {
      // Draw pixelated colors
      for (int x = 0; x < gridWidth; x++) {
        for (int y = 0; y < gridHeight; y++) {
          g2d.setColor(pixelGrid[x][y]);
          g2d.fillRect(x * PIXEL_SIZE, y * PIXEL_SIZE, PIXEL_SIZE, PIXEL_SIZE);
        }
      }
    }

    // Apply glitch effect
    applyGlitch(g2d);
  }

  @Override
  public void run() {
    // Animation loop
    while (true) {
      time += 0.1; // Increment time for animation
      // Recalculate grid size
      int gridWidth = getWidth() / (isGrayscale ? SUB_PIXEL_SIZE : PIXEL_SIZE);
      int gridHeight = getHeight() / (isGrayscale ? SUB_PIXEL_SIZE : PIXEL_SIZE);
      gridWidth = Math.max(1, gridWidth);
      gridHeight = Math.max(1, gridHeight);

      // Update colors
      synchronized (this) {
        if (!isGrayscale) {
          if (pixelGrid == null || pixelGrid.length != gridWidth || (pixelGrid.length > 0 && pixelGrid[0].length != gridHeight)) {
            pixelGrid = new Color[gridWidth][gridHeight];
          }
          updateGridColors(); // Update colors for color mode
        }
        // Glitch is applied in paintComponent for both modes
      }

      repaint(); // Request repaint
      try {
        Thread.sleep(DELAY_MS);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
  }

  public static void main(String[] args) {
    SwingUtilities.invokeLater(() -> {
      JFrame frame = new JFrame("Trippy Pixel Art");
      frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
      frame.setResizable(true); // Make window resizable
      frame.add(new TrippyPixelArt(frame));
      frame.pack();
      frame.setLocationRelativeTo(null);
      frame.setVisible(true);
    });
  }
}
