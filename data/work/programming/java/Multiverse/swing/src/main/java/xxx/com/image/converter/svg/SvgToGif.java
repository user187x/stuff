package xxx.com.image.converter.svg;

import com.formdev.flatlaf.FlatDarculaLaf;
import org.apache.batik.anim.dom.SAXSVGDocumentFactory;
import org.apache.batik.util.XMLResourceDescriptor;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.TranscoderException;
import org.apache.batik.transcoder.SVGAbstractTranscoder;
import org.apache.batik.transcoder.image.ImageTranscoder;
import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.svg.SVGDocument;
import org.w3c.dom.svg.SVGSVGElement;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;

/**
 * Convert (SMIL) animated SVG to animated GIF with a transparent background.
 *
 * <p>Notes: - Requires Apache Batik (animation-enabled). Add dependencies for
 * batik-anim/bridge/dom/gvt. - Uses the built-in JDK GIF writer (com.sun.imageio.plugins.gif) — no
 * extra plugin required. - This will sample the animation timeline at a fixed FPS (no event/script
 * timing). - CSS keyframe animations are NOT supported by Batik. Use SMIL
 * (animate/animateTransform/animateMotion...).
 */
public class SvgToGif {
  public static void main(String[] args) throws Exception {

    if (args.length < 2) {
      System.out.println(
          "Usage: java SvgToGif <input.svg> <output.gif> [--fps=24] [--dur=2.5] [--width=auto] [--height=auto] [--loop=0] [--disposal=none|restoreToBackgroundColor|restoreToPrevious]");
      System.exit(1);
    }

    final File in = new File(args[0]);
    final File out = new File(args[1]);

    int fps = 24;
    double durationSec = 2.5; // fallback if we can't infer duration
    Integer targetW = null; // null => use SVG width
    Integer targetH = null; // null => use SVG height
    int loopCount = 0; // 0 => infinite
    String disposal = "none"; // GIF disposal for each frame

    for (int i = 2; i < args.length; i++) {
      String s = args[i];
      if (s.startsWith("--fps=")) fps = Integer.parseInt(s.substring(6));
      else if (s.startsWith("--dur=")) durationSec = Double.parseDouble(s.substring(6));
      else if (s.startsWith("--width=")) {
        String v = s.substring(8);
        if (!v.equalsIgnoreCase("auto")) targetW = Integer.parseInt(v);
      } else if (s.startsWith("--height=")) {
        String v = s.substring(9);
        if (!v.equalsIgnoreCase("auto")) targetH = Integer.parseInt(v);
      } else if (s.startsWith("--loop=")) loopCount = Integer.parseInt(s.substring(7));
      else if (s.startsWith("--disposal=")) disposal = s.substring(11);
    }

    convert(in, out, fps, durationSec, targetW, targetH, loopCount, disposal);
    System.out.println("Wrote GIF: " + out.getAbsolutePath());
  }

