package xxx.com.exif;

import com.mpatric.mp3agic.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import javax.imageio.ImageIO;

/**
 * A simple Java Swing application to edit the ID3 tags of an MP3 file.
 *
 * This application uses the 'mp3agic' library. You must add the mp3agic JAR
 * to your classpath to compile and run this code.
 *
 * For example, to compile and run from the command line:
 * 1. Download mp3agic-x.y.z.jar
 * 2. Compile: javac -cp ".;mp3agic-0.9.1.jar" Mp3TagEditor.java
 * 3. Run:     java -cp ".;mp3agic-0.9.1.jar" Mp3TagEditor
 * (Use ':' instead of ';' on Linux/macOS for the classpath separator)
 */
public class Mp3TagEditor extends JFrame {

  // --- UI Components ---
  private final JTextField titleField = new JTextField(25);
  private final JTextField artistField = new JTextField(25);
  private final JTextField albumField = new JTextField(25);
  private final JTextField yearField = new JTextField(25);
  private final JTextField genreField = new JTextField(25);
  private final JTextArea commentArea = new JTextArea(3, 25);
  private final JButton saveButton = new JButton("Save Changes");
  private final JLabel statusLabel = new JLabel("Please open an MP3 file to begin.");
  private final JLabel fileLabel = new JLabel("No file loaded.");
  private final JLabel albumArtLabel = new JLabel();
  private final JButton addArtButton = new JButton("Add/Change Art");
  private final JButton removeArtButton = new JButton("Remove Art");


  // --- File Data ---
  private File currentMp3File;
  private Mp3File mp3file;
  private byte[] albumImageBytes;
  private String albumImageMimeType;

  public Mp3TagEditor() {
    super("MP3 ID3 Tag Editor");

    // --- Setup the main window ---
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    setSize(650, 480);
    setLocationRelativeTo(null); // Center the window

    // --- Create Menu Bar ---
    JMenuBar menuBar = new JMenuBar();
    JMenu fileMenu = new JMenu("File");
    JMenuItem openItem = new JMenuItem("Open MP3...");
    fileMenu.add(openItem);
    menuBar.add(fileMenu);
    setJMenuBar(menuBar);

    // --- Create Main Panel ---
    JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
    mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

    // --- Create Form Panel for tags (Left side) ---
    JPanel formPanel = new JPanel(new GridBagLayout());
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.insets = new Insets(5, 5, 5, 5);
    gbc.anchor = GridBagConstraints.WEST;

    // Add form fields
    addField(formPanel, gbc, 0, "Title:", titleField);
    addField(formPanel, gbc, 1, "Artist:", artistField);
    addField(formPanel, gbc, 2, "Album:", albumField);
    addField(formPanel, gbc, 3, "Year:", yearField);
    addField(formPanel, gbc, 4, "Genre:", genreField);

    gbc.gridx = 0;
    gbc.gridy = 5;
    formPanel.add(new JLabel("Comment:"), gbc);
    gbc.gridx = 1;
    gbc.gridwidth = 2; // Span across two columns
    gbc.fill = GridBagConstraints.HORIZONTAL;
    formPanel.add(new JScrollPane(commentArea), gbc);


    // --- Create Album Art Panel (Right side) ---
    JPanel artPanel = new JPanel(new BorderLayout(5, 5));
    artPanel.setBorder(new TitledBorder("Album Art"));
    albumArtLabel.setHorizontalAlignment(SwingConstants.CENTER);
    albumArtLabel.setPreferredSize(new Dimension(150, 150));
    albumArtLabel.setBorder(BorderFactory.createEtchedBorder());
    artPanel.add(albumArtLabel, BorderLayout.CENTER);

    JPanel artButtonPanel = new JPanel(new FlowLayout());
    artButtonPanel.add(addArtButton);
    artButtonPanel.add(removeArtButton);
    artPanel.add(artButtonPanel, BorderLayout.SOUTH);

    // --- Create Center Panel to hold form and art ---
    JPanel centerPanel = new JPanel(new BorderLayout(10, 10));
    centerPanel.add(formPanel, BorderLayout.CENTER);
    centerPanel.add(artPanel, BorderLayout.EAST);

    // --- Create Bottom Panel for buttons and status ---
    JPanel bottomPanel = new JPanel(new BorderLayout(10, 10));
    JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    buttonPanel.add(saveButton);

    bottomPanel.add(fileLabel, BorderLayout.NORTH);
    bottomPanel.add(buttonPanel, BorderLayout.CENTER);
    bottomPanel.add(statusLabel, BorderLayout.SOUTH);

    mainPanel.add(centerPanel, BorderLayout.CENTER);
    mainPanel.add(bottomPanel, BorderLayout.SOUTH);

    add(mainPanel);

    // --- Add Action Listeners ---
    openItem.addActionListener(this::openFile);
    saveButton.addActionListener(this::saveFile);
    addArtButton.addActionListener(this::addAlbumArt);
    removeArtButton.addActionListener(this::removeAlbumArt);


    // Initially disable editing until a file is loaded
    setFieldsEnabled(false);
  }

