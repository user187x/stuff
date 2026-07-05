package xxx.com.mouse.hid;

import com.formdev.flatlaf.FlatDarculaLaf;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MouseController extends JFrame {

    private Process btProcess;
    private BufferedWriter btIn;
    private Thread statusThread;

    // UI Components that need toggling
    private JPanel trackpad;
    private JLabel padLabel;
    private JLabel statusLabel;
    private JButton leftBtn;
    private JButton rightBtn;

    // State tracking
    private volatile boolean isConnected = false;
    private int lastX = -1;
    private int lastY = -1;
    private double pointerSpeed = 1.0;

    public MouseController() {
        setupUI();
        initConnection();
    }

    private void initConnection() {
        File fifo = new File("/tmp/btmouse.fifo");

        // 1. Attach to existing or start a new one
        if (!fifo.exists()) {
            System.out.println("No running daemon found. Launching a new instance...");
            startDaemon();
            // Pause briefly to let the C program write the mkfifo() to disk
            try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
        } else {
            System.out.println("Attached to existing background btmouse process!");
        }

        // 2. Open the Named Pipe for writing
        try {
            // Using true to append so we don't overwrite the pipe stream
            btIn = new BufferedWriter(new FileWriter("/tmp/btmouse.fifo", true));
        } catch (IOException e) {
            System.err.println("Could not establish IPC with /tmp/btmouse.fifo");
            updateStatus("Error: IPC Hook Failed \uD83D\uDD34", false);
        }

        // 3. Monitor the daemon's state file
        startStatusMonitor();

        // 4. Clean shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            // Only kill the process if WE started it. Don't kill an external background daemon.
            if (btProcess != null) {
                sendCommand("q");
                btProcess.destroy();
            }
        }));
    }

    private void startDaemon() {
        try {
            // Adjust the path to your compiled binary as needed
            ProcessBuilder pb = new ProcessBuilder("/home/xxx/devbox/data/work/programming/java/Multiverse/swing/src/main/java/xxx/com/mouse/hid/btmouse");
            pb.redirectErrorStream(true);
            btProcess = pb.start();

            // Consume output to prevent the process from hanging on a full buffer
            new Thread(() -> {
                try (BufferedReader r = new BufferedReader(new InputStreamReader(btProcess.getInputStream()))) {
                    while (r.readLine() != null) {} // Discard, we read from .status now
                } catch (IOException ignored) {}
            }).start();

        } catch (IOException e) {
            updateStatus("Error: Could not start ./btmouse \uD83D\uDD34", false);
            e.printStackTrace();
        }
    }

    private void startStatusMonitor() {
        statusThread = new Thread(() -> {
            Path statusFile = Paths.get("/tmp/btmouse.status");
            while (true) {
                try {
                    if (Files.exists(statusFile)) {
                        String status = new String(Files.readAllBytes(statusFile)).trim();
                        if ("CONNECTED".equals(status)) {
                            updateStatus("Status: Connected \uD83D\uDFE2", true);
                        } else {
                            updateStatus("Status: Waiting for host pairing... \uD83D\uDFE1", false);
                        }
                    } else {
                        updateStatus("Status: Daemon offline \uD83D\uDD34", false);
                    }
                    Thread.sleep(500); // Poll status twice a second
                } catch (Exception e) {
                    try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
                }
            }
        });
        statusThread.setDaemon(true);
        statusThread.start();
    }

    private void updateStatus(String message, boolean connected) {
        this.isConnected = connected;
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText(message);

            if (connected) {
                trackpad.setBackground(new Color(45, 75, 105)); // Active blue
                padLabel.setText("🖱️ TRACKPAD ACTIVE");
                padLabel.setForeground(Color.WHITE);
                leftBtn.setEnabled(true);
                rightBtn.setEnabled(true);
                trackpad.setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
            } else {
                trackpad.setBackground(new Color(30, 35, 40)); // Disabled dark gray
                padLabel.setText("⏳ WAITING FOR PAIRING...");
                padLabel.setForeground(new Color(255, 255, 255, 100));
                leftBtn.setEnabled(false);
                rightBtn.setEnabled(false);
                trackpad.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            }
        });
    }

    private void sendCommand(String cmd) {
        if (btIn == null || !isConnected) return;
        try {
            btIn.write(cmd + "\n");
            btIn.flush();
        } catch (IOException e) {
            System.err.println("Failed to send command: " + cmd);
        }
    }

    private void setupUI() {
        setTitle("Bluetooth Mouse Controller");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(400, 650);
        setLayout(new BorderLayout(10, 10));
        ((JPanel) getContentPane()).setBorder(new EmptyBorder(15, 15, 15, 15));

        // --- 0. Top Status Bar ---
        statusLabel = new JLabel("Status: Initializing... \uD83D\uDFE1", SwingConstants.CENTER);
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD, 14f));
        statusLabel.setBorder(new EmptyBorder(0, 0, 10, 0));
        add(statusLabel, BorderLayout.NORTH);

        // --- 1. Trackpad Area ---
        trackpad = new JPanel();
        trackpad.setBorder(BorderFactory.createLineBorder(new Color(60, 100, 140), 2, true));

        padLabel = new JLabel("", SwingConstants.CENTER);
        trackpad.setLayout(new BorderLayout());
        trackpad.add(padLabel, BorderLayout.CENTER);

        trackpad.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) { handleMotion(e); }
            @Override
            public void mouseDragged(MouseEvent e) { handleMotion(e); }
        });

        trackpad.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                lastX = e.getX();
                lastY = e.getY();
            }
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) sendCommand("c l");
            }
        });

        trackpad.addMouseWheelListener(e -> sendCommand("s " + (-e.getWheelRotation())));

        // --- 2. Left / Right Buttons ---
        JPanel buttonPanel = new JPanel(new GridLayout(1, 2, 15, 0));
        leftBtn = createMouseButton("Left Click", "l");
        rightBtn = createMouseButton("Right Click", "r");
        buttonPanel.add(leftBtn);
        buttonPanel.add(rightBtn);
        buttonPanel.setPreferredSize(new Dimension(getWidth(), 80));

        // --- 3. Settings Area ---
        JPanel settingsPanel = new JPanel(new BorderLayout());
        settingsPanel.setBorder(new EmptyBorder(10, 0, 0, 0));
        JLabel speedLabel = new JLabel("Pointer speed");
        JSlider speedSlider = new JSlider(1, 30, 10);
        speedSlider.addChangeListener(e -> pointerSpeed = speedSlider.getValue() / 10.0);

        settingsPanel.add(speedLabel, BorderLayout.NORTH);
        settingsPanel.add(speedSlider, BorderLayout.CENTER);

        // --- Assemble ---
        JPanel bottomContainer = new JPanel(new BorderLayout(0, 15));
        bottomContainer.add(buttonPanel, BorderLayout.NORTH);
        bottomContainer.add(settingsPanel, BorderLayout.SOUTH);

        add(trackpad, BorderLayout.CENTER);
        add(bottomContainer, BorderLayout.SOUTH);
    }

    private void handleMotion(MouseEvent e) {
        if (!isConnected || lastX == -1 || lastY == -1) {
            lastX = e.getX();
            lastY = e.getY();
            return;
        }

        int dx = (int) ((e.getX() - lastX) * pointerSpeed);
        int dy = (int) ((e.getY() - lastY) * pointerSpeed);

        if (dx != 0 || dy != 0) {
            sendCommand(String.format("m %d %d", dx, dy));
            lastX += (int) (dx / pointerSpeed);
            lastY += (int) (dy / pointerSpeed);
        }
    }

    private JButton createMouseButton(String text, String btnCode) {
        JButton btn = new JButton(text);
        btn.setBackground(new Color(60, 120, 175));
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);

        btn.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) { if (isConnected) sendCommand("d " + btnCode); }
            @Override
            public void mouseReleased(MouseEvent e) { if (isConnected) sendCommand("u " + btnCode); }
        });
        return btn;
    }

    public static void main(String[] args) {
        try {
            FlatDarculaLaf.setup();
        } catch (Exception ex) {
            System.err.println("Failed to initialize FlatDarculaLaf");
        }

        SwingUtilities.invokeLater(() -> {
            MouseController gui = new MouseController();
            gui.setLocationRelativeTo(null);
            gui.setVisible(true);
        });
    }
}