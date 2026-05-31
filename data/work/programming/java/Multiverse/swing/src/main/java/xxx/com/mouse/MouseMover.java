package xxx.com.mouse;

import com.github.kwhat.jnativehook.mouse.NativeMouseMotionListener;
import xxx.com.console.Spinner; // Import the Spinner class

import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;
import com.github.kwhat.jnativehook.mouse.NativeMouseEvent;
import com.github.kwhat.jnativehook.mouse.NativeMouseInputListener;
import java.awt.AWTException;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Robot;
import java.awt.event.MouseEvent;
import java.util.Random;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JPanel;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

public class MouseMover {

  // --- Configuration Constants ---
  public static final int MOVE_INTERVAL_SECONDS = 5;
  public static final int INACTIVITY_TIMEOUT_SECONDS = 10;
  public static final int RIPPLE_RADIUS = 50;
  public static final int RIPPLE_DURATION_MS = 500;
  public static final int MOUSE_MOVE_DURATION_MS = 700;
  public static final int SCROLL_CHANCE = 4;

  // --- State and Utility Variables ---
  private static volatile boolean userIsActive = true;
  private static Timer inactivityTimer;
  private static final Spinner consoleSpinner = new Spinner();
  private static volatile boolean isRobotMovingMouse = false;
  private static final Random random = new Random();
  private static Rectangle actionZone; // Make actionZone accessible globally
  private static ScheduledExecutorService countdownExecutor;

  public static void main(String[] args) {
    System.out.println("Welcome to Smart Mouse Mover!");
    System.out.println("Press the ESC or Q key at any time to safely exit the application.");
    System.out.println("Press and hold CTRL to see the current action zone.");
    System.out.println("---------------------------------------------------------");
    System.out.println("Please choose an operating mode:");
    System.out.println("  1. Automated (runs on all screens)");
    System.out.println("  2. Selection Zone (confine actions to a selected area)");
    System.out.print("Enter your choice (1 or 2): ");

    registerShutdownHook();

    try (Scanner scanner = new Scanner(System.in)) {
      String choice = scanner.nextLine();
      if ("2".equals(choice)) {
        System.out.println("\nPlease LEFT-CLICK and drag to select the action zone.");
        System.out.println("Release the mouse button to confirm the zone.");
        actionZone = ZoneSelector.selectZone();
        if (actionZone == null || actionZone.width < 10 || actionZone.height < 10) {
          System.out.println("Zone selection cancelled or invalid (too small). Exiting.");
          System.exit(0);
        }

        GraphicsDevice[] screens = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();
        int monitorIndex = 1;

        for (GraphicsDevice screen : screens) {
          Rectangle monitorBounds = screen.getDefaultConfiguration().getBounds();
          if (monitorBounds.intersects(actionZone)) {
            System.out.println("✅ Selection zone is primarily on Monitor " + monitorIndex + " (" + screen.getIDstring() + ")");
            break;
          }
          monitorIndex++;
        }

        System.out.println("Zone selected: X=" + actionZone.getX() + " Y=" +  actionZone.getY());

      } else {
        System.out.println("Automated mode selected. The mover will run across all screens.");
        actionZone = getTotalScreenBounds();
      }
    }

    startMover();
  }

  private static void registerShutdownHook() {
    Logger logger = Logger.getLogger(GlobalScreen.class.getPackage().getName());
    logger.setLevel(Level.OFF);
    logger.setUseParentHandlers(false);

    try {
      GlobalScreen.registerNativeHook();
    } catch (NativeHookException ex) {
      System.err.println("There was a problem registering the native hook.");
      System.err.println(ex.getMessage());
      System.exit(1);
    }

    GlobalScreen.addNativeKeyListener(new EmergencyKeyListener());
  }


