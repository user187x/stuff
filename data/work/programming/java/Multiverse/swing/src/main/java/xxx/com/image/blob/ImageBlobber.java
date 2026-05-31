package xxx.com.image.blob;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

/**
 * A Java application that demonstrates packing multiple image resources into a single binary blob
 * file and then reading that blob to reconstruct and display the images.
 */
public class ImageBlobber extends JFrame {

  private final ImageDisplayPanel imagePanel;
  private static final String BLOB_FILE_NAME = "images.blob";

  public ImageBlobber() {
    setTitle("Image Blob Loader");
    setSize(500, 250);
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    setLocationRelativeTo(null);

    imagePanel = new ImageDisplayPanel();
    add(imagePanel, BorderLayout.CENTER);

    // --- Button Panel ---
    JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 10));
    JButton createBlobButton = new JButton("1. Create Image Blob");
    JButton loadImagesButton = new JButton("2. Load Images from Blob");
    buttonPanel.add(createBlobButton);
    buttonPanel.add(loadImagesButton);
    add(buttonPanel, BorderLayout.SOUTH);

    // --- Action Listeners ---
    createBlobButton.addActionListener(
        e -> {
          try {
            // Step 1: Create some sample images for the demo
            List<String> tempImageFiles = createDummyImages();

            // Step 2: Pack them into a blob
            createImageBlob(tempImageFiles, BLOB_FILE_NAME);

            // Step 3: Clean up temporary files
            for (String filePath : tempImageFiles) {
              Files.delete(Paths.get(filePath));
            }

            JOptionPane.showMessageDialog(
                this,
                "Successfully created '" + BLOB_FILE_NAME + "' in the project root.",
                "Blob Created",
                JOptionPane.INFORMATION_MESSAGE);

          } catch (IOException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(
                this,
                "Error creating blob: " + ex.getMessage(),
                "Error",
                JOptionPane.ERROR_MESSAGE);
          }
        });

    loadImagesButton.addActionListener(
        e -> {
          try {
            if (!Files.exists(Paths.get(BLOB_FILE_NAME))) {
              JOptionPane.showMessageDialog(
                  this,
                  "Blob file not found. Please create it first.",
                  "File Not Found",
                  JOptionPane.WARNING_MESSAGE);
              return;
            }
            Map<String, Image> loadedImages = loadImagesFromBlob(BLOB_FILE_NAME);
            imagePanel.setImages(new ArrayList<>(loadedImages.values()));
            JOptionPane.showMessageDialog(
                this,
                "Successfully loaded " + loadedImages.size() + " images from the blob.",
                "Images Loaded",
                JOptionPane.INFORMATION_MESSAGE);
          } catch (IOException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(
                this,
                "Error loading from blob: " + ex.getMessage(),
                "Error",
                JOptionPane.ERROR_MESSAGE);
          }
        });
  }

  /**
   * Stores multiple image files into a single binary blob. The blob format is: 1. (int) Number of
   * images 2. For each image: a. (UTF-String) Filename b. (int) Length of image byte data c.
   * (byte[]) The raw image data
   *
   * @param imagePaths A list of file paths for the images to pack.
   * @param blobFilePath The path for the output blob file.
   * @throws IOException If an I/O error occurs.
   */
  public void createImageBlob(List<String> imagePaths, String blobFilePath) throws IOException {
    System.out.println("Creating image blob at: " + blobFilePath);
    try (FileOutputStream fos = new FileOutputStream(blobFilePath);
        DataOutputStream dos = new DataOutputStream(fos)) {

      // 1. Write the number of images
      dos.writeInt(imagePaths.size());

      for (String path : imagePaths) {
        Path imagePath = Paths.get(path);
        byte[] imageData = Files.readAllBytes(imagePath);

        // 2a. Write the filename
        dos.writeUTF(imagePath.getFileName().toString());
        System.out.println(
            "  - Writing " + imagePath.getFileName() + " (" + imageData.length + " bytes)");

        // 2b. Write the length of the image data
        dos.writeInt(imageData.length);

        // 2c. Write the raw image data
        dos.write(imageData);
      }
    }
    System.out.println("Blob creation complete.");
  }

  /**
   * Reads an image blob file and converts its contents back into Image objects.
   *
   * @param blobFilePath The path to the blob file.
   * @return A Map where keys are filenames and values are the loaded Image objects.
   * @throws IOException If the file cannot be read or the format is incorrect.
   */
  public Map<String, Image> loadImagesFromBlob(String blobFilePath) throws IOException {
    System.out.println("Loading images from blob: " + blobFilePath);
    Map<String, Image> images = new LinkedHashMap<>(); // Use LinkedHashMap to preserve order

    try (FileInputStream fis = new FileInputStream(blobFilePath);
        DataInputStream dis = new DataInputStream(fis)) {

      // 1. Read the number of images
      int imageCount = dis.readInt();
      System.out.println("Found " + imageCount + " images in blob.");

      for (int i = 0; i < imageCount; i++) {
        // 2a. Read the filename
        String fileName = dis.readUTF();

        // 2b. Read the data length
        int dataLength = dis.readInt();

        System.out.println("  - Reading " + fileName + " (" + dataLength + " bytes)");

        // 2c. Read the image data into a byte array
        byte[] imageData = new byte[dataLength];
        dis.readFully(imageData);

        // Convert byte array back to an Image
        try (ByteArrayInputStream bais = new ByteArrayInputStream(imageData)) {
          Image image = ImageIO.read(bais);
          if (image != null) {
            images.put(fileName, image);
          } else {
            System.err.println("Warning: Could not decode image data for " + fileName);
          }
        }
      }
    }
    System.out.println("Image loading complete.");
    return images;
  }

  /**
   * A helper method to generate sample images for the demonstration. It creates three 100x100 PNG
   * images with different colors.
   *
   * @return A list of paths to the created temporary image files.
   * @throws IOException If there's an error writing the files.
   */
  private List<String> createDummyImages() throws IOException {
    List<String> paths = new ArrayList<>();
    String[] colors = {"red", "green", "blue"};
    Color[] awtColors = {Color.RED, Color.GREEN, Color.BLUE};

    for (int i = 0; i < colors.length; i++) {
      String fileName = "temp_image_" + colors[i] + ".png";
      BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g2d = image.createGraphics();

      // Fill with color
      g2d.setColor(awtColors[i]);
      g2d.fillRect(0, 0, 100, 100);

      // Draw a label
      g2d.setColor(Color.WHITE);
      g2d.setFont(new Font("Arial", Font.BOLD, 16));
      g2d.drawString(colors[i].toUpperCase(), 28, 55);

      g2d.dispose();

      File outputFile = new File(fileName);
      ImageIO.write(image, "png", outputFile);
      paths.add(outputFile.getAbsolutePath());
      System.out.println("Created dummy image: " + fileName);
    }
    return paths;
  }

  /** A simple JPanel subclass for displaying a list of images side-by-side. */
  private static class ImageDisplayPanel extends JPanel {
    private List<Image> images = new ArrayList<>();

    public void setImages(List<Image> images) {
      this.images = images;
      repaint(); // Redraw the panel with the new images
    }

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      if (images == null || images.isEmpty()) {
        g.setColor(Color.GRAY);
        g.setFont(new Font("Arial", Font.PLAIN, 14));
        String msg = "Images will be displayed here after loading.";
        int stringWidth = g.getFontMetrics().stringWidth(msg);
        g.drawString(msg, (getWidth() - stringWidth) / 2, getHeight() / 2);
      } else {
        int x = 20;
        int y = (getHeight() - 100) / 2; // Center vertically
        for (Image img : images) {
          if (img != null) {
            g.drawImage(img, x, y, 100, 100, this);
            x += 120; // Move to the right for the next image (100 width + 20 padding)
          }
        }
      }
    }
  }

  public static void main(String[] args) {
    // Run the GUI on the Event Dispatch Thread (EDT)
    SwingUtilities.invokeLater(() -> new ImageBlobber().setVisible(true));
  }
}
