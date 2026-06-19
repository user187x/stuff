package xxx.beta;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListener;
import com.fazecast.jSerialComm.SerialPortEvent;
import com.formdev.flatlaf.FlatDarculaLaf;
import java.awt.*;
import java.nio.charset.StandardCharsets;
import javax.swing.*;
import xxx.beta.audio.AudioBridge;
import xxx.beta.visualizer.AudioVisualizer;

public class Dashboard extends JFrame {

 // --- Serial Components ---
 private final JComboBox<String> portDropdown;
 private final JButton connectBtn;
 private SerialPort activePort;

 // --- Frequency Components ---
 private final JTextField freqField;
 private final JSpinner ghzSpinner, mhzSpinner, hzSpinner;
 private final JSlider fineTuneSlider;
 private final JButton setFreqBtn;
 private final JButton scanBtn;
 private boolean isUpdatingFreq = false; // prevents recursive loop during sync

 // --- Voice/Message Components ---
 private final JToggleButton pttBtn;
 private final JToggleButton rxBtn;
 private final JTextField messageField;
 private final JButton sendMsgBtn;
 private final JTextArea logArea;

 // --- Audio Components ---
 private final JComboBox<String> pcMicCombo;
 private final JComboBox<String> radioMicCombo;
 private final JComboBox<String> pcSpeakerCombo;
 private final JComboBox<String> radioSpeakerCombo;

 // Checkboxes were missing from class fields but called in logic
 private final JCheckBox pcMicEnable;
 private final JCheckBox radioMicEnable;
 private final JCheckBox pcSpeakerEnable;
 private final JCheckBox radioSpeakerEnable;

 private final AudioBridge audioBridge = new AudioBridge();
 private final AudioVisualizer visualizer = new AudioVisualizer();

