package xxx.com.pdf;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.io.Serial;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

/**
 * A Java Swing GUI application to read PDF files from a selected directory, extract text, find the
 * value after "Student's Name: ", and save duplicate PDFs with the extracted student's name as the
 * filename. Users can select the input directory containing PDFs and the output directory via the
 * GUI.
 */
public class PDFExtractionGUI extends JFrame {

  private static final long serialVersionUID = -3088495441318138839L;
  // UI Components
  private final JTextField pdfDirectoryPathField;
  private final JTextField outputPathField;
  private final JTextArea statusArea;
  private final JList<PdfEntry> pdfList;
  private final DefaultListModel<PdfEntry> listModel;
  private final JButton browsePdfDirectoryButton;
  private final JButton browseOutputButton;
  private final JButton processPdfsButton;
  private final JProgressBar progressBar;

  // Context Menu Components
  private final JPopupMenu contextMenu;
  private final JMenuItem convertMenuItem;
  private final JMenuItem openMenuItem;
  private final JMenuItem showPathMenuItem; // New: Show Path menu item

  // Store the actual output directory for the popup
  private File currentOutputDirectory;

  /**
   * Constructor to set up the Swing GUI.
   */
  public PDFExtractionGUI() {
    super("PDF Renamer");
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    setSize(1000, 600);
    setLocationRelativeTo(null);

    JPanel mainPanel = new JPanel(new GridBagLayout());
    mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

    GridBagConstraints gbc = new GridBagConstraints();
    gbc.insets = new Insets(8, 8, 8, 8);
    gbc.fill = GridBagConstraints.HORIZONTAL;

    // 1. PDF Directory Selection
    JLabel pdfLabel = new JLabel("PDF Directory:");
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.anchor = GridBagConstraints.WEST;
    mainPanel.add(pdfLabel, gbc);

    pdfDirectoryPathField = new JTextField(40);
    pdfDirectoryPathField.setEditable(false);
    gbc.gridx = 1;
    gbc.gridy = 0;
    gbc.weightx = 1.0;
    mainPanel.add(pdfDirectoryPathField, gbc);

    browsePdfDirectoryButton = new JButton("Browse");
    browsePdfDirectoryButton.setToolTipText("Select the directory where the PDF files are located");
    gbc.gridx = 2;
    gbc.gridy = 0;
    gbc.weightx = 0;
    mainPanel.add(browsePdfDirectoryButton, gbc);

    // 2. Output Directory Selection
    JLabel outputLabel = new JLabel("Output Directory:");
    gbc.gridx = 0;
    gbc.gridy = 1;
    gbc.anchor = GridBagConstraints.WEST;
    mainPanel.add(outputLabel, gbc);

    outputPathField = new JTextField(40);
    outputPathField.setEditable(false);
    gbc.gridx = 1;
    gbc.gridy = 1;
    gbc.weightx = 1.0;
    mainPanel.add(outputPathField, gbc);

    browseOutputButton = new JButton("Browse");
    browseOutputButton.setToolTipText("Select the directory where the renamed PDFs will be saved.");
    gbc.gridx = 2;
    gbc.gridy = 1;
    gbc.weightx = 0;
    mainPanel.add(browseOutputButton, gbc);

    // 3. Progress Bar
    progressBar = new JProgressBar();
    progressBar.setStringPainted(true);
    progressBar.setString("Scanning for PDFs...");
    progressBar.setIndeterminate(true);
    progressBar.setVisible(false);
    gbc.gridx = 0;
    gbc.gridy = 2;
    gbc.gridwidth = 3;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.anchor = GridBagConstraints.CENTER;
    mainPanel.add(progressBar, gbc);

    // 4. Process Button
    processPdfsButton = new JButton("Process Files");
    processPdfsButton.setFont(
        new Font(
            processPdfsButton.getFont().getName(),
            Font.BOLD,
            processPdfsButton.getFont().getSize() + 2));

    gbc.gridx = 0;
    gbc.gridy = 3;
    gbc.gridwidth = 3;
    gbc.fill = GridBagConstraints.NONE;
    gbc.anchor = GridBagConstraints.CENTER;
    mainPanel.add(processPdfsButton, gbc);

    // 5. PDF List Panel (Bottom Panel)
    listModel = new DefaultListModel<>();
    pdfList = new JList<>(listModel);
    pdfList.setCellRenderer(new PdfListCellRenderer());
    JScrollPane pdfListScrollPane = new JScrollPane(pdfList);
    pdfListScrollPane.setBorder(
        BorderFactory.createTitledBorder("PDF Files ( File Path | Name )"));

    // 6. Status Area Panel (Top Panel)
    statusArea = new JTextArea(8, 50);
    statusArea.setEditable(false);
    statusArea.setLineWrap(true);
    statusArea.setWrapStyleWord(true);
    statusArea.setBackground(new Color(240, 240, 240)); // Set light gray background
    JScrollPane statusScrollPane = new JScrollPane(statusArea);
    statusScrollPane.setBorder(BorderFactory.createTitledBorder("Processing Status"));

    // 7. Use JSplitPane to stack the Status Area and PDF List vertically
    JSplitPane splitPane =
        new JSplitPane(JSplitPane.VERTICAL_SPLIT, statusScrollPane, pdfListScrollPane);
    splitPane.setResizeWeight(0.2); // Give 20% of the space to the top panel (status area)
    splitPane.setContinuousLayout(true);
    splitPane.setOneTouchExpandable(true);

    // Add the split pane to the main panel
    gbc.gridx = 0;
    gbc.gridy = 4;
    gbc.gridwidth = 3;
    gbc.weightx = 1.0;
    gbc.weighty = 1.0;
    gbc.fill = GridBagConstraints.BOTH; // Make it fill the available space
    mainPanel.add(splitPane, gbc);

    add(mainPanel);

    // Initialize Context Menu
    contextMenu = new JPopupMenu();
    openMenuItem = new JMenuItem("Open");
    convertMenuItem = new JMenuItem("Convert Selected");
    showPathMenuItem = new JMenuItem("Show Path"); // New: Initialize Show Path menu item

    contextMenu.add(openMenuItem);
    contextMenu.add(convertMenuItem);
    contextMenu.addSeparator(); // Add a separator for better organization
    contextMenu.add(showPathMenuItem); // Add Show Path

    // Add action listener for the Convert menu item
    convertMenuItem.addActionListener(e -> convertSelectedPdfs());
    // Add action listener for the Open menu item
    openMenuItem.addActionListener(e -> openSelectedPdfs());
    // Add action listener for the Show Path menu item
    showPathMenuItem.addActionListener(e -> showFilePathPopup());

    // Add MouseListener to JList for right-click context menu
    pdfList.addMouseListener(
        new MouseAdapter() {
          public void mousePressed(MouseEvent e) {
            if (e.isPopupTrigger()) {
              showPopupMenu(e);
            }
          }

          public void mouseReleased(MouseEvent e) {
            if (e.isPopupTrigger()) {
              showPopupMenu(e);
            }
          }

          private void showPopupMenu(MouseEvent e) {
            // Get the index of the item under the mouse
            int index = pdfList.locationToIndex(e.getPoint());
            if (index != -1) {
              // If multiple items are already selected, don't change selection
              // If only one item is selected or no items are selected, select the clicked item
              if (pdfList.getSelectedIndices().length <= 1 || !pdfList.isSelectedIndex(index)) {
                pdfList.setSelectedIndex(index);
              }
            }
            else {
              // If clicked on empty space, clear selection
              pdfList.clearSelection();
            }
            // Only show if there's at least one item selected
            if (!pdfList.isSelectionEmpty()) {
              contextMenu.show(e.getComponent(), e.getX(), e.getY());
            }
          }
        });

    // Add action listeners to the buttons
    browsePdfDirectoryButton.addActionListener(e -> selectPdfDirectory());
    browseOutputButton.addActionListener(e -> selectOutputDirectory());
    processPdfsButton.addActionListener(e -> processAllListedPdfs());
  }

