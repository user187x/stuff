package xxx.com.pdf;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Swing GUI application for converting PDF files to images (PNG or JPEG).
 */
public class PdfToImageConverterGUI extends JFrame {

  /**
   * 
   */
  private static final long serialVersionUID = 1L;
  private static final Logger LOGGER = LoggerFactory.getLogger(PdfToImageConverterGUI.class);
  private static final int DEFAULT_DPI = 300; // Dots per inch for rendering

  // GUI Components
  private JTextField pdfPathField;
  private JButton selectPdfButton;
  private JComboBox<String> formatComboBox;
  private JTextField outputBaseNameField;
  private JTextField outputDirField;
  private JButton selectOutputDirButton;
  private JButton convertButton;
  private JTextArea statusArea;
  private JProgressBar progressBar;


  private File selectedPdfFile;
  private File outputDirectory;

  public PdfToImageConverterGUI() {
    super("PDF to Image Converter"); // Frame title
    initComponents();
    layoutComponents();
    attachListeners();

    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    // setSize(600, 450); // Adjusted size for better component visibility
    setMinimumSize(new Dimension(550, 400)); // Set a minimum size
    setLocationRelativeTo(null); // Center the window
    pack(); // Adjust window size to fit components
  }

  /**
   * Initializes all GUI components.
   */
  private void initComponents() {
    // PDF File Selection
    pdfPathField = new JTextField(30);
    pdfPathField.setEditable(false); // User should use the button to select
    selectPdfButton = new JButton("Select PDF...");

    // Image Format Selection
    formatComboBox = new JComboBox<>(new String[] {"PNG", "JPEG"});

    // Output Base Name
    outputBaseNameField = new JTextField("converted_image", 20); // Default base name

    // Output Directory Selection
    outputDirField = new JTextField(30);
    outputDirField.setEditable(false); // User should use the button to select
    selectOutputDirButton = new JButton("Select Output Dir...");

    // Convert Button
    convertButton = new JButton("Convert to Images");

    // Status Area
    statusArea = new JTextArea(10, 40);
    statusArea.setEditable(false);
    statusArea.setLineWrap(true);
    statusArea.setWrapStyleWord(true);

    // Progress Bar
    progressBar = new JProgressBar(0, 100);
    progressBar.setStringPainted(true);
    progressBar.setVisible(false); // Initially hidden
  }

