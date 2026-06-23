package xxx.com.pki.gui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Key;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;

/**
 * A Java Swing application to generate PKI certificates for a client/server setup using
 * keytool. This utility provides a graphical interface to create an Enterprise PKI hierarchy,
 * including Root CAs, Intermediate CAs, CRLs, and a Mock ACME/GitOps Provisioning API.
 */
public class PKICertGenV2 extends JFrame {

    // Corporate Base Fields
    private final JTextField tfOrg = new JTextField("OmniCorp", 20);
    private final List<JTextField> ouFields = new ArrayList<>();
    private JPanel ouPanelContainer;
    private final JTextField tfCity = new JTextField("Baltimore", 20);
    private final JTextField tfState = new JTextField("Maryland", 20);
    private final JTextField tfCountry = new JTextField("US", 20);

    // SAN Components
    private final List<SanEntryPanel> sanFields = new ArrayList<>();
    private JPanel sanPanelContainer;

    // Global Settings
    private final JPasswordField tfPassword = new JPasswordField("password", 20);
    private final JTextField tfValidity = new JTextField("365", 5);
    private final JCheckBox chkNeverExpire = new JCheckBox("Never Expire");

    // Specific Entity CNs
    private final JTextField tfCaIdentity = new JTextField("OmniCorp Root CA", 20);
    private final JTextField tfIntCaIdentity = new JTextField("OmniCorp Issuing Intermediate CA", 20);
    private final JTextField tfServerDomain = new JTextField("api.internal.local", 20);
    private final JTextField tfClientName = new JTextField("Jane Doe", 20);

    private final JTextArea logArea = new JTextArea(15, 80);
    private final JButton generateCaButton = new JButton("Generate Root CA");
    private final JButton generateIntCaButton = new JButton("Generate Intermediate CA");
    private final JButton generateServerButton = new JButton("Generate Server Keystore");
    private final JButton generateClientButton = new JButton("Generate Client Keystore");

    private final JComboBox<String> signerCombo = new JComboBox<>(new String[]{"Root CA", "Intermediate CA"});

    private final JLabel caCheckLabel = new JLabel();
    private final JLabel intCaCheckLabel = new JLabel();
    private final JLabel serverCheckLabel = new JLabel();
    private final JLabel clientCheckLabel = new JLabel();

    private final JButton generatePublicKeyButton = new JButton("Create Public Key");
    private final JTextField tfPublicKeyFilename = new JTextField("public-key.pem", 20);
    private final JLabel publicKeyCheckLabel = new JLabel();
    private JPanel publicKeyPanel;

    private JProgressBar progressBar;

    private final Map<String, byte[]> generatedFiles = new ConcurrentHashMap<>();
    private DefaultTableModel fileTableModel;
    private JTable fileTable;

    // Context menu items
    private JMenuItem viewItem;
    private JMenuItem saveItem;
    private JMenuItem createDerItem;
    private JMenuItem createPemItem;
    private JMenuItem revokeItem;
    private JMenuItem exportItem;

    // State Trackers
    private String activeCaFilename = null;
    private String activeIntCaFilename = null;
    private String activeClientFilename = null;
    private final List<String> revokedSerials = new CopyOnWriteArrayList<>();

    // Embedded API Server
    private HttpServer apiServer;
    private final JButton btnToggleApi = new JButton("Start API Server (Port 8080)");
    private final JTextArea apiLogArea = new JTextArea(15, 80);

    private static Image appIcon;

    private static final Color KINDA_GRAY = new Color(135, 135, 135);
    private static final SimpleDateFormat SDF = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private record CommandResult(int exitCode, String output) {
        public boolean isSuccess() {
            return exitCode == 0;
        }
    }

    // Helper class for dynamic SAN inputs
    private class SanEntryPanel extends JPanel {
        JComboBox<String> typeCombo;
        JTextField valueField;

        public SanEntryPanel(boolean isFirst) {
            setLayout(new BorderLayout(5, 0));
            setBorder(new EmptyBorder(0, 0, 2, 0));

            typeCombo = new JComboBox<>(new String[]{"dns", "ip"});
            valueField = new JTextField(isFirst ? "127.0.0.1" : "", 14);

            JButton button = new JButton(isFirst ? "+" : "-");
            button.setMargin(new Insets(2, 5, 2, 5));
            button.setFont(button.getFont().deriveFont(Font.BOLD));

            if (isFirst) {
                button.addActionListener(e -> addSanField());
            } else {
                button.addActionListener(e -> removeSanField(this));
            }

            add(typeCombo, BorderLayout.WEST);
            add(valueField, BorderLayout.CENTER);
            add(button, BorderLayout.EAST);
        }
    }

    public PKICertGenV2() {
        super("Enterprise PKI Sandbox");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        initComponents();
        setSize(900, 750);
        setLocationRelativeTo(null);
        setVisible(true);
        validateEnvironment();
    }

