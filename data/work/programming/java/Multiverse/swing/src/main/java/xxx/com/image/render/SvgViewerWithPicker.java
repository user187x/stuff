package xxx.com.image.render;

import com.formdev.flatlaf.FlatLightLaf;
import org.apache.batik.swing.JSVGCanvas;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.File;
import java.net.MalformedURLException;
import java.nio.file.Path;

public class SvgViewerWithPicker extends JFrame {

  private final JSVGCanvas svgCanvas;
  private final JComboBox<File> svgComboBox;

  /**
   * Constructor to set up the GUI components.
   */
  public SvgViewerWithPicker() {
    // --- Frame Setup ---
    super("SVG Viewer with Picker");
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    setSize(800, 600);
    setLayout(new BorderLayout());
    setBackground(Color.BLACK);

    // --- UI Components ---
    // The canvas where the SVG will be rendered
    svgCanvas = new JSVGCanvas();

    // The dropdown (combo box) to list SVG files
    svgComboBox = new JComboBox<>();

    // The button to open the directory chooser
    JButton openDirButton = new JButton("Choose Directory...");

    // --- Control Panel Setup ---
    // A panel at the top to hold the button and dropdown
    JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
    controlPanel.add(openDirButton);
    controlPanel.add(svgComboBox);

    // --- Layout ---
    add(controlPanel, BorderLayout.NORTH);
    add(svgCanvas, BorderLayout.CENTER);

    // --- Action Listeners ---
    // 1. When the "Choose Directory..." button is clicked
    openDirButton.addActionListener(e -> openDirectoryChooser());

    // 2. When a new item is selected in the dropdown
    svgComboBox.addActionListener(e -> renderSelectedSvg());
  }

  private File getSvgDirectory() {

    try{

      Path path = Path.of(ClassLoader.getSystemResource("svg/pulse.svg").toURI());
      return path.getParent().toFile();

    } catch (Exception e) {

      String userHome = System.getProperty("user.home");
      String desktopPath = userHome + "\\Desktop";

      return new File(desktopPath);
    }

  }

  /**
   * Opens a JFileChooser to select a directory.
   */
  private void openDirectoryChooser() {

    System.out.println("Opening Directory..." + getSvgDirectory().getAbsolutePath());

    FileNameExtensionFilter imageFilter = new FileNameExtensionFilter(
        "svg", "svg");

    JFileChooser directoryChooser = new JFileChooser(getSvgDirectory().getAbsolutePath());
    directoryChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
    directoryChooser.addChoosableFileFilter(imageFilter);
    directoryChooser.setFileFilter(imageFilter);
    directoryChooser.setDialogTitle("Select a directory containing SVG files");

    int result = directoryChooser.showOpenDialog(this);
    if (result == JFileChooser.APPROVE_OPTION) {
      File selectedDirectory = directoryChooser.getSelectedFile();
      loadSvgFiles(selectedDirectory);
    }
  }

  /**
   * Finds all .svg files in the given directory and populates the dropdown menu.
   * @param directory The directory to search.
   */
  private void loadSvgFiles(File directory) {
    // Clear any previous items from the dropdown
    svgComboBox.removeAllItems();

    // Find all files ending with .svg (case-insensitive)
    File[] svgFiles = directory.listFiles((dir, name) -> name.toLowerCase().endsWith(".svg"));

    if (svgFiles != null && svgFiles.length > 0) {
      for (File file : svgFiles) {
        svgComboBox.addItem(file);
      }
      // The first item is automatically selected, triggering the render action
    } else {
      // Clear the canvas if no SVG files are found
      svgCanvas.setURI(null);
      JOptionPane.showMessageDialog(
          this,
          "No SVG files found in the selected directory.",
          "Information",
          JOptionPane.INFORMATION_MESSAGE
      );
    }
  }

  /**
   * Renders the SVG file currently selected in the dropdown.
   */
  private void renderSelectedSvg() {
    // Get the selected item (which is a File object)
    File selectedFile = (File) svgComboBox.getSelectedItem();

    if (selectedFile != null && selectedFile.exists()) {
      try {
        // Convert the file path to a URI and load it into the canvas
        svgCanvas.setURI(selectedFile.toURI().toURL().toString());

      } catch (MalformedURLException e) {
        e.printStackTrace();
        JOptionPane.showMessageDialog(
            this,
            "Error creating URL for the selected file:\n" + e.getMessage(),
            "Error",
            JOptionPane.ERROR_MESSAGE
        );
      }
    }
  }

  /**
   * Main method to create and run the application.
   */
  public static void main(String[] args) {
    FlatLightLaf.setup();
    SwingUtilities.invokeLater(() -> {
      SvgViewerWithPicker viewer = new SvgViewerWithPicker();
      viewer.setVisible(true);
    });
  }
}