  /**
   * Lays out the GUI components within the frame.
   */
  private void layoutComponents() {
    // Main panel with GridBagLayout for more control
    JPanel mainPanel = new JPanel(new GridBagLayout());
    mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10)); // Padding
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.insets = new Insets(5, 5, 5, 5); // Spacing between components
    gbc.fill = GridBagConstraints.HORIZONTAL; // Make components fill horizontal space

    // Row 1: PDF File Selection
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.anchor = GridBagConstraints.LINE_END;
    mainPanel.add(new JLabel("PDF File:"), gbc);
    gbc.gridx = 1;
    gbc.gridwidth = 2;
    gbc.weightx = 1.0;
    mainPanel.add(pdfPathField, gbc);
    gbc.gridx = 3;
    gbc.gridwidth = 1;
    gbc.weightx = 0;
    gbc.fill = GridBagConstraints.NONE;
    gbc.anchor = GridBagConstraints.LINE_START;
    mainPanel.add(selectPdfButton, gbc);
    gbc.fill = GridBagConstraints.HORIZONTAL; // Reset fill

    // Row 2: Image Format
    gbc.gridx = 0;
    gbc.gridy = 1;
    gbc.anchor = GridBagConstraints.LINE_END;
    mainPanel.add(new JLabel("Output Format:"), gbc);
    gbc.gridx = 1;
    gbc.gridwidth = 1;
    gbc.anchor = GridBagConstraints.LINE_START;
    mainPanel.add(formatComboBox, gbc);

    // Row 3: Output Base Name
    gbc.gridx = 0;
    gbc.gridy = 2;
    gbc.anchor = GridBagConstraints.LINE_END;
    mainPanel.add(new JLabel("Output Base Name:"), gbc);
    gbc.gridx = 1;
    gbc.gridwidth = 2;
    gbc.weightx = 1.0;
    mainPanel.add(outputBaseNameField, gbc);
    gbc.weightx = 0; // Reset weightx

    // Row 4: Output Directory
    gbc.gridx = 0;
    gbc.gridy = 3;
    gbc.anchor = GridBagConstraints.LINE_END;
    mainPanel.add(new JLabel("Output Directory:"), gbc);
    gbc.gridx = 1;
    gbc.gridwidth = 2;
    gbc.weightx = 1.0;
    mainPanel.add(outputDirField, gbc);
    gbc.gridx = 3;
    gbc.gridwidth = 1;
    gbc.weightx = 0;
    gbc.fill = GridBagConstraints.NONE;
    gbc.anchor = GridBagConstraints.LINE_START;
    mainPanel.add(selectOutputDirButton, gbc);
    gbc.fill = GridBagConstraints.HORIZONTAL; // Reset fill

    // Row 5: Convert Button
    gbc.gridx = 0;
    gbc.gridy = 4;
    gbc.gridwidth = 4;
    gbc.anchor = GridBagConstraints.CENTER;
    gbc.fill = GridBagConstraints.NONE;
    mainPanel.add(convertButton, gbc);
    gbc.fill = GridBagConstraints.HORIZONTAL; // Reset fill


    // Row 6: Progress Bar
    gbc.gridx = 0;
    gbc.gridy = 5;
    gbc.gridwidth = 4;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    mainPanel.add(progressBar, gbc);


    // Add mainPanel to the frame's content pane
    getContentPane().setLayout(new BorderLayout(10, 10)); // Main layout for the frame
    getContentPane().add(mainPanel, BorderLayout.NORTH);
    getContentPane().add(new JScrollPane(statusArea), BorderLayout.CENTER); // Status area with scroll pane

  }

  /**
   * Attaches event listeners to interactive components.
   */
  private void attachListeners() {
    // Action listener for "Select PDF" button
    selectPdfButton.addActionListener((ActionEvent e) -> {
      JFileChooser fileChooser = new JFileChooser();
      fileChooser.setDialogTitle("Select PDF File");
      fileChooser.setFileFilter(new FileNameExtensionFilter("PDF Documents (*.pdf)", "pdf"));
      fileChooser.setAcceptAllFileFilterUsed(false);
      int result = fileChooser.showOpenDialog(PdfToImageConverterGUI.this);
      if (result == JFileChooser.APPROVE_OPTION) {
        selectedPdfFile = fileChooser.getSelectedFile();
        pdfPathField.setText(selectedPdfFile.getAbsolutePath());
        // Set default output base name based on PDF name
        String pdfName = selectedPdfFile.getName();
        if (pdfName.toLowerCase().endsWith(".pdf")) {
          outputBaseNameField.setText(pdfName.substring(0, pdfName.length() - 4));
        }
        else {
          outputBaseNameField.setText(pdfName);
        }
        // Set default output directory to PDF's directory
        if (selectedPdfFile.getParentFile() != null) {
          outputDirectory = selectedPdfFile.getParentFile();
          outputDirField.setText(outputDirectory.getAbsolutePath());
        }
        statusArea.append("Selected PDF: " + selectedPdfFile.getAbsolutePath() + "\n");
      }
    });

    // Action listener for "Select Output Directory" button
    selectOutputDirButton.addActionListener((ActionEvent e) -> {
      JFileChooser dirChooser = new JFileChooser();
      dirChooser.setDialogTitle("Select Output Directory");
      dirChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
      dirChooser.setAcceptAllFileFilterUsed(false);
      int result = dirChooser.showOpenDialog(PdfToImageConverterGUI.this);
      if (result == JFileChooser.APPROVE_OPTION) {
        outputDirectory = dirChooser.getSelectedFile();
        outputDirField.setText(outputDirectory.getAbsolutePath());
        statusArea.append("Selected output directory: " + outputDirectory.getAbsolutePath() + "\n");
      }
    });

    // Action listener for "Convert" button
    convertButton.addActionListener((ActionEvent e) -> {
      performConversion();
    });
  }

  /**
   * Validates inputs and starts the PDF conversion process using a SwingWorker.
   */
  private void performConversion() {
    if (selectedPdfFile == null || !selectedPdfFile.exists()) {
      JOptionPane.showMessageDialog(this, "Please select a valid PDF file.", "Input Error", JOptionPane.ERROR_MESSAGE);
      return;
    }
    if (outputDirectory == null || !outputDirectory.exists() || !outputDirectory.isDirectory()) {
      JOptionPane.showMessageDialog(this, "Please select a valid output directory.", "Input Error", JOptionPane.ERROR_MESSAGE);
      return;
    }
    String baseName = outputBaseNameField.getText().trim();
    if (baseName.isEmpty()) {
      JOptionPane.showMessageDialog(this, "Please enter an output base name.", "Input Error", JOptionPane.ERROR_MESSAGE);
      return;
    }
    // Sanitize baseName to prevent issues with file paths
    baseName = baseName.replaceAll("[^a-zA-Z0-9_\\-]", "_");


    String format = ((String) formatComboBox.getSelectedItem()).toLowerCase();

    statusArea.append("Starting conversion...\n");
    convertButton.setEnabled(false); // Disable button during conversion
    progressBar.setValue(0);
    progressBar.setVisible(true);

    // SwingWorker for background processing
    PdfConversionWorker worker = new PdfConversionWorker(selectedPdfFile, outputDirectory, baseName, format);
    worker.execute();
  }

  /**
   * SwingWorker to perform PDF conversion in the background.
   */
  private class PdfConversionWorker extends SwingWorker<Integer, String> {
    private final File pdfFile;
    private final File outputDir;
    private final String baseName;
    private final String imageFormat;
    private int totalPages = 0;

    public PdfConversionWorker(File pdfFile, File outputDir, String baseName, String imageFormat) {
      this.pdfFile = pdfFile;
      this.outputDir = outputDir;
      this.baseName = baseName;
      this.imageFormat = imageFormat;
    }

    @Override
    protected Integer doInBackground() throws Exception {
      int pagesConverted = 0;
      try (PDDocument document = org.apache.pdfbox.Loader.loadPDF(pdfFile)) {

        PDFRenderer pdfRenderer = new PDFRenderer(document);
        totalPages = document.getNumberOfPages();
        publish("PDF has " + totalPages + " page(s). Converting...");

        for (int pageIndex = 0; pageIndex < totalPages; pageIndex++) {
          if (isCancelled()) {
            publish("Conversion cancelled.");
            break;
          }

          // Render page to an image
          BufferedImage bim = pdfRenderer.renderImageWithDPI(pageIndex, DEFAULT_DPI, ImageType.RGB);

          // Construct output file name
          String outputFileName = String.format("%s_page_%d.%s", baseName, pageIndex + 1, imageFormat);
          File outputFile = new File(outputDir, outputFileName);

          // Write the image to a file
          boolean success = ImageIO.write(bim, imageFormat, outputFile);
          if (success) {
            publish("Successfully converted page " + (pageIndex + 1) + " to " + outputFile.getName());
            pagesConverted++;
          }
          else {
            publish("ERROR: Failed to write image for page " + (pageIndex + 1));
            LOGGER.error("Failed to write image for page {} to format {}", pageIndex + 1, imageFormat);
          }
          // Update progress
          int progress = (int) (((pageIndex + 1.0) / totalPages) * 100);
          setProgress(progress);
        }
      }
      catch (IOException ex) {
        publish("ERROR during PDF processing: " + ex.getMessage());
        LOGGER.error("Error during PDF processing: {}", ex.getMessage(), ex);
        throw ex; // Re-throw to be caught by done()
      }
      return pagesConverted;
    }

    @Override
    protected void process(List<String> chunks) {
      // This method is called on the EDT.
      // Update GUI with messages published from doInBackground.
      for (String message : chunks) {
        statusArea.append(message + "\n");
      }
    }

    @Override
    protected void done() {
      // This method is called on the EDT when doInBackground() is finished.
      try {
        Integer pagesConverted = get(); // Get the result from doInBackground
        if (pagesConverted != null) {
          statusArea.append("Conversion finished. " + pagesConverted + " page(s) converted.\n");
          if (pagesConverted == totalPages && totalPages > 0) {
            JOptionPane.showMessageDialog(PdfToImageConverterGUI.this,
                "Conversion successful! " + pagesConverted + " page(s) converted.",
                "Conversion Complete", JOptionPane.INFORMATION_MESSAGE);
          }
          else if (totalPages > 0 && pagesConverted < totalPages && pagesConverted > 0) {
            JOptionPane.showMessageDialog(PdfToImageConverterGUI.this,
                "Conversion partially complete. " + pagesConverted + "/" + totalPages + " page(s) converted.",
                "Conversion Warning", JOptionPane.WARNING_MESSAGE);
          }
          else if (totalPages == 0) {
            JOptionPane.showMessageDialog(PdfToImageConverterGUI.this,
                "No pages found in the PDF or PDF is empty.",
                "Conversion Info", JOptionPane.INFORMATION_MESSAGE);
          }
        }
      }
      catch (InterruptedException e) {
        statusArea.append("Conversion interrupted: " + e.getMessage() + "\n");
        LOGGER.warn("Conversion interrupted", e);
        Thread.currentThread().interrupt(); // Restore interrupt status
      }
      catch (ExecutionException e) {
        statusArea.append("Error during conversion: " + e.getCause().getMessage() + "\n");
        LOGGER.error("ExecutionException during conversion", e.getCause());
        JOptionPane.showMessageDialog(PdfToImageConverterGUI.this,
            "An error occurred during conversion: " + e.getCause().getMessage(),
            "Conversion Error", JOptionPane.ERROR_MESSAGE);
      }
      finally {
        convertButton.setEnabled(true); // Re-enable button
        progressBar.setVisible(false);
        statusArea.append("------------------------------------\n");
      }
    }
  }

  /**
   * Main method to launch the GUI application.
   *
   * @param args Command line arguments (not used).
   */
  public static void main(String[] args) {
    // Set Look and Feel to system default for better native appearance
    try {
      UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
    }
    catch (Exception e) {
      LOGGER.warn("Could not set system Look and Feel.", e);
    }

    // Ensure GUI updates are done on the Event Dispatch Thread (EDT)
    SwingUtilities.invokeLater(() -> {
      PdfToImageConverterGUI gui = new PdfToImageConverterGUI();
      gui.setVisible(true);
    });
  }
}

