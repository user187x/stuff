package xxx.com.ocr.gui;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.File;

public class OfflineOcrApp extends JFrame {

  private final JLabel imageLabel;
  private final JTextArea resultsArea;
  private final JButton selectButton;
  private final JButton setPathButton;
  private final JLabel statusLabel;

  private String tessdataParentPath; // Will be set by the user via the GUI

  public OfflineOcrApp() {
    // --- Frame Setup ---
    setTitle("Java Offline OCR App");
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    setSize(800, 600);
    setLocationRelativeTo(null); // Center the window
    setLayout(new BorderLayout(10, 10));

    // --- Main Panel ---
    JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
    mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
    mainPanel.setBackground(new Color(245, 245, 245));
    add(mainPanel);

    // --- Top Panel for Controls ---
    JPanel topPanel = new JPanel(new BorderLayout(10, 0));
    topPanel.setBackground(Color.WHITE);

    JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
    buttonPanel.setBackground(Color.WHITE);

    setPathButton = new JButton("Set Tesseract Path");
    styleButton(setPathButton, new Color(218, 112, 214)); // Orchid color for setup

    selectButton = new JButton("Select an Image for OCR");
    styleButton(selectButton, new Color(0, 128, 128)); // Teal
    selectButton.setEnabled(false); // Disabled until path is set

    buttonPanel.add(setPathButton);
    buttonPanel.add(selectButton);

    statusLabel = new JLabel("Status: Please set the Tesseract data path.", SwingConstants.CENTER);
    statusLabel.setForeground(Color.RED);
    statusLabel.setFont(new Font("Arial", Font.BOLD, 12));

    topPanel.add(buttonPanel, BorderLayout.CENTER);
    topPanel.add(statusLabel, BorderLayout.SOUTH);

    mainPanel.add(topPanel, BorderLayout.NORTH);

    // --- Center Panel for Image and Results ---
    JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
    splitPane.setResizeWeight(0.5); // Distribute space evenly
    splitPane.setBorder(null);

    // --- Image Display Panel ---
    JPanel imagePanel = new JPanel(new BorderLayout());
    imagePanel.setBackground(Color.WHITE);
    imagePanel.setBorder(BorderFactory.createTitledBorder("Your Image"));
    imageLabel = new JLabel("No image selected", SwingConstants.CENTER);
    imageLabel.setFont(new Font("Serif", Font.ITALIC, 16));
    imagePanel.add(new JScrollPane(imageLabel), BorderLayout.CENTER);
    splitPane.setLeftComponent(imagePanel);

    // --- Results Display Panel ---
    JPanel resultsPanel = new JPanel(new BorderLayout());
    resultsPanel.setBackground(Color.WHITE);
    resultsPanel.setBorder(BorderFactory.createTitledBorder("Extracted Text (OCR)"));
    resultsArea = new JTextArea("Text from the image will appear here...");
    resultsArea.setEditable(false);
    resultsArea.setLineWrap(true);
    resultsArea.setWrapStyleWord(true);
    resultsArea.setFont(new Font("Monospaced", Font.PLAIN, 14));
    resultsArea.setMargin(new Insets(10, 10, 10, 10));
    resultsPanel.add(new JScrollPane(resultsArea), BorderLayout.CENTER);
    splitPane.setRightComponent(resultsPanel);

    mainPanel.add(splitPane, BorderLayout.CENTER);

    // --- Action Listeners ---
    setPathButton.addActionListener(e -> selectTessdataParentDirectory());
    selectButton.addActionListener(e -> selectAndProcessImage());
  }