  /** Library-style conversion entry point for reuse from a GUI or other code. */
  public static void convert(
      File in,
      File out,
      int fps,
      double durationSec,
      Integer targetW,
      Integer targetH,
      int loopCount,
      String disposal)
      throws Exception {
    if (fps < 1) throw new IllegalArgumentException("fps must be >= 1");
    if (durationSec <= 0) throw new IllegalArgumentException("dur must be > 0");

    // 1) Load SVG with Batik in dynamic (animation-enabled) mode
    SVGDocument doc = loadSvg(in);
    SVGSVGElement root = doc.getRootElement();

    // Try to read intrinsic size from SVG
    int svgW = (int) Math.round(safeSvgLength(root.getWidth().getBaseVal().getValue()));
    int svgH = (int) Math.round(safeSvgLength(root.getHeight().getBaseVal().getValue()));
    if (svgW <= 0) svgW = 512; // sane fallback if width unspecified
    if (svgH <= 0) svgH = 512; // sane fallback if height unspecified

    int width = targetW != null ? targetW : svgW;
    int height = targetH != null ? targetH : svgH;

    // We'll use Batik's ImageTranscoder with KEY_SNAPSHOT_TIME to render each frame at time t.
    // This avoids needing a live UpdateManager and reliably advances SMIL animations.

    // 2) Decide frame count and delay
    int frames = (int) Math.max(1, Math.round(durationSec * fps));
    int delayCs =
        Math.max(
            2,
            (int)
                Math.round(
                    100.0 / fps)); // GIF uses centiseconds per frame; clamp to >=2 for broad player
    // compatibility

    // 3) Prepare GIF writer
    ImageWriter gifWriter = getGifWriter();
    if (gifWriter == null)
      throw new IllegalStateException(
          "No GIF ImageWriter found. Ensure your JDK includes the standard GIF writer (com.sun.imageio.plugins.gif). If not, use a JDK that provides it or add a small GIF encoder library.");
    try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
      gifWriter.setOutput(ios);
      gifWriter.prepareWriteSequence(null);

      for (int i = 0; i < frames; i++) {
        double t = (i * 1.0) / fps; // seconds

        // Render the SVG at snapshot time t using the transcoder
        BufferedImage frame = renderAtTime(doc, width, height, (float) t);

        // Create metadata for this frame
        IIOMetadata md =
            buildFrameMetadata(gifWriter, frame, delayCs, disposal, (i == 0), loopCount);

        // Write frame
        IIOImage img = new IIOImage(frame, null, md);
        gifWriter.writeToSequence(img, null);
      }
      gifWriter.endWriteSequence();
    }
  }

  private static SVGDocument loadSvg(File f) throws IOException {
    String parser = XMLResourceDescriptor.getXMLParserClassName();
    SAXSVGDocumentFactory factory = new SAXSVGDocumentFactory(parser);
    String uri = f.toURI().toString();
    return (SVGDocument) factory.createDocument(uri);
  }

  private static int safeSvgLength(float v) {
    if (Float.isNaN(v) || Float.isInfinite(v)) return 0;
    return (int) Math.round(v);
  }

  private static ImageWriter getGifWriter() {
    Iterator<ImageWriter> it = ImageIO.getImageWritersByFormatName("gif");
    return it.hasNext() ? it.next() : null;
  }

  /**
   * Build per-frame GIF metadata (delay/disposal/alpha). Looping is written on the first frame's
   * ApplicationExtension for widest compatibility.
   */
  private static IIOMetadata buildFrameMetadata(
      ImageWriter writer,
      BufferedImage img,
      int delayCentiseconds,
      String disposal,
      boolean firstFrame,
      int loopCount)
      throws IOException {
    ImageTypeSpecifier type = ImageTypeSpecifier.createFromRenderedImage(img);
    IIOMetadata md = writer.getDefaultImageMetadata(type, null);
    String metaFormat = md.getNativeMetadataFormatName();
    IIOMetadataNode root = (IIOMetadataNode) md.getAsTree(metaFormat);

    // GraphicControlExtension
    IIOMetadataNode gce = getOrCreateNode(root, "GraphicControlExtension");
    gce.setAttribute("disposalMethod", disposal);
    gce.setAttribute("userInputFlag", "FALSE");
    gce.setAttribute("delayTime", Integer.toString(Math.max(1, delayCentiseconds)));
    // Transparency: let the encoder/plugin infer from ARGB. If you *must*
    // force a specific transparent index, set transparentColorFlag/index here.
    gce.setAttribute("transparentColorFlag", "FALSE");
    gce.setAttribute("transparentColorIndex", "0");

    // Comment (optional)
    IIOMetadataNode comments = getOrCreateNode(root, "CommentExtensions");
    IIOMetadataNode comment = new IIOMetadataNode("CommentExtension");
    comment.setAttribute("value", "Generated by SvgToGif");
    comments.appendChild(comment);
    // Write NETSCAPE loop (in first frame for broadest compatibility)
    if (firstFrame) {
      IIOMetadataNode appExtensions = getOrCreateNode(root, "ApplicationExtensions");
      IIOMetadataNode app = new IIOMetadataNode("ApplicationExtension");
      app.setAttribute("applicationID", "NETSCAPE");
      app.setAttribute("authenticationCode", "2.0");
      int loops = Math.max(loopCount, 0); // 0 = forever
      byte[] loopBytes = new byte[] {1, (byte) (loops & 0xFF), (byte) ((loops >> 8) & 0xFF)};
      app.setUserObject(loopBytes);
      appExtensions.appendChild(app);
    }

    md.setFromTree(metaFormat, root);
    return md;
  }

  /** Render the SVG at a specific snapshot time using Batik's ImageTranscoder. */
  private static BufferedImage renderAtTime(SVGDocument doc, int width, int height, float timeSec)
      throws TranscoderException {
    class BITranscoder extends ImageTranscoder {
      BufferedImage img;

      @Override
      public BufferedImage createImage(int w, int h) {
        return new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
      }

      @Override
      public void writeImage(BufferedImage image, TranscoderOutput out) {
        this.img = image;
      }
    }
    BITranscoder t = new BITranscoder();
    t.addTranscodingHint(SVGAbstractTranscoder.KEY_EXECUTE_ONLOAD, Boolean.TRUE);
    t.addTranscodingHint(SVGAbstractTranscoder.KEY_SNAPSHOT_TIME, timeSec);
    t.addTranscodingHint(ImageTranscoder.KEY_WIDTH, (float) width);
    t.addTranscodingHint(ImageTranscoder.KEY_HEIGHT, (float) height);
    t.addTranscodingHint(ImageTranscoder.KEY_BACKGROUND_COLOR, new Color(0, 0, 0, 0));
    TranscoderInput input = new TranscoderInput(doc);
    t.transcode(input, null);
    return t.img;
  }

  private static IIOMetadataNode getOrCreateNode(IIOMetadataNode rootNode, String name) {
    for (int i = 0; i < rootNode.getLength(); i++) {
      if (rootNode.item(i).getNodeName().equalsIgnoreCase(name)) {
        return (IIOMetadataNode) rootNode.item(i);
      }
    }
    IIOMetadataNode node = new IIOMetadataNode(name);
    rootNode.appendChild(node);
    return node;
  }
}