  /**
   * Helper method to add a label and text field to the form panel.
   */
  private void addField(JPanel panel, GridBagConstraints gbc, int y, String label, JComponent component) {
    gbc.gridx = 0;
    gbc.gridy = y;
    gbc.fill = GridBagConstraints.NONE;
    panel.add(new JLabel(label), gbc);

    gbc.gridx = 1;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.weightx = 1.0;
    panel.add(component, gbc);
    gbc.weightx = 0; // Reset
  }

  /**
   * Handles the "Open MP3..." menu item action.
   */
  private void openFile(ActionEvent e) {
    JFileChooser fileChooser = new JFileChooser();
    fileChooser.setDialogTitle("Select an MP3 File");
    fileChooser.setFileFilter(new FileNameExtensionFilter("MP3 Audio Files", "mp3"));

    int result = fileChooser.showOpenDialog(this);
    if (result == JFileChooser.APPROVE_OPTION) {
      currentMp3File = fileChooser.getSelectedFile();
      fileLabel.setText("Current File: " + currentMp3File.getName());
      loadTagData();
    }
  }

  /**
   * Loads the ID3 tag data from the selected MP3 file and populates the UI fields.
   */
  private void loadTagData() {
    if (currentMp3File == null) return;

    clearFields(); // Clear previous data first

    try {
      mp3file = new Mp3File(currentMp3File);
      ID3v2 id3v2Tag;

      if (mp3file.hasId3v2Tag()) {
        id3v2Tag = mp3file.getId3v2Tag();
        // Load album art
        albumImageBytes = id3v2Tag.getAlbumImage();
        albumImageMimeType = id3v2Tag.getAlbumImageMimeType();
        displayAlbumArt();
      } else if (mp3file.hasId3v1Tag()) {
        ID3v1 id3v1Tag = mp3file.getId3v1Tag();
        titleField.setText(id3v1Tag.getTitle());
        artistField.setText(id3v1Tag.getArtist());
        albumField.setText(id3v1Tag.getAlbum());
        yearField.setText(id3v1Tag.getYear());
        genreField.setText(id3v1Tag.getGenreDescription());
        commentArea.setText(id3v1Tag.getComment());
        statusLabel.setText("Loaded ID3v1 tag. Changes will be saved as ID3v2.");
        setFieldsEnabled(true);
        return;
      } else {
        id3v2Tag = new ID3v24Tag();
        mp3file.setId3v2Tag(id3v2Tag);
        statusLabel.setText("No tags found. Ready to create a new ID3v2 tag.");
      }

      titleField.setText(id3v2Tag.getTitle());
      artistField.setText(id3v2Tag.getArtist());
      albumField.setText(id3v2Tag.getAlbum());
      yearField.setText(id3v2Tag.getYear());
      genreField.setText(id3v2Tag.getGenreDescription());
      commentArea.setText(id3v2Tag.getComment());

      statusLabel.setText("ID3v2 tag loaded successfully.");
      setFieldsEnabled(true);

    } catch (IOException | UnsupportedTagException | InvalidDataException ex) {
      statusLabel.setText("Error: Could not read MP3 file. " + ex.getMessage());
      JOptionPane.showMessageDialog(this,
          "Could not read the selected file. It may be corrupt or not a valid MP3 file.",
          "File Error", JOptionPane.ERROR_MESSAGE);
      clearFields();
      setFieldsEnabled(false);
    }
  }

