package xxx.com.image.generators;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.*;
import java.util.List;

public class TrippyMazeGenerator extends JPanel implements Runnable {
  private static final int CELL_SIZE = 10; // Target size of each cell
  private static final int DEFAULT_WINDOW_WIDTH = 640;
  private static final int DEFAULT_WINDOW_HEIGHT = 360;
  private static final int DELAY_MS = 50; // Animation speed for color shifts
  private static final int REGEN_INTERVAL_MS = 2000; // Time between maze regenerations
  private static final double HUE_SPEED = 0.02; // Speed of hue shift
  private static final double WAVE_SPEED = 0.1; // Speed of wave distortion

  private int[][] mazeGrid; // 1 = wall, 0 = path
  private Random random;
  private boolean isFullScreen;
  private GraphicsDevice graphicsDevice;
  private DisplayMode originalDisplayMode;
  private JFrame frame;
  private double time; // Tracks animation time for effects
  private long lastRegenTime;

  public TrippyMazeGenerator(JFrame frame) {
    this.frame = frame;
    setPreferredSize(new Dimension(DEFAULT_WINDOW_WIDTH, DEFAULT_WINDOW_HEIGHT));
    random = new Random();
    isFullScreen = false;
    time = 0;
    lastRegenTime = 0;
    graphicsDevice = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
    originalDisplayMode = graphicsDevice.getDisplayMode();
    generateMaze();
    setupKeyListener();
    setupResizeListener();
    // Start animation thread
    new Thread(this).start();
  }

  private void generateMaze() {
    // Calculate grid size based on current window size
    int gridWidth = Math.max(3, (getWidth() / CELL_SIZE) | 1); // Ensure odd and at least 3
    int gridHeight = Math.max(3, (getHeight() / CELL_SIZE) | 1); // Ensure odd and at least 3

    mazeGrid = new int[gridWidth][gridHeight];
    // Initialize all cells as walls
    for (int x = 0; x < gridWidth; x++) {
      Arrays.fill(mazeGrid[x], 1);
    }

    // Start carving from (1,1)
    carveMaze(1, 1);

    // Ensure borders are walls
    for (int x = 0; x < gridWidth; x++) {
      mazeGrid[x][0] = 1;
      mazeGrid[x][gridHeight - 1] = 1;
    }
    for (int y = 0; y < gridHeight; y++) {
      mazeGrid[0][y] = 1;
      mazeGrid[gridWidth - 1][y] = 1;
    }
  }

  private void carveMaze(int cx, int cy) {
    mazeGrid[cx][cy] = 0; // Mark as path

    // Directions: up, right, down, left
    int[][] directions = {{-1, 0}, {0, 1}, {1, 0}, {0, -1}};
    List<int[]> dirList = Arrays.asList(directions);
    Collections.shuffle(dirList, random);

    for (int[] dir : dirList) {
      int nx = cx + dir[0] * 2;
      int ny = cy + dir[1] * 2;
      if (nx > 0
          && nx < mazeGrid.length - 1
          && ny > 0
          && ny < mazeGrid[0].length - 1
          && mazeGrid[nx][ny] == 1) {
        mazeGrid[cx + dir[0]][cy + dir[1]] = 0; // Remove wall
        carveMaze(nx, ny);
      }
    }
  }

  private Color getTrippyColor(int x, int y, double t, boolean isWall) {
    double value = (Math.sin(x * 0.1 + t * WAVE_SPEED) + Math.cos(y * 0.1 + t * WAVE_SPEED)) * 0.5;
    value = (value + t * HUE_SPEED) % 1.0;
    if (value < 0) value += 1.0;

    if (isWall) {
      // Darker colors for walls
      return Color.getHSBColor((float) value, 0.8f, 0.3f);
    } else {
      // Brighter colors for paths
      return Color.getHSBColor((float) value, 0.6f, 0.7f);
    }
  }

  private void setupKeyListener() {
    // Add key listener for full-screen toggle
    addKeyListener(
        new KeyAdapter() {
          @Override
          public void keyPressed(KeyEvent e) {
            if (e.getKeyCode() == KeyEvent.VK_F) {
              toggleFullScreen();
            }
          }
        });
    setFocusable(true);
    requestFocusInWindow();
  }

  private void setupResizeListener() {
    // Regenerate maze on window resize
    frame.addComponentListener(
        new ComponentAdapter() {
          @Override
          public void componentResized(ComponentEvent e) {
            SwingUtilities.invokeLater(
                () -> {
                  generateMaze();
                  repaint();
                });
          }
        });
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
    // Regenerate maze after size change
    SwingUtilities.invokeLater(
        () -> {
          generateMaze();
          repaint();
        });
    requestFocusInWindow();
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    if (mazeGrid == null) {
      generateMaze(); // Ensure maze exists
      return;
    }

    int gridWidth = mazeGrid.length;
    int gridHeight = mazeGrid[0].length;

    // Calculate cell size to exactly fill the window
    int cellWidth = getWidth() / gridWidth;
    int cellHeight = getHeight() / gridHeight;
    // Distribute remaining pixels to ensure full coverage
    int extraWidth = getWidth() - (cellWidth * gridWidth);
    int extraHeight = getHeight() - (cellHeight * gridHeight);

    for (int x = 0; x < gridWidth; x++) {
      for (int y = 0; y < gridHeight; y++) {
        boolean isWall = mazeGrid[x][y] == 1;
        g.setColor(getTrippyColor(x, y, time, isWall));
        // Add extra pixels to last row/column to fill window
        int drawWidth = cellWidth + (x == gridWidth - 1 ? extraWidth : 0);
        int drawHeight = cellHeight + (y == gridHeight - 1 ? extraHeight : 0);
        g.fillRect(x * cellWidth, y * cellHeight, drawWidth, drawHeight);
      }
    }
  }

  @Override
  public void run() {
    // Animation loop
    while (true) {
      time += 0.1; // Increment time for color shifts

      long currentTime = System.currentTimeMillis();
      if (currentTime - lastRegenTime > REGEN_INTERVAL_MS) {
        SwingUtilities.invokeLater(
            () -> {
              generateMaze(); // Regenerate maze periodically
              lastRegenTime = currentTime;
              repaint();
            });
      }

      repaint(); // Request repaint for color updates

      try {
        Thread.sleep(DELAY_MS);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
  }

  public static void main(String[] args) {
    SwingUtilities.invokeLater(
        () -> {
          JFrame frame = new JFrame("Trippy Maze Generator");
          frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
          frame.setResizable(true); // Make window resizable
          TrippyMazeGenerator panel = new TrippyMazeGenerator(frame);
          frame.add(panel);
          frame.pack();
          frame.setLocationRelativeTo(null);
          frame.setVisible(true);
        });
  }
}
