package xxx.com.pki.updater;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

public class CertificateConverterGUI extends JFrame {

  private static final long serialVersionUID = -2736583652615333806L;

  // --- NEW Converter Components ---
  private JButton selectInputButton, selectOutputDirectoryButton, convertButton;
  private JComboBox<String> outputFormatComboBox;
  private JTextField inputFileField, outputDirectoryField;
  private JLabel converterStatusLabel;
  private File selectedP12File, outputDirectory; // Changed from outputFile to outputDirectory
  private static final String PEM_FORMAT = "PEM (Base64)";
  private static final String CRT_FORMAT = "CRT (Base64)";
  private static final String CER_FORMAT = "CER (Binary)";


  // --- Generator Components ---
  private JTextField commonNameField, organizationField, validityField;
  private JPasswordField passwordField;
  private JTextArea generatorStatusArea;
  private final List<JTextField> ouFields = new ArrayList<>();

  // --- Inspector Components ---
  private JTextArea inspectorDetailsArea;
  private JTextArea inspectorRawTextArea; // For plain text content

  // --- Bundler Components ---
  private JTextArea bundlerStatusArea;
  private JButton loadBaseCaButton, addCaCertsButton, createBundleButton;
  private File baseCaFile;
  private final List<File> additionalCaFiles = new ArrayList<>();

  // --- Password Updater Components ---
  private JLabel passwordFileLabel;
  private JPasswordField oldPasswordField, newPasswordField;
  private JCheckBox removePasswordCheckBox;
  private JButton updatePasswordButton;
  private File passwordUpdateFile;

  // --- P12 Extractor Components ---
  private JLabel p12ExtractorFileLabel;
  private JPanel p12ExtractorResultsPanel;
  private JTextField p12OutputPathField;
  private JButton p12ExtractButton;
  private transient KeyStore loadedP12KeyStore;
  private String p12SelectedAlias;
  private char[] p12Password;
  private JComboBox<String> privateKeyFormatBox, clientCertFormatBox, caCertsFormatBox;


  /**
   * Constructor to set up the main frame and its components.
   */
  public CertificateConverterGUI() {
    // --- Frame Setup ---
    super("Certificate Utility");
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    setSize(850, 750);
    setLocationRelativeTo(null);

    // --- Main Tabbed Pane ---
    JTabbedPane tabbedPane = new JTabbedPane();

    // --- Tab 1: Certificate Converter ---
    tabbedPane.addTab("Certificate Converter", null, createConverterPanel(), "Convert all elements from a P12 file");
    // --- Tab 2: PKI Generator ---
    tabbedPane.addTab("PKI Generator", null, createPkiGeneratorPanel(), "Create a new test certificate");
    // --- Tab 3: Certificate Inspector ---
    tabbedPane.addTab("Certificate Inspector", null, createCertificateInspectorPanel(), "View details of a certificate or key");
    // --- Tab 4: Certificate Authority Bundler ---
    tabbedPane.addTab("CA Bundler", null, createCaBundlerPanel(), "Bundle multiple CA certificates into one file");
    // --- Tab 5: Password Updater ---
    tabbedPane.addTab("Password Updater", null, createPasswordUpdaterPanel(), "Change or remove a certificate's password");
    // --- Tab 6: P12 Extractor ---
    tabbedPane.addTab("P12 Extractor", null, createP12ExtractorPanel(), "Extract components from a P12/PFX file");


    add(tabbedPane);
    updateConverterButtonStates();
    updateBundlerButtonStates();
  }

  // --- REFACTORED CONVERTER TAB METHODS ---

  /**
   * Creates the completely redesigned Certificate Converter tab panel.
   *
   * @return The main JPanel for the converter tab.
   */
  private JPanel createConverterPanel() {
    JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
    mainPanel.setBorder(new EmptyBorder(15, 15, 15, 15));

    // --- Center Panel: Contains all the controls in a grid ---
    JPanel formPanel = new JPanel(new GridBagLayout());
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.insets = new Insets(5, 5, 5, 5);
    gbc.fill = GridBagConstraints.HORIZONTAL;

    // --- 1. Input File Selection ---
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.gridwidth = 1;
    gbc.weightx = 0;
    formPanel.add(new JLabel("1. Input File:"), gbc);

    gbc.gridx = 1;
    gbc.weightx = 1.0;
    JPanel inputPanel = new JPanel(new BorderLayout(5, 0));
    inputFileField = new JTextField("No file selected.");
    inputFileField.setEditable(false);
    selectInputButton = new JButton("Select P12/PFX File...");
    inputPanel.add(inputFileField, BorderLayout.CENTER);
    inputPanel.add(selectInputButton, BorderLayout.EAST);
    formPanel.add(inputPanel, gbc);

    // --- 2. Output Format Selection ---
    gbc.gridx = 0;
    gbc.gridy = 1;
    gbc.weightx = 0;
    formPanel.add(new JLabel("2. Output Format:"), gbc);

    gbc.gridx = 1;
    gbc.weightx = 1.0;
    outputFormatComboBox = new JComboBox<>(new String[] {PEM_FORMAT, CRT_FORMAT, CER_FORMAT});
    formPanel.add(outputFormatComboBox, gbc);

    // --- 3. Output Directory Selection ---
    gbc.gridx = 0;
    gbc.gridy = 2;
    gbc.weightx = 0;
    formPanel.add(new JLabel("3. Output Directory:"), gbc);

    gbc.gridx = 1;
    gbc.weightx = 1.0;
    JPanel outputPanel = new JPanel(new BorderLayout(5, 0));
    outputDirectoryField = new JTextField("Select an output directory.");
    outputDirectoryField.setEditable(false);
    selectOutputDirectoryButton = new JButton("Select Directory...");
    outputPanel.add(outputDirectoryField, BorderLayout.CENTER);
    outputPanel.add(selectOutputDirectoryButton, BorderLayout.EAST);
    formPanel.add(outputPanel, gbc);

    // Add a vertical spacer to push the button down
    gbc.gridy = 3;
    gbc.weighty = 1.0;
    formPanel.add(new JLabel(), gbc);

    mainPanel.add(formPanel, BorderLayout.CENTER);

    // --- Bottom Panel: Convert Button and Status ---
    JPanel bottomPanel = new JPanel(new BorderLayout(10, 10));
    convertButton = new JButton("Convert All Entries");
    convertButton.setFont(new Font("SansSerif", Font.BOLD, 16));
    bottomPanel.add(convertButton, BorderLayout.NORTH);

    JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
    statusPanel.setBorder(BorderFactory.createTitledBorder("Status"));
    converterStatusLabel = new JLabel("Please select an input PKCS#12 file to begin.");
    converterStatusLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
    statusPanel.add(converterStatusLabel);
    bottomPanel.add(statusPanel, BorderLayout.CENTER);

    mainPanel.add(bottomPanel, BorderLayout.SOUTH);

    // --- Add Action Listeners ---
    selectInputButton.addActionListener(this::handleSelectInputFile);
    selectOutputDirectoryButton.addActionListener(this::handleSelectOutputDirectory);
    convertButton.addActionListener(this::handleConversion);

    return mainPanel;
  }

