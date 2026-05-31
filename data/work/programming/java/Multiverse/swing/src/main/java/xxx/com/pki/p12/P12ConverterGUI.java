package xxx.com.pki.p12;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.*;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Enumeration;

/**
 * A Java Swing GUI to guide a user through extracting content from a PKCS#12 (.p12) file. The UI
 * dynamically presents conversion and extraction options based on user choices. This version
 * correctly handles P12 files that are not password-protected.
 */
public class P12ConverterGUI extends JFrame {

  // --- UI Components ---
  private final JPanel mainPanel;
  private JPanel fileSelectionPanel;
  private JPanel conversionPanel;
  private JButton selectFileButton;
  private JButton loadP12Button;
  private JLabel selectedFileLabel;
  private JPasswordField passwordField;

  // Conversion/Extraction Buttons
  private JButton extractPrivateKeyPemButton;
  private JButton extractCertPemButton;
  private JButton extractCertDerButton;
  private JButton convertPemToDerButton;
  private JButton convertDerToPemButton;

  // --- State Management ---
  private File p12File;
  private KeyStore keyStore;
  private X509Certificate certificate;
  private String keyAlias;

  // Flags to track what has been extracted/converted
  private boolean pemCertExtracted = false;
  private boolean derCertExtracted = false;

  public P12ConverterGUI() {
    setTitle("P12 Certificate Converter");
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    setSize(600, 450);
    setLocationRelativeTo(null); // Center the window

    mainPanel = new JPanel();
    mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
    mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
    add(mainPanel);

    setupFileSelectionPanel();
    setupConversionPanel();

    mainPanel.add(fileSelectionPanel);
    mainPanel.add(Box.createRigidArea(new Dimension(0, 10))); // Spacer
    mainPanel.add(conversionPanel);

    // Initially, the conversion panel is hidden
    conversionPanel.setVisible(false);
  }

  /**
   * Sets up the initial panel for selecting the .p12 file and entering the password.
   */
  private void setupFileSelectionPanel() {
    fileSelectionPanel = new JPanel(new GridBagLayout());
    fileSelectionPanel.setBorder(new TitledBorder("Step 1: Load P12 File"));
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.insets = new Insets(5, 5, 5, 5);
    gbc.fill = GridBagConstraints.HORIZONTAL;

    // Row 1: File Selection
    selectFileButton = new JButton("Select .p12 File");
    selectedFileLabel = new JLabel("No file selected.");
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.weightx = 0.2;
    fileSelectionPanel.add(selectFileButton, gbc);
    gbc.gridx = 1;
    gbc.gridy = 0;
    gbc.weightx = 0.8;
    fileSelectionPanel.add(selectedFileLabel, gbc);

    // Row 2: Password
    JLabel passwordLabel = new JLabel("Password (optional):");
    passwordField = new JPasswordField();
    gbc.gridx = 0;
    gbc.gridy = 1;
    gbc.weightx = 0.2;
    fileSelectionPanel.add(passwordLabel, gbc);
    gbc.gridx = 1;
    gbc.gridy = 1;
    gbc.weightx = 0.8;
    fileSelectionPanel.add(passwordField, gbc);

    // Row 3: Load Button
    loadP12Button = new JButton("Unlock & Analyze");
    loadP12Button.setEnabled(false); // Enabled when a file is chosen
    gbc.gridx = 1;
    gbc.gridy = 2;
    gbc.anchor = GridBagConstraints.EAST;
    gbc.fill = GridBagConstraints.NONE;
    fileSelectionPanel.add(loadP12Button, gbc);

    // --- Action Listeners ---
    selectFileButton.addActionListener(this::selectFileAction);
    loadP12Button.addActionListener(this::loadP12Action);
  }