  /**
   * Opens a JFileChooser to allow the user to select a directory containing PDF files. Updates the
   * pdfDirectoryPathField with the selected directory's path and then automatically triggers the
   * search for applicable PDFs in a background thread.
   */
  private void selectPdfDirectory() {
    String userDesktop = System.getProperty("user.home") + File.separator + "Desktop";
    JFileChooser fileChooser = new JFileChooser(userDesktop);

    fileChooser.setDialogTitle("Select Directory Containing PDF Files");
    fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
    int userSelection = fileChooser.showOpenDialog(this);

    if (userSelection == JFileChooser.APPROVE_OPTION) {
      File selectedDirectory = fileChooser.getSelectedFile();
      pdfDirectoryPathField.setText(selectedDirectory.getAbsolutePath());
      logStatus("ⓘ Directory Selected: " + selectedDirectory.getAbsolutePath());
      statusArea.setText(""); // Clear status area for a fresh start
      listModel.clear(); // Clear the list for a new search

      // Disable buttons and show progress bar
      setGuiEnabledState(false);
      progressBar.setVisible(true);
      progressBar.setIndeterminate(true);
      progressBar.setString("Scanning for applicable PDFs...");

      // Start the background task to find and list applicable PDFs
      new PdfSearchWorker(selectedDirectory).execute();
    }
  }

