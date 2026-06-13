package xxx.alpha;

import com.fazecast.jSerialComm.SerialPort;

import javax.sound.sampled.LineUnavailableException;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class App {

    private static MainWindow mainWindow;
    private static AudioManager audioManager;
    private static SerialManager serialManager;
    private static final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();

    public static void main(String[] args) {
        // Enforce a darker, system-agnostic Look and Feel suitable for the Dracula/Nord aesthetic
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (ClassNotFoundException | InstantiationException |
                 IllegalAccessException | UnsupportedLookAndFeelException e) {
            System.err.println("Could not set custom Look and Feel, falling back to default.");
        }

        // Initialize Subsystems
        audioManager = new AudioManager();
        serialManager = new SerialManager();

        try {
            System.out.println("Initializing Audio Subsystem...");
            audioManager.initAudio();
        } catch (LineUnavailableException e) {
            System.err.println("CRITICAL: Desktop audio lines unavailable. Ensure pulse/pipewire is running.");
            e.printStackTrace();
            System.exit(1);
        }

        // Auto-detect the ESP32 TTY interface
        String targetPort = autodetectSerialPort();
        if (targetPort == null) {
            System.err.println("CRITICAL: No suitable serial device found. Is the KV4P-HT plugged in?");
            System.err.println("Ensure your user is in the 'dialout' group: sudo usermod -a -G dialout $USER");
            System.exit(1);
        }

        System.out.println("Connecting to hardware on " + targetPort + "...");
        if (!serialManager.connect(targetPort)) {
            System.err.println("CRITICAL: Failed to open serial port " + targetPort);
            System.exit(1);
        }

        // Launch UI on the Event Dispatch Thread
        SwingUtilities.invokeLater(() -> {
            mainWindow = new MainWindow();
            mainWindow.setLocationRelativeTo(null); // Center on screen
            mainWindow.setVisible(true);
        });

        // Add Shutdown Hook for clean resource deallocation
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nInitiating graceful shutdown...");
            ioExecutor.shutdownNow();
            serialManager.close();
            System.out.println("Hardware disconnected. Goodbye.");
        }));

        // Start the background IO / DSP loop
        startIoLoop();
    }

    /**
     * Scans available serial ports and returns the first likely candidate for the radio.
     * Prioritizes standard Linux paths for CH340, CP2102, and CDC-ACM devices.
     */
    private static String autodetectSerialPort() {
        SerialPort[] availablePorts = SerialPort.getCommPorts();
        for (SerialPort port : availablePorts) {
            String sysPortName = port.getSystemPortName();
            if (sysPortName.contains("ttyUSB") || sysPortName.contains("ttyACM")) {
                return port.getSystemPortPath();
            }
        }
        return null;
    }

    /**
     * Dedicated loop for capturing mic data when PTT is active, or updating the
     * visualizer when receiving audio data from the serial manager.
     */
    private static void startIoLoop() {
        ioExecutor.submit(() -> {
            // Buffer size matched to 44.1kHz / 22kHz sampling
            int bufferSize = 1024;

            while (!Thread.currentThread().isInterrupted()) {
                try {
                    // 1. Check if MainWindow indicates PTT is pressed
                    // (Assuming you expose a boolean `isPttActive()` in MainWindow)
                    if (mainWindow != null && mainWindow.isPttActive()) {
                        byte[] micBuffer = audioManager.captureAudio(bufferSize);

                        // Pass to serial manager to encode into KISS frames & transmit
                        serialManager.transmit(micBuffer);

                        // Give the visualizer immediate feedback of local mic
                        SwingUtilities.invokeLater(() -> mainWindow.updateVisualizer(micBuffer));
                    } else {
                        // 2. If RX, the SerialManager's event listener is pushing data to AudioManager.
                        // We could poll a buffer here to feed the visualizer.
                        // For demonstration, simulating a small thread yield to prevent CPU pegging.
                        Thread.sleep(10);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    System.err.println("IO Loop Error: " + e.getMessage());
                }
            }
        });
    }
}