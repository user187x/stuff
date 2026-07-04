package xxx.com.mouse.hid;

import com.formdev.flatlaf.FlatDarculaLaf;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;

public class MouseController extends JFrame {

    private Process btProcess;
    private BufferedWriter btIn;

    // State tracking for relative motion
    private int lastX = -1;
    private int lastY = -1;

    // Configurable pointer speed matching your UI mockup
    private double pointerSpeed = 1.0;

    public MouseController() {
        initBluetoothProcess();
        setupUI();
    }

    private void initBluetoothProcess() {
        try {
            // Spawns the executable you built via Makefile. 
            // Note: Since bt_setup.c uses sudo, run this Java app from a terminal 
            // so you can enter your password if prompted.
            ProcessBuilder pb = new ProcessBuilder("/home/xxx/devbox/data/work/programming/java/Multiverse/swing/src/main/java/xxx/com/mouse/hid/btmouse");
            pb.redirectErrorStream(true);
            btProcess = pb.start();
            btIn = new BufferedWriter(new OutputStreamWriter(btProcess.getOutputStream()));

            // Clean shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                sendCommand("q");
                if (btProcess != null) btProcess.destroy();
            }));
        } catch (IOException e) {
            System.err.println("Could not start ./btmouse. Ensure it is compiled and in the same directory.");
            e.printStackTrace();
        }
    }

    private void sendCommand(String cmd) {
        if (btIn == null) return;
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
        setSize(400, 600);
        setLayout(new BorderLayout(10, 10));
        ((JPanel) getContentPane()).setBorder(new EmptyBorder(15, 15, 15, 15));

        // --- 1. Trackpad Area ---
        JPanel trackpad = new JPanel();
        trackpad.setBackground(new Color(45, 75, 105)); // Matches the dark blue from your mockup
        trackpad.setBorder(BorderFactory.createLineBorder(new Color(60, 100, 140), 2, true));
        trackpad.setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));

        // Label to show a mouse icon / instructions
        JLabel padLabel = new JLabel("🖱️ TRACKPAD AREA", SwingConstants.CENTER);
        padLabel.setForeground(new Color(255, 255, 255, 100));
        trackpad.setLayout(new BorderLayout());
        trackpad.add(padLabel, BorderLayout.CENTER);

        // Motion tracking
        trackpad.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                handleMotion(e);
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                handleMotion(e);
            }
        });

        // Reset relative coordinates when mouse enters so it doesn't jump
        trackpad.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                lastX = e.getX();
                lastY = e.getY();
            }

            // Trackpad tapping = left click
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) sendCommand("c l");
            }
        });

        // Wheel scrolling
        trackpad.addMouseWheelListener(e -> {
            int scrollAmount = e.getWheelRotation();
            // Invert if necessary based on natural scrolling preference
            sendCommand("s " + (-scrollAmount));
        });

        // --- 2. Left / Right Buttons ---
        JPanel buttonPanel = new JPanel(new GridLayout(1, 2, 15, 0));
        JButton leftBtn = createMouseButton("Left Click", "l");
        JButton rightBtn = createMouseButton("Right Click", "r");
        buttonPanel.add(leftBtn);
        buttonPanel.add(rightBtn);
        buttonPanel.setPreferredSize(new Dimension(getWidth(), 80));

        // --- 3. Settings Area (Speed Slider) ---
        JPanel settingsPanel = new JPanel(new BorderLayout());
        settingsPanel.setBorder(new EmptyBorder(10, 0, 0, 0));
        JLabel speedLabel = new JLabel("Pointer speed");
        JSlider speedSlider = new JSlider(1, 30, 10); // 0.1x to 3.0x
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
        if (lastX == -1 || lastY == -1) {
            lastX = e.getX();
            lastY = e.getY();
            return;
        }

        int dx = (int) ((e.getX() - lastX) * pointerSpeed);
        int dy = (int) ((e.getY() - lastY) * pointerSpeed);

        // Only send if there's actual mathematical movement
        if (dx != 0 || dy != 0) {
            // The C backend clamps between -127 and 127
            sendCommand(String.format("m %d %d", dx, dy));

            // Adjust lastX/Y based on what was actually consumed
            lastX += (int) (dx / pointerSpeed);
            lastY += (int) (dy / pointerSpeed);
        }
    }

    private JButton createMouseButton(String text, String btnCode) {
        JButton btn = new JButton(text);
        btn.setBackground(new Color(60, 120, 175));
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);

        // We use mouse press/release to allow click-and-drag behaviors
        btn.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                sendCommand("d " + btnCode);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                sendCommand("u " + btnCode);
            }
        });
        return btn;
    }

    public static void main(String[] args) {
        // Modernize the UI via FlatLaf Dark
        try {
            FlatDarculaLaf.setup();
        } catch (Exception ex) {
            System.err.println("Failed to initialize FlatDarkLaf");
        }

        SwingUtilities.invokeLater(() -> {
            MouseController gui = new MouseController();
            gui.setLocationRelativeTo(null);
            gui.setVisible(true);
        });
    }
}
