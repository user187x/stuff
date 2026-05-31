package xxx.com.image.transformer;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.LineBorder;
import javax.swing.filechooser.FileNameExtensionFilter;


/**
 * A simple utility class to convert a PNG image to a Windows ICO file.
 * This converter embeds the PNG data directly into the ICO container,
 * a feature supported by Windows Vista and later versions.
 * Includes a simple drag-and-drop GUI.
 */
public class PngToIcoConverter {

  /**
   * Converts a PNG file to an ICO file.
   *
   * @param inputPngPath  The path to the source PNG file. The recommended size is 256x256 pixels.
   * @param outputIcoPath The path where the destination ICO file will be saved.
   * @throws IOException If there is an error reading the PNG or writing the ICO file.
   */
  public static void convert(String inputPngPath, String outputIcoPath) throws IOException {
    // 1. Read the PNG file into a BufferedImage to validate it and get its dimensions.
    File pngFile = new File(inputPngPath);
    BufferedImage image = ImageIO.read(pngFile);

    if (image == null) {
      throw new IOException("The specified file is not a valid PNG image: " + inputPngPath);
    }

    // 2. Read the raw bytes of the PNG file. We will embed this directly.
    byte[] pngBytes = Files.readAllBytes(pngFile.toPath());

    // 3. The total size of the ICO file will be:
    //    - ICO Header (6 bytes)
    //    - Icon Directory Entry (16 bytes)
    //    - The actual PNG image data (variable size)
    int fileSize = 6 + 16 + pngBytes.length;

    // 4. Use a ByteBuffer to construct the ICO file in memory.
    //    The ICO format uses little-endian byte order.
    ByteBuffer icoBuffer = ByteBuffer.allocate(fileSize);
    icoBuffer.order(ByteOrder.LITTLE_ENDIAN);

    // --- Write ICO Header (6 bytes) ---
    icoBuffer.putShort((short) 0); // Reserved, must be 0.
    icoBuffer.putShort((short) 1); // Type, 1 for ICO.
    icoBuffer.putShort((short) 1); // Number of images in the file.

    // --- Write Icon Directory Entry (16 bytes) ---
    int width = image.getWidth();
    int height = image.getHeight();

    // In ICO format, a value of 0 is used for 256 pixels.
    icoBuffer.put((byte) (width == 256 ? 0 : width));   // Image width
    icoBuffer.put((byte) (height == 256 ? 0 : height)); // Image height
    icoBuffer.put((byte) 0);                            // Number of colors in palette (0 for no palette).
    icoBuffer.put((byte) 0);                            // Reserved, should be 0.

    icoBuffer.putShort((short) 1);                      // Color planes.
    icoBuffer.putShort((short) 32);                     // Bits per pixel.

    icoBuffer.putInt(pngBytes.length);                  // Size of the image data in bytes.

    // Offset of image data from the beginning of the file.
    // It's always 22 for an ICO with a single image (6-byte header + 16-byte entry).
    icoBuffer.putInt(22);

    // --- Write the actual PNG image data ---
    icoBuffer.put(pngBytes);

    // 5. Write the ByteBuffer's content to the output ICO file.
    try (FileOutputStream fos = new FileOutputStream(outputIcoPath)) {
      fos.write(icoBuffer.array());
    }
  }

  /**
   * Main method to run the converter.
   * Launches the GUI if no arguments are provided, otherwise uses command-line mode.
   *
   * @param args Command-line arguments. Expects two for CLI mode: input PNG path and output ICO path.
   */
  public static void main(String[] args) {
    if (args.length == 2) {
      // Command-line interface logic
      runCli(args);
    } else {
      // Graphical user interface logic
      SwingUtilities.invokeLater(() -> new ConverterGUI().setVisible(true));
    }
  }

