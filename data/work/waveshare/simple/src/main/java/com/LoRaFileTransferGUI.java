package com;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListener;
import com.fazecast.jSerialComm.SerialPortEvent;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;

/**
 * LoRaFileTransferGUI — a Swing front end that lets you pick an image on your
 * computer and send it to another LoRa node, and that simultaneously receives
 * any incoming transfer and saves it to disk.
 *
 * It is a thin shell around {@link LoRaTransferProtocol}:
 *   - the serial port supplies whole lines  -> protocol.feed(line)
 *   - the protocol asks to transmit a line   -> we write it to the serial port
 *
 * Assumes the module is in TRANSPARENT / pass-through mode (bytes written to the
 * UART are sent over the air and received bytes appear on the UART) — the same
 * assumption as the existing console chat tool. If your module needs AT commands
 * to transmit, wrap the LineSender below accordingly.
 */
public class LoRaFileTransferGUI extends JFrame {

    private static final long serialVersionUID = -5681978916417585423L;

    private static final int PREVIEW = 240; // px box for image previews

    // This node's id — stamped on every outgoing frame so we ignore our own echoes.
    private final String nodeId = UUID.randomUUID().toString().substring(0, 6).toUpperCase();

    // ---- serial / protocol ----
    private SerialPort[] ports = new SerialPort[0];
    private SerialPort serialPort;
    private LoRaTransferProtocol protocol;
    private SerialLineReader reader;

    // ---- send state ----
    private byte[] selectedBytes;
    private String selectedName;

    // ---- receive state ----
    private File saveDir = new File(System.getProperty("user.home"), "LoRa_Received");

    // ---- widgets ----
    private final JComboBox<String> portCombo = new JComboBox<>();
    private final JButton refreshBtn = new JButton("Refresh");
    private final JButton connectBtn = new JButton("Connect");
    private final JLabel  statusLabel = new JLabel("Disconnected");
    private final JLabel  nodeLabel   = new JLabel("Node " + nodeId);

    private final JButton chooseBtn = new JButton("Choose Image\u2026");
    private final JButton sendBtn   = new JButton("Send");
    private final JLabel  sendPreview = new JLabel("(no image selected)", SwingConstants.CENTER);
    private final JLabel  sendInfo    = new JLabel(" ");

    private final JLabel  rxPreview = new JLabel("(waiting for incoming\u2026)", SwingConstants.CENTER);
    private final JLabel  rxInfo    = new JLabel(" ");
    private final JButton saveDirBtn = new JButton("Save folder\u2026");
    private final JLabel  saveDirLabel = new JLabel();

    private final JProgressBar progress = new JProgressBar(0, 100);
    private final JLabel progressLabel = new JLabel("Idle");
    private final JTextArea logArea = new JTextArea(10, 60);

    public LoRaFileTransferGUI() {
        super("LoRa Image Transfer");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        buildUI();
        refreshPorts();
        sendBtn.setEnabled(false);
        //noinspection ResultOfMethodCallIgnored
        saveDir.mkdirs();
        saveDirLabel.setText(saveDir.getAbsolutePath());
        log("Ready. Node id for this session: " + nodeId);
        log("Received files will be saved to: " + saveDir.getAbsolutePath());
        pack();
        setLocationRelativeTo(null);
    }

    // ============================ UI ============================

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(new EmptyBorder(8, 8, 8, 8));
        setContentPane(root);

        // ----- connection bar -----
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        top.add(new JLabel("Port:"));
        portCombo.setPreferredSize(new Dimension(220, 26));
        top.add(portCombo);
        top.add(refreshBtn);
        top.add(connectBtn);
        top.add(Box.createHorizontalStrut(16));
        statusLabel.setOpaque(true);
        statusLabel.setBorder(new EmptyBorder(2, 8, 2, 8));
        setStatus(false);
        top.add(statusLabel);
        top.add(Box.createHorizontalStrut(12));
        nodeLabel.setFont(nodeLabel.getFont().deriveFont(Font.BOLD));
        top.add(nodeLabel);
        root.add(top, BorderLayout.NORTH);

        // ----- center: send | receive -----
        JPanel center = new JPanel(new GridLayout(1, 2, 8, 0));

