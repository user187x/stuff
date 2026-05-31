package xxx.com.tinder;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.io.File;
// No need to import Profile as it's in the same package
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * A JDialog for creating or editing user profiles.
 */
public class ProfileEditorDialog extends JDialog { // Class must be public
  private JTextField nameField;
  private JSpinner ageSpinner;
  private JTextArea bioArea;
  private JLabel imagePreviewLabel;
  private String selectedImagePath;

  private boolean saved = false;
  private Profile currentProfile; // To hold the profile being edited/created

  public ProfileEditorDialog(Frame owner) {
    super(owner, "Profile Editor", true); // Modal dialog
    setupUI();
    setSize(450, 550);
    setLocationRelativeTo(owner);
  }

  private void setupUI() {
    setLayout(new BorderLayout(10, 10));
    JPanel formPanel = new JPanel(new GridBagLayout());
    formPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.insets = new Insets(5, 5, 5, 5);
    gbc.fill = GridBagConstraints.HORIZONTAL;

    // Name
    gbc.gridx = 0;
    gbc.gridy = 0;
    formPanel.add(new JLabel("Name:"), gbc);
    gbc.gridx = 1;
    gbc.gridy = 0;
    gbc.weightx = 1.0;
    nameField = new JTextField(20);
    formPanel.add(nameField, gbc);

    // Age
    gbc.gridx = 0;
    gbc.gridy = 1;
    gbc.weightx = 0;
    formPanel.add(new JLabel("Age:"), gbc);
    gbc.gridx = 1;
    gbc.gridy = 1;
    ageSpinner = new JSpinner(new SpinnerNumberModel(20, 18, 99, 1));
    formPanel.add(ageSpinner, gbc);

    // Bio
    gbc.gridx = 0;
    gbc.gridy = 2;
    formPanel.add(new JLabel("Bio:"), gbc);
    gbc.gridx = 1;
    gbc.gridy = 2;
    gbc.weighty = 1.0; // Allow bio area to expand vertically
    gbc.fill = GridBagConstraints.BOTH;
    bioArea = new JTextArea(5, 20);
    bioArea.setLineWrap(true);
    bioArea.setWrapStyleWord(true);
    JScrollPane bioScrollPane = new JScrollPane(bioArea);
    formPanel.add(bioScrollPane, gbc);
    gbc.weighty = 0; // Reset weight for subsequent components
    gbc.fill = GridBagConstraints.HORIZONTAL;

    // Image Selection
    gbc.gridx = 0;
    gbc.gridy = 3;
    formPanel.add(new JLabel("Picture:"), gbc);
    gbc.gridx = 1;
    gbc.gridy = 3;
    JPanel imagePanel = new JPanel(new BorderLayout(5, 5));
    imagePreviewLabel = new JLabel("No Image Selected", SwingConstants.CENTER);
    imagePreviewLabel.setPreferredSize(new Dimension(200, 150));
    imagePreviewLabel.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));
    imagePanel.add(imagePreviewLabel, BorderLayout.CENTER);

    JButton browseButton = new JButton("Browse...");
    browseButton.addActionListener(e -> selectImageFile());
    imagePanel.add(browseButton, BorderLayout.EAST);
    formPanel.add(imagePanel, gbc);

    add(formPanel, BorderLayout.CENTER);

    // Buttons Panel
    JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
    JButton saveButton = new JButton("Save");
    saveButton.addActionListener(e -> {
      saved = true;
      setVisible(false);
    });
    JButton cancelButton = new JButton("Cancel");
    cancelButton.addActionListener(e -> {
      saved = false;
      setVisible(false);
    });
    buttonPanel.add(saveButton);
    buttonPanel.add(cancelButton);
    add(buttonPanel, BorderLayout.SOUTH);
  }

  /**
   * Populates the dialog fields with data from a given Profile object for editing. If profile is
   * null, it prepares for a new profile creation.
   * 
   * @param profile The profile to edit, or null for a new profile.
   */
  public void setProfile(Profile profile) {
    this.currentProfile = profile;
    if (profile != null) {
      nameField.setText(profile.getName());
      ageSpinner.setValue(profile.getAge());
      bioArea.setText(profile.getBio());
      selectedImagePath = profile.getImagePath();
      updateImagePreview(selectedImagePath);
      setTitle("Edit Profile");
    }
    else {
      // Reset for new profile
      nameField.setText("");
      ageSpinner.setValue(20);
      bioArea.setText("");
      selectedImagePath = null;
      imagePreviewLabel.setIcon(null);
      imagePreviewLabel.setText("No Image Selected");
      setTitle("Create New Profile");
    }
    saved = false; // Reset saved state for each new display
  }

  /**
   * Returns a Profile object based on the current dialog inputs. Returns null if "Cancel" was clicked
   * or if fields are invalid.
   * 
   * @return A Profile object with the entered data, or null.
   */
  public Profile getProfile() {
    if (!saved) {
      return null;
    }
    // Basic validation
    if (nameField.getText().trim().isEmpty() || bioArea.getText().trim().isEmpty()) {
      JOptionPane.showMessageDialog(this, "Name and Bio cannot be empty.", "Input Error", JOptionPane.ERROR_MESSAGE);
      return null;
    }

    String name = nameField.getText().trim();
    int age = (Integer) ageSpinner.getValue();
    String bio = bioArea.getText().trim();

    if (currentProfile == null) {
      // New profile
      return new Profile(name, age, bio, selectedImagePath);
    }
    else {
      // Update existing profile
      currentProfile.setName(name);
      currentProfile.setAge(age);
      currentProfile.setBio(bio);
      currentProfile.setImagePath(selectedImagePath);
      return currentProfile;
    }
  }

  /**
   * Opens a file chooser to select an image file.
   */
  private void selectImageFile() {
    JFileChooser fileChooser = new JFileChooser();
    FileNameExtensionFilter filter = new FileNameExtensionFilter(
        "Image Files", "jpg", "jpeg", "png", "gif");
    fileChooser.setFileFilter(filter);

    int returnValue = fileChooser.showOpenDialog(this);
    if (returnValue == JFileChooser.APPROVE_OPTION) {
      File selectedFile = fileChooser.getSelectedFile();
      selectedImagePath = selectedFile.getAbsolutePath();
      updateImagePreview(selectedImagePath);
    }
  }

  /**
   * Updates the image preview label with the selected image.
   * 
   * @param path The absolute path to the image file.
   */
  private void updateImagePreview(String path) {
    if (path != null && !path.isEmpty()) {
      try {
        ImageIcon icon = new ImageIcon(path);
        // Scale the image to fit the preview label
        Image image = icon.getImage();
        Image scaledImage = image.getScaledInstance(
            imagePreviewLabel.getWidth(), imagePreviewLabel.getHeight(), Image.SCALE_SMOOTH);
        imagePreviewLabel.setIcon(new ImageIcon(scaledImage));
        imagePreviewLabel.setText(""); // Clear text if image loaded
      }
      catch (Exception e) {
        imagePreviewLabel.setIcon(null);
        imagePreviewLabel.setText("Error loading image");
        System.err.println("Error loading image for preview: " + path + " - " + e.getMessage());
      }
    }
    else {
      imagePreviewLabel.setIcon(null);
      imagePreviewLabel.setText("No Image Selected");
    }
  }

  public boolean isSaved() {
    return saved;
  }
}
