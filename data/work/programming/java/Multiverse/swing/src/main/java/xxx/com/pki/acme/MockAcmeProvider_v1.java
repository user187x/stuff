package xxx.com.pki.acme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

public class MockAcmeProvider_v1 extends JFrame {

 private JTextField domainField;
 private JTextArea csrTextArea;
 private JTextArea responseTextArea;

 public MockAcmeProvider_v1() {
  setTitle("Mock ACME Provider (Educational Tool)");
  setSize(600, 500);
  setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
  setLocationRelativeTo(null);

  // Main Layout
  JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
  mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

  // --- Top Panel: CSR Generation ---
  JPanel topPanel = new JPanel(new BorderLayout(5, 5));
  topPanel.setBorder(BorderFactory.createTitledBorder("1. Generate CSR (Mock)"));

  JPanel inputPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
  inputPanel.add(new JLabel("Common Name (Domain):"));
  domainField = new JTextField("example.com", 20);
  inputPanel.add(domainField);

  JButton generateCsrBtn = new JButton("Generate CSR");
  inputPanel.add(generateCsrBtn);

  csrTextArea = new JTextArea(6, 40);
  csrTextArea.setEditable(false);
  csrTextArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
  JScrollPane csrScroll = new JScrollPane(csrTextArea);

  topPanel.add(inputPanel, BorderLayout.NORTH);
  topPanel.add(csrScroll, BorderLayout.CENTER);

  // --- Bottom Panel: ACME Submission & EAB Response ---
  JPanel bottomPanel = new JPanel(new BorderLayout(5, 5));
  bottomPanel.setBorder(BorderFactory.createTitledBorder("2. Submit to Mock ACME CA"));

  JButton submitBtn = new JButton("Submit CSR & Request EAB Credentials");
  submitBtn.setBackground(new Color(40, 167, 69));
  submitBtn.setForeground(Color.WHITE);
  submitBtn.setOpaque(true);
  submitBtn.setBorderPainted(false);

  responseTextArea = new JTextArea(8, 40);
  responseTextArea.setEditable(false);
  responseTextArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
  JScrollPane responseScroll = new JScrollPane(responseTextArea);

  bottomPanel.add(submitBtn, BorderLayout.NORTH);
  bottomPanel.add(responseScroll, BorderLayout.CENTER);

  // Add to main frame
  mainPanel.add(topPanel, BorderLayout.NORTH);
  mainPanel.add(bottomPanel, BorderLayout.CENTER);
  add(mainPanel);

  // --- Event Listeners ---
  generateCsrBtn.addActionListener(e -> generateMockCSR());
  submitBtn.addActionListener(e -> submitMockACME());
 }

 private void generateMockCSR() {
  String domain = domainField.getText().trim();
  if (domain.isEmpty()) {
   JOptionPane.showMessageDialog(this, "Please enter a domain name.", "Error", JOptionPane.ERROR_MESSAGE);
   return;
  }

  // Generate a realistic-looking (but cryptographically fake) Base64 string for the CSR
  SecureRandom random = new SecureRandom();
  byte[] fakeKeyData = new byte[256];
  random.nextBytes(fakeKeyData);
  String base64Data = Base64.getEncoder().encodeToString(fakeKeyData);

  // Format as PEM
  StringBuilder pem = new StringBuilder();
  pem.append("-----BEGIN CERTIFICATE REQUEST-----\n");
  pem.append("Subject: CN=").append(domain).append("\n"); // Educational metadata

  // Chunk the Base64 string to 64 characters per line
  for (int i = 0; i < base64Data.length(); i += 64) {
   pem.append(base64Data, i, Math.min(i + 64, base64Data.length())).append("\n");
  }
  pem.append("-----END CERTIFICATE REQUEST-----");

  csrTextArea.setText(pem.toString());
  responseTextArea.setText(""); // Clear previous responses
 }

 private void submitMockACME() {
  String csr = csrTextArea.getText();
  if (csr.isEmpty()) {
   JOptionPane.showMessageDialog(this, "Please generate a CSR first.", "Error", JOptionPane.WARNING_MESSAGE);
   return;
  }

  // 1. Generate Mock KID (Usually an alphanumeric string or UUID)
  String kid = UUID.randomUUID().toString();

  // 2. Generate Mock HMAC Key (Usually a 256-bit / 32-byte secure random value, Base64URL encoded)
  SecureRandom random = new SecureRandom();
  byte[] hmacBytes = new byte[32];
  random.nextBytes(hmacBytes);
  String hmac = Base64.getUrlEncoder().withoutPadding().encodeToString(hmacBytes);

  // 3. Format the JSON Response
  String jsonResponse = String.format(
      "{\n" +
          "  \"status\": \"pending\",\n" +
          "  \"message\": \"CSR Received. External Account Binding (EAB) required.\",\n" +
          "  \"eab_credentials\": {\n" +
          "    \"kid\": \"%s\",\n" +
          "    \"hmac_key\": \"%s\"\n" +
          "  },\n" +
          "  \"directory\": \"https://mock-acme.local/directory\"\n" +
          "}", kid, hmac);

  responseTextArea.setText(jsonResponse);
 }

 public static void main(String[] args) {
  // Set System Look and Feel for better UI
  try {
   UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
  } catch (Exception e) {
   e.printStackTrace();
  }

  SwingUtilities.invokeLater(() -> {
   new MockAcmeProvider_v1().setVisible(true);
  });
 }
}