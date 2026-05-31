package xxx.com.image.transformer;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** A library class to load, transform, and save animated GIFs with an intuitive API. */
public class GifTransformer {

  public static final String TARGET_GIF = "/gif/activity.gif";

  /** A container for a single frame of a GIF, holding the image and its delay. */
  public static class GifFrame {
    public final BufferedImage image;
    public final int delay; // Delay in milliseconds

    public GifFrame(BufferedImage image, int delay) {
      this.image = image;
      this.delay = delay;
    }
  }

  public ImageIcon toImageIcon() throws IOException {

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    GifEncoder encoder = new GifEncoder();
    encoder.start(baos);
    encoder.setRepeat(0); // 0 for infinite loop
    for (GifTransformer.GifFrame frame : getFrames()) {
      encoder.setDelay(frame.delay);
      encoder.addFrame(frame.image);
    }
    encoder.finish();
    return new ImageIcon(baos.toByteArray());
  }

  private List<GifFrame> frames;

  public GifTransformer(List<GifFrame> frames) {
    this.frames = frames;
  }

  /**
   * Loads an animated GIF from a URL.
   *
   * @param url The URL of the animated GIF.
   * @return A GifTransformer instance with the loaded frames.
   * @throws IOException If there is an error reading the GIF.
   */
  public static GifTransformer load(URL url) throws IOException {
    if (url == null) throw new IllegalArgumentException("URL cannot be null.");
    List<GifFrame> loadedFrames = readFrames(url, false, 0, 0);
    return new GifTransformer(loadedFrames);
  }

  /**
   * Loads an animated GIF from a file.
   *
   * @param file The file of the animated GIF.
   * @return A GifTransformer instance with the loaded frames.
   * @throws IOException If there is an error reading the GIF.
   */
  public static GifTransformer load(File file) throws IOException {
    if (file == null) throw new IllegalArgumentException("File cannot be null.");
    return load(file.toURI().toURL());
  }

