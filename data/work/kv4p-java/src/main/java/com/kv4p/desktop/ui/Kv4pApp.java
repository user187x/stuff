package com.kv4p.desktop.ui;

import static com.kv4p.desktop.protocol.Kv4pProtocol.*;

import com.fazecast.jSerialComm.SerialPort;
import com.formdev.flatlaf.FlatDarkLaf;
import com.kv4p.desktop.Kv4pClient;
import com.kv4p.desktop.audio.RxAudioPlayer;
import com.kv4p.desktop.audio.TxAudioCapture;
import com.kv4p.desktop.protocol.Structs.DeviceState;
import com.kv4p.desktop.protocol.Structs.Hello;
import com.kv4p.desktop.serial.SerialLink;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;
import javax.swing.*;
import javax.swing.border.TitledBorder;

public final class Kv4pApp extends JFrame {
  private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

  // Connection & Audio Routing
  private final JComboBox<SerialPort> portCombo = new JComboBox<>();
  private final JComboBox<Mixer.Info> rxAudioCombo = new JComboBox<>();
  private final JComboBox<Mixer.Info> txAudioCombo = new JComboBox<>();
  private final JButton refreshBtn = new JButton("Refresh Devices");
  private final JButton connectBtn = new JButton("Connect");
  private final JLabel statusLabel = new JLabel("Disconnected");

