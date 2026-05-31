package xxx.com.console;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * A utility class for displaying various text-based animations in the console. It uses a
 * ScheduledExecutorService for efficient, non-blocking animations.
 */
public class Spinner {

  private ScheduledExecutorService executor;
  private String currentMessage = "";
  private Runnable currentTask; // Store the current running task
  private final char[] spinnerChars = {'\\', '|', '/'};

  // --- Task for Spinner Animation ---
  private class SpinnerTask implements Runnable {
    private int index = 0;
    private final String[] frames;

    public SpinnerTask(String[] frames) {
      this.frames = frames;
    }

    @Override
    public void run() {
      System.out.print(currentMessage + " " + frames[index] + "\r");
      index = (index + 1) % frames.length;
    }
  }

  // --- Task for Dot Animation ---
  private class DotTask implements Runnable {
    private int dotCount = 0;

    @Override
    public void run() {

      // The modulo 4 creates the cycle 0, 1, 2, 3.
      String dots = "...".substring(0, dotCount);
      String output = String.format("%s %-3s", currentMessage, dots);
      System.out.print(output + "\r");
      dotCount = (dotCount + 1) % 4;
    }
  }

  // --- Task for Echo Animation ---
  private class EchoTask implements Runnable {
    private final int width;
    private int position = 0;
    private int direction = 1; // 1 for forward, -1 for backward

    public EchoTask(int width) {
      this.width = Math.max(2, width);
    }

    @Override
    public void run() {
      if (position >= width - 2) {
        direction = -1;
      } else if (position <= 0) {
        direction = 1;
      }
      StringBuilder sb = new StringBuilder(width);
      sb.append(" ".repeat(width));
      String arrow = (direction == 1) ? ">>" : "<<";
      sb.replace(position, position + 2, arrow);
      System.out.print(currentMessage + " [" + sb.toString() + "]\r");
      position += direction;
    }
  }

  // --- Task for Progress Bar Animation ---
  private class ProgressBarTask implements Runnable {
    private final int width;
    private final boolean isManual;
    private volatile int progress = 0;

    public ProgressBarTask(int width, boolean isManual) {
      this.width = Math.max(1, width);
      this.isManual = isManual;
    }

    @Override
    public void run() {
      if (!isManual) {
        progress = (progress + 1) % 101;
      }
      int filledLength = (width * progress) / 100;
      String filled = "█".repeat(filledLength);
      String empty = "-".repeat(width - filledLength);
      String bar = String.format("%s [%s%s] %d%%", currentMessage, filled, empty, progress);
      System.out.print(bar + "\r");
    }

    public void incrementProgress(int step) {
      this.progress = Math.min(100, this.progress + step);
    }

    public void setExactProgress(double percent) {
      this.progress = Math.max(0, Math.min(100, (int) percent));
    }
  }

  // --- NEW: Marquee Task ---
  private class MarqueeTask implements Runnable {
    private final String text;
    private final int width;
    private final boolean bounce;
    private final String paddedText;
    private int position = 0;
    private int direction = 1;

    public MarqueeTask(String text, int width, boolean bounce) {
      // Ensure the text is not null and the width is reasonable
      this.text = (text == null) ? "" : text;
      this.width = Math.max(this.text.length(), width);
      this.bounce = bounce;

      // Create a padded string to make the scrolling logic simpler.
      // The padding is the size of the viewing window (width).
      String padding = " ".repeat(this.width);
      this.paddedText = padding + this.text + padding;
    }

    @Override
    public void run() {
      // Extract the portion of the text to be displayed in the current frame.
      String frame = paddedText.substring(position, position + width);
      System.out.print(currentMessage + " [" + frame + "]\r");

      // Move the position for the next frame.
      position += direction;

      // Check the boundaries and decide what to do next.
      if (bounce) {
        // If bouncing, reverse direction at the ends.
        if (position <= 0 || position >= paddedText.length() - width) {
          direction *= -1;
        }
      } else {
        // If wrapping, reset the position to the beginning when it goes too far.
        if (position >= text.length() + width) {
          position = 0;
        }
      }
    }
  }

  // --- Control Methods ---
  public synchronized void startDots(String message) {
    startAnimation(message, new DotTask(), 500);
  }

