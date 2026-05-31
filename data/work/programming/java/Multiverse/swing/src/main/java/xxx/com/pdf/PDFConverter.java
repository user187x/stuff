package xxx.com.pdf; // Or a more general package like xxx.com.converter

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
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
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Swing GUI application for converting PDF files to images (PNG or JPEG)
 * and converting images (PNG, JPEG) to PDF files.
 */
public class PDFConverter extends JFrame { // Renamed class

    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = LoggerFactory.getLogger(PDFConverter.class); // Updated logger class
    private static final int DEFAULT_DPI = 300; // Dots per inch for rendering

    // Conversion Modes
    private static final String MODE_PDF_TO_IMAGE = "PDF to Images";
    private static final String MODE_IMAGE_TO_PDF = "Image to PDF";

    // GUI Components
    private JComboBox<String> conversionTypeComboBox;
    private JLabel inputFileLabel;
    private JTextField inputPathField;
    private JButton selectInputFileButton;
    private JLabel pdfToImageOutputFormatLabel;
    private JComboBox<String> imageFormatComboBox; // Was formatComboBox
    private JLabel outputNameLabel;
    private JTextField outputNameField; // Was outputBaseNameField
    private JTextField outputDirField;
    private JButton selectOutputDirButton;
    private JButton convertButton;
    private JTextArea statusArea;
    private JProgressBar progressBar;

    private File selectedInputFile; // Generalized from selectedPdfFile
    // private List<File> selectedImageFiles; // For future multi-image to PDF
    private File outputDirectory;