  /**
   * Resizes the GIF to the specified dimensions.
   *
   * @param width The new width.
   * @param height The new height.
   * @return This GifTransformer for chaining.
   */
  public GifTransformer resize(int width, int height) {
    if (width <= 0 || height <= 0) {
      throw new IllegalArgumentException("Dimensions must be positive.");
    }
    List<GifFrame> newFrames = new ArrayList<>();
    for (GifFrame frame : frames) {
      BufferedImage scaledImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g2d = scaledImage.createGraphics();
      g2d.setRenderingHint(
          RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
      g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
      g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2d.drawImage(frame.image, 0, 0, width, height, null);
      g2d.dispose();
      newFrames.add(new GifFrame(scaledImage, frame.delay));
    }
    frames = newFrames;
    return this;
  }

  /**
   * Changes the playback speed of the GIF.
   *
   * @param speedFactor A multiplier for the speed. Values > 1.0 are faster, < 1.0 are slower.
   * @return This GifTransformer for chaining.
   */
  public GifTransformer changeSpeed(double speedFactor) {
    if (speedFactor <= 0) {
      throw new IllegalArgumentException("Speed factor must be positive.");
    }
    List<GifFrame> newFrames = new ArrayList<>();
    for (GifFrame frame : frames) {
      int newDelay = (int) (frame.delay / speedFactor);
      if (newDelay < 10) newDelay = 10;
      newFrames.add(new GifFrame(frame.image, newDelay));
    }
    frames = newFrames;
    return this;
  }

  /**
   * Applies a color tint to the GIF.
   *
   * @param tintColor The color to apply as a tint.
   * @return This GifTransformer for chaining.
   */
  public GifTransformer applyTint(Color tintColor) {
    if (tintColor == null) return this;
    List<GifFrame> newFrames = new ArrayList<>();
    for (GifFrame frame : frames) {
      BufferedImage tintedImage =
          new BufferedImage(
              frame.image.getWidth(), frame.image.getHeight(), BufferedImage.TYPE_INT_ARGB);
      Graphics2D g2d = tintedImage.createGraphics();
      g2d.drawImage(frame.image, 0, 0, null);
      g2d.setComposite(AlphaComposite.SrcAtop.derive(0.5f));
      g2d.setColor(tintColor);
      g2d.fillRect(0, 0, tintedImage.getWidth(), tintedImage.getHeight());
      g2d.dispose();
      newFrames.add(new GifFrame(tintedImage, frame.delay));
    }
    frames = newFrames;
    return this;
  }

  /**
   * Saves the transformed GIF to a file.
   *
   * @param file The file to save to.
   * @throws IOException If there is an error writing the file.
   */
  public void save(File file) throws IOException {
    if (frames == null || frames.isEmpty() || file == null) {
      throw new IllegalArgumentException("Frames and file cannot be null or empty.");
    }
    try (FileOutputStream fos = new FileOutputStream(file)) {
      GifEncoder encoder = new GifEncoder();
      encoder.start(fos);
      encoder.setRepeat(0); // 0 for infinite loop
      for (GifFrame frame : frames) {
        encoder.setDelay(frame.delay);
        encoder.addFrame(frame.image);
      }
      encoder.finish();
    }
  }

  /**
   * Extracts the frames as BufferedImages (without delays).
   *
   * @return A list of BufferedImage objects.
   */
  public List<BufferedImage> getImages() {
    List<BufferedImage> images = new ArrayList<>();
    for (GifFrame frame : frames) {
      images.add(frame.image);
    }
    return images;
  }

  /**
   * Gets the current frames with images and delays.
   *
   * @return A copy of the list of GifFrame objects.
   */
  public List<GifFrame> getFrames() {
    return new ArrayList<>(frames);
  }

  private static List<GifFrame> readFrames(
      URL url, boolean scale, int targetWidth, int targetHeight) throws IOException {
    List<GifFrame> readFrames = new ArrayList<>();
    try (ImageInputStream stream = ImageIO.createImageInputStream(url.openStream())) {
      ImageReader reader = ImageIO.getImageReaders(stream).next();
      reader.setInput(stream);
      int numFrames = reader.getNumImages(true);
      for (int i = 0; i < numFrames; i++) {
        BufferedImage originalFrame = reader.read(i);
        IIOMetadata metadata = reader.getImageMetadata(i);
        String metaFormatName = metadata.getNativeMetadataFormatName();
        IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(metaFormatName);
        IIOMetadataNode graphicsControlExtensionNode = findNode(root, "GraphicControlExtension");
        int delay = 100;
        if (graphicsControlExtensionNode != null) {
          delay = Integer.parseInt(graphicsControlExtensionNode.getAttribute("delayTime")) * 10;
          if (delay < 10) delay = 100;
        }
        BufferedImage frameImage;
        if (scale) {
          frameImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
          Graphics2D g2d = frameImage.createGraphics();
          g2d.setRenderingHint(
              RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
          g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
          g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
          g2d.drawImage(originalFrame, 0, 0, targetWidth, targetHeight, null);
          g2d.dispose();
        } else {
          frameImage =
              new BufferedImage(
                  originalFrame.getWidth(), originalFrame.getHeight(), BufferedImage.TYPE_INT_ARGB);
          Graphics2D g2d = frameImage.createGraphics();
          g2d.drawImage(originalFrame, 0, 0, null);
          g2d.dispose();
        }
        readFrames.add(new GifFrame(frameImage, delay));
      }
    }
    return readFrames;
  }

  private static IIOMetadataNode findNode(IIOMetadataNode rootNode, String nodeName) {
    if (rootNode == null) return null;
    for (int i = 0; i < rootNode.getLength(); i++) {
      if (rootNode.item(i).getNodeName().equalsIgnoreCase(nodeName)) {
        return (IIOMetadataNode) rootNode.item(i);
      }
    }
    return null;
  }
}

/** A demo application showing how to use the GifTransformer library with interactive controls. */
class GifTransformerDemo {

  public static void main(String[] args) {
    SwingUtilities.invokeLater(
        () -> {
          try {
            createAndShowGui();
          } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(
                null, "Failed to load GIF: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
          }
        });
  }

  private static void createAndShowGui() throws IOException {
    JFrame frame = new JFrame("Interactive GIF Transformer Demo");
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    frame.setLayout(new GridLayout(2, 3, 10, 10));
    frame.getContentPane().setBackground(new Color(30, 30, 30));

    URL gifUrl = GifTransformerDemo.class.getResource(GifTransformer.TARGET_GIF);

    frame.add(createAllInOnePanel(gifUrl));
    frame.add(createFrameExtractorPanel(gifUrl));
    frame.add(createSpeedControlPanel(gifUrl));
    frame.add(createScalingControlPanel(gifUrl));

    JPanel originalPanel = createDemoPanel("Original GIF");
    JLabel originalLabel = new JLabel(new ImageIcon(gifUrl));
    originalPanel.add(originalLabel, BorderLayout.CENTER);
    frame.add(originalPanel);

    frame.pack();
    frame.setMinimumSize(frame.getSize());
    frame.setLocationRelativeTo(null);
    frame.setVisible(true);
  }

  private static JPanel createAllInOnePanel(URL gifUrl) throws IOException {
    JPanel panel = createDemoPanel("All-In-One Control");
    GifTransformer originalTransformer = GifTransformer.load(gifUrl);
    List<GifTransformer.GifFrame> originalFrames = originalTransformer.getFrames();
    final int originalWidth = originalFrames.get(0).image.getWidth();
    final int originalHeight = originalFrames.get(0).image.getHeight();

    final int[] scalePercent = {100};
    final double[] speedFactor = {1.0};
    final Color[] tintColor = {null};
    final Timer[] animationTimer = {null};
    final List<GifTransformer.GifFrame> lastProcessedFrames = new ArrayList<>();

    JLabel imageLabel = new JLabel();
    imageLabel.setHorizontalAlignment(JLabel.CENTER);
    panel.add(imageLabel, BorderLayout.CENTER);

    JPanel controls = new JPanel(new GridLayout(0, 1, 5, 5));
    controls.setBackground(panel.getBackground());

    JSlider speedSlider = new JSlider(10, 300, 100);
    setupSlider(speedSlider, "Speed (1.00x)");
    JSlider scalingSlider = new JSlider(10, 500, 100);
    setupSlider(scalingSlider, "Size (100%)");
    JPanel colorPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
    colorPanel.setBackground(panel.getBackground());
    JButton colorButton = new JButton("Choose Color");
    JButton clearColorButton = new JButton("Clear");
    colorPanel.add(colorButton);
    colorPanel.add(clearColorButton);
    JButton exportFrameButton = new JButton("Export Frame");
    JButton exportGifButton = new JButton("Export GIF");

    controls.add(speedSlider);
    controls.add(scalingSlider);
    controls.add(colorPanel);
    controls.add(exportFrameButton);
    controls.add(exportGifButton);
    panel.add(controls, BorderLayout.SOUTH);

    Runnable updateAnimation =
        () -> {
          panel.setEnabled(false);
          SwingWorker<List<GifTransformer.GifFrame>, Void> worker =
              new SwingWorker<>() {
                @Override
                protected List<GifTransformer.GifFrame> doInBackground() throws Exception {
                  int newWidth = (originalWidth * scalePercent[0]) / 100;
                  int newHeight = (originalHeight * scalePercent[0]) / 100;
                  GifTransformer transformer = new GifTransformer(new ArrayList<>(originalFrames));
                  transformer.resize(newWidth, newHeight).changeSpeed(speedFactor[0]);
                  if (tintColor[0] != null) {
                    transformer.applyTint(tintColor[0]);
                  }
                  return transformer.getFrames();
                }

                @Override
                protected void done() {
                  try {
                    List<GifTransformer.GifFrame> processedFrames = get();
                    lastProcessedFrames.clear();
                    lastProcessedFrames.addAll(processedFrames);
                    if (animationTimer[0] != null) animationTimer[0].stop();
                    final int[] frameIndex = {0};
                    animationTimer[0] =
                        new Timer(
                            processedFrames.get(0).delay,
                            evt -> {
                              frameIndex[0] = (frameIndex[0] + 1) % processedFrames.size();
                              imageLabel.setIcon(
                                  new ImageIcon(processedFrames.get(frameIndex[0]).image));
                              ((Timer) evt.getSource())
                                  .setDelay(processedFrames.get(frameIndex[0]).delay);
                            });
                    imageLabel.setIcon(new ImageIcon(processedFrames.get(0).image));
                    animationTimer[0].start();
                  } catch (Exception ex) {
                    ex.printStackTrace();
                  } finally {
                    panel.setEnabled(true);
                  }
                }
              };
          worker.execute();
        };

    speedSlider.addChangeListener(
        e -> {
          double factor = speedSlider.getValue() / 100.0;
          ((TitledBorder) speedSlider.getBorder())
              .setTitle("Speed (" + String.format("%.2f", factor) + "x)");
          speedSlider.repaint();
          if (!speedSlider.getValueIsAdjusting()) {
            speedFactor[0] = factor;
            updateAnimation.run();
          }
        });
    scalingSlider.addChangeListener(
        e -> {
          int percent = scalingSlider.getValue();
          ((TitledBorder) scalingSlider.getBorder()).setTitle("Size (" + percent + "%)");
          scalingSlider.repaint();
          if (!scalingSlider.getValueIsAdjusting()) {
            scalePercent[0] = percent;
            updateAnimation.run();
          }
        });
    colorButton.addActionListener(
        e -> {
          Color chosenColor = JColorChooser.showDialog(panel, "Select a Tint Color", tintColor[0]);
          if (chosenColor != null) {
            tintColor[0] = chosenColor;
            updateAnimation.run();
          }
        });
    clearColorButton.addActionListener(
        e -> {
          tintColor[0] = null;
          updateAnimation.run();
        });
    exportFrameButton.addActionListener(e -> exportFrameAction(panel, imageLabel));
    exportGifButton.addActionListener(
        e -> {
          if (!lastProcessedFrames.isEmpty()) {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle("Save Animated GIF");
            fileChooser.setFileFilter(new FileNameExtensionFilter("GIF Image", "gif"));
            if (fileChooser.showSaveDialog(panel) == JFileChooser.APPROVE_OPTION) {
              File fileToSave = fileChooser.getSelectedFile();
              if (!fileToSave.getName().toLowerCase().endsWith(".gif")) {
                fileToSave = new File(fileToSave.getParentFile(), fileToSave.getName() + ".gif");
              }
              try {
                new GifTransformer(lastProcessedFrames).save(fileToSave);
              } catch (IOException ioException) {
                JOptionPane.showMessageDialog(
                    panel,
                    "Error saving GIF: " + ioException.getMessage(),
                    "Save Error",
                    JOptionPane.ERROR_MESSAGE);
              }
            }
          }
        });
    updateAnimation.run();
    return panel;
  }

  private static void exportFrameAction(JPanel panel, JLabel imageLabel) {
    Icon icon = imageLabel.getIcon();
    if (icon instanceof ImageIcon) {
      BufferedImage currentImage = (BufferedImage) ((ImageIcon) icon).getImage();
      JFileChooser fileChooser = new JFileChooser();
      fileChooser.setDialogTitle("Save Frame As PNG");
      fileChooser.setFileFilter(new FileNameExtensionFilter("PNG Image", "png"));
      if (fileChooser.showSaveDialog(panel) == JFileChooser.APPROVE_OPTION) {
        File fileToSave = fileChooser.getSelectedFile();
        if (!fileToSave.getName().toLowerCase().endsWith(".png")) {
          fileToSave = new File(fileToSave.getParentFile(), fileToSave.getName() + ".png");
        }
        try {
          ImageIO.write(currentImage, "png", fileToSave);
        } catch (IOException ioException) {
          JOptionPane.showMessageDialog(
              panel,
              "Error saving file: " + ioException.getMessage(),
              "Save Error",
              JOptionPane.ERROR_MESSAGE);
        }
      }
    }
  }

  private static JPanel createFrameExtractorPanel(URL gifUrl) throws IOException {
    JPanel panel = createDemoPanel("Frame Extractor");
    GifTransformer transformer = GifTransformer.load(gifUrl);
    List<BufferedImage> frames = transformer.getImages();
    JLabel imageLabel = new JLabel(new ImageIcon(frames.get(0)), JLabel.CENTER);
    panel.add(imageLabel, BorderLayout.CENTER);
    DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
    for (int i = 0; i < frames.size(); i++) model.addElement("Frame " + (i + 1));
    JComboBox<String> frameSelector = new JComboBox<>(model);
    frameSelector.addActionListener(
        e -> imageLabel.setIcon(new ImageIcon(frames.get(frameSelector.getSelectedIndex()))));
    JButton exportButton = new JButton("Export Current Frame");
    exportButton.addActionListener(
        e -> {
          BufferedImage currentFrame = frames.get(frameSelector.getSelectedIndex());
          exportFrameAction(panel, new JLabel(new ImageIcon(currentFrame)));
        });
    JPanel southPanel = new JPanel(new BorderLayout());
    southPanel.add(frameSelector, BorderLayout.NORTH);
    southPanel.add(exportButton, BorderLayout.SOUTH);
    panel.add(southPanel, BorderLayout.SOUTH);
    return panel;
  }

  private static JPanel createSpeedControlPanel(URL gifUrl) throws IOException {
    JPanel panel = createDemoPanel("Speed Control");
    GifTransformer baseTransformer = GifTransformer.load(gifUrl).resize(150, 150);
    final List<GifTransformer.GifFrame> baseFrames = baseTransformer.getFrames();
    final List<GifTransformer.GifFrame> currentFrames = new ArrayList<>(baseFrames);
    JLabel imageLabel = new JLabel(new ImageIcon(baseFrames.get(0).image), JLabel.CENTER);
    panel.add(imageLabel, BorderLayout.CENTER);
    JSlider speedSlider = new JSlider(10, 300, 100);
    setupSlider(speedSlider, "Speed (1.00x)");
    final int[] frameIndex = {0};
    Timer animationTimer =
        new Timer(
            baseFrames.get(0).delay,
            e -> {
              frameIndex[0] = (frameIndex[0] + 1) % currentFrames.size();
              GifTransformer.GifFrame currentFrame = currentFrames.get(frameIndex[0]);
              imageLabel.setIcon(new ImageIcon(currentFrame.image));
              ((Timer) e.getSource()).setDelay(currentFrame.delay);
            });
    animationTimer.start();
    speedSlider.addChangeListener(
        e -> {
          double factor = speedSlider.getValue() / 100.0;
          ((TitledBorder) speedSlider.getBorder())
              .setTitle("Speed (" + String.format("%.2f", factor) + "x)");
          speedSlider.repaint();
          if (!speedSlider.getValueIsAdjusting()) {
            GifTransformer transformer = new GifTransformer(new ArrayList<>(baseFrames));
            List<GifTransformer.GifFrame> newFrames = transformer.changeSpeed(factor).getFrames();
            currentFrames.clear();
            currentFrames.addAll(newFrames);
            animationTimer.restart();
          }
        });
    panel.add(speedSlider, BorderLayout.SOUTH);
    return panel;
  }

  private static JPanel createScalingControlPanel(URL gifUrl) throws IOException {
    JPanel panel = createDemoPanel("Scaling Control");
    GifTransformer originalTransformer = GifTransformer.load(gifUrl);
    List<GifTransformer.GifFrame> originalFrames = originalTransformer.getFrames();
    final int originalWidth = originalFrames.get(0).image.getWidth();
    final int originalHeight = originalFrames.get(0).image.getHeight();
    JLabel imageLabel = new JLabel();
    imageLabel.setHorizontalAlignment(JLabel.CENTER);
    panel.add(imageLabel, BorderLayout.CENTER);
    JSlider scalingSlider = new JSlider(10, 500, 100);
    setupSlider(scalingSlider, "Size (100%)");
    final Timer[] animationTimer = {null};
    scalingSlider.addChangeListener(
        e -> {
          int percentage = scalingSlider.getValue();
          ((TitledBorder) scalingSlider.getBorder()).setTitle("Size (" + percentage + "%)");
          scalingSlider.repaint();
          if (!scalingSlider.getValueIsAdjusting()) {
            scalingSlider.setEnabled(false);
            int newWidth = (originalWidth * percentage) / 100;
            int newHeight = (originalHeight * percentage) / 100;
            SwingWorker<List<GifTransformer.GifFrame>, Void> worker =
                new SwingWorker<>() {
                  @Override
                  protected List<GifTransformer.GifFrame> doInBackground() throws Exception {
                    GifTransformer transformer =
                        new GifTransformer(new ArrayList<>(originalFrames));
                    return transformer.resize(newWidth, newHeight).getFrames();
                  }

                  @Override
                  protected void done() {
                    try {
                      List<GifTransformer.GifFrame> scaledFrames = get();
                      if (animationTimer[0] != null) animationTimer[0].stop();
                      final int[] frameIndex = {0};
                      animationTimer[0] =
                          new Timer(
                              scaledFrames.get(0).delay,
                              evt -> {
                                frameIndex[0] = (frameIndex[0] + 1) % scaledFrames.size();
                                GifTransformer.GifFrame currentFrame =
                                    scaledFrames.get(frameIndex[0]);
                                imageLabel.setIcon(new ImageIcon(currentFrame.image));
                                ((Timer) evt.getSource()).setDelay(currentFrame.delay);
                              });
                      imageLabel.setIcon(new ImageIcon(scaledFrames.get(0).image));
                      animationTimer[0].start();
                    } catch (Exception ex) {
                      ex.printStackTrace();
                    } finally {
                      scalingSlider.setEnabled(true);
                    }
                  }
                };
            worker.execute();
          }
        });
    scalingSlider.setValue(150);
    scalingSlider.setValue(100);
    panel.add(scalingSlider, BorderLayout.SOUTH);
    return panel;
  }

  private static void setupSlider(JSlider slider, String title) {
    slider.setBorder(BorderFactory.createTitledBorder(title));
    slider.setMajorTickSpacing(100);
    slider.setPaintTicks(true);
    slider.setPaintLabels(true);
    slider.setBackground(new Color(50, 50, 50));
    slider.setForeground(Color.WHITE);
  }

  private static JPanel createDemoPanel(String title) {
    JPanel panel = new JPanel(new BorderLayout(5, 5));
    panel.setBorder(
        BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(Color.GRAY),
            title,
            TitledBorder.CENTER,
            TitledBorder.TOP,
            new Font("SansSerif", Font.BOLD, 12),
            Color.WHITE));
    panel.setBackground(new Color(50, 50, 50));
    return panel;
  }
}

/**
 * A self-contained, single-class GIF encoder. Original author: Elliot Kroo (<a
 * href="https://github.com/elliotkroo/gif-encoder/blob/master/src/main/java/com/madgag/gif/encoder/GifEncoder.java">...</a>)
 * Included here to avoid external dependencies.
 */
class GifEncoder {
  protected int width;
  protected int height;
  protected int x = 0;
  protected int y = 0;
  protected int transparent = -1;
  protected int transIndex;
  protected int repeat = -1;
  protected int delay = 0;
  protected boolean started = false;
  protected OutputStream out;
  protected BufferedImage image;
  protected byte[] pixels;
  protected byte[] indexedPixels;
  protected int colorDepth;
  protected byte[] colorTab;
  protected boolean[] usedEntry = new boolean[256];
  protected int palSize = 7;
  protected int dispose = -1;
  protected boolean closeStream = false;
  protected boolean firstFrame = true;
  protected boolean sizeSet = false;
  protected int sample = 10;