  /**
   * Opens a dialog for the user to select the parent directory of "tessdata".
   */
  private void selectTessdataParentDirectory() {
    JFileChooser chooser = new JFileChooser();
    chooser.setDialogTitle("Select Tesseract's Parent Directory (contains 'tessdata')");
    chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
    chooser.setAcceptAllFileFilterUsed(false);

    if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
      File selectedDir = chooser.getSelectedFile();
      File tessdataDir = new File(selectedDir, "tessdata");

      // Validate that the selected directory actually contains the "tessdata" subfolder
      if (tessdataDir.exists() && tessdataDir.isDirectory()) {
        this.tessdataParentPath = selectedDir.getAbsolutePath();
        statusLabel.setText("Status: Tesseract path is set. Ready to process images.");
        statusLabel.setForeground(new Color(0, 128, 0)); // Dark Green
        selectButton.setEnabled(true);
        JOptionPane.showMessageDialog(this,
            "Tesseract path set successfully!",
            "Success",
            JOptionPane.INFORMATION_MESSAGE);
      } else {
        JOptionPane.showMessageDialog(this,
            "The selected directory does not contain the required 'tessdata' subfolder.\n" +
                "Please select the folder that is the direct parent of 'tessdata'.",
            "Invalid Directory",
            JOptionPane.ERROR_MESSAGE);
      }
    }
  }

  /**
   * Styles a JButton to give it a modern look.
   * @param button The button to style.
   * @param color The background color for the button.
   */
  private void styleButton(JButton button, Color color) {
    button.setFont(new Font("Arial", Font.BOLD, 14));
    button.setBackground(color);
    button.setForeground(Color.WHITE);
    button.setFocusPainted(false);
    button.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
    button.setCursor(new Cursor(Cursor.HAND_CURSOR));
  }

  /**
   * Opens a file chooser to select an image, displays it, and starts the OCR process.
   */
  private void selectAndProcessImage() {
    JFileChooser fileChooser = new JFileChooser();
    FileNameExtensionFilter filter = new FileNameExtensionFilter(
        "Image Files (JPG, PNG, GIF, BMP, TIFF)", "jpg", "jpeg", "png", "gif", "bmp", "tif", "tiff");
    fileChooser.setFileFilter(filter);

    if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
      String selectedImagePath = fileChooser.getSelectedFile().getAbsolutePath();

      // Display the selected image
      ImageIcon imageIcon = new ImageIcon(new ImageIcon(selectedImagePath).getImage().getScaledInstance(350, -1, Image.SCALE_SMOOTH));
      imageLabel.setIcon(imageIcon);
      imageLabel.setText(null);

      // Show loading message and analyze in a background thread
      resultsArea.setText("Performing OCR... Please wait.");
      setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
      selectButton.setEnabled(false);
      setPathButton.setEnabled(false);

      new SwingWorker<String, Void>() {
        @Override
        protected String doInBackground() {
          return performOcr(selectedImagePath);
        }

        @Override
        protected void done() {
          try {
            resultsArea.setText(get());
          } catch (Exception e) {
            resultsArea.setText("An error occurred during OCR:\n" + e.getMessage());
            e.printStackTrace();
          } finally {
            setCursor(Cursor.getDefaultCursor());
            selectButton.setEnabled(true);
            setPathButton.setEnabled(true);
          }
        }
      }.execute();
    }
  }

  /**
   * Performs OCR on the image at the given path using Tesseract.
   * @param filePath The path to the image file.
   * @return The extracted text.
   */
  private String performOcr(String filePath) {
    File imageFile = new File(filePath);
    Tesseract tesseract = new Tesseract();

    try {
      // Use the path set by the user through the GUI
      tesseract.setDatapath(this.tessdataParentPath);
      tesseract.setLanguage("eng");

      String result = tesseract.doOCR(imageFile);

      return (result == null || result.trim().isEmpty()) ? "No text was found in the image." : result;

    } catch (TesseractException e) {
      String errorMessage = "Error while processing image with Tesseract.\n\n";
      errorMessage += "Possible causes:\n";
      errorMessage += "1. Tesseract is not installed correctly on your system.\n";
      errorMessage += "2. The 'tessdata' path is incorrect or the files are corrupted.\n";
      errorMessage += "   (Details: " + e.getMessage() + ")";
      System.err.println(e.getMessage());
      return errorMessage;
    }
  }

  /**
   * The main entry point for the application.
   */
  public static void main(String[] args) {
    SwingUtilities.invokeLater(() -> {
      try {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
      } catch (Exception e) {
        e.printStackTrace();
      }
      new OfflineOcrApp().setVisible(true);
    });
  }
}