  /**
   * Opens a JFileChooser to allow the user to select an output directory. Updates the outputPathField
   * with the selected directory's path.
   *
   * @return true if a directory was selected, false if cancelled.
   */
  private boolean selectOutputDirectory() {
    String userDesktop = System.getProperty("user.home") + File.separator + "Desktop";
    JFileChooser fileChooser = new JFileChooser(userDesktop);

    fileChooser.setDialogTitle("Select Output Directory");
    fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
    int userSelection = fileChooser.showSaveDialog(this);

    if (userSelection == JFileChooser.APPROVE_OPTION) {
      File selectedDirectory = fileChooser.getSelectedFile();
      outputPathField.setText(selectedDirectory.getAbsolutePath());
      logStatus("Output directory selected: " + selectedDirectory.getAbsolutePath());
      currentOutputDirectory =
          new File(selectedDirectory, "UpdatedPDFs"); // Store the actual target folder
      return true;
    }
    else {
      logStatus("Output directory selection cancelled.");
      return false;
    }
  }

  /**
   * Logs messages to the status JTextArea.
   *
   * @param message The message to log.
   */
  private void logStatus(String message) {
    SwingUtilities.invokeLater(
        () -> {
          statusArea.append(message + "\n");
          statusArea.setCaretPosition(statusArea.getDocument().getLength());
        });
  }

  /**
   * Sets the enabled state of main GUI components.
   *
   * @param enabled true to enable, false to disable.
   */
  private void setGuiEnabledState(boolean enabled) {
    browsePdfDirectoryButton.setEnabled(enabled);
    browseOutputButton.setEnabled(enabled);
    processPdfsButton.setEnabled(enabled);
    pdfList.setEnabled(enabled);
    convertMenuItem.setEnabled(enabled);
    openMenuItem.setEnabled(enabled);
    showPathMenuItem.setEnabled(enabled); // Enable/disable Show Path menu item
  }

  /**
   * Helper method to recursively find all PDF files within a given directory and its subdirectories.
   * This method does not check for the "Student's Name:" pattern.
   *
   * @param directory The starting directory to search.
   * @param pdfFiles A list to populate with all found PDF files.
   */
  private void findPdfFilesRecursive(File directory, List<File> pdfFiles) {
    File[] files = directory.listFiles();
    if (files != null) {
      for (File file : files) {
        if (file.isDirectory()) {
          findPdfFilesRecursive(file, pdfFiles);
        }
        else if (file.isFile() && file.getName().toLowerCase().endsWith(".pdf")) {
          pdfFiles.add(file);
        }
      }
    }
  }

  /** SwingWorker to perform the PDF search and content check in a background thread. */
  private class PdfSearchWorker extends SwingWorker<List<PdfEntry>, PdfEntry> {
    private final File inputDirectory;
    private final PdfStudentNameExtractor extractor;
    private int totalPdfsScanned = 0;

    public PdfSearchWorker(File inputDirectory) {
      this.inputDirectory = inputDirectory;
      this.extractor = new PdfStudentNameExtractor();
    }

