package xxx.simple;

import com.fazecast.jSerialComm.SerialPort;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Swing front end for the LoRa dongle. Every radio behaviour lives in the
 * single-responsibility classes behind {@link Radio}; this class only builds the
 * UI and marshals between Swing (the event-dispatch thread) and the radio
 * (background threads, because tuning/scanning enter AT command mode and block).
 *
 * Centrepiece is the {@link SpectrumPanel} live band visualizer. The other
 * controls cover the five features: tuning, scanning, messaging, beaconing /
 * peer discovery, and file transfer, plus "join net" for sharing a channel.
 *
 * Run the GUI:        java -cp <classpath-with-jSerialComm> xxx.simple.RadioDashboard
 * Preview visualizer: java -cp ... xxx.simple.RadioDashboard --demo   (no hardware)
 */
public class RadioDashboard extends JFrame {

    private static final int MINCH = RadioConfig.MIN_CHANNEL;
    private static final int MAXCH = RadioConfig.MAX_CHANNEL;

    private final DeviceManager dm = new DeviceManager();
    private Radio radio;

    // background executor for blocking radio ops (serialised: the module can't
    // run two command sessions at once anyway).
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "radio-io");
        t.setDaemon(true);
        return t;
    });

    private volatile boolean monitoring = false;
    private int operatingChannel = -1;

    // Components toggled by connection state.
    private final List<JComponent> connControls = new ArrayList<>();  // enabled when connected
    private final List<JComponent> preControls = new ArrayList<>();   // enabled when disconnected

    private final SpectrumPanel spectrum = new SpectrumPanel();

    // connection / identity
    private JComboBox<String> portCombo;
    private JButton refreshBtn, openBtn, closeBtn;
    private JSpinner addrSpinner;
    private JTextField nameField;
    private JRadioButton hfBtn, lfBtn;

    // tuning
    private JTextField freqField;
    private JButton freqTuneBtn, chTuneBtn;
    private JSpinner chSpinner;
    private JLabel currentLabel;

    // module net
    private JSpinner moduleAddrSpinner, moduleChSpinner;
    private JButton joinBtn;

    // visualizer controls
    private JTextField dwellField, stepField;
    private JButton monitorBtn, sweepBtn, stopVizBtn;

    // messaging
    private JTextField msgDst, msgField;
    private JButton sendBtn, broadcastBtn;
    private JTextArea msgArea;

    // scanning
    private JSpinner scanStart, scanEnd, scanStep;
    private JTextField scanDwell;
    private JButton scanBtn, scanBandBtn;
    private JLabel scanResult;

    // peers
    private JButton beaconStartBtn, beaconStopBtn;
    private PeerModel peerModel;
    private Timer peerTimer;

    // files
    private JTextField fileDst;
    private JButton chooseBtn, sendFileBtn;
    private JLabel fileLabel;
    private JProgressBar fileProgress;
    private File chosenFile;

    // console + status
    private JTextArea console;
    private JLabel status;

    public RadioDashboard() {
        super("LoRa Dongle Console");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout(6, 6));

        add(buildTopPanel(), BorderLayout.NORTH);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                buildVisualizerPanel(), buildTabs());
        split.setResizeWeight(0.5);
        split.setBorder(null);
        add(split, BorderLayout.CENTER);

        status = new JLabel("Not connected.");
        status.setBorder(BorderFactory.createEmptyBorder(2, 8, 4, 8));
        add(status, BorderLayout.SOUTH);

        peerTimer = new Timer(1000, e -> {
            if (radio != null) peerModel.setPeers(radio.peers());
        });

        setConnected(false);
        setSize(1000, 780);
        setLocationRelativeTo(null);
    }

    // --- panel construction -------------------------------------------------

    private JPanel buildTopPanel() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(BorderFactory.createTitledBorder("Connection & tuning"));

        // Row 1: port / identity / band
        JPanel r1 = row();
        portCombo = new JComboBox<>();
        pre(portCombo);
        refreshBtn = btn("Refresh", e -> populatePorts());
        pre(refreshBtn);
        openBtn = btn("Open", e -> doOpen());
        closeBtn = btn("Close", e -> doClose());
        addrSpinner = new JSpinner(new SpinnerNumberModel(1, 0, 65534, 1));
        pre(addrSpinner);
        nameField = new JTextField("node-1", 8);
        pre(nameField);
        hfBtn = new JRadioButton("HF 850-930", true);
        lfBtn = new JRadioButton("LF 410-490");
        ButtonGroup bg = new ButtonGroup();
        bg.add(hfBtn); bg.add(lfBtn);
        pre(hfBtn); pre(lfBtn);
        r1.add(new JLabel("Port:")); r1.add(portCombo); r1.add(refreshBtn);
        r1.add(openBtn); r1.add(closeBtn);
        r1.add(sep()); r1.add(new JLabel("Addr:")); r1.add(addrSpinner);
        r1.add(new JLabel("Name:")); r1.add(nameField);
        r1.add(sep()); r1.add(new JLabel("Band:")); r1.add(hfBtn); r1.add(lfBtn);

        // Row 2: tuning
        JPanel r2 = row();
        freqField = new JTextField("868", 6);
        freqTuneBtn = btn("Tune freq", e -> doTuneFreq());
        chSpinner = new JSpinner(new SpinnerNumberModel(18, MINCH, MAXCH, 1));
        chTuneBtn = btn("Tune ch", e -> doTuneChannel());
        currentLabel = new JLabel("Current: --");
        conn(freqField); conn(freqTuneBtn); conn(chSpinner); conn(chTuneBtn);
        r2.add(new JLabel("Freq MHz:")); r2.add(freqField); r2.add(freqTuneBtn);
        r2.add(sep()); r2.add(new JLabel("Channel:")); r2.add(chSpinner); r2.add(chTuneBtn);
        r2.add(sep()); r2.add(currentLabel);

        // Row 3: module net
        JPanel r3 = row();
        moduleAddrSpinner = new JSpinner(new SpinnerNumberModel(1, 0, 65535, 1));
        moduleChSpinner = new JSpinner(new SpinnerNumberModel(18, MINCH, MAXCH, 1));
        joinBtn = btn("Apply to module", e -> doJoinNet());
        conn(moduleAddrSpinner); conn(moduleChSpinner); conn(joinBtn);
        r3.add(new JLabel("Net \u2192 module RF addr:")); r3.add(moduleAddrSpinner);
        r3.add(new JLabel("channel:")); r3.add(moduleChSpinner); r3.add(joinBtn);
        r3.add(new JLabel("  (units sharing addr+channel hear each other)"));

        p.add(r1); p.add(r2); p.add(r3);
        return p;
    }

    private JPanel buildVisualizerPanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createTitledBorder("Live band activity (survey spectrum + waterfall)"));

        JPanel controls = row();
        dwellField = new JTextField("120", 5);
        stepField = new JTextField("1", 3);
        monitorBtn = btn("Monitor band", e -> toggleMonitor());
        sweepBtn = btn("Single sweep", e -> doSingleSweep());
        stopVizBtn = btn("Stop", e -> stopMonitor());
        conn(dwellField); conn(stepField); conn(monitorBtn); conn(sweepBtn); conn(stopVizBtn);
        controls.add(new JLabel("Dwell ms:")); controls.add(dwellField);
        controls.add(new JLabel("Step:")); controls.add(stepField);
        controls.add(monitorBtn); controls.add(sweepBtn); controls.add(stopVizBtn);

        p.add(controls, BorderLayout.NORTH);
        p.add(spectrum, BorderLayout.CENTER);
        return p;
    }

    private JTabbedPane buildTabs() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Messages", buildMessagesTab());
        tabs.addTab("Scan", buildScanTab());
        tabs.addTab("Peers", buildPeersTab());
        tabs.addTab("Files", buildFilesTab());
        tabs.addTab("Console", buildConsoleTab());
        return tabs;
    }

    private JPanel buildMessagesTab() {
        JPanel p = new JPanel(new BorderLayout(4, 4));
        JPanel top = row();
        msgDst = new JTextField("", 6);
        msgField = new JTextField(28);
        sendBtn = btn("Send", e -> doSend());
        broadcastBtn = btn("Broadcast", e -> doBroadcast());
        conn(msgDst); conn(msgField); conn(sendBtn); conn(broadcastBtn);
        top.add(new JLabel("To (blank=broadcast):")); top.add(msgDst);
        top.add(new JLabel("Message:")); top.add(msgField);
        top.add(sendBtn); top.add(broadcastBtn);

        msgArea = new JTextArea(8, 60);
        msgArea.setEditable(false);
        p.add(top, BorderLayout.NORTH);
        p.add(new JScrollPane(msgArea), BorderLayout.CENTER);
        return p;
    }

    private JPanel buildScanTab() {
        JPanel p = new JPanel(new BorderLayout(4, 4));
        JPanel top = row();
        scanStart = new JSpinner(new SpinnerNumberModel(MINCH, MINCH, MAXCH, 1));
        scanEnd = new JSpinner(new SpinnerNumberModel(MAXCH, MINCH, MAXCH, 1));
        scanStep = new JSpinner(new SpinnerNumberModel(1, 1, 80, 1));
        scanDwell = new JTextField("400", 5);
        scanBtn = btn("Scan range", e -> doScan());
        scanBandBtn = btn("Scan whole band", e -> doScanBand());
        conn(scanStart); conn(scanEnd); conn(scanStep); conn(scanDwell); conn(scanBtn); conn(scanBandBtn);
        top.add(new JLabel("Start:")); top.add(scanStart);
        top.add(new JLabel("End:")); top.add(scanEnd);
        top.add(new JLabel("Step:")); top.add(scanStep);
        top.add(new JLabel("Dwell ms:")); top.add(scanDwell);
        top.add(scanBtn); top.add(scanBandBtn);

        scanResult = new JLabel("Scan stops on the first channel with traffic.");
        scanResult.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        p.add(top, BorderLayout.NORTH);
        p.add(scanResult, BorderLayout.CENTER);
        return p;
    }

    private JPanel buildPeersTab() {
        JPanel p = new JPanel(new BorderLayout(4, 4));
        JPanel top = row();
        beaconStartBtn = btn("Start beacon", e -> bg(() -> { if (radio != null) radio.startBeacon(); }));
        beaconStopBtn = btn("Stop beacon", e -> bg(() -> { if (radio != null) radio.stopBeacon(); }));
        conn(beaconStartBtn); conn(beaconStopBtn);
        top.add(beaconStartBtn); top.add(beaconStopBtn);
        top.add(new JLabel("  Peers heard on this channel:"));

        peerModel = new PeerModel();
        JTable table = new JTable(peerModel);
        p.add(top, BorderLayout.NORTH);
        p.add(new JScrollPane(table), BorderLayout.CENTER);
        return p;
    }

    private JPanel buildFilesTab() {
        JPanel p = new JPanel(new BorderLayout(4, 4));
        JPanel top = row();
        fileDst = new JTextField("", 6);
        chooseBtn = btn("Choose file\u2026", e -> doChooseFile());
        sendFileBtn = btn("Send file", e -> doSendFile());
        fileLabel = new JLabel("No file selected.");
        conn(fileDst); conn(chooseBtn); conn(sendFileBtn);
        top.add(new JLabel("To (blank=broadcast):")); top.add(fileDst);
        top.add(chooseBtn); top.add(sendFileBtn); top.add(fileLabel);

        fileProgress = new JProgressBar(0, 100);
        fileProgress.setStringPainted(true);
        JPanel center = new JPanel(new BorderLayout());
        center.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        center.add(fileProgress, BorderLayout.NORTH);
        center.add(new JLabel("Received files are written to ./received"), BorderLayout.CENTER);

        p.add(top, BorderLayout.NORTH);
        p.add(center, BorderLayout.CENTER);
        return p;
    }

    private JPanel buildConsoleTab() {
        JPanel p = new JPanel(new BorderLayout());
        console = new JTextArea(10, 80);
        console.setEditable(false);
        p.add(new JScrollPane(console), BorderLayout.CENTER);
        return p;
    }

    // --- actions ------------------------------------------------------------

    private void populatePorts() {
        portCombo.removeAllItems();
        for (String name : DeviceManager.listPorts()) portCombo.addItem(name);
        if (portCombo.getItemCount() == 0) status("No serial ports found - plug in the dongle, then Refresh.");
    }

    private void doOpen() {
        String port = (String) portCombo.getSelectedItem();
        if (port == null || port.isBlank()) { status("Pick a port first."); return; }
        int addr = spinnerInt(addrSpinner);
        String name = nameField.getText().trim();
        if (name.isEmpty()) name = "node-" + addr;
        RadioConfig.Band band = hfBtn.isSelected() ? RadioConfig.Band.HF : RadioConfig.Band.LF;
        final String nm = name;

        bg(() -> {
            boolean ok = dm.open(port);
            if (!ok) { ui(() -> status("Could not open '" + port + "' (check name / dialout permissions).")); return; }
            SerialPort sp = dm.getSerialPort();
            Radio r = new Radio(sp, addr, band, nm, Path.of("received"));
            r.setLog(this::log);
            r.setAtTrace(line -> log("[AT] " + line));
            r.setMessageHandler((src, text) ->
                    ui(() -> appendMsg(String.format("0x%04X >> %s", src, text))));
            r.setActivitySink((ch, bytes, ts) -> ui(() -> spectrum.pushLiveActivity(bytes)));
            r.setFileReceiveSink((src, fname, bytes, crcOk) -> ui(() -> {
                appendMsg(String.format("[file] %s from 0x%04X (%d bytes) %s",
                        fname, src, bytes, crcOk ? "OK" : "FAILED"));
                status(crcOk ? "Received file: " + fname : "File receive failed: " + fname);
            }));
            radio = r;
            ui(() -> {
                spectrum.setBand(band.baseMHz, MINCH, MAXCH);
                setConnected(true);
                peerTimer.start();
                status(String.format("Connected on %s as \"%s\" (0x%04X), %s band.", port, nm, addr, band));
                log("Radio ready. Tune a frequency, then Monitor band to see activity.");
            });
        });
    }

    private void doClose() {
        bg(() -> {
            monitoring = false;
            if (radio != null) { radio.cancelScan(); radio.shutdown(); }
            dm.close();
            radio = null;
            ui(() -> {
                setConnected(false);
                peerTimer.stop();
                spectrum.clear();
                currentLabel.setText("Current: --");
                status("Disconnected.");
            });
        });
    }

    private void doTuneFreq() {
        if (radio == null) return;
        double mhz = parseDouble(freqField, 868);
        status(String.format("Tuning to %.0f MHz\u2026", mhz));
        bg(() -> {
            int ch = radio.setFrequency(mhz);
            boolean ack = radio.lastCommandAcknowledged();
            operatingChannel = ch;
            ui(() -> {
                spectrum.setCurrentChannel(ch);
                chSpinner.setValue(ch);
                updateCurrentLabel();
                status(ack
                        ? String.format("Tuned to %.0f MHz (ch %d) \u2014 module acknowledged.", radio.currentFrequencyMHz(), ch)
                        : String.format("Sent tune to ch %d but module did not acknowledge \u2014 see Console.", ch));
            });
        });
    }

    private void doTuneChannel() {
        if (radio == null) return;
        int wanted = spinnerInt(chSpinner);
        status("Tuning to channel " + wanted + "\u2026");
        bg(() -> {
            int ch = radio.setChannel(wanted);
            boolean ack = radio.lastCommandAcknowledged();
            operatingChannel = ch;
            ui(() -> {
                spectrum.setCurrentChannel(ch);
                updateCurrentLabel();
                status(ack
                        ? String.format("Tuned to ch %d (%.0f MHz) \u2014 module acknowledged.", ch, radio.currentFrequencyMHz())
                        : String.format("Sent tune to ch %d but module did not acknowledge \u2014 see Console.", ch));
            });
        });
    }

    private void doJoinNet() {
        if (radio == null) return;
        int maddr = spinnerInt(moduleAddrSpinner);
        int mch = spinnerInt(moduleChSpinner);
        bg(() -> {
            radio.joinNet(maddr, mch);
            operatingChannel = mch;
            ui(() -> { spectrum.setCurrentChannel(mch); chSpinner.setValue(mch); updateCurrentLabel(); });
        });
    }

    private void toggleMonitor() {
        if (monitoring) stopMonitor(); else startMonitor();
    }

    private void startMonitor() {
        if (radio == null || monitoring) return;
        monitoring = true;
        long dwell = parseLong(dwellField, 120);
        int step = parseInt(stepField, 1);
        ui(() -> { monitorBtn.setText("Stop monitoring"); sweepBtn.setEnabled(false); });
        bg(() -> {
            while (monitoring) {
                radio.survey(MINCH, MAXCH, step, dwell, (ch, mhz, bytes) ->
                        ui(() -> { spectrum.setChannelActivity(ch, bytes); spectrum.setCurrentChannel(ch); }));
                ui(spectrum::commitWaterfallRow);
            }
            if (operatingChannel >= 0) radio.setChannel(operatingChannel);
            ui(() -> {
                if (operatingChannel >= 0) spectrum.setCurrentChannel(operatingChannel);
                monitorBtn.setText("Monitor band");
                sweepBtn.setEnabled(true);
                updateCurrentLabel();
            });
        });
    }

    private void stopMonitor() {
        monitoring = false;
        if (radio != null) radio.cancelScan();
    }

    private void doSingleSweep() {
        if (radio == null || monitoring) return;
        long dwell = parseLong(dwellField, 120);
        int step = parseInt(stepField, 1);
        ui(() -> { sweepBtn.setEnabled(false); monitorBtn.setEnabled(false); });
        bg(() -> {
            radio.survey(MINCH, MAXCH, step, dwell, (ch, mhz, bytes) ->
                    ui(() -> { spectrum.setChannelActivity(ch, bytes); spectrum.setCurrentChannel(ch); }));
            ui(spectrum::commitWaterfallRow);
            if (operatingChannel >= 0) radio.setChannel(operatingChannel);
            ui(() -> {
                if (operatingChannel >= 0) spectrum.setCurrentChannel(operatingChannel);
                sweepBtn.setEnabled(true); monitorBtn.setEnabled(true); updateCurrentLabel();
            });
        });
    }

    private void doSend() {
        if (radio == null) return;
        String dst = msgDst.getText().trim();
        String msg = msgField.getText();
        if (msg.isEmpty()) return;
        bg(() -> {
            if (dst.isEmpty()) radio.sendBroadcast(msg);
            else radio.sendMessage(Integer.decode(dst) & 0xFFFF, msg);
            ui(() -> {
                appendMsg("me " + (dst.isEmpty() ? "(broadcast)" : "\u2192 " + dst) + ": " + msg);
                msgField.setText("");
            });
        });
    }

    private void doBroadcast() {
        if (radio == null) return;
        String msg = msgField.getText();
        if (msg.isEmpty()) return;
        bg(() -> {
            radio.sendBroadcast(msg);
            ui(() -> { appendMsg("me (broadcast): " + msg); msgField.setText(""); });
        });
    }

    private void doScan() {
        if (radio == null) return;
        int s = spinnerInt(scanStart), e = spinnerInt(scanEnd), st = spinnerInt(scanStep);
        long d = parseLong(scanDwell, 400);
        ui(() -> { scanBtn.setEnabled(false); scanBandBtn.setEnabled(false); scanResult.setText("Scanning\u2026"); });
        bg(() -> {
            SignalScanner.ScanResult r = radio.scan(s, e, st, d);
            operatingChannel = r.channel;
            ui(() -> {
                scanResult.setText(r.toString());
                spectrum.setCurrentChannel(r.channel);
                chSpinner.setValue(r.channel);
                updateCurrentLabel();
                scanBtn.setEnabled(true); scanBandBtn.setEnabled(true);
            });
        });
    }

    private void doScanBand() {
        if (radio == null) return;
        long d = parseLong(scanDwell, 400);
        ui(() -> { scanBtn.setEnabled(false); scanBandBtn.setEnabled(false); scanResult.setText("Scanning band\u2026"); });
        bg(() -> {
            SignalScanner.ScanResult r = radio.scanBand(d);
            operatingChannel = r.channel;
            ui(() -> {
                scanResult.setText(r.toString());
                spectrum.setCurrentChannel(r.channel);
                chSpinner.setValue(r.channel);
                updateCurrentLabel();
                scanBtn.setEnabled(true); scanBandBtn.setEnabled(true);
            });
        });
    }

    private void doChooseFile() {
        JFileChooser fc = new JFileChooser();
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            chosenFile = fc.getSelectedFile();
            fileLabel.setText(chosenFile.getName() + " (" + chosenFile.length() + " bytes)");
        }
    }

    private void doSendFile() {
        if (radio == null || chosenFile == null) { status("Choose a file first."); return; }
        String dst = fileDst.getText().trim();
        int dstAddr = dst.isEmpty() ? Protocol.BROADCAST_ADDR : (Integer.decode(dst) & 0xFFFF);
        final File f = chosenFile;
        ui(() -> { fileProgress.setValue(0); sendFileBtn.setEnabled(false); });
        bg(() -> {
            try {
                radio.sendFile(dstAddr, f, (name, sent, total) -> ui(() -> {
                    if (total > 0) { fileProgress.setMaximum(total); fileProgress.setValue(sent); }
                    fileProgress.setString(name + "  " + sent + "/" + total);
                }));
                ui(() -> status("File sent: " + f.getName()));
            } catch (Exception ex) {
                ui(() -> status("File error: " + ex.getMessage()));
            } finally {
                ui(() -> sendFileBtn.setEnabled(true));
            }
        });
    }

    // --- demo (no hardware) -------------------------------------------------

    /** Animate the visualizer with synthetic data so it can be seen without a dongle. */
    public void startDemo() {
        spectrum.setBand(RadioConfig.Band.HF.baseMHz, MINCH, MAXCH);
        spectrum.setCurrentChannel(18);
        status("DEMO MODE \u2014 synthetic band activity, no hardware connected.");
        final Random rnd = new Random();
        final int[] signals = {18, 40, 65};
        Timer t = new Timer(120, e -> {
            for (int ch = MINCH; ch <= MAXCH; ch++) {
                int val = rnd.nextInt(6); // background noise
                for (int s : signals) {
                    int dch = Math.abs(ch - s);
                    if (dch <= 1) val += 130 - dch * 45 + rnd.nextInt(40);
                }
                spectrum.setChannelActivity(ch, val);
            }
            spectrum.pushLiveActivity(20 + rnd.nextInt(80));
            spectrum.commitWaterfallRow();
        });
        t.setRepeats(true);
        t.start();
    }

    // --- helpers ------------------------------------------------------------

    private void setConnected(boolean connected) {
        openBtn.setEnabled(!connected);
        closeBtn.setEnabled(connected);
        for (JComponent c : connControls) c.setEnabled(connected);
        for (JComponent c : preControls) c.setEnabled(!connected);
    }

    private void updateCurrentLabel() {
        if (radio == null || radio.currentChannel() < 0) { currentLabel.setText("Current: --"); return; }
        currentLabel.setText(String.format("Current: %.0f MHz (ch %d)",
                radio.currentFrequencyMHz(), radio.currentChannel()));
    }

    private void log(String s) {
        ui(() -> { console.append(s + "\n"); console.setCaretPosition(console.getDocument().getLength()); });
    }

    private void appendMsg(String s) {
        ui(() -> { msgArea.append(s + "\n"); msgArea.setCaretPosition(msgArea.getDocument().getLength()); });
    }

    private void status(String s) { ui(() -> status.setText(s)); }

    private void bg(Runnable r) {
        io.submit(() -> {
            try { r.run(); }
            catch (Exception e) { log("Error: " + e); }
        });
    }

    private static void ui(Runnable r) { SwingUtilities.invokeLater(r); }

    private JButton btn(String label, java.awt.event.ActionListener a) {
        JButton b = new JButton(label);
        b.addActionListener(a);
        return b;
    }

    private static JPanel row() { return new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 3)); }

    private static JComponent sep() {
        JLabel l = new JLabel(" | ");
        return l;
    }

    private JComponent conn(JComponent c) { connControls.add(c); return c; }
    private JComponent pre(JComponent c) { preControls.add(c); return c; }

    private static int spinnerInt(JSpinner s) { return ((Number) s.getValue()).intValue(); }

    private static int parseInt(JTextField f, int def) {
        try { return Integer.decode(f.getText().trim()); } catch (Exception e) { return def; }
    }
    private static long parseLong(JTextField f, long def) {
        try { return Long.parseLong(f.getText().trim()); } catch (Exception e) { return def; }
    }
    private static double parseDouble(JTextField f, double def) {
        try { return Double.parseDouble(f.getText().trim()); } catch (Exception e) { return def; }
    }

    // --- peer table model ---------------------------------------------------

    private static final class PeerModel extends AbstractTableModel {
        private final String[] cols = {"Address", "Name", "Last seen (s)"};
        private List<Beacon.Peer> rows = new ArrayList<>();

        void setPeers(List<Beacon.Peer> peers) { this.rows = peers; fireTableDataChanged(); }

        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return cols.length; }
        @Override public String getColumnName(int c) { return cols[c]; }

        @Override public Object getValueAt(int r, int c) {
            Beacon.Peer p = rows.get(r);
            return switch (c) {
                case 0 -> String.format("0x%04X", p.address);
                case 1 -> p.name;
                case 2 -> (System.currentTimeMillis() - p.lastSeenMs) / 1000;
                default -> "";
            };
        }
    }

    // --- entry point --------------------------------------------------------

    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
        catch (Exception ignored) { }
        boolean demo = Arrays.asList(args).contains("--demo");
        SwingUtilities.invokeLater(() -> {
            RadioDashboard d = new RadioDashboard();
            d.setVisible(true);
            d.populatePorts();
            if (demo) d.startDemo();
        });
    }
}
