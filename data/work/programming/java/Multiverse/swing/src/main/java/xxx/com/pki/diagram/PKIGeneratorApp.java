package xxx.com.pki.diagram;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.FlatDarculaLaf;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;

import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.geom.AffineTransform;
import java.io.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.List;

/**
 * The main graphical user interface for the PKI File Generator. It presents a clear, step-by-step
 * flow for creating various PKI components from a self-signed P12 file.
 */
public class PKIGeneratorApp extends JFrame {

  private final PKIGenerator pkiGenerator = new PKIGenerator();
  private final AnimatedPanel animatedPanel;

  // --- UI Panels ---
  private FilePanel p12Panel;
  private FilePanel privateKeyPanel, certificatePanel, caBundlePanel;
  private FilePanel publicKeyPanel, csrPanel;

  // --- In-memory crypto objects ---
  private byte[] p12Bytes;
  private PrivateKey privateKey;
  private X509Certificate certificate;
  private PublicKey publicKey;

  // --- UI Colors ---
  private static final Color INACTIVE_COLOR = new Color(50, 50, 50);
  private static final Color ACTIVE_COLOR_P12 = new Color(0, 188, 212); // Cyan
  private static final Color ACTIVE_COLOR_CERTS = new Color(76, 175, 80); // Green
  private static final Color ACTIVE_COLOR_KEYS = new Color(255, 193, 7); // Amber
  private static final Color ACTIVE_COLOR_CSR = new Color(156, 39, 176); // Purple

  public static void main(String[] args)
      throws UnsupportedLookAndFeelException,
          ClassNotFoundException,
          InstantiationException,
          IllegalAccessException {
    FlatDarculaLaf.setup();
    SwingUtilities.invokeLater(PKIGeneratorApp::new);
  }

  public PKIGeneratorApp() {
    setTitle("Interactive PKI File Generator (Drop P12 file here)");
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    setSize(1000, 600); // Reduced height slightly
    setLocationRelativeTo(null);
    setLayout(null);

    animatedPanel = new AnimatedPanel();
    animatedPanel.setBounds(0, 0, 1000, 600);
    // Add Drag and Drop functionality to the main panel
    new DropTarget(animatedPanel, new P12DropListener());
    getContentPane().add(animatedPanel);

    initComponents();
    setVisible(true);
  }

