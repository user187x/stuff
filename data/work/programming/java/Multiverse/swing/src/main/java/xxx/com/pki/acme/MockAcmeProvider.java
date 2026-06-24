package xxx.com.pki.acme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.net.URL;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;

public class MockAcmeProvider extends JFrame {

 private JTextField domainField;
 private JTextArea csrTextArea;
 private JTextArea responseTextArea;
 private JButton submitBtn;
 private JLabel animationLabel;

 public MockAcmeProvider() {
  setTitle("Mock ACME Provider (The Certificate Factory)");
  setSize(650, 600);
  setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
  setLocationRelativeTo(null);

  JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
  mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

  // --- Top Panel: CSR Generation ---
  JPanel topPanel = new JPanel(new BorderLayout(5, 5));
  topPanel.setBorder(BorderFactory.createTitledBorder("1. The Raw Material: Generate CSR"));

  JPanel inputPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
  inputPanel.add(new JLabel("Domain Name:"));
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

  // --- Bottom Panel: ACME Submission & Animation ---
  JPanel bottomPanel = new JPanel(new BorderLayout(5, 5));
  bottomPanel.setBorder(BorderFactory.createTitledBorder("2. The Factory Machine: Stamp EAB Credentials"));

  submitBtn = new JButton("Submit CSR to the Machine");
  submitBtn.setBackground(new Color(40, 167, 69));
  submitBtn.setForeground(Color.WHITE);
  submitBtn.setOpaque(true);
  submitBtn.setSize(40, 40);
  submitBtn.setBorderPainted(false);
  submitBtn.setFont(new Font("SansSerif", Font.BOLD, 14));

  URL imgUrl = this.getClass().getResource("/gif/gears-2.gif"); // Note the leading slash

  if (imgUrl != null) {
   ImageIcon gearIcon = new ImageIcon(imgUrl);
   animationLabel = new JLabel("Processing CSR... Stamping Keys...", gearIcon, JLabel.CENTER);
  } else {
   System.err.println("Warning: Could not find /gif/gears-2.gif on classpath.");
   // Fallback to text-only if the image is missing
   animationLabel = new JLabel("Processing CSR... Stamping Keys...", JLabel.CENTER);
  }

  animationLabel.setFont(new Font("SansSerif", Font.ITALIC, 14));
  animationLabel.setForeground(new Color(100, 100, 100));
  animationLabel.setVisible(false); // Hidden until button is clicke

  animationLabel.setFont(new Font("SansSerif", Font.ITALIC, 14));
  animationLabel.setForeground(new Color(100, 100, 100));
  animationLabel.setVisible(false); // Hidden until button is clicked

  JPanel middleActionPanel = new JPanel(new BorderLayout());
  middleActionPanel.add(submitBtn, BorderLayout.NORTH);
  middleActionPanel.add(animationLabel, BorderLayout.CENTER);
  middleActionPanel.setPreferredSize(new Dimension(600, 120)); // Made slightly taller to fit the GIF comfortably

  responseTextArea = new JTextArea(10, 40);
  responseTextArea.setEditable(false);
  responseTextArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
  JScrollPane responseScroll = new JScrollPane(responseTextArea);

  bottomPanel.add(middleActionPanel, BorderLayout.NORTH);
  bottomPanel.add(responseScroll, BorderLayout.CENTER);

  mainPanel.add(topPanel, BorderLayout.NORTH);
  mainPanel.add(bottomPanel, BorderLayout.CENTER);
  add(mainPanel);

  // --- Event Listeners ---
  generateCsrBtn.addActionListener(e -> generateMockCSR());
  submitBtn.addActionListener(e -> processAcmeWithAnimation());
 }

 private void generateMockCSR() {
  String domain = domainField.getText().trim();
  if (domain.isEmpty()) {
   JOptionPane.showMessageDialog(this, "Please enter a domain name.", "Error", JOptionPane.ERROR_MESSAGE);
   return;
  }

  SecureRandom random = new SecureRandom();
  byte[] fakeKeyData = new byte[256];
  random.nextBytes(fakeKeyData);
  String base64Data = Base64.getEncoder().encodeToString(fakeKeyData);

  StringBuilder pem = new StringBuilder();
  pem.append("-----BEGIN CERTIFICATE REQUEST-----\n");
  pem.append("Subject: CN=").append(domain).append("\n");

  for (int i = 0; i < base64Data.length(); i += 64) {
   pem.append(base64Data, i, Math.min(i + 64, base64Data.length())).append("\n");
  }
  pem.append("-----END CERTIFICATE REQUEST-----");

  csrTextArea.setText(pem.toString());
  responseTextArea.setText("");
 }

 private void processAcmeWithAnimation() {
  if (csrTextArea.getText().isEmpty()) {
   JOptionPane.showMessageDialog(this, "Please generate a CSR first so the machine has raw material!", "Missing CSR", JOptionPane.WARNING_MESSAGE);
   return;
  }

  // 1. Prepare UI for processing (Show the gears.gif)
  submitBtn.setEnabled(false);
  submitBtn.setText("Machine is running...");
  responseTextArea.setText("");
  animationLabel.setVisible(true);

  // 2. Use SwingWorker to prevent UI freezing
  SwingWorker<String, Void> worker = new SwingWorker<String, Void>() {
   @Override
   protected String doInBackground() throws Exception {
    // Keep the animation visible for 3 seconds
    Thread.sleep(3000);

    // Generate Mock KID
    String kid = UUID.randomUUID().toString();

    // Generate Mock HMAC Key (Base64URL encoded)
    SecureRandom random = new SecureRandom();
    byte[] hmacBytes = new byte[32];
    random.nextBytes(hmacBytes);
    String hmac = Base64.getUrlEncoder().withoutPadding().encodeToString(hmacBytes);

    // Format the JSON Response
    return String.format(
        "{\n" +
            "  \"status\": \"success\",\n" +
            "  \"message\": \"CSR Successfully Stamped! EAB Credentials Generated.\",\n" +
            "  \"eab_credentials\": {\n" +
            "    \"kid\": \"%s\",\n" +
            "    \"hmac_key\": \"%s\"\n" +
            "  },\n" +
            "  \"directory\": \"https://mock-acme.local/directory\"\n" +
            "}", kid, hmac);
   }

   @Override
   protected void done() {
    // 3. Output is ready, hide the gears.gif
    try {
     String result = get();
     responseTextArea.setText(result);
    } catch (Exception ex) {
     responseTextArea.setText("Error in the machine: " + ex.getMessage());
    } finally {
     // Reset UI
     animationLabel.setVisible(false);
     submitBtn.setEnabled(true);
     submitBtn.setText("Submit CSR to the Machine");
    }
   }
  };

  worker.execute();
 }

 public static void main(String[] args) {
  try {
   UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
  } catch (Exception e) {
   e.printStackTrace();
  }

  SwingUtilities.invokeLater(() -> {
   new MockAcmeProvider().setVisible(true);
  });
 }
}