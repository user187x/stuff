package xxx;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListener;
import com.fazecast.jSerialComm.SerialPortEvent;
import com.formdev.flatlaf.FlatDarculaLaf;

import javax.swing.*;
import java.awt.*;
import java.nio.charset.StandardCharsets;

public class Dashboard extends JFrame {

    // --- UI Components ---
    private final JComboBox<String> portDropdown;
    private final JButton connectBtn;
    private final JTextField freqField;
    private final JButton setFreqBtn;
    private final JToggleButton pttBtn;
    private final JToggleButton rxBtn; // RX listen / mute toggle
    private final JTextField messageField;
    private final JButton sendMsgBtn;
    private final JTextArea logArea;
    private SerialPort activePort;

    // --- Audio Components ---
    private final JComboBox<String> pcMicCombo;
    private final JComboBox<String> radioMicCombo;
    private final JComboBox<String> pcSpeakerCombo;
    private final JComboBox<String> radioSpeakerCombo;
    private final AudioBridge audioBridge = new AudioBridge();
    private final AudioVisualizer visualizer = new AudioVisualizer();

    public Dashboard() {
        setTitle("KV4P HT V2.0 Control Dashboard");
        setSize(800, 880);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));

        // The visualizer watches whatever audio the bridge is routing.
        audioBridge.setSampleListener(visualizer);

        // --- WRAPPER FOR TOP CONTROLS ---
        JPanel controlWrapper = new JPanel();
        controlWrapper.setLayout(new BoxLayout(controlWrapper, BoxLayout.Y_AXIS));

        // --- TOP PANEL: Connection & Audio ---
        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
        topPanel.setBorder(BorderFactory.createTitledBorder("Hardware Configuration"));

        // Row 1: Serial Port
        JPanel serialPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        portDropdown = new JComboBox<>();
        refreshPorts();
        connectBtn = new JButton("Connect");
        connectBtn.addActionListener(e -> toggleConnection());
        JButton refreshBtn = new JButton("Refresh Ports");
        refreshBtn.addActionListener(e -> refreshPorts());
        serialPanel.add(new JLabel("Radio Serial Port:"));
        serialPanel.add(portDropdown);
        serialPanel.add(refreshBtn);
        serialPanel.add(connectBtn);
        topPanel.add(serialPanel);

        // Row 2: Microphones (Inputs)
        JPanel micPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        pcMicCombo = new JComboBox<>(AudioBridge.getAvailableInputs().toArray(new String[0]));
        radioMicCombo = new JComboBox<>(AudioBridge.getAvailableInputs().toArray(new String[0]));
        micPanel.add(new JLabel("PC Mic:"));
        micPanel.add(pcMicCombo);
        micPanel.add(Box.createHorizontalStrut(15));
        micPanel.add(new JLabel("Radio USB Mic (RX):"));
        micPanel.add(radioMicCombo);
        topPanel.add(micPanel);

        // Row 3: Speakers (Outputs)
        JPanel speakerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        pcSpeakerCombo = new JComboBox<>(AudioBridge.getAvailableOutputs().toArray(new String[0]));
        radioSpeakerCombo = new JComboBox<>(AudioBridge.getAvailableOutputs().toArray(new String[0]));
        speakerPanel.add(new JLabel("PC Speakers:"));
        speakerPanel.add(pcSpeakerCombo);
        speakerPanel.add(Box.createHorizontalStrut(15));
        speakerPanel.add(new JLabel("Radio USB Speaker (TX):"));
        speakerPanel.add(radioSpeakerCombo);
        topPanel.add(speakerPanel);

        controlWrapper.add(topPanel);

        // --- CENTER PANEL: Controls ---
        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // 1. Frequency Control
        JPanel freqPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        freqPanel.setBorder(BorderFactory.createTitledBorder("VHF Tuning"));
        freqField = new JTextField("162.400", 10);
        setFreqBtn = new JButton("Set Frequency");
        setFreqBtn.setEnabled(false);
        setFreqBtn.addActionListener(e -> setFrequency());
        freqPanel.add(new JLabel("Frequency (MHz):"));
        freqPanel.add(freqField);
        freqPanel.add(setFreqBtn);
        centerPanel.add(freqPanel);

        // 2. Voice Operation Control
        JPanel voicePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        voicePanel.setBorder(BorderFactory.createTitledBorder("Voice Operation"));

        // RX defaults to ON: audio is meant to be audible from start-up unless muted.
        rxBtn = new JToggleButton("RX: ON (Listening)");
        rxBtn.setSelected(true);
        rxBtn.setBackground(new Color(100, 200, 100)); // Green indicator
        rxBtn.setEnabled(false);
        rxBtn.addActionListener(e -> toggleRX());

        pttBtn = new JToggleButton("PTT: OFF");
        pttBtn.setBackground(Color.LIGHT_GRAY);
        pttBtn.setEnabled(false);
        pttBtn.addActionListener(e -> togglePTT());

        voicePanel.add(rxBtn);
        voicePanel.add(Box.createHorizontalStrut(10));
        voicePanel.add(pttBtn);
        centerPanel.add(voicePanel);

        // 3. Digital Messaging Control
        JPanel textPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        textPanel.setBorder(BorderFactory.createTitledBorder("Digital APRS / Text"));
        messageField = new JTextField(35);
        sendMsgBtn = new JButton("Send Message");
        sendMsgBtn.setEnabled(false);
        sendMsgBtn.addActionListener(e -> sendTextMessage());
        textPanel.add(messageField);
        textPanel.add(sendMsgBtn);
        centerPanel.add(textPanel);

        controlWrapper.add(centerPanel);
        add(controlWrapper, BorderLayout.NORTH);

        // --- CENTER: Visualizer (top) + Logs (below) ---
        JPanel scopePanel = new JPanel(new BorderLayout());
        scopePanel.setBorder(BorderFactory.createTitledBorder("Signal Monitor"));
        scopePanel.add(visualizer, BorderLayout.CENTER);

        logArea = new JTextArea(8, 50);
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Radio Logs & Reception"));

        JPanel monitorPanel = new JPanel(new BorderLayout(0, 8));
        monitorPanel.add(scopePanel, BorderLayout.NORTH);
        monitorPanel.add(scrollPane, BorderLayout.CENTER);
        add(monitorPanel, BorderLayout.CENTER);
    }

    // --- LOGIC METHODS ---

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                FlatDarculaLaf.setup();
            } catch (Exception e) {
                e.printStackTrace();
            }
            Dashboard dashboard = new Dashboard();
            dashboard.setLocationRelativeTo(null);
            dashboard.setVisible(true);
        });
    }

    private void refreshPorts() {
        portDropdown.removeAllItems();
        SerialPort[] ports = SerialPort.getCommPorts();
        for (SerialPort port : ports) {
            portDropdown.addItem(port.getSystemPortName());
        }
        if (ports.length == 0) {
            portDropdown.addItem("No ports found");
        }
    }

    private void toggleConnection() {
        if (activePort == null || !activePort.isOpen()) {
            String portName = (String) portDropdown.getSelectedItem();
            if (portName == null || portName.contains("No ports")) return;

            activePort = SerialPort.getCommPort(portName);
            activePort.setComPortParameters(115200, 8, 1, 0);

            if (activePort.openPort()) {
                connectBtn.setText("Disconnect");
                log("Connected to " + portName + " at 115200 baud.");
                enableControls(true);
                setupRadioListener();

                // Audio should be audible from start-up unless the user has muted RX.
                if (rxBtn.isSelected()) {
                    startRXRouting();
                } else {
                    log("Audio: RX is muted. Press RX to start listening.");
                }
            } else {
                log("ERROR: Could not open port " + portName + ". Check permissions (dialout).");
            }
        } else {
            // Disconnect & Cleanup
            activePort.removeDataListener();
            activePort.closePort();
            activePort = null;
            connectBtn.setText("Connect");
            log("Disconnected.");
            enableControls(false);

            // Reset PTT, but keep the user's RX preference for the next connection.
            pttBtn.setSelected(false);
            pttBtn.setText("PTT: OFF");
            pttBtn.setBackground(Color.LIGHT_GRAY);

            audioBridge.stopRouting();
        }
    }

    private void enableControls(boolean enable) {
        setFreqBtn.setEnabled(enable);
        pttBtn.setEnabled(enable);
        rxBtn.setEnabled(enable);
        sendMsgBtn.setEnabled(enable);
    }

    private void setFrequency() {
        String freq = freqField.getText().trim();
        String cmd = String.format("AT+DMOSETGROUP=0,%s,%s,0000,4,0000\r\n", freq, freq);
        sendSerialCommand(cmd);
        log("Tuning to " + freq + " MHz...");
    }

    private void toggleRX() {
        if (rxBtn.isSelected()) {
            rxBtn.setText("RX: ON (Listening)");
            rxBtn.setBackground(new Color(100, 200, 100)); // Green indicator
            startRXRouting();
        } else {
            rxBtn.setText("RX: OFF (Muted)");
            rxBtn.setBackground(Color.LIGHT_GRAY);
            audioBridge.stopRouting();
            log("Audio: RX Muted.");
        }
    }

    private void startRXRouting() {
        String radioMic = (String) radioMicCombo.getSelectedItem();
        String pcSpeaker = (String) pcSpeakerCombo.getSelectedItem();
        try {
            if (radioMic != null && pcSpeaker != null) {
                audioBridge.startRouting(radioMic, pcSpeaker);
                log("Audio: Routing Radio RX -> PC Speakers (" + radioMic + " -> " + pcSpeaker + ").");
            } else {
                log("Audio Error: Please select valid RX audio devices.");
            }
        } catch (Exception ex) {
            log("Audio Error: " + ex.getMessage());
        }
    }

    private void togglePTT() {
        String pcMic = (String) pcMicCombo.getSelectedItem();
        String radioSpeaker = (String) radioSpeakerCombo.getSelectedItem();

        if (pttBtn.isSelected()) {
            // Start Transmitting
            pttBtn.setText("PTT: ON (Transmitting)");
            pttBtn.setBackground(Color.RED);
            rxBtn.setEnabled(false); // Lock out RX while talking
            sendSerialCommand("TX_ON\r\n");

            try {
                if (pcMic != null && radioSpeaker != null) {
                    // startRouting() tears down RX first, so TX cannot stack on top of it.
                    audioBridge.startRouting(pcMic, radioSpeaker);
                    log("Audio: Transmitting PC Mic -> Radio...");
                } else {
                    log("Audio Error: Please select valid TX audio devices.");
                }
            } catch (Exception ex) {
                log("Audio Error: " + ex.getMessage());
            }

        } else {
            // Stop Transmitting
            pttBtn.setText("PTT: OFF");
            pttBtn.setBackground(Color.LIGHT_GRAY);
            rxBtn.setEnabled(true);
            sendSerialCommand("TX_OFF\r\n");

            audioBridge.stopRouting();

            // Auto-resume listening if RX was left on.
            if (rxBtn.isSelected()) {
                startRXRouting();
            } else {
                log("Audio: TX Ended. RX is currently muted.");
            }
        }
    }

    private void sendTextMessage() {
        String msg = messageField.getText().trim();
        if (!msg.isEmpty()) {
            sendSerialCommand("MSG=" + msg + "\r\n");
            log("Sent Digital Data: " + msg);
            messageField.setText("");
        }
    }

    private void sendSerialCommand(String cmd) {
        if (activePort != null && activePort.isOpen()) {
            byte[] bytes = cmd.getBytes(StandardCharsets.UTF_8);
            activePort.writeBytes(bytes, bytes.length);
        }
    }

    private void setupRadioListener() {
        activePort.addDataListener(new SerialPortDataListener() {
            @Override
            public int getListeningEvents() { return SerialPort.LISTENING_EVENT_DATA_AVAILABLE; }

            @Override
            public void serialEvent(SerialPortEvent event) {
                if (event.getEventType() != SerialPort.LISTENING_EVENT_DATA_AVAILABLE) return;

                byte[] newData = new byte[activePort.bytesAvailable()];
                int numRead = activePort.readBytes(newData, newData.length);
                if (numRead > 0) {
                    String response = new String(newData, StandardCharsets.UTF_8).trim();
                    if (!response.isEmpty()) {
                        SwingUtilities.invokeLater(() -> log("RX: " + response));
                    }
                }
            }
        });
    }

    private void log(String message) {
        logArea.append(message + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }
}