  // Radio config
  private final JTextField freqRxField = new JTextField("146.520", 8);
  private final JTextField freqTxField = new JTextField("146.520", 8);
  private final JComboBox<String> bwCombo = new JComboBox<>(new String[] {"12.5 kHz", "25 kHz"});
  private final JSpinner squelchSpinner = new JSpinner(new SpinnerNumberModel(2, 0, 8, 1));
  private final JSpinner ctcssTxSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 38, 1));
  private final JSpinner ctcssRxSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 38, 1));
  private final JButton tuneBtn = new JButton("Tune");

  // Toggles
  private final JCheckBox highPowerCheck = new JCheckBox("High power", true);
  private final JCheckBox filterPreCheck = new JCheckBox("Pre/de-emphasis");
  private final JCheckBox filterHighCheck = new JCheckBox("High-pass");
  private final JCheckBox filterLowCheck = new JCheckBox("Low-pass");
  private final JCheckBox txAudioCheck = new JCheckBox("Tx audio", true);
  private final JCheckBox rxAudioCheck = new JCheckBox("RX audio", true);

  // Live state
  private final JButton pttButton = new JButton("PUSH TO TALK");
  private final JButton sendButton = new JButton("Send")
  private final JProgressBar rssiBar = new JProgressBar(0, 255);
  private final JLabel modeLabel = new JLabel("---");
  private final JLabel squelchLabel = new JLabel("---");
  private final JLabel freqLabel = new JLabel("---");
  private final JTextArea logArea = new JTextArea(10, 60);

  private SerialLink link;
  private Kv4pClient client;
  private RxAudioPlayer rxPlayer;
  private TxAudioCapture txCapture;

  public Kv4pApp() {
    super("HAM Radio");
    setDefaultCloseOperation(EXIT_ON_CLOSE);
    setLayout(new BorderLayout(8, 8));

    add(buildConnectionPanel(), BorderLayout.NORTH);
    add(buildCenterPanel(), BorderLayout.CENTER);
    add(buildLogPanel(), BorderLayout.SOUTH);

    wireActions();
    setControlsEnabled(false);
    refreshDevices();
    pack();
    setMinimumSize(getSize());
    setLocationRelativeTo(null);
  }

  // ---------------- UI construction ----------------
  private JPanel buildConnectionPanel() {
    var panel = new JPanel(new GridLayout(3, 1, 4, 4));
    panel.setBorder(new TitledBorder("Hardware Setup"));

    // Isolated renderer for the Serial Port selection list
    portCombo.setRenderer(
        new DefaultListCellRenderer() {
          @Override
          public Component getListCellRendererComponent(
              JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof SerialPort p) {
              setText(p.getSystemPortName() + "   " + p.getDescriptivePortName());
            }
            return this;
          }
        });

    // Isolated renderer for RX Audio selection
    rxAudioCombo.setRenderer(
        new DefaultListCellRenderer() {
          @Override
          public Component getListCellRendererComponent(
              JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof Mixer.Info info) {
              setText(info.getName() + " (" + info.getDescription() + ")");
            }
            return this;
          }
        });

    // Isolated renderer for TX Audio selection
    txAudioCombo.setRenderer(
        new DefaultListCellRenderer() {
          @Override
          public Component getListCellRendererComponent(
              JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof Mixer.Info info) {
              setText(info.getName() + " (" + info.getDescription() + ")");
            }
            return this;
          }
        });

    // Use safe baseline dimension limits rather than uncalculated runtime lookups
    portCombo.setPreferredSize(new Dimension(280, 26));
    rxAudioCombo.setPreferredSize(new Dimension(350, 26));
    txAudioCombo.setPreferredSize(new Dimension(350, 26));

    // Row 1: Serial Selection
    var serialRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
    serialRow.add(new JLabel("Serial Port:"));
    serialRow.add(portCombo);
    serialRow.add(refreshBtn);
    serialRow.add(connectBtn);
    serialRow.add(statusLabel);
    panel.add(serialRow);

    // Row 2: RX Audio (Speaker) Selection
    var rxRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
    rxRow.add(new JLabel("RX Audio (Speaker):"));
    rxRow.add(rxAudioCombo);
    panel.add(rxRow);

    // Row 3: TX Audio (Mic) Selection
    var txRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
    txRow.add(new JLabel("TX Audio (Mic):    "));
    txRow.add(txAudioCombo);
    panel.add(txRow);

    return panel;
  }

  private JPanel buildCenterPanel() {
    var center = new JPanel(new GridBagLayout());
    var gbc = new GridBagConstraints();
    gbc.insets = new Insets(4, 8, 4, 8);
    gbc.fill = GridBagConstraints.BOTH;
    gbc.weightx = 1;

    gbc.gridx = 0;
    gbc.gridy = 0;
    center.add(buildRadioConfigPanel(), gbc);

    gbc.gridx = 1;
    center.add(buildTogglesPanel(), gbc);

    gbc.gridx = 0;
    gbc.gridy = 1;
    gbc.gridwidth = 2;
    center.add(buildLivePanel(), gbc);

    return center;
  }

  private JPanel buildRadioConfigPanel() {
    var panel = new JPanel(new GridBagLayout());
    panel.setBorder(new TitledBorder("Radio"));
    var gbc = new GridBagConstraints();
    gbc.insets = new Insets(2, 4, 2, 4);
    gbc.anchor = GridBagConstraints.WEST;

    int row = 0;
    addRow(panel, gbc, row++, "RX freq (MHz):", freqRxField);
    addRow(panel, gbc, row++, "TX freq (MHz):", freqTxField);
    addRow(panel, gbc, row++, "Bandwidth:", bwCombo);
    addRow(panel, gbc, row++, "Squelch (0-8):", squelchSpinner);
    addRow(panel, gbc, row++, "CTCSS TX idx:", ctcssTxSpinner);
    addRow(panel, gbc, row++, "CTCSS RX idx:", ctcssRxSpinner);

    gbc.gridx = 0;
    gbc.gridy = row;
    gbc.gridwidth = 2;
    panel.add(tuneBtn, gbc);

    bwCombo.setSelectedIndex(1);
    return panel;
  }

  private static void addRow(
      JPanel panel, GridBagConstraints gbc, int row, String label, JComponent field) {
    gbc.gridwidth = 1;
    gbc.gridx = 0;
    gbc.gridy = row;
    panel.add(new JLabel(label), gbc);
    gbc.gridx = 1;
    panel.add(field, gbc);
  }

  private JPanel buildTogglesPanel() {
    var panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
    panel.setBorder(new TitledBorder("Settings"));

    panel.add(highPowerCheck);
    panel.add(filterPreCheck);
    panel.add(filterHighCheck);
    panel.add(filterLowCheck);
    panel.add(rxAudioCheck);
    panel.add(Box.createVerticalStrut(8));
    panel.add(txAudioCheck);

    return panel;
  }

  private JPanel buildLivePanel() {
    var panel = new JPanel(new BorderLayout(8, 8));
    panel.setBorder(new TitledBorder("Controls"));

    pttButton.setFont(pttButton.getFont().deriveFont(Font.BOLD, 20f));
    pttButton.setPreferredSize(new Dimension(260, 70));
    pttButton.setBackground(new Color(220, 220, 220));
    panel.add(pttButton, BorderLayout.WEST);

    var status = new JPanel(new GridLayout(4, 2, 6, 2));
    status.add(new JLabel("Mode:"));
    status.add(modeLabel);
    status.add(new JLabel("Squelch:"));
    status.add(squelchLabel);
    status.add(new JLabel("Tuned:"));
    status.add(freqLabel);
    status.add(new JLabel("RSSI:"));

    rssiBar.setStringPainted(true);
    status.add(rssiBar);

    panel.add(status, BorderLayout.CENTER);
    return panel;
  }

  // TODO - Implement the message functionality
  private JPanel buildMessagePanel() {

    var panel = new JPanel(new BorderLayout(8, 8));
    panel.setBorder(new TitledBorder("Messenger"));

    sendButton.setFont(pttButton.getFont().deriveFont(Font.BOLD, 20f));
    sendButton.setPreferredSize(new Dimension(200, 70));
    sendButton.setBackground(new Color(220, 220, 220));
    panel.add(sendButton, BorderLayout.WEST);

    var messages = new JPanel(new GridLayout(4, 2, 6, 2));
    messages.add(new JLabel("Messages"));
    messages.add(modeLabel);

    panel.add(messages, BorderLayout.CENTER);
    return panel;
  }

  private JScrollPane buildLogPanel() {
    logArea.setEditable(false);
    logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
    var scroll = new JScrollPane(logArea);
    scroll.setBorder(new TitledBorder("Firmware log"));
    return scroll;
  }

  // ---------------- Behavior ----------------
  private void wireActions() {
    refreshBtn.addActionListener(e -> refreshDevices());

    connectBtn.addActionListener(
        e -> {
          if (client == null) connect();
          else disconnect("Disconnected");
        });

    tuneBtn.addActionListener(e -> tune());

    highPowerCheck.addActionListener(
        e -> ifConnected(c -> c.setFlag(HOST_STATE_HIGH_POWER, highPowerCheck.isSelected())));
    filterPreCheck.addActionListener(
        e -> ifConnected(c -> c.setFlag(HOST_STATE_FILTER_PRE, filterPreCheck.isSelected())));
    filterHighCheck.addActionListener(
        e -> ifConnected(c -> c.setFlag(HOST_STATE_FILTER_HIGH, filterHighCheck.isSelected())));
    filterLowCheck.addActionListener(
        e -> ifConnected(c -> c.setFlag(HOST_STATE_FILTER_LOW, filterLowCheck.isSelected())));
    txAudioCheck.addActionListener(
        e -> ifConnected(c -> c.setTxAllowed(txAudioCheck.isSelected())));
    rxAudioCheck.addActionListener(
        e -> ifConnected(c -> c.setRxAudioOpen(rxAudioCheck.isSelected())));

    // Press-and-hold PTT
    pttButton.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mousePressed(MouseEvent e) {
            setPtt(true);
          }

          @Override
          public void mouseReleased(MouseEvent e) {
            setPtt(false);
          }
        });
  }

  private void refreshDevices() {
    // Refresh Serial Ports
    portCombo.removeAllItems();
    for (SerialPort p : SerialLink.availablePorts()) {
      portCombo.addItem(p);
    }

    // Refresh Audio Devices
    rxAudioCombo.removeAllItems();
    txAudioCombo.removeAllItems();

    DataLine.Info rxInfo = new DataLine.Info(SourceDataLine.class, RxAudioPlayer.FORMAT);
    DataLine.Info txInfo = new DataLine.Info(TargetDataLine.class, TxAudioCapture.FORMAT);

    for (Mixer.Info info : AudioSystem.getMixerInfo()) {
      Mixer mixer = AudioSystem.getMixer(info);

      // Identify speakers that support our RX data format
      if (mixer.isLineSupported(rxInfo)) {
        rxAudioCombo.addItem(info);
      }
      // Identify microphones that support our TX data format
      if (mixer.isLineSupported(txInfo)) {
        txAudioCombo.addItem(info);
      }
    }
  }

  private void connect() {
    SerialPort selectedPort = (SerialPort) portCombo.getSelectedItem();
    Mixer.Info selectedRxAudio = (Mixer.Info) rxAudioCombo.getSelectedItem();

    if (selectedPort == null) {
      JOptionPane.showMessageDialog(
          this, "No serial port selected.", "kv4p", JOptionPane.WARNING_MESSAGE);
      return;
    }

    try {
      link = SerialLink.open(selectedPort);
      client = Kv4pClient.connect(link);
      client.addListener(new UiListener());

      rxPlayer = new RxAudioPlayer();
      rxPlayer.start(selectedRxAudio);

      txCapture =
          new TxAudioCapture(
              frame -> {
                Kv4pClient c = client;
                if (c != null) c.sendTxAudioFrame(frame);
              });

      statusLabel.setText("Waiting for HELLO on " + link.portName() + "...");
      connectBtn.setText("Disconnect");
      log(
          "Opened "
              + link.portName()
              + " @ "
              + SERIAL_BAUD
              + " baud, reset device, waiting for HELLO");

    } catch (Exception ex) {
      disconnect("Connect failed: " + ex.getMessage());
      JOptionPane.showMessageDialog(
          this, ex.getMessage(), "Connect failed", JOptionPane.ERROR_MESSAGE);
    }
  }

  private void disconnect(String reason) {
    if (txCapture != null) {
      txCapture.close();
      txCapture = null;
    }
    if (rxPlayer != null) {
      rxPlayer.close();
      rxPlayer = null;
    }
    if (client != null) {
      client.close();
      client = null;
    }
    link = null;

    SwingUtilities.invokeLater(
        () -> {
          statusLabel.setText(reason);
          connectBtn.setText("Connect");
          setControlsEnabled(false);
          modeLabel.setText("---");
          squelchLabel.setText("---");
          freqLabel.setText("---");
          rssiBar.setValue(0);
        });
  }

  private void tune() {
    ifConnected(
        c -> {
          try {
            float rx = Float.parseFloat(freqRxField.getText().trim());
            float tx = Float.parseFloat(freqTxField.getText().trim());
            c.setRadioConfig(
                tx,
                rx,
                bwCombo.getSelectedIndex() == 0 ? BW_12K5 : BW_25K,
                (Integer) ctcssTxSpinner.getValue(),
                (Integer) ctcssRxSpinner.getValue(),
                (Integer) squelchSpinner.getValue(),
                -1);
            log(String.format("Tune requested: RX %.4f MHz / TX %.4f MHz", rx, tx));
          } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(
                this, "Invalid frequency.", "kv4p", JOptionPane.WARNING_MESSAGE);
          }
        });
  }

  private void setPtt(boolean down) {
    Kv4pClient c = client;
    if (c == null) return;

    if (down && !txAudioCheck.isSelected()) {
      log("PTT ignored: enable 'TX allowed' first (firmware safety flag).");
      return;
    }

    c.setPtt(down);
    pttButton.setBackground(down ? new Color(255, 120, 120) : new Color(220, 220, 220));

    Mixer.Info selectedTxAudio = (Mixer.Info) txAudioCombo.getSelectedItem();
    try {
      if (down) {
        txCapture.start(selectedTxAudio);
      } else {
        txCapture.close();
        txCapture =
            new TxAudioCapture(
                f -> {
                  if (client != null) client.sendTxAudioFrame(f);
                });
      }
    } catch (Exception ex) {
      log("Mic error: " + ex.getMessage());
    }
  }

  private void ifConnected(java.util.function.Consumer<Kv4pClient> action) {
    Kv4pClient c = client;
    if (c != null) action.accept(c);
  }

  private void setControlsEnabled(boolean enabled) {
    for (JComponent comp :
        new JComponent[] {
          freqRxField,
          freqTxField,
          bwCombo,
          squelchSpinner,
          ctcssTxSpinner,
          ctcssRxSpinner,
          tuneBtn,
          highPowerCheck,
          filterPreCheck,
          filterHighCheck,
          filterLowCheck,
          txAudioCheck,
          rxAudioCheck,
          pttButton
        }) {
      comp.setEnabled(enabled);
    }
  }

  private void log(String message) {
    SwingUtilities.invokeLater(
        () -> {
          logArea.append("[" + LocalTime.now().format(TS) + "] " + message + "\n");
          logArea.setCaretPosition(logArea.getDocument().getLength());
        });
  }

  // ---------------- Client events ----------------
  private final class UiListener implements Kv4pClient.Listener {
    @Override
    public void onHello(Hello hello) {
      log(
          String.format(
              "HELLO: fw v%d, module=%s, window=%d, band %.1f-%.1f MHz, features=0x%02X",
              hello.version().ver(),
              hello.version().radioModuleStatus() == RADIO_MODULE_FOUND ? "found" : "NOT FOUND",
              hello.version().windowSize(),
              hello.version().minRadioFreq(),
              hello.version().maxRadioFreq(),
              hello.version().features()));

      SwingUtilities.invokeLater(
          () -> {
            statusLabel.setText("Connected - firmware v" + hello.version().ver());
            setControlsEnabled(true);

            DeviceState d = hello.deviceState();
            freqRxField.setText(String.format("%.4f", d.freqRx()));
            freqTxField.setText(String.format("%.4f", d.freqTx()));
            bwCombo.setSelectedIndex(d.bw() == BW_12K5 ? 0 : 1);
            squelchSpinner.setValue(d.squelch());
            ctcssTxSpinner.setValue(d.ctcssTx());
            ctcssRxSpinner.setValue(d.ctcssRx());
            highPowerCheck.setSelected(d.flag(HOST_STATE_HIGH_POWER));
            filterPreCheck.setSelected(d.flag(HOST_STATE_FILTER_PRE));
            filterHighCheck.setSelected(d.flag(HOST_STATE_FILTER_HIGH));
            filterLowCheck.setSelected(d.flag(HOST_STATE_FILTER_LOW));
            txAudioCheck.setSelected(d.flag(HOST_STATE_TX_ALLOWED));
          });

      // Open RX audio by default, mirroring the Android app.
      ifConnected(c -> c.setRxAudioOpen(rxAudioCheck.isSelected()));
    }

    @Override
    public void onDeviceState(DeviceState state) {
      SwingUtilities.invokeLater(
          () -> {
            modeLabel.setText(
                switch (state.mode()) {
                  case DEVICE_MODE_TX -> "TX";
                  case DEVICE_MODE_RX -> "RX";
                  default -> "Stopped";
                });
            squelchLabel.setText(state.flag(DEVICE_STATE_SQUELCHED) ? "Closed" : "OPEN");
            freqLabel.setText(
                String.format("RX %.4f / TX %.4f MHz", state.freqRx(), state.freqTx()));
            rssiBar.setValue(state.latestRssi());
            rssiBar.setString("S-" + state.latestRssi());
            if (state.lastError() != DEVICE_STATE_ERROR_NONE) {
              log("Device error: " + state.lastError());
            }
          });
    }

    @Override
    public void onRxAudio(byte[] adpcmPayload) {
      RxAudioPlayer p = rxPlayer;
      if (p != null) p.playAdpcm(adpcmPayload);
    }

    @Override
    public void onAx25Received(byte[] ax25) {
      log("AX.25 packet received (" + ax25.length + " bytes)");
    }

    @Override
    public void onDebugMessage(int level, String message) {
      log(message.strip());
    }

    @Override
    public void onDisconnected(String reason) {
      disconnect("Disconnected: " + reason);
    }
  }

  public static void main(String[] args) {
    FlatDarkLaf.setup();
    SwingUtilities.invokeLater(() -> new Kv4pApp().setVisible(true));
  }
}