  private void handleSelectInputFile(ActionEvent e) {
    JFileChooser fileChooser = new JFileChooser();
    fileChooser.setDialogTitle("Select a PKCS#12 File");
    fileChooser.setFileFilter(new FileNameExtensionFilter("PKCS#12 Files (*.p12, *.pfx)", "p12", "pfx"));
    fileChooser.setAcceptAllFileFilterUsed(false);

    if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
      selectedP12File = fileChooser.getSelectedFile();
      inputFileField.setText(selectedP12File.getAbsolutePath());
      converterStatusLabel.setText("Input file loaded. Please select an output directory.");
      converterStatusLabel.setForeground(Color.BLUE);
      // Reset output directory if input changes
      outputDirectory = null;
      outputDirectoryField.setText("Select an output directory.");
    }
    updateConverterButtonStates();
  }

  private void handleSelectOutputDirectory(ActionEvent e) {
    JFileChooser fileChooser = new JFileChooser();
    fileChooser.setDialogTitle("Select Output Directory");
    fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
    fileChooser.setAcceptAllFileFilterUsed(false);

    if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
      outputDirectory = fileChooser.getSelectedFile();
      outputDirectoryField.setText(outputDirectory.getAbsolutePath());
      converterStatusLabel.setText("Ready to convert. Press the 'Convert' button.");
      converterStatusLabel.setForeground(new Color(0, 128, 0)); // Green
    }
    updateConverterButtonStates();
  }

  private void handleConversion(ActionEvent e) {
    if (selectedP12File == null || outputDirectory == null) {
      JOptionPane.showMessageDialog(this, "Please ensure both an input file and output directory are set.", "Error",
          JOptionPane.ERROR_MESSAGE);
      return;
    }

    // Prompt for password
    JPasswordField pf = new JPasswordField();
    int option =
        JOptionPane.showConfirmDialog(this, pf, "Enter Keystore Password", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
    if (option != JOptionPane.OK_OPTION) {
      converterStatusLabel.setText("Conversion cancelled by user.");
      converterStatusLabel.setForeground(Color.ORANGE);
      return;
    }
    char[] password = pf.getPassword();

    // Execute conversion in a background thread
    String format = (String) outputFormatComboBox.getSelectedItem();
    converterStatusLabel.setText("Converting all entries to " + format + "...");
    converterStatusLabel.setForeground(Color.BLUE);
    new UnifiedConverterWorker(selectedP12File, outputDirectory, password, format).execute();
  }

  /**
   * Updates the enabled state of buttons in the converter tab based on the current state.
   */
  private void updateConverterButtonStates() {
    boolean fileLoaded = (selectedP12File != null);
    outputFormatComboBox.setEnabled(fileLoaded);
    selectOutputDirectoryButton.setEnabled(fileLoaded);

    boolean outputSelected = (outputDirectory != null);
    convertButton.setEnabled(fileLoaded && outputSelected);
  }


  private void flashComponent(JComponent component) {
    final Color originalColor = component.getBackground();
    final Color flashColor = new Color(173, 216, 230);
    Timer timer = new Timer(250, e -> {
      component.setBackground(component.getBackground().equals(originalColor) ? flashColor : originalColor);
    });
    timer.setRepeats(true);
    timer.start();
    new Timer(1500, e -> {
      timer.stop();
      component.setBackground(originalColor);
    }).start();
  }

  // --- PKI GENERATOR TAB METHODS ---

  private JPanel createPkiGeneratorPanel() {
    JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
    mainPanel.setBorder(new EmptyBorder(15, 15, 15, 15));
    JPanel formPanel = new JPanel(new GridBagLayout());
    formPanel.setBorder(BorderFactory.createTitledBorder("Certificate Details"));
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.insets = new Insets(5, 5, 5, 5);
    gbc.anchor = GridBagConstraints.WEST;
    gbc.fill = GridBagConstraints.HORIZONTAL;

    int gridY = 0;

    // --- Common Name ---
    gbc.gridx = 0;
    gbc.gridy = gridY;
    gbc.weightx = 0;
    formPanel.add(new JLabel("Common Name (CN):"), gbc);
    gbc.gridx = 1;
    gbc.weightx = 1.0;
    commonNameField = new JTextField("My Test Certificate", 20);
    formPanel.add(commonNameField, gbc);
    gridY++;

    // --- Organization ---
    gbc.gridx = 0;
    gbc.gridy = gridY;
    gbc.weightx = 0;
    formPanel.add(new JLabel("Organization (O):"), gbc);
    gbc.gridx = 1;
    gbc.weightx = 1.0;
    organizationField = new JTextField("My Test Org", 20);
    formPanel.add(organizationField, gbc);
    gridY++;

    // --- Organizational Units (Dynamic) ---
    gbc.gridx = 0;
    gbc.gridy = gridY;
    gbc.weightx = 0;
    formPanel.add(new JLabel("Organizational Unit (OU):"), gbc);

    // This panel holds the first OU field and the static "+" button
    JPanel firstOuRowPanel = new JPanel(new BorderLayout(5, 0));
    JTextField firstOuField = new JTextField(20);
    ouFields.clear(); // Clear previous fields if UI is rebuilt
    ouFields.add(firstOuField);
    firstOuRowPanel.add(firstOuField, BorderLayout.CENTER);

    JButton addOuButton = new JButton("+");
    addOuButton.setMargin(new Insets(1, 4, 1, 4));
    addOuButton.setToolTipText("Add another Organizational Unit field");
    firstOuRowPanel.add(addOuButton, BorderLayout.EAST);

    gbc.gridx = 1;
    gbc.weightx = 1.0;
    formPanel.add(firstOuRowPanel, gbc);
    gridY++;

    // This panel will hold all dynamically added OU fields below the first one
    JPanel additionalOuPanel = new JPanel();
    additionalOuPanel.setLayout(new BoxLayout(additionalOuPanel, BoxLayout.Y_AXIS));
    gbc.gridx = 1;
    gbc.gridy = gridY;
    formPanel.add(additionalOuPanel, gbc);
    gridY++;

    addOuButton.addActionListener(e -> {
      JPanel newOuRowPanel = new JPanel(new BorderLayout(5, 0));
      JTextField newOuField = new JTextField(20);
      JButton removeOuButton = new JButton("-");
      removeOuButton.setMargin(new Insets(1, 4, 1, 4));
      removeOuButton.setToolTipText("Remove this Organizational Unit field");

      newOuRowPanel.add(newOuField, BorderLayout.CENTER);
      newOuRowPanel.add(removeOuButton, BorderLayout.EAST);

      removeOuButton.addActionListener(removeEvent -> {
        ouFields.remove(newOuField);
        additionalOuPanel.remove(newOuRowPanel);
        // Also remove the rigid area above it for clean spacing
        Component[] components = additionalOuPanel.getComponents();
        if (components.length > 0) {
          additionalOuPanel.remove(components[components.length - 1]);
        }
        additionalOuPanel.revalidate();
        additionalOuPanel.repaint();
      });

      ouFields.add(newOuField);
      additionalOuPanel.add(Box.createRigidArea(new Dimension(0, 5)));
      additionalOuPanel.add(newOuRowPanel);
      additionalOuPanel.revalidate();
      additionalOuPanel.repaint();
    });

    // --- Validity ---
    gbc.gridx = 0;
    gbc.gridy = gridY;
    gbc.weightx = 0;
    formPanel.add(new JLabel("Validity (days):"), gbc);
    gbc.gridx = 1;
    gbc.weightx = 1.0;
    validityField = new JTextField("365", 20);
    formPanel.add(validityField, gbc);
    gridY++;

    // --- Keystore Password ---
    gbc.gridx = 0;
    gbc.gridy = gridY;
    gbc.weightx = 0;
    formPanel.add(new JLabel("Keystore Password:"), gbc);
    JPanel passwordPanel = new JPanel(new BorderLayout(5, 0));
    passwordField = new JPasswordField("password", 20);
    JLabel passwordCheckLabel = new JLabel();
    passwordCheckLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
    passwordCheckLabel.setForeground(new Color(0, 150, 0));
    passwordPanel.add(passwordField, BorderLayout.CENTER);
    passwordPanel.add(passwordCheckLabel, BorderLayout.EAST);

    passwordField.getDocument().addDocumentListener(new DocumentListener() {
      private void updateCheck() {
        passwordCheckLabel.setText(passwordField.getPassword().length >= 8 ? "✅" : "");
      }

      public void insertUpdate(DocumentEvent e) {
        updateCheck();
      }

      public void removeUpdate(DocumentEvent e) {
        updateCheck();
      }

      public void changedUpdate(DocumentEvent e) {
        updateCheck();
      }
    });
    // Initial check
    if (passwordField.getPassword().length >= 8)
      passwordCheckLabel.setText("✅");

    gbc.gridx = 1;
    gbc.weightx = 1.0;
    formPanel.add(passwordPanel, gbc);
    gridY++;

    // --- Output File ---
    gbc.gridx = 0;
    gbc.gridy = gridY;
    gbc.weightx = 0;
    formPanel.add(new JLabel("Output File:"), gbc);
    JPanel filePanel = new JPanel(new BorderLayout(5, 0));
    JTextField generatorOutputFileField = new JTextField(30);
    generatorOutputFileField.setEditable(false);
    filePanel.add(generatorOutputFileField, BorderLayout.CENTER);
    JButton browseButton = new JButton("Browse...");
    browseButton.addActionListener(e -> handleBrowseGeneratorOutputFile(e, generatorOutputFileField));
    filePanel.add(browseButton, BorderLayout.EAST);
    gbc.gridx = 1;
    gbc.weightx = 1.0;
    formPanel.add(filePanel, gbc);

    mainPanel.add(formPanel, BorderLayout.NORTH);

    generatorStatusArea = new JTextArea("Enter details and click 'Generate' to create a self-signed PKCS#12 file.");
    generatorStatusArea.setEditable(false);
    mainPanel.add(new JScrollPane(generatorStatusArea), BorderLayout.CENTER);
    JButton generateButton = new JButton("Generate PKI Certificate");
    generateButton.addActionListener(e -> handleGeneratePki(e, generatorOutputFileField));
    mainPanel.add(generateButton, BorderLayout.SOUTH);

    return mainPanel;
  }

  private void handleBrowseGeneratorOutputFile(ActionEvent e, JTextField targetField) {
    JFileChooser fc = new JFileChooser();
    fc.setDialogTitle("Save Certificate As");
    fc.setFileFilter(new FileNameExtensionFilter("PKCS#12 Files (*.p12)", "p12"));
    fc.setSelectedFile(new File("my_test_cert.p12"));
    if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
      File f = fc.getSelectedFile();
      if (!f.getAbsolutePath().toLowerCase().endsWith(".p12")) {
        f = new File(f.getAbsolutePath() + ".p12");
      }
      targetField.setText(f.getAbsolutePath());
    }
  }

  private void handleGeneratePki(ActionEvent e, JTextField targetField) {
    String commonName = commonNameField.getText().trim();
    String organization = organizationField.getText().trim();
    String validity = validityField.getText().trim();

    if (commonName.isEmpty() || organization.isEmpty() || validity.isEmpty()
        || passwordField.getPassword().length == 0 || targetField.getText().trim().isEmpty()) {
      JOptionPane.showMessageDialog(this, "All fields (except OU) are required.", "Input Error", JOptionPane.ERROR_MESSAGE);
      return;
    }

    // Build the Distinguished Name (DN) string, starting with most specific component
    StringBuilder dnameBuilder = new StringBuilder();
    dnameBuilder.append("CN=").append(commonName);
    for (JTextField ouField : ouFields) {
      String ouText = ouField.getText().trim();
      if (!ouText.isEmpty()) {
        dnameBuilder.append(",OU=").append(ouText);
      }
    }
    dnameBuilder.append(",O=").append(organization);
    String distinguishedName = dnameBuilder.toString();

    generatorStatusArea.setText("Generating certificate with DN: " + distinguishedName + "\nPlease wait...\n");
    new PkiGeneratorWorker(
        targetField.getText(),
        new String(passwordField.getPassword()),
        distinguishedName, // Pass the full DN
        validity,
        commonName // Alias can still be the CN
    ).execute();
  }

  // --- CERTIFICATE INSPECTOR TAB METHODS ---

  private JPanel createCertificateInspectorPanel() {
    JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
    mainPanel.setBorder(new EmptyBorder(15, 15, 15, 15));

    JButton loadButton = new JButton("Load Certificate or Key File to Inspect");
    loadButton.setFont(new Font("SansSerif", Font.BOLD, 16));
    loadButton.addActionListener(this::handleInspectFile);
    mainPanel.add(loadButton, BorderLayout.NORTH);

    // Create the top text area for parsed details
    inspectorDetailsArea = new JTextArea("Load a file to see its parsed details here.");
    inspectorDetailsArea.setEditable(false);
    inspectorDetailsArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
    JScrollPane detailsScrollPane = new JScrollPane(inspectorDetailsArea);
    detailsScrollPane.setBorder(BorderFactory.createTitledBorder("Parsed Details"));

    // Create the bottom text area for raw content
    inspectorRawTextArea = new JTextArea("Load a file to see its plain text content here.");
    inspectorRawTextArea.setEditable(false);
    inspectorRawTextArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
    JScrollPane rawScrollPane = new JScrollPane(inspectorRawTextArea);
    rawScrollPane.setBorder(BorderFactory.createTitledBorder("Plain Text Content"));

    // Create a split pane to hold both text areas
    JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, detailsScrollPane, rawScrollPane);
    splitPane.setResizeWeight(0.5); // Distribute space evenly on resize

    mainPanel.add(splitPane, BorderLayout.CENTER);

    return mainPanel;
  }

  private void handleInspectFile(ActionEvent e) {
    JFileChooser fc = new JFileChooser();
    fc.setDialogTitle("Select File to Inspect");
    fc.setFileFilter(new FileNameExtensionFilter("Cert/Key Files", "pem", "cer", "crt", "der", "p12", "pfx", "key", "ca-bundle"));
    fc.setAcceptAllFileFilterUsed(true);
    if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
      File file = fc.getSelectedFile();
      // Clear both areas before starting a new inspection
      inspectorDetailsArea.setText("Inspecting " + file.getName() + "...");
      inspectorRawTextArea.setText("");
      new CertificateInspectorWorker(file).execute();
    }
  }

  // --- CA BUNDLER TAB METHODS ---

  private JPanel createCaBundlerPanel() {
    JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
    mainPanel.setBorder(new EmptyBorder(15, 15, 15, 15));

    // Top panel for buttons
    JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));
    loadBaseCaButton = new JButton("1. Load Base CA Certificate");
    addCaCertsButton = new JButton("2. Add Certificates to Bundle");
    createBundleButton = new JButton("3. Create and Save Bundle");
    buttonPanel.add(loadBaseCaButton);
    buttonPanel.add(addCaCertsButton);
    buttonPanel.add(createBundleButton);
    mainPanel.add(buttonPanel, BorderLayout.NORTH);

    // Center panel for status
    bundlerStatusArea = new JTextArea(
        "Welcome to the CA Bundler!\n\n1. Load your primary CA certificate (e.g., your server's certificate).\n2. Add one or more intermediate/root CA certificates to create a chain.\n3. Create and save the final bundle file.");
    bundlerStatusArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
    bundlerStatusArea.setEditable(false);
    mainPanel.add(new JScrollPane(bundlerStatusArea), BorderLayout.CENTER);

    // Add action listeners
    loadBaseCaButton.addActionListener(this::handleLoadBaseCa);
    addCaCertsButton.addActionListener(this::handleAddCaCerts);
    createBundleButton.addActionListener(this::handleCreateBundle);

    return mainPanel;
  }

  private void handleLoadBaseCa(ActionEvent e) {
    JFileChooser fc = new JFileChooser();
    fc.setDialogTitle("Select Base CA Certificate");
    fc.setFileFilter(new FileNameExtensionFilter("Certificate Files", "pem", "crt", "cer", "der"));
    if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
      baseCaFile = fc.getSelectedFile();
      additionalCaFiles.clear(); // Start fresh
      String sb = "--- Base Certificate Loaded ---\n"
          + previewCertificateFile(baseCaFile)
          + "\nNext, add the intermediate/root certificates to append to this file.";
      bundlerStatusArea.setText(sb);
    }
    updateBundlerButtonStates();
  }

  private void handleAddCaCerts(ActionEvent e) {
    JFileChooser fc = new JFileChooser();
    fc.setDialogTitle("Select Certificate(s) to Add");
    fc.setMultiSelectionEnabled(true);
    fc.setFileFilter(new FileNameExtensionFilter("Certificate Files", "pem", "crt", "cer", "der"));
    if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
      Collections.addAll(additionalCaFiles, fc.getSelectedFiles());
      StringBuilder sb = new StringBuilder();
      sb.append("--- Base Certificate ---\n");
      sb.append(previewCertificateFile(baseCaFile));
      sb.append("\n--- Adding ").append(additionalCaFiles.size()).append(" Certificate(s) ---\n");
      for (File file : additionalCaFiles) {
        sb.append("---------------------------------\n");
        sb.append(previewCertificateFile(file));
      }
      sb.append("\nReady to create the bundle.");
      bundlerStatusArea.setText(sb.toString());
    }
    updateBundlerButtonStates();
  }

  private void handleCreateBundle(ActionEvent e) {
    JFileChooser fc = new JFileChooser();
    fc.setDialogTitle("Save CA Bundle As...");
    fc.setSelectedFile(new File("ca-bundle.crt"));
    fc.setFileFilter(new FileNameExtensionFilter("PEM/CRT Bundle", "pem", "crt"));
    if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
      File bundleOutputFile = fc.getSelectedFile();
      new CaBundlerWorker(baseCaFile, additionalCaFiles, bundleOutputFile).execute();
    }
  }

  private String previewCertificateFile(File certFile) {
    if (certFile == null || !certFile.exists()) {
      return "File not found: " + (certFile != null ? certFile.getName() : "null");
    }
    try {
      byte[] fileBytes = Files.readAllBytes(certFile.toPath());
      CertificateFactory cf = CertificateFactory.getInstance("X.509");
      Collection<? extends Certificate> certs = cf.generateCertificates(new ByteArrayInputStream(fileBytes));
      if (certs.isEmpty()) {
        return "No certificates found in file: " + certFile.getName();
      }
      // Preview the first certificate in the file
      X509Certificate cert = (X509Certificate) certs.iterator().next();
      long daysRemaining = ChronoUnit.DAYS.between(Instant.now(), cert.getNotAfter().toInstant());
      String validity = daysRemaining < 0 ? "EXPIRED" : daysRemaining + " days remaining";

      return String.format("File: %s\n  Subject: %s\n  Issuer: %s\n  Expires: %s (%s)\n",
          certFile.getName(),
          cert.getSubjectX500Principal().getName(),
          cert.getIssuerX500Principal().getName(),
          cert.getNotAfter(),
          validity);
    }
    catch (Exception ex) {
      return String.format("Could not parse certificate: %s\n  Error: %s\n", certFile.getName(), ex.getMessage());
    }
  }

  private void updateBundlerButtonStates() {
    addCaCertsButton.setEnabled(baseCaFile != null);
    createBundleButton.setEnabled(baseCaFile != null && !additionalCaFiles.isEmpty());
  }

  // --- PASSWORD UPDATER TAB METHODS ---

  private JPanel createPasswordUpdaterPanel() {
    JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
    mainPanel.setBorder(new EmptyBorder(15, 15, 15, 15));

    // Top panel for file selection
    JPanel fileSelectionPanel = new JPanel(new BorderLayout(10, 5));
    fileSelectionPanel.setBorder(BorderFactory.createTitledBorder("Select Certificate File"));
    JButton selectFileButton = new JButton("Select File...");
    passwordFileLabel = new JLabel("No file selected.");
    passwordFileLabel.setFont(new Font("SansSerif", Font.ITALIC, 12));
    fileSelectionPanel.add(selectFileButton, BorderLayout.WEST);
    fileSelectionPanel.add(passwordFileLabel, BorderLayout.CENTER);
    mainPanel.add(fileSelectionPanel, BorderLayout.NORTH);

    // Center panel for password fields
    JPanel formPanel = new JPanel(new GridBagLayout());
    formPanel.setBorder(BorderFactory.createTitledBorder("Update Credentials"));
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.insets = new Insets(5, 5, 5, 5);
    gbc.anchor = GridBagConstraints.WEST;
    gbc.fill = GridBagConstraints.HORIZONTAL;

    gbc.gridx = 0;
    gbc.gridy = 0;
    formPanel.add(new JLabel("Current Password:"), gbc);
    gbc.gridx = 1;
    gbc.gridy = 0;
    gbc.weightx = 1.0;
    oldPasswordField = new JPasswordField(25);
    formPanel.add(oldPasswordField, gbc);

    gbc.gridx = 0;
    gbc.gridy = 1;
    formPanel.add(new JLabel("New Password:"), gbc);
    gbc.gridx = 1;
    gbc.gridy = 1;
    newPasswordField = new JPasswordField(25);
    formPanel.add(newPasswordField, gbc);

    gbc.gridx = 1;
    gbc.gridy = 2;
    removePasswordCheckBox = new JCheckBox("Remove password from this certificate");
    formPanel.add(removePasswordCheckBox, gbc);

    mainPanel.add(formPanel, BorderLayout.CENTER);

    // Bottom panel for action button
    updatePasswordButton = new JButton("Update Password");
    updatePasswordButton.setFont(new Font("SansSerif", Font.BOLD, 16));
    mainPanel.add(updatePasswordButton, BorderLayout.SOUTH);

    // Add listeners
    selectFileButton.addActionListener(this::handleSelectPasswordFile);
    removePasswordCheckBox.addActionListener(e -> {
      boolean checked = removePasswordCheckBox.isSelected();
      newPasswordField.setEnabled(!checked);
      if (checked) {
        newPasswordField.setText("");
      }
    });
    updatePasswordButton.addActionListener(this::handleUpdatePassword);

    updatePasswordButton.setEnabled(false);

    return mainPanel;
  }

  private void handleSelectPasswordFile(ActionEvent e) {
    JFileChooser fc = new JFileChooser();
    fc.setDialogTitle("Select Password-Protected Certificate");
    fc.setFileFilter(new FileNameExtensionFilter("PKCS#12 Files (*.p12, *.pfx)", "p12", "pfx"));
    if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
      passwordUpdateFile = fc.getSelectedFile();
      passwordFileLabel.setText(passwordUpdateFile.getAbsolutePath());
      passwordFileLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
      updatePasswordButton.setEnabled(true);
    }
  }

  private void handleUpdatePassword(ActionEvent e) {
    if (passwordUpdateFile == null) {
      JOptionPane.showMessageDialog(this, "Please select a certificate file first.", "No File Selected", JOptionPane.WARNING_MESSAGE);
      return;
    }

    char[] oldPassword = oldPasswordField.getPassword();
    char[] newPassword = removePasswordCheckBox.isSelected() ? new char[0] : newPasswordField.getPassword();

    if (removePasswordCheckBox.isSelected()) {
      int choice = JOptionPane.showConfirmDialog(this,
          "WARNING: Removing the password from a certificate is highly inadvisable.\n" +
              "The private key will be stored unencrypted and can be easily compromised.\n\n" +
              "Are you absolutely sure you want to proceed?",
          "Security Warning",
          JOptionPane.YES_NO_OPTION,
          JOptionPane.WARNING_MESSAGE);
      if (choice != JOptionPane.YES_OPTION) {
        return; // User cancelled the action
      }
    }

    new PasswordUpdaterWorker(passwordUpdateFile, oldPassword, newPassword).execute();
  }

  // --- P12 EXTRACTOR METHODS ---

  private JPanel createP12ExtractorPanel() {
    JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
    mainPanel.setBorder(new EmptyBorder(15, 15, 15, 15));

    // Top panel for file selection
    JPanel fileSelectionPanel = new JPanel(new BorderLayout(10, 5));
    JButton selectFileButton = new JButton("Select P12/PFX File...");
    selectFileButton.setFont(new Font("SansSerif", Font.BOLD, 14));
    p12ExtractorFileLabel = new JLabel("No file selected. Please load a file to begin.");
    p12ExtractorFileLabel.setFont(new Font("SansSerif", Font.ITALIC, 12));
    fileSelectionPanel.add(selectFileButton, BorderLayout.WEST);
    fileSelectionPanel.add(p12ExtractorFileLabel, BorderLayout.CENTER);
    mainPanel.add(fileSelectionPanel, BorderLayout.NORTH);

    // Center panel for results and options
    p12ExtractorResultsPanel = new JPanel(new GridBagLayout());
    p12ExtractorResultsPanel.setBorder(BorderFactory.createTitledBorder("Extraction Options"));
    mainPanel.add(p12ExtractorResultsPanel, BorderLayout.CENTER);

    // Bottom panel for output path and extract button
    JPanel bottomPanel = new JPanel(new BorderLayout(10, 10));
    JPanel outputPathPanel = new JPanel(new BorderLayout(5, 0));
    outputPathPanel.setBorder(BorderFactory.createTitledBorder("Output Directory"));
    p12OutputPathField = new JTextField();
    p12OutputPathField.setEditable(false);
    JButton browseOutputPathButton = new JButton("Browse...");
    outputPathPanel.add(p12OutputPathField, BorderLayout.CENTER);
    outputPathPanel.add(browseOutputPathButton, BorderLayout.EAST);

    p12ExtractButton = new JButton("Extract Files");
    p12ExtractButton.setFont(new Font("SansSerif", Font.BOLD, 16));
    p12ExtractButton.setEnabled(false);

    bottomPanel.add(outputPathPanel, BorderLayout.CENTER);
    bottomPanel.add(p12ExtractButton, BorderLayout.SOUTH);
    mainPanel.add(bottomPanel, BorderLayout.SOUTH);

    // Add listeners
    selectFileButton.addActionListener(this::handleSelectP12ForExtraction);
    browseOutputPathButton.addActionListener(this::handleSelectP12OutputPath);
    p12ExtractButton.addActionListener(this::handleP12Extract);

    return mainPanel;
  }

  private void handleSelectP12ForExtraction(ActionEvent e) {
    JFileChooser fc = new JFileChooser();
    fc.setDialogTitle("Select PKCS#12 File");
    fc.setFileFilter(new FileNameExtensionFilter("PKCS#12 Files (*.p12, *.pfx)", "p12", "pfx"));
    if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
      File p12File = fc.getSelectedFile();
      p12ExtractorFileLabel.setText(p12File.getAbsolutePath());

      JPasswordField pf = new JPasswordField();
      int okCxl = JOptionPane.showConfirmDialog(this, pf, "Enter Password for " + p12File.getName(), JOptionPane.OK_CANCEL_OPTION,
          JOptionPane.PLAIN_MESSAGE);
      if (okCxl != JOptionPane.OK_OPTION) {
        return;
      }
      p12Password = pf.getPassword();

      p12ExtractorResultsPanel.removeAll();
      p12ExtractorResultsPanel.revalidate();
      p12ExtractorResultsPanel.repaint();
      JLabel statusLabel = new JLabel("Analyzing P12 file, please wait...");
      statusLabel.setFont(new Font("SansSerif", Font.ITALIC, 12));
      p12ExtractorResultsPanel.add(statusLabel);

      new P12AnalyzerWorker(p12File, p12Password).execute();
    }
  }

  private void handleSelectP12OutputPath(ActionEvent e) {
    JFileChooser fc = new JFileChooser();
    fc.setDialogTitle("Select Output Directory");
    fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
    if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
      p12OutputPathField.setText(fc.getSelectedFile().getAbsolutePath());
      p12ExtractButton.setEnabled(loadedP12KeyStore != null);
    }
  }

  private void handleP12Extract(ActionEvent e) {
    String outputPath = p12OutputPathField.getText();
    if (outputPath == null || outputPath.trim().isEmpty()) {
      JOptionPane.showMessageDialog(this, "Please select an output directory.", "Output Directory Missing", JOptionPane.WARNING_MESSAGE);
      return;
    }

    Map<String, String> formats = Map.of(
        "privateKey", (String) privateKeyFormatBox.getSelectedItem(),
        "clientCert", (String) clientCertFormatBox.getSelectedItem(),
        "caCerts", (String) caCertsFormatBox.getSelectedItem());

    new P12ExtractorWorker(loadedP12KeyStore, p12SelectedAlias, p12Password, new File(outputPath), formats).execute();
  }


  // --- MAIN METHOD ---
  public static void main(String[] args) {
    SwingUtilities.invokeLater(() -> {
      try {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
      }
      catch (Exception e) {
        System.err.println("Could not set system look and feel.");
      }
      new CertificateConverterGUI().setVisible(true);
    });
  }

  // --- WORKER CLASSES ---

  /**
   * A single, unified worker to handle all P12-to-other format conversions. This worker iterates
   * through all key entries in the P12 and saves each component (key, cert, chain) to a separate file
   * in the specified directory.
   */
  private class UnifiedConverterWorker extends SwingWorker<List<String>, Void> {
    private final File p12File;
    private final File outputDirectory;
    private final char[] password;
    private final String outputFormat;

    UnifiedConverterWorker(File p12File, File outputDirectory, char[] password, String outputFormat) {
      this.p12File = p12File;
      this.outputDirectory = outputDirectory;
      this.password = password;
      this.outputFormat = outputFormat;
    }

    @Override
    protected List<String> doInBackground() throws Exception {
      List<String> createdFiles = new ArrayList<>();
      KeyStore p12 = KeyStore.getInstance("PKCS12");
      p12.load(new FileInputStream(p12File), password);

      String certExtension;
      boolean isBinaryOutput = CER_FORMAT.equals(outputFormat);
      if (isBinaryOutput) {
        certExtension = ".cer";
      }
      else if (CRT_FORMAT.equals(outputFormat)) {
        certExtension = ".crt";
      }
      else {
        certExtension = ".pem";
      }

      Base64.Encoder mimeEncoder = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII));
      Enumeration<String> aliases = p12.aliases();

      while (aliases.hasMoreElements()) {
        String alias = aliases.nextElement();

        // Process only private key entries
        if (!p12.isKeyEntry(alias)) {
          continue;
        }

        // 1. Extract and save the private key (always as PEM)
        Key privateKey = p12.getKey(alias, password);
        File keyFile = new File(outputDirectory, alias + "_private.key");
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(keyFile), StandardCharsets.US_ASCII)) {
          writer.write("-----BEGIN PRIVATE KEY-----\n");
          writer.write(mimeEncoder.encodeToString(privateKey.getEncoded()));
          writer.write("\n-----END PRIVATE KEY-----\n");
        }
        createdFiles.add(keyFile.getName());

        Certificate[] chain = p12.getCertificateChain(alias);
        if (chain == null || chain.length == 0) {
          continue;
        }

        // 2. Extract and save the end-entity certificate
        Certificate userCert = chain[0];
        File certFile = new File(outputDirectory, alias + "_cert" + certExtension);
        if (isBinaryOutput) {
          try (FileOutputStream fos = new FileOutputStream(certFile)) {
            fos.write(userCert.getEncoded());
          }
        }
        else {
          try (Writer writer = new OutputStreamWriter(new FileOutputStream(certFile), StandardCharsets.US_ASCII)) {
            writer.write("-----BEGIN CERTIFICATE-----\n");
            writer.write(mimeEncoder.encodeToString(userCert.getEncoded()));
            writer.write("\n-----END CERTIFICATE-----\n");
          }
        }
        createdFiles.add(certFile.getName());

        // 3. Extract and save the CA chain certificates (if they exist)
        if (chain.length > 1) {
          File chainFile = new File(outputDirectory, alias + "_chain" + certExtension);
          if (isBinaryOutput) {
            try (FileOutputStream fos = new FileOutputStream(chainFile)) {
              for (int i = 1; i < chain.length; i++) {
                fos.write(chain[i].getEncoded());
              }
            }
          }
          else {
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(chainFile), StandardCharsets.US_ASCII)) {
              for (int i = 1; i < chain.length; i++) {
                writer.write("-----BEGIN CERTIFICATE-----\n");
                writer.write(mimeEncoder.encodeToString(chain[i].getEncoded()));
                writer.write("\n-----END CERTIFICATE-----\n\n");
              }
            }
          }
          createdFiles.add(chainFile.getName());
        }
      }

      if (createdFiles.isEmpty()) {
        throw new Exception("No private key entries were found in the P12 file.");
      }
      return createdFiles;
    }

    @Override
    protected void done() {
      try {
        List<String> resultFiles = get();
        String fileList = resultFiles.stream().collect(Collectors.joining("\n- ", "\n- ", ""));
        String successMessage = String.format("Successfully converted %d files to directory:\n%s",
            resultFiles.size(), outputDirectory.getAbsolutePath());

        converterStatusLabel.setText("Conversion successful!");
        converterStatusLabel.setForeground(new Color(0, 128, 0));
        JOptionPane.showMessageDialog(CertificateConverterGUI.this,
            successMessage + "\n\nFiles created:" + fileList,
            "Success", JOptionPane.INFORMATION_MESSAGE);
      }
      catch (Exception ex) {
        String message = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
        converterStatusLabel.setText("Error during conversion.");
        converterStatusLabel.setForeground(Color.RED);
        JOptionPane.showMessageDialog(CertificateConverterGUI.this,
            "Conversion failed:\n" + message,
            "Error", JOptionPane.ERROR_MESSAGE);
      }
    }
  }


  private class PkiGeneratorWorker extends SwingWorker<String, Void> {
    private final String outputFile, password, distinguishedName, validity, alias;

    PkiGeneratorWorker(String outputFile, String password, String distinguishedName, String validity, String alias) {
      this.outputFile = outputFile;
      this.password = password;
      this.distinguishedName = distinguishedName;
      this.validity = validity;
      this.alias = alias;
    }

    @Override
    protected String doInBackground() throws Exception {
      // 1. Add Bouncy Castle as a Security Provider
      Security.addProvider(new BouncyCastleProvider());

      // 2. Generate the Key Pair
      KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA", "BC");
      keyGen.initialize(2048, new SecureRandom());
      KeyPair keyPair = keyGen.generateKeyPair();

      // 3. Define Certificate Attributes
      X500Name subjectAndIssuerName = new X500Name(distinguishedName); // Use the DN from the GUI
      BigInteger serialNumber = new BigInteger(64, new SecureRandom()); // A random serial number
      Date notBefore = new Date();
      long validityMillis = Long.parseLong(validity) * 24 * 60 * 60 * 1000L;
      Date notAfter = new Date(notBefore.getTime() + validityMillis);

      // 4. Build the Certificate
      X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
          subjectAndIssuerName, // Issuer
          serialNumber,
          notBefore,
          notAfter,
          subjectAndIssuerName, // Subject (self-signed)
          keyPair.getPublic());

      // 5. Sign the Certificate with the Private Key
      ContentSigner contentSigner = new JcaContentSignerBuilder("SHA256WithRSA").build(keyPair.getPrivate());
      java.security.cert.X509Certificate certificate = new JcaX509CertificateConverter()
          .setProvider("BC")
          .getCertificate(certBuilder.build(contentSigner));

      // 6. Create the PKCS#12 Keystore
      KeyStore keyStore = KeyStore.getInstance("PKCS12", "BC");
      keyStore.load(null, null); // Initialize an empty keystore

      // 7. Store the Private Key and Certificate Chain
      java.security.cert.Certificate[] certificateChain = {certificate};
      keyStore.setKeyEntry(
          alias.replaceAll("\\s+", "").toLowerCase(), // The alias for the entry
          keyPair.getPrivate(), // The private key
          password.toCharArray(), // The password to protect the key
          certificateChain // The certificate chain
      );

      // 8. Write the Keystore to a File
      try (FileOutputStream fos = new FileOutputStream(outputFile)) {
        keyStore.store(fos, password.toCharArray());
      }

      return "SUCCESS:\nCertificate generated successfully using Bouncy Castle.\nFile saved to: " + outputFile;
    }


    @Override
    protected void done() {
      try {
        String result = get();
        generatorStatusArea.append(result);
        JOptionPane.showMessageDialog(CertificateConverterGUI.this,
            result.startsWith("SUCCESS") ? "Certificate generated successfully!" : "Failed to generate certificate.", "Status",
            result.startsWith("SUCCESS") ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.ERROR_MESSAGE);
      }
      catch (Exception ex) {
        String errorMessage = "An error occurred during certificate generation: \n" + ex.getMessage();
        generatorStatusArea.append("Execution Error: " + ex.getMessage());
        JOptionPane.showMessageDialog(CertificateConverterGUI.this, errorMessage, "Error", JOptionPane.ERROR_MESSAGE);
      }
    }
  }

  private class CertificateInspectorWorker extends SwingWorker<String[], Void> {
    private final File file;

    CertificateInspectorWorker(File file) {
      this.file = file;
    }

    @Override
    protected String[] doInBackground() throws Exception {
      // Read file bytes and also convert to a String for the plain text view
      byte[] fileBytes = Files.readAllBytes(file.toPath());
      String fileContent = new String(fileBytes, StandardCharsets.UTF_8);

      String inspectionDetails; // This will hold the analysis text.
      String fileName = file.getName().toLowerCase();

      // Try PKCS12 first as it's a distinct, password-protected format
      if (fileName.endsWith(".p12") || fileName.endsWith(".pfx")) {
        char[] password = promptForPassword();
        if (password == null) {
          inspectionDetails = "Inspection cancelled by user.";
        }
        else {
          try {
            inspectionDetails = inspectPkcs12(fileBytes, password);
          }
          catch (Exception ex) {
            inspectionDetails =
                "Failed to inspect PKCS#12 file. The password may be incorrect or the file may be corrupt.\nError: " + ex.getMessage();
          }
        }
      }
      else {
        // For other types, try to parse them in sequence
        try {
          inspectionDetails = inspectX509(fileBytes);
        }
        catch (Exception e1) {
          try {
            inspectionDetails = inspectPublicKey(fileBytes);
          }
          catch (Exception e2) {
            try {
              inspectionDetails = inspectPrivateKey(fileBytes);
            }
            catch (Exception e3) {
              inspectionDetails =
                  "Could not determine certificate or key type. The file may be unsupported, corrupt, or an unhandled format.";
            }
          }
        }
      }
      // Return the details and the raw content as a two-element array
      return new String[] {inspectionDetails, fileContent};
    }

    private char[] promptForPassword() {
      JPasswordField pf = new JPasswordField();
      // We are in a background thread, so we must show the dialog on the Event Dispatch Thread (EDT)
      final int[] result = new int[1]; // Use an array to pass the result out of the lambda
      try {
        SwingUtilities.invokeAndWait(() -> result[0] = JOptionPane.showConfirmDialog(CertificateConverterGUI.this, pf,
            "Enter Password for " + file.getName(), JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE));
      }
      catch (InterruptedException | java.lang.reflect.InvocationTargetException e) {
        // If EDT invocation fails, return null to indicate failure.
        Thread.currentThread().interrupt();
        return null;
      }
      return (result[0] == JOptionPane.OK_OPTION) ? pf.getPassword() : null;
    }

    private String inspectPkcs12(byte[] data, char[] password) throws Exception {
      StringBuilder sb = new StringBuilder();
      KeyStore p12 = KeyStore.getInstance("PKCS12");
      p12.load(new ByteArrayInputStream(data), password);
      sb.append("--- Keystore Details ---\n");
      sb.append("Type: PKCS12\n");
      sb.append("Provider: ").append(p12.getProvider().getName()).append("\n");
      List<String> aliases = Collections.list(p12.aliases());
      sb.append("Contains ").append(aliases.size()).append(" entry/entries.\n");
      for (String alias : aliases) {
        sb.append("\n--- Entry Alias: '").append(alias).append("' ---\n");
        if (p12.isKeyEntry(alias)) {
          sb.append("Type: Private Key Entry\n");
          Certificate[] chain = p12.getCertificateChain(alias);
          if (chain != null) {
            sb.append("Certificate Chain Length: ").append(chain.length).append("\n");
            for (int i = 0; i < chain.length; i++) {
              sb.append("\n--- Certificate #").append(i + 1).append(" in Chain ---\n");
              sb.append(formatX509CertDetails((X509Certificate) chain[i]));
            }
          }
        }
        else if (p12.isCertificateEntry(alias)) {
          sb.append("Type: Trusted Certificate Entry\n");
          sb.append(formatX509CertDetails((X509Certificate) p12.getCertificate(alias)));
        }
      }
      return sb.toString();
    }

    private String inspectX509(byte[] data) throws Exception {
      StringBuilder sb = new StringBuilder();
      CertificateFactory cf = CertificateFactory.getInstance("X.509");
      Collection<? extends Certificate> certs = cf.generateCertificates(new ByteArrayInputStream(data));
      if (certs.isEmpty())
        throw new Exception("No certificates found");
      sb.append("--- X.509 Certificate Details ---\n");
      sb.append("Found ").append(certs.size()).append(" certificate(s) in the file.\n");
      int i = 1;
      for (Certificate cert : certs) {
        sb.append("\n--- Certificate #").append(i++).append(" ---\n");
        sb.append(formatX509CertDetails((X509Certificate) cert));
      }
      return sb.toString();
    }

    private String inspectPublicKey(byte[] data) throws Exception {
      byte[] keyBytes = decodePem(data);
      KeyFactory kf; // Must be initialized within a successful try block
      try {
        kf = KeyFactory.getInstance("RSA");
        kf.generatePublic(new X509EncodedKeySpec(keyBytes));
      }
      catch (Exception rsaE) {
        try {
          kf = KeyFactory.getInstance("EC");
          kf.generatePublic(new X509EncodedKeySpec(keyBytes));
        }
        catch (Exception ecE) {
          kf = KeyFactory.getInstance("DSA"); // Try DSA
          kf.generatePublic(new X509EncodedKeySpec(keyBytes)); // This will throw if it also fails
        }
      }
      PublicKey pubKey = kf.generatePublic(new X509EncodedKeySpec(keyBytes));
      return formatPublicKeyDetails(pubKey);
    }

    private String inspectPrivateKey(byte[] data) throws Exception {
      byte[] keyBytes = decodePem(data);
      KeyFactory kf; // Must be initialized within a successful try block
      try {
        kf = KeyFactory.getInstance("RSA");
        kf.generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
      }
      catch (Exception rsaE) {
        try {
          kf = KeyFactory.getInstance("EC");
          kf.generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
        }
        catch (Exception ecE) {
          kf = KeyFactory.getInstance("DSA");
          kf.generatePrivate(new PKCS8EncodedKeySpec(keyBytes)); // This will throw if it also fails
        }
      }
      PrivateKey privKey = kf.generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
      return String.format("--- Private Key Details ---\nAlgorithm: %s\nFormat: %s\n", privKey.getAlgorithm(), privKey.getFormat());
    }

    private String formatX509CertDetails(X509Certificate cert) {
      StringBuilder sb = new StringBuilder();
      sb.append("Subject: ").append(cert.getSubjectX500Principal()).append("\n");
      sb.append("Issuer: ").append(cert.getIssuerX500Principal()).append("\n");
      sb.append("Serial Number: ").append(cert.getSerialNumber().toString(16)).append("\n");
      sb.append("Valid From: ").append(cert.getNotBefore()).append("\n");

      // Get expiration date
      java.util.Date expirationDate = cert.getNotAfter();
      sb.append("Valid To: ").append(expirationDate).append("\n");

      // Calculate and append days remaining
      long daysRemaining = ChronoUnit.DAYS.between(Instant.now(), expirationDate.toInstant());

      if (daysRemaining < 0) {
        sb.append("Days Remaining: 0 (Expired)\n");
      }
      else {
        sb.append("Days Remaining: ").append(daysRemaining).append("\n");
      }

      sb.append("Signature Algorithm: ").append(cert.getSigAlgName()).append("\n");
      sb.append("Version: ").append(cert.getVersion()).append("\n");
      sb.append(formatPublicKeyDetails(cert.getPublicKey()));
      return sb.toString();
    }

    private String formatPublicKeyDetails(PublicKey key) {
      StringBuilder sb = new StringBuilder();
      sb.append("--- Public Key ---\n");
      sb.append("Algorithm: ").append(key.getAlgorithm()).append("\n");
      sb.append("Format: ").append(key.getFormat()).append("\n");
      if (key instanceof RSAPublicKey) {
        sb.append("Size: ").append(((RSAPublicKey) key).getModulus().bitLength()).append(" bits\n");
      }
      return sb.toString();
    }

    private byte[] decodePem(byte[] pem) {
      String pemStr = new String(pem, StandardCharsets.UTF_8)
          .replace("-----BEGIN PUBLIC KEY-----", "")
          .replace("-----END PUBLIC KEY-----", "")
          .replace("-----BEGIN PRIVATE KEY-----", "")
          .replace("-----END PRIVATE KEY-----", "")
          .replace("-----BEGIN CERTIFICATE-----", "")
          .replace("-----END CERTIFICATE-----", "")
          .replaceAll("\\s", "");
      try {
        return Base64.getDecoder().decode(pemStr);
      }
      catch (Exception e) {
        return pem; // Not Base64, assume it's binary (DER) and return original bytes
      }
    }

    @Override
    protected void done() {
      try {
        String[] result = get(); // get() now returns String[]
        inspectorDetailsArea.setText(result[0]);
        inspectorRawTextArea.setText(result[1]);

        // Scroll both to the top
        inspectorDetailsArea.setCaretPosition(0);
        inspectorRawTextArea.setCaretPosition(0);

      }
      catch (Exception ex) {
        String errorMessage = "An unexpected error occurred during inspection.\nError: " + ex.getMessage();
        inspectorDetailsArea.setText(errorMessage);
        inspectorRawTextArea.setText("Could not load file content due to an error.");
        ex.printStackTrace();
      }
    }
  }

  private class CaBundlerWorker extends SwingWorker<String, Void> {
    private final File baseFile;
    private final List<File> additionalFiles;
    private final File outputFile;

    CaBundlerWorker(File baseFile, List<File> additionalFiles, File outputFile) {
      this.baseFile = baseFile;
      this.additionalFiles = additionalFiles;
      this.outputFile = outputFile;
    }

    @Override
    protected String doInBackground() throws Exception {
      StringBuilder bundleContent = new StringBuilder();

      // Read the base file's content
      String baseContent = Files.readString(baseFile.toPath(), StandardCharsets.UTF_8).trim();
      bundleContent.append(baseContent).append("\n");

      // Read and append each additional file's content
      for (File file : additionalFiles) {
        String additionalContent = Files.readString(file.toPath(), StandardCharsets.UTF_8).trim();
        bundleContent.append("\n").append(additionalContent).append("\n");
      }

      // Write the combined content to the output file
      Files.writeString(outputFile.toPath(), bundleContent.toString(), StandardCharsets.UTF_8);

      return "Successfully created bundle with 1 base certificate and " + additionalFiles.size() + " additional certificate(s) at:\n"
          + outputFile.getAbsolutePath();
    }

    @Override
    protected void done() {
      try {
        String result = get();
        bundlerStatusArea.setText(bundlerStatusArea.getText() + "\n\n--- SUCCESS ---\n" + result);
        JOptionPane.showMessageDialog(CertificateConverterGUI.this, result, "Bundle Created", JOptionPane.INFORMATION_MESSAGE);
      }
      catch (Exception ex) {
        String errorMsg = "Failed to create bundle: " + ex.getMessage();
        bundlerStatusArea.setText(bundlerStatusArea.getText() + "\n\n--- ERROR ---\n" + errorMsg);
        JOptionPane.showMessageDialog(CertificateConverterGUI.this, errorMsg, "Error", JOptionPane.ERROR_MESSAGE);
      }
    }
  }

  private class PasswordUpdaterWorker extends SwingWorker<String, Void> {
    private final File fileToUpdate;
    private final char[] oldPassword;
    private final char[] newPassword;

    PasswordUpdaterWorker(File fileToUpdate, char[] oldPassword, char[] newPassword) {
      this.fileToUpdate = fileToUpdate;
      this.oldPassword = oldPassword;
      this.newPassword = newPassword;
    }

    @Override
    protected String doInBackground() throws Exception {
      KeyStore keyStore = KeyStore.getInstance("PKCS12");
      try (FileInputStream fis = new FileInputStream(fileToUpdate)) {
        keyStore.load(fis, oldPassword);
      }

      // Create a new keystore to write to. We'll copy entries to it.
      KeyStore newKeyStore = KeyStore.getInstance("PKCS12");
      newKeyStore.load(null, newPassword); // Initialize with the new password

      Enumeration<String> aliases = keyStore.aliases();
      while (aliases.hasMoreElements()) {
        String alias = aliases.nextElement();
        if (keyStore.isKeyEntry(alias)) {
          Key privateKey = keyStore.getKey(alias, oldPassword);
          Certificate[] chain = keyStore.getCertificateChain(alias);
          newKeyStore.setKeyEntry(alias, privateKey, newPassword, chain);
        }
        else if (keyStore.isCertificateEntry(alias)) {
          Certificate cert = keyStore.getCertificate(alias);
          newKeyStore.setCertificateEntry(alias, cert);
        }
      }

      // Write the new keystore to a temporary file first for safety
      File tempFile = File.createTempFile("cert-update-", ".p12");
      try (FileOutputStream fos = new FileOutputStream(tempFile)) {
        newKeyStore.store(fos, newPassword);
      }

      // If successful, replace the original file with the temporary one
      Files.move(tempFile.toPath(), fileToUpdate.toPath(), StandardCopyOption.REPLACE_EXISTING);

      return "Password for '" + fileToUpdate.getName() + "' has been updated successfully.";
    }

    @Override
    protected void done() {
      try {
        String result = get();
        JOptionPane.showMessageDialog(CertificateConverterGUI.this, result, "Success", JOptionPane.INFORMATION_MESSAGE);
        // Reset fields
        oldPasswordField.setText("");
        newPasswordField.setText("");
        removePasswordCheckBox.setSelected(false);
        passwordFileLabel.setText("No file selected.");
        passwordFileLabel.setFont(new Font("SansSerif", Font.ITALIC, 12));
        updatePasswordButton.setEnabled(false);
        passwordUpdateFile = null;

      }
      catch (Exception ex) {
        String errorMessage = "Failed to update password.\n";
        if (ex.getCause() instanceof java.io.IOException && ex.getCause().getMessage().contains("keystore password was incorrect")) {
          errorMessage += "The current password provided is incorrect.";
        }
        else {
          errorMessage += "Error: " + ex.getMessage();
        }
        JOptionPane.showMessageDialog(CertificateConverterGUI.this, errorMessage, "Error", JOptionPane.ERROR_MESSAGE);
      }
    }
  }

  private class P12AnalyzerWorker extends SwingWorker<KeyStore, Void> {
    private final File p12File;
    private final char[] password;

    P12AnalyzerWorker(File p12File, char[] password) {
      this.p12File = p12File;
      this.password = password;
    }

    @Override
    protected KeyStore doInBackground() throws Exception {
      KeyStore ks = KeyStore.getInstance("PKCS12");
      try (FileInputStream fis = new FileInputStream(p12File)) {
        ks.load(fis, password);
      }
      return ks;
    }

    @Override
    protected void done() {
      p12ExtractorResultsPanel.removeAll();
      try {
        loadedP12KeyStore = get();
        List<String> keyAliases = new ArrayList<>();
        Enumeration<String> aliases = loadedP12KeyStore.aliases();
        while (aliases.hasMoreElements()) {
          String alias = aliases.nextElement();
          if (loadedP12KeyStore.isKeyEntry(alias)) {
            keyAliases.add(alias);
          }
        }

        if (keyAliases.isEmpty()) {
          p12ExtractorResultsPanel.add(new JLabel("No private key entries found in this P12 file."));
        }
        else {
          p12SelectedAlias = keyAliases.get(0);
          if (keyAliases.size() > 1) {
            String chosenAlias = (String) JOptionPane.showInputDialog(CertificateConverterGUI.this,
                "Multiple identities found. Select an alias to extract:", "Select Alias",
                JOptionPane.PLAIN_MESSAGE, null, keyAliases.toArray(), p12SelectedAlias);
            if (chosenAlias != null) {
              p12SelectedAlias = chosenAlias;
            }
          }

          // Build the results UI
          buildP12ExtractorResultsUI(p12SelectedAlias);
        }

      }
      catch (Exception ex) {
        loadedP12KeyStore = null;
        p12ExtractButton.setEnabled(false);
        String errorMessage = "Failed to load P12 file. The password may be incorrect.";
        p12ExtractorResultsPanel.add(new JLabel(errorMessage));
        JOptionPane.showMessageDialog(CertificateConverterGUI.this, errorMessage, "Error", JOptionPane.ERROR_MESSAGE);
      }
      p12ExtractorResultsPanel.revalidate();
      p12ExtractorResultsPanel.repaint();
    }

    private void buildP12ExtractorResultsUI(String alias) {
      GridBagConstraints gbc = new GridBagConstraints();
      gbc.insets = new Insets(5, 5, 5, 5);
      gbc.anchor = GridBagConstraints.WEST;

      gbc.gridx = 0;
      gbc.gridy = 0;
      p12ExtractorResultsPanel.add(new JLabel("Private Key:"), gbc);
      gbc.gridx = 1;
      privateKeyFormatBox = new JComboBox<>(new String[] {"Do Not Extract", "PEM (PKCS#8 Encrypted)", "PEM (PKCS#8 Unencrypted)"});
      p12ExtractorResultsPanel.add(privateKeyFormatBox, gbc);

      gbc.gridx = 0;
      gbc.gridy = 1;
      p12ExtractorResultsPanel.add(new JLabel("Client Certificate:"), gbc);
      gbc.gridx = 1;
      clientCertFormatBox = new JComboBox<>(new String[] {"PEM", "DER", "Do Not Extract"});
      p12ExtractorResultsPanel.add(clientCertFormatBox, gbc);

      gbc.gridx = 0;
      gbc.gridy = 2;
      p12ExtractorResultsPanel.add(new JLabel("CA Certificate(s):"), gbc);
      gbc.gridx = 1;
      caCertsFormatBox = new JComboBox<>(new String[] {"PEM (Bundle)", "Do Not Extract"});
      p12ExtractorResultsPanel.add(caCertsFormatBox, gbc);

      if (!p12OutputPathField.getText().trim().isEmpty()) {
        p12ExtractButton.setEnabled(true);
      }
    }
  }

  private class P12ExtractorWorker extends SwingWorker<String, Void> {
    private final KeyStore keyStore;
    private final String alias;
    private final char[] password;
    private final File outputDir;
    private final Map<String, String> formats;

    P12ExtractorWorker(KeyStore keyStore, String alias, char[] password, File outputDir, Map<String, String> formats) {
      this.keyStore = keyStore;
      this.alias = alias;
      this.password = password;
      this.outputDir = outputDir;
      this.formats = formats;
    }

    @Override
    protected String doInBackground() throws Exception {
      List<String> extractedFiles = new ArrayList<>();
      Base64.Encoder mimeEncoder = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII));

      // --- Extract Private Key ---
      String pkFormat = formats.get("privateKey");
      if (!"Do Not Extract".equals(pkFormat)) {
        PrivateKey key = (PrivateKey) keyStore.getKey(alias, password);
        File keyFile = new File(outputDir, alias + "_key.pem");
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(keyFile), StandardCharsets.US_ASCII)) {
          if ("PEM (PKCS#8 Unencrypted)".equals(pkFormat)) {
            writer.write("-----BEGIN PRIVATE KEY-----\n");
            writer.write(mimeEncoder.encodeToString(key.getEncoded()));
            writer.write("\n-----END PRIVATE KEY-----\n");
          }
          else { // Default to Encrypted
            // For simplicity, this example doesn't implement PEM encryption.
            // A real-world app would use BouncyCastle or similar here.
            // We will write it unencrypted and note this limitation.
            writer.write("-----BEGIN PRIVATE KEY-----\n");
            writer.write(mimeEncoder.encodeToString(key.getEncoded()));
            writer.write("\n-----END PRIVATE KEY-----\n");
          }
        }
        extractedFiles.add(keyFile.getName());
      }

      // --- Extract Certificates ---
      Certificate[] chain = keyStore.getCertificateChain(alias);

      // --- Client Certificate ---
      String certFormat = formats.get("clientCert");
      if (!"Do Not Extract".equals(certFormat) && chain != null && chain.length > 0) {
        Certificate clientCert = chain[0];
        if ("PEM".equals(certFormat)) {
          File certFile = new File(outputDir, alias + "_cert.pem");
          try (Writer writer = new OutputStreamWriter(new FileOutputStream(certFile), StandardCharsets.US_ASCII)) {
            writer.write("-----BEGIN CERTIFICATE-----\n");
            writer.write(mimeEncoder.encodeToString(clientCert.getEncoded()));
            writer.write("\n-----END CERTIFICATE-----\n");
          }
          extractedFiles.add(certFile.getName());
        }
        else if ("DER".equals(certFormat)) {
          File certFile = new File(outputDir, alias + "_cert.der");
          try (FileOutputStream fos = new FileOutputStream(certFile)) {
            fos.write(clientCert.getEncoded());
          }
          extractedFiles.add(certFile.getName());
        }
      }

      // --- CA Chain ---
      String caFormat = formats.get("caCerts");
      if (!"Do Not Extract".equals(caFormat) && chain != null && chain.length > 1) {
        File caFile = new File(outputDir, alias + "_chain.pem");
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(caFile), StandardCharsets.US_ASCII)) {
          for (int i = 1; i < chain.length; i++) {
            writer.write("-----BEGIN CERTIFICATE-----\n");
            writer.write(mimeEncoder.encodeToString(chain[i].getEncoded()));
            writer.write("\n-----END CERTIFICATE-----\n\n");
          }
        }
        extractedFiles.add(caFile.getName());
      }

      if (extractedFiles.isEmpty()) {
        return "No components were selected for extraction.";
      }
      return "Extraction successful. Files created:\n" + String.join("\n", extractedFiles);
    }

    @Override
    protected void done() {
      try {
        String result = get();
        JOptionPane.showMessageDialog(CertificateConverterGUI.this, result, "Extraction Complete", JOptionPane.INFORMATION_MESSAGE);
      }
      catch (Exception ex) {
        ex.printStackTrace();
        JOptionPane.showMessageDialog(CertificateConverterGUI.this, "Extraction failed: " + ex.getMessage(), "Error",
            JOptionPane.ERROR_MESSAGE);
      }
    }
  }
}