  public synchronized void startDots2(String message) {
    startAnimation(
        message, new SpinnerTask(new String[] {"⣾", "⣽", "⣻", "⢿", "⡿", "⣟", "⣯", "⣷"}), 80);
  }

  public synchronized void startSkull(String message) {
    String[] skullFrames =
        new String[] {"(ò_ó)", "(ò_o)", "(ò_ )", "(ò  )", "( o )", "(  ò)", "( _ó)", "(o_ó)"};
    startAnimation(message, new SpinnerTask(skullFrames), 120);
  }

  public synchronized void startDots12(String message) {
    startAnimation(
        message,
        new SpinnerTask(new String[] {"⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏"}),
        80);
  }

  public synchronized void startLine(String message) {
    startAnimation(message, new SpinnerTask(new String[] {"-", "\\", "|", "/"}), 130);
  }

  public synchronized void startArrow(String message) {
    startAnimation(
        message, new SpinnerTask(new String[] {"←", "↖", "↑", "↗", "→", "↘", "↓", "↙"}), 80);
  }

  public synchronized void startBouncingBar(String message) {
    startAnimation(
        message,
        new SpinnerTask(
            new String[] {
              "[    ]", "[   =]", "[  ==]", "[ ===]", "[====]", "[=== ]", "[==  ]", "[=   ]"
            }),
        80);
  }

  public synchronized void startBoxBounce(String message) {
    startAnimation(message, new SpinnerTask(new String[] {"▖", "▘", "▝", "▗"}), 100);
  }

  public synchronized void startTriangle(String message) {
    startAnimation(message, new SpinnerTask(new String[] {"◢", "◣", "◤", "◥"}), 120);
  }

  public synchronized void startArc(String message) {
    startAnimation(message, new SpinnerTask(new String[] {"◜", "◝", "◞", "◟"}), 100);
  }

  public synchronized void startCircle(String message) {
    startAnimation(message, new SpinnerTask(new String[] {"◐", "◓", "◑", "◒"}), 100);
  }

  public synchronized void startEcho(String message, int width) {
    startAnimation(message, new EchoTask(width), 80);
  }

  public synchronized void startProgressBar(String message, int width, boolean isManual) {
    startAnimation(message, new ProgressBarTask(width, isManual), 50);
  }

  // --- NEW: Control Method for Marquee ---
  /**
   * Starts a marquee (scrolling text) animation.
   *
   * @param message The static message to display before the animation.
   * @param marqueeText The text that will be scrolling.
   * @param width The width of the scrolling text area.
   * @param speed The update interval in milliseconds (e.g., 100).
   * @param bounce If true, the text will move back and forth. If false, it will wrap around.
   */
  public synchronized void startMarquee(
      String message, String marqueeText, int width, int speed, boolean bounce) {
    startAnimation(message, new MarqueeTask(marqueeText, width, bounce), speed);
  }

  private void startAnimation(String message, Runnable task, long period) {

    if (executor != null && !executor.isShutdown()) {
      System.err.println("An animation is already running.");
      return;
    }

    this.currentMessage = message;
    this.currentTask = task; // Keep a reference to the task
    executor = Executors.newSingleThreadScheduledExecutor();
    executor.scheduleAtFixedRate(task, 0, period, TimeUnit.MILLISECONDS);
  }

  public synchronized void progress(int step) {
    if (currentTask instanceof ProgressBarTask) {
      ((ProgressBarTask) currentTask).incrementProgress(step);
    }
  }

  public synchronized void setProgress(double percent) {
    if (currentTask instanceof ProgressBarTask) {
      ((ProgressBarTask) currentTask).setExactProgress(percent);
    }
  }

  public synchronized void stop(String finalMessage) {
    if (executor == null || executor.isShutdown()) {
      return;
    }
    executor.shutdown();

    try {
      if (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
        executor.shutdownNow();
      }
    } catch (InterruptedException e) {
      executor.shutdownNow();
      Thread.currentThread().interrupt();
    }

    this.currentTask = null; // Clear the task reference
    String cleanup = " ".repeat(100);
    System.out.print("\r" + cleanup + "\r");
    System.out.println(finalMessage);
  }

