package xxx.com.zip;

import com.formdev.flatlaf.FlatDarkLaf;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * A simple modern GUI using FlatDarkLaf to demonstrate various compression types and options from Apache Commons Compress.
 * Allows selecting format, options, drag-and-drop input, and performing compression/decompression with save dialog.
 */
public class CompressionGUI extends JFrame {
  private JComboBox<String> formatCombo;
  private JPanel optionsPanel;
  private JButton compressButton;
  private JButton decompressButton;
  private JFileChooser fileChooser;
  private String inputPath;
  private JLabel dragLabel;

  // Options components
  private JSpinner levelSpinner;
  private JCheckBox zipStoreCheck;
  private JComboBox<String> tarModeCombo;
  private JCheckBox encryptCheck;
  private JPasswordField passwordField;
  private JComboBox<String> encryptionStrengthCombo;
  private JCheckBox deleteOriginalCheck;

  public CompressionGUI() {
    try {
      UIManager.setLookAndFeel(new FlatDarkLaf());
    } catch (UnsupportedLookAndFeelException e) {
      e.printStackTrace();
    }

    setTitle("Compression Demo GUI");
    setSize(600, 400);
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    setLayout(new BorderLayout(10, 10));

    // Top panel for format selection
    JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
    formatCombo = new JComboBox<>(new String[]{
        "ZIP", "TAR", "GZIP", "BZIP2", "7Z", "XZ", "Deflate", "LZMA", "LZ4", "Snappy", "Zstandard", "Pack200"
    });
    formatCombo.addActionListener(this::updateOptionsPanel);
    topPanel.add(new JLabel("Format:"));
    topPanel.add(formatCombo);

    // Options panel (dynamic, vertical layout for better look)
    optionsPanel = new JPanel();
    optionsPanel.setLayout(new BoxLayout(optionsPanel, BoxLayout.Y_AXIS));
    optionsPanel.setBorder(BorderFactory.createTitledBorder("Options"));
    updateOptionsPanel(null); // Initial update

    // Drag and drop panel
    JPanel dragPanel = new JPanel(new BorderLayout());
    dragPanel.setBorder(BorderFactory.createDashedBorder(null, 5f, 5));
    dragLabel = new JLabel("Drag and drop input file here", SwingConstants.CENTER);
    dragPanel.add(dragLabel, BorderLayout.CENTER);
    dragPanel.setTransferHandler(new FileTransferHandler());
    dragPanel.setPreferredSize(new Dimension(400, 200));

    // Buttons panel (vertical for modern feel)
    JPanel buttonPanel = new JPanel();
    buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.Y_AXIS));
    compressButton = new JButton("Compress");
    decompressButton = new JButton("Decompress");
    compressButton.setAlignmentX(Component.CENTER_ALIGNMENT);
    decompressButton.setAlignmentX(Component.CENTER_ALIGNMENT);
    compressButton.addActionListener(this::performCompress);
    decompressButton.addActionListener(this::performDecompress);
    buttonPanel.add(Box.createVerticalGlue());
    buttonPanel.add(compressButton);
    buttonPanel.add(Box.createRigidArea(new Dimension(0, 10)));
    buttonPanel.add(decompressButton);
    buttonPanel.add(Box.createVerticalGlue());

    // Main layout: North - format, Center - drag, West - options, East - buttons
    add(topPanel, BorderLayout.NORTH);
    add(new JScrollPane(optionsPanel), BorderLayout.WEST);
    add(dragPanel, BorderLayout.CENTER);
    add(buttonPanel, BorderLayout.EAST);

    fileChooser = new JFileChooser();
  }

  private void updateOptionsPanel(ActionEvent e) {
    optionsPanel.removeAll();
    levelSpinner = null;
    zipStoreCheck = null;
    tarModeCombo = null;
    encryptCheck = null;
    passwordField = null;
    encryptionStrengthCombo = null;
    deleteOriginalCheck = null;
    String format = (String) formatCombo.getSelectedItem();

    // Format-specific options
    if (format.equals("ZIP")) {
      JPanel levelPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
      levelPanel.add(new JLabel("Compression Level (0-9):"));
      levelSpinner = new JSpinner(new SpinnerNumberModel(9, 0, 9, 1));
      levelPanel.add(levelSpinner);
      optionsPanel.add(levelPanel);

      JPanel storePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
      storePanel.add(new JLabel("Store (no compression):"));
      zipStoreCheck = new JCheckBox();
      storePanel.add(zipStoreCheck);
      optionsPanel.add(storePanel);
    } else if (format.equals("TAR")) {
      JPanel modePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
      modePanel.add(new JLabel("Long File Mode:"));
      tarModeCombo = new JComboBox<>(new String[]{"POSIX", "GNU", "TRUNCATE"});
      modePanel.add(tarModeCombo);
      optionsPanel.add(modePanel);
    } else if (format.equals("GZIP")) {
      JPanel levelPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
      levelPanel.add(new JLabel("Compression Level (-1-9):"));
      levelSpinner = new JSpinner(new SpinnerNumberModel(9, -1, 9, 1));
      levelPanel.add(levelSpinner);
      optionsPanel.add(levelPanel);
    } else if (format.equals("BZIP2")) {
      JPanel blockPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
      blockPanel.add(new JLabel("Block Size (1-9):"));
      levelSpinner = new JSpinner(new SpinnerNumberModel(9, 1, 9, 1));
      blockPanel.add(levelSpinner);
      optionsPanel.add(blockPanel);
    } else if (format.equals("7Z")) {
      JPanel levelPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
      levelPanel.add(new JLabel("Compression Level (0-9):"));
      levelSpinner = new JSpinner(new SpinnerNumberModel(9, 0, 9, 1));
      levelPanel.add(levelSpinner);
      optionsPanel.add(levelPanel);
    } else if (format.equals("XZ")) {
      JPanel presetPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
      presetPanel.add(new JLabel("Preset Level (0-9):"));
      levelSpinner = new JSpinner(new SpinnerNumberModel(9, 0, 9, 1));
      presetPanel.add(levelSpinner);
      optionsPanel.add(presetPanel);
    } else if (format.equals("Deflate")) {
      JPanel levelPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
      levelPanel.add(new JLabel("Compression Level (0-9):"));
      levelSpinner = new JSpinner(new SpinnerNumberModel(9, 0, 9, 1));
      levelPanel.add(levelSpinner);
      optionsPanel.add(levelPanel);
    } else if (format.equals("Zstandard")) {
      JPanel levelPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
      levelPanel.add(new JLabel("Level (-131072 to 22):"));
      levelSpinner = new JSpinner(new SpinnerNumberModel(22, -131072, 22, 1));
      levelPanel.add(levelSpinner);
      optionsPanel.add(levelPanel);
    } else {
      JPanel noOptionsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
      noOptionsPanel.add(new JLabel("No specific options for " + format));
      optionsPanel.add(noOptionsPanel);
    }

    // Encryption options for supported formats
    if (format.equals("ZIP") || format.equals("7Z")) {
      JPanel encryptPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
      encryptPanel.add(new JLabel("Encrypt:"));
      encryptCheck = new JCheckBox();
      encryptPanel.add(encryptCheck);
      optionsPanel.add(encryptPanel);

      JPanel passwordPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
      passwordPanel.add(new JLabel("Password:"));
      passwordField = new JPasswordField(20);
      passwordPanel.add(passwordField);
      optionsPanel.add(passwordPanel);

      JPanel strengthPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
      strengthPanel.add(new JLabel("Encryption Strength:"));
      encryptionStrengthCombo = new JComboBox<>(new String[]{"AES-128", "AES-256"});
      strengthPanel.add(encryptionStrengthCombo);
      optionsPanel.add(strengthPanel);
    }

    // Delete original option (always available for compression)
    JPanel deletePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
    deletePanel.add(new JLabel("Delete original after compression:"));
    deleteOriginalCheck = new JCheckBox();
    deletePanel.add(deleteOriginalCheck);
    optionsPanel.add(deletePanel);

    optionsPanel.revalidate();
    optionsPanel.repaint();
  }

  private void performCompress(ActionEvent e) {
    if (inputPath == null || inputPath.isEmpty()) {
      JOptionPane.showMessageDialog(this, "Please drag an input file first.");
      return;
    }
    String format = (String) formatCombo.getSelectedItem();
    int level = levelSpinner != null ? (int) levelSpinner.getValue() : 9;
    String password = null;
    String strength = null;
    if (encryptCheck != null && encryptCheck.isSelected()) {
      password = new String(passwordField.getPassword());
      if (password.isEmpty()) {
        JOptionPane.showMessageDialog(this, "Please enter a password for encryption.");
        return;
      }
      strength = (String) encryptionStrengthCombo.getSelectedItem();
    }

    fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
    fileChooser.setSelectedFile(new File("output." + getExtensionForFormat(format)));
    int result = fileChooser.showSaveDialog(this);
    if (result != JFileChooser.APPROVE_OPTION) {
      return;
    }
    String output = fileChooser.getSelectedFile().getAbsolutePath();

    try {
      switch (format) {
        case "ZIP":
          boolean store = zipStoreCheck != null && zipStoreCheck.isSelected();
          CompressionDemo.compressZip(inputPath, output, level, store, password, strength);
          break;
        case "TAR":
          String tarMode = tarModeCombo != null ? (String) tarModeCombo.getSelectedItem() : "POSIX";
          CompressionDemo.compressTar(inputPath, output, tarMode);
          break;
        case "GZIP":
          CompressionDemo.compressGzip(inputPath, output, level);
          break;
        case "BZIP2":
          CompressionDemo.compressBzip2(inputPath, output, level);
          break;
        case "7Z":
          CompressionDemo.compress7z(inputPath, output, level, password, strength);
          break;
        case "XZ":
          CompressionDemo.compressXz(inputPath, output, level);
          break;
        case "Deflate":
          CompressionDemo.compressDeflate(inputPath, output, level);
          break;
        case "LZMA":
          CompressionDemo.compressLzma(inputPath, output);
          break;
        case "LZ4":
          CompressionDemo.compressLz4Framed(inputPath, output);
          break;
        case "Snappy":
          CompressionDemo.compressSnappyFramed(inputPath, output);
          break;
        case "Zstandard":
          CompressionDemo.compressZstandard(inputPath, output, level);
          break;
        case "Pack200":
          CompressionDemo.compressPack200(inputPath, output);
          break;
      }
      JOptionPane.showMessageDialog(this, "Compression completed!");
      if (deleteOriginalCheck.isSelected()) {
        new File(inputPath).delete();
      }
    } catch (IOException ex) {
      JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
    }
  }

  private void performDecompress(ActionEvent e) {
    if (inputPath == null || inputPath.isEmpty()) {
      JOptionPane.showMessageDialog(this, "Please drag an input file first.");
      return;
    }
    String format = (String) formatCombo.getSelectedItem();

    boolean isArchive = format.equals("ZIP") || format.equals("TAR") || format.equals("7Z");
    fileChooser.setFileSelectionMode(isArchive ? JFileChooser.DIRECTORIES_ONLY : JFileChooser.FILES_ONLY);
    fileChooser.setSelectedFile(new File(isArchive ? "extracted" : "decompressed.txt"));
    int result = fileChooser.showSaveDialog(this);
    if (result != JFileChooser.APPROVE_OPTION) {
      return;
    }
    String output = fileChooser.getSelectedFile().getAbsolutePath();

    try {
      switch (format) {
        case "ZIP":
          CompressionDemo.extractZip(inputPath, output);
          break;
        case "TAR":
          CompressionDemo.extractTar(inputPath, output);
          break;
        case "GZIP":
          CompressionDemo.decompressGzip(inputPath, output);
          break;
        case "BZIP2":
          CompressionDemo.decompressBzip2(inputPath, output);
          break;
        case "7Z":
          CompressionDemo.extract7z(inputPath, output);
          break;
        case "XZ":
          CompressionDemo.decompressXz(inputPath, output);
          break;
        // Add decompress for others where supported
        default:
          JOptionPane.showMessageDialog(this, "Decompression not implemented for " + format);
          return;
      }
      JOptionPane.showMessageDialog(this, "Decompression completed!");
    } catch (IOException ex) {
      JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
    }
  }

  private String getExtensionForFormat(String format) {
    switch (format) {
      case "ZIP": return "zip";
      case "TAR": return "tar";
      case "GZIP": return "gz";
      case "BZIP2": return "bz2";
      case "7Z": return "7z";
      case "XZ": return "xz";
      case "Deflate": return "deflate";
      case "LZMA": return "lzma";
      case "LZ4": return "lz4";
      case "Snappy": return "sz";
      case "Zstandard": return "zst";
      case "Pack200": return "pack";
      default: return "out";
    }
  }

  private class FileTransferHandler extends TransferHandler {
    @Override
    public boolean canImport(TransferSupport support) {
      return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
    }

    @Override
    public boolean importData(TransferSupport support) {
      if (!canImport(support)) {
        return false;
      }
      Transferable t = support.getTransferable();
      try {
        @SuppressWarnings("unchecked")
        List<File> files = (List<File>) t.getTransferData(DataFlavor.javaFileListFlavor);
        if (!files.isEmpty()) {
          inputPath = files.get(0).getAbsolutePath();
          dragLabel.setText("Input: " + new File(inputPath).getName());
          return true;
        }
      } catch (Exception ex) {
        ex.printStackTrace();
      }
      return false;
    }
  }

  public static void main(String[] args) {
    SwingUtilities.invokeLater(() -> new CompressionGUI().setVisible(true));
  }
}