 public Dashboard() {
  setTitle("KV4P HT V2.0 Control Dashboard");
  setSize(850, 950);
  setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
  setLayout(new BorderLayout(10, 10));

  audioBridge.setSampleListener(visualizer);

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

  // Row 2: Microphones (Inputs) - Consolidated
  JPanel micPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
  pcMicCombo = new JComboBox<>(AudioBridge.getAvailableInputs().toArray(new String[0]));
  radioMicCombo = new JComboBox<>(AudioBridge.getAvailableInputs().toArray(new String[0]));
  pcMicEnable = new JCheckBox("En", true);
  radioMicEnable = new JCheckBox("En", true);

  micPanel.add(new JLabel("PC Mic:"));
  micPanel.add(pcMicCombo);
  micPanel.add(pcMicEnable);
  micPanel.add(Box.createHorizontalStrut(15));
  micPanel.add(new JLabel("Radio USB Mic (RX):"));
  micPanel.add(radioMicCombo);
  micPanel.add(radioMicEnable);
  topPanel.add(micPanel);

  // Row 3: Speakers (Outputs) - Consolidated
  JPanel speakerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
  pcSpeakerCombo = new JComboBox<>(AudioBridge.getAvailableOutputs().toArray(new String[0]));
  radioSpeakerCombo = new JComboBox<>(AudioBridge.getAvailableOutputs().toArray(new String[0]));
  pcSpeakerEnable = new JCheckBox("En", true);
  radioSpeakerEnable = new JCheckBox("En", true);

  speakerPanel.add(new JLabel("PC Speakers:"));
  speakerPanel.add(pcSpeakerCombo);
  speakerPanel.add(pcSpeakerEnable);
  speakerPanel.add(Box.createHorizontalStrut(15));
  speakerPanel.add(new JLabel("Radio USB Speaker (TX):"));
  speakerPanel.add(radioSpeakerCombo);
  speakerPanel.add(radioSpeakerEnable);
  topPanel.add(speakerPanel);

  controlWrapper.add(topPanel);

  // --- CENTER PANEL: Controls ---
  JPanel centerPanel = new JPanel();
  centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
  centerPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

  // 1. Frequency Control (Granular)
  JPanel freqPanel = new JPanel(new BorderLayout());
  freqPanel.setBorder(BorderFactory.createTitledBorder("RF Tuning"));

  JPanel freqInputPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
  freqField = new JTextField("162.400000", 10);
  setFreqBtn = new JButton("Set Frequency");
  scanBtn = new JButton("Scan");

  setFreqBtn.setEnabled(false);
  setFreqBtn.addActionListener(e -> setFrequency());

  ghzSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 9, 1));
  mhzSpinner = new JSpinner(new SpinnerNumberModel(162, 0, 999, 1));
  hzSpinner = new JSpinner(new SpinnerNumberModel(400000, 0, 999999, 1000));

  freqInputPanel.add(new JLabel("Direct (MHz):"));
  freqInputPanel.add(freqField);
  freqInputPanel.add(Box.createHorizontalStrut(10));
  freqInputPanel.add(new JLabel("GHz:"));
  freqInputPanel.add(ghzSpinner);
  freqInputPanel.add(new JLabel("MHz:"));
  freqInputPanel.add(mhzSpinner);
  freqInputPanel.add(new JLabel("Hz:"));
  freqInputPanel.add(hzSpinner);
  freqInputPanel.add(setFreqBtn);
  freqInputPanel.add(scanBtn);

  fineTuneSlider = new JSlider(0, 999999, 400000);
  fineTuneSlider.setMajorTickSpacing(100000);
  fineTuneSlider.setPaintTicks(true);

  freqPanel.add(freqInputPanel, BorderLayout.NORTH);
  freqPanel.add(fineTuneSlider, BorderLayout.SOUTH);
  centerPanel.add(freqPanel);

  setupFrequencyBindings();

  // 2. Voice Operation Control
  JPanel voicePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
  voicePanel.setBorder(BorderFactory.createTitledBorder("Voice Operation"));

  rxBtn = new JToggleButton("RX: ON (Listening)");
  rxBtn.setSelected(true);
  rxBtn.setBackground(new Color(100, 200, 100));
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

  JButton visModeBtn = new JButton("Toggle Spectrum/Oscilloscope");
  visModeBtn.addActionListener(e -> visualizer.toggleMode());
  JPanel visControl = new JPanel(new FlowLayout(FlowLayout.RIGHT));
  visControl.add(visModeBtn);

  scopePanel.add(visControl, BorderLayout.NORTH);
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

 private void setupFrequencyBindings() {
  // Sync Spinners/Slider -> Text Field
  Runnable syncToText = () -> {
   if (isUpdatingFreq) return;
   isUpdatingFreq = true;
   try {
    int ghz = (int) ghzSpinner.getValue();
    int mhz = (int) mhzSpinner.getValue();
    int hz = (int) hzSpinner.getValue();

    long totalHz = (ghz * 1_000_000_000L) + (mhz * 1_000_000L) + hz;
    double freqMhz = totalHz / 1_000_000.0;
    freqField.setText(String.format("%.6f", freqMhz));

    if (fineTuneSlider.getValue() != hz) {
     fineTuneSlider.setValue(hz);
    }
   } finally {
    isUpdatingFreq = false;
   }
  };

  ghzSpinner.addChangeListener(e -> syncToText.run());
  mhzSpinner.addChangeListener(e -> syncToText.run());
  hzSpinner.addChangeListener(e -> syncToText.run());
  fineTuneSlider.addChangeListener(e -> {
   if (!isUpdatingFreq) hzSpinner.setValue(fineTuneSlider.getValue());
  });

  // Sync Text Field -> Spinners/Slider
  freqField.addActionListener(e -> {
   if (isUpdatingFreq) return;
   isUpdatingFreq = true;
   try {
    double freqMhz = Double.parseDouble(freqField.getText().trim());
    long totalHz = (long) (freqMhz * 1_000_000L);

    ghzSpinner.setValue((int) (totalHz / 1_000_000_000L));
    totalHz %= 1_000_000_000L;
    mhzSpinner.setValue((int) (totalHz / 1_000_000L));
    int hz = (int) (totalHz % 1_000_000L);
    hzSpinner.setValue(hz);
    fineTuneSlider.setValue(hz);
   } catch (NumberFormatException ignored) {}
   finally {
    isUpdatingFreq = false;
   }
  });
 }

 public static void main(String[] args) {
  SwingUtilities.invokeLater(() -> {
   try { FlatDarculaLaf.setup(); } catch (Exception e) { e.printStackTrace(); }
   Dashboard dashboard = new Dashboard();
   dashboard.setLocationRelativeTo(null);
   dashboard.setVisible(true);
  });
 }

 private void refreshPorts() {
  portDropdown.removeAllItems();
  SerialPort[] ports = SerialPort.getCommPorts();
  for (SerialPort port : ports) portDropdown.addItem(port.getSystemPortName());
  if (ports.length == 0) portDropdown.addItem("No ports found");
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

    if (rxBtn.isSelected()) startRXRouting();
    else log("Audio: RX is muted. Press RX to start listening.");
   } else {
    log("ERROR: Could not open port " + portName + ". Check permissions (dialout).");
   }
  } else {
   activePort.removeDataListener();
   activePort.closePort();
   activePort = null;
   connectBtn.setText("Connect");
   log("Disconnected.");
   enableControls(false);

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
  // Trigger manual sync in case they typed without hitting enter
  freqField.postActionEvent();
  String freq = freqField.getText().trim();
  String cmd = String.format("AT+DMOSETGROUP=0,%s,%s,0000,4,0000\r\n", freq, freq);
  sendSerialCommand(cmd);
  log("Tuning to " + freq + " MHz...");
 }

 private void toggleRX() {
  if (rxBtn.isSelected()) {
   rxBtn.setText("RX: ON (Listening)");
   rxBtn.setBackground(new Color(100, 200, 100));
   startRXRouting();
  } else {
   rxBtn.setText("RX: OFF (Muted)");
   rxBtn.setBackground(Color.LIGHT_GRAY);
   audioBridge.stopRouting();
   log("Audio: RX Muted.");
  }
 }

 private void startRXRouting() {
  String pcSpeaker = (String) pcSpeakerCombo.getSelectedItem();
  try {
   if (pcSpeaker != null) {
    audioBridge.startRouting(pcSpeaker);
    log("Audio: Routing Radio RX -> PC Speakers (" + pcSpeaker + ").");
   } else {
    log("Audio Error: Please select valid RX audio devices.");
   }
  } catch (Exception ex) {
   log("Audio Error: " + ex.getMessage());
  }
 }

 private void togglePTT() {
  if (pttBtn.isSelected()) {
   pttBtn.setText("PTT: ON (Transmitting)");
   pttBtn.setBackground(Color.RED);
   rxBtn.setEnabled(false);
   sendSerialCommand("TX_ON\r\n");

   if (!pcMicEnable.isSelected() || !radioSpeakerEnable.isSelected()) {
    log("Audio Warning: TX routing aborted because PC Mic or Radio Speaker is manually disabled.");
    return;
   }

   String pcMic = (String) pcMicCombo.getSelectedItem();
   String radioSpeaker = (String) radioSpeakerCombo.getSelectedItem();

   try {
    if (pcMic != null && radioSpeaker != null) {
     // startRouting() tears down RX first, so TX cannot stack on top of it.
     audioBridge.startRouting(radioSpeaker);
     log("Audio: Transmitting PC Mic -> Radio...");
    } else {
     log("Audio Error: Please select valid TX audio devices.");
    }
   } catch (Exception ex) {
    log("Audio Error: " + ex.getMessage());
   }
  } else {
   pttBtn.setText("PTT: OFF");
   pttBtn.setBackground(Color.LIGHT_GRAY);
   rxBtn.setEnabled(true);
   sendSerialCommand("TX_OFF\r\n");

   audioBridge.stopRouting();

   if (rxBtn.isSelected()) startRXRouting();
   else log("Audio: TX Ended. RX is currently muted.");
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
     if (!response.isEmpty()) SwingUtilities.invokeLater(() -> log("RX: " + response));
    }
   }
  });
 }

 private void log(String message) {
  logArea.append(message + "\n");
  logArea.setCaretPosition(logArea.getDocument().getLength());
 }
}