    private void initComponents() {
        try {
            Image image = Toolkit.getDefaultToolkit().getImage(getClass().getResource("/png/pki/storm.png"));
            appIcon = image.getScaledInstance(40, 40, Image.SCALE_SMOOTH);
            setIconImage(appIcon);
        } catch (Exception e) {
            // Fallback if icon missing
        }

        JTabbedPane mainTabs = new JTabbedPane();

        // ---------------------------------------------------------
        // TAB 1: PKI Generator (The core GUI)
        // ---------------------------------------------------------
        JPanel generatorTab = new JPanel(new BorderLayout(10, 0));
        generatorTab.setBorder(new EmptyBorder(10, 10, 10, 10));

        // --- Configuration Panel ---
        JPanel configPanel = new JPanel(new GridBagLayout());
        configPanel.setBorder(BorderFactory.createTitledBorder("Corporate Identity Base"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 5, 2, 5);
        gbc.anchor = GridBagConstraints.WEST;
        int gridY = 0;

        addConfigRow(configPanel, gbc, gridY++, "Organization (O):", tfOrg);

        gbc.gridy = gridY++;
        gbc.gridx = 0;
        gbc.anchor = GridBagConstraints.NORTHEAST;
        configPanel.add(new JLabel("Organizational Unit (OU):"), gbc);
        ouPanelContainer = new JPanel();
        ouPanelContainer.setLayout(new BoxLayout(ouPanelContainer, BoxLayout.Y_AXIS));
        addOuField();
        gbc.gridx = 1;
        gbc.anchor = GridBagConstraints.WEST;
        configPanel.add(ouPanelContainer, gbc);

        addConfigRow(configPanel, gbc, gridY++, "City/Locality (L):", tfCity);
        addConfigRow(configPanel, gbc, gridY++, "State/Province (S):", tfState);
        addConfigRow(configPanel, gbc, gridY++, "Country Code (C):", tfCountry);

        gbc.gridy = gridY++;
        gbc.gridx = 0;
        gbc.anchor = GridBagConstraints.NORTHEAST;
        configPanel.add(new JLabel("Subject Alt Names (SAN):"), gbc);
        sanPanelContainer = new JPanel();
        sanPanelContainer.setLayout(new BoxLayout(sanPanelContainer, BoxLayout.Y_AXIS));
        addSanField();
        gbc.gridx = 1;
        gbc.anchor = GridBagConstraints.WEST;
        configPanel.add(sanPanelContainer, gbc);

        JPanel passwordPanel = new JPanel(new BorderLayout());
        passwordPanel.add(tfPassword, BorderLayout.CENTER);
        ImageIcon eyeOpenIcon = recolorIcon(new ImageIcon(new ImageIcon(Objects.requireNonNull(getClass().getResource("/png/password/eye-open.png"))).getImage().getScaledInstance(20, 20, Image.SCALE_SMOOTH)), KINDA_GRAY);
        ImageIcon eyeShutIcon = recolorIcon(new ImageIcon(new ImageIcon(Objects.requireNonNull(getClass().getResource("/png/password/eye-shut.png"))).getImage().getScaledInstance(20, 20, Image.SCALE_SMOOTH)), KINDA_GRAY);
        JLabel passwordRevealLabel = new JLabel(eyeShutIcon);
        passwordRevealLabel.setBorder(new EmptyBorder(0, 5, 0, 5));
        passwordRevealLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));
        passwordPanel.add(passwordRevealLabel, BorderLayout.EAST);
        char defaultEchoChar = tfPassword.getEchoChar();
        passwordRevealLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                passwordRevealLabel.setIcon(eyeOpenIcon);
                tfPassword.setEchoChar((char) 0);
            }
            @Override
            public void mouseReleased(MouseEvent e) {
                passwordRevealLabel.setIcon(eyeShutIcon);
                tfPassword.setEchoChar(defaultEchoChar);
            }
        });
        addConfigRow(configPanel, gbc, gridY++, "Keystore Password:", passwordPanel);

        JPanel validityPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        validityPanel.add(tfValidity);
        validityPanel.add(Box.createHorizontalStrut(5));
        validityPanel.add(chkNeverExpire);
        addConfigRow(configPanel, gbc, gridY++, "Expiration (Days):", validityPanel);
        chkNeverExpire.addActionListener(e -> tfValidity.setEnabled(!chkNeverExpire.isSelected()));

        addConfigRow(configPanel, gbc, gridY++, "Sign Entities With:", signerCombo);

        // --- Generation Control Panel ---
        JPanel generatorPanel = new JPanel();
        generatorPanel.setLayout(new BoxLayout(generatorPanel, BoxLayout.Y_AXIS));
        generatorPanel.setBorder(BorderFactory.createTitledBorder("Entity Generation"));

        ImageIcon caIcon = new ImageIcon(new ImageIcon(Objects.requireNonNull(getClass().getResource("/png/pki/certificate5.png"))).getImage().getScaledInstance(24, 24, Image.SCALE_SMOOTH));
        ImageIcon serverIcon = new ImageIcon(new ImageIcon(Objects.requireNonNull(getClass().getResource("/png/server2.png"))).getImage().getScaledInstance(22, 22, Image.SCALE_SMOOTH));
        ImageIcon clientIcon = new ImageIcon(new ImageIcon(Objects.requireNonNull(getClass().getResource("/png/client.png"))).getImage().getScaledInstance(22, 22, Image.SCALE_SMOOTH));
        ImageIcon publicKeyIcon = new ImageIcon(new ImageIcon(Objects.requireNonNull(getClass().getResource("/png/pki/publicKey3.png"))).getImage().getScaledInstance(22, 22, Image.SCALE_SMOOTH));

        Icon checkIcon = createCheckIcon();
        caCheckLabel.setIcon(checkIcon);
        intCaCheckLabel.setIcon(checkIcon);
        serverCheckLabel.setIcon(checkIcon);
        clientCheckLabel.setIcon(checkIcon);
        caCheckLabel.setVisible(false);
        intCaCheckLabel.setVisible(false);
        serverCheckLabel.setVisible(false);
        clientCheckLabel.setVisible(false);

        JPanel caPanel = createStepPanel("Root CA (CN):", caIcon, tfCaIdentity, generateCaButton, caCheckLabel);
        JPanel intCaPanel = createStepPanel("Intermediate CA (CN):", caIcon, tfIntCaIdentity, generateIntCaButton, intCaCheckLabel);
        JPanel serverPanel = createStepPanel("Server Domain (CN):", serverIcon, tfServerDomain, generateServerButton, serverCheckLabel);
        JPanel clientPanel = createStepPanel("User/Employee Name (CN):", clientIcon, tfClientName, generateClientButton, clientCheckLabel);

        generatorPanel.add(caPanel);
        generatorPanel.add(Box.createRigidArea(new Dimension(0, 5)));
        generatorPanel.add(intCaPanel);
        generatorPanel.add(Box.createRigidArea(new Dimension(0, 5)));
        generatorPanel.add(serverPanel);
        generatorPanel.add(Box.createRigidArea(new Dimension(0, 5)));
        generatorPanel.add(clientPanel);

        publicKeyCheckLabel.setIcon(checkIcon);
        publicKeyCheckLabel.setVisible(false);
        publicKeyPanel = createStepPanel("Client Public Key", publicKeyIcon, tfPublicKeyFilename, generatePublicKeyButton, publicKeyCheckLabel);
        publicKeyPanel.setVisible(false);
        publicKeyPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 0));
        generatorPanel.add(Box.createRigidArea(new Dimension(0, 5)));
        generatorPanel.add(publicKeyPanel);

        JPanel topPanel = new JPanel(new BorderLayout(10, 0));
        topPanel.add(configPanel, BorderLayout.WEST);
        topPanel.add(generatorPanel, BorderLayout.CENTER);

        JPanel topSectionPanel = new JPanel(new BorderLayout());
        topSectionPanel.add(topPanel, BorderLayout.CENTER);

        JPanel clearButtonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        clearButtonPanel.setBorder(new EmptyBorder(5, 5, 2, 5));
        JButton clearButton = new JButton("Reset");
        clearButton.addActionListener(this::clearAllAction);
        clearButtonPanel.add(clearButton);
        topSectionPanel.add(clearButtonPanel, BorderLayout.SOUTH);

        // --- Tabbed Log/File Panel ---
        JTabbedPane bottomTabbedPane = new JTabbedPane();
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane logScrollPane = new JScrollPane(logArea);
        bottomTabbedPane.addTab("Output Log", logScrollPane);

        String[] columnNames = {"", "Filename", "Size (bytes)", "Type", "Generated", "Fingerprint (SHA-256)"};
        fileTableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
            @Override
            public Class<?> getColumnClass(int columnIndex) {
                if (columnIndex == 0) return Icon.class;
                return super.getColumnClass(columnIndex);
            }
        };

        fileTable = new JTable(fileTableModel);
        fileTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        fileTable.setRowHeight(24);
        fileTable.getColumnModel().getColumn(0).setMaxWidth(30);
        fileTable.getColumnModel().getColumn(1).setPreferredWidth(220);
        fileTable.getColumnModel().getColumn(2).setPreferredWidth(80);
        fileTable.getColumnModel().getColumn(3).setPreferredWidth(140);
        fileTable.getColumnModel().getColumn(4).setPreferredWidth(140);
        fileTable.getColumnModel().getColumn(5).setPreferredWidth(450);

        // --- Context Menu for File Table ---
        JPopupMenu fileTableContextMenu = new JPopupMenu();
        viewItem = new JMenuItem("View");
        saveItem = new JMenuItem("Save");
        createDerItem = new JMenuItem("Create DER");
        createPemItem = new JMenuItem("Create PEM Bundle");
        revokeItem = new JMenuItem("Revoke & Update CRL");
        exportItem = new JMenuItem("Export");

        viewItem.addActionListener(e -> viewFileAction());
        saveItem.addActionListener(e -> saveSingleFileAction());
        createDerItem.addActionListener(e -> createExtractedFormatAction(false));
        createPemItem.addActionListener(e -> createExtractedFormatAction(true));
        revokeItem.addActionListener(e -> revokeCertificateAction());
        exportItem.addActionListener(e -> exportFilesAction());

        fileTableContextMenu.add(viewItem);
        fileTableContextMenu.add(saveItem);
        fileTableContextMenu.addSeparator();
        fileTableContextMenu.add(createDerItem);
        fileTableContextMenu.add(createPemItem);
        fileTableContextMenu.addSeparator();
        fileTableContextMenu.add(revokeItem);
        fileTableContextMenu.addSeparator();
        fileTableContextMenu.add(exportItem);

        fileTable.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && !e.isPopupTrigger()) {
                    int row = fileTable.rowAtPoint(e.getPoint());
                    if (row >= 0) {
                        String filename = (String) fileTable.getValueAt(row, 1);
                        saveSingleFilePrompt(filename);
                    }
                }
            }
            private void showPopup(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    int row = fileTable.rowAtPoint(e.getPoint());
                    if (row >= 0) {
                        if (!fileTable.isRowSelected(row)) {
                            fileTable.setRowSelectionInterval(row, row);
                        }

                        int[] selectedRows = fileTable.getSelectedRows();
                        boolean canDer = false;
                        boolean canPem = false;
                        boolean canRevoke = false;

                        for (int r : selectedRows) {
                            String filename = (String) fileTable.getValueAt(r, 1);
                            String type = (String) fileTable.getValueAt(r, 3);
                            String lower = filename.toLowerCase();
                            if (lower.endsWith(".p12") || lower.endsWith(".pem")) canDer = true;
                            if (lower.endsWith(".p12")) canPem = true;
                            if (type.contains("Server") || type.contains("Client")) canRevoke = true;
                        }

                        createDerItem.setEnabled(canDer);
                        createPemItem.setEnabled(canPem);
                        revokeItem.setEnabled(canRevoke && selectedRows.length == 1);

                        boolean isSingleSelection = selectedRows.length == 1;
                        viewItem.setEnabled(isSingleSelection);
                        saveItem.setEnabled(isSingleSelection);
                        exportItem.setEnabled(selectedRows.length > 0);

                        fileTableContextMenu.show(e.getComponent(), e.getX(), e.getY());
                    }
                }
            }
            @Override public void mousePressed(MouseEvent e) { showPopup(e); }
            @Override public void mouseReleased(MouseEvent e) { showPopup(e); }
        });

        JScrollPane fileTableScrollPane = new JScrollPane(fileTable);
        bottomTabbedPane.addTab("Generated Files", fileTableScrollPane);

        generatorTab.add(topSectionPanel, BorderLayout.NORTH);
        generatorTab.add(bottomTabbedPane, BorderLayout.CENTER);

        // ---------------------------------------------------------
        // TAB 2: API Provisioner
        // ---------------------------------------------------------
        JPanel apiTab = new JPanel(new BorderLayout(10, 10));
        apiTab.setBorder(new EmptyBorder(10, 10, 10, 10));

        JPanel apiHeader = new JPanel(new BorderLayout());
        JLabel apiInstructions = new JLabel("<html><b>Mock ACME/GitOps API Server</b><br/>" +
                "Start the server to listen on <i>http://localhost:8080/api/sign</i>.<br/>" +
                "POST a raw CSR to this endpoint, and it will be signed by the currently selected CA and returned as a PEM certificate.</html>");
        apiInstructions.setBorder(new EmptyBorder(10, 0, 10, 0));

        btnToggleApi.addActionListener(e -> toggleApiServer());
        apiHeader.add(apiInstructions, BorderLayout.CENTER);
        apiHeader.add(btnToggleApi, BorderLayout.EAST);

        apiLogArea.setEditable(false);
        apiLogArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane apiLogScroll = new JScrollPane(apiLogArea);

        apiTab.add(apiHeader, BorderLayout.NORTH);
        apiTab.add(apiLogScroll, BorderLayout.CENTER);

        // ---------------------------------------------------------
        // Compile Main Frame
        // ---------------------------------------------------------
        mainTabs.addTab("PKI Generator", generatorTab);
        mainTabs.addTab("API Provisioner", apiTab);

        progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setStringPainted(true);
        progressBar.setVisible(false);

        JPanel wrapperPanel = new JPanel(new BorderLayout());
        wrapperPanel.add(mainTabs, BorderLayout.CENTER);
        wrapperPanel.add(progressBar, BorderLayout.SOUTH);

        generateCaButton.addActionListener(this::generateRootCaAction);
        generateIntCaButton.addActionListener(this::generateIntCaAction);
        generateServerButton.addActionListener(this::generateServerAction);
        generateClientButton.addActionListener(this::generateClientAction);
        generatePublicKeyButton.addActionListener(this::generatePublicKeyAction);

        wrapperPanel.setDropTarget(new DropTarget(this, DnDConstants.ACTION_COPY, new DropTargetAdapter() {
            @Override public void drop(DropTargetDropEvent dtde) { handleFileDrop(dtde); }
        }));

        add(wrapperPanel);
    }

    private void validateEnvironment() {
        CompletableFuture.runAsync(() -> {
            CommandResult result = executeCommand(List.of("keytool", "-help"), true);
            if (!result.isSuccess()) {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this,
                            "CRITICAL ERROR: 'keytool' is not recognized in your system PATH.\n" +
                                    "Please ensure the Java JDK is installed and configured correctly.",
                            "Missing Dependency", JOptionPane.ERROR_MESSAGE);
                    generateCaButton.setEnabled(false);
                    generateIntCaButton.setEnabled(false);
                    generateServerButton.setEnabled(false);
                    generateClientButton.setEnabled(false);
                    generatePublicKeyButton.setEnabled(false);
                });
            }
        });
    }

    public static ImageIcon recolorIcon(ImageIcon icon, Color color) {
        BufferedImage bufferedImage = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = bufferedImage.createGraphics();
        g2d.drawImage(icon.getImage(), 0, 0, null);
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_IN, 1.0f));
        g2d.setColor(color);
        g2d.fillRect(0, 0, icon.getIconWidth(), icon.getIconHeight());
        g2d.dispose();
        return new ImageIcon(bufferedImage);
    }

    private void showProgress(String message) {
        SwingUtilities.invokeLater(() -> {
            progressBar.setString(message);
            progressBar.setVisible(true);
            generateCaButton.setEnabled(false);
            generateIntCaButton.setEnabled(false);
            generateServerButton.setEnabled(false);
            generateClientButton.setEnabled(false);
            generatePublicKeyButton.setEnabled(false);
        });
    }

    private void hideProgress() {
        SwingUtilities.invokeLater(() -> {
            progressBar.setVisible(false);
            progressBar.setString("");
            generateCaButton.setEnabled(true);
            generateIntCaButton.setEnabled(true);
            generateServerButton.setEnabled(true);
            generateClientButton.setEnabled(true);
            generatePublicKeyButton.setEnabled(true);
        });
    }

    private void addOuField() {
        JPanel ouRowPanel = new JPanel(new BorderLayout(5, 0));
        ouRowPanel.setBorder(new EmptyBorder(0, 0, 2, 0));
        boolean isFirst = ouFields.isEmpty();
        JTextField ouField = new JTextField(isFirst ? "Platform Engineering" : "", 20);
        ouFields.add(ouField);

        JButton button = new JButton(isFirst ? "+" : "-");
        button.setMargin(new Insets(2, 5, 2, 5));
        button.setFont(button.getFont().deriveFont(Font.BOLD));
        if (isFirst) button.addActionListener(e -> addOuField());
        else button.addActionListener(e -> removeOuField(ouRowPanel, ouField));

        ouRowPanel.add(ouField, BorderLayout.CENTER);
        ouRowPanel.add(button, BorderLayout.EAST);
        ouPanelContainer.add(ouRowPanel);
        ouPanelContainer.revalidate();
        ouPanelContainer.repaint();
    }

    private void removeOuField(JPanel panelToRemove, JTextField fieldToRemove) {
        ouFields.remove(fieldToRemove);
        ouPanelContainer.remove(panelToRemove);
        ouPanelContainer.revalidate();
        ouPanelContainer.repaint();
    }

    private void addSanField() {
        boolean isFirst = sanFields.isEmpty();
        SanEntryPanel sanPanel = new SanEntryPanel(isFirst);
        sanFields.add(sanPanel);
        sanPanelContainer.add(sanPanel);
        sanPanelContainer.revalidate();
        sanPanelContainer.repaint();
    }

    private void removeSanField(SanEntryPanel panelToRemove) {
        sanFields.remove(panelToRemove);
        sanPanelContainer.remove(panelToRemove);
        sanPanelContainer.revalidate();
        sanPanelContainer.repaint();
    }

    private String buildSanString() {
        List<String> validSans = new ArrayList<>();
        for (SanEntryPanel sp : sanFields) {
            String val = sp.valueField.getText().trim();
            if (!val.isEmpty()) {
                validSans.add(sp.typeCombo.getSelectedItem() + ":" + val);
            }
        }
        return validSans.isEmpty() ? null : "san=" + String.join(",", validSans);
    }

    private String sanitizeFilename(String input) {
        if (input == null || input.trim().isEmpty()) return "keystore";
        return input.trim().toLowerCase().replaceAll("[^a-z0-9\\.]", "-").replaceAll("-+", "-");
    }

    private List<String> getSelectedFilenames() {
        int[] rows = fileTable.getSelectedRows();
        List<String> names = new ArrayList<>();
        for (int r : rows) {
            names.add((String) fileTable.getValueAt(r, 1));
        }
        return names;
    }

    // ---------------------------------------------------------
    // GUI Action Handlers
    // ---------------------------------------------------------

    private void generateRootCaAction(ActionEvent e) {
        String dynamicFilename = sanitizeFilename(tfCaIdentity.getText()) + ".p12";
        if (activeCaFilename != null && generatedFiles.containsKey(activeCaFilename)) {
            if (JOptionPane.showConfirmDialog(this, "A Root CA already exists. Overwrite?", "Confirm Overwrite", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.NO_OPTION) {
                return;
            }
            removeGeneratedFile(activeCaFilename);
        }
        showProgress("Generating Root Certificate Authority...");
        generateRootCaAsync().whenComplete((success, ex) -> hideProgress());
    }

    private void generateIntCaAction(ActionEvent e) {
        String dynamicFilename = sanitizeFilename(tfIntCaIdentity.getText()) + ".p12";
        if (activeIntCaFilename != null && generatedFiles.containsKey(activeIntCaFilename)) {
            if (JOptionPane.showConfirmDialog(this, "An Intermediate CA already exists. Overwrite?", "Confirm Overwrite", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.NO_OPTION) {
                return;
            }
            removeGeneratedFile(activeIntCaFilename);
        }
        showProgress("Generating Intermediate CA...");
        ensureRootCaExists().thenComposeAsync(caSuccess ->
                caSuccess ? generateIntermediateCaAsync() : CompletableFuture.completedFuture(false)
        ).whenComplete((success, ex) -> hideProgress());
    }

    private void generateServerAction(ActionEvent e) {
        showProgress("Generating Server Keystore...");
        final String serverCN = tfServerDomain.getText();
        final String sanParams = buildSanString();

        ensureSignerExists().thenComposeAsync(signerSuccess ->
                signerSuccess ? generateLeafCertificate("server", serverCN, sanParams) : CompletableFuture.completedFuture(false)
        ).whenComplete((success, ex) -> hideProgress());
    }

    private void generateClientAction(ActionEvent e) {
        showProgress("Generating Client Keystore...");
        final String sanParams = buildSanString();

        ensureSignerExists().thenComposeAsync(signerSuccess ->
                signerSuccess ? generateLeafCertificate("client", tfClientName.getText(), sanParams) : CompletableFuture.completedFuture(false)
        ).whenComplete((success, ex) -> hideProgress());
    }

    private void generatePublicKeyAction(ActionEvent e) {
        showProgress("Generating Client Public Key...");
        final String publicKeyFilename = tfPublicKeyFilename.getText();

        CompletableFuture.runAsync(() -> {
            if (activeClientFilename == null || !generatedFiles.containsKey(activeClientFilename)) {
                log("ERROR: Active Client Keystore not found in memory. Please generate a client first.");
                return;
            }

            byte[] exported = exportCertificateFromKeystore(activeClientFilename, "client", true);
            if (exported != null) {
                addGeneratedFile(publicKeyFilename, exported, "Public Key (PEM)", calculateSha256(exported));
                log("--- Successfully generated public key. ---");
            }
        }).whenComplete((v, ex) -> hideProgress());
    }

    private void revokeCertificateAction() {
        List<String> filenames = getSelectedFilenames();
        if (filenames.size() != 1) return;
        String targetFilename = filenames.getFirst();

        showProgress("Revoking Certificate and Updating CRL...");
        CompletableFuture.runAsync(() -> {
            try {
                byte[] ksBytes = generatedFiles.get(targetFilename);
                KeyStore ks = KeyStore.getInstance("PKCS12");
                ks.load(new ByteArrayInputStream(ksBytes), new String(tfPassword.getPassword()).toCharArray());
                String targetAlias = targetFilename.contains("server") ? "server" : "client";
                Certificate cert = ks.getCertificate(targetAlias);
                if (!(cert instanceof X509Certificate x509)) {
                    log("ERROR: Could not find valid X509 certificate in " + targetFilename);
                    return;
                }

                String serial = x509.getSerialNumber().toString(10);
                if (!revokedSerials.contains(serial)) {
                    revokedSerials.add(serial);
                }
                log("Revoking serial number: " + serial);

                String signerSelection = (String) signerCombo.getSelectedItem();
                boolean useIntCa = "Intermediate CA".equals(signerSelection);
                String activeSignerFile = useIntCa ? activeIntCaFilename : activeCaFilename;
                String signerAlias = useIntCa ? "intermediate" : "rootca";

                if (activeSignerFile == null || !generatedFiles.containsKey(activeSignerFile)) {
                    log("ERROR: The required Signer CA is not in memory to generate the CRL.");
                    return;
                }

                Path tempCaKs = Files.createTempFile("signer_temp", ".p12");
                Files.write(tempCaKs, generatedFiles.get(activeSignerFile));
                Path tempCrl = Files.createTempFile("revoked", ".crl");
                Files.delete(tempCrl);

                List<String> command = new ArrayList<>(Arrays.asList(
                        "keytool", "-gencrl",
                        "-alias", signerAlias,
                        "-keystore", tempCaKs.toAbsolutePath().toString(),
                        "-storetype", "pkcs12",
                        "-storepass", new String(tfPassword.getPassword()),
                        "-file", tempCrl.toAbsolutePath().toString()
                ));

                for (String s : revokedSerials) {
                    command.add("-id");
                    command.add(s);
                }

                if (executeCommand(command, new String(tfPassword.getPassword())).isSuccess()) {
                    byte[] crlBytes = Files.readAllBytes(tempCrl);
                    String crlFilename = (useIntCa ? "intermediate" : "root") + ".crl";
                    addGeneratedFile(crlFilename, crlBytes, "Revocation List (CRL)", calculateSha256(crlBytes));
                    log("--- Successfully generated/updated CRL. ---");
                }

                Files.deleteIfExists(tempCaKs);
                Files.deleteIfExists(tempCrl);

            } catch (Exception ex) {
                log("ERROR during revocation: " + ex.getMessage());
            }
        }).whenComplete((v, ex) -> hideProgress());
    }

    private void clearAllAction(ActionEvent e) {
        tfOrg.setText("OmniCorp"); tfCity.setText("Baltimore"); tfState.setText("Maryland"); tfCountry.setText("US"); tfPassword.setText("password");
        ouPanelContainer.removeAll(); ouFields.clear(); addOuField();
        sanPanelContainer.removeAll(); sanFields.clear(); addSanField();
        tfValidity.setText("365"); chkNeverExpire.setSelected(false); tfValidity.setEnabled(true);
        tfCaIdentity.setText("OmniCorp Root CA"); tfIntCaIdentity.setText("OmniCorp Issuing Intermediate CA");
        tfServerDomain.setText("api.internal.local"); tfClientName.setText("Jane Doe"); tfPublicKeyFilename.setText("public-key.pem");

        caCheckLabel.setVisible(false); intCaCheckLabel.setVisible(false); serverCheckLabel.setVisible(false); clientCheckLabel.setVisible(false); publicKeyCheckLabel.setVisible(false);
        togglePublicKeyPanel(false);
        generatedFiles.clear(); fileTableModel.setRowCount(0);
        activeCaFilename = null; activeIntCaFilename = null; activeClientFilename = null; revokedSerials.clear();
        logArea.setText(""); log("UI cleared.");
    }

    // --- The Previously Truncated Missing Method ---
    private void togglePublicKeyPanel(boolean show) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> togglePublicKeyPanel(show));
            return;
        }

        int targetHeight = show ? publicKeyPanel.getPreferredSize().height : 0;
        if (publicKeyPanel.getHeight() == targetHeight) {
            if (show) publicKeyPanel.setVisible(true);
            return;
        }

        publicKeyPanel.setVisible(true);
        int duration = 200, steps = 20, stepDelay = duration / steps;
        int heightStep = (targetHeight - publicKeyPanel.getHeight()) / steps;

        javax.swing.Timer timer = new javax.swing.Timer(stepDelay, null);
        timer.addActionListener(e -> {
            int newHeight = publicKeyPanel.getHeight() + heightStep;
            if ((heightStep > 0 && newHeight >= targetHeight) || (heightStep < 0 && newHeight <= targetHeight)) {
                publicKeyPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, targetHeight));
                if (!show) {
                    publicKeyPanel.setVisible(false);
                    publicKeyCheckLabel.setVisible(false);
                }
                timer.stop();
            } else {
                publicKeyPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, newHeight));
            }
            publicKeyPanel.getParent().revalidate();
            publicKeyPanel.getParent().repaint();
        });
        timer.start();
    }

    // ---------------------------------------------------------
    // PKI Generation Logic
    // ---------------------------------------------------------

    private CompletableFuture<Boolean> ensureRootCaExists() {
        if (activeCaFilename != null && generatedFiles.containsKey(activeCaFilename)) {
            return CompletableFuture.completedFuture(true);
        }
        return generateRootCaAsync();
    }

    private CompletableFuture<Boolean> ensureSignerExists() {
        String selection = (String) signerCombo.getSelectedItem();
        if ("Intermediate CA".equals(selection)) {
            if (activeIntCaFilename != null && generatedFiles.containsKey(activeIntCaFilename)) {
                return CompletableFuture.completedFuture(true);
            } else {
                return ensureRootCaExists().thenCompose(success -> success ? generateIntermediateCaAsync() : CompletableFuture.completedFuture(false));
            }
        } else {
            return ensureRootCaExists();
        }
    }

    private CompletableFuture<Boolean> generateRootCaAsync() {
        log("--- Generating Root Certificate Authority ---");
        final String dname = buildDname(tfCaIdentity.getText());
        final String password = new String(tfPassword.getPassword());
        final String validity = chkNeverExpire.isSelected() ? "99999" : "3650";
        final String dynamicFilename = sanitizeFilename(tfCaIdentity.getText()) + ".p12";

        CompletableFuture<Boolean> future = new CompletableFuture<>();
        CompletableFuture.runAsync(() -> {
            Path tempKeystore = null;
            try {
                tempKeystore = Files.createTempFile("rootca", ".p12");
                Files.delete(tempKeystore);

                List<String> command = Arrays.asList(
                        "keytool", "-genkeypair", "-alias", "rootca", "-keyalg", "RSA", "-keysize", "4096",
                        "-validity", validity, "-dname", dname,
                        "-keystore", tempKeystore.toAbsolutePath().toString(),
                        "-storetype", "pkcs12", "-storepass", password, "-keypass", password, "-ext", "bc=ca:true");

                if (executeCommand(command, password).isSuccess()) {
                    byte[] fileBytes = Files.readAllBytes(tempKeystore);
                    activeCaFilename = dynamicFilename;
                    addGeneratedFile(dynamicFilename, fileBytes, "Root CA", calculateSha256(fileBytes));
                    log("--- Successfully generated Root CA. ---");
                    future.complete(true);
                } else {
                    future.complete(false);
                }
            } catch (Exception ex) {
                log("ERROR during Root CA generation: " + ex.getMessage());
                future.completeExceptionally(ex);
            } finally {
                try { if (tempKeystore != null) Files.deleteIfExists(tempKeystore); } catch (Exception ignored) {}
            }
        });
        return future;
    }

    private CompletableFuture<Boolean> generateIntermediateCaAsync() {
        log("--- Generating Intermediate CA ---");
        final String dname = buildDname(tfIntCaIdentity.getText());
        final String password = new String(tfPassword.getPassword());
        final String validity = chkNeverExpire.isSelected() ? "99999" : "1825"; // 5 years
        final String dynamicFilename = sanitizeFilename(tfIntCaIdentity.getText()) + ".p12";

        CompletableFuture<Boolean> future = new CompletableFuture<>();
        CompletableFuture.runAsync(() -> {
            Path tempRootKs = null, tempIntKs = null, tempCsr = null, tempCert = null, tempRootCert = null;
            try {
                tempRootKs = Files.createTempFile("root_temp", ".p12");
                Files.write(tempRootKs, generatedFiles.get(activeCaFilename));

                tempIntKs = Files.createTempFile("intca", ".p12"); Files.delete(tempIntKs);
                tempCsr = Files.createTempFile("intca", ".csr"); Files.delete(tempCsr);
                tempCert = Files.createTempFile("intca", ".cer"); Files.delete(tempCert);
                tempRootCert = Files.createTempFile("rootca", ".cer"); Files.delete(tempRootCert);

                String intKsPath = tempIntKs.toAbsolutePath().toString();
                String rootKsPath = tempRootKs.toAbsolutePath().toString();

                if (!executeCommand(Arrays.asList("keytool", "-genkeypair", "-alias", "intermediate", "-keyalg", "RSA", "-keysize", "4096", "-dname", dname, "-keystore", intKsPath, "-storetype", "pkcs12", "-storepass", password, "-keypass", password), password).isSuccess()) { future.complete(false); return; }
                if (!executeCommand(Arrays.asList("keytool", "-certreq", "-alias", "intermediate", "-file", tempCsr.toAbsolutePath().toString(), "-keystore", intKsPath, "-storetype", "pkcs12", "-storepass", password), password).isSuccess()) { future.complete(false); return; }
                if (!executeCommand(Arrays.asList("keytool", "-gencert", "-alias", "rootca", "-infile", tempCsr.toAbsolutePath().toString(), "-outfile", tempCert.toAbsolutePath().toString(), "-keystore", rootKsPath, "-storetype", "pkcs12", "-storepass", password, "-validity", validity, "-ext", "bc=ca:true,pathlen:0"), password).isSuccess()) { future.complete(false); return; }
                if (!executeCommand(Arrays.asList("keytool", "-exportcert", "-alias", "rootca", "-file", tempRootCert.toAbsolutePath().toString(), "-keystore", rootKsPath, "-storetype", "pkcs12", "-storepass", password, "-rfc"), password).isSuccess()) { future.complete(false); return; }
                if (!executeCommand(Arrays.asList("keytool", "-importcert", "-alias", "rootca", "-file", tempRootCert.toAbsolutePath().toString(), "-keystore", intKsPath, "-storetype", "pkcs12", "-storepass", password, "-noprompt"), password).isSuccess()) { future.complete(false); return; }
                if (!executeCommand(Arrays.asList("keytool", "-importcert", "-alias", "intermediate", "-file", tempCert.toAbsolutePath().toString(), "-keystore", intKsPath, "-storetype", "pkcs12", "-storepass", password), password).isSuccess()) { future.complete(false); return; }

                byte[] finalKeystoreBytes = Files.readAllBytes(tempIntKs);
                activeIntCaFilename = dynamicFilename;
                addGeneratedFile(dynamicFilename, finalKeystoreBytes, "Intermediate CA", calculateSha256(finalKeystoreBytes));
                log("--- Successfully created Intermediate CA. ---");
                future.complete(true);
            } catch (Exception ex) {
                log("ERROR during Int CA generation: " + ex.getMessage());
                future.complete(false);
            } finally {
                try {
                    if (tempRootKs != null) Files.deleteIfExists(tempRootKs);
                    if (tempIntKs != null) Files.deleteIfExists(tempIntKs);
                    if (tempCsr != null) Files.deleteIfExists(tempCsr);
                    if (tempCert != null) Files.deleteIfExists(tempCert);
                    if (tempRootCert != null) Files.deleteIfExists(tempRootCert);
                } catch (Exception ignored) {}
            }
        });
        return future;
    }

    private CompletableFuture<Boolean> generateLeafCertificate(String alias, String cn, String san) {
        final String password = new String(tfPassword.getPassword());
        final String validity = chkNeverExpire.isSelected() ? "99999" : tfValidity.getText();
        final String dname = buildDname(cn);
        final String dynamicFilename = sanitizeFilename(cn) + ".p12";

        final boolean useIntCa = "Intermediate CA".equals(signerCombo.getSelectedItem());
        final String activeSignerFile = useIntCa ? activeIntCaFilename : activeCaFilename;
        final String signerAlias = useIntCa ? "intermediate" : "rootca";

        final CompletableFuture<Boolean> future = new CompletableFuture<>();
        log(String.format("--- Generating %s Certificate (Signed by %s) ---", alias, signerAlias));

        CompletableFuture.runAsync(() -> {
            Path tempSignerKs = null, tempKeystore = null, tempCsr = null, tempCert = null, tempSignerCert = null;
            try {
                byte[] signerBytes = generatedFiles.get(activeSignerFile);
                tempSignerKs = Files.createTempFile("signer_temp", ".p12");
                Files.write(tempSignerKs, signerBytes);

                tempKeystore = Files.createTempFile(alias, ".p12"); Files.delete(tempKeystore);
                tempCsr = Files.createTempFile(alias, ".csr"); Files.delete(tempCsr);
                tempCert = Files.createTempFile(alias, ".cer"); Files.delete(tempCert);
                tempSignerCert = Files.createTempFile("signer", ".cer"); Files.delete(tempSignerCert);

                String ksPath = tempKeystore.toAbsolutePath().toString();
                String signerKsPath = tempSignerKs.toAbsolutePath().toString();

                if (!executeCommand(Arrays.asList("keytool", "-genkeypair", "-alias", alias, "-keyalg", "RSA", "-keysize", "2048", "-dname", dname, "-keystore", ksPath, "-storetype", "pkcs12", "-storepass", password, "-keypass", password), password).isSuccess()) { future.complete(false); return; }
                if (!executeCommand(Arrays.asList("keytool", "-certreq", "-alias", alias, "-file", tempCsr.toAbsolutePath().toString(), "-keystore", ksPath, "-storetype", "pkcs12", "-storepass", password), password).isSuccess()) { future.complete(false); return; }

                List<String> signCsrCommand = new ArrayList<>(Arrays.asList("keytool", "-gencert", "-alias", signerAlias, "-infile", tempCsr.toAbsolutePath().toString(), "-outfile", tempCert.toAbsolutePath().toString(), "-keystore", signerKsPath, "-storetype", "pkcs12", "-storepass", password, "-validity", validity));
                if (san != null && !san.trim().isEmpty()) {
                    signCsrCommand.add("-ext"); signCsrCommand.add(san);
                }

                if (!executeCommand(signCsrCommand, password).isSuccess()) { future.complete(false); return; }
                if (!executeCommand(Arrays.asList("keytool", "-exportcert", "-alias", signerAlias, "-file", tempSignerCert.toAbsolutePath().toString(), "-keystore", signerKsPath, "-storetype", "pkcs12", "-storepass", password, "-rfc"), password).isSuccess()) { future.complete(false); return; }
                if (!executeCommand(Arrays.asList("keytool", "-importcert", "-alias", signerAlias, "-file", tempSignerCert.toAbsolutePath().toString(), "-keystore", ksPath, "-storetype", "pkcs12", "-storepass", password, "-noprompt"), password).isSuccess()) { future.complete(false); return; }
                if (!executeCommand(Arrays.asList("keytool", "-importcert", "-alias", alias, "-file", tempCert.toAbsolutePath().toString(), "-keystore", ksPath, "-storetype", "pkcs12", "-storepass", password), password).isSuccess()) { future.complete(false); return; }

                byte[] finalKeystoreBytes = Files.readAllBytes(tempKeystore);
                String type = alias.substring(0, 1).toUpperCase() + alias.substring(1) + " Keystore";

                if (alias.equals("client")) activeClientFilename = dynamicFilename;

                addGeneratedFile(dynamicFilename, finalKeystoreBytes, type, calculateSha256(finalKeystoreBytes));
                log(String.format("--- Successfully created %s keystore. ---", alias));
                future.complete(true);
            } catch (Exception ex) {
                log("ERROR during certificate generation: " + ex.getMessage());
                future.complete(false);
            } finally {
                try {
                    if (tempSignerKs != null) Files.deleteIfExists(tempSignerKs);
                    if (tempKeystore != null) Files.deleteIfExists(tempKeystore);
                    if (tempCsr != null) Files.deleteIfExists(tempCsr);
                    if (tempCert != null) Files.deleteIfExists(tempCert);
                    if (tempSignerCert != null) Files.deleteIfExists(tempSignerCert);
                } catch (Exception ignored) {}
            }
        });
        return future;
    }

    // ---------------------------------------------------------
    // MOCK ACME / GITOPS API SERVER
    // ---------------------------------------------------------

    private void toggleApiServer() {
        if (apiServer == null) {
            try {
                apiServer = HttpServer.create(new InetSocketAddress(8080), 0);
                apiServer.createContext("/api/sign", new ApiSignHandler());
                apiServer.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
                apiServer.start();
                btnToggleApi.setText("Stop API Server");
                apiLog("SUCCESS: API Server started on http://localhost:8080");
                apiLog("Waiting for POST requests containing CSRs at /api/sign...");
            } catch (IOException e) {
                apiLog("ERROR: Could not start API server: " + e.getMessage());
            }
        } else {
            apiServer.stop(0);
            apiServer = null;
            btnToggleApi.setText("Start API Server (Port 8080)");
            apiLog("API Server stopped.");
        }
    }

    private void apiLog(String msg) {
        SwingUtilities.invokeLater(() -> {
            apiLogArea.append(SDF.format(new Date()) + " | " + msg + "\n");
            apiLogArea.setCaretPosition(apiLogArea.getDocument().getLength());
        });
    }

    private class ApiSignHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            apiLog("Received CSR via API...");
            String signerSelection = (String) signerCombo.getSelectedItem();
            boolean useIntCa = "Intermediate CA".equals(signerSelection);
            String activeSignerFile = useIntCa ? activeIntCaFilename : activeCaFilename;
            String signerAlias = useIntCa ? "intermediate" : "rootca";

            if (activeSignerFile == null || !generatedFiles.containsKey(activeSignerFile)) {
                String err = "ERROR: Required Signer CA is not generated yet.";
                apiLog(err);
                exchange.sendResponseHeaders(500, err.length());
                OutputStream os = exchange.getResponseBody();
                os.write(err.getBytes());
                os.close();
                return;
            }

            InputStream is = exchange.getRequestBody();
            String csrBody = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))
                    .lines().collect(Collectors.joining("\n"));

            if (!csrBody.contains("BEGIN CERTIFICATE REQUEST")) {
                String err = "Bad Request: Body does not contain a valid PEM CSR.";
                apiLog(err);
                exchange.sendResponseHeaders(400, err.length());
                OutputStream os = exchange.getResponseBody();
                os.write(err.getBytes());
                os.close();
                return;
            }

            Path tempCsr = null;
            Path tempCert = null;
            Path tempSignerKs = null;

            try {
                tempCsr = Files.createTempFile("api_req", ".csr");
                Files.write(tempCsr, csrBody.getBytes(StandardCharsets.UTF_8));

                tempCert = Files.createTempFile("api_res", ".cer");
                Files.delete(tempCert);

                tempSignerKs = Files.createTempFile("signer", ".p12");
                Files.write(tempSignerKs, generatedFiles.get(activeSignerFile));

                String password = new String(tfPassword.getPassword());

                List<String> command = new ArrayList<>(Arrays.asList(
                        "keytool", "-gencert",
                        "-alias", signerAlias,
                        "-infile", tempCsr.toAbsolutePath().toString(),
                        "-outfile", tempCert.toAbsolutePath().toString(),
                        "-keystore", tempSignerKs.toAbsolutePath().toString(),
                        "-storetype", "pkcs12",
                        "-storepass", password,
                        "-validity", "90",
                        "-rfc"
                ));

                CommandResult result = executeCommand(command, true, password);
                if (result.isSuccess()) {
                    byte[] certBytes = Files.readAllBytes(tempCert);
                    exchange.sendResponseHeaders(200, certBytes.length);
                    OutputStream os = exchange.getResponseBody();
                    os.write(certBytes);
                    os.close();
                    apiLog("Successfully signed and returned certificate payload.");
                } else {
                    String err = "Internal CA Error processing CSR.";
                    apiLog("ERROR: " + result.output());
                    exchange.sendResponseHeaders(500, err.length());
                    OutputStream os = exchange.getResponseBody();
                    os.write(err.getBytes());
                    os.close();
                }
            } catch (Exception e) {
                apiLog("Exception handling API request: " + e.getMessage());
            } finally {
                try {
                    if (tempCsr != null) Files.deleteIfExists(tempCsr);
                    if (tempCert != null) Files.deleteIfExists(tempCert);
                    if (tempSignerKs != null) Files.deleteIfExists(tempSignerKs);
                } catch (Exception ignored) {}
            }
        }
    }

    // ---------------------------------------------------------
    // Utility and Action Methods
    // ---------------------------------------------------------

    private String buildDname(String cn) {
        final List<String> ouTexts = ouFields.stream().map(JTextField::getText).toList();
        String ous = ouTexts.stream().filter(s -> s != null && !s.trim().isEmpty()).map(s -> "OU=" + s).collect(Collectors.joining(", "));
        return String.format("CN=%s, %s, O=%s, L=%s, ST=%s, C=%s", cn, ous, tfOrg.getText(), tfCity.getText(), tfState.getText(), tfCountry.getText());
    }

    private void removeGeneratedFile(String filename) {
        if (generatedFiles.remove(filename) == null) return;
        SwingUtilities.invokeLater(() -> {
            if (filename.equals(activeCaFilename)) { caCheckLabel.setVisible(false); activeCaFilename = null; }
            if (filename.equals(activeIntCaFilename)) { intCaCheckLabel.setVisible(false); activeIntCaFilename = null; }
            if (filename.equals(activeClientFilename)) {
                clientCheckLabel.setVisible(false); togglePublicKeyPanel(false);
                removeGeneratedFile(tfPublicKeyFilename.getText());
                activeClientFilename = null;
            }
            if (filename.equals(tfPublicKeyFilename.getText())) publicKeyCheckLabel.setVisible(false);

            for (int i = fileTableModel.getRowCount() - 1; i >= 0; i--) {
                if (filename.equals(fileTableModel.getValueAt(i, 1))) {
                    fileTableModel.removeRow(i);
                    break;
                }
            }
        });
    }

    private void addGeneratedFile(String filename, byte[] content, String type, String fingerprint) {
        generatedFiles.put(filename, content);
        SwingUtilities.invokeLater(() -> {
            if (filename.equals(activeCaFilename)) caCheckLabel.setVisible(true);
            if (filename.equals(activeIntCaFilename)) intCaCheckLabel.setVisible(true);
            if (type.contains("Server")) serverCheckLabel.setVisible(true);
            if (filename.equals(activeClientFilename)) {
                clientCheckLabel.setVisible(true); togglePublicKeyPanel(true);
            }
            if (filename.equals(tfPublicKeyFilename.getText())) publicKeyCheckLabel.setVisible(true);

            for (int i = 0; i < fileTableModel.getRowCount(); i++) {
                if (filename.equals(fileTableModel.getValueAt(i, 1))) {
                    fileTableModel.removeRow(i);
                    break;
                }
            }

            fileTableModel.addRow(new Object[] {UIManager.getIcon("FileView.fileIcon"), filename, content.length, type, SDF.format(new Date()), fingerprint});
        });
    }

    private CommandResult executeCommand(List<String> command, String... passwordsToMask) {
        return executeCommand(command, false, passwordsToMask);
    }

    private CommandResult executeCommand(List<String> command, boolean silent, String... passwordsToMask) {
        try {
            String commandString = String.join(" ", command);
            for (String pwd : passwordsToMask) {
                if (pwd != null && !pwd.isEmpty()) commandString = commandString.replace(pwd, "********");
            }

            if (!silent) log("Executing: " + commandString);

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) output.append(line).append("\n");
            }

            int exitCode = process.waitFor();
            String finalOutput = output.toString().trim();

            if (!silent) {
                if (!finalOutput.isEmpty()) log(finalOutput);
                if (exitCode != 0) log("ERROR: Command failed with exit code " + exitCode);
                else log("Command executed successfully.");
            }

            return new CommandResult(exitCode, finalOutput);
        } catch (Exception e) {
            if (!silent) log("ERROR: Failed to execute command. Is 'keytool' in your system's PATH?");
            return new CommandResult(-1, e.getMessage());
        }
    }

    // ---------------------------------------------------------
    // Restored Drag and Drop & Inspect Logic
    // ---------------------------------------------------------

    private void handleFileDrop(DropTargetDropEvent dtde) {
        try {
            dtde.acceptDrop(DnDConstants.ACTION_COPY);
            Transferable transferable = dtde.getTransferable();
            if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                @SuppressWarnings("unchecked")
                List<File> files = (List<File>) transferable.getTransferData(DataFlavor.javaFileListFlavor);
                if (!files.isEmpty()) {
                    File droppedFile = files.getFirst();
                    log("File dropped: " + droppedFile.getAbsolutePath());
                    String lowerCaseName = droppedFile.getName().toLowerCase();

                    if (lowerCaseName.endsWith(".p12") || lowerCaseName.endsWith(".jks") || lowerCaseName.endsWith(".keystore")) handleDroppedKeystore(droppedFile);
                    else if (lowerCaseName.endsWith(".csr") || lowerCaseName.endsWith(".req")) handleDroppedCsr(droppedFile);
                    else if (lowerCaseName.endsWith(".der") || lowerCaseName.endsWith(".cer") || lowerCaseName.endsWith(".crt")) handleDroppedDer(droppedFile);
                    else log("Unsupported file type: " + droppedFile.getName());
                }
            }
        } catch (Exception e) { log("ERROR: Could not handle dropped file."); }
        finally { dtde.dropComplete(true); }
    }

    private void handleDroppedKeystore(File file) {
        JPasswordField passwordField = new JPasswordField(20);
        Object[] message = {"Enter password for " + file.getName() + ":", passwordField};
        if (JOptionPane.showConfirmDialog(this, message, "Keystore Password", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
            char[] passwordChars = passwordField.getPassword();
            if (passwordChars != null && passwordChars.length > 0) {
                String password = new String(passwordChars);
                showProgress("Inspecting keystore: " + file.getName());

                CompletableFuture.runAsync(() -> {
                    try {
                        List<String> command = Arrays.asList("keytool", "-list", "-v", "-keystore", file.getAbsolutePath(), "-storepass", password, "-storetype", "pkcs12");
                        CommandResult result = executeCommand(command, password);
                        String output = result.output();

                        if (!result.isSuccess() || output.contains("Keystore was tampered with, or password was incorrect")) {
                            log("PKCS12 failed, trying with JKS storetype...");
                            command = Arrays.asList("keytool", "-list", "-v", "-keystore", file.getAbsolutePath(), "-storepass", password, "-storetype", "jks");
                            result = executeCommand(command, password);
                            output = result.output();
                        }

                        if (result.isSuccess() && !output.contains("Keystore was tampered with, or password was incorrect")) {
                            log("--- Keystore Details ---"); log(output); log("------------------------");
                            String ownerDn = parseDnFromKeytoolOutput(output, "Owner:");
                            if (ownerDn != null) parseAndPopulateDname(ownerDn);

                            byte[] fileBytes = Files.readAllBytes(file.toPath());
                            String fingerprint = calculateSha256(fileBytes);
                            String type = "Client Keystore";
                            String internalFilename = file.getName();

                            if (output.contains("BasicConstraints:[CA:true")) {
                                if (output.contains("pathlen:0")) {
                                    type = "Intermediate CA";
                                    activeIntCaFilename = internalFilename;
                                    generatedFiles.put(internalFilename, fileBytes);
                                    SwingUtilities.invokeLater(() -> { intCaCheckLabel.setVisible(true); tfIntCaIdentity.setText(getCnFromDn(ownerDn)); });
                                } else {
                                    type = "Root CA";
                                    activeCaFilename = internalFilename;
                                    generatedFiles.put(internalFilename, fileBytes);
                                    SwingUtilities.invokeLater(() -> { caCheckLabel.setVisible(true); tfCaIdentity.setText(getCnFromDn(ownerDn)); });
                                }
                            } else if (output.contains("SubjectAlternativeName")) {
                                type = "Server Keystore";
                                generatedFiles.put(internalFilename, fileBytes);
                                SwingUtilities.invokeLater(() -> { serverCheckLabel.setVisible(true); tfServerDomain.setText(getCnFromDn(ownerDn)); });
                            } else {
                                activeClientFilename = internalFilename;
                                generatedFiles.put(internalFilename, fileBytes);
                                SwingUtilities.invokeLater(() -> { clientCheckLabel.setVisible(true); tfClientName.setText(getCnFromDn(ownerDn)); togglePublicKeyPanel(true); });
                            }
                            addGeneratedFile(internalFilename, fileBytes, type, fingerprint);
                        } else {
                            log("ERROR: Failed to read keystore.");
                        }
                    } catch (Exception e) { log("ERROR processing keystore."); }
                }).whenComplete((v, ex) -> hideProgress());
            }
        }
    }

    private void handleDroppedCsr(File file) {
        showProgress("Inspecting CSR: " + file.getName());
        CompletableFuture.runAsync(() -> {
            try {
                CommandResult result = executeCommand(Arrays.asList("keytool", "-printcertreq", "-v", "-file", file.getAbsolutePath()));
                String output = result.output();
                if (result.isSuccess() && !output.toLowerCase().contains("error")) {
                    log("--- CSR Details for " + file.getName() + " ---"); log(output); log("---------------------------------------");
                    String subjectDn = parseDnFromKeytoolOutput(output, "Subject:");
                    if (subjectDn != null) parseAndPopulateDname(subjectDn);
                } else { log("ERROR: Could not read CSR file."); }
            } catch (Exception e) { log("ERROR processing CSR."); }
        }).whenComplete((v, ex) -> hideProgress());
    }

    private void handleDroppedDer(File file) {
        showProgress("Inspecting certificate: " + file.getName());
        CompletableFuture.runAsync(() -> {
            try {
                CommandResult result = executeCommand(Arrays.asList("keytool", "-printcert", "-v", "-file", file.getAbsolutePath()));
                String output = result.output();
                if (result.isSuccess() && !output.toLowerCase().contains("error")) {
                    log("--- Certificate Details for " + file.getName() + " ---"); log(output); log("---------------------------------------");
                    String ownerDn = parseDnFromKeytoolOutput(output, "Owner:");
                    if (ownerDn != null) parseAndPopulateDname(ownerDn);
                    byte[] fileBytes = Files.readAllBytes(file.toPath());
                    String type = output.contains("BasicConstraints:[CA:true") ? "CA Certificate (DER)" : output.contains("SubjectAlternativeName") ? "Server Certificate (DER)" : "Certificate (DER)";
                    addGeneratedFile(file.getName(), fileBytes, type, calculateSha256(fileBytes));
                } else { log("ERROR: Could not read certificate file."); }
            } catch (Exception e) { log("ERROR processing certificate file."); }
        }).whenComplete((v, ex) -> hideProgress());
    }

    private String parseDnFromKeytoolOutput(String output, String marker) {
        for (String line : output.split("\\r?\\n")) {
            if (line.trim().startsWith(marker)) return line.substring(line.indexOf(":") + 1).trim();
        }
        return null;
    }

    private String getCnFromDn(String dn) {
        if (dn == null) return "";
        for (String part : dn.split(",")) {
            String[] kv = part.trim().split("=");
            if (kv.length == 2 && kv[0].equalsIgnoreCase("CN")) return kv[1];
        }
        return "";
    }

    private void parseAndPopulateDname(String dname) {
        if (dname == null) return;
        SwingUtilities.invokeLater(() -> {
            while (ouFields.size() > 1) removeOuField((JPanel) ouPanelContainer.getComponent(ouFields.size() - 1), ouFields.getLast());
            if (!ouFields.isEmpty()) ouFields.getFirst().setText("");

            List<String> ous = new ArrayList<>();
            for (String part : dname.split(",")) {
                String[] kv = part.trim().split("=", 2);
                if (kv.length == 2) {
                    switch (kv[0].toUpperCase()) {
                        case "OU": ous.add(kv[1]); break;
                        case "O": tfOrg.setText(kv[1]); break;
                        case "L": tfCity.setText(kv[1]); break;
                        case "ST": tfState.setText(kv[1]); break;
                        case "C": tfCountry.setText(kv[1]); break;
                    }
                }
            }

            if (!ous.isEmpty()) {
                ouFields.getFirst().setText(ous.getFirst());
                for (int i = 1; i < ous.size(); i++) {
                    addOuField();
                    ouFields.get(i).setText(ous.get(i));
                }
            }
        });
    }

    // ---------------------------------------------------------
    // View & Export Formatting
    // ---------------------------------------------------------

    private void viewFileAction() {
        List<String> filenames = getSelectedFilenames();
        if (filenames.size() != 1) return;

        String filename = filenames.getFirst();
        byte[] content = generatedFiles.get(filename);
        if (content == null) {
            JOptionPane.showMessageDialog(this, "Could not find file content in memory for: " + filename, "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String displayContent;
        if (filename.toLowerCase().endsWith(".p12")) {
            displayContent = inspectKeystoreInMemory(filename, content);
        } else {
            displayContent = new String(content);
        }

        JTextArea textArea = new JTextArea(displayContent);
        textArea.setEditable(false);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        textArea.setCaretPosition(0);

        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setPreferredSize(new Dimension(700, 500));
        JOptionPane.showMessageDialog(this, scrollPane, "Viewing File: " + filename, JOptionPane.PLAIN_MESSAGE);
    }

    private String inspectKeystoreInMemory(String filename, byte[] content) {
        String password = new String(tfPassword.getPassword());
        Path tempKeystore = null;
        try {
            tempKeystore = Files.createTempFile("inspect", ".p12");
            Files.write(tempKeystore, content);
            List<String> command = Arrays.asList("keytool", "-list", "-v", "-keystore", tempKeystore.toAbsolutePath().toString(), "-storepass", password, "-storetype", "pkcs12");
            CommandResult result = executeCommand(command, true, password);
            if (result.isSuccess()) return "--- Keystore Details for " + filename + " ---\n\n" + result.output();
            else return "Failed to inspect keystore.\n\n" + result.output();
        } catch (Exception e) {
            return "Error inspecting keystore: " + e.getMessage();
        } finally {
            if (tempKeystore != null) { try { Files.deleteIfExists(tempKeystore); } catch (Exception ignored) {} }
        }
    }

    private void saveSingleFileAction() {
        List<String> filenames = getSelectedFilenames();
        if (filenames.size() == 1) saveSingleFilePrompt(filenames.getFirst());
    }

    private void saveSingleFilePrompt(String filename) {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Save " + filename);
        fileChooser.setSelectedFile(new File(filename));
        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File fileToSave = fileChooser.getSelectedFile();
            byte[] content = generatedFiles.get(filename);
            if (content != null) {
                try (FileOutputStream fos = new FileOutputStream(fileToSave)) {
                    fos.write(content);
                    log("Successfully saved file: " + fileToSave.getAbsolutePath());
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(this, "Error saving file: " + e.getMessage(), "File Save Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }
    }

    private void exportFilesAction() {
        List<String> filenames = getSelectedFilenames();
        if (filenames.isEmpty()) return;

        JFileChooser directoryChooser = new JFileChooser();
        directoryChooser.setDialogTitle("Select Export Directory for " + filenames.size() + " files");
        directoryChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

        if (directoryChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File dir = directoryChooser.getSelectedFile();
            for (String fname : filenames) {
                byte[] content = generatedFiles.get(fname);
                if (content != null) {
                    try (FileOutputStream fos = new FileOutputStream(new File(dir, fname))) {
                        fos.write(content);
                        log("Successfully exported: " + fname);
                    } catch (Exception ex) {
                        log("ERROR: Could not export " + fname + " - " + ex.getMessage());
                    }
                }
            }
            JOptionPane.showMessageDialog(this, "Exported " + filenames.size() + " files to:\n" + dir.getAbsolutePath(), "Export Complete", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void createExtractedFormatAction(boolean asPem) {
        int[] selectedRows = fileTable.getSelectedRows();
        for (int r : selectedRows) {
            String filename = (String) fileTable.getValueAt(r, 1);
            String type = (String) fileTable.getValueAt(r, 3);
            String lower = filename.toLowerCase();

            if (asPem && !lower.endsWith(".p12")) continue;
            if (!asPem && !(lower.endsWith(".p12") || lower.endsWith(".pem"))) continue;

            String extension = asPem ? ".pem" : ".der";
            String newFilename = filename.substring(0, filename.lastIndexOf('.')) + extension;
            byte[] extractedBytes = null;

            try {
                if (!asPem && lower.endsWith(".pem")) {
                    log("--- Converting " + filename + " from PEM to DER ---");
                    byte[] pemBytes = generatedFiles.get(filename);
                    CertificateFactory cf = CertificateFactory.getInstance("X.509");
                    X509Certificate cert = (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(pemBytes));
                    extractedBytes = cert.getEncoded();
                } else if (lower.endsWith(".p12")) {
                    if (asPem) {
                        log(String.format("--- Creating Full PEM Bundle from %s ---", filename));
                        extractedBytes = createFullPemBundle(generatedFiles.get(filename), new String(tfPassword.getPassword()));
                    } else {
                        log(String.format("--- Creating DER format from %s ---", filename));
                        String alias = null;
                        if (type.contains("Root Authority") || type.contains("Root CA")) alias = "rootca";
                        else if (type.contains("Intermediate")) alias = "intermediate";
                        else if (type.contains("Server")) alias = "server";
                        else if (type.contains("Client")) alias = "client";

                        if (alias != null) extractedBytes = exportCertificateFromKeystore(filename, alias, false);
                    }
                }

                if (extractedBytes != null) {
                    String newType = asPem ? "PEM Bundle (Key + Certs)" : "Certificate (DER)";
                    addGeneratedFile(newFilename, extractedBytes, newType, calculateSha256(extractedBytes));
                    log("Successfully generated " + newFilename + " in memory.");
                }
            } catch (Exception e) {
                log("ERROR during memory creation for " + filename + ": " + e.getMessage());
            }
        }
    }

    private byte[] createFullPemBundle(byte[] p12Bytes, String password) throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(new ByteArrayInputStream(p12Bytes), password.toCharArray());
        StringBuilder pemBuilder = new StringBuilder();
        Base64.Encoder encoder = Base64.getMimeEncoder(64, new byte[]{'\n'});

        for (Enumeration<String> e = ks.aliases(); e.hasMoreElements(); ) {
            String alias = e.nextElement();
            pemBuilder.append("Bag Attributes\n    friendlyName: ").append(alias).append("\n");

            if (ks.isKeyEntry(alias)) {
                Key key = ks.getKey(alias, password.toCharArray());
                if (key != null) {
                    pemBuilder.append("Key Attributes: <No Attributes>\n");
                    pemBuilder.append("-----BEGIN PRIVATE KEY-----\n");
                    pemBuilder.append(encoder.encodeToString(key.getEncoded()));
                    pemBuilder.append("\n-----END PRIVATE KEY-----\n\n");
                }

                Certificate[] chain = ks.getCertificateChain(alias);
                if (chain != null) {
                    for (Certificate cert : chain) {
                        if (cert instanceof X509Certificate x509) {
                            pemBuilder.append("Bag Attributes\n");
                            pemBuilder.append("    subject: ").append(x509.getSubjectX500Principal().getName()).append("\n");
                            pemBuilder.append("    issuer: ").append(x509.getIssuerX500Principal().getName()).append("\n");
                        }
                        pemBuilder.append("-----BEGIN CERTIFICATE-----\n");
                        pemBuilder.append(encoder.encodeToString(cert.getEncoded()));
                        pemBuilder.append("\n-----END CERTIFICATE-----\n\n");
                    }
                }
            } else if (ks.isCertificateEntry(alias)) {
                Certificate cert = ks.getCertificate(alias);
                pemBuilder.append("-----BEGIN CERTIFICATE-----\n");
                pemBuilder.append(encoder.encodeToString(cert.getEncoded()));
                pemBuilder.append("\n-----END CERTIFICATE-----\n\n");
            }
        }
        return pemBuilder.toString().getBytes(StandardCharsets.UTF_8);
    }

    private byte[] exportCertificateFromKeystore(String keystoreFilename, String alias, boolean asPem) {
        final String password = new String(tfPassword.getPassword());
        byte[] keystoreBytes = generatedFiles.get(keystoreFilename);
        Path tempKeystore = null, tempOutFile = null;

        try {
            tempKeystore = Files.createTempFile("keystore_temp", ".p12");
            Files.write(tempKeystore, keystoreBytes);
            tempOutFile = Files.createTempFile("cert_export", asPem ? ".pem" : ".der");
            Files.delete(tempOutFile);

            List<String> command = new ArrayList<>(Arrays.asList(
                    "keytool", "-exportcert", "-alias", alias,
                    "-keystore", tempKeystore.toAbsolutePath().toString(),
                    "-storetype", "pkcs12", "-storepass", password,
                    "-file", tempOutFile.toAbsolutePath().toString()
            ));
            if (asPem) command.add("-rfc");

            if (executeCommand(command, true, password).isSuccess()) return Files.readAllBytes(tempOutFile);
            return null;
        } catch (Exception ex) { return null; }
        finally {
            try { if (tempKeystore != null) Files.deleteIfExists(tempKeystore); if (tempOutFile != null) Files.deleteIfExists(tempOutFile); } catch (Exception ex) {}
        }
    }

    private void addConfigRow(JPanel panel, GridBagConstraints gbc, int y, String labelText, JComponent component) {
        gbc.gridy = y; gbc.gridx = 0; gbc.anchor = GridBagConstraints.EAST; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        panel.add(new JLabel(labelText), gbc); gbc.gridx = 1; gbc.anchor = GridBagConstraints.WEST; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1;
        panel.add(component, gbc);
    }

    private JPanel createStepPanel(String labelText, ImageIcon icon, JTextField textField, JButton button, JLabel checkLabel) {
        JPanel panel = new JPanel(new BorderLayout(5, 5)); panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        JPanel headerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        if (icon != null) headerPanel.add(new JLabel(icon));
        headerPanel.add(new JLabel(labelText)); panel.add(headerPanel, BorderLayout.NORTH); panel.add(textField, BorderLayout.CENTER);
        JPanel buttonPanel = new JPanel(new BorderLayout(5, 0)); buttonPanel.add(button, BorderLayout.CENTER); buttonPanel.add(checkLabel, BorderLayout.EAST); panel.add(buttonPanel, BorderLayout.EAST);
        return panel;
    }

    private void log(String message) {
        SwingUtilities.invokeLater(() -> { logArea.append(message + "\n"); logArea.setCaretPosition(logArea.getDocument().getLength()); });
    }

    private String calculateSha256(byte[] fileBytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256"); byte[] hash = digest.digest(fileBytes);
            StringBuilder hexString = new StringBuilder(2 * hash.length);
            for (byte b : hash) { String hex = Integer.toHexString(0xff & b); if (hex.length() == 1) hexString.append('0'); hexString.append(hex); }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) { return "N/A"; }
    }

    private Icon createCheckIcon() {
        return new Icon() {
            @Override public void paintIcon(Component c, Graphics g, int x, int y) { Graphics2D g2 = (Graphics2D) g.create(); g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON); g2.setStroke(new BasicStroke(2.5f)); g2.setColor(new Color(0, 153, 0)); g2.drawLine(x + 3, y + 8, x + 8, y + 13); g2.drawLine(x + 8, y + 13, x + 15, y + 4); g2.dispose(); }
            @Override public int getIconWidth() { return 20; }
            @Override public int getIconHeight() { return 20; }
        };
    }

    public static void main(String[] args) {
        UIManager.put("Component.accentColor", new Color(0xbd93f9));
        UIManager.put("Component.focusColor", new Color(0x6272a4));
        UIManager.put("TextField.selectionBackground", new Color(0x44475a));
        UIManager.put("Button.default.background", new Color(0x6272a4));
        UIManager.put("ProgressBar.foreground", new Color(0x50fa7b));
        FlatDarkLaf.setup();
        SwingUtilities.invokeLater(PKICertGenV2::new);
    }
}