  public static void startMover() {
    UserInputListener listener = new UserInputListener();
    GlobalScreen.addNativeKeyListener(listener);
    GlobalScreen.addNativeMouseMotionListener(listener);

    setupInactivityTimer();
    startPausedCountdown();

    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      System.out.print("\u001B[?25h"); // Show cursor on shutdown
      consoleSpinner.stop("MouseMover stopped. Goodbye! 👋");
      if (countdownExecutor != null && !countdownExecutor.isShutdown()) {
        countdownExecutor.shutdownNow();
      }
      try {
        if (GlobalScreen.isNativeHookRegistered()) {
          GlobalScreen.unregisterNativeHook();
        }
      } catch (NativeHookException ex) {
        ex.printStackTrace();
      }
    }));

    try {
      Robot robot = new Robot();
      while (true) {
        if (!userIsActive) {
          if (random.nextInt(SCROLL_CHANCE) == 0) {
            boolean scrollUp = random.nextBoolean();
            int scrollAmount = (random.nextInt(5) + 3) * (scrollUp ? -1 : 1);
            robot.mouseWheel(scrollAmount);
            Point currentPos = MouseInfo.getPointerInfo().getLocation();
            SwingUtilities.invokeLater(() -> createScrollEffect(currentPos.x, currentPos.y, scrollUp));
          } else {
            Point startPoint = MouseInfo.getPointerInfo().getLocation();
            int endX = random.nextInt(actionZone.width) + actionZone.x;
            int endY = random.nextInt(actionZone.height) + actionZone.y;
            Point finalPoint = moveMouseFluidly(robot, startPoint.x, startPoint.y, endX, endY, MOUSE_MOVE_DURATION_MS);
            SwingUtilities.invokeLater(() -> createRippleEffect(finalPoint.x, finalPoint.y));
          }
          Thread.sleep(MOVE_INTERVAL_SECONDS * 1000);
        } else {
          Thread.sleep(1000);
        }
      }
    } catch (AWTException e) {
      e.printStackTrace();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private static Point moveMouseFluidly(Robot robot, int startX, int startY, int endX, int endY, int durationMs) {
    final int STEPS = 100;
    final int DELAY = durationMs / STEPS;
    isRobotMovingMouse = true;
    boolean useCurvedPath = random.nextBoolean();
    double amplitude = 0, frequency = 1;
    if (useCurvedPath) {
      amplitude = random.nextDouble() * 150 + 50;
      frequency = random.nextDouble() * 2 + 1;
    }
    double deltaX = endX - startX, deltaY = endY - startY;
    double perpX = -deltaY, perpY = deltaX;
    double perpLength = Math.sqrt(perpX * perpX + perpY * perpY);
    if (perpLength > 0) {
      perpX /= perpLength;
      perpY /= perpLength;
    }
    int lastX = startX, lastY = startY;
    for (int i = 0; i <= STEPS; i++) {
      float progress = (float) i / STEPS;
      double linearX = startX + deltaX * progress;
      double linearY = startY + deltaY * progress;
      double curveOffset = amplitude * Math.sin(progress * frequency * Math.PI);
      int currentX = (int) (linearX + perpX * curveOffset);
      int currentY = (int) (linearY + perpY * curveOffset);

      currentX = Math.max(actionZone.x, Math.min(actionZone.x + actionZone.width - 1, currentX));
      currentY = Math.max(actionZone.y, Math.min(actionZone.y + actionZone.height - 1, currentY));

      robot.mouseMove(currentX, currentY);
      lastX = currentX;
      lastY = currentY;
      try {
        Thread.sleep(DELAY);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      }
    }
    isRobotMovingMouse = false;
    return new Point(lastX, lastY);
  }

  private static Rectangle getTotalScreenBounds() {
    Rectangle totalBounds = new Rectangle();
    for (GraphicsDevice device : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
      totalBounds = totalBounds.union(device.getDefaultConfiguration().getBounds());
    }
    return totalBounds;
  }

  private static void setupInactivityTimer() {
    inactivityTimer = new Timer(INACTIVITY_TIMEOUT_SECONDS * 1000, e -> {
      userIsActive = false;
      showActiveSpinner();
    });
    inactivityTimer.setRepeats(false);
    inactivityTimer.start();
  }

  public static void onUserActivity() {
    startPausedCountdown();
    userIsActive = true;
    inactivityTimer.restart();
  }

  private static void startPausedCountdown() {
    consoleSpinner.stop("");
    if (countdownExecutor != null && !countdownExecutor.isShutdown()) {
      countdownExecutor.shutdownNow();
    }

    System.out.print("\u001B[?25l"); // Hide cursor

    countdownExecutor = Executors.newSingleThreadScheduledExecutor();

    final AtomicInteger countdown = new AtomicInteger(INACTIVITY_TIMEOUT_SECONDS);
    countdownExecutor.scheduleAtFixedRate(() -> {

      System.out.print("\rActivity Detected : Resuming in " + countdown.getAndDecrement() + " seconds");

      if (countdown.get() < 0) {
        countdownExecutor.shutdown();
      }
    }, 0, 1, TimeUnit.SECONDS);
  }

  private static void showActiveSpinner() {
    if (countdownExecutor != null && !countdownExecutor.isShutdown()) {
      countdownExecutor.shutdownNow();
    }

    System.out.print("\u001B[?25h"); // Show cursor
    consoleSpinner.stop("");

    System.out.print("\u001B[?25l"); // Hide cursor
    consoleSpinner.startDots2("MouseMover Running ");
  }

  private static void createRippleEffect(int x, int y) {
    final JWindow effectWindow = new JWindow();
    final RipplePanel ripplePanel = new RipplePanel();
    effectWindow.add(ripplePanel);
    effectWindow.setAlwaysOnTop(true);
    effectWindow.setBackground(new Color(0, 0, 0, 0));
    int windowSize = RIPPLE_RADIUS * 2;
    effectWindow.setBounds(x - RIPPLE_RADIUS, y - RIPPLE_RADIUS, windowSize, windowSize);
    effectWindow.setVisible(true);
    ripplePanel.startAnimation(() -> {
      effectWindow.setVisible(false);
      effectWindow.dispose();
    });
  }

  private static void createScrollEffect(int x, int y, boolean isScrollingUp) {
    final JWindow effectWindow = new JWindow();
    final ScrollAnimationPanel scrollPanel = new ScrollAnimationPanel(isScrollingUp);
    effectWindow.add(scrollPanel);
    effectWindow.setAlwaysOnTop(true);
    effectWindow.setBackground(new Color(0, 0, 0, 0));
    effectWindow.setBounds(x - 25, y - 50, 50, 100);
    effectWindow.setVisible(true);
    scrollPanel.startAnimation(() -> {
      effectWindow.setVisible(false);
      effectWindow.dispose();
    });
  }

  private static void createZoneOutlineEffect() {
    final JWindow effectWindow = new JWindow();
    final ZoneOutlinePanel outlinePanel = new ZoneOutlinePanel();
    effectWindow.add(outlinePanel);
    effectWindow.setAlwaysOnTop(true);
    effectWindow.setBackground(new Color(0, 0, 0, 0));
    effectWindow.setBounds(getTotalScreenBounds());
    effectWindow.setVisible(true);
    outlinePanel.startAnimation(() -> {
      effectWindow.setVisible(false);
      effectWindow.dispose();
    });
  }

  static class UserInputListener implements NativeKeyListener, NativeMouseMotionListener {
    @Override
    public void nativeKeyPressed(NativeKeyEvent e) {
      if (e.getKeyCode() != NativeKeyEvent.VC_CONTROL && e.getKeyCode() != NativeKeyEvent.VC_CONTROL &&
          e.getKeyCode() != NativeKeyEvent.VC_ESCAPE && e.getKeyCode() != NativeKeyEvent.VC_Q) {
        onUserActivity();
      }
    }

    @Override public void nativeMouseMoved(NativeMouseEvent e) { if (!isRobotMovingMouse) onUserActivity(); }
    @Override public void nativeKeyReleased(NativeKeyEvent e) {}
    @Override public void nativeKeyTyped(NativeKeyEvent e) {}
    @Override public void nativeMouseDragged(NativeMouseEvent e) { if (!isRobotMovingMouse) onUserActivity(); }
  }

  static class EmergencyKeyListener implements NativeKeyListener {
    private JWindow outlineWindow;

    @Override
    public void nativeKeyPressed(NativeKeyEvent e) {
      if (e.getKeyCode() == NativeKeyEvent.VC_ESCAPE || e.getKeyCode() == NativeKeyEvent.VC_Q) {
        System.out.println("\nShutdown key pressed. Terminating application...");
        System.exit(0);
      }
      if (e.getKeyCode() == NativeKeyEvent.VC_CONTROL || e.getKeyCode() == NativeKeyEvent.VC_CONTROL) {
        if (outlineWindow == null || !outlineWindow.isVisible()) {
          createZoneOutlineEffect();
        }
      }
    }
  }

  static class RipplePanel extends JPanel {
    private Timer timer;
    private long startTime;
    private final int animationDuration = RIPPLE_DURATION_MS;
    public RipplePanel() { setOpaque(false); }
    public void startAnimation(Runnable onFinished) {
      startTime = System.currentTimeMillis();
      timer = new Timer(16, e -> {
        if (System.currentTimeMillis() - startTime > animationDuration) {
          timer.stop();
          onFinished.run();
        } else {
          repaint();
        }
      });
      timer.start();
    }
    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      Graphics2D g2d = (Graphics2D) g.create();
      g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      long elapsedTime = System.currentTimeMillis() - startTime;
      float progress = (float) elapsedTime / animationDuration;
      if (progress > 1.0f) progress = 1.0f;
      int currentRadius = (int) (RIPPLE_RADIUS * progress);
      float alpha = 1.0f - progress;
      g2d.setColor(new Color(0.1f, 0.5f, 1.0f, alpha > 0 ? alpha : 0));
      g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
      int centerX = getWidth() / 2;
      int centerY = getHeight() / 2;
      g2d.setStroke(new BasicStroke(2));
      g2d.drawOval(centerX - currentRadius, centerY - currentRadius, currentRadius * 2, currentRadius * 2);
      int secondRadius = (int) (currentRadius * 0.66);
      g2d.drawOval(centerX - secondRadius, centerY - secondRadius, secondRadius * 2, secondRadius * 2);
      int thirdRadius = (int) (currentRadius * 0.33);
      g2d.drawOval(centerX - thirdRadius, centerY - thirdRadius, thirdRadius * 2, thirdRadius * 2);
      g2d.dispose();
    }
  }

  static class ScrollAnimationPanel extends JPanel {
    private Timer timer;
    private long startTime;
    private final boolean isScrollingUp;
    private final int animationDuration = 600;

    public ScrollAnimationPanel(boolean isScrollingUp) {
      this.isScrollingUp = isScrollingUp;
      setOpaque(false);
    }
    public void startAnimation(Runnable onFinished) {
      startTime = System.currentTimeMillis();
      timer = new Timer(16, e -> {
        if (System.currentTimeMillis() - startTime > animationDuration) {
          timer.stop();
          onFinished.run();
        } else {
          repaint();
        }
      });
      timer.start();
    }
    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      Graphics2D g2d = (Graphics2D) g.create();
      g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2d.setStroke(new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      long elapsedTime = System.currentTimeMillis() - startTime;
      float progress = (float) elapsedTime / animationDuration;
      if (progress > 1.0f) progress = 1.0f;
      float alpha = 1.0f - progress;
      g2d.setColor(new Color(0.2f, 0.8f, 0.2f, alpha > 0 ? alpha : 0));
      int centerX = getWidth() / 2;
      int startY = isScrollingUp ? getHeight() : 0;
      int endY = isScrollingUp ? 0 : getHeight();
      int currentY = (int) (startY + (endY - startY) * progress);
      for (int i = 0; i < 3; i++) {
        int yOffset = i * 15 * (isScrollingUp ? -1 : 1);
        int yPos = currentY + yOffset;
        if (isScrollingUp) {
          g2d.drawLine(centerX - 10, yPos + 10, centerX, yPos);
          g2d.drawLine(centerX, yPos, centerX + 10, yPos + 10);
        } else {
          g2d.drawLine(centerX - 10, yPos - 10, centerX, yPos);
          g2d.drawLine(centerX, yPos, centerX + 10, yPos - 10);
        }
      }
      g2d.dispose();
    }
  }

  static class ZoneSelector {
    private final CountDownLatch latch = new CountDownLatch(1);
    private Rectangle selection;

    public static Rectangle selectZone() {
      ZoneSelector selector = new ZoneSelector();
      SwingUtilities.invokeLater(selector::show);
      try {
        selector.latch.await();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return null;
      }
      return selector.selection;
    }

    private void show() {
      JWindow window = new JWindow();
      window.setBounds(getTotalScreenBounds());
      window.setAlwaysOnTop(true);
      window.setBackground(new Color(0, 0, 0, 1));
      window.setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
      SelectionPanel panel = new SelectionPanel(this, window);
      window.add(panel);
      window.setVisible(true);
    }

    void setSelection(Rectangle rect) {
      this.selection = rect;
      latch.countDown();
    }
  }

  static class SelectionPanel extends JPanel implements NativeMouseInputListener {
    private Point startPoint;
    private Rectangle currentSelection;
    private final ZoneSelector parentSelector;
    private final JWindow parentWindow;

    public SelectionPanel(ZoneSelector selector, JWindow window) {
      this.parentSelector = selector;
      this.parentWindow = window;
      setOpaque(false);
      GlobalScreen.addNativeMouseListener(this);
      GlobalScreen.addNativeMouseMotionListener(this);
    }

    @Override
    public void nativeMousePressed(NativeMouseEvent e) {
      if (e.getButton() == MouseEvent.BUTTON1) {
        startPoint = e.getPoint();
        currentSelection = new Rectangle(startPoint);
        repaint();
      }
    }

    @Override
    public void nativeMouseDragged(NativeMouseEvent e) {
      if (startPoint != null) {
        Point currentPoint = e.getPoint();
        currentSelection.setBounds(
            Math.min(startPoint.x, currentPoint.x),
            Math.min(startPoint.y, currentPoint.y),
            Math.abs(startPoint.x - currentPoint.x),
            Math.abs(startPoint.y - currentPoint.y)
        );
        repaint();
      }
    }

    @Override
    public void nativeMouseReleased(NativeMouseEvent e) {
      if (startPoint != null && e.getButton() == MouseEvent.BUTTON1) {
        SwingUtilities.invokeLater(() -> {
          GlobalScreen.removeNativeMouseListener(this);
          GlobalScreen.removeNativeMouseMotionListener(this);
          parentWindow.dispose();
          parentSelector.setSelection(currentSelection);
        });
      }
    }

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      if (currentSelection != null) {
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setColor(new Color(0, 120, 215, 64));
        g2d.fillRect(currentSelection.x, currentSelection.y, currentSelection.width, currentSelection.height);
        g2d.setColor(new Color(0, 120, 215));
        g2d.setStroke(new BasicStroke(1));
        g2d.drawRect(currentSelection.x, currentSelection.y, currentSelection.width, currentSelection.height);
        g2d.dispose();
      }
    }

    @Override public void nativeMouseClicked(NativeMouseEvent e) {}
    @Override public void nativeMouseMoved(NativeMouseEvent e) {}
  }

  static class ZoneOutlinePanel extends JPanel {
    private Timer timer;
    private long startTime;
    private final int animationDuration = 1000; // 1 second duration

    public ZoneOutlinePanel() {
      setOpaque(false);
    }

    public void startAnimation(Runnable onFinished) {
      startTime = System.currentTimeMillis();
      timer = new Timer(16, e -> {
        if (System.currentTimeMillis() - startTime > animationDuration) {
          timer.stop();
          onFinished.run();
        } else {
          repaint();
        }
      });
      timer.start();
    }

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      Graphics2D g2d = (Graphics2D) g.create();
      g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

      long elapsedTime = System.currentTimeMillis() - startTime;
      float progress = (float) elapsedTime / animationDuration;
      if (progress > 1.0f) progress = 1.0f;

      float alpha = 1.0f - progress;

      for (int i = 6; i >= 0; i -= 2) {
        float strokeAlpha = alpha * (1.0f - (float)i / 8.0f);
        if (strokeAlpha < 0) strokeAlpha = 0;
        g2d.setColor(new Color(1.0f, 0.2f, 0.2f, strokeAlpha));
        g2d.setStroke(new BasicStroke(i));
        g2d.drawRect(actionZone.x, actionZone.y, actionZone.width, actionZone.height);
      }
      g2d.dispose();
    }
  }
}
