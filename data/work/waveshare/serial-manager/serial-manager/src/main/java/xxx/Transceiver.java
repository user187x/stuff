package xxx;

import com.fazecast.jSerialComm.SerialPort;

import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.*;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static java.util.prefs.Preferences.userNodeForPackage;

public class Transceiver implements Serializable {
 private static final long serialVersionUID = 1L;

 // Hardware & App State
 private SerialPort comPort;
 private RadioConfig radioConfig;
 private Sender sender;
 private RxListener rxListener;
 private Protocol.Decoder decoder = new Protocol.Decoder();

 public int myAddress = 105;
 public int messageCounter = 0;

 // UI Configuration
 public float Frequency = 915.0f;
 public int PowerDBm = 22;
 public int m_BaudRate = 115200;

 // Preferences.java Integration Variables
 public String mDomainName = "http://dashboard.local/";
 public String ProjectId = "60";
 public String StringLogFileName = "RF.csv";

 // UI Elements
 private JFrame mainFrame;
 private JComboBox<String> mUIComPortName;
 private JTextArea logText;
 private JTextArea recevieText;
 private JTextArea sendText;
 private JLabel labelMessage;
 private JLabel labelLoRaStatus;
 private JLabel commIndicator;

 public void savePreference(String PREF_NAME, String newValue) {
  userNodeForPackage(this.getClass()).put(PREF_NAME, newValue);
 }

 public String loadPreferences(String PREF_NAME, String defaultValue) {
  return userNodeForPackage(this.getClass()).get(PREF_NAME, defaultValue);
 }

 public Transceiver() {
  this.logText = new JTextArea(10, 25);
  this.logText.setEditable(false);
  this.logText.setBackground(new Color(40, 42, 54));
  this.logText.setForeground(Color.LIGHT_GRAY);

  this.Frequency = Float.parseFloat(loadPreferences("Frequency", "915.0"));
  this.PowerDBm = Integer.parseInt(loadPreferences("PowerDBm", "22"));
  this.m_BaudRate = Integer.parseInt(loadPreferences("BaudRate", "115200"));
  this.myAddress = Integer.parseInt(loadPreferences("MyAddress", "105"));

  this.StringLogFileName = loadPreferences("StringLogFileName", "RF.csv");
  this.ProjectId = loadPreferences("ProjectId", "60");
 }

 public void init() {
  this.mainFrame = new JFrame("RF Protocol Manager");
  this.mainFrame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
  this.mainFrame.addWindowListener(new WindowAdapter() {
   public void windowClosing(WindowEvent windowEvent) {
    disconnectDevice();
    System.exit(0);
   }
  });

  // Loading Screen
  JPanel loadingPanel = new JPanel(new BorderLayout(10, 10));
  loadingPanel.setBorder(BorderFactory.createEmptyBorder(40, 40, 40, 40));
  JLabel loadingLabel = new JLabel("Searching Devices...", SwingConstants.CENTER);

  java.net.URL imgURL = getClass().getResource("/loading.gif");
  if (imgURL != null) {
   loadingPanel.add(new JLabel(new ImageIcon(imgURL)), BorderLayout.CENTER);
  } else {
   JProgressBar progressBar = new JProgressBar();
   progressBar.setIndeterminate(true);
   loadingPanel.add(progressBar, BorderLayout.CENTER);
  }
  loadingPanel.add(loadingLabel, BorderLayout.SOUTH);

  this.mainFrame.setContentPane(loadingPanel);
  this.mainFrame.pack();
  this.mainFrame.setLocationRelativeTo(null);
  this.mainFrame.setVisible(true);

  // Async Init
  SwingWorker<ArrayList<String>, Void> initWorker = new SwingWorker<>() {
   @Override
   protected ArrayList<String> doInBackground() {
    return scanPorts();
   }
   @Override
   protected void done() {
    try {
     buildMainUI(get());
    } catch (InterruptedException | ExecutionException e) {
     throw new RuntimeException(e);
    }
   }
  };
  initWorker.execute();
 }