  public static void main(String[] args) throws InterruptedException {

    Spinner animator = new Spinner();

    System.out.println("\n--- Demonstrating Dots ---");
    animator.startDots("Connecting to server");
    Thread.sleep(4000);
    animator.stop("Connection successful. ✅");

    Thread.sleep(1000);

      System.out.println("\n--- Demonstrating Skulls ---");
      animator.startSkull("Connecting to server");
      Thread.sleep(4000);
      animator.stop("Connection successful. ✅");

      Thread.sleep(1000);

    System.out.println("\n--- Demonstrating Dots2 ---");
    animator.startDots2("Loading data...");
    Thread.sleep(4000);
    animator.stop("Data loaded. ✅");

    Thread.sleep(1000);

    System.out.println("\n--- Demonstrating Line ---");
    animator.startLine("Fetching resources...");
    Thread.sleep(4000);
    animator.stop("Resources fetched. ✅");

    Thread.sleep(1000);

    System.out.println("\n--- Demonstrating Arrow ---");
    animator.startArrow("Searching for devices...");
    Thread.sleep(4000);
    animator.stop("Devices found. ✅");

    Thread.sleep(1000);

    System.out.println("\n--- Demonstrating Bouncing Bar ---");
    animator.startBouncingBar("Copying files...");
    Thread.sleep(4000);
    animator.stop("Files copied. ✅");

    Thread.sleep(1000);

    System.out.println("\n--- Demonstrating Box Bounce ---");
    animator.startBoxBounce("Installing packages...");
    Thread.sleep(4000);
    animator.stop("Packages installed. ✅");

    Thread.sleep(1000);

    System.out.println("\n--- Demonstrating Triangle ---");
    animator.startTriangle("Building project...");
    Thread.sleep(4000);
    animator.stop("Project built. ✅");

    Thread.sleep(1000);

    System.out.println("\n--- Demonstrating Arc ---");
    animator.startArc("Deploying application...");
    Thread.sleep(4000);
    animator.stop("Application deployed. ✅");

    Thread.sleep(1000);

    System.out.println("\n--- Demonstrating Circle ---");
    animator.startCircle("Finalizing setup...");
    Thread.sleep(4000);
    animator.stop("Setup finalized. ✅");

    Thread.sleep(1000);

    System.out.println("\n--- Demonstrating Echo ---");
    animator.startEcho("Pinging host...", 20);
    Thread.sleep(5000);
    animator.stop("Host responded. ✅");

    Thread.sleep(1000);

    // --- Automatic Progress Bar ---
    System.out.println("\n--- Demonstrating Automatic Progress Bar ---");
    animator.startProgressBar("Downloading update...", 30, false);
    Thread.sleep(4000);
    animator.stop("Update downloaded. ✅");

    Thread.sleep(1000);

    // --- NEW: Manual Progress Bar ---
    System.out.println("\n--- Demonstrating Manual Progress Bar ---");
    animator.startProgressBar("Processing files...", 40, true);

    // Simulate work and update progress manually
    Thread.sleep(1500);
    System.out.print("\r"); // Clear line for intermediate status
    System.out.println("Processed files...");
    animator.progress(25); // Increment by 25%

    Thread.sleep(2000);
    System.out.print("\r");
    System.out.println("Processed more files...");
    animator.progress(50); // Increment by another 50%

    Thread.sleep(1800);
    System.out.print("\r");
    System.out.println("Processed more files...");
    animator.progress(25); // Set directly to 75%

    Thread.sleep(1000);
    System.out.print("\r");
    System.out.println("Finishing up...");
    animator.setProgress(100); // Set directly to 100%
    Thread.sleep(500);

    animator.stop("All files processed. ✅");

    Thread.sleep(1000);

    // --- NEW: Demonstrating Marquee (Wrapping) ---
    System.out.println("\n--- Demonstrating Marquee (Wrapping) ---");
    animator.startMarquee(
        "Reading status:",
        "This is a long status message that will wrap around the screen...",
        40,
        100,
        false);
    Thread.sleep(10000);
    animator.stop("Status read. ✅");

    Thread.sleep(1000);

    // --- Demonstrating Marquee (Bouncing) ---
    System.out.println("\n--- Demonstrating Marquee (Bouncing) ---");
    animator.startMarquee("Scanning:", "Scanning for devices...", 30, 80, true);
    Thread.sleep(8000);
    animator.stop("Scan complete. ✅");

    // --- Demonstrating file loaded Spinners ---
    SpinLoader.display();
  }
}