    @Override
    protected List<PdfEntry> doInBackground() throws Exception {
      logStatus("» Searching for applicable PDF files ");

      List<File> allPdfs = new ArrayList<>();
      findPdfFilesRecursive(inputDirectory, allPdfs);

      if (allPdfs.isEmpty()) {
        return new ArrayList<>();
      }

      SwingUtilities.invokeLater(
          () -> {
            progressBar.setIndeterminate(false);
            progressBar.setMaximum(allPdfs.size());
            progressBar.setValue(0);
            progressBar.setString("Scanning 0/" + allPdfs.size() + " PDFs...");
          });

      List<PdfEntry> applicablePdfs = new ArrayList<>();
      for (File pdfFile : allPdfs) {
        PDDocument document = null;
        try {
          document = Loader.loadPDF(pdfFile);
          PDFTextStripper textStripper = new PDFTextStripper();
          String fullText = textStripper.getText(document);

          if (fullText.contains("Student's Name:")) {
            String studentName = extractor.extractStudentName(document);
            PdfEntry entry = new PdfEntry(pdfFile, studentName);
            applicablePdfs.add(entry);
            publish(entry);
          }
        }
        catch (IOException e) {
          logStatus("Error reading PDF file '" + pdfFile.getName() + "': " + e.getMessage());
        }
        finally {
          if (document != null) {
            try {
              document.close();
            }
            catch (IOException e) {
              // Suppress, already logged by logStatus if needed
            }
          }
        }
        totalPdfsScanned++;
        SwingUtilities.invokeLater(
            () -> {
              progressBar.setValue(totalPdfsScanned);
              progressBar.setString(
                  "Scanning " + totalPdfsScanned + "/" + allPdfs.size() + " PDFs...");
            });
      }
      return applicablePdfs;
    }

    @Override
    protected void process(List<PdfEntry> chunks) {
      for (PdfEntry entry : chunks) {
        listModel.addElement(entry);
      }
    }

    @Override
    protected void done() {
      try {
        List<PdfEntry> result = get();
        logStatus(" → Total PDF Files Found : " + result.size());
      }
      catch (Exception e) {
        logStatus("An error occurred during PDF search: " + e.getMessage());
        e.printStackTrace();
      }
      finally {
        // logStatus("» PDF Search Finished ");
        progressBar.setVisible(false);
        progressBar.setIndeterminate(false);
        progressBar.setValue(0);
        progressBar.setString("");
        setGuiEnabledState(true);
      }
    }
  }

  /**
   * Opens the selected PDF files in the JList using the operating system's default application.
   */
  private void openSelectedPdfs() {
    List<PdfEntry> selectedPdfs = pdfList.getSelectedValuesList();

    if (selectedPdfs.isEmpty()) {
      logStatus("No PDF files selected to open.");
      return;
    }

    if (!Desktop.isDesktopSupported()) {
      logStatus("Desktop API is not supported on this platform. Cannot open files automatically.");
      return;
    }

    Desktop desktop = Desktop.getDesktop();
    for (PdfEntry pdfEntry : selectedPdfs) {
      File pdfFile = pdfEntry.getPdfFile();
      if (pdfFile.exists()
          && pdfFile.isFile()
          && pdfFile.getName().toLowerCase().endsWith(".pdf")) {
        try {
          logStatus("Attempting to open: " + pdfFile.getAbsolutePath());
          desktop.open(pdfFile);
        }
        catch (IOException e) {
          logStatus("Error opening '" + pdfFile.getName() + "': " + e.getMessage());
        }
        catch (IllegalArgumentException e) {
          logStatus(
              "Invalid file path for opening: '"
                  + pdfFile.getName()
                  + "'. Error: "
                  + e.getMessage());
        }
      }
      else {
        logStatus("Skipping invalid file: " + pdfFile.getAbsolutePath());
      }
    }
    logStatus("» Attempted to open selected PDF files ");
  }

