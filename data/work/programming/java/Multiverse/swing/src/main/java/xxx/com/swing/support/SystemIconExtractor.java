package xxx.com.swing.support;

import javax.swing.*;
import javax.swing.filechooser.FileSystemView;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * A utility to extract a file's system icon, scale it with high quality, and display it.
 */
public class SystemIconExtractor {

  /**
   * Converts an Icon to a BufferedImage.
   * This is a crucial step because the Icon from FileSystemView might not be an ImageIcon.
   *
   * @param icon The Icon to convert.
   * @return A BufferedImage representing the icon.
   */
  public static BufferedImage iconToBufferedImage(Icon icon) {
    // Create a new BufferedImage with the icon's dimensions and transparency
    BufferedImage bufferedImage = new BufferedImage(
        icon.getIconWidth(),
        icon.getIconHeight(),
        BufferedImage.TYPE_INT_ARGB
    );
    // Get the graphics context of the new image
    Graphics2D g2d = bufferedImage.createGraphics();
    // Paint the icon onto the new image
    icon.paintIcon(null, g2d, 0, 0);
    // Dispose of the graphics context to release resources
    g2d.dispose();
    return bufferedImage;
  }

  /**
   * Scales an image using Graphics2D for high quality rendering with anti-aliasing.
   * This is superior to Image.getScaledInstance().
   *
   * @param sourceImage The image to scale.
   * @param width The target width.
   * @param height The target height.
   * @return A new, high-quality scaled BufferedImage.
   */
  public static BufferedImage scaleImage(Image sourceImage, int width, int height) {
    BufferedImage scaledImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g2d = scaledImage.createGraphics();

    // Set rendering hints for the best quality
    g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

    // Draw the source image into the new scaled image
    g2d.drawImage(sourceImage, 0, 0, width, height, null);
    g2d.dispose();

    return scaledImage;
  }


  public static void main(String[] args) {
    // Use SwingUtilities.invokeLater to ensure UI updates are on the Event Dispatch Thread
    SwingUtilities.invokeLater(() -> {
      FileSystemView fsv = FileSystemView.getFileSystemView();
      // Specify the file you want the icon from.
      File file = new File("C:\\Windows\\System32\\notepad.exe");

      // 1. Get the original system icon
      Icon originalIcon = fsv.getSystemIcon(file);

      if (originalIcon != null) {
        // 2. Convert the Icon to a BufferedImage to make it scalable
        Image originalImage = iconToBufferedImage(originalIcon);

        // 3. Scale the Image using our high-quality method
        Image scaledImage = scaleImage(originalImage, 96, 96); // Increased size to better see quality

        // 4. Create a new ImageIcon from the scaled Image
        ImageIcon scaledIcon = new ImageIcon(scaledImage);

        // --- UI Setup ---
        JLabel originalLabel = new JLabel("Original", originalIcon, SwingConstants.CENTER);
        originalLabel.setVerticalTextPosition(SwingConstants.BOTTOM);
        originalLabel.setHorizontalTextPosition(SwingConstants.CENTER);

        JLabel scaledLabel = new JLabel("High-Quality Scaled (96x96)", scaledIcon, SwingConstants.CENTER);
        scaledLabel.setVerticalTextPosition(SwingConstants.BOTTOM);
        scaledLabel.setHorizontalTextPosition(SwingConstants.CENTER);

        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 30, 20));
        panel.add(originalLabel);
        panel.add(scaledLabel);

        JFrame frame = new JFrame("System Icon Scaler");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.add(panel);
        frame.pack();
        frame.setLocationRelativeTo(null); // Center the frame
        frame.setVisible(true);
      } else {
        System.out.println("Could not retrieve icon for the specified file.");
      }
    });
  }
}
