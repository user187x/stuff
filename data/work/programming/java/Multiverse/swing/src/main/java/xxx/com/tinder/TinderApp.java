package xxx.com.tinder;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.AbstractBorder;

/**
 * Main application class for the rudimentary Tinder-like GUI.
 */
public class TinderApp extends JFrame {

  private List<Profile> profiles;
  private int currentProfileIndex;

  private JLabel profileNameAgeLabel;
  private JTextArea profileBioArea;
  private JLabel profileImagePlaceholder; // Displays the actual image
  private JButton likeButton;
  private JButton dislikeButton;

  private final ProfileEditorDialog profileEditorDialog;

  public TinderApp() {
    super("Rudimentary Tinder App"); // Set the frame title
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE); // Close operation
    setSize(400, 600); // Set initial size
    setLocationRelativeTo(null); // Center the window on the screen

    // Initialize profiles
    initializeProfiles();
    currentProfileIndex = 0;

    // Initialize the profile editor dialog
    profileEditorDialog = new ProfileEditorDialog(this);

    // Set up the UI components
    setupUI();
    setupMenuBar(); // Add the menu bar

    // Display the first profile
    displayCurrentProfile();
  }

  /**
   * Initializes a list of dummy profiles.
   */
  private void initializeProfiles() {
    profiles = new ArrayList<>();
    // For image paths, use absolute paths for testing, or ensure images are in a known location
    // relative to the JAR.
    // For this example, we keep placeholders for demonstration.
    // If you have images, replace "placeholder_alice.png" with their actual paths:
    // E.g., new Profile("Alice", 28, "Loves hiking and coffee.", "/Users/youruser/images/alice.jpg")
    profiles.add(new Profile("Alice", 28, "Loves hiking and coffee.", "")); // No image for Alice
    profiles.add(new Profile("Bob", 30, "Software engineer, avid gamer.", ""));
    profiles.add(new Profile("Charlie", 25, "Artist, enjoys painting and music.", ""));
    profiles.add(new Profile("Diana", 29, "Travel enthusiast and food lover.", ""));
  }

  /**
   * Sets up the main user interface layout and components.
   */
  private void setupUI() {
    setLayout(new BorderLayout(10, 10)); // Use BorderLayout with gaps

    // --- Profile Panel ---
    JPanel profilePanel = new JPanel();
    profilePanel.setLayout(new BorderLayout(5, 5));
    profilePanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20)); // Padding
    profilePanel.setBackground(new Color(245, 245, 245)); // Light background

    profileImagePlaceholder = new JLabel();
    profileImagePlaceholder.setPreferredSize(new Dimension(300, 300)); // Fixed size for display
    profileImagePlaceholder.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY, 1));
    profileImagePlaceholder.setHorizontalAlignment(SwingConstants.CENTER);
    profileImagePlaceholder.setVerticalAlignment(SwingConstants.CENTER);
    profileImagePlaceholder.setText("No Image Available"); // Default text
    profilePanel.add(profileImagePlaceholder, BorderLayout.CENTER);

    profileNameAgeLabel = new JLabel("Name, Age", SwingConstants.CENTER);
    profileNameAgeLabel.setFont(new Font("Arial", Font.BOLD, 24));
    profileNameAgeLabel.setForeground(new Color(50, 50, 50));
    profilePanel.add(profileNameAgeLabel, BorderLayout.NORTH);

    profileBioArea = new JTextArea("Bio goes here...");
    profileBioArea.setFont(new Font("Arial", Font.PLAIN, 16));
    profileBioArea.setWrapStyleWord(true);
    profileBioArea.setLineWrap(true);
    profileBioArea.setEditable(false);
    profileBioArea.setBackground(new Color(245, 245, 245)); // Match panel background
    profileBioArea.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0)); // Top padding for bio
    JScrollPane scrollPane = new JScrollPane(profileBioArea);
    scrollPane.setPreferredSize(new Dimension(300, 80));
    profilePanel.add(scrollPane, BorderLayout.SOUTH);

    add(profilePanel, BorderLayout.CENTER);

    // --- Buttons Panel ---
    JPanel buttonPanel = new JPanel();
    buttonPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 30, 20)); // Center buttons with gap
    buttonPanel.setBackground(new Color(230, 230, 230)); // Slightly darker background for buttons

    dislikeButton = new JButton("Dislike");
    styleButton(dislikeButton, new Color(255, 99, 71), Color.WHITE); // Tomato red
    dislikeButton.addActionListener(e -> handleDislike());
    buttonPanel.add(dislikeButton);

    likeButton = new JButton("Like");
    styleButton(likeButton, new Color(60, 179, 113), Color.WHITE); // Medium Sea Green
    likeButton.addActionListener(e -> handleLike());
    buttonPanel.add(likeButton);

    add(buttonPanel, BorderLayout.SOUTH);
  }

  /**
   * Sets up the menu bar for profile management.
   */
  private void setupMenuBar() {
    JMenuBar menuBar = new JMenuBar();
    JMenu profileMenu = new JMenu("Profile");

    JMenuItem newProfileItem = new JMenuItem("New Profile");
    newProfileItem.addActionListener(e -> createNewProfile());
    profileMenu.add(newProfileItem);

    JMenuItem editProfileItem = new JMenuItem("Edit Current Profile");
    editProfileItem.addActionListener(e -> editCurrentProfile());
    profileMenu.add(editProfileItem);

    menuBar.add(profileMenu);
    setJMenuBar(menuBar);
  }

  /**
   * Opens the dialog to create a new profile.
   */
  private void createNewProfile() {
    profileEditorDialog.setProfile(null); // Indicate new profile creation
    profileEditorDialog.setVisible(true);

    if (profileEditorDialog.isSaved()) {
      Profile newProfile = profileEditorDialog.getProfile();
      if (newProfile != null) {
        profiles.add(newProfile);
        // If this is the first profile, display it
        if (profiles.size() == 1) {
          currentProfileIndex = 0;
          displayCurrentProfile();
        }
        else {
          // Otherwise, just confirm addition (or could navigate to it)
          JOptionPane.showMessageDialog(this, "Profile '" + newProfile.getName() + "' added.", "Profile Added",
              JOptionPane.INFORMATION_MESSAGE);
        }
      }
    }
  }

  /**
   * Opens the dialog to edit the currently displayed profile.
   */
  private void editCurrentProfile() {
    if (profiles.isEmpty()) {
      JOptionPane.showMessageDialog(this, "No profiles to edit.", "No Profiles", JOptionPane.INFORMATION_MESSAGE);
      return;
    }

    Profile profileToEdit = profiles.get(currentProfileIndex);
    profileEditorDialog.setProfile(profileToEdit); // Pass the profile to edit
    profileEditorDialog.setVisible(true);

    if (profileEditorDialog.isSaved()) {
      Profile updatedProfile = profileEditorDialog.getProfile(); // This returns the *same* object updated
      if (updatedProfile != null) {
        // The currentProfile object in the list is already updated by setProfile if it's the same object
        // We just need to refresh the display
        displayCurrentProfile();
        JOptionPane.showMessageDialog(this, "Profile '" + updatedProfile.getName() + "' updated.", "Profile Updated",
            JOptionPane.INFORMATION_MESSAGE);
      }
    }
  }


  /**
   * Applies a consistent style to the buttons.
   */
  private void styleButton(JButton button, Color bgColor, Color fgColor) {
    button.setBackground(bgColor);
    button.setForeground(fgColor);
    button.setFont(new Font("Arial", Font.BOLD, 18));
    button.setFocusPainted(false); // Remove focus border
    button.setBorder(BorderFactory.createLineBorder(bgColor.darker(), 2)); // Darker border
    button.setPreferredSize(new Dimension(120, 50)); // Fixed size
    button.setCursor(new Cursor(Cursor.HAND_CURSOR)); // Hand cursor on hover
    // Add rounded corners
    button.setBorder(new RoundButtonBorder(15, bgColor.darker())); // Custom border for rounded corners
  }

  // Custom Border class for rounded buttons (inner class as before)
  class RoundButtonBorder extends AbstractBorder {
    private final int radius;
    private final Color borderColor;

    RoundButtonBorder(int radius, Color borderColor) {
      this.radius = radius;
      this.borderColor = borderColor;
    }

    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
      Graphics2D g2 = (Graphics2D) g.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      // DO NOT fill the entire rectangle here; let the button's default painting handle the background.
      // Only draw the border outline.
      g2.setColor(borderColor); // Use the specified border color
      g2.drawRoundRect(x, y, width - 1, height - 1, radius, radius);
      g2.dispose();
    }

    @Override
    public Insets getBorderInsets(Component c) {
      return new Insets(this.radius, this.radius, this.radius, this.radius);
    }

    @Override
    public Insets getBorderInsets(Component c, Insets insets) {
      insets.left = insets.top = insets.right = insets.bottom = this.radius;
      return insets;
    }
  }


  /**
   * Displays the profile at the currentProfileIndex.
   */
  private void displayCurrentProfile() {
    if (profiles.isEmpty()) {
      profileNameAgeLabel.setText("No profiles yet!");
      profileBioArea.setText("Create a new profile using the 'Profile' menu.");
      profileImagePlaceholder.setIcon(null);
      profileImagePlaceholder.setText("No profiles");
      likeButton.setEnabled(false);
      dislikeButton.setEnabled(false);
      return;
    }

    if (currentProfileIndex < profiles.size()) {
      Profile currentProfile = profiles.get(currentProfileIndex);
      profileNameAgeLabel.setText(currentProfile.getName() + ", " + currentProfile.getAge());
      profileBioArea.setText(currentProfile.getBio());

      // Load and display the image
      if (currentProfile.getImagePath() != null && !currentProfile.getImagePath().isEmpty()) {
        try {
          ImageIcon icon = new ImageIcon(currentProfile.getImagePath());
          // Scale the image to fit the placeholder label size
          Image image = icon.getImage();
          Image scaledImage = image.getScaledInstance(
              profileImagePlaceholder.getWidth(), profileImagePlaceholder.getHeight(), Image.SCALE_SMOOTH);
          profileImagePlaceholder.setIcon(new ImageIcon(scaledImage));
          profileImagePlaceholder.setText(""); // Clear text if image loaded
        }
        catch (Exception e) {
          profileImagePlaceholder.setIcon(null);
          profileImagePlaceholder.setText("Image not found: " + currentProfile.getImagePath());
          System.err.println("Error loading profile image: " + currentProfile.getImagePath() + " " + e.getMessage());
        }
      }
      else {
        profileImagePlaceholder.setIcon(null);
        profileImagePlaceholder.setText("No Image Available");
      }

      likeButton.setEnabled(true);
      dislikeButton.setEnabled(true);
    }
    else {
      // No more profiles
      profileNameAgeLabel.setText("No more profiles!");
      profileBioArea.setText("You've seen everyone around here.");
      profileImagePlaceholder.setText("End of the line!");
      profileImagePlaceholder.setIcon(null);
      likeButton.setEnabled(false); // Disable buttons when no more profiles
      dislikeButton.setEnabled(false);
    }
  }

  /**
   * Handles the 'Like' action.
   */
  private void handleLike() {
    if (currentProfileIndex < profiles.size()) {
      Profile likedProfile = profiles.get(currentProfileIndex);
      System.out.println("Liked: " + likedProfile.getName());
      // In a real app, this would save the 'like' to a database
      moveToNextProfile();
    }
  }

  /**
   * Handles the 'Dislike' action.
   */
  private void handleDislike() {
    if (currentProfileIndex < profiles.size()) {
      Profile dislikedProfile = profiles.get(currentProfileIndex);
      System.out.println("Disliked: " + dislikedProfile.getName());
      // In a real app, this would save the 'dislike' or simply move on
      moveToNextProfile();
    }
  }

  /**
   * Moves to the next profile and updates the display.
   */
  private void moveToNextProfile() {
    currentProfileIndex++;
    // Loop back to the beginning if all profiles are seen, or show "no more profiles"
    if (currentProfileIndex >= profiles.size() && !profiles.isEmpty()) {
      currentProfileIndex = 0; // Loop back to the first profile
      JOptionPane.showMessageDialog(this, "Looping back to the beginning of profiles.", "End of Profiles", JOptionPane.INFORMATION_MESSAGE);
    }
    displayCurrentProfile();
  }

  public static void main(String[] args) {
    // Ensure the GUI is created and updated on the Event Dispatch Thread (EDT)
    FlatDarkLaf.setup();

    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        new TinderApp().setVisible(true);
      }
    });
  }
}