  /**
   * Displays a popup dialog with the full path of the selected PDF file and a button to open its
   * containing directory in the system's file browser.
   */
  private void showFilePathPopup() {
    if (pdfList.getSelectedIndices().length != 1) {
      logStatus("Please select exactly one PDF file to show its path.");
      JOptionPane.showMessageDialog(
          this,
          "Please select exactly one PDF file.",
          "Selection Error",
          JOptionPane.WARNING_MESSAGE);
      return;
    }

    PdfEntry selectedEntry = pdfList.getSelectedValue();
    File selectedFile = selectedEntry.getPdfFile();
    String fullPath = selectedFile.getAbsolutePath();
    File parentDir = selectedFile.getParentFile();

    // Create a panel to hold the message content for better layout.
    JPanel panel = new JPanel(new BorderLayout(5, 5));
    panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

    JLabel infoLabel = new JLabel("Full path of the selected file:");
    panel.add(infoLabel, BorderLayout.NORTH);

    // Use a JTextArea to display the path, allowing wrapping and selection.
    JTextArea pathArea = new JTextArea(fullPath);
    pathArea.setWrapStyleWord(true);
    pathArea.setLineWrap(true);
    pathArea.setEditable(false);
    pathArea.setBackground(panel.getBackground()); // Match the panel's background
    JScrollPane scrollPane = new JScrollPane(pathArea);
    scrollPane.setBorder(BorderFactory.createEtchedBorder()); // Add a subtle border
    panel.add(scrollPane, BorderLayout.CENTER);

    // Define the options for the dialog.
    String[] options = {"Open Containing Directory", "Close"};

    // Show the dialog with the custom panel and options.
    int choice =
        JOptionPane.showOptionDialog(
            this, // Parent component
            panel, // Message
            "File Path Information", // Title
            JOptionPane.DEFAULT_OPTION, // optionType
            JOptionPane.PLAIN_MESSAGE, // messageType
            null, // Icon
            options, // Options
            options[0] // Initial value
        );

    // Check which button was clicked. "Open Containing Directory" is at index 0.
    if (choice == 0) {
      openContainingDirectory(parentDir);
    }
  }

  /**
   * Attempts to open the containing directory of a file in the system's file explorer.
   *
   * @param directory The File object representing the directory to open.
   */
  private void openContainingDirectory(File directory) {
    if (directory != null && directory.exists() && directory.isDirectory()) {
      if (Desktop.isDesktopSupported()) {
        try {
          Desktop.getDesktop().open(directory);
          logStatus("Opened directory: " + directory.getAbsolutePath());
        }
        catch (IOException e) {
          logStatus("Error opening directory: " + e.getMessage());
          JOptionPane.showMessageDialog(
              this,
              "Could not open the directory:\n" + e.getMessage(),
              "Error",
              JOptionPane.ERROR_MESSAGE);
        }
      }
      else {
        logStatus(
            "Desktop API is not supported on this platform. Cannot open directory automatically.");
        JOptionPane.showMessageDialog(
            this,
            "This feature is not supported on your operating system.",
            "Unsupported Action",
            JOptionPane.WARNING_MESSAGE);
      }
    }
    else {
      String path = (directory != null ? directory.getAbsolutePath() : "null");
      logStatus("Directory does not exist or is not a valid directory: " + path);
      JOptionPane.showMessageDialog(
          this,
          "The directory could not be found:\n" + path,
          "Directory Not Found",
          JOptionPane.ERROR_MESSAGE);
    }
  }

  /**
   * Converts the currently selected PDF files in the JList. Prompts for an output directory if one is
   * not already selected.
   */
  private void convertSelectedPdfs() {
    List<PdfEntry> selectedPdfs = pdfList.getSelectedValuesList();

    if (selectedPdfs.isEmpty()) {
      logStatus("No PDF files selected for conversion.");
      return;
    }

    // Check if an output directory is selected. If not, open file picker.
    if (outputPathField.getText().isEmpty()) {
      logStatus("Output directory not selected. Opening dialog to select output directory...");
      if (!selectOutputDirectory()) {
        // User cancelled output directory selection
        return;
      }
    }

    // Ensure currentOutputDirectory is set, or re-derive it
    if (currentOutputDirectory == null
        || !currentOutputDirectory
            .getAbsolutePath()
            .equals(new File(outputPathField.getText(), "UpdatedPDFs").getAbsolutePath())) {
      currentOutputDirectory = new File(outputPathField.getText(), "UpdatedPDFs");
    }

    // Disable buttons and show progress bar for conversion
    setGuiEnabledState(false);
    progressBar.setVisible(true);
    progressBar.setIndeterminate(false);
    progressBar.setMaximum(selectedPdfs.size());
    progressBar.setValue(0);
    progressBar.setString("Converting 0/" + selectedPdfs.size() + " PDFs...");

    // Start the conversion worker
    new PdfConversionWorker(selectedPdfs, new File(outputPathField.getText())).execute();
  }

