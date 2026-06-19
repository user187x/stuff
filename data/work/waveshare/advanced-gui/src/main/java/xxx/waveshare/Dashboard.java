package xxx.waveshare;

import com.fazecast.jSerialComm.SerialPort;
import com.formdev.flatlaf.FlatDarculaLaf;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.io.File;

public class Dashboard extends JFrame {

    // --- UI Components ---
    private JComboBox<String> portDropdown;
    private JButton connectBtn;

    // Hardware & Protocol Controls
    private JSpinner channelSpinner;
    private JButton setChannelBtn;
    private JToggleButton scanBtn;
    private JToggleButton beaconBtn;

    // Messaging & File Transfer
    private JTextField messageField;
    private JButton sendMsgBtn;
    private JButton sendFileBtn;

    // Output & Visualization
    private JTextArea logArea;
    private AudioVisualizer visualizer;
    private JButton toggleVisBtn;

    // State Variables
    private boolean isConnected = false;

    public Dashboard() {
        setTitle("LoRa SX1262 Command Center");
        setSize(1000, 750);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));
        ((JPanel) getContentPane()).setBorder(new EmptyBorder(10, 10, 10, 10));

        initComponents();
        layoutComponents();

        // Disable controls until connected
        enableRadioControls(false);
    }

    private void initComponents() {
        // Top: Connection
        portDropdown = new JComboBox<>();
        refreshPorts();
        connectBtn = new JButton("Connect");
        connectBtn.addActionListener(e -> toggleConnection());

        // Right Sidebar: Radio Controls
        SpinnerModel channelModel = new SpinnerNumberModel(18, 0, 80, 1); // 18 is roughly 915MHz
        channelSpinner = new JSpinner(channelModel);

        setChannelBtn = new JButton("Tune Channel");
        scanBtn = new JToggleButton("🔍 Start Scan");
        beaconBtn = new JToggleButton("📡 Beacon (Off)");

        // Center: Visualizer & Logs
        visualizer = new AudioVisualizer();
        toggleVisBtn = new JButton("Toggle View (OSC/FFT)");
        toggleVisBtn.addActionListener(e -> {
            visualizer.toggleMode();
            log("Visualizer mode: " + visualizer.getMode());
        });

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 13));
        logArea.setBackground(new Color(43, 43, 43));
        logArea.setForeground(new Color(169, 183, 198));

        // Bottom: Messaging
        messageField = new JTextField();
        sendMsgBtn = new JButton("✉ Send");
        sendFileBtn = new JButton("📎 Send File");

        // Action Listeners for new capabilities
        setChannelBtn.addActionListener(e -> tuneChannel());
        scanBtn.addActionListener(e -> toggleScan());
        beaconBtn.addActionListener(e -> toggleBeacon());
        sendMsgBtn.addActionListener(e -> sendMessage());
        sendFileBtn.addActionListener(e -> sendFile());
        messageField.addActionListener(e -> sendMessage()); // Allow Enter key
    }

    private void layoutComponents() {
        // --- TOP PANEL (Connection) ---
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.setBorder(createCleanBorder("Hardware Connection"));
        topPanel.add(new JLabel("Port:"));
        topPanel.add(portDropdown);
        JButton refreshBtn = new JButton("↻");
        refreshBtn.addActionListener(e -> refreshPorts());
        topPanel.add(refreshBtn);
        topPanel.add(connectBtn);
        add(topPanel, BorderLayout.NORTH);

        // --- RIGHT PANEL (Radio Sidebar) ---
        JPanel sidebar = new JPanel();
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setBorder(createCleanBorder("Radio Controls"));
        sidebar.setPreferredSize(new Dimension(220, 0));

        JPanel tunePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        tunePanel.add(new JLabel("CH (0-80):"));
        tunePanel.add(channelSpinner);
        sidebar.add(tunePanel);

        setChannelBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        sidebar.add(setChannelBtn);
        sidebar.add(Box.createVerticalStrut(20));

        scanBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        scanBtn.setMaximumSize(new Dimension(180, 35));
        sidebar.add(scanBtn);
        sidebar.add(Box.createVerticalStrut(10));

        beaconBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        beaconBtn.setMaximumSize(new Dimension(180, 35));
        sidebar.add(beaconBtn);
        sidebar.add(Box.createVerticalStrut(30));

        toggleVisBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        sidebar.add(toggleVisBtn);

        add(sidebar, BorderLayout.EAST);

        // --- CENTER PANEL (Visualizer + Chat/Logs) ---
        JPanel centerPanel = new JPanel(new BorderLayout(0, 10));

        JPanel visWrapper = new JPanel(new BorderLayout());
        visWrapper.setBorder(createCleanBorder("Signal Monitor"));
        visWrapper.add(visualizer, BorderLayout.CENTER);
        centerPanel.add(visWrapper, BorderLayout.NORTH);

        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(createCleanBorder("Network Activity"));
        centerPanel.add(logScroll, BorderLayout.CENTER);

        add(centerPanel, BorderLayout.CENTER);

        // --- BOTTOM PANEL (Messaging) ---
        JPanel bottomPanel = new JPanel(new BorderLayout(10, 0));
        bottomPanel.setBorder(createCleanBorder("Transmit"));
        bottomPanel.add(sendFileBtn, BorderLayout.WEST);
        bottomPanel.add(messageField, BorderLayout.CENTER);
        bottomPanel.add(sendMsgBtn, BorderLayout.EAST);
        add(bottomPanel, BorderLayout.SOUTH);
    }

    private TitledBorder createCleanBorder(String title) {
        TitledBorder border = BorderFactory.createTitledBorder(title);
        border.setTitleColor(new Color(120, 170, 220)); // Soft modern blue
        return border;
    }

    // --- Action Implementations ---

    private void refreshPorts() {
        portDropdown.removeAllItems();
        for (SerialPort port : SerialPort.getCommPorts()) {
            portDropdown.addItem(port.getSystemPortName());
        }
    }

    private void toggleConnection() {
        if (!isConnected) {
            String portName = (String) portDropdown.getSelectedItem();
            if (portName == null) return;

            // TODO: Wire up DeviceManager, LoRaHardwareController, LoRaProtocol here
            isConnected = true;
            connectBtn.setText("Disconnect");
            log("Connected to " + portName + " at 115200 baud.");
            enableRadioControls(true);
        } else {
            // TODO: Cleanup hardware connection here
            isConnected = false;
            connectBtn.setText("Connect");
            log("Disconnected.");
            enableRadioControls(false);

            scanBtn.setSelected(false);
            scanBtn.setText("🔍 Start Scan");
            beaconBtn.setSelected(false);
            beaconBtn.setText("📡 Beacon (Off)");
        }
    }

    private void enableRadioControls(boolean enable) {
        setChannelBtn.setEnabled(enable);
        scanBtn.setEnabled(enable);
        beaconBtn.setEnabled(enable);
        sendMsgBtn.setEnabled(enable);
        sendFileBtn.setEnabled(enable);
        messageField.setEnabled(enable);
        channelSpinner.setEnabled(enable);
    }

    private void tuneChannel() {
        int channel = (Integer) channelSpinner.getValue();
        // TODO: Call hardwareController.setChannel(channel);
        log("=> Tuning radio to Channel " + channel + "...");
    }

    private void toggleScan() {
        if (scanBtn.isSelected()) {
            scanBtn.setText("🛑 Stop Scan");
            scanBtn.setForeground(Color.ORANGE);
            log("=> Started background channel scanning...");
            // TODO: Call hardwareController.startScan(0, 80);
        } else {
            scanBtn.setText("🔍 Start Scan");
            scanBtn.setForeground(UIManager.getColor("Button.foreground"));
            log("=> Scan halted.");
            // TODO: Call hardwareController.stopScan();
        }
    }

    private void toggleBeacon() {
        if (beaconBtn.isSelected()) {
            beaconBtn.setText("📡 Beacon (Active)");
            beaconBtn.setForeground(new Color(80, 235, 90)); // Green
            log("=> Discovery beacon activated (Ping every 15s).");
            // TODO: Call protocol.startBeacon(15000);
        } else {
            beaconBtn.setText("📡 Beacon (Off)");
            beaconBtn.setForeground(UIManager.getColor("Button.foreground"));
            log("=> Discovery beacon deactivated.");
            // TODO: Call protocol.stopBeacon();
        }
    }

    private void sendMessage() {
        String msg = messageField.getText().trim();
        if (!msg.isEmpty()) {
            // TODO: Call protocol.sendTextMessage(msg);
            log("[ME]: " + msg);
            messageField.setText("");
        }
    }

    private void sendFile() {
        JFileChooser fileChooser = new JFileChooser();
        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            log("=> Preparing to send file: " + selectedFile.getName());
            // TODO: Read bytes and call protocol.sendFile(selectedFile.getName(), fileBytes);
        }
    }

    public void log(String message) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    // --- Main ---
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                // Initialize modern dark theme
                FlatDarculaLaf.setup();

                // Enhance specific UI defaults for a sleeker look
                UIManager.put("Button.arc", 8);
                UIManager.put("Component.arc", 8);
                UIManager.put("TextComponent.arc", 8);
                UIManager.put("ScrollBar.thumbArc", 999);
                UIManager.put("ScrollBar.thumbInsets", new Insets(2, 2, 2, 2));
            } catch (Exception e) {
                e.printStackTrace();
            }

            Dashboard dashboard = new Dashboard();
            dashboard.setLocationRelativeTo(null);
            dashboard.setVisible(true);
        });
    }
}