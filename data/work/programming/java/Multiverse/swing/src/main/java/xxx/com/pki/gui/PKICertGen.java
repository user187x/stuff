package xxx.com.pki.gui;

import com.formdev.flatlaf.FlatDarkLaf;
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
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
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
 * A simple Java Swing application to generate PKI certificates for a client/server setup using
 * keytool. This utility provides a graphical interface to create a Certificate Authority (CA), a
 * server certificate, and a client certificate.
 */
public class PKICertGen extends JFrame {

  private final JTextField tfCn = new JTextField("John Doe", 20);
  private final JTextField tfPwd = new JTextField();
  private final JTextField tfOrg = new JTextField("USA", 20);
  private final List<JTextField> ouFields = new ArrayList<>();
  private JPanel ouPanelContainer; // Panel to hold dynamic OU fields
  private final JTextField tfCity = new JTextField("DC", 20);
  private final JTextField tfState = new JTextField("Maryland", 20);
  private final JTextField tfCountry = new JTextField("US", 20);
  private final JPasswordField tfPassword = new JPasswordField("password", 20);

  private final JTextField tfCaCN = new JTextField("ca-authority.p12", 20);
  private final JTextField tfServerCN = new JTextField("server.p12", 20);
  private final JTextField tfClientCN = new JTextField("client.p12", 20);
  private final JTextField tfValidity = new JTextField("365", 5);
  private final JCheckBox chkNeverExpire = new JCheckBox("Never Expire");

  private final JTextArea logArea = new JTextArea(15, 80);
  private final JButton generateCaButton = new JButton("Generate CA Keystore");
  private final JButton generateServerButton = new JButton("Generate Server Keystore");
  private final JButton generateClientButton = new JButton("Generate Client Keystore");
  private final JLabel caCheckLabel = new JLabel();
  private final JLabel serverCheckLabel = new JLabel();
  private final JLabel clientCheckLabel = new JLabel();

  // New components for public key generation
  private final JButton generatePublicKeyButton = new JButton("Create Public Key");
  private final JTextField tfPublicKeyFilename = new JTextField("public-key.pem", 20);
  private final JLabel publicKeyCheckLabel = new JLabel();
  private JPanel publicKeyPanel;

  private JProgressBar progressBar;

  private final Map<String, byte[]> generatedFiles = new ConcurrentHashMap<>();
  private DefaultTableModel fileTableModel;
  private JTable fileTable;
  private JMenuItem exportDerItem; // Context menu item for DER export
  private static Image appIcon;

  private static final Color KINDA_GRAY = new Color(135, 135, 135);

  public PKICertGen() {
    super("PKI Certificate Generator");
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    initComponents();
    // pack();
    setSize(800, 600);
    setLocationRelativeTo(null); // Center the window
    setVisible(true);
  }