  /** Processes all PDF files currently listed in the JList. */
  private void processAllListedPdfs() {
    List<PdfEntry> allListedPdfs = new ArrayList<>();
    for (int i = 0; i < listModel.size(); i++) {
      allListedPdfs.add(listModel.getElementAt(i));
    }

    if (allListedPdfs.isEmpty()) {
      logStatus(
          "No PDF files listed to process. Please select a PDF directory and let the application find applicable PDFs.");
      return;
    }

    // Check if an output directory is selected. If not, open file picker.
    if (outputPathField.getText().isEmpty()) {
      logStatus("Output directory not selected. Opening dialog to select output directory...");
      if (!selectOutputDirectory()) {
        // User cancelled output directory selection
        return;
      }
    }

    // Ensure currentOutputDirectory is set, or re-derive it
    if (currentOutputDirectory == null
        || !currentOutputDirectory
            .getAbsolutePath()
            .equals(new File(outputPathField.getText(), "UpdatedPDFs").getAbsolutePath())) {
      currentOutputDirectory = new File(outputPathField.getText(), "UpdatedPDFs");
    }

    // Disable buttons and show progress bar for conversion
    setGuiEnabledState(false);
    progressBar.setVisible(true);
    progressBar.setIndeterminate(false);
    progressBar.setMaximum(allListedPdfs.size());
    progressBar.setValue(0);
    progressBar.setString("Converting 0/" + allListedPdfs.size() + " PDFs...");

    // Start the conversion worker for all listed PDFs
    new PdfConversionWorker(allListedPdfs, new File(outputPathField.getText())).execute();
  }

  /** SwingWorker to handle the PDF conversion process in a background thread. */
  private class PdfConversionWorker extends SwingWorker<Void, String> {
    private final List<PdfEntry> pdfsToConvert;
    private final File outputBaseDirectory; // The user-selected output directory
    private File finalOutputDirectory; // The "UpdatedPDFs" subfolder
    private final PdfStudentNameExtractor extractor;
    private int convertedCount = 0;

    public PdfConversionWorker(List<PdfEntry> pdfsToConvert, File outputBaseDirectory) {
      this.pdfsToConvert = pdfsToConvert;
      this.outputBaseDirectory = outputBaseDirectory;
      this.extractor = new PdfStudentNameExtractor();
    }

    @Override
    protected Void doInBackground() throws Exception {
      logStatus("» Starting PDF Conversion ");

      // Create the "UpdatedPDFs" subfolder inside the selected base directory
      finalOutputDirectory = new File(outputBaseDirectory, "UpdatedPDFs");
      if (!finalOutputDirectory.exists()) {
        if (finalOutputDirectory.mkdirs()) {
          publish("Created output subdirectory: " + finalOutputDirectory.getAbsolutePath());
        }
        else {
          publish(
              "Error: Failed to create output subdirectory: "
                  + finalOutputDirectory.getAbsolutePath());
          return null; // Stop processing if directory cannot be created
        }
      }
      else {
        publish("Output subdirectory already exists: " + finalOutputDirectory.getAbsolutePath());
      }

      for (PdfEntry pdfEntry : pdfsToConvert) {
        File pdfFile = pdfEntry.getPdfFile();
        publish(
            "\n» Processing file: "
                + pdfFile.getName()
                + " (Path: "
                + pdfFile.getAbsolutePath()
                + ") ");
        PDDocument document = null;
        try {
          document = Loader.loadPDF(pdfFile);
          publish("Successfully loaded PDF: " + pdfFile.getName());

          String studentName = pdfEntry.getStudentName();
          if (studentName == null || studentName.isEmpty()) {
            studentName = extractor.extractStudentName(document);
          }

          if (studentName != null && !studentName.isEmpty()) {
            publish("Found Student's Name: '" + studentName + "' in " + pdfFile.getName());

            String formattedSanitizedName = extractor.formatAndSanitizeFilename(studentName);
            String newPdfFileName = formattedSanitizedName + ".pdf";
            String newPdfFilePath =
                new File(finalOutputDirectory, newPdfFileName).getAbsolutePath();

            document.save(newPdfFilePath);
            publish("Duplicate PDF successfully saved as: " + newPdfFilePath);

          }
          else {
            publish(
                "Could not find a valid student's name in '"
                    + pdfFile.getName()
                    + "'. No duplicate PDF was created.");
          }

        }
        catch (IOException e) {
          publish(
              "An I/O error occurred processing '" + pdfFile.getName() + "': " + e.getMessage());
          e.printStackTrace();
        }
        catch (Exception e) {
          publish(
              "An unexpected error occurred processing '"
                  + pdfFile.getName()
                  + "': "
                  + e.getMessage());
          e.printStackTrace();
        }
        finally {
          if (document != null) {
            try {
              document.close();
              publish("PDF document for '" + pdfFile.getName() + "' successfully closed.");
            }
            catch (IOException e) {
              // Suppress
            }
          }
        }
        convertedCount++;
        SwingUtilities.invokeLater(
            () -> {
              progressBar.setValue(convertedCount);
              progressBar.setString(
                  "Converting " + convertedCount + "/" + pdfsToConvert.size() + " PDFs...");
            });
      }
      return null;
    }

