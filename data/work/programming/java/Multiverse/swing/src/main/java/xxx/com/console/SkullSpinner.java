package xxx.com.console;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * A utility for displaying a multi-line, 3D rotating ASCII skull animation in the console.
 * This class is self-contained and uses ANSI escape codes for in-place animation.
 */
public class SkullSpinner {

    private ScheduledExecutorService executor;
    private int currentFrame = 0;
    private boolean isFirstRun = true;
    // Each frame of the skull animation is 6 lines tall.
    private final int FRAME_HEIGHT = 6;

    // ASCII art frames for the rotating skull. Padded to ensure stable width.
    private static final String[] FRAMES = {
            // Frame 1
            "      .--.        \n" +
                    "     /  _ \\       \n" +
                    "    |  (_) |      \n" +
                    "    |      |      \n" +
                    "     \\    /       \n" +
                    "      `--'        ",
            // Frame 2
            "    .----.        \n" +
                    "   / o_  \\       \n" +
                    "  | ( )_  |       \n" +
                    "  |  _  ) |       \n" +
                    "   \\ `)/ /        \n" +
                    "    `--'          ",
            // Frame 3
            "  .------.        \n" +
                    " /  o o  \\       \n" +
                    "|    _    |       \n" +
                    "|  `---'  |       \n" +
                    " \\        /      \n" +
                    "  `------'        ",
            // Frame 4
            " .--------.       \n" +
                    "/   o  o   \\      \n" +
                    "|     _    |      \n" +
                    "|   `---'  |      \n" +
                    " \\          /     \n" +
                    "  `--------'      ",
            // Frame 5
            "        .------.  \n" +
                    "       /  o o  \\ \n" +
                    "      |    _    | \n" +
                    "      |  `---'  | \n" +
                    "       \\        / \n" +
                    "        `------'  ",
            // Frame 6
            "        .----.    \n" +
                    "       /  _o \\   \n" +
                    "      |  _( ) |   \n" +
                    "      | (  _  |   \n" +
                    "       \\ \\(` /    \n" +
                    "        `--'      ",
            // Frame 7
            "        .--.      \n" +
                    "       / _  \\     \n" +
                    "      | (_)  |    \n" +
                    "      |      |    \n" +
                    "       \\    /     \n" +
                    "        `--'      ",
            // Frame 8
            "        .----.    \n" +
                    "       /  _o \\   \n" +
                    "      |  _( ) |   \n" +
                    "      | (  _  |   \n" +
                    "       \\ \\(` /    \n" +
                    "        `--'      "
    };

    /**
     * Prints a single frame of the animation, moving the cursor to overwrite the previous frame.
     * @param message The message to display below the animation.
     */
    private void printFrame(String message) {
        if (!isFirstRun) {
            // ANSI escape code to move cursor up by the height of the frame + one line for the message.
            System.out.print("\033[" + (FRAME_HEIGHT + 1) + "A");
        }
        // Print the current frame, followed by the message.
        System.out.println(FRAMES[currentFrame]);
        System.out.print(message + "\r");
        System.out.flush();

        currentFrame = (currentFrame + 1) % FRAMES.length;
        isFirstRun = false;
    }

    /**
     * Starts the skull animation.
     * @param message A message to display while the animation is running.
     */
    public synchronized void start(String message) {
        if (executor != null && !executor.isShutdown()) {
            return; // Animation is already running.
        }
        isFirstRun = true;
        currentFrame = 0;
        executor = Executors.newSingleThreadScheduledExecutor();
        Runnable task = () -> printFrame(message);
        // The period (150ms) controls the speed of the rotation.
        executor.scheduleAtFixedRate(task, 0, 150, TimeUnit.MILLISECONDS);
    }

    /**
     * Stops the animation, cleans up the console area, and prints a final message.
     * @param finalMessage The message to print after the animation stops.
     */
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

        // Move cursor up to the start of the animation area.
        System.out.print("\033[" + (FRAME_HEIGHT + 1) + "A");
        // Clear the animation area by overwriting it with blank lines.
        for (int i = 0; i < FRAME_HEIGHT + 1; i++) {
            System.out.print("\r" + " ".repeat(100) + "\n");
        }
        // Move cursor back to the start position.
        System.out.print("\033[" + (FRAME_HEIGHT + 1) + "A");

        if (finalMessage != null && !finalMessage.isEmpty()) {
            System.out.print(finalMessage);
            System.out.flush();
        }
    }

    /**
     * A simple main method to demonstrate the SkullSpinner.
     * This allows the class to be run standalone for testing the animation.
     * @param args Command line arguments (not used).
     * @throws InterruptedException if the thread is interrupted while sleeping.
     */
    public static void main(String[] args) throws InterruptedException {
        SkullSpinner spinner = new SkullSpinner();

        // Create a blank "canvas" for the animation to run in. This prevents
        // the animation from overwriting the command prompt or other text.
        for (int i = 0; i < spinner.FRAME_HEIGHT + 1; i++) {
            System.out.println();
        }

        // Move the cursor back to the top of the newly created blank area.
        System.out.print("\033[" + (spinner.FRAME_HEIGHT + 1) + "A");

        // Start the animation, which will now draw inside our canvas.
        spinner.start("Thinking...");

        // Let the animation run for 5 seconds.
        Thread.sleep(5000);

        // Stop the animation. This will clear the canvas and print a final message.
        spinner.stop("Done! ✅");

        // Add a final newline to move the cursor below the animation area for a clean exit.
        System.out.println();
    }
}