  /**
   * Handles the command-line execution.
   * @param args The command-line arguments.
   */
  private static void runCli(String[] args) {
    String inputPng = args[0];
    String outputIco = args[1];

    if (!inputPng.toLowerCase().endsWith(".png")) {
      System.err.println("Error: Input file must be a .png file.");
      return;
    }

    if (!outputIco.toLowerCase().endsWith(".ico")) {
      System.err.println("Error: Output file must be an .ico file.");
      return;
    }

    try {
      System.out.println("Converting " + inputPng + " to " + outputIco + "...");
      convert(inputPng, outputIco);
      System.out.println("Conversion completed successfully!");
    } catch (IOException e) {
      System.err.println("An error occurred during conversion: " + e.getMessage());
    }
  }

  /**
   * A simple Swing GUI for drag-and-drop functionality.
   */
  public static class ConverterGUI extends JFrame {
    private final JLabel dropLabel;

    public ConverterGUI() {
      super("PNG to ICO Converter");
      setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
      setSize(400, 300);
      setLocationRelativeTo(null); // Center the window

      // Create a panel and a label to act as the drop target.
      JPanel dropPanel = new JPanel(new BorderLayout());
      dropPanel.setBorder(new LineBorder(Color.GRAY, 2, true));

      dropLabel = new JLabel("Drag & Drop PNG File Here", SwingConstants.CENTER);
      dropPanel.add(dropLabel, BorderLayout.CENTER);
      add(dropPanel);

      // Set up the drop target.
      new DropTarget(dropPanel, new DropTargetHandler());
    }

    /**
     * Handles the file drop logic.
     */
    private class DropTargetHandler extends DropTarget {
      @Override
      public synchronized void drop(DropTargetDropEvent evt) {
        try {
          evt.acceptDrop(DnDConstants.ACTION_COPY);
          Transferable transferable = evt.getTransferable();

          if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
            List<File> droppedFiles = (List<File>) transferable.getTransferData(DataFlavor.javaFileListFlavor);

            // We only process the first valid PNG file.
            for (File file : droppedFiles) {
              if (file.getName().toLowerCase().endsWith(".png")) {
                processPngFile(file);
                // Stop after processing the first one.
                return;
              }
            }
          }
          // If no PNG was found
          JOptionPane.showMessageDialog(ConverterGUI.this,
              "Please drop a valid .png file.", "Invalid File", JOptionPane.ERROR_MESSAGE);

        } catch (Exception ex) {
          System.out.println(ex.getMessage());
        }
      }
    }

    /**
     * Processes the dropped PNG file: prompts for save location and converts.
     * @param pngFile The PNG file to convert.
     */
    private void processPngFile(File pngFile) {
      dropLabel.setText("Processing: " + pngFile.getName());

      JFileChooser fileChooser = new JFileChooser(pngFile.getParentFile());
      fileChooser.setDialogTitle("Save ICO File");
      // Suggest a filename
      String originalName = pngFile.getName();
      String suggestedName = originalName.substring(0, originalName.lastIndexOf('.')) + ".ico";
      fileChooser.setSelectedFile(new File(suggestedName));
      fileChooser.setFileFilter(new FileNameExtensionFilter("ICO Images", "ico"));

      int userSelection = fileChooser.showSaveDialog(this);

      if (userSelection == JFileChooser.APPROVE_OPTION) {
        File fileToSave = fileChooser.getSelectedFile();
        String outputIcoPath = fileToSave.getAbsolutePath();

        // Ensure the file has the .ico extension
        if (!outputIcoPath.toLowerCase().endsWith(".ico")) {
          outputIcoPath += ".ico";
        }

        try {
          convert(pngFile.getAbsolutePath(), outputIcoPath);
          JOptionPane.showMessageDialog(this,
              "Successfully converted to\n" + outputIcoPath,
              "Conversion Complete",
              JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException e) {
          JOptionPane.showMessageDialog(this,
              "Error during conversion: " + e.getMessage(),
              "Conversion Failed",
              JOptionPane.ERROR_MESSAGE);
        }
      }

      // Reset the label for the next operation.
      dropLabel.setText("Drag & Drop PNG File Here");
    }
  }
}