    @Override
    protected void process(List<String> chunks) {
      for (String message : chunks) {
        statusArea.append(message + "\n");
      }
      statusArea.setCaretPosition(statusArea.getDocument().getLength());
    }

    @Override
    protected void done() {
      logStatus("\n» PDF Conversion Finished ");
      progressBar.setVisible(false);
      progressBar.setIndeterminate(false);
      progressBar.setValue(0);
      progressBar.setString("");
      setGuiEnabledState(true);

      // Show success popup
      int option =
          JOptionPane.showOptionDialog(
              PDFExtractionGUI.this,
              "PDF conversion completed successfully!\n" + convertedCount + " files processed.",
              "Conversion Complete",
              JOptionPane.YES_NO_OPTION,
              JOptionPane.INFORMATION_MESSAGE,
              null,
              new String[] {"Open Output Directory", "Dismiss"},
              "Open Output Directory");

      if (option == JOptionPane.YES_OPTION) {
        openOutputDirectoryInExplorer(finalOutputDirectory);
      }
    }
  }

  /**
   * Attempts to open the specified directory in the system's file explorer.
   *
   * @param directory The File object representing the directory to open.
   */
  private void openOutputDirectoryInExplorer(File directory) {
    if (directory != null && directory.exists() && directory.isDirectory()) {
      if (Desktop.isDesktopSupported()) {
        try {
          Desktop.getDesktop().open(directory);
          logStatus("Opened output directory: " + directory.getAbsolutePath());
        }
        catch (IOException e) {
          logStatus("Error opening directory: " + e.getMessage());
        }
      }
      else {
        logStatus(
            "Desktop API is not supported on this platform. Cannot open directory automatically.");
      }
    }
    else {
      logStatus(
          "Output directory does not exist or is not a valid directory: "
              + (directory != null ? directory.getAbsolutePath() : "null"));
    }
  }

  /** Extracts the student's name from a loaded PDF document. */
  private static class PdfStudentNameExtractor {
    public String extractStudentName(PDDocument document) {
      try {
        PDFTextStripper pdfStripper = new PDFTextStripper();
        String text = pdfStripper.getText(document);

        Pattern pattern =
            Pattern.compile("Student's Name:\\s*(.*?)(?:\\r?\\n|$)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(text);

        if (matcher.find()) {
          return matcher.group(1).trim();
        }
        else {
          return null;
        }
      }
      catch (IOException e) {
        System.err.println("Error extracting text from PDF document: " + e.getMessage());
        return null;
      }
    }

    /**
     * Sanitizes a string to be suitable for a filename on Windows and formats it as "Last name, First
     * name".
     */
    private String formatAndSanitizeFilename(String name) {
      if (name == null || name.isEmpty()) {
        return "UnnamedStudent";
      }

      String sanitized = name.replaceAll("[\\\\/:*?\"<>|]", "_");
      sanitized = sanitized.trim().replaceAll("\\s+", " ");

      String firstName = "";
      String lastName = "";

      String[] nameParts = sanitized.split(" ");

      if (nameParts.length >= 2) {
        lastName = nameParts[nameParts.length - 1];
        StringBuilder sbFirstName = new StringBuilder();
        for (int i = 0; i < nameParts.length - 1; i++) {
          sbFirstName.append(nameParts[i]);
          if (i < nameParts.length - 2) {
            sbFirstName.append(" ");
          }
        }
        firstName = sbFirstName.toString();
      }
      else if (nameParts.length == 1) {
        firstName = nameParts[0];
        lastName = nameParts[0];
      }
      else {
        return "UnnamedStudent";
      }

      String formattedName = lastName + ", " + firstName;

      if (formattedName.endsWith(".")) {
        formattedName = formattedName.substring(0, formattedName.length() - 1);
      }
      return formattedName;
    }
  }

