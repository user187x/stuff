package xxx.com.encrypt;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;

public class Encryptor extends JFrame {

  private static final long serialVersionUID = 765570579234342061L;
  private final JTextArea textArea;
  private final JPasswordField passwordField;
  private final JToggleButton toggleButton;
  private final JButton processButton;

  // Encryption parameters
  private static final String ALGORITHM = "AES/GCM/NoPadding";
  private static final int GCM_IV_LENGTH = 12; // 96 bits
  private static final int GCM_TAG_LENGTH = 128; // 128 bits
  private static final String PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256";
  private static final int ITERATIONS = 65536; // Number of iterations for PBKDF2
  private static final int KEY_LENGTH = 256; // AES-256

  public Encryptor() {
    setTitle("Secure Encryptor/Decryptor");
    setSize(600, 400);
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    setLocationRelativeTo(null); // Center the window

    // Initialize components
    textArea = new JTextArea(10, 40);
    textArea.setLineWrap(true);
    textArea.setWrapStyleWord(true);
    JScrollPane scrollPane = new JScrollPane(textArea);

    passwordField = new JPasswordField(20);

    toggleButton = new JToggleButton("Mode: Encrypt");
    toggleButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (toggleButton.isSelected()) {
          toggleButton.setText("Mode: Decrypt");
          processButton.setText("Decrypt");
        }
        else {
          toggleButton.setText("Mode: Encrypt");
          processButton.setText("Encrypt");
        }
      }
    });

    processButton = new JButton("Encrypt");
    processButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        processText();
      }
    });

    // Layout components
    setLayout(new BorderLayout(10, 10)); // Add some padding

    // Top panel for password and toggle
    JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5)); // Use FlowLayout for simple arrangement
    topPanel.add(new JLabel("Password:"));
    topPanel.add(passwordField);
    topPanel.add(toggleButton);

    // Bottom panel for the process button
    JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
    bottomPanel.add(processButton);

    add(topPanel, BorderLayout.NORTH);
    add(scrollPane, BorderLayout.CENTER);
    add(bottomPanel, BorderLayout.SOUTH);

    setVisible(true);
  }

  private void processText() {
    String input = textArea.getText();
    char[] passwordChars = passwordField.getPassword();
    String password = new String(passwordChars);

    if (input.isEmpty() || password.isEmpty()) {
      JOptionPane.showMessageDialog(this, "Please enter both text and a password.", "Error", JOptionPane.ERROR_MESSAGE);
      return;
    }

    try {
      if (toggleButton.isSelected()) { // Decrypt mode
        String decryptedText = decrypt(input, password);
        textArea.setText(decryptedText);
      }
      else { // Encrypt mode
        String encryptedText = encrypt(input, password);
        textArea.setText(encryptedText);
      }
    }
    catch (Exception ex) {
      JOptionPane.showMessageDialog(this, "Operation failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
      ex.printStackTrace(); // For debugging
    }
    finally {
      // Clear password from memory
      java.util.Arrays.fill(passwordChars, ' ');
    }
  }

  private SecretKeySpec deriveKey(String password, byte[] salt) throws Exception {
    PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH);
    SecretKeyFactory factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM);
    byte[] keyBytes = factory.generateSecret(spec).getEncoded();
    return new SecretKeySpec(keyBytes, "AES");
  }

  private String encrypt(String plainText, String password) throws Exception {
    SecureRandom secureRandom = new SecureRandom();

    // 1. Generate Salt
    byte[] salt = new byte[16]; // 16 bytes for salt
    secureRandom.nextBytes(salt);

    // 2. Derive Key
    SecretKeySpec key = deriveKey(password, salt);

    // 3. Generate IV
    byte[] iv = new byte[GCM_IV_LENGTH];
    secureRandom.nextBytes(iv);

    // 4. Initialize Cipher
    Cipher cipher = Cipher.getInstance(ALGORITHM);
    GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
    cipher.init(Cipher.ENCRYPT_MODE, key, gcmParameterSpec);

    // 5. Encrypt
    byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

    // 6. Combine Salt, IV, and Ciphertext for storage (Base64 encoded)
    // Format: Base64(salt):Base64(iv):Base64(ciphertext)
    String encodedSalt = Base64.getEncoder().encodeToString(salt);
    String encodedIv = Base64.getEncoder().encodeToString(iv);
    String encodedCipherText = Base64.getEncoder().encodeToString(cipherText);

    return encodedSalt + ":" + encodedIv + ":" + encodedCipherText;
  }

  private String decrypt(String encryptedText, String password) throws Exception {
    // 1. Parse Salt, IV, and Ciphertext from the input string
    String[] parts = encryptedText.split(":");
    if (parts.length != 3) {
      throw new IllegalArgumentException("Invalid encrypted text format. Expected salt:iv:ciphertext.");
    }

    byte[] salt = Base64.getDecoder().decode(parts[0]);
    byte[] iv = Base64.getDecoder().decode(parts[1]);
    byte[] cipherText = Base64.getDecoder().decode(parts[2]);

    // 2. Derive Key (using the provided salt)
    SecretKeySpec key = deriveKey(password, salt);

    // 3. Initialize Cipher
    Cipher cipher = Cipher.getInstance(ALGORITHM);
    GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
    cipher.init(Cipher.DECRYPT_MODE, key, gcmParameterSpec);

    // 4. Decrypt
    byte[] plainTextBytes = cipher.doFinal(cipherText);

    return new String(plainTextBytes, StandardCharsets.UTF_8);
  }

  public static void main(String[] args) {
    // Run the GUI on the Event Dispatch Thread
    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        new Encryptor();
      }
    });
  }
}