 private ArrayList<String> scanPorts() {
  ArrayList<String> ports = new ArrayList<>();
  String os = System.getProperty("os.name");
  for (SerialPort p : SerialPort.getCommPorts()) {
   if (os.startsWith("Linux") || os.startsWith("Mac") || os.startsWith("Windows")) {
    ports.add(p.getSystemPortName());
   }
  }
  return ports;
 }

 private void buildMainUI(ArrayList<String> availablePorts) {
  this.labelMessage = new JLabel("Status: Ready");
  this.labelLoRaStatus = new JLabel("LoRa Off");

  JPanel mainContent = new JPanel(new BorderLayout());
  JPanel mainPanel = new JPanel();
  mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
  mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

  // Device Panel
  JPanel devPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
  devPanel.setBorder(BorderFactory.createTitledBorder("Device Selection"));
  devPanel.add(new JLabel("ComPort:"));

  mUIComPortName = new JComboBox<>();
  DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
  model.addElement("Select Device");
  for (String port : availablePorts) model.addElement(port);
  mUIComPortName.setModel(model);
  devPanel.add(mUIComPortName);

  devPanel.add(new JLabel("  Baud:"));
  JComboBox<String> choiceBaudRate = new JComboBox<>();
  int[] baudRates = new int[]{1200, 2400, 4800, 9600, 19200, 38400, 57600, 115200};
  for (int b : baudRates) {
   choiceBaudRate.addItem(b + " bps");
  }
  choiceBaudRate.setSelectedItem(this.m_BaudRate + " bps");
  choiceBaudRate.addItemListener(e -> {
   if (e.getStateChange() == ItemEvent.SELECTED) {
    this.m_BaudRate = Integer.parseInt(e.getItem().toString().replace(" bps", ""));
    savePreference("BaudRate", Integer.toString(this.m_BaudRate));
   }
  });
  devPanel.add(choiceBaudRate);

  JButton btnConnect = new JButton("Connect & Apply Settings");
  btnConnect.addActionListener(e -> toggleConnection(btnConnect));
  devPanel.add(btnConnect);

  JButton btnPrefs = new JButton("Preferences");
  btnPrefs.addActionListener(e -> new Preferences(Transceiver.this));
  devPanel.add(btnPrefs);

  mainPanel.add(devPanel);

  // Settings Panel
  JPanel setPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 5));
  setPanel.setBorder(BorderFactory.createTitledBorder("Radio Configuration"));

  // --- NEW: Bypass AT Config Checkbox to ignore hardware quirks ---
  JCheckBox checkBypass = new JCheckBox("Bypass AT Config");
  checkBypass.setSelected(Boolean.parseBoolean(loadPreferences("BypassAtConfig", "true")));
  checkBypass.addItemListener(e -> {
   savePreference("BypassAtConfig", Boolean.toString(e.getStateChange() == ItemEvent.SELECTED));
  });
  setPanel.add(checkBypass);
  // ----------------------------------------------------------------

  setPanel.add(new JLabel("Node ID: "));
  JSpinner idSpinner = new JSpinner(new SpinnerNumberModel(this.myAddress, 1, 65534, 1));
  idSpinner.setEditor(new JSpinner.NumberEditor(idSpinner, "#"));
  idSpinner.addChangeListener(e -> {
   this.myAddress = (Integer) idSpinner.getValue();
   savePreference("MyAddress", Integer.toString(this.myAddress));
  });
  setPanel.add(idSpinner);

  setPanel.add(new JLabel("Freq (MHz): "));
  JSpinner freqSpinner = new JSpinner(new SpinnerNumberModel(this.Frequency, 850.0, 930.0, 0.5));
  freqSpinner.addChangeListener(e -> {
   this.Frequency = ((Double) freqSpinner.getValue()).floatValue();
   savePreference("Frequency", Float.toString(this.Frequency));
  });
  setPanel.add(freqSpinner);

  setPanel.add(new JLabel("Power: "));
  JComboBox<String> choicePower = new JComboBox<>();
  for (int i = 10; i <= 22; ++i) choicePower.addItem(i + " dBm");
  choicePower.setSelectedItem(this.PowerDBm + " dBm");
  choicePower.addItemListener(e -> {
   if (e.getStateChange() == ItemEvent.SELECTED) {
    this.PowerDBm = Integer.parseInt(e.getItem().toString().replace(" dBm", ""));
    savePreference("PowerDBm", Integer.toString(this.PowerDBm));
   }
  });
  setPanel.add(choicePower);
  mainPanel.add(setPanel);

  // Transmission Panel
  JPanel transPanel = new JPanel(new BorderLayout(10, 0));
  transPanel.setBorder(BorderFactory.createTitledBorder("Data Stream"));

  JPanel logPanel = new JPanel(new BorderLayout());
  logPanel.add(new JLabel(" System Log"), BorderLayout.NORTH);
  logPanel.add(new JScrollPane(this.logText), BorderLayout.CENTER);
  transPanel.add(logPanel, BorderLayout.WEST);

  JPanel rightPanel = new JPanel();
  rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.Y_AXIS));

  JPanel sendRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
  sendRow.add(new JLabel(" Payload: "));
  JButton buttonSend = new JButton("Send Frame");
  buttonSend.addActionListener(e -> sendProtocolFrame());
  sendRow.add(buttonSend);
  rightPanel.add(sendRow);

  this.sendText = new JTextArea(3, 40);
  this.sendText.setText("Hello Secure Node!");
  rightPanel.add(new JScrollPane(this.sendText));

  JPanel receiveRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
  receiveRow.add(new JLabel(" Received Frames: "));
  JButton buttonClear = new JButton("Clear");
  buttonClear.addActionListener(e -> {
   recevieText.setText("");
   logText.setText("");
  });
  receiveRow.add(buttonClear);
  rightPanel.add(receiveRow);

  this.recevieText = new JTextArea(8, 40);
  rightPanel.add(new JScrollPane(this.recevieText));
  transPanel.add(rightPanel, BorderLayout.CENTER);

  mainPanel.add(transPanel);

  // Status Bar
  JPanel statusBar = new JPanel(new BorderLayout());
  statusBar.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
  JPanel leftStatus = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
  this.commIndicator = new JLabel("● Disconnected");
  this.commIndicator.setForeground(Color.RED);
  leftStatus.add(this.commIndicator);
  leftStatus.add(this.labelMessage);
  statusBar.add(leftStatus, BorderLayout.WEST);
  statusBar.add(this.labelLoRaStatus, BorderLayout.EAST);

  mainContent.add(mainPanel, BorderLayout.CENTER);
  mainContent.add(statusBar, BorderLayout.SOUTH);

  this.mainFrame.setContentPane(mainContent);
  this.mainFrame.setPreferredSize(new Dimension(1050, 650));
  this.mainFrame.pack();
  this.mainFrame.setLocationRelativeTo(null);
 }

 private void toggleConnection(JButton btn) {
  if (rxListener != null && rxListener.isRunning()) {
   disconnectDevice();
   btn.setText("Connect & Apply Settings");
  } else {
   if (mUIComPortName.getSelectedIndex() <= 0) {
    appendLog("Error: Select a valid COM port.");
    return;
   }
   connectDevice(mUIComPortName.getSelectedItem().toString());
   if (comPort != null && comPort.isOpen()) {
    btn.setText("Disconnect");
   }
  }
 }

 private void connectDevice(String portName) {
  try {
   comPort = SerialPort.getCommPort(portName);
   comPort.setBaudRate(m_BaudRate);
   comPort.openPort();

   // Initialize Architecture
   rxListener = new RxListener();
   radioConfig = new RadioConfig(comPort, rxListener, RadioConfig.Band.HF);
   sender = new Sender(comPort, rxListener, 100);
   decoder.reset();

   // Check if we are bypassing the fragile hardware AT commands
   boolean bypassAt = Boolean.parseBoolean(loadPreferences("BypassAtConfig", "true"));

   if (!bypassAt) {
    // Configure Hardware via AT commands
    appendLog("System: Entering AT Mode to configure module...");
    radioConfig.commander().setTrace(msg -> appendLog("AT_TRACE: " + msg));
    radioConfig.beginSession();

    radioConfig.setMode(RadioConfig.Mode.STREAM);
    radioConfig.tuneFrequency(Frequency);
    radioConfig.setPower(PowerDBm);

    radioConfig.setAddress(0);
    radioConfig.setNetworkId(0);

    radioConfig.setSpreadingFactor(7);
    radioConfig.setBandwidth(0);
    radioConfig.setCodingRate(1);

    radioConfig.endSession();
    appendLog("System: AT Configuration complete.");
   } else {
    appendLog("System: Bypassing AT Config. Using hardware as a transparent pipe.");
   }

   // Start Listening
   rxListener.startListener();
   setConnectionStatus(true);
   labelMessage.setText("Ready: Node " + myAddress);
   labelLoRaStatus.setText("Freq: " + Frequency + " MHz | " + PowerDBm + "dBm");

  } catch (Exception e) {
   appendLog("Failed to connect: " + e.getMessage());
   setConnectionStatus(false);
  }
 }

 private void disconnectDevice() {
  if (rxListener != null) rxListener.stopListener();
  if (comPort != null && comPort.isOpen()) comPort.closePort();
  setConnectionStatus(false);
  labelMessage.setText("Disconnected");
  labelLoRaStatus.setText("LoRa Off");
  appendLog("System: Disconnected.");
 }

 private void sendProtocolFrame() {
  if (sender == null || comPort == null || !comPort.isOpen()) {
   appendLog("Error: Device not connected.");
   return;
  }

  String text = sendText.getText();

  Protocol.Frame frame = new Protocol.Frame(
      Protocol.Type.DATA,
      myAddress,                // SRC
      Protocol.BROADCAST_ADDR,  // DST
      messageCounter++,         // MSG ID
      0, 1,
      text.getBytes(StandardCharsets.UTF_8)
  );

  sender.sendFrame(frame);
  appendLog("Tx [Msg " + (messageCounter-1) + "] -> " + text);
 }

 public void appendLog(String message) {
  SwingUtilities.invokeLater(() -> {
   if (logText != null) {
    logText.append(message + "\n");
    logText.setCaretPosition(logText.getDocument().getLength());
   }
  });
 }

 public void setConnectionStatus(boolean connected) {
  SwingUtilities.invokeLater(() -> {
   if (commIndicator != null) {
    commIndicator.setText(connected ? "● Connected" : "● Disconnected");
    commIndicator.setForeground(connected ? Color.GREEN : Color.RED);
   }
  });
 }

 class RxListener extends Thread implements Listener {
  private volatile boolean running = false;
  private volatile boolean muted = false;

  public boolean isRunning() { return running; }

  @Override
  public void stop() {

  }

  public void startListener() { running = true; start(); }
  public void stopListener() { running = false; interrupt(); }

  public void mute() { muted = true; }
  public void unmute() { muted = false; }

  @Override
  public void run() {
   byte[] buf = new byte[2048];
   while (running && !isInterrupted()) {
    try {
     if (comPort != null && comPort.isOpen() && comPort.bytesAvailable() > 0) {
      int n = comPort.readBytes(buf, buf.length);

      if (!muted && n > 0) {
       byte[] validBytes = new byte[n];
       System.arraycopy(buf, 0, validBytes, 0, n);

       List<Protocol.Frame> frames = decoder.feed(validBytes, n);

       for (Protocol.Frame frame : frames) {
        if (frame.src == myAddress) {
         appendLog("Filtered local echo from Address " + frame.src);
         continue;
        }

        final String msg = "Rx [Node " + frame.src + "]: " + frame.text();
        SwingUtilities.invokeLater(() -> {
         recevieText.append(msg + "\n");
         recevieText.setCaretPosition(recevieText.getDocument().getLength());
        });
       }
      }
     }
     Thread.sleep(20);
    } catch (Exception e) {
     break;
    }
   }
  }
 }
}