/**
 * Super simple Swing UI that wraps SvgToGif.convert(). Keep it in the same file as a
 * package-private (non-public) class so you can compile both with a single javac call targeting
 * SvgToGif.java.
 */
class SvgToGifUI extends JFrame {

  private final JTextField inputField = new JTextField();
  private final JTextField outputField = new JTextField();
  private final JSpinner fpsSpinner = new JSpinner(new SpinnerNumberModel(24, 1, 120, 1));
  private final JSpinner durSpinner = new JSpinner(new SpinnerNumberModel(2.5, 0.1, 3600.0, 0.1));
  private final JTextField widthField = new JTextField();
  private final JTextField heightField = new JTextField();
  private final JSpinner loopSpinner = new JSpinner(new SpinnerNumberModel(0, -1, 10000, 1));
  private final JComboBox<String> disposalBox =
      new JComboBox<>(new String[] {"none", "restoreToBackgroundColor", "restoreToPrevious"});
  private final JButton convertBtn = new JButton("Convert");
  private final JButton browseInBtn = new JButton("Browse…");
  private final JButton browseOutBtn = new JButton("Browse…");
  private final JLabel status = new JLabel(" ");

  private SvgToGifUI() {

    super("SVG → GIF Converter");
    setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
    setLayout(new BorderLayout(8, 8));
    ((JComponent) getContentPane()).setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

    JPanel form = new JPanel(new GridBagLayout());
    GridBagConstraints c = new GridBagConstraints();
    c.insets = new Insets(4, 4, 4, 4);
    c.fill = GridBagConstraints.HORIZONTAL;
    c.weightx = 0;

    int row = 0;
    addRow(form, c, row++, "Input SVG", inputField, browseInBtn);
    addRow(form, c, row++, "Output GIF", outputField, browseOutBtn);
    addRow(form, c, row++, "FPS", fpsSpinner, null);
    addRow(form, c, row++, "Duration (s)", durSpinner, null);
    addRow(form, c, row++, "Width (px)", widthField, null);
    addRow(form, c, row++, "Height (px)", heightField, null);
    addRow(form, c, row++, "Loop count (0=∞, -1=>0)", loopSpinner, null);
    addRow(form, c, row++, "Disposal", disposalBox, null);

    JPanel south = new JPanel(new BorderLayout(8, 8));
    south.add(convertBtn, BorderLayout.WEST);
    south.add(status, BorderLayout.CENTER);

    add(form, BorderLayout.CENTER);
    add(south, BorderLayout.SOUTH);

    inputField.setEditable(false);
    outputField.setEditable(false);

    browseInBtn.addActionListener(e -> chooseFile(inputField, true));
    browseOutBtn.addActionListener(e -> chooseFile(outputField, false));
    convertBtn.addActionListener(e -> onConvert());

    pack();
    setSize(560, getHeight());
    setLocationRelativeTo(null);

    setVisible(true);
  }