  public void setDelay(int ms) {
    delay = Math.round(ms / 10.0f);
  }

  public void setDispose(int code) {
    if (code >= 0) {
      dispose = code;
    }
  }

  public void setRepeat(int iter) {
    if (iter >= 0) {
      repeat = iter;
    }
  }

  public void setTransparent(Color c) {
    transparent = c.getRGB();
  }

  public boolean addFrame(BufferedImage im) {
    if ((im == null) || !started) {
      return false;
    }
    boolean ok = true;
    try {
      if (!sizeSet) {
        setSize(im.getWidth(), im.getHeight());
      }
      image = im;
      // Handle transparency preservation
      if (transparent == -1) {
        HashSet<Integer> usedColors = new HashSet<>();
        boolean hasTransparent = false;
        for (int py = 0; py < height; ++py) {
          for (int px = 0; px < width; ++px) {
            int argb = image.getRGB(px, py);
            int alpha = (argb >> 24) & 0xFF;
            if (alpha == 0) {
              hasTransparent = true;
            }
            if (alpha != 0) {
              usedColors.add(argb & 0x00FFFFFF);
            }
          }
        }
        if (hasTransparent) {
          int transRGB = 0;
          while (usedColors.contains(transRGB)) {
            transRGB = (transRGB + 1) & 0x00FFFFFF;
          }
          transparent = 0xFF000000 | transRGB;
          for (int py = 0; py < height; ++py) {
            for (int px = 0; px < width; ++px) {
              int argb = image.getRGB(px, py);
              int alpha = (argb >> 24) & 0xFF;
              if (alpha == 0) {
                image.setRGB(px, py, transparent);
              }
            }
          }
        }
      }
      getImagePixels();
      analyzePixels();
      if (firstFrame) {
        writeLSD();
        writePalette();
        if (repeat >= 0) {
          writeNetscapeExt();
        }
      }
      writeGraphicCtrlExt();
      writeImageDesc();
      if (!firstFrame) {
        writePalette();
      }
      writePixels();
      firstFrame = false;
    } catch (IOException e) {
      ok = false;
    }
    return ok;
  }