  /**
   * Handles the "Save Changes" button action.
   */
  private void saveFile(ActionEvent e) {
    if (mp3file == null) {
      JOptionPane.showMessageDialog(this, "No MP3 file is loaded.", "Save Error", JOptionPane.ERROR_MESSAGE);
      return;
    }

    ID3v2 id3v2Tag;
    if (mp3file.hasId3v2Tag()) {
      id3v2Tag = mp3file.getId3v2Tag();
    } else {
      id3v2Tag = new ID3v24Tag();
      mp3file.setId3v2Tag(id3v2Tag);
    }

    id3v2Tag.setTitle(titleField.getText());
    id3v2Tag.setArtist(artistField.getText());
    id3v2Tag.setAlbum(albumField.getText());
    id3v2Tag.setYear(yearField.getText());
    id3v2Tag.setComment(commentArea.getText());
    id3v2Tag.setAlbumImage(albumImageBytes, albumImageMimeType);

    try {
      int genre = ID3v1Genres.matchGenreDescription(genreField.getText());
      id3v2Tag.setGenre(genre);
    } catch (Exception ex) {
      id3v2Tag.setGenreDescription(genreField.getText());
    }

    File tempFile = new File(currentMp3File.getParent(), currentMp3File.getName() + ".tmp");
    try {
      mp3file.save(tempFile.getAbsolutePath());
      Files.move(tempFile.toPath(), currentMp3File.toPath(), StandardCopyOption.REPLACE_EXISTING);
      statusLabel.setText("File saved successfully!");
      JOptionPane.showMessageDialog(this, "Tags saved successfully!", "Success", JOptionPane.INFORMATION_MESSAGE);
    } catch (IOException | NotSupportedException ex) {
      statusLabel.setText("Error: Could not save file. " + ex.getMessage());
      JOptionPane.showMessageDialog(this, "Could not save the file: " + ex.getMessage(), "Save Error", JOptionPane.ERROR_MESSAGE);
      if (tempFile.exists()) {
        tempFile.delete();
      }
    }
  }

  /**
   * Opens a file chooser to select an image for album art.
   */
  private void addAlbumArt(ActionEvent e) {
    JFileChooser fileChooser = new JFileChooser();
    fileChooser.setDialogTitle("Select Album Art Image");
    fileChooser.setFileFilter(new FileNameExtensionFilter("Image Files", "jpg", "jpeg", "png", "gif"));
    int result = fileChooser.showOpenDialog(this);
    if (result == JFileChooser.APPROVE_OPTION) {
      File imageFile = fileChooser.getSelectedFile();
      try {
        albumImageBytes = Files.readAllBytes(imageFile.toPath());
        albumImageMimeType = Files.probeContentType(imageFile.toPath());
        if (albumImageMimeType == null) {
          // Provide a fallback MIME type
          String name = imageFile.getName().toLowerCase();
          if (name.endsWith("png")) albumImageMimeType = "image/png";
          else albumImageMimeType = "image/jpeg";
        }
        displayAlbumArt();
        statusLabel.setText("Album art selected. Click 'Save Changes' to apply.");
      } catch (IOException ex) {
        JOptionPane.showMessageDialog(this, "Error reading image file: " + ex.getMessage(), "Image Error", JOptionPane.ERROR_MESSAGE);
      }
    }
  }

  /**
   * Removes the currently selected or loaded album art.
   */
  private void removeAlbumArt(ActionEvent e) {
    albumImageBytes = null;
    albumImageMimeType = null;
    displayAlbumArt(); // This will clear the label
    statusLabel.setText("Album art removed. Click 'Save Changes' to apply.");
  }

  /**
   * Displays the current album art in the JLabel, scaling it appropriately.
   */
  private void displayAlbumArt() {
    if (albumImageBytes != null) {
      try {
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(albumImageBytes));
        Image scaledImg = img.getScaledInstance(albumArtLabel.getWidth(), albumArtLabel.getHeight(), Image.SCALE_SMOOTH);
        albumArtLabel.setIcon(new ImageIcon(scaledImg));
      } catch (IOException e) {
        albumArtLabel.setIcon(null);
        albumArtLabel.setText("Preview N/A");
      }
    } else {
      albumArtLabel.setIcon(null);
      albumArtLabel.setText("No Album Art");
    }
  }

  /**
   * Enables or disables all the text fields and the save button.
   */
  private void setFieldsEnabled(boolean enabled) {
    titleField.setEnabled(enabled);
    artistField.setEnabled(enabled);
    albumField.setEnabled(enabled);
    yearField.setEnabled(enabled);
    genreField.setEnabled(enabled);
    commentArea.setEnabled(enabled);
    saveButton.setEnabled(enabled);
    addArtButton.setEnabled(enabled);
    removeArtButton.setEnabled(enabled);
  }

  /**
   * Clears all text fields and the album art.
   */
  private void clearFields() {
    titleField.setText("");
    artistField.setText("");
    albumField.setText("");
    yearField.setText("");
    genreField.setText("");
    commentArea.setText("");
    albumImageBytes = null;
    albumImageMimeType = null;
    displayAlbumArt();
  }

  /**
   * Main method to run the application.
   */
  public static void main(String[] args) {
    try {
      UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
    } catch (Exception e) {
      // Keep the default look and feel
    }

    SwingUtilities.invokeLater(() -> {
      new Mp3TagEditor().setVisible(true);
    });
  }
}