  private void addRow(
      JPanel panel,
      GridBagConstraints c,
      int row,
      String label,
      JComponent comp,
      JButton extraBtn) {
    c.gridx = 0;
    c.gridy = row;
    c.weightx = 0;
    panel.add(new JLabel(label), c);
    c.gridx = 1;
    c.gridy = row;
    c.weightx = 1;
    panel.add(comp, c);
    if (extraBtn != null) {
      c.gridx = 2;
      c.gridy = row;
      c.weightx = 0;
      panel.add(extraBtn, c);
    }
  }

  private void chooseFile(JTextField field, boolean open) {
    JFileChooser fc = new JFileChooser();
    if (open) {
      fc.setFileFilter(new FileNameExtensionFilter("SVG Files", "svg"));
      if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
        field.setText(fc.getSelectedFile().getAbsolutePath());
      }
    } else {
      fc.setFileFilter(new FileNameExtensionFilter("GIF Files", "gif"));
      if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
        File f = fc.getSelectedFile();
        String path = f.getAbsolutePath();
        if (!path.toLowerCase().endsWith(".gif")) path += ".gif";
        field.setText(path);
      }
    }
  }

  private void setUiEnabled(boolean enabled) {
    for (Component comp : getContentPane().getComponents()) comp.setEnabled(enabled);
    // Manually re-enable status label to keep it readable
    status.setEnabled(true);
  }

  private void onConvert() {
    String inPath = inputField.getText().trim();
    String outPath = outputField.getText().trim();
    if (inPath.isEmpty() || outPath.isEmpty()) {
      JOptionPane.showMessageDialog(
          this,
          "Please choose both input SVG and output GIF.",
          "Missing info",
          JOptionPane.WARNING_MESSAGE);
      return;
    }

    File in = new File(inPath);
    File out = new File(outPath);
    int fps = ((Number) fpsSpinner.getValue()).intValue();
    double dur = ((Number) durSpinner.getValue()).doubleValue();
    Integer w = parseIntOrNull(widthField.getText().trim());
    Integer h = parseIntOrNull(heightField.getText().trim());
    int loop = ((Number) loopSpinner.getValue()).intValue();
    String disposal = (String) disposalBox.getSelectedItem();

    setUiEnabled(false);
    status.setText("Converting…");

    SwingWorker<Void, Void> worker =
        new SwingWorker<>() {
          @Override
          protected Void doInBackground() throws Exception {
            SvgToGif.convert(in, out, fps, dur, w, h, loop, disposal);
            return null;
          }

          @Override
          protected void done() {
            setUiEnabled(true);
            try {
              get();
              status.setText("Done: " + out.getAbsolutePath());
            } catch (Exception ex) {
              status.setText("Failed");
              String msg = ex.getMessage() == null ? ex.toString() : ex.getMessage();
              JOptionPane.showMessageDialog(
                  SvgToGifUI.this, msg, "Error", JOptionPane.ERROR_MESSAGE);
            }
          }
        };
    worker.execute();
  }

  private static Integer parseIntOrNull(String s) {
    if (StringUtils.isBlank(s)) return null;
    try {
      return Integer.parseInt(s);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  public static void main(String[] args) {
    FlatDarculaLaf.setup();
    SwingUtilities.invokeLater(SvgToGifUI::new);
  }
}