  /** A custom class to hold PDF file information for the JList. */
  private static class PdfEntry {
    private final File pdfFile;
    private final String studentName;

    public PdfEntry(File pdfFile, String studentName) {
      this.pdfFile = pdfFile;
      this.studentName = studentName;
    }

    public File getPdfFile() {
      return pdfFile;
    }

    public String getStudentName() {
      return studentName;
    }

    @Override
    public String toString() {
      return pdfFile.getAbsolutePath() + " | " + (studentName != null ? studentName : "N/A");
    }
  }

  /** Custom ListCellRenderer to display PDF file path and student name uniformly. */
  private static class PdfListCellRenderer extends JPanel implements ListCellRenderer<PdfEntry> {

    @Serial
    private static final long serialVersionUID = 5498461513200156037L;
    private final JLabel filePathLabel;
    private final JLabel studentNameLabel;

    // Arbitrary character limit for the visible path part (before truncation)
    // Adjust this value based on how much of the path you want to generally see
    private static final int MAX_DISPLAY_PATH_CHARS = 50;
    private static final String ELLIPSIS = "...";

    public PdfListCellRenderer() {
      setLayout(new GridBagLayout());
      GridBagConstraints gbcPanel = new GridBagConstraints();
      gbcPanel.insets = new Insets(1, 2, 1, 2);

      filePathLabel = new JLabel();
      gbcPanel.gridx = 0;
      gbcPanel.weightx = 0.7; // Path takes more space
      gbcPanel.fill = GridBagConstraints.HORIZONTAL;
      gbcPanel.anchor = GridBagConstraints.WEST;
      add(filePathLabel, gbcPanel);

      studentNameLabel = new JLabel();
      gbcPanel.gridx = 1;
      gbcPanel.weightx = 0.3; // Name takes less space
      gbcPanel.fill = GridBagConstraints.HORIZONTAL;
      gbcPanel.anchor = GridBagConstraints.EAST;
      add(studentNameLabel, gbcPanel);
    }

    @Override
    public Component getListCellRendererComponent(JList<? extends PdfEntry> list, PdfEntry value, int index, boolean isSelected, boolean cellHasFocus) {
      if (value != null) {
        String fullPath = value.getPdfFile().getAbsolutePath();
        String displayPath;

        // Simple character-based truncation from the beginning
        if (fullPath.length() > MAX_DISPLAY_PATH_CHARS) {
          // Show ellipsis at the beginning, and the last part of the path
          displayPath =
              ELLIPSIS
                  + fullPath.substring(
                      fullPath.length() - (MAX_DISPLAY_PATH_CHARS - ELLIPSIS.length()));
        }
        else {
          displayPath = fullPath;
        }
        filePathLabel.setText(displayPath);

        String displayName =
            (value.getStudentName() != null && !value.getStudentName().isEmpty())
                ? new PdfStudentNameExtractor().formatAndSanitizeFilename(value.getStudentName())
                : "N/A";
        studentNameLabel.setText(displayName);
      }
      else {
        filePathLabel.setText("");
        studentNameLabel.setText("");
      }

      if (isSelected) {
        setBackground(list.getSelectionBackground());
        setForeground(list.getSelectionForeground());
        filePathLabel.setForeground(list.getSelectionForeground());
        studentNameLabel.setForeground(list.getSelectionForeground());
      }
      else {
        setBackground(list.getBackground());
        setForeground(list.getForeground());
        filePathLabel.setForeground(list.getForeground());
        studentNameLabel.setForeground(list.getForeground());
      }

      setEnabled(list.isEnabled());
      setFont(list.getFont());
      setBorder(
          cellHasFocus
              ? BorderFactory.createLineBorder(list.getSelectionBackground())
              : BorderFactory.createEmptyBorder());

      return this;
    }
  }

  /**
   * Main method to run the Swing application. It ensures the GUI is created and updated on the Event
   * Dispatch Thread (EDT).
   */
  public static void main(String[] args) {
    SwingUtilities.invokeLater(
        () -> {
          PDFExtractionGUI frame = new PDFExtractionGUI();
          frame.setVisible(true);
        });
  }
}