  public boolean finish() {
    if (!started) return false;
    boolean ok = true;
    started = false;
    try {
      out.write(0x3b); // gif trailer
      out.flush();
      if (closeStream) {
        out.close();
      }
    } catch (IOException e) {
      ok = false;
    }
    transIndex = 0;
    out = null;
    image = null;
    pixels = null;
    indexedPixels = null;
    colorTab = null;
    closeStream = false;
    firstFrame = true;
    return ok;
  }

  public void setFrameRate(float fps) {
    if (fps != 0f) {
      delay = Math.round(100f / fps);
    }
  }

  public void setQuality(int quality) {
    if (quality < 1) quality = 1;
    sample = quality;
  }

  public void setSize(int w, int h) {
    if (started && !firstFrame) return;
    width = w;
    height = h;
    if (width < 1) width = 320;
    if (height < 1) height = 240;
    sizeSet = true;
  }

  public boolean start(OutputStream os) {
    if (os == null) return false;
    boolean ok = true;
    closeStream = false;
    out = os;
    try {
      writeString("GIF89a");
    } catch (IOException e) {
      ok = false;
    }
    return started = ok;
  }

  protected void analyzePixels() {
    int len = pixels.length;
    int nPix = len / 3;
    indexedPixels = new byte[nPix];
    NeuQuant nq = new NeuQuant(pixels, len, sample);
    colorTab = nq.process();
    for (int i = 0; i < colorTab.length; i += 3) {
      byte temp = colorTab[i];
      colorTab[i] = colorTab[i + 2];
      colorTab[i + 2] = temp;
      usedEntry[i / 3] = false;
    }
    int k = 0;
    for (int i = 0; i < nPix; i++) {
      int index = nq.map(pixels[k++] & 0xff, pixels[k++] & 0xff, pixels[k++] & 0xff);
      usedEntry[index] = true;
      indexedPixels[i] = (byte) index;
    }
    pixels = null;
    colorDepth = 8;
    palSize = 7;
    if (transparent != -1) {
      transIndex = findClosest(transparent);
    }
  }