  private void initComponents() {

    Image image =
        Toolkit.getDefaultToolkit().getImage(getClass().getResource("/png/pki/storm.png"));
    appIcon = image.getScaledInstance(40, 40, Image.SCALE_SMOOTH);
    setIconImage(appIcon);

    JPanel mainPanel = new JPanel(new BorderLayout(10, 0));
    mainPanel.setPreferredSize(new Dimension(800, 600));
    mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

    // --- Configuration Panel ---
    JPanel configPanel = new JPanel(new GridBagLayout()); // Use GridBagLayout for alignment
    configPanel.setBorder(BorderFactory.createTitledBorder("Certificate Details"));
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.insets = new Insets(2, 5, 2, 5);
    gbc.anchor = GridBagConstraints.WEST;
    int gridY = 0;

    // Add components row by row using the helper method for perfect alignment
    addConfigRow(configPanel, gbc, gridY++, "Common Name (CN):", tfCn);
    addConfigRow(configPanel, gbc, gridY++, "Organization (O):", tfOrg);

    // Special handling for dynamic OU panel
    gbc.gridy = gridY++;
    gbc.gridx = 0;
    gbc.anchor = GridBagConstraints.NORTHEAST; // Align label to the top-right of its cell
    configPanel.add(new JLabel("Organizational Unit (OU):"), gbc);

    ouPanelContainer = new JPanel();
    ouPanelContainer.setLayout(new BoxLayout(ouPanelContainer, BoxLayout.Y_AXIS));
    addOuField(); // Add the initial OU field with a '+' button

    gbc.gridx = 1;
    gbc.anchor = GridBagConstraints.WEST;
    configPanel.add(ouPanelContainer, gbc);

    addConfigRow(configPanel, gbc, gridY++, "City/Locality (L):", tfCity);
    addConfigRow(configPanel, gbc, gridY++, "State/Province (S):", tfState);
    addConfigRow(configPanel, gbc, gridY++, "Country Code (C):", tfCountry);

    // --- Password reveal component setup ---
    JPanel passwordPanel = new JPanel(new BorderLayout());
    passwordPanel.add(tfPassword, BorderLayout.CENTER);

    // Load icons, assuming they are in the same resource path as the others
    ImageIcon eyeOpenIcon =
        recolorIcon(
            new ImageIcon(
                new ImageIcon(
                        Objects.requireNonNull(
                            getClass().getResource("/png/password/eye-open.png")))
                    .getImage()
                    .getScaledInstance(20, 20, Image.SCALE_SMOOTH)),
            KINDA_GRAY);
    ImageIcon eyeShutIcon =
        recolorIcon(
            new ImageIcon(
                new ImageIcon(
                        Objects.requireNonNull(
                            getClass().getResource("/png/password/eye-shut.png")))
                    .getImage()
                    .getScaledInstance(20, 20, Image.SCALE_SMOOTH)),
            KINDA_GRAY);

    JLabel passwordRevealLabel = new JLabel(eyeShutIcon);
    passwordRevealLabel.setBorder(new EmptyBorder(0, 5, 0, 5));
    passwordRevealLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));
    passwordPanel.add(passwordRevealLabel, BorderLayout.EAST);

    // Store the default echo character
    char defaultEchoChar = tfPassword.getEchoChar();

    passwordRevealLabel.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mousePressed(MouseEvent e) {
            passwordRevealLabel.setIcon(eyeOpenIcon);
            tfPassword.setEchoChar((char) 0); // Show text
          }

          @Override
          public void mouseReleased(MouseEvent e) {
            passwordRevealLabel.setIcon(eyeShutIcon);
            tfPassword.setEchoChar(defaultEchoChar); // Hide text
          }
        });

    addConfigRow(configPanel, gbc, gridY++, "Keystore Password:", passwordPanel);
    // --- End of password reveal setup ---

    // Validity Panel needs to combine two components in the second column
    JPanel validityPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    validityPanel.add(tfValidity);
    validityPanel.add(Box.createHorizontalStrut(5));
    validityPanel.add(chkNeverExpire);
    addConfigRow(configPanel, gbc, gridY++, "Expiration (Days):", validityPanel);

    chkNeverExpire.addActionListener(e -> tfValidity.setEnabled(!chkNeverExpire.isSelected()));

    // --- Generation Control Panel ---
    JPanel generatorPanel = new JPanel();
    generatorPanel.setLayout(new BoxLayout(generatorPanel, BoxLayout.Y_AXIS));
    generatorPanel.setBorder(BorderFactory.createTitledBorder("Certificate Generation"));

    // Load icons for each step (20x20)
    ImageIcon caIcon =
        new ImageIcon(
            new ImageIcon(
                    Objects.requireNonNull(getClass().getResource("/png/pki/certificate5.png")))
                .getImage()
                .getScaledInstance(24, 24, Image.SCALE_SMOOTH));
    // NOTE: Assuming these icon files exist in the resources at the specified path
    ImageIcon serverIcon =
        new ImageIcon(
            new ImageIcon(Objects.requireNonNull(getClass().getResource("/png/server2.png")))
                .getImage()
                .getScaledInstance(22, 22, Image.SCALE_SMOOTH));
    ImageIcon clientIcon =
        new ImageIcon(
            new ImageIcon(Objects.requireNonNull(getClass().getResource("/png/client.png")))
                .getImage()
                .getScaledInstance(22, 22, Image.SCALE_SMOOTH));
    ImageIcon publicKeyIcon =
        new ImageIcon(
            new ImageIcon(Objects.requireNonNull(getClass().getResource("/png/pki/publicKey3.png")))
                .getImage()
                .getScaledInstance(22, 22, Image.SCALE_SMOOTH));

    Icon checkIcon = createCheckIcon();
    caCheckLabel.setIcon(checkIcon);
    serverCheckLabel.setIcon(checkIcon);
    clientCheckLabel.setIcon(checkIcon);
    caCheckLabel.setVisible(false);
    serverCheckLabel.setVisible(false);
    clientCheckLabel.setVisible(false);

    JPanel caPanel =
        createStepPanel(
            "Certificate Authority (CA)", caIcon, tfCaCN, generateCaButton, caCheckLabel);
    JPanel serverPanel =
        createStepPanel(
            "Server Certificate", serverIcon, tfServerCN, generateServerButton, serverCheckLabel);
    JPanel clientPanel =
        createStepPanel(
            "Client Certificate", clientIcon, tfClientCN, generateClientButton, clientCheckLabel);

    generatorPanel.add(caPanel);
    generatorPanel.add(Box.createRigidArea(new Dimension(0, 5)));
    generatorPanel.add(serverPanel);
    generatorPanel.add(Box.createRigidArea(new Dimension(0, 5)));
    generatorPanel.add(clientPanel);

    // --- Create the hidden Public Key Panel ---
    publicKeyCheckLabel.setIcon(checkIcon);
    publicKeyCheckLabel.setVisible(false);
    publicKeyPanel =
        createStepPanel(
            "Client Public Key",
            publicKeyIcon,
            tfPublicKeyFilename,
            generatePublicKeyButton,
            publicKeyCheckLabel);
    publicKeyPanel.setVisible(false); // Hide initially
    // Set initial size to zero for animation
    publicKeyPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 0));

    generatorPanel.add(Box.createRigidArea(new Dimension(0, 5)));
    generatorPanel.add(publicKeyPanel); // Add to layout

    JPanel topPanel = new JPanel(new BorderLayout(10, 0));
    topPanel.add(configPanel, BorderLayout.WEST);
    topPanel.add(generatorPanel, BorderLayout.CENTER);

    // --- Container for Top Panel and Clear Button ---
    JPanel topSectionPanel = new JPanel(new BorderLayout());
    topSectionPanel.add(topPanel, BorderLayout.CENTER);

    // --- Clear Button Panel ---
    JPanel clearButtonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
    clearButtonPanel.setBorder(new EmptyBorder(5, 5, 2, 5));
    JButton clearButton = new JButton("Reset");
    clearButton.addActionListener(this::clearAllAction);
    clearButtonPanel.add(clearButton);
    topSectionPanel.add(clearButtonPanel, BorderLayout.SOUTH);

    // --- Tabbed Log/File Panel ---
    JTabbedPane bottomTabbedPane = new JTabbedPane();

    // Log Panel
    logArea.setEditable(false);
    logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
    JScrollPane logScrollPane = new JScrollPane(logArea);
    bottomTabbedPane.addTab("Output Log", logScrollPane);

    // Generated Files Panel (now a JTable)
    String[] columnNames = {
      "", "Filename", "Size (bytes)", "Type", "Generated", "Fingerprint (SHA-256)"
    };
    fileTableModel =
        new DefaultTableModel(columnNames, 0) {
          @Override
          public boolean isCellEditable(int row, int column) {
            return false; // Make table cells non-editable
          }

          @Override
          public Class<?> getColumnClass(int columnIndex) {
            if (columnIndex == 0) return Icon.class;
            return super.getColumnClass(columnIndex);
          }
        };

    fileTable = new JTable(fileTableModel);
    fileTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    fileTable.setRowHeight(24);
    fileTable.getColumnModel().getColumn(0).setMaxWidth(30); // Icon column
    fileTable.getColumnModel().getColumn(1).setPreferredWidth(120); // Filename
    fileTable.getColumnModel().getColumn(2).setPreferredWidth(80); // Size
    fileTable.getColumnModel().getColumn(3).setPreferredWidth(120); // Type
    fileTable.getColumnModel().getColumn(4).setPreferredWidth(140); // Timestamp
    fileTable.getColumnModel().getColumn(5).setPreferredWidth(450); // Fingerprint

    // --- Context Menu for File Table ---
    JPopupMenu fileTableContextMenu = new JPopupMenu();
    JMenuItem viewItem = new JMenuItem("View");
    JMenuItem saveItem = new JMenuItem("Save");
    exportDerItem = new JMenuItem("Export as DER");

    viewItem.addActionListener(
        e -> {
          int selectedRow = fileTable.getSelectedRow();
          if (selectedRow >= 0) {
            String filename = (String) fileTable.getValueAt(selectedRow, 1);
            viewFileAction(filename);
          }
        });

    saveItem.addActionListener(
        e -> {
          int selectedRow = fileTable.getSelectedRow();
          if (selectedRow >= 0) {
            String filename = (String) fileTable.getValueAt(selectedRow, 1);
            saveFileAction(filename);
          }
        });

    exportDerItem.addActionListener(
        e -> {
          int selectedRow = fileTable.getSelectedRow();
          if (selectedRow >= 0) {
            String filename = (String) fileTable.getValueAt(selectedRow, 1);
            exportAsDerAction(filename);
          }
        });

    fileTableContextMenu.add(viewItem);
    fileTableContextMenu.add(saveItem);
    fileTableContextMenu.add(exportDerItem);

    fileTable.addMouseListener(
        new MouseAdapter() {
          // Handle double-click to save
          public void mouseClicked(MouseEvent e) {
            if (e.getClickCount() == 2 && !e.isPopupTrigger()) {
              int row = fileTable.rowAtPoint(e.getPoint());
              if (row >= 0) {
                String filename = (String) fileTable.getValueAt(row, 1);
                saveFileAction(filename);
              }
            }
          }

          // Handle right-click for context menu
          private void showPopup(MouseEvent e) {
            if (e.isPopupTrigger()) {
              int row = fileTable.rowAtPoint(e.getPoint());
              if (row >= 0) {
                // Select the row under the cursor before showing the context menu
                fileTable.setRowSelectionInterval(row, row);

                // Enable/disable DER export based on file type
                String filename = (String) fileTable.getValueAt(row, 1);
                boolean isExportable =
                    filename.toLowerCase().endsWith(".p12")
                        || filename.toLowerCase().endsWith(".pem");
                exportDerItem.setEnabled(isExportable);

                fileTableContextMenu.show(e.getComponent(), e.getX(), e.getY());
              }
            }
          }

          @Override
          public void mousePressed(MouseEvent e) {
            showPopup(e);
          }

          @Override
          public void mouseReleased(MouseEvent e) {
            showPopup(e);
          }
        });
    JScrollPane fileTableScrollPane = new JScrollPane(fileTable);
    bottomTabbedPane.addTab("Generated Files", fileTableScrollPane);

    mainPanel.add(topSectionPanel, BorderLayout.NORTH);
    mainPanel.add(bottomTabbedPane, BorderLayout.CENTER);

    // --- Progress Bar ---
    progressBar = new JProgressBar();
    progressBar.setIndeterminate(true);
    progressBar.setStringPainted(true);
    progressBar.setVisible(false); // Initially hidden
    mainPanel.add(progressBar, BorderLayout.SOUTH);

    // --- Button Actions ---
    generateCaButton.addActionListener(this::generateCaAction);
    generateServerButton.addActionListener(this::generateServerAction);
    generateClientButton.addActionListener(this::generateClientAction);
    generatePublicKeyButton.addActionListener(this::generatePublicKeyAction);

    // --- Drag and Drop ---
    mainPanel.setDropTarget(
        new DropTarget(
            this,
            DnDConstants.ACTION_COPY,
            new DropTargetAdapter() {
              @Override
              public void drop(DropTargetDropEvent dtde) {
                handleFileDrop(dtde);
              }
            }));

    add(mainPanel);
  }

  public static ImageIcon recolorIcon(ImageIcon icon, Color color) {

    BufferedImage bufferedImage =
        new BufferedImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
    Graphics2D g2d = bufferedImage.createGraphics();

    g2d.drawImage(icon.getImage(), 0, 0, null);
    g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_IN, 1.0f));
    g2d.setColor(color);
    g2d.fillRect(0, 0, icon.getIconWidth(), icon.getIconHeight());
    g2d.dispose();

    return new ImageIcon(bufferedImage);
  }

  private void showProgress(String message) {
    SwingUtilities.invokeLater(
        () -> {
          progressBar.setString(message);
          progressBar.setVisible(true);
          generateCaButton.setEnabled(false);
          generateServerButton.setEnabled(false);
          generateClientButton.setEnabled(false);
          generatePublicKeyButton.setEnabled(false);
        });
  }

  private void hideProgress() {
    SwingUtilities.invokeLater(
        () -> {
          progressBar.setVisible(false);
          progressBar.setString("");
          generateCaButton.setEnabled(true);
          generateServerButton.setEnabled(true);
          generateClientButton.setEnabled(true);
          generatePublicKeyButton.setEnabled(true);
        });
  }

  private void addOuField() {
    JPanel ouRowPanel = new JPanel(new BorderLayout(5, 0));
    ouRowPanel.setBorder(new EmptyBorder(0, 0, 2, 0)); // Small bottom margin

    boolean isFirst = ouFields.isEmpty();

    JTextField ouField = new JTextField(isFirst ? "Cyber" : "", 20);
    ouFields.add(ouField);

    JButton button = new JButton(isFirst ? "+" : "-");
    button.setMargin(new Insets(2, 5, 2, 5));
    button.setFont(button.getFont().deriveFont(Font.BOLD));

    if (isFirst) {
      button.addActionListener(e -> addOuField());
    } else {
      button.addActionListener(e -> removeOuField(ouRowPanel, ouField));
    }

    ouRowPanel.add(ouField, BorderLayout.CENTER);
    ouRowPanel.add(button, BorderLayout.EAST);
    ouPanelContainer.add(ouRowPanel);
    ouPanelContainer.revalidate();
    ouPanelContainer.repaint();
    // FIX: Only pack the window if the component is part of a visible window hierarchy.
    Window window = SwingUtilities.getWindowAncestor(ouPanelContainer);
    if (window != null) {
      window.pack();
    }
  }

  private void removeOuField(JPanel panelToRemove, JTextField fieldToRemove) {
    ouFields.remove(fieldToRemove);
    ouPanelContainer.remove(panelToRemove);
    ouPanelContainer.revalidate();
    ouPanelContainer.repaint();
    // FIX: Also check here for safety.
    Window window = SwingUtilities.getWindowAncestor(ouPanelContainer);
    if (window != null) {
      window.pack();
    }
  }

  private void viewFileAction(String filename) {
    byte[] content = generatedFiles.get(filename);
    if (content != null) {
      // Attempt to display the content as text. This is useful for PEM files.
      // For binary files like .p12, it will show non-human-readable characters.
      JTextArea textArea = new JTextArea(new String(content));
      textArea.setEditable(false);
      textArea.setLineWrap(true);
      textArea.setWrapStyleWord(true);
      textArea.setFont(new Font("Monospaced", Font.PLAIN, 12));

      JScrollPane scrollPane = new JScrollPane(textArea);
      scrollPane.setPreferredSize(new Dimension(600, 400));

      JOptionPane.showMessageDialog(
          this, scrollPane, "Viewing File: " + filename, JOptionPane.PLAIN_MESSAGE);
    } else {
      JOptionPane.showMessageDialog(
          this,
          "Could not find file content in memory for: " + filename,
          "Error",
          JOptionPane.ERROR_MESSAGE);
    }
  }

  private void saveFileAction(String filename) {
    JFileChooser fileChooser = new JFileChooser();
    fileChooser.setDialogTitle("Save " + filename);
    fileChooser.setSelectedFile(new File(filename));
    int userSelection = fileChooser.showSaveDialog(this);

    if (userSelection == JFileChooser.APPROVE_OPTION) {
      File fileToSave = fileChooser.getSelectedFile();
      byte[] content = generatedFiles.get(filename);
      if (content != null) {
        try (FileOutputStream fos = new FileOutputStream(fileToSave)) {
          fos.write(content);
          log("Successfully saved file: " + fileToSave.getAbsolutePath());
        } catch (Exception e) {
          log("ERROR: Could not save file " + fileToSave.getName());
          JOptionPane.showMessageDialog(
              this,
              "Error saving file: " + e.getMessage(),
              "File Save Error",
              JOptionPane.ERROR_MESSAGE);
        }
      }
    }
  }

  /**
   * Handles the action to export a certificate file (.p12 or .pem) to binary DER format.
   *
   * @param filename The name of the file in memory to export.
   */
  private void exportAsDerAction(String filename) {
    if (!generatedFiles.containsKey(filename)) {
      log("ERROR: File " + filename + " not found in memory.");
      JOptionPane.showMessageDialog(
          this, "File content not found in memory.", "Export Error", JOptionPane.ERROR_MESSAGE);
      return;
    }

    String derFilename = filename.substring(0, filename.lastIndexOf('.')) + ".der";
    byte[] derBytes = null;

    try {
      if (filename.toLowerCase().endsWith(".pem")) {
        // Handle PEM to DER conversion using Java Crypto API
        log("--- Converting " + filename + " from PEM to DER ---");
        byte[] pemBytes = generatedFiles.get(filename);
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        X509Certificate cert =
            (X509Certificate) cf.generateCertificate(new java.io.ByteArrayInputStream(pemBytes));
        derBytes = cert.getEncoded();
        log("Successfully converted PEM to DER in memory.");

      } else if (filename.toLowerCase().endsWith(".p12")) {
        // Handle P12 to DER conversion using keytool
        log("--- Exporting certificate from " + filename + " to DER format ---");
        String alias;
        switch (filename) {
          case "certificate-authority.p12" -> alias = "rootca";
          case "server.p12" -> alias = "server";
          case "client.p12" -> alias = "client";
          default -> {
            JOptionPane.showMessageDialog(
                this,
                "Cannot determine alias for '"
                    + filename
                    + "'.\nDER export is supported only for standard generated keystores.",
                "Export Error",
                JOptionPane.ERROR_MESSAGE);
            return;
          }
        }
        derBytes = exportCertificateFromKeystore(filename, alias);

      } else {
        JOptionPane.showMessageDialog(
            this,
            "DER export is not supported for this file type: " + filename,
            "Unsupported Operation",
            JOptionPane.WARNING_MESSAGE);
        return;
      }

      if (derBytes != null) {
        // Prompt user to save the generated DER bytes
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Export as DER");
        fileChooser.setSelectedFile(new File(derFilename));
        int userSelection = fileChooser.showSaveDialog(this);

        if (userSelection == JFileChooser.APPROVE_OPTION) {
          File fileToSave = fileChooser.getSelectedFile();
          try (FileOutputStream fos = new FileOutputStream(fileToSave)) {
            fos.write(derBytes);
            log("Successfully exported to DER: " + fileToSave.getAbsolutePath());
          } catch (Exception e) {
            log("ERROR: Could not save DER file " + fileToSave.getName());
            JOptionPane.showMessageDialog(
                this,
                "Error saving file: " + e.getMessage(),
                "File Save Error",
                JOptionPane.ERROR_MESSAGE);
          }
        }
      } else {
        log("ERROR: Failed to generate DER content for " + filename);
        JOptionPane.showMessageDialog(
            this, "Failed to generate DER content.", "Export Error", JOptionPane.ERROR_MESSAGE);
      }

    } catch (Exception e) {
      log("ERROR during DER export for " + filename + ": " + e.getMessage());
      JOptionPane.showMessageDialog(
          this,
          "An error occurred during DER export: " + e.getMessage(),
          "Export Error",
          JOptionPane.ERROR_MESSAGE);
    }
  }

  /**
   * Extracts a certificate from an in-memory keystore and returns its DER-encoded bytes.
   *
   * @param keystoreFilename The filename of the .p12 keystore in memory.
   * @param alias The alias of the certificate to export.
   * @return A byte array of the DER-encoded certificate, or null on failure.
   */
  private byte[] exportCertificateFromKeystore(String keystoreFilename, String alias) {
    final String password = new String(tfPassword.getPassword());
    byte[] keystoreBytes = generatedFiles.get(keystoreFilename);
    if (keystoreBytes == null) {
      log("ERROR: Keystore " + keystoreFilename + " not found in memory.");
      return null;
    }

    Path tempKeystore = null;
    Path tempDerFile = null;

    try {
      // Write in-memory keystore to a temp file
      tempKeystore = Files.createTempFile("keystore_temp", ".p12");
      Files.write(tempKeystore, keystoreBytes);

      tempDerFile = Files.createTempFile("cert_export", ".der");
      Files.delete(tempDerFile); // keytool will create it

      String keystorePath = tempKeystore.toAbsolutePath().toString();
      String derPath = tempDerFile.toAbsolutePath().toString();

      List<String> command =
          Arrays.asList(
              "keytool",
              "-exportcert",
              "-alias",
              alias,
              "-keystore",
              keystorePath,
              "-storetype",
              "pkcs12",
              "-storepass",
              password,
              "-file",
              derPath
              // NOTE: The absence of the '-rfc' flag produces DER format by default
              );

      if (executeCommandSync(command)) {
        return Files.readAllBytes(tempDerFile);
      } else {
        log("ERROR: keytool command failed to export DER certificate for alias '" + alias + "'.");
        return null;
      }

    } catch (Exception ex) {
      log("ERROR during keystore certificate export: " + ex.getMessage());
      return null;
    } finally {
      // Cleanup temporary files
      try {
        if (tempKeystore != null) Files.deleteIfExists(tempKeystore);
        if (tempDerFile != null) Files.deleteIfExists(tempDerFile);
      } catch (Exception ex) {
        log("Warning: Failed to clean up temporary files for DER export.");
      }
    }
  }

  /**
   * Helper method to add a labeled component row to a GridBagLayout panel.
   *
   * @param panel The parent panel with GridBagLayout.
   * @param gbc The GridBagConstraints object.
   * @param y The gridy (row) position.
   * @param labelText The text for the label.
   * @param component The component to add next to the label.
   */
  private void addConfigRow(
      JPanel panel, GridBagConstraints gbc, int y, String labelText, JComponent component) {
    gbc.gridy = y;
    gbc.gridx = 0;
    gbc.anchor = GridBagConstraints.EAST;
    gbc.fill = GridBagConstraints.NONE;
    gbc.weightx = 0;
    panel.add(new JLabel(labelText), gbc);

    gbc.gridx = 1;
    gbc.anchor = GridBagConstraints.WEST;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.weightx = 1;
    panel.add(component, gbc);
  }

  private JPanel createStepPanel(
      String labelText, ImageIcon icon, JTextField textField, JButton button, JLabel checkLabel) {
    JPanel panel = new JPanel(new BorderLayout(5, 5));
    panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

    // Create a header panel for the icon and text
    JPanel headerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
    headerPanel.add(new JLabel(icon));
    headerPanel.add(new JLabel(labelText));
    panel.add(headerPanel, BorderLayout.NORTH);

    panel.add(textField, BorderLayout.CENTER);

    JPanel buttonPanel = new JPanel(new BorderLayout(5, 0));
    buttonPanel.add(button, BorderLayout.CENTER);
    buttonPanel.add(checkLabel, BorderLayout.EAST);
    panel.add(buttonPanel, BorderLayout.EAST);

    return panel;
  }

  private void generateCaAction(ActionEvent e) {
    String caFilename = "certificate-authority.p12";
    if (generatedFiles.containsKey(caFilename)) {
      int result =
          JOptionPane.showConfirmDialog(
              this,
              "A CA keystore already exists. Overwriting it will invalidate any existing server and client certificates.\n"
                  + "Do you want to proceed?",
              "Confirm Overwrite",
              JOptionPane.YES_NO_OPTION,
              JOptionPane.WARNING_MESSAGE);
      if (result == JOptionPane.NO_OPTION) {
        log("CA regeneration cancelled by user.");
        return;
      }
      // User confirmed overwrite.
      log("Overwriting CA. Removing existing certificates.");
      removeGeneratedFile("server.p12");
      removeGeneratedFile("client.p12");
      removeGeneratedFile(caFilename);
    }

    showProgress("Generating Certificate Authority...");
    generateCaAsync()
        .whenComplete(
            (success, ex) -> {
              if (success != null && success) {
                log("CA generation process completed successfully.");
              } else {
                log("CA generation process failed.");
                if (ex != null) {
                  log("ERROR: " + ex.getMessage());
                }
              }
              hideProgress();
            });
  }

  private void generateServerAction(ActionEvent e) {
    showProgress("Generating Server Keystore...");
    ensureCaExists()
        .thenComposeAsync(
            caSuccess -> {
              if (caSuccess) {
                // FIX: Read UI components on the Event Dispatch Thread (EDT)
                String serverCN = tfServerCN.getText();
                return generateCertificate(
                    "server", serverCN, "san=dns:" + serverCN + ",ip:127.0.0.1");
              } else {
                log(
                    "ERROR: Server certificate generation failed because CA could not be generated.");
                return CompletableFuture.completedFuture(false);
              }
            })
        .whenComplete(
            (certSuccess, ex) -> {
              if (ex != null) {
                log("ERROR: " + ex.getMessage());
              }
              hideProgress();
            });
  }

  private void generateClientAction(ActionEvent e) {
    showProgress("Generating Client Keystore...");
    ensureCaExists()
        .thenComposeAsync(
            caSuccess -> {
              if (caSuccess) {
                // FIX: Read UI components on the Event Dispatch Thread (EDT)
                return generateCertificate("client", tfClientCN.getText(), null);
              } else {
                log(
                    "ERROR: Client certificate generation failed because CA could not be generated.");
                return CompletableFuture.completedFuture(false);
              }
            })
        .whenComplete(
            (certSuccess, ex) -> {
              if (ex != null) {
                log("ERROR: " + ex.getMessage());
              }
              hideProgress();
            });
  }

  private void generatePublicKeyAction(ActionEvent e) {
    showProgress("Generating Client Public Key...");
    // FIX: Read UI components on the Event Dispatch Thread (EDT) before starting a background task.
    final String publicKeyFilename = tfPublicKeyFilename.getText();
    final String password = new String(tfPassword.getPassword());

    CompletableFuture.runAsync(
            () -> {
              log("--- Generating Client Public Key (PEM) ---");
              String clientKeystoreName = "client.p12";

              if (!generatedFiles.containsKey(clientKeystoreName)) {
                log(
                    "ERROR: Client Keystore 'client.p12' not found in memory. Please generate it first.");
                return;
              }
              if (publicKeyFilename == null || publicKeyFilename.trim().isEmpty()) {
                log("ERROR: Public key filename cannot be empty.");
                return;
              }

              Path tempClientKeystore = null;
              Path tempPemFile = null;
              try {
                // Write in-memory client keystore to a temp file
                byte[] clientKeystoreBytes = generatedFiles.get(clientKeystoreName);
                tempClientKeystore = Files.createTempFile("client_temp", ".p12");
                Files.write(tempClientKeystore, clientKeystoreBytes);

                tempPemFile = Files.createTempFile("client_pub", ".pem");
                Files.delete(tempPemFile); // keytool creates it

                String clientKsPath = tempClientKeystore.toAbsolutePath().toString();
                String pemPath = tempPemFile.toAbsolutePath().toString();

                // Execute keytool command to export the certificate
                List<String> command =
                    Arrays.asList(
                        "keytool",
                        "-exportcert",
                        "-alias",
                        "client",
                        "-keystore",
                        clientKsPath,
                        "-storetype",
                        "pkcs12",
                        "-storepass",
                        password,
                        "-rfc", // This specifies PEM format
                        "-file",
                        pemPath);

                if (executeCommandSync(command)) {
                  byte[] pemBytes = Files.readAllBytes(tempPemFile);
                  String fingerprint = calculateSha256(pemBytes);
                  addGeneratedFile(publicKeyFilename, pemBytes, "Public Key (PEM)", fingerprint);
                  log(
                      "--- Successfully generated public key. Find it in the 'Generated Files' tab. ---");
                }

              } catch (Exception ex) {
                log("ERROR during public key generation: " + ex.getMessage());
              } finally {
                // Cleanup temporary files
                try {
                  if (tempClientKeystore != null) Files.deleteIfExists(tempClientKeystore);
                  if (tempPemFile != null) Files.deleteIfExists(tempPemFile);
                } catch (Exception ex) {
                  log("Warning: Failed to clean up temporary files for public key generation.");
                }
              }
            })
        .whenComplete(
            (v, ex) -> {
              if (ex != null) {
                log("ERROR during public key generation task: " + ex.getMessage());
              }
              hideProgress();
            });
  }

  private void clearAllAction(ActionEvent e) {
    // Reset certificate details
    tfCn.setText("MyCn");
    tfOrg.setText("MyOrg");
    tfCity.setText("DC");
    tfState.setText("Maryland");
    tfCountry.setText("US");
    tfPassword.setText("password");

    // Reset OU fields to a single default
    ouPanelContainer.removeAll();
    ouFields.clear();
    addOuField();

    // Reset validity
    tfValidity.setText("365");
    chkNeverExpire.setSelected(false);
    tfValidity.setEnabled(true);

    // Reset certificate generation text fields
    tfCaCN.setText("ca-authority.p12");
    tfServerCN.setText("server.p12");
    tfClientCN.setText("client.p12");
    tfPublicKeyFilename.setText("public-key.pem");

    // Hide all checkmarks
    caCheckLabel.setVisible(false);
    serverCheckLabel.setVisible(false);
    clientCheckLabel.setVisible(false);
    publicKeyCheckLabel.setVisible(false);

    // Hide the public key panel
    togglePublicKeyPanel(false);

    // Clear data and UI tables/logs
    generatedFiles.clear();
    fileTableModel.setRowCount(0);
    logArea.setText("");

    log("UI cleared. Ready for new certificate generation.");
  }

  private void togglePublicKeyPanel(boolean show) {
    // Ensure this runs on the EDT
    if (!SwingUtilities.isEventDispatchThread()) {
      SwingUtilities.invokeLater(() -> togglePublicKeyPanel(show));
      return;
    }

    int targetHeight = show ? publicKeyPanel.getPreferredSize().height : 0;
    if (publicKeyPanel.getHeight() == targetHeight) {
      if (show) publicKeyPanel.setVisible(true);
      return; // No animation needed
    }

    publicKeyPanel.setVisible(true); // Make it visible to animate

    int duration = 200; // Animation duration in ms
    int steps = 20;
    int stepDelay = duration / steps;
    int heightStep = (targetHeight - publicKeyPanel.getHeight()) / steps;

    javax.swing.Timer timer = new javax.swing.Timer(stepDelay, null);
    timer.addActionListener(
        e -> {
          int currentHeight = publicKeyPanel.getHeight();
          int newHeight = currentHeight + heightStep;

          if ((heightStep > 0 && newHeight >= targetHeight)
              || (heightStep < 0 && newHeight <= targetHeight)) {
            // Animation finished
            publicKeyPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, targetHeight));
            if (!show) {
              publicKeyPanel.setVisible(false);
              publicKeyCheckLabel.setVisible(false);
            }
            timer.stop();
          } else {
            publicKeyPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, newHeight));
          }
          // Revalidate the container
          publicKeyPanel.getParent().revalidate();
          publicKeyPanel.getParent().repaint();
        });
    timer.start();
  }

  /**
   * Ensures the Certificate Authority keystore exists, generating it asynchronously if it doesn't.
   *
   * @return A CompletableFuture that completes with true if the CA exists or was created
   *     successfully, false otherwise.
   */
  private CompletableFuture<Boolean> ensureCaExists() {
    if (generatedFiles.containsKey("certificate-authority.p12")) {
      return CompletableFuture.completedFuture(true);
    }
    return generateCaAsync();
  }

  /**
   * Performs the asynchronous generation of the Certificate Authority keystore.
   *
   * @return A CompletableFuture that completes with true on success, false on failure.
   */
  private CompletableFuture<Boolean> generateCaAsync() {
    log("--- Generating Certificate Authority (CA) ---");
    // FIX: Read UI components on the Event Dispatch Thread (EDT)
    final String dname = buildDname(tfCaCN.getText());
    final String password = new String(tfPassword.getPassword());
    final String validity = chkNeverExpire.isSelected() ? "99999" : "3650";

    CompletableFuture<Boolean> future = new CompletableFuture<>();
    CompletableFuture.runAsync(
        () -> {
          Path tempKeystore = null;
          try {
            tempKeystore = Files.createTempFile("ca", ".p12");
            Files.delete(tempKeystore); // keytool creates it

            List<String> command =
                Arrays.asList(
                    "keytool", "-genkeypair",
                    "-alias", "rootca",
                    "-keyalg", "RSA",
                    "-keysize", "2048",
                    "-validity", validity,
                    "-dname", dname,
                    "-keystore", tempKeystore.toAbsolutePath().toString(),
                    "-storetype", "pkcs12",
                    "-storepass", password,
                    "-keypass", password,
                    "-ext", "bc=ca:true");

            if (executeCommandSync(command)) {
              byte[] fileBytes = Files.readAllBytes(tempKeystore);
              String fingerprint = calculateSha256(fileBytes);
              addGeneratedFile(
                  "certificate-authority.p12", fileBytes, "Certificate Authority", fingerprint);
              log("--- Successfully generated CA. Find it in the 'Generated Files' tab. ---");
              future.complete(true);
            } else {
              future.complete(false);
            }
          } catch (Exception ex) {
            log("ERROR during CA generation: " + ex.getMessage());
            future.completeExceptionally(ex);
          } finally {
            try {
              if (tempKeystore != null) Files.deleteIfExists(tempKeystore);
            } catch (Exception ex) {
              log("Warning: Failed to clean up temporary CA file.");
            }
          }
        });
    return future;
  }

  private CompletableFuture<Boolean> generateCertificate(String alias, String cn, String san) {
    // FIX: Read UI components on the Event Dispatch Thread (EDT)
    final String password = new String(tfPassword.getPassword());
    final String validity = chkNeverExpire.isSelected() ? "99999" : tfValidity.getText();
    final String dname = buildDname(cn);
    final String keystoreName = alias + ".p12";
    final String caKeystoreName = "certificate-authority.p12";

    final CompletableFuture<Boolean> future = new CompletableFuture<>();
    log(String.format("--- Generating %s Certificate ---", alias));

    CompletableFuture.runAsync(
        () -> {
          Path tempCaKeystore = null,
              tempKeystore = null,
              tempCsr = null,
              tempCert = null,
              tempCaCert = null;
          try {
            byte[] caBytes = generatedFiles.get(caKeystoreName);
            tempCaKeystore = Files.createTempFile("ca_temp", ".p12");
            Files.write(tempCaKeystore, caBytes);

            tempKeystore = Files.createTempFile(alias, ".p12");
            Files.delete(tempKeystore);
            tempCsr = Files.createTempFile(alias, ".csr");
            Files.delete(tempCsr);
            tempCert = Files.createTempFile(alias, ".cer");
            Files.delete(tempCert);
            tempCaCert = Files.createTempFile("rootca", ".cer");
            Files.delete(tempCaCert);

            String ksPath = tempKeystore.toAbsolutePath().toString();
            String caKsPath = tempCaKeystore.toAbsolutePath().toString();
            String csrPath = tempCsr.toAbsolutePath().toString();
            String certPath = tempCert.toAbsolutePath().toString();
            String caCertPath = tempCaCert.toAbsolutePath().toString();

            if (!executeCommandSync(
                new ArrayList<>(
                    Arrays.asList(
                        "keytool",
                        "-genkeypair",
                        "-alias",
                        alias,
                        "-keyalg",
                        "RSA",
                        "-keysize",
                        "2048",
                        "-dname",
                        dname,
                        "-keystore",
                        ksPath,
                        "-storetype",
                        "pkcs12",
                        "-storepass",
                        password,
                        "-keypass",
                        password)))) {
              future.complete(false);
              return;
            }
            if (!executeCommandSync(
                Arrays.asList(
                    "keytool",
                    "-certreq",
                    "-alias",
                    alias,
                    "-file",
                    csrPath,
                    "-keystore",
                    ksPath,
                    "-storetype",
                    "pkcs12",
                    "-storepass",
                    password))) {
              future.complete(false);
              return;
            }

            List<String> signCsrCommand =
                new ArrayList<>(
                    Arrays.asList(
                        "keytool",
                        "-gencert",
                        "-alias",
                        "rootca",
                        "-infile",
                        csrPath,
                        "-outfile",
                        certPath,
                        "-keystore",
                        caKsPath,
                        "-storetype",
                        "pkcs12",
                        "-storepass",
                        password,
                        "-validity",
                        validity));
            if (san != null && !san.trim().isEmpty()) {
              signCsrCommand.add("-ext");
              signCsrCommand.add(san);
            }
            if (!executeCommandSync(signCsrCommand)) {
              future.complete(false);
              return;
            }
            if (!executeCommandSync(
                Arrays.asList(
                    "keytool",
                    "-exportcert",
                    "-alias",
                    "rootca",
                    "-file",
                    caCertPath,
                    "-keystore",
                    caKsPath,
                    "-storetype",
                    "pkcs12",
                    "-storepass",
                    password,
                    "-rfc"))) {
              future.complete(false);
              return;
            }
            if (!executeCommandSync(
                Arrays.asList(
                    "keytool",
                    "-importcert",
                    "-alias",
                    "rootca",
                    "-file",
                    caCertPath,
                    "-keystore",
                    ksPath,
                    "-storetype",
                    "pkcs12",
                    "-storepass",
                    password,
                    "-noprompt"))) {
              future.complete(false);
              return;
            }
            if (!executeCommandSync(
                Arrays.asList(
                    "keytool",
                    "-importcert",
                    "-alias",
                    alias,
                    "-file",
                    certPath,
                    "-keystore",
                    ksPath,
                    "-storetype",
                    "pkcs12",
                    "-storepass",
                    password))) {
              future.complete(false);
              return;
            }

            byte[] finalKeystoreBytes = Files.readAllBytes(tempKeystore);
            String type = alias.substring(0, 1).toUpperCase() + alias.substring(1) + " Keystore";
            String fingerprint = calculateSha256(finalKeystoreBytes);
            addGeneratedFile(keystoreName, finalKeystoreBytes, type, fingerprint);
            log(
                String.format(
                    "--- Successfully created %s keystore. Find it in the 'Generated Files' tab. ---",
                    alias));
            future.complete(true);

          } catch (Exception ex) {
            log("ERROR during certificate generation: " + ex.getMessage());
            future.complete(false);
          } finally {
            try {
              if (tempCaKeystore != null) Files.deleteIfExists(tempCaKeystore);
              if (tempKeystore != null) Files.deleteIfExists(tempKeystore);
              if (tempCsr != null) Files.deleteIfExists(tempCsr);
              if (tempCert != null) Files.deleteIfExists(tempCert);
              if (tempCaCert != null) Files.deleteIfExists(tempCaCert);
            } catch (Exception ex) {
              log("Warning: Failed to clean up temporary files.");
            }
          }
        });
    return future;
  }

  private String buildDname(String cn) {
    // FIX: Read UI components on the Event Dispatch Thread (EDT)
    final List<String> ouTexts = ouFields.stream().map(JTextField::getText).toList();
    final String orgText = tfOrg.getText();
    final String cityText = tfCity.getText();
    final String stateText = tfState.getText();
    final String countryText = tfCountry.getText();

    String ous =
        ouTexts.stream()
            .filter(s -> s != null && !s.trim().isEmpty())
            .map(s -> "OU=" + s)
            .collect(Collectors.joining(", "));

    return String.format(
        "CN=%s, %s, O=%s, L=%s, ST=%s, C=%s", cn, ous, orgText, cityText, stateText, countryText);
  }

  private void removeGeneratedFile(String filename) {
    if (!generatedFiles.containsKey(filename)) {
      return; // Nothing to remove
    }
    generatedFiles.remove(filename);
    SwingUtilities.invokeLater(
        () -> {
          // FIX: Filenames were outdated after a previous change.
          if ("certificate-authority.p12".equals(filename)) caCheckLabel.setVisible(false);
          if ("server.p12".equals(filename)) serverCheckLabel.setVisible(false);
          if ("client.p12".equals(filename)) {
            clientCheckLabel.setVisible(false);
            togglePublicKeyPanel(false); // Hide the panel
            // Also remove the public key file if it exists
            removeGeneratedFile(tfPublicKeyFilename.getText());
          }
          if (filename.equals(tfPublicKeyFilename.getText())) {
            publicKeyCheckLabel.setVisible(false);
          }

          // Find and remove the row from the table model
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
    SwingUtilities.invokeLater(
        () -> {
          // Set checkmark visibility
          // FIX: Filenames were outdated after a previous change, causing checkmarks not to appear.
          if ("certificate-authority.p12".equals(filename)) caCheckLabel.setVisible(true);
          if ("server.p12".equals(filename)) serverCheckLabel.setVisible(true);
          if ("client.p12".equals(filename)) {
            clientCheckLabel.setVisible(true);
            togglePublicKeyPanel(true); // Show the panel
          }
          if (filename.equals(tfPublicKeyFilename.getText())) {
            publicKeyCheckLabel.setVisible(true);
          }

          // Remove if it already exists to avoid duplicates, then add
          for (int i = 0; i < fileTableModel.getRowCount(); i++) {
            if (filename.equals(fileTableModel.getValueAt(i, 1))) {
              fileTableModel.removeRow(i);
              break;
            }
          }
          SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
          fileTableModel.addRow(
              new Object[] {
                UIManager.getIcon("FileView.fileIcon"),
                filename,
                content.length,
                type,
                sdf.format(new Date()),
                fingerprint
              });
        });
  }

  private boolean executeCommandSync(List<String> command) {
    try {
      log(
          "Executing: "
              + String.join(" ", command)
                  .replaceAll(new String(tfPassword.getPassword()), "********"));
      ProcessBuilder pb = new ProcessBuilder(command);
      pb.redirectErrorStream(true);
      Process process = pb.start();

      StringBuilder output = new StringBuilder();
      try (BufferedReader reader =
          new BufferedReader(new InputStreamReader(process.getInputStream()))) {
        String line;
        while ((line = reader.readLine()) != null) {
          output.append(line).append("\n");
        }
      }

      int exitCode = process.waitFor();
      String finalOutput = output.toString();

      if (!finalOutput.trim().isEmpty()) {
        log(finalOutput);
      }

      if (exitCode == 0) {
        log("Command executed successfully.");
        return true;
      } else {
        log("ERROR: Command failed with exit code " + exitCode);
        return false;
      }
    } catch (Exception e) {
      log("ERROR: Failed to execute command. Make sure 'keytool' is in your system's PATH.");
      log(e.getMessage());
      return false;
    }
  }

  private void handleFileDrop(DropTargetDropEvent dtde) {
    try {
      dtde.acceptDrop(DnDConstants.ACTION_COPY);
      Transferable transferable = dtde.getTransferable();
      if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {

        @SuppressWarnings("unchecked")
        List<File> files = (List<File>) transferable.getTransferData(DataFlavor.javaFileListFlavor);

        if (!files.isEmpty()) {
          File droppedFile = files.getFirst(); // Process the first file
          log("File dropped: " + droppedFile.getAbsolutePath());

          String lowerCaseName = droppedFile.getName().toLowerCase();
          if (lowerCaseName.endsWith(".p12")
              || lowerCaseName.endsWith(".jks")
              || lowerCaseName.endsWith(".keystore")) {
            handleDroppedKeystore(droppedFile);
          } else if (lowerCaseName.endsWith(".csr") || lowerCaseName.endsWith(".req")) {
            handleDroppedCsr(droppedFile);
          } else if (lowerCaseName.endsWith(".der")
              || lowerCaseName.endsWith(".cer")
              || lowerCaseName.endsWith(".crt")) {
            handleDroppedDer(droppedFile);
          } else {
            log(
                "Unsupported file type: "
                    + droppedFile.getName()
                    + ". Please drop a P12/JKS keystore, a CSR, or a DER/CER/CRT certificate file.");
          }
        }
      }
    } catch (Exception e) {
      log("ERROR: Could not handle dropped file. " + e.getMessage());
    } finally {
      dtde.dropComplete(true);
    }
  }

  private void handleDroppedKeystore(File file) {
    JPasswordField passwordField = new JPasswordField(20);
    Object[] message = {"Enter password for " + file.getName() + ":", passwordField};
    int option =
        JOptionPane.showConfirmDialog(
            this,
            message,
            "Keystore Password",
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE);

    if (option == JOptionPane.OK_OPTION) {
      char[] passwordChars = passwordField.getPassword();
      if (passwordChars != null && passwordChars.length > 0) {
        String password = new String(passwordChars);
        showProgress("Inspecting keystore: " + file.getName());

        CompletableFuture.runAsync(
                () -> {
                  try {
                    List<String> command =
                        Arrays.asList(
                            "keytool",
                            "-list",
                            "-v",
                            "-keystore",
                            file.getAbsolutePath(),
                            "-storepass",
                            password,
                            "-storetype",
                            "pkcs12");
                    String output = executeCommandAndGetOutput(command, password);

                    if (output == null
                        || output.contains(
                            "Keystore was tampered with, or password was incorrect")) {
                      log("PKCS12 failed, trying with JKS storetype...");
                      command =
                          Arrays.asList(
                              "keytool",
                              "-list",
                              "-v",
                              "-keystore",
                              file.getAbsolutePath(),
                              "-storepass",
                              password,
                              "-storetype",
                              "jks");
                      output = executeCommandAndGetOutput(command, password);
                    }

                    if (output != null
                        && !output.contains(
                            "Keystore was tampered with, or password was incorrect")) {
                      log("--- Keystore Details ---");
                      log(output);
                      log("------------------------");

                      String ownerDn = parseDnFromKeytoolOutput(output, "Owner:");
                      if (ownerDn != null) {
                        parseAndPopulateDname(ownerDn);
                      }

                      byte[] fileBytes = Files.readAllBytes(file.toPath());
                      String fingerprint = calculateSha256(fileBytes);

                      String type = "Client Keystore";
                      String internalFilename = file.getName(); // Default

                      if (output.contains("BasicConstraints:[CA:true")) {
                        type = "Certificate Authority";
                        internalFilename = "certificate-authority.p12";
                        generatedFiles.put(internalFilename, fileBytes);
                        SwingUtilities.invokeLater(
                            () -> {
                              caCheckLabel.setVisible(true);
                              tfCaCN.setText(getCnFromDn(ownerDn));
                            });
                      } else if (output.contains("SubjectAlternativeName")) {
                        type = "Server Keystore";
                        internalFilename = "server.p12";
                        generatedFiles.put(internalFilename, fileBytes);
                        SwingUtilities.invokeLater(
                            () -> {
                              serverCheckLabel.setVisible(true);
                              tfServerCN.setText(getCnFromDn(ownerDn));
                            });
                      } else {
                        internalFilename = "client.p12";
                        generatedFiles.put(internalFilename, fileBytes);
                        SwingUtilities.invokeLater(
                            () -> {
                              clientCheckLabel.setVisible(true);
                              tfClientCN.setText(getCnFromDn(ownerDn));
                              togglePublicKeyPanel(true);
                            });
                      }
                      addGeneratedFile(internalFilename, fileBytes, type, fingerprint);
                    } else {
                      log(
                          "ERROR: Failed to read keystore. It might be corrupted, password may be incorrect, or the storetype is not PKCS12/JKS.");
                    }
                  } catch (Exception e) {
                    log("ERROR processing keystore: " + e.getMessage());
                  }
                })
            .whenComplete((v, ex) -> hideProgress());
      }
    }
  }

  private void handleDroppedCsr(File file) {
    showProgress("Inspecting CSR: " + file.getName());
    CompletableFuture.runAsync(
            () -> {
              try {
                List<String> command =
                    Arrays.asList(
                        "keytool", "-printcertreq", "-v", "-file", file.getAbsolutePath());
                String output = executeCommandAndGetOutput(command);

                if (output != null && !output.toLowerCase().contains("error")) {
                  log("--- CSR Details for " + file.getName() + " ---");
                  log(output);
                  log("---------------------------------------");

                  String subjectDn = parseDnFromKeytoolOutput(output, "Subject:");
                  if (subjectDn != null) {
                    parseAndPopulateDname(subjectDn);
                  }
                } else {
                  log("ERROR: Could not read CSR file. It may be invalid or corrupted.");
                  if (output != null) log(output);
                }
              } catch (Exception e) {
                log("ERROR processing CSR: " + e.getMessage());
              }
            })
        .whenComplete((v, ex) -> hideProgress());
  }

  private void handleDroppedDer(File file) {
    showProgress("Inspecting certificate: " + file.getName());
    CompletableFuture.runAsync(
            () -> {
              try {
                List<String> command =
                    Arrays.asList("keytool", "-printcert", "-v", "-file", file.getAbsolutePath());
                String output = executeCommandAndGetOutput(command);

                if (output != null && !output.toLowerCase().contains("error")) {
                  log("--- Certificate Details for " + file.getName() + " ---");
                  log(output);
                  log("---------------------------------------");

                  String ownerDn = parseDnFromKeytoolOutput(output, "Owner:");
                  if (ownerDn != null) {
                    parseAndPopulateDname(ownerDn);
                  }

                  byte[] fileBytes = Files.readAllBytes(file.toPath());
                  String fingerprint = calculateSha256(fileBytes);
                  String type;

                  if (output.contains("BasicConstraints:[CA:true")) {
                    type = "CA Certificate (DER)";
                  } else if (output.contains("SubjectAlternativeName")) {
                    type = "Server Certificate (DER)";
                  } else {
                    type = "Certificate (DER)";
                  }

                  // Add the file to the table for inspection, but it won't be used for generation
                  // since its name won't match the expected keystore names.
                  addGeneratedFile(file.getName(), fileBytes, type, fingerprint);

                } else {
                  log("ERROR: Could not read certificate file. It may be invalid or corrupted.");
                  if (output != null) {
                    log(output);
                  }
                }
              } catch (Exception e) {
                log("ERROR processing certificate file: " + e.getMessage());
              }
            })
        .whenComplete((v, ex) -> hideProgress());
  }

  private String parseDnFromKeytoolOutput(String output, String marker) {
    String[] lines = output.split("\\r?\\n");
    for (String line : lines) {
      if (line.trim().startsWith(marker)) {
        return line.substring(line.indexOf(":") + 1).trim();
      }
    }
    return null;
  }

  private String getCnFromDn(String dn) {
    if (dn == null) return "";
    String[] parts = dn.split(",");
    for (String part : parts) {
      String[] kv = part.trim().split("=");
      if (kv.length == 2 && kv[0].equalsIgnoreCase("CN")) {
        return kv[1];
      }
    }
    return "";
  }

  private void parseAndPopulateDname(String dname) {
    if (dname == null) return;
    SwingUtilities.invokeLater(
        () -> {
          while (ouFields.size() > 1) {
            removeOuField(
                (JPanel) ouPanelContainer.getComponent(ouFields.size() - 1), ouFields.getLast());
          }
          if (!ouFields.isEmpty()) ouFields.getFirst().setText("");

          List<String> ous = new ArrayList<>();
          String[] parts = dname.split(",");
          for (String part : parts) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length == 2) {
              String key = kv[0];
              String value = kv[1];
              switch (key.toUpperCase()) {
                case "OU":
                  ous.add(value);
                  break;
                case "O":
                  tfOrg.setText(value);
                  break;
                case "L":
                  tfCity.setText(value);
                  break;
                case "ST":
                  tfState.setText(value);
                  break;
                case "C":
                  tfCountry.setText(value);
                  break;
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

  private String executeCommandAndGetOutput(List<String> command, String... passwordToMask) {
    try {
      String commandString = String.join(" ", command);
      if (passwordToMask.length > 0 && passwordToMask[0] != null) {
        commandString = commandString.replace(passwordToMask[0], "********");
      }
      log("Executing: " + commandString);

      ProcessBuilder pb = new ProcessBuilder(command);
      pb.redirectErrorStream(true);
      Process process = pb.start();

      StringBuilder output = new StringBuilder();
      try (BufferedReader reader =
          new BufferedReader(new InputStreamReader(process.getInputStream()))) {
        String line;
        while ((line = reader.readLine()) != null) {
          output.append(line).append("\n");
        }
      }

      process.waitFor();
      return output.toString();
    } catch (Exception e) {
      log("ERROR: Failed to execute command. Make sure 'keytool' is in your system's PATH.");
      log(e.getMessage());
      return null;
    }
  }

  private void log(String message) {
    SwingUtilities.invokeLater(
        () -> {
          logArea.append(message + "\n");
          logArea.setCaretPosition(logArea.getDocument().getLength());
        });
  }

  private String calculateSha256(byte[] fileBytes) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(fileBytes);
      StringBuilder hexString = new StringBuilder(2 * hash.length);
      for (byte b : hash) {
        String hex = Integer.toHexString(0xff & b);
        if (hex.length() == 1) {
          hexString.append('0');
        }
        hexString.append(hex);
      }
      return hexString.toString();
    } catch (NoSuchAlgorithmException e) {
      log("ERROR: SHA-256 algorithm not found.");
      return "N/A";
    }
  }

  private Icon createCheckIcon() {
    return new Icon() {
      @Override
      public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setStroke(new BasicStroke(2.5f));
        g2.setColor(new Color(0, 153, 0)); // Dark green
        g2.drawLine(x + 3, y + 8, x + 8, y + 13);
        g2.drawLine(x + 8, y + 13, x + 15, y + 4);
        g2.dispose();
      }

      @Override
      public int getIconWidth() {
        return 20;
      }

      @Override
      public int getIconHeight() {
        return 20;
      }
    };
  }

  public static void main(String[] args) {
    FlatDarkLaf.setup();
    SwingUtilities.invokeLater(PKICertGen::new);
  }
}