  /**
   * Sets up the panel that will dynamically show conversion and extraction options.
   */
  private void setupConversionPanel() {
    conversionPanel = new JPanel();
    conversionPanel.setLayout(new BoxLayout(conversionPanel, BoxLayout.Y_AXIS));
    conversionPanel.setBorder(new TitledBorder("Step 2: Extract or Convert"));

    // --- Extraction from P12 ---
    JPanel p12ExtractPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
    p12ExtractPanel.setBorder(new TitledBorder("From P12 Archive"));
    extractPrivateKeyPemButton = new JButton("Extract Private Key (.key)");
    extractCertPemButton = new JButton("Extract Certificate (.pem)");
    extractCertDerButton = new JButton("Extract Certificate (.der)");
    p12ExtractPanel.add(extractPrivateKeyPemButton);
    p12ExtractPanel.add(extractCertPemButton);
    p12ExtractPanel.add(extractCertDerButton);

    // --- Subsequent Conversions ---
    JPanel subsequentConversionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
    subsequentConversionPanel.setBorder(new TitledBorder("Subsequent Conversions"));
    convertPemToDerButton = new JButton("Convert PEM to DER");
    convertDerToPemButton = new JButton("Convert DER to PEM");
    subsequentConversionPanel.add(convertPemToDerButton);
    subsequentConversionPanel.add(convertDerToPemButton);

    // Initially hide conversion buttons
    convertPemToDerButton.setVisible(false);
    convertDerToPemButton.setVisible(false);

    conversionPanel.add(p12ExtractPanel);
    conversionPanel.add(subsequentConversionPanel);

    // --- Action Listeners ---
    extractPrivateKeyPemButton.addActionListener(this::extractPrivateKeyAction);
    extractCertPemButton.addActionListener(this::extractCertPemAction);
    extractCertDerButton.addActionListener(this::extractCertDerAction);
    convertPemToDerButton.addActionListener(this::convertPemToDerAction);
    convertDerToPemButton.addActionListener(this::convertDerToPemAction);
  }

  // --- GUI Action Methods ---

  private void selectFileAction(ActionEvent e) {
    JFileChooser fileChooser = new JFileChooser();
    fileChooser.setDialogTitle("Select a .p12 or .pfx file");
    fileChooser.setFileFilter(new javax.swing.filechooser.FileFilter() {
      public boolean accept(File f) {
        return f.getName().toLowerCase().endsWith(".p12") ||
            f.getName().toLowerCase().endsWith(".pfx") ||
            f.isDirectory();
      }

      public String getDescription() {
        return "PKCS12 Files (*.p12, *.pfx)";
      }
    });
    int result = fileChooser.showOpenDialog(this);
    if (result == JFileChooser.APPROVE_OPTION) {
      p12File = fileChooser.getSelectedFile();
      selectedFileLabel.setText(p12File.getName());
      loadP12Button.setEnabled(true);
    }
  }