  protected int findClosest(int c) {
    if (colorTab == null) return -1;
    int r = (c >> 16) & 0xff;
    int g = (c >> 8) & 0xff;
    int b = (c) & 0xff;
    int minpos = 0;
    int dmin = 256 * 256 * 256;
    int len = colorTab.length;
    for (int i = 0; i < len; ) {
      int dr = r - (colorTab[i++] & 0xff);
      int dg = g - (colorTab[i++] & 0xff);
      int db = b - (colorTab[i] & 0xff);
      int d = dr * dr + dg * dg + db * db;
      int index = i / 3;
      if (usedEntry[index] && (d < dmin)) {
        dmin = d;
        minpos = index;
      }
      i++;
    }
    return minpos;
  }

  protected void getImagePixels() {
    int w = image.getWidth();
    int h = image.getHeight();
    int type = image.getType();
    if ((w != width)
        || (h != height)
        || (type != BufferedImage.TYPE_3BYTE_BGR && type != BufferedImage.TYPE_4BYTE_ABGR)) {
      BufferedImage temp = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
      Graphics2D g = temp.createGraphics();
      g.drawImage(image, 0, 0, null);
      image = temp;
    }
    pixels = ((java.awt.image.DataBufferByte) image.getRaster().getDataBuffer()).getData();
  }

  protected void writeGraphicCtrlExt() throws IOException {
    out.write(0x21); // extension introducer
    out.write(0xf9); // GCE label
    out.write(4); // data block size
    int transp, disp;
    if (transparent == -1) {
      transp = 0;
      disp = 0; // dispose = no action
    } else {
      transp = 1;
      disp = 2; // force clear if using transparent color
    }
    if (dispose >= 0) {
      disp = dispose & 7; // user override
    }
    disp <<= 2;
    out.write(0 | disp | 0 | transp);
    writeShort(delay);
    out.write(transIndex);
    out.write(0);
  }

