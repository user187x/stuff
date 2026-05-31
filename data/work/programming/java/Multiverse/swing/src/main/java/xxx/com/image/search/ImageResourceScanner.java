package xxx.com.image.search;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Font;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

/**
 * A utility to scan a Java project directory for image resource references.
 * It parses .java files to find filenames of common image types and then
 * locates those files within the project structure, listing their absolute paths.
 */
public class ImageResourceScanner extends JFrame {

  private final JTextArea resultArea;
  private final JButton scanButton;
  private final JLabel statusLabel;

  public ImageResourceScanner() {
    setTitle("Image Resource Scanner");
    setSize(800, 600);
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    setLocationRelativeTo(null);

    // --- UI Components ---
    scanButton = new JButton("Select Project Directory or File and Scan for Images");
    statusLabel = new JLabel("Please select a directory to scan.", SwingConstants.CENTER);
    resultArea = new JTextArea("Found image paths will be listed here...");
    resultArea.setEditable(false);
    resultArea.setFont(new Font("Monospaced", Font.PLAIN, 12));

    JScrollPane scrollPane = new JScrollPane(resultArea);

    // --- Layout ---
    Container contentPane = getContentPane();
    contentPane.setLayout(new BorderLayout(10, 10));
    contentPane.add(scanButton, BorderLayout.NORTH);
    contentPane.add(scrollPane, BorderLayout.CENTER);
    contentPane.add(statusLabel, BorderLayout.SOUTH);

    ((JPanel) contentPane).setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));


    // --- Action Listener ---
    scanButton.addActionListener(e -> selectDirectoryAndScan());
  }

  private void selectDirectoryAndScan() {
    JFileChooser chooser = new JFileChooser();
    chooser.setDialogTitle("Select Project Directory or a File to Scan");
    chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
    chooser.setAcceptAllFileFilterUsed(true);

    if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
      File selection = chooser.getSelectedFile();

      Path rootDir;
      if (selection.isDirectory()) {
        rootDir = selection.toPath();
      } else {
        // If a file is selected, scan its parent directory
        rootDir = selection.getParentFile().toPath();
      }

      statusLabel.setText("Scanning in directory: " + rootDir.toString());
      resultArea.setText("Scanning... please wait.\n");

      // Run the scan in a background thread to keep the GUI responsive
      new Thread(() -> {
        try {
          Set<String> imagePaths = findImageResources(rootDir);

          // Update GUI on the Event Dispatch Thread
          SwingUtilities.invokeLater(() -> {
            if (imagePaths.isEmpty()) {
              resultArea.setText("No image resource references found in .java files.");
            } else {
              resultArea.setText(String.join("\n", imagePaths));
            }
            statusLabel.setText("Scan complete. Found " + imagePaths.size() + " unique image resources.");
          });

        } catch (IOException ex) {
          ex.printStackTrace();
          SwingUtilities.invokeLater(() -> {
            statusLabel.setText("An error occurred during the scan.");
            resultArea.setText("Error: " + ex.getMessage());
          });
        }
      }).start();
    }
  }

  /**
   * Scans a root directory, finds all .java files, extracts image references,
   * and finds the absolute paths of those images.
   *
   * @param rootDir The root directory of the project to scan.
   * @return A Set of unique, absolute paths to the found image files.
   * @throws IOException If an I/O error occurs while walking the file tree.
   */
  public Set<String> findImageResources(Path rootDir) throws IOException {
    // A pattern to find strings ending with image file extensions in Java code.
    // It looks for quoted strings like "path/to/image.png".
    Pattern imagePattern = Pattern.compile("\"([^\"]*\\.(?i)(png|jpg|jpeg|gif|bmp))\"");
    Set<String> foundRelativePaths = new HashSet<>();

    // 1. Walk the directory to find all .java files
    List<Path> javaFiles;
    try (Stream<Path> paths = Files.walk(rootDir)) {
      javaFiles = paths
          .filter(Files::isRegularFile)
          .filter(path -> path.toString().endsWith(".java"))
          .collect(Collectors.toList());
    }

    System.out.println("Found " + javaFiles.size() + " Java source files to analyze.");

    // 2. Read each Java file and find potential image references
    for (Path javaFile : javaFiles) {
      String content = new String(Files.readAllBytes(javaFile));
      Matcher matcher = imagePattern.matcher(content);
      while (matcher.find()) {
        // The first group (group(1)) captures the full path inside the quotes.
        String imagePath = matcher.group(1).replace("/", File.separator); // Normalize path separators
        foundRelativePaths.add(imagePath);
      }
    }

    System.out.println("Found " + foundRelativePaths.size() + " unique image references: " + foundRelativePaths);


    // 3. Search the entire project directory for files matching the discovered relative paths.
    Set<String> absolutePaths = new HashSet<>();
    for (String relativePath : foundRelativePaths) {
      try (Stream<Path> fileStream = Files.walk(rootDir)) {
        fileStream
            .filter(Files::isRegularFile)
            .filter(path -> path.toString().endsWith(relativePath))
            .findFirst()
            .ifPresent(path -> absolutePaths.add(path.toAbsolutePath().toString()));
      }
    }

    System.out.println("Resolved " + absolutePaths.size() + " absolute file paths.");
    return absolutePaths;
  }

  public static void main(String[] args) {
    SwingUtilities.invokeLater(() -> new ImageResourceScanner().setVisible(true));
  }
}