  private void loadP12Action(ActionEvent e) {
    if (p12File == null) {
      JOptionPane.showMessageDialog(this, "Please select a file first.", "Input Missing", JOptionPane.WARNING_MESSAGE);
      return;
    }

    char[] password = passwordField.getPassword();
    // If the password field is empty, we must try loading with null.
    char[] loadPassword = (password.length == 0) ? null : password;

    try (FileInputStream fis = new FileInputStream(p12File)) {
      keyStore = KeyStore.getInstance("PKCS12");
      // Try loading the keystore. This will throw an exception on incorrect password.
      keyStore.load(fis, loadPassword);

      // Find the alias for the private key and certificate
      Enumeration<String> aliases = keyStore.aliases();
      while (aliases.hasMoreElements()) {
        String alias = aliases.nextElement();
        if (keyStore.isKeyEntry(alias)) {
          keyAlias = alias;
          certificate = (X509Certificate) keyStore.getCertificate(alias);
          break;
        }
      }

      if (keyAlias == null) {
        throw new KeyStoreException("Could not find a private key entry in the keystore.");
      }

      // --- UI Transition ---
      fileSelectionPanel.setBorder(new TitledBorder("Step 1: Loaded: " + p12File.getName()));
      loadP12Button.setEnabled(false);
      selectFileButton.setEnabled(false);
      passwordField.setEnabled(false);
      conversionPanel.setVisible(true);
      JOptionPane.showMessageDialog(this, "P12 file loaded successfully!", "Success", JOptionPane.INFORMATION_MESSAGE);

    }
    catch (IOException ex) {
      // Provide a more specific error for password failure
      if (ex.getMessage() != null
          && (ex.getMessage().contains("password was incorrect") || ex.getMessage().contains("failed to decrypt"))) {
        JOptionPane.showMessageDialog(this, "Failed to load P12 file: The password was incorrect or the file is corrupt.",
            "Authentication Error", JOptionPane.ERROR_MESSAGE);
      }
      else {
        JOptionPane.showMessageDialog(this, "Failed to load P12 file:\n" + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
      }
      ex.printStackTrace();
    }
    catch (Exception ex) {
      JOptionPane.showMessageDialog(this, "An unexpected error occurred:\n" + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
      ex.printStackTrace();
    }
  }

  private void extractPrivateKeyAction(ActionEvent e) {
    try {
      // Use null for password if the field is empty, consistent with loading logic.
      char[] password = passwordField.getPassword();
      char[] keyPassword = (password.length == 0) ? null : password;

      java.security.Key key = keyStore.getKey(keyAlias, keyPassword);
      if (key == null) {
        // This can happen if the key is protected by a different password within the keystore,
        // but for most P12s, it's the same as the keystore password.
        throw new KeyStoreException("Could not retrieve key. It might be protected by a different password than the keystore.");
      }
      String encodedKey = "-----BEGIN PRIVATE KEY-----\n" +
          Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(key.getEncoded()) +
          "\n-----END PRIVATE KEY-----";

      saveToFile(encodedKey, "key", "Save Private Key as PEM");
      extractPrivateKeyPemButton.setEnabled(false); // Disable after extraction
      extractPrivateKeyPemButton.setText("Private Key Extracted");

    }
    catch (Exception ex) {
      JOptionPane.showMessageDialog(this, "Could not extract private key: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
      ex.printStackTrace();
    }
  }

  private void extractCertPemAction(ActionEvent e) {
    try {
      String encodedCert = "-----BEGIN CERTIFICATE-----\n" +
          Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(certificate.getEncoded()) +
          "\n-----END CERTIFICATE-----";

      saveToFile(encodedCert, "pem", "Save Certificate as PEM");
      extractCertPemButton.setEnabled(false);
      extractCertPemButton.setText("PEM Cert Extracted");
      pemCertExtracted = true;
      updateConversionButtons();

    }
    catch (CertificateEncodingException ex) {
      JOptionPane.showMessageDialog(this, "Could not encode certificate: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
    }
  }

  private void extractCertDerAction(ActionEvent e) {
    try {
      byte[] derCert = certificate.getEncoded();
      saveToFile(derCert, "der", "Save Certificate as DER");
      extractCertDerButton.setEnabled(false);
      extractCertDerButton.setText("DER Cert Extracted");
      derCertExtracted = true;
      updateConversionButtons();

    }
    catch (CertificateEncodingException ex) {
      JOptionPane.showMessageDialog(this, "Could not encode certificate: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
    }
  }

  private void convertPemToDerAction(ActionEvent e) {
    extractCertDerAction(e); // The logic is the same as extracting to DER
    convertPemToDerButton.setEnabled(false);
    convertPemToDerButton.setText("PEM -> DER Done");
  }

  private void convertDerToPemAction(ActionEvent e) {
    extractCertPemAction(e); // The logic is the same as extracting to PEM
    convertDerToPemButton.setEnabled(false);
    convertDerToPemButton.setText("DER -> PEM Done");
  }

  /**
   * Shows/hides the subsequent conversion buttons based on what has been extracted.
   */
  private void updateConversionButtons() {
    // If a PEM cert is available and a DER cert is not, show the PEM->DER button
    if (pemCertExtracted && !derCertExtracted) {
      convertPemToDerButton.setVisible(true);
    }
    // If a DER cert is available and a PEM cert is not, show the DER->PEM button
    if (derCertExtracted && !pemCertExtracted) {
      convertDerToPemButton.setVisible(true);
    }
    // If both are extracted, hide both conversion buttons as there's nothing left to do.
    if (pemCertExtracted && derCertExtracted) {
      convertPemToDerButton.setVisible(false);
      convertDerToPemButton.setVisible(false);
    }
  }

  // --- Helper Methods ---

  private void saveToFile(String content, String extension, String dialogTitle) {
    JFileChooser fileChooser = new JFileChooser();
    fileChooser.setDialogTitle(dialogTitle);
    fileChooser.setSelectedFile(new File("output." + extension));
    if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
      File file = fileChooser.getSelectedFile();
      try (FileWriter writer = new FileWriter(file)) {
        writer.write(content);
        JOptionPane.showMessageDialog(this, "Successfully saved to " + file.getName(), "Save Successful", JOptionPane.INFORMATION_MESSAGE);
      }
      catch (IOException ex) {
        JOptionPane.showMessageDialog(this, "Error saving file: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
      }
    }
  }

  private void saveToFile(byte[] content, String extension, String dialogTitle) {
    JFileChooser fileChooser = new JFileChooser();
    fileChooser.setDialogTitle(dialogTitle);
    fileChooser.setSelectedFile(new File("output." + extension));
    if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
      File file = fileChooser.getSelectedFile();
      try (FileOutputStream fos = new FileOutputStream(file)) {
        fos.write(content);
        JOptionPane.showMessageDialog(this, "Successfully saved to " + file.getName(), "Save Successful", JOptionPane.INFORMATION_MESSAGE);
      }
      catch (IOException ex) {
        JOptionPane.showMessageDialog(this, "Error saving file: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
      }
    }
  }

  public static void main(String[] args) {
    // Set a modern Look and Feel
    try {
      UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
    }
    catch (Exception e) {
      e.printStackTrace();
    }

    SwingUtilities.invokeLater(() -> {
      P12ConverterGUI gui = new P12ConverterGUI();
      gui.setVisible(true);
    });
  }
}