  protected void writeImageDesc() throws IOException {
    out.write(0x2c); // image separator
    writeShort(x);
    writeShort(y);
    writeShort(width);
    writeShort(height);
    if (firstFrame) {
      out.write(0);
    } else {
      out.write(0x80 | 0 | 0 | 0 | palSize);
    }
  }

  protected void writeLSD() throws IOException {
    writeShort(width);
    writeShort(height);
    out.write((0x80 | 0x70 | 0x00 | palSize));
    out.write(0);
    out.write(0);
  }

  protected void writeNetscapeExt() throws IOException {
    out.write(0x21); // extension introducer
    out.write(0xff); // app extension label
    out.write(11); // block size
    writeString("NETSCAPE2.0"); // app id + auth code
    out.write(3); // sub-block size
    out.write(1); // loop sub-block id
    writeShort(repeat); // loop count (0 = forever)
    out.write(0); // block terminator
  }

  protected void writePalette() throws IOException {
    out.write(colorTab, 0, colorTab.length);
    int n = (3 * 256) - colorTab.length;
    for (int i = 0; i < n; i++) {
      out.write(0);
    }
  }

  protected void writePixels() throws IOException {
    LZWEncoder encoder = new LZWEncoder(width, height, indexedPixels, colorDepth);
    encoder.encode(out);
  }

  protected void writeShort(int value) throws IOException {
    out.write(value & 0xff);
    out.write((value >> 8) & 0xff);
  }

  protected void writeString(String s) throws IOException {
    for (int i = 0; i < s.length(); i++) {
      out.write((byte) s.charAt(i));
    }
  }

  class LZWEncoder {
    private static final int EOF = -1;
    private int imgW, imgH;
    private byte[] pixAry;
    private int initCodeSize;
    private int remaining;
    private int curPixel;
    private static final int BITS = 12;
    private static final int HSIZE = 5003;
    int n_bits;
    int maxbits = BITS;
    int maxcode;
    int maxmaxcode = 1 << BITS;
    int[] htab = new int[HSIZE];
    int[] codetab = new int[HSIZE];
    int hsize = HSIZE;
    int free_ent = 0;
    boolean clear_flg = false;
    int g_init_bits;
    int ClearCode;
    int EOFCode;
    int cur_accum = 0;
    int cur_bits = 0;
    int masks[] = {
      0x0000, 0x0001, 0x0003, 0x0007, 0x000F, 0x001F, 0x003F, 0x007F, 0x00FF, 0x01FF, 0x03FF,
      0x07FF, 0x0FFF, 0x1FFF, 0x3FFF, 0x7FFF
    };
    byte[] accum = new byte[256];
    int a_count;

    LZWEncoder(int width, int height, byte[] pixels, int color_depth) {
      imgW = width;
      imgH = height;
      pixAry = pixels;
      initCodeSize = Math.max(2, color_depth);
    }

    void char_out(byte c, OutputStream outs) throws IOException {
      accum[a_count++] = c;
      if (a_count >= 254) flush_char(outs);
    }

    void cl_block(OutputStream outs) throws IOException {
      cl_hash(hsize);
      free_ent = ClearCode + 2;
      clear_flg = true;
      output(ClearCode, outs);
    }

    void cl_hash(int hsize) {
      for (int i = 0; i < hsize; ++i) htab[i] = -1;
    }

    void compress(int init_bits, OutputStream outs) throws IOException {
      int fcode;
      int i;
      int c;
      int ent;
      int disp;
      int hsize_reg;
      int hshift;
      g_init_bits = init_bits;
      clear_flg = false;
      n_bits = g_init_bits;
      maxcode = MAXCODE(n_bits);
      ClearCode = 1 << (init_bits - 1);
      EOFCode = ClearCode + 1;
      free_ent = ClearCode + 2;
      a_count = 0;
      ent = nextPixel();
      hshift = 0;
      for (fcode = hsize; fcode < 65536; fcode *= 2) hshift++;
      hshift = 8 - hshift;
      hsize_reg = hsize;
      cl_hash(hsize_reg);
      output(ClearCode, outs);
      outer_loop:
      while ((c = nextPixel()) != EOF) {
        fcode = (c << maxbits) + ent;
        i = (c << hshift) ^ ent;
        if (htab[i] == fcode) {
          ent = codetab[i];
          continue;
        } else if (htab[i] >= 0) {
          disp = hsize_reg - i;
          if (i == 0) disp = 1;
          do {
            if ((i -= disp) < 0) i += hsize_reg;
            if (htab[i] == fcode) {
              ent = codetab[i];
              continue outer_loop;
            }
          } while (htab[i] >= 0);
        }
        output(ent, outs);
        ent = c;
        if (free_ent < maxmaxcode) {
          codetab[i] = free_ent++;
          htab[i] = fcode;
        } else cl_block(outs);
      }
      output(ent, outs);
      output(EOFCode, outs);
    }

    void encode(OutputStream os) throws IOException {
      os.write(initCodeSize);
      remaining = imgW * imgH;
      curPixel = 0;
      compress(initCodeSize + 1, os);
      os.write(0);
    }

    void flush_char(OutputStream outs) throws IOException {
      if (a_count > 0) {
        outs.write(a_count);
        outs.write(accum, 0, a_count);
        a_count = 0;
      }
    }

    final int MAXCODE(int n_bits) {
      return (1 << n_bits) - 1;
    }

    private int nextPixel() {
      if (remaining == 0) return EOF;
      --remaining;
      byte pix = pixAry[curPixel++];
      return pix & 0xff;
    }