  private void initComponents() {
    // Tier 0: The root P12 file
    p12Panel = new FilePanel("P12 Certificate", INACTIVE_COLOR, 180);
    p12Panel.setBounds(410, 40, 180, 50); // Adjusted Y
    p12Panel.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mouseClicked(MouseEvent e) {
            if (!p12Panel.isReady()) generateP12();
          }
        });
    add(p12Panel);

    // Tier 1: Directly derived from P12
    certificatePanel = new FilePanel("Certificate", INACTIVE_COLOR, 150);
    certificatePanel.setBounds(225, 150, 150, 50); // Compacted X
    add(certificatePanel);

    caBundlePanel = new FilePanel("CA-Bundle", INACTIVE_COLOR, 150);
    caBundlePanel.setBounds(425, 150, 150, 50); // Centered
    add(caBundlePanel);

    privateKeyPanel = new FilePanel("Private Key", INACTIVE_COLOR, 150);
    privateKeyPanel.setBounds(625, 150, 150, 50); // Compacted X
    add(privateKeyPanel);

    // Tier 2: Derived from Private Key
    publicKeyPanel = new FilePanel("Public Key", INACTIVE_COLOR, 150);
    publicKeyPanel.setBounds(625, 260, 150, 50); // Compacted Y
    add(publicKeyPanel);

    // Tier 3: Derived from Public Key
    csrPanel = new FilePanel("CSR", INACTIVE_COLOR, 150);
    csrPanel.setBounds(625, 370, 150, 50); // Compacted Y
    add(csrPanel);
  }

  /**
   * Handles the successful loading of PKI components, either from generation or a file.
   *
   * @param pkiData A map containing the loaded crypto objects.
   * @param p12FileName The name to display in the P12 panel.
   */
  private void onP12Loaded(Map<String, Object> pkiData, String p12FileName) {
    p12Bytes = (byte[]) pkiData.get("p12Bytes");
    privateKey = (PrivateKey) pkiData.get("privateKey");
    certificate = (X509Certificate) pkiData.get("certificate");
    publicKey = certificate.getPublicKey();

    p12Panel.setBackground(ACTIVE_COLOR_P12);
    p12Panel.setReady(true);
    p12Panel.setText(p12FileName);
    // Clear old listeners before adding a new one
    for (MouseListener ml : p12Panel.getMouseListeners()) {
      if (ml instanceof FileDownloadListener) {
        p12Panel.removeMouseListener(ml);
      }
    }
    p12Panel.addMouseListener(
        new FileDownloadListener(Base64.getEncoder().encode(p12Bytes), "loaded.p12"));
    animateTier1();
  }

  private void generateP12() {
    p12Panel.setText("Generating...");
    new SwingWorker<Map<String, Object>, Void>() {
      @Override
      protected Map<String, Object> doInBackground() throws Exception {
        return pkiGenerator.createSelfSignedP12();
      }

      @Override
      protected void done() {
        try {
          Map<String, Object> p12data = get();
          onP12Loaded(p12data, "P12 (Self-Signed)");
        } catch (Exception e) {
          p12Panel.setText("Error!");
          handleError("Failed to generate P12 file", e);
        }
      }
    }.execute();
  }

  private void loadP12FromFile(File p12File) {
    JPasswordField passwordField = new JPasswordField(20);
    int option =
        JOptionPane.showConfirmDialog(
            this,
            passwordField,
            "Enter Password for " + p12File.getName(),
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE);

    if (option != JOptionPane.OK_OPTION) return;
    char[] password = passwordField.getPassword();
    p12Panel.setText("Loading...");

    new SwingWorker<Map<String, Object>, Void>() {
      @Override
      protected Map<String, Object> doInBackground() throws Exception {
        byte[] fileBytes = Files.readAllBytes(p12File.toPath());
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(new ByteArrayInputStream(fileBytes), password);

        Enumeration<String> aliases = keyStore.aliases();
        String alias = null;
        while (aliases.hasMoreElements()) {
          String currentAlias = aliases.nextElement();
          if (keyStore.isKeyEntry(currentAlias)) {
            alias = currentAlias;
            break;
          }
        }

        if (alias == null) {
          throw new KeyStoreException("Could not find a private key entry in the P12 file.");
        }

        PrivateKey loadedKey = (PrivateKey) keyStore.getKey(alias, password);
        X509Certificate loadedCert = (X509Certificate) keyStore.getCertificate(alias);

        Map<String, Object> result = new HashMap<>();
        result.put("p12Bytes", fileBytes);
        result.put("privateKey", loadedKey);
        result.put("certificate", loadedCert);
        return result;
      }

      @Override
      protected void done() {
        try {
          Map<String, Object> p12data = get();
          onP12Loaded(p12data, p12File.getName());
        } catch (Exception e) {
          p12Panel.setText("Error Loading!");
          handleError("Failed to load P12 file", e);
        }
      }
    }.execute();
  }

  private void animateTier1() {
    animatedPanel.clearArrows();
    Point p12Exit = getExitPoint(p12Panel);

    List<AnimatedPanel.ArrowSpec> arrows =
        Arrays.asList(
            new AnimatedPanel.ArrowSpec(p12Exit, getEntryPoint(privateKeyPanel)),
            new AnimatedPanel.ArrowSpec(p12Exit, getEntryPoint(certificatePanel)),
            new AnimatedPanel.ArrowSpec(p12Exit, getEntryPoint(caBundlePanel)));
    animatedPanel.animateArrows(arrows, this::deriveAndActivateTier1);
  }

  private void deriveAndActivateTier1() {
    try {
      activatePanel(
          privateKeyPanel,
          ACTIVE_COLOR_KEYS,
          "private.key.pem",
          Base64.getEncoder().encode(pkiGenerator.toPem(privateKey).getBytes()));
      activatePanel(
          certificatePanel,
          ACTIVE_COLOR_CERTS,
          "certificate.pem",
          Base64.getEncoder().encode(pkiGenerator.toPem(certificate).getBytes()));
      byte[] pemBundleBytes = pkiGenerator.createPemBundle(privateKey, certificate);
      activatePanel(
          caBundlePanel,
          ACTIVE_COLOR_P12,
          "bundle.pem",
          Base64.getEncoder().encode(pemBundleBytes));

      animateTier2();
    } catch (Exception e) {
      handleError("Failed to derive files from P12", e);
    }
  }

  private void animateTier2() {
    Point privateKeyExit = getExitPoint(privateKeyPanel);
    List<AnimatedPanel.ArrowSpec> arrows =
        Collections.singletonList(
            new AnimatedPanel.ArrowSpec(privateKeyExit, getEntryPoint(publicKeyPanel)));
    animatedPanel.animateArrows(arrows, this::deriveAndActivateTier2);
  }

  private void deriveAndActivateTier2() {
    try {
      activatePanel(
          publicKeyPanel,
          ACTIVE_COLOR_KEYS,
          "public.key.pem",
          Base64.getEncoder().encode(pkiGenerator.toPem(publicKey).getBytes()));
      animateTier3();
    } catch (Exception e) {
      handleError("Failed to derive Public Key", e);
    }
  }

  private void animateTier3() {
    Point publicKeyExit = getExitPoint(publicKeyPanel);
    List<AnimatedPanel.ArrowSpec> arrows =
        Collections.singletonList(
            new AnimatedPanel.ArrowSpec(publicKeyExit, getEntryPoint(csrPanel)));
    animatedPanel.animateArrows(arrows, this::deriveAndActivateTier3);
  }

  private void deriveAndActivateTier3() {
    try {
      byte[] csrBytes = pkiGenerator.createCsr(privateKey, publicKey, "CN=csr.example.com");
      activatePanel(
          csrPanel, ACTIVE_COLOR_CSR, "request.csr", Base64.getEncoder().encode(csrBytes));
    } catch (Exception e) {
      handleError("Failed to create CSR", e);
    }
  }

  private void activatePanel(FilePanel panel, Color color, String fileName, byte[] data) {
    panel.setBackground(color);
    panel.setReady(true);
    // Clear old listeners before adding a new one
    for (MouseListener ml : panel.getMouseListeners()) {
      if (ml instanceof FileDownloadListener) {
        panel.removeMouseListener(ml);
      }
    }
    panel.addMouseListener(new FileDownloadListener(data, fileName));
  }

  private void handleError(String message, Exception e) {
    e.printStackTrace();
    // Unwrap the underlying cause from InvocationTargetException if it's a SwingWorker error
    Throwable cause = (e.getCause() != null) ? e.getCause() : e;
    JOptionPane.showMessageDialog(
        this, message + ": " + cause.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
  }

  private Point getEntryPoint(Component c) {
    return new Point(c.getX() + c.getWidth() / 2, c.getY());
  }

  private Point getExitPoint(Component c) {
    return new Point(c.getX() + c.getWidth() / 2, c.getY() + c.getHeight());
  }

  /** Listener class to handle drag-and-drop functionality for P12 files. */
  private class P12DropListener extends DropTargetAdapter {
    @Override
    public void dragEnter(DropTargetDragEvent dtde) {
      if (dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
        dtde.acceptDrag(DnDConstants.ACTION_COPY);
      } else {
        dtde.rejectDrag();
      }
    }

    @Override
    public void drop(DropTargetDropEvent dtde) {
      try {
        dtde.acceptDrop(DnDConstants.ACTION_COPY);
        Transferable transferable = dtde.getTransferable();
        if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
          List<File> files =
              (List<File>) transferable.getTransferData(DataFlavor.javaFileListFlavor);
          if (files != null && files.size() == 1) {
            loadP12FromFile(files.get(0));
            dtde.dropComplete(true);
          }
        }
      } catch (Exception e) {
        handleError("Could not handle dropped file", e);
        dtde.dropComplete(false);
      }
    }
  }

  private static class FilePanel extends JPanel {
    private boolean isReady = false;
    private final JLabel label;

    FilePanel(String text, Color bgColor, int width) {
      putClientProperty(FlatClientProperties.STYLE, "arc: 20");
      setBackground(bgColor);
      setLayout(new GridBagLayout());
      label =
          new JLabel(
              "<html><div style='text-align: center; width: "
                  + (width - 20)
                  + "px;'>"
                  + text
                  + "</div></html>");
      label.setFont(new Font("Inter", Font.BOLD, 12));
      add(label);
    }

    public void setText(String text) {
      label.setText("<html><div style='text-align: center;'>" + text + "</div></html>");
    }

    public boolean isReady() {
      return isReady;
    }

    public void setReady(boolean ready) {
      this.isReady = ready;
      setCursor(ready ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
      setToolTipText(ready ? "Click to download (Base64)" : null);
    }
  }

  private class FileDownloadListener extends MouseAdapter {
    private final byte[] fileBytes;
    private final String fileName;

    FileDownloadListener(byte[] fileBytes, String fileName) {
      this.fileBytes = fileBytes;
      this.fileName = fileName;
    }

    @Override
    public void mouseClicked(MouseEvent e) {
      if (!((FilePanel) e.getSource()).isReady()) return;
      // Prevents this from firing if a click was intended for the generation button
      if (e.getSource() == p12Panel && p12Bytes == null) return;

      JFileChooser fileChooser = new JFileChooser();
      fileChooser.setSelectedFile(new File(fileName));
      if (fileChooser.showSaveDialog(PKIGeneratorApp.this) == JFileChooser.APPROVE_OPTION) {
        try (FileOutputStream fos = new FileOutputStream(fileChooser.getSelectedFile())) {
          fos.write(fileBytes);
        } catch (Exception ex) {
          handleError("Error saving file", ex);
        }
      }
    }
  }

  public static class AnimatedPanel extends JPanel {
    private final List<Arrow> allArrows = new ArrayList<>();
    private Timer animationTimer;

    public record ArrowSpec(Point start, Point end) {}

    public AnimatedPanel() {
      setOpaque(false);
    }

    public void clearArrows() {
      if (animationTimer != null && animationTimer.isRunning()) {
        animationTimer.stop();
      }
      allArrows.clear();
      repaint();
    }

    public void animateArrows(List<ArrowSpec> specs, Runnable onAllFinished) {
      if (animationTimer != null && animationTimer.isRunning()) {
        // Stop previous timer to start a new batch animation.
        animationTimer.stop();
      }

      // This list holds only the arrows for the current animation cycle.
      List<Arrow> currentBatch = new ArrayList<>();
      specs.forEach(
          spec -> {
            Arrow newArrow = new Arrow(spec.start, spec.end);
            currentBatch.add(newArrow);
            allArrows.add(newArrow); // Add to the master list for drawing.
          });

      animationTimer =
          new Timer(
              20,
              event -> {
                // Update only the arrows in the current batch.
                currentBatch.forEach(Arrow::update);
                repaint(); // Repaint the whole panel, which draws all arrows.

                // Check if this specific batch is finished.
                if (currentBatch.stream().allMatch(Arrow::isFinished)) {
                  animationTimer.stop();
                  if (onAllFinished != null) {
                    SwingUtilities.invokeLater(onAllFinished);
                  }
                }
              });
      animationTimer.start();
    }

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      Graphics2D g2d = (Graphics2D) g.create();
      g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2d.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      // Draw ALL arrows every time.
      allArrows.forEach(arrow -> arrow.draw(g2d));
      g2d.dispose();
    }

    private static class Arrow {
      private final Point start, end;
      private double progress = 0.0;

      Arrow(Point start, Point end) {
        this.start = start;
        this.end = end;
      }

      void update() {
        if (progress < 1.0) progress = Math.min(1.0, progress + 0.015);
      }

      boolean isFinished() {
        return progress >= 1.0;
      }

      void draw(Graphics2D g2d) {
        int currentX = (int) (start.x + (end.x - start.x) * progress);
        int currentY = (int) (start.y + (end.y - start.y) * progress);
        if (!isFinished()) {
          g2d.setPaint(
              new GradientPaint(
                  start.x,
                  start.y,
                  new Color(150, 150, 150),
                  currentX,
                  currentY,
                  new Color(200, 200, 200)));
          g2d.drawLine(start.x, start.y, currentX, currentY);
        } else {
          g2d.setPaint(
              new GradientPaint(
                  start.x,
                  start.y,
                  new Color(150, 150, 150),
                  end.x,
                  end.y,
                  new Color(200, 200, 200)));
          g2d.drawLine(start.x, start.y, end.x, end.y);
          drawArrowHead(g2d, end);
        }
      }

      private void drawArrowHead(Graphics2D g2d, Point p) {
        double angle = Math.atan2(p.y - start.y, p.x - start.x);
        AffineTransform originalTx = g2d.getTransform();
        AffineTransform at = AffineTransform.getTranslateInstance(p.x, p.y);
        at.rotate(angle);
        g2d.setTransform(at);
        g2d.setColor(new Color(200, 200, 200));
        g2d.fillPolygon(new int[] {0, -15, -15}, new int[] {0, -7, 7}, 3);
        g2d.setTransform(originalTx);
      }
    }
  }

  public static class PKIGenerator {
    static {
      Security.addProvider(new BouncyCastleProvider());
    }

    private static final String SIGNATURE_ALGORITHM = "SHA256WithRSA";

    public Map<String, Object> createSelfSignedP12() throws Exception {
      KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA", "BC");
      kpg.initialize(2048);
      KeyPair keyPair = kpg.generateKeyPair();

      X500Name dn = new X500Name("CN=SelfSigned CA, O=PKI Generator, C=US");
      X509Certificate certificate =
          createCertificate(dn, dn.toString(), keyPair.getPublic(), keyPair.getPrivate());

      KeyStore keyStore = KeyStore.getInstance("PKCS12", "BC");
      keyStore.load(null, null);
      keyStore.setKeyEntry(
          "selfsigned",
          keyPair.getPrivate(),
          "password".toCharArray(),
          new Certificate[] {certificate});

      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      keyStore.store(baos, "password".toCharArray());

      Map<String, Object> result = new HashMap<>();
      result.put("p12Bytes", baos.toByteArray());
      result.put("privateKey", keyPair.getPrivate());
      result.put("certificate", certificate);
      return result;
    }

    public byte[] createCsr(PrivateKey privateKey, PublicKey publicKey, String subjectDN)
        throws Exception {
      JcaPKCS10CertificationRequestBuilder p10Builder =
          new JcaPKCS10CertificationRequestBuilder(new X500Name(subjectDN), publicKey);
      JcaContentSignerBuilder csBuilder =
          new JcaContentSignerBuilder(SIGNATURE_ALGORITHM).setProvider("BC");
      PKCS10CertificationRequest csr = p10Builder.build(csBuilder.build(privateKey));
      return toPem(csr).getBytes();
    }

    public byte[] signCsr(PrivateKey caPrivateKey, X509Certificate caCert) throws Exception {
      // In a real scenario, you'd parse a CSR from bytes. Here we generate one for signing.
      // This demonstrates signing a newly generated key pair's CSR with the CA key.
      KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA", "BC");
      kpg.initialize(2048);
      KeyPair newKeyPair = kpg.generateKeyPair();

      X500Name subject = new X500Name("CN=newcert.example.com, O=PKI Generator, C=US");
      X509Certificate newCert =
          createCertificate(
              subject,
              caCert.getSubjectX500Principal().getName(),
              newKeyPair.getPublic(),
              caPrivateKey);
      return toPem(newCert).getBytes();
    }

    private X509Certificate createCertificate(
        X500Name subject, String issuerDN, PublicKey subjectPublicKey, PrivateKey issuerPrivateKey)
        throws Exception {
      Instant now = Instant.now();
      JcaX509v3CertificateBuilder certBuilder =
          new JcaX509v3CertificateBuilder(
              new X500Name(issuerDN),
              BigInteger.valueOf(System.currentTimeMillis()),
              Date.from(now),
              Date.from(now.plus(365, ChronoUnit.DAYS)),
              subject,
              subjectPublicKey);

      JcaContentSignerBuilder signerBuilder =
          new JcaContentSignerBuilder(SIGNATURE_ALGORITHM).setProvider("BC");
      return new JcaX509CertificateConverter()
          .setProvider("BC")
          .getCertificate(certBuilder.build(signerBuilder.build(issuerPrivateKey)));
    }

    public byte[] createSshPublicKey(RSAPublicKey publicKey, String comment) throws IOException {
      ByteArrayOutputStream byteOs = new ByteArrayOutputStream();
      DataOutputStream dos = new DataOutputStream(byteOs);
      dos.writeInt("ssh-rsa".getBytes().length);
      dos.write("ssh-rsa".getBytes());
      dos.writeInt(publicKey.getPublicExponent().toByteArray().length);
      dos.write(publicKey.getPublicExponent().toByteArray());
      dos.writeInt(publicKey.getModulus().toByteArray().length);
      dos.write(publicKey.getModulus().toByteArray());
      String sshKey =
          "ssh-rsa " + Base64.getEncoder().encodeToString(byteOs.toByteArray()) + " " + comment;
      return sshKey.getBytes(StandardCharsets.UTF_8);
    }

    public byte[] createPemBundle(PrivateKey privateKey, Certificate certificate)
        throws IOException {
      return (toPem(privateKey) + toPem(certificate)).getBytes();
    }

    public String toPem(Object object) throws IOException {
      StringWriter stringWriter = new StringWriter();
      try (JcaPEMWriter pemWriter = new JcaPEMWriter(stringWriter)) {
        pemWriter.writeObject(object);
      }
      return stringWriter.toString();
    }
  }
}