        JPanel sendPanel = titled("Send");
        sendPreview.setPreferredSize(new Dimension(PREVIEW, PREVIEW));
        sendPreview.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        JPanel sendBtns = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 4));
        sendBtns.add(chooseBtn);
        sendBtns.add(sendBtn);
        sendPanel.add(sendBtns, BorderLayout.NORTH);
        sendPanel.add(sendPreview, BorderLayout.CENTER);
        sendInfo.setHorizontalAlignment(SwingConstants.CENTER);
        sendPanel.add(sendInfo, BorderLayout.SOUTH);

        JPanel rxPanel = titled("Received");
        rxPreview.setPreferredSize(new Dimension(PREVIEW, PREVIEW));
        rxPreview.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        JPanel rxTop = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        rxTop.add(saveDirBtn);
        rxTop.add(new JLabel("\u2192"));
        saveDirLabel.setFont(saveDirLabel.getFont().deriveFont(Font.PLAIN, 11f));
        rxTop.add(saveDirLabel);
        rxPanel.add(rxTop, BorderLayout.NORTH);
        rxPanel.add(rxPreview, BorderLayout.CENTER);
        rxInfo.setHorizontalAlignment(SwingConstants.CENTER);
        rxPanel.add(rxInfo, BorderLayout.SOUTH);

        center.add(sendPanel);
        center.add(rxPanel);
        root.add(center, BorderLayout.CENTER);

        // ----- bottom: progress + log -----
        JPanel bottom = new JPanel(new BorderLayout(6, 6));
        JPanel progRow = new JPanel(new BorderLayout(8, 0));
        progress.setStringPainted(true);
        progRow.add(progress, BorderLayout.CENTER);
        progressLabel.setPreferredSize(new Dimension(180, 22));
        progRow.add(progressLabel, BorderLayout.EAST);
        bottom.add(progRow, BorderLayout.NORTH);

        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(new TitledBorder("Log"));
        bottom.add(logScroll, BorderLayout.CENTER);
        root.add(bottom, BorderLayout.SOUTH);

        // ----- wiring -----
        refreshBtn.addActionListener(e -> refreshPorts());
        connectBtn.addActionListener(e -> toggleConnection());
        chooseBtn.addActionListener(e -> chooseImage());
        sendBtn.addActionListener(e -> sendSelected());
        saveDirBtn.addActionListener(e -> chooseSaveDir());
    }

    private static JPanel titled(String title) {
        JPanel p = new JPanel(new BorderLayout(4, 4));
        p.setBorder(new TitledBorder(title));
        return p;
    }

    // ====================== connection ======================

    private void refreshPorts() {
        ports = SerialPort.getCommPorts();
        portCombo.removeAllItems();
        for (SerialPort p : ports) {
            portCombo.addItem(p.getSystemPortName() + "  (" + p.getDescriptivePortName() + ")");
        }
        if (ports.length == 0) {
            portCombo.addItem("— no serial ports found —");
            connectBtn.setEnabled(false);
        } else {
            connectBtn.setEnabled(true);
        }
    }

    private void toggleConnection() {
        if (serialPort != null && serialPort.isOpen()) {
            disconnect();
        } else {
            connect();
        }
    }

    private void connect() {
        int idx = portCombo.getSelectedIndex();
        if (idx < 0 || idx >= ports.length) { log("No port selected."); return; }

        SerialPort p = ports[idx];
        p.setBaudRate(115200);
        p.setNumDataBits(8);
        p.setNumStopBits(SerialPort.ONE_STOP_BIT);
        p.setParity(SerialPort.NO_PARITY);
        p.setFlowControl(SerialPort.FLOW_CONTROL_DISABLED);

        if (!p.openPort()) {
            log("Failed to open " + p.getSystemPortName() + " (busy or locked?).");
            return;
        }
        serialPort = p;

        // Build the protocol. LineSender writes a finished frame out the UART.
        LoRaTransferProtocol.LineSender lineSender = line -> {
            SerialPort sp = serialPort;
            if (sp != null && sp.isOpen()) {
                byte[] b = (line + "\r\n").getBytes(StandardCharsets.UTF_8);
                sp.writeBytes(b, b.length);
            }
        };
        protocol = new LoRaTransferProtocol(nodeId, lineSender, new GuiListener());

        // Feed inbound lines from the serial port into the protocol.
        reader = new SerialLineReader(protocol::feed);
        serialPort.addDataListener(reader);

        setStatus(true);
        connectBtn.setText("Disconnect");
        portCombo.setEnabled(false);
        refreshBtn.setEnabled(false);
        sendBtn.setEnabled(selectedBytes != null);
        log("Connected on " + serialPort.getSystemPortName()
                + "  |  chunk size " + protocol.getChunkSize() + " B (from MAX_LORA_PAYLOAD="
                + protocol.getMaxLoraPayload() + ")");
    }

    private void disconnect() {
        try {
            if (serialPort != null) {
                serialPort.removeDataListener();
                if (serialPort.isOpen()) serialPort.closePort();
            }
        } catch (Exception ignore) { }
        serialPort = null;
        protocol = null;
        setStatus(false);
        connectBtn.setText("Connect");
        portCombo.setEnabled(true);
        refreshBtn.setEnabled(true);
        sendBtn.setEnabled(false);
        log("Disconnected.");
    }

    private void setStatus(boolean connected) {
        statusLabel.setText(connected ? "Connected" : "Disconnected");
        statusLabel.setBackground(connected ? new Color(0xD4, 0xED, 0xDA) : new Color(0xF8, 0xD7, 0xDA));
        statusLabel.setForeground(connected ? new Color(0x155724) : new Color(0x721C24));
    }

    // ====================== send side ======================

    private void chooseImage() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Choose an image to send");
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "Images (png, jpg, jpeg, gif, bmp)", "png", "jpg", "jpeg", "gif", "bmp"));
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File f = fc.getSelectedFile();
        try {
            byte[] bytes = Files.readAllBytes(f.toPath());
            selectedBytes = bytes;
            selectedName = f.getName();
            sendPreview.setIcon(scaleToFit(bytes, PREVIEW));
            sendPreview.setText(sendPreview.getIcon() == null ? "(preview unavailable)" : null);

            int chunkSize = (protocol != null) ? protocol.getChunkSize()
                    : LoRaTransferProtocol.DEFAULT_MAX_LORA_PAYLOAD; // rough hint pre-connect
            long est = (protocol != null) ? (long) Math.ceil(bytes.length / (double) chunkSize) : -1;
            sendInfo.setText(selectedName + "  —  " + humanSize(bytes.length)
                    + (est >= 0 ? "  (~" + est + " chunks)" : ""));
            sendBtn.setEnabled(serialPort != null && serialPort.isOpen());
            log("Selected " + selectedName + " (" + bytes.length + " bytes).");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Could not read file:\n" + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void sendSelected() {
        if (protocol == null || serialPort == null || !serialPort.isOpen()) { log("Not connected."); return; }
        if (selectedBytes == null) { log("No image selected."); return; }

        final byte[] data = selectedBytes;
        final String name = selectedName;
        setSendingUiEnabled(false);
        progressLabel.setText("Sending 0/?");
        progress.setValue(0);

        // sendData() blocks waiting for ACKs, so run it off the EDT.
        new SwingWorker<Boolean, Void>() {
            @Override protected Boolean doInBackground() { return protocol.sendData(data, name); }
            @Override protected void done() {
                boolean ok = false;
                try { ok = get(); } catch (Exception ignored) { }
                setSendingUiEnabled(true);
                progressLabel.setText(ok ? "Sent" : "Send failed");
                if (ok) JOptionPane.showMessageDialog(LoRaFileTransferGUI.this,
                        "Sent " + name + " successfully.", "Done", JOptionPane.INFORMATION_MESSAGE);
            }
        }.execute();
    }

    private void setSendingUiEnabled(boolean enabled) {
        sendBtn.setEnabled(enabled && selectedBytes != null && serialPort != null && serialPort.isOpen());
        chooseBtn.setEnabled(enabled);
        connectBtn.setEnabled(enabled);
    }

    private void chooseSaveDir() {
        JFileChooser fc = new JFileChooser(saveDir);
        fc.setDialogTitle("Choose where received images are saved");
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            saveDir = fc.getSelectedFile();
            //noinspection ResultOfMethodCallIgnored
            saveDir.mkdirs();
            saveDirLabel.setText(saveDir.getAbsolutePath());
            log("Save folder set to " + saveDir.getAbsolutePath());
        }
    }

    // ====================== protocol callbacks ======================
    // Every method hops back onto the EDT before touching widgets.

    private class GuiListener implements LoRaTransferProtocol.Listener {
        @Override public void onLog(String msg) { log(msg); }
        @Override public void onError(String msg) { log("ERROR: " + msg);
            SwingUtilities.invokeLater(() -> progressLabel.setText("Error")); }

        @Override public void onSendProgress(int done, int total) {
            SwingUtilities.invokeLater(() -> {
                progress.setMaximum(total);
                progress.setValue(done);
                progressLabel.setText("Sending " + done + "/" + total);
            });
        }
        @Override public void onSendComplete(String filename) {
            SwingUtilities.invokeLater(() -> { progress.setValue(progress.getMaximum());
                progressLabel.setText("Sent"); });
        }

        @Override public void onReceiveStart(String filename, long size, int totalChunks) {
            SwingUtilities.invokeLater(() -> {
                progress.setMaximum(Math.max(totalChunks, 1));
                progress.setValue(0);
                progressLabel.setText("Receiving 0/" + totalChunks);
                rxInfo.setText("Incoming: " + filename + "  —  " + humanSize(size));
                rxPreview.setIcon(null);
                rxPreview.setText("(receiving\u2026)");
            });
        }
        @Override public void onReceiveProgress(int done, int total) {
            SwingUtilities.invokeLater(() -> { progress.setValue(done);
                progressLabel.setText("Receiving " + done + "/" + total); });
        }
        @Override public void onReceiveComplete(String filename, byte[] data) {
            // Save to disk, then show it.
            File out = uniqueFile(saveDir, filename);
            String savedPath;
            try {
                Files.write(out.toPath(), data);
                savedPath = out.getAbsolutePath();
                log("Saved received file to " + savedPath);
            } catch (Exception ex) {
                savedPath = "(save failed: " + ex.getMessage() + ")";
                log("Could not save received file: " + ex.getMessage());
            }
            final String shownPath = savedPath;
            SwingUtilities.invokeLater(() -> {
                progress.setValue(progress.getMaximum());
                progressLabel.setText("Received");
                ImageIcon icon = scaleToFit(data, PREVIEW);
                rxPreview.setIcon(icon);
                rxPreview.setText(icon == null ? "(received, not an image)" : null);
                rxInfo.setText(filename + "  —  " + humanSize(data.length));
                JOptionPane.showMessageDialog(LoRaFileTransferGUI.this,
                        "Received " + filename + "\nSaved to:\n" + shownPath,
                        "Incoming file", JOptionPane.INFORMATION_MESSAGE);
            });
        }
    }

    // ====================== serial reader ======================
    // Accumulates raw bytes and emits one trimmed line per newline.

    private static class SerialLineReader implements SerialPortDataListener {
        private final java.util.function.Consumer<String> lineConsumer;
        private final StringBuilder buffer = new StringBuilder();
        SerialLineReader(java.util.function.Consumer<String> lineConsumer) { this.lineConsumer = lineConsumer; }

        @Override public int getListeningEvents() { return SerialPort.LISTENING_EVENT_DATA_AVAILABLE; }

        @Override public void serialEvent(SerialPortEvent event) {
            if (event.getEventType() != SerialPort.LISTENING_EVENT_DATA_AVAILABLE) return;
            SerialPort port = event.getSerialPort();
            int avail = port.bytesAvailable();
            if (avail <= 0) return;

            byte[] buf = new byte[avail];
            int n = port.readBytes(buf, buf.length);
            if (n <= 0) return;

            buffer.append(new String(buf, 0, n, StandardCharsets.UTF_8));
            int nl;
            while ((nl = buffer.indexOf("\n")) != -1) {
                String line = buffer.substring(0, nl).trim();
                buffer.delete(0, nl + 1);
                if (!line.isEmpty()) lineConsumer.accept(line);
            }
        }
    }

    // ====================== small helpers ======================

    private void log(String msg) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(msg + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    /** Decode image bytes and scale to fit a square box, preserving aspect ratio. */
    private static ImageIcon scaleToFit(byte[] bytes, int box) {
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
            if (img == null) return null;
            int w = img.getWidth(), h = img.getHeight();
            double s = Math.min(box / (double) w, box / (double) h);
            int nw = Math.max(1, (int) Math.round(w * s));
            int nh = Math.max(1, (int) Math.round(h * s));
            Image scaled = img.getScaledInstance(nw, nh, Image.SCALE_SMOOTH);
            return new ImageIcon(scaled);
        } catch (Exception e) {
            return null;
        }
    }

    private static File uniqueFile(File dir, String name) {
        File f = new File(dir, name);
        if (!f.exists()) return f;
        String base = name, ext = "";
        int dot = name.lastIndexOf('.');
        if (dot > 0) { base = name.substring(0, dot); ext = name.substring(dot); }
        for (int i = 1; ; i++) {
            File c = new File(dir, base + " (" + i + ")" + ext);
            if (!c.exists()) return c;
        }
    }

    private static String humanSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }

    // ====================== main ======================

    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
        catch (Exception ignore) { }
        SwingUtilities.invokeLater(() -> new LoRaFileTransferGUI().setVisible(true));
    }
}