    void output(int code, OutputStream outs) throws IOException {
      cur_accum &= masks[cur_bits];
      if (cur_bits > 0) cur_accum |= (code << cur_bits);
      else cur_accum = code;
      cur_bits += n_bits;
      while (cur_bits >= 8) {
        char_out((byte) (cur_accum & 0xff), outs);
        cur_accum >>= 8;
        cur_bits -= 8;
      }
      if (free_ent > maxcode || clear_flg) {
        if (clear_flg) {
          maxcode = MAXCODE(n_bits = g_init_bits);
          clear_flg = false;
        } else {
          ++n_bits;
          if (n_bits == maxbits) maxcode = maxmaxcode;
          else maxcode = MAXCODE(n_bits);
        }
      }
      if (code == EOFCode) {
        while (cur_bits > 0) {
          char_out((byte) (cur_accum & 0xff), outs);
          cur_accum >>= 8;
          cur_bits -= 8;
        }
        flush_char(outs);
      }
    }
  }

  class NeuQuant {
    protected static final int netsize = 256;
    protected static final int prime1 = 499;
    protected static final int prime2 = 491;
    protected static final int prime3 = 487;
    protected static final int prime4 = 503;
    protected static final int minpicturebytes = (3 * prime4);
    protected static final int maxnetpos = (netsize - 1);
    protected static final int netbiasshift = 4;
    protected static final int ncycles = 100;
    protected static final int intbiasshift = 16;
    protected static final int intbias = (1 << intbiasshift);
    protected static final int gammashift = 10;
    protected static final int gamma = (1 << gammashift);
    protected static final int betashift = 10;
    protected static final int beta = (intbias >> betashift);
    protected static final int betagamma = (intbias << (gammashift - betashift));
    protected int[][] network;
    protected int[] netindex = new int[256];
    protected int[] bias = new int[netsize];
    protected int[] freq = new int[netsize];
    protected int[] radpower = new int[netsize >> 3];
    protected byte[] thepicture;
    protected int lengthcount;
    protected int samplefac;

    public NeuQuant(byte[] thepic, int len, int sample) {
      int i;
      int[] p;
      thepicture = thepic;
      lengthcount = len;
      samplefac = sample;
      network = new int[netsize][];
      for (i = 0; i < netsize; i++) {
        network[i] = new int[4];
        p = network[i];
        p[0] = p[1] = p[2] = (i << (netbiasshift + 8)) / netsize;
        freq[i] = intbias / netsize;
        bias[i] = 0;
      }
    }

    public byte[] colorMap() {
      byte[] map = new byte[3 * netsize];
      int[] index = new int[netsize];
      for (int i = 0; i < netsize; i++) index[network[i][3]] = i;
      int k = 0;
      for (int i = 0; i < netsize; i++) {
        int j = index[i];
        map[k++] = (byte) (network[j][0]);
        map[k++] = (byte) (network[j][1]);
        map[k++] = (byte) (network[j][2]);
      }
      return map;
    }

    public void inxbuild() {
      int i, j, smallpos, smallval;
      int[] p;
      int[] q;
      int previouscol, startpos;
      previouscol = 0;
      startpos = 0;
      for (i = 0; i < netsize; i++) {
        p = network[i];
        smallpos = i;
        smallval = p[1];
        for (j = i + 1; j < netsize; j++) {
          q = network[j];
          if (q[1] < smallval) {
            smallpos = j;
            smallval = q[1];
          }
        }
        q = network[smallpos];
        if (i != smallpos) {
          j = q[0];
          q[0] = p[0];
          p[0] = j;
          j = q[1];
          q[1] = p[1];
          p[1] = j;
          j = q[2];
          q[2] = p[2];
          p[2] = j;
          j = q[3];
          q[3] = p[3];
          p[3] = j;
        }
        if (smallval != previouscol) {
          netindex[previouscol] = (startpos + i) >> 1;
          for (j = previouscol + 1; j < smallval; j++) netindex[j] = i;
          previouscol = smallval;
          startpos = i;
        }
      }
      netindex[previouscol] = (startpos + maxnetpos) >> 1;
      for (j = previouscol + 1; j < 256; j++) netindex[j] = maxnetpos;
    }

    public void learn() {
      int i, j, b, g, r;
      int radius, rad, alpha, step, delta, samplepixels;
      byte[] p;
      int pix, lim;
      if (lengthcount < minpicturebytes) samplefac = 1;
      alphadec = 30 + ((samplefac - 1) / 3);
      p = thepicture;
      pix = 0;
      lim = lengthcount;
      samplepixels = lengthcount / (3 * samplefac);
      delta = samplepixels / ncycles;
      alpha = initalpha;
      radius = initradius;
      rad = radius >> radbiasshift;
      if (rad <= 1) rad = 0;
      for (i = 0; i < rad; i++)
        radpower[i] = alpha * (((rad * rad - i * i) * radbias) / (rad * rad));
      if (lengthcount < minpicturebytes) step = 3;
      else if ((lengthcount % prime1) != 0) step = 3 * prime1;
      else {
        if ((lengthcount % prime2) != 0) step = 3 * prime2;
        else {
          if ((lengthcount % prime3) != 0) step = 3 * prime3;
          else step = 3 * prime4;
        }
      }
      i = 0;
      while (i < samplepixels) {
        b = (p[pix + 0] & 0xff) << netbiasshift;
        g = (p[pix + 1] & 0xff) << netbiasshift;
        r = (p[pix + 2] & 0xff) << netbiasshift;
        j = contest(b, g, r);
        altersingle(alpha, j, b, g, r);
        if (rad != 0) alterneigh(rad, j, b, g, r);
        pix += step;
        if (pix >= lim) pix -= lengthcount;
        i++;
        if (delta == 0) delta = 1;
        if (i % delta == 0) {
          alpha -= alpha / alphadec;
          radius -= radius / radiusdec;
          rad = radius >> radbiasshift;
          if (rad <= 1) rad = 0;
          for (j = 0; j < rad; j++)
            radpower[j] = alpha * (((rad * rad - j * j) * radbias) / (rad * rad));
        }
      }
    }