    public PDFConverter() {
        super("File Converter"); // Updated frame title
        initComponents();
        layoutComponents();
        attachListeners();
        updateGUIForConversionType(); // Initial GUI setup based on mode

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(600, 480)); // Adjusted size
        setLocationRelativeTo(null);
        pack();
    }

    private void initComponents() {
        conversionTypeComboBox = new JComboBox<>(new String[]{MODE_PDF_TO_IMAGE, MODE_IMAGE_TO_PDF});

        inputFileLabel = new JLabel("Input File:");
        inputPathField = new JTextField(30);
        inputPathField.setEditable(false);
        selectInputFileButton = new JButton("Select File...");

        pdfToImageOutputFormatLabel = new JLabel("Image Output Format:");
        imageFormatComboBox = new JComboBox<>(new String[]{"PNG", "JPEG"});

        outputNameLabel = new JLabel("Output Name:");
        outputNameField = new JTextField("converted_output", 20); // Default name

        outputDirField = new JTextField(30);
        outputDirField.setEditable(false);
        selectOutputDirButton = new JButton("Select Output Dir...");

        convertButton = new JButton("Convert"); // Text will be updated dynamically

        statusArea = new JTextArea(10, 40);
        statusArea.setEditable(false);
        statusArea.setLineWrap(true);
        statusArea.setWrapStyleWord(true);

        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setVisible(false);
    }

    private void layoutComponents() {
        JPanel mainPanel = new JPanel(new GridBagLayout());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Row 0: Conversion Type
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.LINE_END;
        mainPanel.add(new JLabel("Conversion Type:"), gbc);
        gbc.gridx = 1;
        gbc.gridwidth = 3; // Span across remaining columns
        gbc.anchor = GridBagConstraints.LINE_START;
        mainPanel.add(conversionTypeComboBox, gbc);
        gbc.gridwidth = 1; // Reset gridwidth

        // Row 1: Input File Selection
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.anchor = GridBagConstraints.LINE_END;
        mainPanel.add(inputFileLabel, gbc); // Using the new label
        gbc.gridx = 1;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        mainPanel.add(inputPathField, gbc);
        gbc.gridx = 3;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.LINE_START;
        mainPanel.add(selectInputFileButton, gbc);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Row 2: PDF to Image Output Format
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.anchor = GridBagConstraints.LINE_END;
        mainPanel.add(pdfToImageOutputFormatLabel, gbc);
        gbc.gridx = 1;
        gbc.gridwidth = 1;
        gbc.anchor = GridBagConstraints.LINE_START;
        mainPanel.add(imageFormatComboBox, gbc);

        // Row 3: Output Name
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.anchor = GridBagConstraints.LINE_END;
        mainPanel.add(outputNameLabel, gbc);
        gbc.gridx = 1;
        gbc.gridwidth = 2; // Span 2 columns
        gbc.weightx = 1.0;
        mainPanel.add(outputNameField, gbc);
        gbc.weightx = 0;

        // Row 4: Output Directory
        gbc.gridx = 0;
        gbc.gridy = 4;
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
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Row 5: Convert Button
        gbc.gridx = 0;
        gbc.gridy = 5;
        gbc.gridwidth = 4;
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.fill = GridBagConstraints.NONE;
        mainPanel.add(convertButton, gbc);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Row 6: Progress Bar
        gbc.gridx = 0;
        gbc.gridy = 6;
        gbc.gridwidth = 4;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        mainPanel.add(progressBar, gbc);

        getContentPane().setLayout(new BorderLayout(10, 10));
        getContentPane().add(mainPanel, BorderLayout.NORTH);
        getContentPane().add(new JScrollPane(statusArea), BorderLayout.CENTER);
    }

    private void updateGUIForConversionType() {
        String selectedMode = (String) conversionTypeComboBox.getSelectedItem();
        if (MODE_PDF_TO_IMAGE.equals(selectedMode)) {
            inputFileLabel.setText("PDF File:");
            selectInputFileButton.setToolTipText("Select a PDF file to convert to images");
            pdfToImageOutputFormatLabel.setVisible(true);
            imageFormatComboBox.setVisible(true);
            outputNameLabel.setText("Output Base Name:");
            outputNameField.setToolTipText("Base name for the output image files (e.g., mydoc_page_1.png)");
            convertButton.setText("Convert to Images");

            // Clear image-specific selections if any
            // if (selectedImageFiles != null) selectedImageFiles.clear();

        } else if (MODE_IMAGE_TO_PDF.equals(selectedMode)) {
            inputFileLabel.setText("Image File:");
            selectInputFileButton.setToolTipText("Select an image file (PNG, JPEG) to convert to PDF");
            pdfToImageOutputFormatLabel.setVisible(false);
            imageFormatComboBox.setVisible(false);
            outputNameLabel.setText("Output PDF Name:");
            outputNameField.setToolTipText("Name of the output PDF file (e.g., mydocument.pdf)");
            convertButton.setText("Convert to PDF");
        }
        // Reset paths and names on mode switch to avoid confusion
        inputPathField.setText("");
        selectedInputFile = null;
        // outputNameField.setText("converted_output"); // Keep or clear? User might want to reuse.
        outputDirField.setText("");
        outputDirectory = null;
        statusArea.setText(""); // Clear status
    }


    private void attachListeners() {
        conversionTypeComboBox.addActionListener(e -> updateGUIForConversionType());

        selectInputFileButton.addActionListener((ActionEvent e) -> {
            JFileChooser fileChooser = new JFileChooser();
            String selectedMode = (String) conversionTypeComboBox.getSelectedItem();

            if (MODE_PDF_TO_IMAGE.equals(selectedMode)) {
                fileChooser.setDialogTitle("Select PDF File");
                fileChooser.setFileFilter(new FileNameExtensionFilter("PDF Documents (*.pdf)", "pdf"));
                fileChooser.setMultiSelectionEnabled(false);
            } else if (MODE_IMAGE_TO_PDF.equals(selectedMode)) {
                fileChooser.setDialogTitle("Select Image File (PNG, JPEG)");
                fileChooser.setFileFilter(new FileNameExtensionFilter("Images (*.png, *.jpeg, *.jpg)", "png", "jpeg", "jpg"));
                // For now, single image selection. Multi-selection can be enabled here:
                // fileChooser.setMultiSelectionEnabled(true);
                fileChooser.setMultiSelectionEnabled(false);
            }
            fileChooser.setAcceptAllFileFilterUsed(false);

            int result = fileChooser.showOpenDialog(PDFConverter.this);
            if (result == JFileChooser.APPROVE_OPTION) {
                selectedInputFile = fileChooser.getSelectedFile();
                inputPathField.setText(selectedInputFile.getAbsolutePath());

                String inputFileName = selectedInputFile.getName();
                String baseName = inputFileName;
                int dotIndex = inputFileName.lastIndexOf('.');
                if (dotIndex > 0) {
                    baseName = inputFileName.substring(0, dotIndex);
                }
                outputNameField.setText(baseName);


                if (selectedInputFile.getParentFile() != null) {
                    outputDirectory = selectedInputFile.getParentFile();
                    outputDirField.setText(outputDirectory.getAbsolutePath());
                }
                statusArea.append("Selected input file: " + selectedInputFile.getAbsolutePath() + "\n");
            }
        });

        selectOutputDirButton.addActionListener((ActionEvent e) -> {
            JFileChooser dirChooser = new JFileChooser();
            dirChooser.setDialogTitle("Select Output Directory");
            dirChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            dirChooser.setAcceptAllFileFilterUsed(false);
            int result = dirChooser.showOpenDialog(PDFConverter.this);
            if (result == JFileChooser.APPROVE_OPTION) {
                outputDirectory = dirChooser.getSelectedFile();
                outputDirField.setText(outputDirectory.getAbsolutePath());
                statusArea.append("Selected output directory: " + outputDirectory.getAbsolutePath() + "\n");
            }
        });

        convertButton.addActionListener((ActionEvent e) -> {
            performConversion();
        });
    }

    private void performConversion() {
        if (selectedInputFile == null || !selectedInputFile.exists()) {
            JOptionPane.showMessageDialog(this, "Please select a valid input file.", "Input Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (outputDirectory == null || !outputDirectory.exists() || !outputDirectory.isDirectory()) {
            JOptionPane.showMessageDialog(this, "Please select a valid output directory.", "Input Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        String outputName = outputNameField.getText().trim();
        if (outputName.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter an output name.", "Input Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        // Sanitize outputName to prevent issues with file paths
        outputName = outputName.replaceAll("[^a-zA-Z0-9_\\-]", "_");

        statusArea.append("Starting conversion...\n");
        convertButton.setEnabled(false);
        progressBar.setValue(0);
        progressBar.setVisible(true);

        String selectedMode = (String) conversionTypeComboBox.getSelectedItem();

        if (MODE_PDF_TO_IMAGE.equals(selectedMode)) {
            String imageFormat = ((String) imageFormatComboBox.getSelectedItem()).toLowerCase();
            PdfToImageConversionWorker worker = new PdfToImageConversionWorker(selectedInputFile, outputDirectory, outputName, imageFormat);
            worker.execute();
        } else if (MODE_IMAGE_TO_PDF.equals(selectedMode)) {
            // For Image to PDF, outputName is the full PDF filename (without .pdf yet, worker adds it)
            ImageToPdfWorker worker = new ImageToPdfWorker(Collections.singletonList(selectedInputFile), outputDirectory, outputName);
            worker.execute();
        }
    }

    /**
     * SwingWorker for PDF to Image conversion. (Original worker, renamed)
     */
    private class PdfToImageConversionWorker extends SwingWorker<Integer, String> {
        private final File pdfFile;
        private final File outputDir;
        private final String baseName;
        private final String imageFormat;
        private int totalPages = 0;

        public PdfToImageConversionWorker(File pdfFile, File outputDir, String baseName, String imageFormat) {
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
                publish("PDF has " + totalPages + " page(s). Converting to " + imageFormat.toUpperCase() + "...");

                for (int pageIndex = 0; pageIndex < totalPages; pageIndex++) {
                    if (isCancelled()) {
                        publish("Conversion cancelled.");
                        break;
                    }
                    BufferedImage bim = pdfRenderer.renderImageWithDPI(pageIndex, DEFAULT_DPI, ImageType.RGB);
                    String outputFileName = String.format("%s_page_%d.%s", baseName, pageIndex + 1, imageFormat);
                    File outputFile = new File(outputDir, outputFileName);
                    boolean success = ImageIO.write(bim, imageFormat, outputFile);
                    if (success) {
                        publish("Successfully converted page " + (pageIndex + 1) + " to " + outputFile.getName());
                        pagesConverted++;
                    } else {
                        publish("ERROR: Failed to write image for page " + (pageIndex + 1));
                        LOGGER.error("Failed to write image for page {} to format {}", pageIndex + 1, imageFormat);
                    }
                    int progress = (int) (((pageIndex + 1.0) / totalPages) * 100);
                    setProgress(progress);
                }
            } catch (IOException ex) {
                publish("ERROR during PDF processing: " + ex.getMessage());
                LOGGER.error("Error during PDF processing: {}", ex.getMessage(), ex);
                throw ex;
            }
            return pagesConverted;
        }

        @Override
        protected void process(List<String> chunks) {
            for (String message : chunks) {
                statusArea.append(message + "\n");
            }
        }

        @Override
        protected void done() {
            try {
                Integer pagesConverted = get();
                if (pagesConverted != null) {
                    statusArea.append("Conversion finished. " + pagesConverted + " page(s) converted.\n");
                    if (pagesConverted == totalPages && totalPages > 0) {
                        JOptionPane.showMessageDialog(PDFConverter.this,
                                "Conversion successful! " + pagesConverted + " page(s) converted.",
                                "Conversion Complete", JOptionPane.INFORMATION_MESSAGE);
                    } else if (totalPages > 0 && pagesConverted < totalPages && pagesConverted > 0) {
                        JOptionPane.showMessageDialog(PDFConverter.this,
                                "Conversion partially complete. " + pagesConverted + "/" + totalPages + " page(s) converted.",
                                "Conversion Warning", JOptionPane.WARNING_MESSAGE);
                    } else if (totalPages == 0 && pagesConverted == 0) { // If PDF was empty or unreadable
                         JOptionPane.showMessageDialog(PDFConverter.this,
                                "No pages found or converted from the PDF.",
                                "Conversion Info", JOptionPane.INFORMATION_MESSAGE);
                    }
                }
            } catch (InterruptedException e) {
                statusArea.append("Conversion interrupted: " + e.getMessage() + "\n");
                LOGGER.warn("Conversion interrupted", e);
                Thread.currentThread().interrupt();
            } catch (ExecutionException e) {
                statusArea.append("Error during conversion: " + e.getCause().getMessage() + "\n");
                LOGGER.error("ExecutionException during conversion", e.getCause());
                JOptionPane.showMessageDialog(PDFConverter.this,
                        "An error occurred during PDF to Image conversion: " + e.getCause().getMessage(),
                        "Conversion Error", JOptionPane.ERROR_MESSAGE);
            } finally {
                convertButton.setEnabled(true);
                progressBar.setVisible(false);
                statusArea.append("------------------------------------\n");
            }
        }
    }

    /**
     * SwingWorker to perform Image to PDF conversion in the background.
     */
    private class ImageToPdfWorker extends SwingWorker<Void, String> {
        private final List<File> imageFiles; // Prepare for multiple files, though currently using one
        private final File outputDir;
        private final String outputPdfName; // Name without .pdf extension

        public ImageToPdfWorker(List<File> imageFiles, File outputDir, String outputPdfName) {
            this.imageFiles = imageFiles; // Currently, this will be a list with one file
            this.outputDir = outputDir;
            this.outputPdfName = outputPdfName;
        }

        @Override
        protected Void doInBackground() throws Exception {
            // For now, we assume imageFiles contains one file as per current GUI
            if (imageFiles.isEmpty()) {
                publish("No image files selected for PDF conversion.");
                return null;
            }

            File imageFile = imageFiles.get(0); // Get the single selected image file

            try (PDDocument document = new PDDocument()) {
                publish("Processing image: " + imageFile.getName());

                BufferedImage bImage = ImageIO.read(imageFile);
                if (bImage == null) {
                    publish("ERROR: Could not read image file: " + imageFile.getName());
                    throw new IOException("Could not read image file: " + imageFile.getName());
                }
                
                // Create a page with the same dimensions as the image
                PDPage page = new PDPage(new PDRectangle(bImage.getWidth(), bImage.getHeight()));
                document.addPage(page);

                PDImageXObject pdImage = PDImageXObject.createFromFileByContent(imageFile, document);

                try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                    // Draw the image on the page, fitting it to the page size
                    contentStream.drawImage(pdImage, 0, 0, page.getMediaBox().getWidth(), page.getMediaBox().getHeight());
                }
                
                setProgress(50); // Arbitrary progress for single image

                File outputFile = new File(outputDir, outputPdfName + ".pdf");
                document.save(outputFile);
                publish("Successfully converted " + imageFile.getName() + " to " + outputFile.getName());
                setProgress(100);

            } catch (IOException ex) {
                publish("ERROR during Image to PDF conversion: " + ex.getMessage());
                LOGGER.error("Error during Image to PDF conversion for file {}: {}", imageFile.getName(), ex.getMessage(), ex);
                throw ex; // Re-throw to be caught by done()
            }
            return null;
        }

        @Override
        protected void process(List<String> chunks) {
            for (String message : chunks) {
                statusArea.append(message + "\n");
            }
        }

        @Override
        protected void done() {
            try {
                get(); // Call get to throw exceptions from doInBackground
                // If no exception, it was successful for the single image case
                 if (!isCancelled()){
                    statusArea.append("Image to PDF conversion finished.\n");
                    JOptionPane.showMessageDialog(PDFConverter.this,
                            "Image successfully converted to " + outputPdfName + ".pdf",
                            "Conversion Complete", JOptionPane.INFORMATION_MESSAGE);
                 }
            } catch (InterruptedException e) {
                statusArea.append("Image to PDF conversion interrupted: " + e.getMessage() + "\n");
                LOGGER.warn("Image to PDF conversion interrupted", e);
                Thread.currentThread().interrupt();
            } catch (ExecutionException e) {
                statusArea.append("Error during Image to PDF conversion: " + e.getCause().getMessage() + "\n");
                LOGGER.error("ExecutionException during Image to PDF conversion", e.getCause());
                JOptionPane.showMessageDialog(PDFConverter.this,
                        "An error occurred during Image to PDF conversion: " + e.getCause().getMessage(),
                        "Conversion Error", JOptionPane.ERROR_MESSAGE);
            } finally {
                convertButton.setEnabled(true);
                progressBar.setVisible(false);
                statusArea.append("------------------------------------\n");
            }
        }
    }


    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            LOGGER.warn("Could not set system Look and Feel.", e);
        }

        SwingUtilities.invokeLater(() -> {
            PDFConverter gui = new PDFConverter();
            gui.setVisible(true);
        });
    }
}
