package xxx.com.encrypt; // Added package declaration

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font; // Import for Font
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Hashtable; // Import for Hashtable
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JSlider; // Changed from JToggleButton
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent; // Import for ChangeEvent
import javax.swing.event.ChangeListener; // Import for ChangeListener
import javax.swing.filechooser.FileNameExtensionFilter;

public class Steganographer extends JFrame {

  private static final long serialVersionUID = 3788994997166883012L;

  private final JTextArea textArea;
  private final JPasswordField passwordField;

  private final JSlider modeSlider; // Replaced JToggleButton with JSlider
  private final JButton encryptTextButton;
  private final JButton decryptTextButton;

  private final JButton selectImageButton;
  private final JLabel imagePathLabel;
  private final JButton embedTextInImageButton;
  private final JButton extractTextFromImageButton;

  private File selectedImageFile;

  // Encryption parameters (unchanged)
  private static final String ALGORITHM = "AES/GCM/NoPadding";
  private static final int GCM_IV_LENGTH = 12; // 96 bits
  private static final int GCM_TAG_LENGTH = 128; // 128 bits
  private static final String PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256";
  private static final int ITERATIONS = 65536; // Number of iterations for PBKDF2
  private static final int KEY_LENGTH = 256; // AES-256

  public Steganographer() {
    setTitle("Secure Encryptor/Decryptor with Steganography");
    setSize(850, 600);
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    setLocationRelativeTo(null);

    // --- Initialize GUI Components ---
    textArea = new JTextArea(15, 50);
    textArea.setLineWrap(true);
    textArea.setWrapStyleWord(true);
    JScrollPane scrollPane = new JScrollPane(textArea);
    scrollPane.setBorder(BorderFactory.createTitledBorder("Enter Text Here / View Results Below"));

    passwordField = new JPasswordField(25);

    // Initialize the mode slider (replaces toggle button)
    modeSlider = new JSlider(JSlider.HORIZONTAL, 0, 1, 0); // Min: 0 (Encrypt), Max: 1 (Decrypt), Initial: 0
    modeSlider.setMajorTickSpacing(1); // Ensure only two positions
    modeSlider.setPaintTicks(true); // Paint ticks for clarity
    modeSlider.setPaintLabels(true); // Paint labels at tick marks
    modeSlider.setSnapToTicks(true); // Make the slider snap to the tick marks, acting like a switch

    // Set custom labels for the slider positions
    Hashtable<Integer, JLabel> labelTable = new Hashtable<>();
    JLabel encryptLabel = new JLabel("Encrypt Mode");
    encryptLabel.setFont(encryptLabel.getFont().deriveFont(Font.BOLD, 12f)); // Make labels bold
    JLabel decryptLabel = new JLabel("Decrypt Mode");
    decryptLabel.setFont(decryptLabel.getFont().deriveFont(Font.BOLD, 12f)); // Make labels bold

    labelTable.put(0, encryptLabel);
    labelTable.put(1, decryptLabel);
    modeSlider.setLabelTable(labelTable);

    modeSlider.setBorder(BorderFactory.createTitledBorder("Operation Mode")); // Add a border around the slider

    // Add a ChangeListener to react to slider movements
    modeSlider.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent e) {
        // Only update UI when the slider value is not being adjusted (i.e., user has released it)
        if (!modeSlider.getValueIsAdjusting()) {
          updateUIForMode();
        }
      }
    });

    encryptTextButton = new JButton("Perform Text Encryption"); // Renamed for clarity
    decryptTextButton = new JButton("Perform Text Decryption"); // Renamed for clarity

    selectImageButton = new JButton("Step 1: Select Image File");
    imagePathLabel = new JLabel("No image selected");
    embedTextInImageButton = new JButton("Step 2: Embed Encrypted Text into Image");
    extractTextFromImageButton = new JButton("Step 2: Extract & Decrypt Text from Image");

    // --- Layout Components ---
    setLayout(new BorderLayout(15, 15));

    // --- Top Panel: Password Input & Mode Slider ---
    // CHANGED HERE: Using FlowLayout.CENTER to center components
    JPanel topControlPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));
    topControlPanel.setBorder(BorderFactory.createTitledBorder("Control Panel"));
    topControlPanel.add(new JLabel("Password:"));
    topControlPanel.add(passwordField);
    topControlPanel.add(modeSlider); // Add the slider here

    // --- Operations Panel (Left: Text, Right: Image) ---
    JPanel operationsPanel = new JPanel(new GridBagLayout());
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.insets = new Insets(10, 10, 10, 10);

    // --- Text-Only Operations Panel ---
    JPanel textOpsPanel = new JPanel(new GridBagLayout());
    textOpsPanel.setBorder(BorderFactory.createTitledBorder("Text Encryption / Decryption Actions"));

    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.gridwidth = 2;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    textOpsPanel.add(new JLabel("Action for text only:"), gbc);

    gbc.gridy = 1;
    gbc.gridwidth = 1;
    textOpsPanel.add(encryptTextButton, gbc);

    gbc.gridx = 1;
    textOpsPanel.add(decryptTextButton, gbc);

    // Add textOpsPanel to main operations panel
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.gridwidth = 1;
    gbc.fill = GridBagConstraints.BOTH;
    gbc.weightx = 0.5;
    gbc.weighty = 1.0;
    operationsPanel.add(textOpsPanel, gbc);


    // --- Image Steganography Panel ---
    JPanel imageOpsPanel = new JPanel(new GridBagLayout());
    imageOpsPanel.setBorder(BorderFactory.createTitledBorder("Image Steganography Actions"));

    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.gridwidth = 2;
    imageOpsPanel.add(new JLabel("Select an image and choose an action:"), gbc);

    gbc.gridy = 1;
    gbc.gridwidth = 2; // Span across two columns for select image button and label
    imageOpsPanel.add(selectImageButton, gbc);

    gbc.gridy = 2;
    gbc.gridwidth = 2;
    imageOpsPanel.add(imagePathLabel, gbc);

    gbc.gridy = 3;
    gbc.gridwidth = 2;
    imageOpsPanel.add(embedTextInImageButton, gbc);

    gbc.gridy = 4;
    imageOpsPanel.add(extractTextFromImageButton, gbc);

    // Add imageOpsPanel to main operations panel
    gbc.gridx = 1;
    gbc.gridy = 0;
    gbc.gridwidth = 1;
    gbc.fill = GridBagConstraints.BOTH;
    gbc.weightx = 0.5;
    gbc.weighty = 1.0;
    operationsPanel.add(imageOpsPanel, gbc);


    // Add panels to the JFrame
    add(topControlPanel, BorderLayout.NORTH);
    add(scrollPane, BorderLayout.CENTER);
    add(operationsPanel, BorderLayout.SOUTH);

    // --- Add Action Listeners ---
    encryptTextButton.addActionListener(e -> processText(true));
    decryptTextButton.addActionListener(e -> processText(false));

    selectImageButton.addActionListener(e -> {
      JFileChooser fileChooser = new JFileChooser();
      fileChooser.setDialogTitle("Select an Image File");
      FileNameExtensionFilter imageFilter = new FileNameExtensionFilter(
          "Image Files (PNG, JPG, GIF, BMP)", "png", "jpg", "jpeg", "gif", "bmp");
      fileChooser.setFileFilter(imageFilter);
      fileChooser.setAcceptAllFileFilterUsed(false);

      int userSelection = fileChooser.showOpenDialog(Steganographer.this);
      if (userSelection == JFileChooser.APPROVE_OPTION) {
        selectedImageFile = fileChooser.getSelectedFile();
        imagePathLabel.setText(selectedImageFile.getName());
      }
      else {
        selectedImageFile = null;
        imagePathLabel.setText("No image selected");
      }
    });

    embedTextInImageButton.addActionListener(e -> {
      if (selectedImageFile == null) {
        JOptionPane.showMessageDialog(this, "Please select an image first.", "Error", JOptionPane.ERROR_MESSAGE);
        return;
      }
      String plainText = textArea.getText();
      char[] passwordChars = passwordField.getPassword();
      String password = new String(passwordChars);

      if (plainText.isEmpty() || password.isEmpty()) {
        JOptionPane.showMessageDialog(this, "Please enter text and a password to embed.", "Error", JOptionPane.ERROR_MESSAGE);
        return;
      }

      try {
        encryptAndEmbedImage(plainText, password, selectedImageFile);
        JOptionPane.showMessageDialog(this,
            "Text encrypted and embedded successfully! New image saved as 'stegano_"
                + selectedImageFile.getName().replaceAll("(?i)\\.(png|jpg|jpeg|gif|bmp)$", "") + ".png" + "' in the same directory.",
            "Success",
            JOptionPane.INFORMATION_MESSAGE);
      }
      catch (Exception ex) {
        JOptionPane.showMessageDialog(this, "Failed to embed text: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        ex.printStackTrace();
      }
      finally {
        java.util.Arrays.fill(passwordChars, ' ');
      }
    });

    extractTextFromImageButton.addActionListener(e -> {
      if (selectedImageFile == null) {
        JOptionPane.showMessageDialog(this, "Please select an image first.", "Error", JOptionPane.ERROR_MESSAGE);
        return;
      }
      char[] passwordChars = passwordField.getPassword();
      String password = new String(passwordChars);

      if (password.isEmpty()) {
        JOptionPane.showMessageDialog(this, "Please enter the password to decrypt.", "Error", JOptionPane.ERROR_MESSAGE);
        return;
      }

      try {
        String extractedDecryptedText = extractAndDecryptImage(selectedImageFile, password);
        textArea.setText(extractedDecryptedText);
        JOptionPane.showMessageDialog(this, "Text extracted and decrypted successfully!", "Success", JOptionPane.INFORMATION_MESSAGE);
      }
      catch (Exception ex) {
        JOptionPane.showMessageDialog(this, "Failed to extract and decrypt text: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        ex.printStackTrace();
      }
      finally {
        java.util.Arrays.fill(passwordChars, ' ');
      }
    });

    // Initialize the UI mode after all components are set up
    updateUIForMode();

    setVisible(true);
  }

  /**
   * Updates the UI components based on the selected mode (Encrypt/Decrypt) from the slider.
   */
  private void updateUIForMode() {
    boolean isDecryptMode = modeSlider.getValue() == 1; // If slider value is 1, it's Decrypt mode

    if (isDecryptMode) {
      encryptTextButton.setEnabled(false);
      decryptTextButton.setEnabled(true);
      embedTextInImageButton.setEnabled(false);
      extractTextFromImageButton.setEnabled(true);
      textArea.setText("Paste encrypted text here or select an image to extract from.");
    }
    else { // Encrypt Mode (slider value is 0)
      encryptTextButton.setEnabled(true);
      decryptTextButton.setEnabled(false);
      embedTextInImageButton.setEnabled(true);
      extractTextFromImageButton.setEnabled(false);
      textArea.setText("Enter plaintext here to encrypt or embed.");
    }
  }


  /**
   * Processes text based on the encryption/decryption mode (for text-only operations).
   *
   * @param isEncrypt true for encryption, false for decryption.
   */
  private void processText(boolean isEncrypt) {
    String input = textArea.getText();
    char[] passwordChars = passwordField.getPassword();
    String password = new String(passwordChars);

    if (input.isEmpty() || password.isEmpty()) {
      JOptionPane.showMessageDialog(this, "Please enter both text and a password.", "Error", JOptionPane.ERROR_MESSAGE);
      return;
    }

    try {
      if (isEncrypt) {
        String encryptedText = encrypt(input, password);
        textArea.setText(encryptedText);
        JOptionPane.showMessageDialog(this, "Text encrypted successfully!", "Success", JOptionPane.INFORMATION_MESSAGE);
      }
      else {
        String decryptedText = decrypt(input, password);
        textArea.setText(decryptedText);
        JOptionPane.showMessageDialog(this, "Text decrypted successfully!", "Success", JOptionPane.INFORMATION_MESSAGE);
      }
    }
    catch (Exception ex) {
      JOptionPane.showMessageDialog(this, "Operation failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
      ex.printStackTrace();
    }
    finally {
      java.util.Arrays.fill(passwordChars, ' ');
    }
  }

  /**
   * Derives a secret key from a password and salt using PBKDF2. This is crucial for securely
   * generating a strong encryption key from a user-provided password.
   *
   * @param password The user's password.
   * @param salt The random salt used to prevent rainbow table attacks.
   * @return A SecretKeySpec suitable for AES encryption.
   * @throws Exception If key derivation fails.
   */
  private SecretKeySpec deriveKey(String password, byte[] salt) throws Exception {
    PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH);
    SecretKeyFactory factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM);
    byte[] keyBytes = factory.generateSecret(spec).getEncoded();
    return new SecretKeySpec(keyBytes, "AES");
  }

  /**
   * Encrypts a plaintext string using AES/GCM. A unique salt and IV are generated for each
   * encryption, and the output is a Base64-encoded string combining salt, IV, and ciphertext,
   * separated by colons.
   *
   * @param plainText The text to encrypt.
   * @param password The password used to derive the encryption key.
   * @return The Base64-encoded encrypted string (salt:iv:ciphertext).
   * @throws Exception If encryption fails.
   */
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

  /**
   * Decrypts an encrypted string using AES/GCM. The input string is expected to be in the format
   * salt:iv:ciphertext (Base64 encoded).
   *
   * @param encryptedText The Base64-encoded encrypted string.
   * @param password The password used to derive the decryption key.
   * @return The original plaintext string.
   * @throws Exception If decryption fails (e.g., wrong password, tampered data, invalid format).
   */
  private String decrypt(String encryptedText, String password) throws Exception {
    // 1. Parse Salt, IV, and Ciphertext from the input string
    String[] parts = encryptedText.split(":");
    if (parts.length != 3) {
      throw new IllegalArgumentException("Invalid encrypted text format. Expected salt:iv:ciphertext.");
    }

    byte[] salt = Base64.getDecoder().decode(parts[0]);
    byte[] iv = Base64.getDecoder().decode(parts[1]);
    byte[] cipherText = Base64.getDecoder().decode(parts[2]);

    // 2. Derive Key (using the provided salt from the encrypted text)
    SecretKeySpec key = deriveKey(password, salt);

    // 3. Initialize Cipher
    Cipher cipher = Cipher.getInstance(ALGORITHM);
    GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
    cipher.init(Cipher.DECRYPT_MODE, key, gcmParameterSpec);

    // 4. Decrypt
    byte[] plainTextBytes = cipher.doFinal(cipherText);

    return new String(plainTextBytes, StandardCharsets.UTF_8);
  }

  /**
   * Encrypts the provided text and embeds the resulting encrypted bytes into the given image. The
   * modified image is then saved with a "stegano_" prefix.
   *
   * @param plainText The text to encrypt and embed.
   * @param password The password for encryption.
   * @param originalImageFile The image file to embed the text into.
   * @throws Exception If encryption, embedding, or image saving fails.
   */
  private void encryptAndEmbedImage(String plainText, String password, File originalImageFile) throws Exception {
    String encryptedDataString = encrypt(plainText, password);
    byte[] dataToEmbed = encryptedDataString.getBytes(StandardCharsets.UTF_8);

    BufferedImage originalImage = ImageIO.read(originalImageFile);
    if (originalImage == null) {
      throw new IOException("Could not read image file or unsupported format. Please select a valid image file.");
    }
    if (originalImage.getType() != BufferedImage.TYPE_INT_ARGB && originalImage.getType() != BufferedImage.TYPE_INT_RGB) {
      // Convert to a compatible type if necessary, to ensure pixel manipulation works as expected
      BufferedImage newImage = new BufferedImage(originalImage.getWidth(), originalImage.getHeight(), BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = newImage.createGraphics();
      g.drawImage(originalImage, 0, 0, null);
      g.dispose();
      originalImage = newImage;
    }

    // Add 4 bytes for data length at the beginning of the data to embed
    byte[] dataWithLength = new byte[dataToEmbed.length + 4];
    System.arraycopy(intToBytes(dataToEmbed.length), 0, dataWithLength, 0, 4);
    System.arraycopy(dataToEmbed, 0, dataWithLength, 4, dataToEmbed.length);

    if (!canEmbedData(originalImage, dataWithLength.length)) {
      throw new IllegalArgumentException("Image is too small to embed all the data. Requires " + dataWithLength.length + " bytes.");
    }

    BufferedImage steganographicImage = embedBytes(originalImage, dataWithLength);
    // Ensure the output is always a PNG to preserve LSB changes
    File outputFile = new File(originalImageFile.getParent(),
        "stegano_" + originalImageFile.getName().replaceAll("(?i)\\.(png|jpg|jpeg|gif|bmp)$", "") + ".png");
    ImageIO.write(steganographicImage, "png", outputFile); // Save as PNG to preserve LSB changes
  }

  /**
   * Extracts embedded data from the given image and then decrypts it.
   *
   * @param imageWithEmbeddedDataFile The image file containing the embedded encrypted text.
   * @param password The password for decryption.
   * @return The original plaintext string.
   * @throws Exception If extraction, decryption, or image reading fails.
   */
  private String extractAndDecryptImage(File imageWithEmbeddedDataFile, String password) throws Exception {
    BufferedImage steganographicImage = ImageIO.read(imageWithEmbeddedDataFile);
    if (steganographicImage == null) {
      throw new IOException("Could not read image file or unsupported format. Please select a valid image file.");
    }
    if (steganographicImage.getType() != BufferedImage.TYPE_INT_ARGB && steganographicImage.getType() != BufferedImage.TYPE_INT_RGB) {
      // Convert to a compatible type if necessary for extraction
      BufferedImage newImage =
          new BufferedImage(steganographicImage.getWidth(), steganographicImage.getHeight(), BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = newImage.createGraphics();
      g.drawImage(steganographicImage, 0, 0, null);
      g.dispose();
      steganographicImage = newImage;
    }

    byte[] extractedData = extractBytes(steganographicImage);

    // First 4 bytes are the length of the actual message
    byte[] lengthBytes = new byte[4];
    System.arraycopy(extractedData, 0, lengthBytes, 0, 4);
    int dataLength = bytesToInt(lengthBytes);

    if (dataLength < 0 || dataLength > (extractedData.length - 4)) {
      throw new IllegalArgumentException("Invalid data length extracted from image. Possible corruption or no hidden message.");
    }

    byte[] encryptedDataBytes = new byte[dataLength];
    System.arraycopy(extractedData, 4, encryptedDataBytes, 0, dataLength);

    String encryptedDataString = new String(encryptedDataBytes, StandardCharsets.UTF_8);
    return decrypt(encryptedDataString, password);
  }

  /**
   * Checks if the image has enough capacity to embed the given number of bytes. Each pixel has 3
   * color channels (R, G, B), and each channel can store 1 bit. So, 3 bits per pixel are available
   * for embedding.
   *
   * @param image The BufferedImage to check.
   * @param dataLength The number of bytes to embed.
   * @return true if the image can hold the data, false otherwise.
   */
  private boolean canEmbedData(BufferedImage image, int dataLength) {
    long totalPixels = (long) image.getWidth() * image.getHeight();
    // Each pixel can store 3 bits (R, G, B LSBs)
    long totalBitsCapacity = totalPixels * 3;
    // Data length in bits
    long dataBits = (long) dataLength * 8;
    return dataBits <= totalBitsCapacity;
  }


  /**
   * Embeds a byte array into the LSBs of a BufferedImage's pixel data. This method modifies the image
   * by changing the least significant bit of each red, green, and blue color component to store the
   * data.
   *
   * @param originalImage The image to embed data into.
   * @param data The byte array to embed.
   * @return A new BufferedImage with the data embedded.
   */
  private BufferedImage embedBytes(BufferedImage originalImage, byte[] data) {
    BufferedImage newImage = new BufferedImage(originalImage.getWidth(), originalImage.getHeight(), originalImage.getType());
    Graphics2D g = newImage.createGraphics();
    g.drawImage(originalImage, 0, 0, null);
    g.dispose(); // Ensure graphics context is released

    int dataIndex = 0;
    int bitIndex = 0; // Current bit position within the current byte
    int currentByte = 0; // The byte we are currently embedding

    // Iterate over pixels
    for (int y = 0; y < newImage.getHeight(); y++) {
      for (int x = 0; x < newImage.getWidth(); x++) {
        if (dataIndex < data.length) { // Only embed if there's data left
          int pixel = newImage.getRGB(x, y);

          int alpha = (pixel >> 24) & 0xFF;
          int red = (pixel >> 16) & 0xFF;
          int green = (pixel >> 8) & 0xFF;
          int blue = pixel & 0xFF;

          if (bitIndex == 0) { // Get the next byte to embed
            currentByte = data[dataIndex];
          }

          // Embed into Red channel LSB
          red = (red & 0xFE) | ((currentByte >> (7 - bitIndex)) & 0x01);
          bitIndex++;
          if (bitIndex == 8 && dataIndex < data.length - 1) { // Move to next byte if current byte is fully embedded
            bitIndex = 0;
            dataIndex++;
          }
          else if (bitIndex == 8 && dataIndex == data.length - 1) { // If last byte is fully embedded
            // Don't increment dataIndex, we're done with this byte
          }


          // Embed into Green channel LSB
          if (dataIndex < data.length) { // Check if we still have data
            if (bitIndex == 0) {
              currentByte = data[dataIndex];
            }
            green = (green & 0xFE) | ((currentByte >> (7 - bitIndex)) & 0x01);
            bitIndex++;
            if (bitIndex == 8 && dataIndex < data.length - 1) {
              bitIndex = 0;
              dataIndex++;
            }
            else if (bitIndex == 8 && dataIndex == data.length - 1) {
              // Don't increment dataIndex
            }
          }
          else { // No more data, keep original green LSB
            green = (green & 0xFE); // Ensure LSB is 0 if no more data, or keep it as is
          }

          // Embed into Blue channel LSB
          if (dataIndex < data.length) { // Check if we still have data
            if (bitIndex == 0) {
              currentByte = data[dataIndex];
            }
            blue = (blue & 0xFE) | ((currentByte >> (7 - bitIndex)) & 0x01);
            bitIndex++;
            if (bitIndex == 8) { // If a byte is fully embedded (all 8 bits)
              bitIndex = 0;
              dataIndex++;
            }
          }
          else { // No more data, keep original blue LSB
            blue = (blue & 0xFE); // Ensure LSB is 0 if no more data, or keep it as is
          }

          // Reconstruct the pixel and set it
          int newPixel = (alpha << 24) | (red << 16) | (green << 8) | blue;
          newImage.setRGB(x, y, newPixel);
        }
        else {
          // No more data to embed, break out of loops (or continue to copy remaining pixels)
          break;
        }
      }
      if (dataIndex >= data.length) { // If all data is embedded, we can stop
        break;
      }
    }
    return newImage;
  }


  /**
   * Extracts a byte array from the LSBs of a BufferedImage's pixel data. It reads bits from the red,
   * green, and blue channels' least significant bits until the entire message (whose length is
   * prefixed in the first 4 bytes) is reconstructed.
   *
   * @param image The BufferedImage to extract data from.
   * @return The extracted byte array.
   */
  private byte[] extractBytes(BufferedImage image) {
    // We'll read more than we need initially, then trim it based on the actual length.
    // Assume maximum possible embedded data size for initial buffer, or read until capacity.
    // A safer approach for initial read is to determine length first.
    // For simplicity, let's read based on image capacity first, then trim.
    // Max bytes an image can hold: (width * height * 3) / 8
    int maxBytesPossible = (image.getWidth() * image.getHeight() * 3) / 8;
    byte[] extractedRawBytes = new byte[maxBytesPossible];

    int dataIndex = 0;
    int bitIndex = 0;
    byte currentByte = 0;

    for (int y = 0; y < image.getHeight(); y++) {
      for (int x = 0; x < image.getWidth(); x++) {
        int pixel = image.getRGB(x, y);

        int red = (pixel >> 16) & 0xFF;
        int green = (pixel >> 8) & 0xFF;
        int blue = pixel & 0xFF;

        // Extract bit from Red channel LSB
        currentByte = (byte) ((currentByte << 1) | (red & 0x01));
        bitIndex++;
        if (bitIndex == 8) {
          if (dataIndex < extractedRawBytes.length) {
            extractedRawBytes[dataIndex++] = currentByte;
          }
          currentByte = 0;
          bitIndex = 0;
        }

        // Extract bit from Green channel LSB
        currentByte = (byte) ((currentByte << 1) | (green & 0x01));
        bitIndex++;
        if (bitIndex == 8) {
          if (dataIndex < extractedRawBytes.length) {
            extractedRawBytes[dataIndex++] = currentByte;
          }
          currentByte = 0;
          bitIndex = 0;
        }

        // Extract bit from Blue channel LSB
        currentByte = (byte) ((currentByte << 1) | (blue & 0x01));
        bitIndex++;
        if (bitIndex == 8) {
          if (dataIndex < extractedRawBytes.length) {
            extractedRawBytes[dataIndex++] = currentByte;
          }
          currentByte = 0;
          bitIndex = 0;
        }

        // Optimization: If we have read enough to get the length,
        // and we've read the full data based on that length, stop.
        if (dataIndex >= 4) { // We have at least the length bytes
          byte[] lengthPrefix = new byte[4];
          System.arraycopy(extractedRawBytes, 0, lengthPrefix, 0, 4);
          int expectedDataLength = bytesToInt(lengthPrefix);
          // Total data to read will be 4 (for length) + expectedDataLength
          if (dataIndex >= (4 + expectedDataLength)) {
            break; // All necessary data seems to be extracted
          }
        }
      }
      if (dataIndex >= 4) { // Check again after inner loop
        byte[] lengthPrefix = new byte[4];
        System.arraycopy(extractedRawBytes, 0, lengthPrefix, 0, 4);
        int expectedDataLength = bytesToInt(lengthPrefix);
        if (dataIndex >= (4 + expectedDataLength)) {
          break;
        }
      }
    }

    // Now, trim the extractedRawBytes to the actual length
    // First 4 bytes indicate the length of the *actual message* (excluding the 4-byte length prefix)
    if (dataIndex < 4) { // Not even enough bytes for the length prefix
      throw new IllegalArgumentException("Not enough data extracted to determine message length. Image may not contain hidden data.");
    }

    byte[] lengthPrefix = new byte[4];
    System.arraycopy(extractedRawBytes, 0, lengthPrefix, 0, 4);
    int actualMessageLength = bytesToInt(lengthPrefix);

    // Total bytes needed: 4 (for length) + actualMessageLength
    int totalBytesNeeded = 4 + actualMessageLength;

    if (dataIndex < totalBytesNeeded) {
      throw new IllegalArgumentException("Incomplete message extracted. Expected " + totalBytesNeeded + " bytes but got " + dataIndex
          + ". Image may be corrupted or not contain the full message.");
    }

    byte[] finalExtractedData = new byte[totalBytesNeeded];
    System.arraycopy(extractedRawBytes, 0, finalExtractedData, 0, totalBytesNeeded);

    return finalExtractedData;
  }


  /**
   * Converts an integer to a 4-byte array.
   *
   * @param value The integer to convert.
   * @return A 4-byte array representing the integer.
   */
  private byte[] intToBytes(int value) {
    return new byte[] {
        (byte) (value >> 24),
        (byte) (value >> 16),
        (byte) (value >> 8),
        (byte) value
    };
  }

  /**
   * Converts a 4-byte array to an integer.
   *
   * @param bytes The 4-byte array.
   * @return The integer represented by the bytes.
   */
  private int bytesToInt(byte[] bytes) {
    return ((bytes[0] & 0xFF) << 24) |
        ((bytes[1] & 0xFF) << 16) |
        ((bytes[2] & 0xFF) << 8) |
        (bytes[3] & 0xFF);
  }

  public static void main(String[] args) {
    SwingUtilities.invokeLater(Steganographer::new);
  }
}