    public int map(int b, int g, int r) {
      int i, j, dist, a, bestd;
      int[] p;
      int best;
      bestd = 1000;
      best = -1;
      i = netindex[g];
      j = i - 1;
      while ((i < netsize) || (j >= 0)) {
        if (i < netsize) {
          p = network[i];
          dist = p[1] - g;
          if (dist >= bestd) i = netsize;
          else {
            i++;
            if (dist < 0) dist = -dist;
            a = p[0] - b;
            if (a < 0) a = -a;
            dist += a;
            if (dist < bestd) {
              a = p[2] - r;
              if (a < 0) a = -a;
              dist += a;
              if (dist < bestd) {
                bestd = dist;
                best = p[3];
              }
            }
          }
        }
        if (j >= 0) {
          p = network[j];
          dist = g - p[1];
          if (dist >= bestd) j = -1;
          else {
            j--;
            if (dist < 0) dist = -dist;
            a = p[0] - b;
            if (a < 0) a = -a;
            dist += a;
            if (dist < bestd) {
              a = p[2] - r;
              if (a < 0) a = -a;
              dist += a;
              if (dist < bestd) {
                bestd = dist;
                best = p[3];
              }
            }
          }
        }
      }
      return (best);
    }

    public byte[] process() {
      learn();
      unbiasnet();
      inxbuild();
      return colorMap();
    }

    public void unbiasnet() {
      int i;
      for (i = 0; i < netsize; i++) {
        network[i][0] >>= netbiasshift;
        network[i][1] >>= netbiasshift;
        network[i][2] >>= netbiasshift;
        network[i][3] = i;
      }
    }

    protected void alterneigh(int rad, int i, int b, int g, int r) {
      int j, k, dist, a, m;
      int[] p;
      int lo = i - rad;
      if (lo < -1) lo = -1;
      int hi = i + rad;
      if (hi > netsize) hi = netsize;
      j = i + 1;
      k = i - 1;
      m = 1;
      while ((j < hi) || (k > lo)) {
        a = radpower[m++];
        if (j < hi) {
          p = network[j++];
          try {
            p[0] -= (a * (p[0] - b)) / alpharadbias;
            p[1] -= (a * (p[1] - g)) / alpharadbias;
            p[2] -= (a * (p[2] - r)) / alpharadbias;
          } catch (Exception e) {
          }
        }
        if (k > lo) {
          p = network[k--];
          try {
            p[0] -= (a * (p[0] - b)) / alpharadbias;
            p[1] -= (a * (p[1] - g)) / alpharadbias;
            p[2] -= (a * (p[2] - r)) / alpharadbias;
          } catch (Exception e) {
          }
        }
      }
    }

    protected void altersingle(int alpha, int i, int b, int g, int r) {
      int[] n = network[i];
      n[0] -= (alpha * (n[0] - b)) / initalpha;
      n[1] -= (alpha * (n[1] - g)) / initalpha;
      n[2] -= (alpha * (n[2] - r)) / initalpha;
    }

    protected int contest(int b, int g, int r) {
      int i, dist, a, biasdist, betafreq;
      int bestpos, bestbiaspos, bestd, bestbiasd;
      int[] n;
      bestd = ~(((int) 1) << 31);
      bestbiasd = bestd;
      bestpos = -1;
      bestbiaspos = bestpos;
      for (i = 0; i < netsize; i++) {
        n = network[i];
        dist = n[0] - b;
        if (dist < 0) dist = -dist;
        a = n[1] - g;
        if (a < 0) a = -a;
        dist += a;
        a = n[2] - r;
        if (a < 0) a = -a;
        dist += a;
        if (dist < bestd) {
          bestd = dist;
          bestpos = i;
        }
        biasdist = dist - ((bias[i]) >> (intbiasshift - netbiasshift));
        if (biasdist < bestbiasd) {
          bestbiasd = biasdist;
          bestbiaspos = i;
        }
        betafreq = (freq[i] >> betashift);
        freq[i] -= betafreq;
        bias[i] += (betafreq << gammashift);
      }
      freq[bestpos] += beta;
      bias[bestpos] -= betagamma;
      return (bestbiaspos);
    }

    protected static final int initalpha = (1 << netbiasshift);
    protected int alphadec;
    protected static final int radbiasshift = 8;
    protected static final int radbias = (1 << radbiasshift);
    protected static final int initradius = (netsize >> 3);
    protected int radiusdec = 30;
    protected static final int alpharadbiasshift = (netbiasshift + radbiasshift);
    protected static final int alpharadbias = (1 << alpharadbiasshift);
  }

  public static class GifExample {

    public static void main(String[] args) throws IOException {

      // Example 1: Load, resize, turn into ImageIcon
      URL gifUrl1 = GifExample.class.getResource("/gif/gears.gif");
      GifTransformer transformer1 = GifTransformer.load(gifUrl1);
      transformer1.resize(200, 200);
      ImageIcon resizedIcon = transformer1.toImageIcon();

      // Example 2: Load, change color, turn into ImageIcon
      URL gifUrl2 = GifExample.class.getResource("/gif/gears.gif");
      GifTransformer transformer2 = GifTransformer.load(gifUrl2);
      transformer2.applyTint(Color.RED);
      File tempFile2 = File.createTempFile("tinted", ".gif");
      transformer2.save(tempFile2);
      ImageIcon tintedIcon = new ImageIcon(tempFile2.toURI().toURL());

      // Example 3: Load, change speed, turn into ImageIcon
      URL gifUrl3 = GifExample.class.getResource("/gif/gears.gif");
      GifTransformer transformer3 = GifTransformer.load(gifUrl3);
      transformer3.changeSpeed(2.0);
      File tempFile3 = File.createTempFile("spedup", ".gif");
      transformer3.save(tempFile3);
      ImageIcon spedUpIcon = new ImageIcon(tempFile3.toURI().toURL());
    }
  }
}
