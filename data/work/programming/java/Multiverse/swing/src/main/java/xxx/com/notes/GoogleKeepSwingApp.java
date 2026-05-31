package xxx.com.notes;

import com.formdev.flatlaf.FlatDarkLaf;
import xxx.com.swing.fab.FloatingButton;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID; // To generate unique IDs for notes
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;

public class GoogleKeepSwingApp extends JFrame {

  private final JPanel notesPanel; // Panel to hold all the notes
  private final JScrollPane scrollPane; // Scroll pane for the notes panel
  private final JButton addNoteButton; // Button to add a new note
  private final JButton exportDbButton; // Button to export the database
  private final JButton importDataButton; // Button to import data

  // Array of colors for the notes, similar to Google Keep
  private final Color[] noteColors = {
      new Color(255, 255, 138), // Yellow
      new Color(255, 182, 193), // Pink
      new Color(173, 216, 230), // Light Blue
      new Color(144, 238, 144), // Light Green
      new Color(250, 128, 114), // Salmon
      new Color(240, 230, 140) // Khaki
  };
  private final Random random = new Random(); // For picking random colors

  private int noteX = 10; // Initial x position for new notes
  private int noteY = 10; // Initial y position for new notes
  private final static int NOTE_SPACING = 10; // Spacing between notes

  // Database connection details
  // Using a relative path './' means the database file will be in the directory
  // from which the application is executed.
  private static final String DB_NAME = "keep_notes_db";
  private static final String DB_URL = "jdbc:h2:./" + DB_NAME + ";AUTO_SERVER=TRUE";
  private static final String DB_USER = "sa"; // Default user
  private static final String DB_PASSWORD = ""; // Default password
  private Connection dbConnection;

  public GoogleKeepSwingApp() {

    FloatingButton.setup(this);

    // Set up the main frame
    setTitle("Simple Keep Notes");
    setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE); // Handle closing manually for saving
    setSize(800, 600);
    setLocationRelativeTo(null); // Center the window

    // Initialize database connection and table
    initDatabase();

    // Create the panel to hold notes. Using null layout for drag and drop.
    notesPanel = new JPanel(null); // Use null layout for manual positioning
    notesPanel.setBackground(new Color(248, 248, 248)); // Light grey background

    // Wrap the notes panel in a scroll pane
    scrollPane = new JScrollPane(notesPanel);
    scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER); // No horizontal scroll
    scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED); // Vertical scroll as needed
    scrollPane.getVerticalScrollBar().setUnitIncrement(16); // Make scrolling smoother

    // Create the button to add notes
    addNoteButton = new JButton("Add Note");
    addNoteButton.setFont(new Font("Arial", Font.BOLD, 14));
    addNoteButton.setBackground(new Color(106, 90, 205)); // Slate Blue
    addNoteButton.setForeground(Color.WHITE);
    addNoteButton.setFocusPainted(false); // Remove focus border
    addNoteButton.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20)); // Add padding
    addNoteButton.setCursor(new Cursor(Cursor.HAND_CURSOR)); // Change cursor on hover

    // Create Export Db button
    exportDbButton = new JButton("Export Db");
    exportDbButton.setFont(new Font("Arial", Font.BOLD, 14));
    exportDbButton.setBackground(new Color(60, 179, 113)); // Medium Sea Green
    exportDbButton.setForeground(Color.WHITE);
    exportDbButton.setFocusPainted(false);
    exportDbButton.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
    exportDbButton.setCursor(new Cursor(Cursor.HAND_CURSOR));

    // Create Import Data button
    importDataButton = new JButton("Import Data");
    importDataButton.setFont(new Font("Arial", Font.BOLD, 14));
    importDataButton.setBackground(new Color(70, 130, 180)); // Steel Blue
    importDataButton.setForeground(Color.WHITE);
    importDataButton.setFocusPainted(false);
    importDataButton.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
    importDataButton.setCursor(new Cursor(Cursor.HAND_CURSOR));


    // Add action listeners to the buttons
    addNoteButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        addNewNote();
      }
    });

    exportDbButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        exportDatabase();
      }
    });

    importDataButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        importDatabase();
      }
    });


    // Create a panel for the buttons and add it to the frame
    JPanel buttonPanel = new JPanel();
    buttonPanel.setBackground(new Color(248, 248, 248)); // Match background
    buttonPanel.add(addNoteButton);
    buttonPanel.add(exportDbButton);
    buttonPanel.add(importDataButton);


    // Add components to the frame
    add(buttonPanel, BorderLayout.NORTH);
    add(scrollPane, BorderLayout.CENTER);

    // Load notes from the database
    loadNotesFromDatabase();

    // Add window listener to save notes on close
    addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosing(WindowEvent e) {
        saveNotesToDatabase(); // Save current state before closing
        closeDatabase();
        dispose(); // Close the frame
        System.exit(0); // Terminate the application
      }
    });
  }

  // Method to initialize the database connection and table
  private void initDatabase() {
    try {
      // Load the H2 database driver
      Class.forName("org.h2.Driver");
      // Establish the connection
      dbConnection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
      System.out.println("Database connection established.");

      // Create the notes table if it doesn't exist
      Statement stmt = dbConnection.createStatement();
      String createTableSQL = "CREATE TABLE IF NOT EXISTS notes (" +
          "id VARCHAR(36) PRIMARY KEY," +
          "content VARCHAR(1000)," +
          "x INT," +
          "y INT," +
          "color INT," + // Store color as an integer RGB value
          "image_path VARCHAR(255)," +
          "z_order INT" +
          ")";
      stmt.execute(createTableSQL);
      System.out.println("Notes table checked/created.");
      stmt.close();
    }
    catch (ClassNotFoundException | SQLException e) {
      e.printStackTrace();
      JOptionPane.showMessageDialog(this,
          "Error initializing database: " + e.getMessage() + "\nPlease ensure the H2 database JAR is in your classpath.",
          "Database Error", JOptionPane.ERROR_MESSAGE);
      // Exit if database initialization fails
      System.exit(1);
    }
  }

  // Method to close the database connection
  private void closeDatabase() {
    try {
      if (dbConnection != null && !dbConnection.isClosed()) {
        dbConnection.close();
        System.out.println("Database connection closed.");
      }
    }
    catch (SQLException e) {
      e.printStackTrace();
    }
  }

  // Method to add a new note to the panel and database
  private void addNewNote() {
    String noteId = UUID.randomUUID().toString(); // Generate a unique ID
    Color initialColor = getRandomNoteColor();
    NotePanel newNote = new NotePanel(noteId, initialColor, notesPanel, this, dbConnection); // Pass ID and connection

    // Set the initial location for the new note
    newNote.setBounds(noteX, noteY, 200, 200); // Set size and position

    notesPanel.add(newNote);

    // Update the position for the next note (simple sequential placement)
    noteX += 200 + NOTE_SPACING;
    if (noteX + 200 > notesPanel.getWidth() && notesPanel.getWidth() > 0) {
      noteX = 10;
      noteY += 200 + NOTE_SPACING;
    }

    // Insert the new note into the database
    insertNoteIntoDatabase(newNote);

    // Ensure the notesPanel's preferred size is large enough to contain all notes
    updateNotesPanelPreferredSize();


    notesPanel.revalidate(); // Re-layout the panel
    notesPanel.repaint(); // Repaint the panel

    // Scroll down to the new note (optional, but nice)
    JScrollBar vertical = scrollPane.getVerticalScrollBar();
    vertical.setValue(vertical.getMaximum());
  }

  // Method to insert a new note record into the database
  private void insertNoteIntoDatabase(NotePanel note) {
    String sql = "INSERT INTO notes (id, content, x, y, color, image_path, z_order) VALUES (?, ?, ?, ?, ?, ?, ?)";
    try (PreparedStatement pstmt = dbConnection.prepareStatement(sql)) {
      pstmt.setString(1, note.getNoteId());
      pstmt.setString(2, note.getNoteContent());
      pstmt.setInt(3, note.getX());
      pstmt.setInt(4, note.getY());
      pstmt.setInt(5, note.getBackground().getRGB()); // Store color as RGB integer
      pstmt.setString(6, note.getBackgroundImagePath()); // Store image path
      pstmt.setInt(7, notesPanel.getComponentZOrder(note)); // Store Z-order
      pstmt.executeUpdate();
      System.out.println("Note inserted into database: " + note.getNoteId());
    }
    catch (SQLException e) {
      e.printStackTrace();
      JOptionPane.showMessageDialog(this,
          "Error inserting note into database: " + e.getMessage(),
          "Database Error", JOptionPane.ERROR_MESSAGE);
    }
  }

  // Method to load notes from the database
  private void loadNotesFromDatabase() {
    // Clear current notes from the panel before loading
    notesPanel.removeAll();
    noteX = 10; // Reset initial positioning for new notes
    noteY = 10;

    String sql = "SELECT id, content, x, y, color, image_path, z_order FROM notes ORDER BY z_order ASC"; // Order by Z-order
    try (Statement stmt = dbConnection.createStatement();
        ResultSet rs = stmt.executeQuery(sql)) {

      List<NoteData> loadedNotes = new ArrayList<>();
      while (rs.next()) {
        String id = rs.getString("id");
        String content = rs.getString("content");
        int x = rs.getInt("x");
        int y = rs.getInt("y");
        int colorRGB = rs.getInt("color");
        String imagePath = rs.getString("image_path");
        int zOrder = rs.getInt("z_order");

        loadedNotes.add(new NoteData(id, content, x, y, new Color(colorRGB), imagePath, zOrder));
      }

      // Add notes to the panel in the correct Z-order
      for (NoteData noteData : loadedNotes) {
        NotePanel loadedNote = new NotePanel(noteData.getId(), noteData.getColor(), notesPanel, this, dbConnection);
        loadedNote.setNoteContent(noteData.getContent());
        loadedNote.setBounds(noteData.getX(), noteData.getY(), 200, 200);

        // Load background image if path exists
        if (noteData.getImagePath() != null && !noteData.getImagePath().isEmpty()) {
          try {
            loadedNote.setBackgroundImage(ImageIO.read(new File(noteData.getImagePath())));
            // Clear background color if image is loaded
            loadedNote.setBackground(new Color(0, 0, 0, 0));
            loadedNote.noteArea.setBackground(new Color(0, 0, 0, 0));
            loadedNote.noteArea.setOpaque(false);
          }
          catch (IOException e) {
            System.err.println("Error loading background image for note " + noteData.getId() + ": " + e.getMessage());
            // Continue without the image if loading fails
          }
        }
        else {
          // Ensure text area is transparent even without image if color was transparent
          loadedNote.noteArea.setOpaque(false);
        }

        notesPanel.add(loadedNote, noteData.getZOrder()); // Add with specified Z-order
      }

      // Ensure the notesPanel's preferred size is large enough for loaded notes
      updateNotesPanelPreferredSize();

      notesPanel.revalidate();
      notesPanel.repaint();
      System.out.println("Notes loaded from database.");

    }
    catch (SQLException e) {
      e.printStackTrace();
      JOptionPane.showMessageDialog(this,
          "Error loading notes from database: " + e.getMessage(),
          "Database Error", JOptionPane.ERROR_MESSAGE);
    }
  }

  // Method to save all current notes to the database (used on application close and export)
  private void saveNotesToDatabase() {
    // Clear existing notes in the database before saving current state
    String clearSql = "DELETE FROM notes";
    try (Statement stmt = dbConnection.createStatement()) {
      stmt.executeUpdate(clearSql);
      System.out.println("Cleared existing notes in database.");
    }
    catch (SQLException e) {
      e.printStackTrace();
      JOptionPane.showMessageDialog(this,
          "Error clearing notes from database: " + e.getMessage(),
          "Database Error", JOptionPane.ERROR_MESSAGE);
      return; // Stop saving if clearing fails
    }

    // Insert all current notes into the database
    String insertSql = "INSERT INTO notes (id, content, x, y, color, image_path, z_order) VALUES (?, ?, ?, ?, ?, ?, ?)";
    try (PreparedStatement pstmt = dbConnection.prepareStatement(insertSql)) {
      Component[] components = notesPanel.getComponents();
      for (int i = 0; i < components.length; i++) {
        if (components[i] instanceof NotePanel note) {
          pstmt.setString(1, note.getNoteId());
          pstmt.setString(2, note.getNoteContent());
          pstmt.setInt(3, note.getX());
          pstmt.setInt(4, note.getY());
          pstmt.setInt(5, note.getBackground().getRGB());
          pstmt.setString(6, note.getBackgroundImagePath());
          pstmt.setInt(7, notesPanel.getComponentZOrder(note)); // Save current Z-order
          pstmt.executeUpdate();
        }
      }
      System.out.println("All notes saved to database.");
    }
    catch (SQLException e) {
      e.printStackTrace();
      JOptionPane.showMessageDialog(this,
          "Error saving notes to database: " + e.getMessage(),
          "Database Error", JOptionPane.ERROR_MESSAGE);
    }
  }

  // Method to export the database file
  private void exportDatabase() {
    JFileChooser fileChooser = new JFileChooser();
    fileChooser.setDialogTitle("Export Database");
    fileChooser.setSelectedFile(new File(DB_NAME + ".mv.db")); // Suggest a default file name

    int userSelection = fileChooser.showSaveDialog(this);

    if (userSelection == JFileChooser.APPROVE_OPTION) {
      File fileToSave = fileChooser.getSelectedFile();
      // Ensure the file has the .mv.db extension
      if (!fileToSave.getAbsolutePath().toLowerCase().endsWith(".mv.db")) {
        fileToSave = new File(fileToSave.getAbsolutePath() + ".mv.db");
      }

      // Close the database connection before copying the file
      closeDatabase();

      try {
        File sourceFile = new File(DB_NAME + ".mv.db"); // The current database file
        Files.copy(sourceFile.toPath(), fileToSave.toPath(), StandardCopyOption.REPLACE_EXISTING);
        JOptionPane.showMessageDialog(this,
            "Database exported successfully!",
            "Export Successful", JOptionPane.INFORMATION_MESSAGE);
      }
      catch (IOException e) {
        e.printStackTrace();
        JOptionPane.showMessageDialog(this,
            "Error exporting database: " + e.getMessage(),
            "Export Error", JOptionPane.ERROR_MESSAGE);
      }
      finally {
        // Re-initialize the database connection after copying
        initDatabase();
        // Reload notes to ensure the application state is consistent
        loadNotesFromDatabase();
      }
    }
  }

  // Method to import a database file
  private void importDatabase() {
    JFileChooser fileChooser = new JFileChooser();
    fileChooser.setDialogTitle("Import Database");
    // Optional: Filter for H2 database files
    FileNameExtensionFilter filter = new FileNameExtensionFilter(
        "H2 Database Files (*.mv.db)", "mv.db");
    fileChooser.setFileFilter(filter);

    int userSelection = fileChooser.showOpenDialog(this);

    if (userSelection == JFileChooser.APPROVE_OPTION) {
      File fileToLoad = fileChooser.getSelectedFile();

      // Close the database connection before replacing the file
      closeDatabase();

      try {
        File targetFile = new File(DB_NAME + ".mv.db"); // The current database file to be replaced
        // Attempt to delete the existing database file(s) first
        File oldDbFile = new File(DB_NAME + ".mv.db");
        if (oldDbFile.exists())
          oldDbFile.delete();
        // H2 might also create a .trace.db file
        File oldTraceFile = new File(DB_NAME + ".trace.db");
        if (oldTraceFile.exists())
          oldTraceFile.delete();


        Files.copy(fileToLoad.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        JOptionPane.showMessageDialog(this,
            "Database imported successfully! The application will now reload.",
            "Import Successful", JOptionPane.INFORMATION_MESSAGE);

      }
      catch (IOException e) {
        e.printStackTrace();
        JOptionPane.showMessageDialog(this,
            "Error importing database: " + e.getMessage(),
            "Import Error", JOptionPane.ERROR_MESSAGE);
      }
      finally {
        // Re-initialize the database connection after copying
        initDatabase();
        // Reload notes from the newly imported database
        loadNotesFromDatabase();
      }
    }
  }


  // Helper method to update the notesPanel's preferred size based on note positions
  private void updateNotesPanelPreferredSize() {
    int maxRight = 0;
    int maxBottom = 0;
    for (Component comp : notesPanel.getComponents()) {
      if (comp.getX() + comp.getWidth() > maxRight) {
        maxRight = comp.getX() + comp.getWidth();
      }
      if (comp.getY() + comp.getHeight() > maxBottom) {
        maxBottom = comp.getY() + comp.getHeight();
      }
    }
    notesPanel.setPreferredSize(new Dimension(maxRight + NOTE_SPACING, maxBottom + NOTE_SPACING));
    notesPanel.revalidate(); // Re-layout the parent panel
  }


  // Method to get a random color from the predefined array
  private Color getRandomNoteColor() {
    return noteColors[random.nextInt(noteColors.length)];
  }

  // Custom JPanel class to represent a single note with dragging, right-click menu, and database
  // interaction
  private static class NotePanel extends JPanel {
    private final String noteId; // Unique ID for the note
    private final JTextArea noteArea; // Text area for the note content
    private Point initialClick; // To store the initial click point for dragging
    private final JPanel parentPanel; // Reference to the parent panel (notesPanel)
    private final JFrame parentFrame; // Reference to the parent frame
    private BufferedImage backgroundImage; // To store the background image
    private String backgroundImagePath; // To store the path of the background image
    private final Connection dbConnection; // Database connection

    public NotePanel(String id, Color backgroundColor, JPanel parentPanel, JFrame parentFrame, Connection dbConnection) {
      this.noteId = id;
      this.parentPanel = parentPanel;
      this.parentFrame = parentFrame;
      this.dbConnection = dbConnection;

      // Set up the note panel
      setBackground(backgroundColor); // Set background color
      setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY, 1)); // Add a light border
      setLayout(new BorderLayout()); // Use BorderLayout for the text area

      // Create the text area for the note content
      noteArea = new JTextArea("Enter your note here...");
      noteArea.setLineWrap(true); // Enable line wrapping
      noteArea.setWrapStyleWord(true); // Wrap at word boundaries
      noteArea.setFont(new Font("Arial", Font.PLAIN, 12));
      noteArea.setMargin(new Insets(10, 10, 10, 10)); // Add padding inside the text area
      noteArea.setBackground(backgroundColor); // Match text area background to panel
      noteArea.setOpaque(false); // Make text area transparent to show background image/color

      // Add the text area to the panel
      add(noteArea, BorderLayout.CENTER);

      // Add mouse listeners for dragging and right-click menu
      MouseAdapter mouseAdapter = new MouseAdapter() {
        @Override
        public void mousePressed(MouseEvent e) {
          // Record the initial click point relative to the note panel
          initialClick = e.getPoint();
          getComponentAt(initialClick); // Important for dragging

          // Check for right-click to show the context menu
          if (SwingUtilities.isRightMouseButton(e)) {
            showContextMenu(e);
          }
        }

        @Override
        public void mouseDragged(MouseEvent e) {
          if (initialClick != null) {
            // Get the current location of the note panel
            int thisX = getLocation().x;
            int thisY = getLocation().y;

            // Calculate the new location based on mouse movement
            int xMoved = e.getX() - initialClick.x;
            int yMoved = e.getY() - initialClick.y;

            int newX = thisX + xMoved;
            int newY = thisY + yMoved;

            // Update the note panel's location
            setLocation(newX, newY);

            // Update the preferred size of the parent panel if the note is dragged outside
            int maxRight = 0;
            int maxBottom = 0;
            for (Component comp : parentPanel.getComponents()) {
              if (comp.getX() + comp.getWidth() > maxRight) {
                maxRight = comp.getX() + comp.getWidth();
              }
              if (comp.getY() + comp.getHeight() > maxBottom) {
                maxBottom = comp.getY() + comp.getHeight();
              }
            }
            parentPanel.setPreferredSize(new Dimension(maxRight + NOTE_SPACING, maxBottom + NOTE_SPACING));
            parentPanel.revalidate(); // Re-layout the parent panel

            // Update the note's position in the database during dragging (can be optimized)
            updateNotePositionInDatabase();
          }
        }

        @Override
        public void mouseReleased(MouseEvent e) {
          initialClick = null; // Reset initial click point
          // Final update of the note's position in the database after dragging
          updateNotePositionInDatabase();
        }
      };

      addMouseListener(mouseAdapter);
      addMouseMotionListener(mouseAdapter);

      // Add mouse listener to the text area as well, so dragging works even when clicking on text
      noteArea.addMouseListener(mouseAdapter);
      noteArea.addMouseMotionListener(mouseAdapter);

      // Add a listener to save content changes when the text area loses focus
      noteArea.addFocusListener(new java.awt.event.FocusAdapter() {
        @Override
        public void focusLost(java.awt.event.FocusEvent evt) {
          updateNoteContentInDatabase();
        }
      });
    }

    // Getters for note properties
    public String getNoteId() {
      return noteId;
    }

    public String getNoteContent() {
      return noteArea.getText();
    }

    public String getBackgroundImagePath() {
      return backgroundImagePath;
    }

    // Setter for note content (used when loading from database)
    public void setNoteContent(String content) {
      noteArea.setText(content);
    }

    // Setter for background image (used when loading from database)
    public void setBackgroundImage(BufferedImage image) {
      this.backgroundImage = image;
    }


    // Override paintComponent to draw the background image or color
    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g); // Paint the default background (color)

      if (backgroundImage != null) {
        // Draw the background image scaled to the panel size
        g.drawImage(backgroundImage, 0, 0, getWidth(), getHeight(), this);
      }
    }

    // Method to show the right-click context menu
    private void showContextMenu(MouseEvent e) {
      JPopupMenu contextMenu = new JPopupMenu();

      JMenuItem sendBackButton = new JMenuItem("Send Back");
      sendBackButton.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent ae) {
          // Move the note to the bottom of the Z-order
          parentPanel.setComponentZOrder(NotePanel.this, 0);
          parentPanel.repaint(); // Repaint to reflect the change
          updateNoteZOrderInDatabase(); // Update Z-order in database
        }
      });

      JMenuItem bringFrontButton = new JMenuItem("Bring Front");
      bringFrontButton.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent ae) {
          // Move the note to the top of the Z-order
          parentPanel.setComponentZOrder(NotePanel.this, parentPanel.getComponentCount() - 1);
          parentPanel.repaint(); // Repaint to reflect the change
          updateNoteZOrderInDatabase(); // Update Z-order in database
        }
      });

      JMenuItem changeColorButton = new JMenuItem("Change Color");
      changeColorButton.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent ae) {
          // Show a color chooser dialog
          Color newColor = JColorChooser.showDialog(
              parentFrame, // Parent component for the dialog
              "Choose Note Color", // Dialog title
              getBackground()); // Initial color (current background)

          // If a color was selected (user didn't cancel)
          if (newColor != null) {
            setBackground(newColor); // Set the note panel's background
            noteArea.setBackground(newColor); // Set the text area's background
            noteArea.setOpaque(false); // Ensure text area is transparent
            backgroundImage = null; // Clear any background image
            backgroundImagePath = null; // Clear image path
            repaint(); // Repaint the note panel
            updateNoteColorAndImageInDatabase(); // Update in database
          }
        }
      });

      JMenuItem setBackgroundImageButton = new JMenuItem("Set Background Image");
      setBackgroundImageButton.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent ae) {
          JFileChooser fileChooser = new JFileChooser();
          // Optional: Filter for image files
          FileNameExtensionFilter filter = new FileNameExtensionFilter(
              "Image Files", ImageIO.getReaderFileSuffixes());
          fileChooser.setFileFilter(filter);

          int result = fileChooser.showOpenDialog(parentFrame);

          if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            try {
              backgroundImage = ImageIO.read(selectedFile);
              backgroundImagePath = selectedFile.getAbsolutePath(); // Store the image path
              // Clear the background color when an image is set
              setBackground(new Color(0, 0, 0, 0)); // Set to transparent
              noteArea.setBackground(new Color(0, 0, 0, 0)); // Set text area to transparent
              noteArea.setOpaque(false); // Ensure text area is transparent
              repaint(); // Repaint the note panel to show the image
              updateNoteColorAndImageInDatabase(); // Update in database
            }
            catch (IOException ex) {
              ex.printStackTrace();
              JOptionPane.showMessageDialog(parentFrame,
                  "Error loading image: " + ex.getMessage(),
                  "Image Load Error", JOptionPane.ERROR_MESSAGE);
            }
          }
        }
      });

      JMenuItem deleteNoteButton = new JMenuItem("Delete Note");
      deleteNoteButton.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent ae) {
          // Remove the note panel from its parent container
          parentPanel.remove(NotePanel.this);
          parentPanel.revalidate(); // Re-layout the parent panel
          parentPanel.repaint(); // Repaint the parent panel
          deleteNoteFromDatabase(); // Delete from database
        }
      });


      contextMenu.add(sendBackButton);
      contextMenu.add(bringFrontButton);
      contextMenu.addSeparator(); // Add a separator line
      contextMenu.add(changeColorButton);
      contextMenu.add(setBackgroundImageButton);
      contextMenu.addSeparator(); // Add another separator line
      contextMenu.add(deleteNoteButton);


      contextMenu.show(e.getComponent(), e.getX(), e.getY());
    }

    // Database update methods
    private void updateNotePositionInDatabase() {
      String sql = "UPDATE notes SET x = ?, y = ? WHERE id = ?";
      try (PreparedStatement pstmt = dbConnection.prepareStatement(sql)) {
        pstmt.setInt(1, getX());
        pstmt.setInt(2, getY());
        pstmt.setString(3, noteId);
        pstmt.executeUpdate();
        // System.out.println("Note position updated in database: " + noteId); // Optional: for debugging
      }
      catch (SQLException e) {
        e.printStackTrace();
      }
    }

    private void updateNoteContentInDatabase() {
      String sql = "UPDATE notes SET content = ? WHERE id = ?";
      try (PreparedStatement pstmt = dbConnection.prepareStatement(sql)) {
        pstmt.setString(1, getNoteContent());
        pstmt.setString(2, noteId);
        pstmt.executeUpdate();
        System.out.println("Note content updated in database: " + noteId); // Optional: for debugging
      }
      catch (SQLException e) {
        e.printStackTrace();
      }
    }

    private void updateNoteColorAndImageInDatabase() {
      String sql = "UPDATE notes SET color = ?, image_path = ? WHERE id = ?";
      try (PreparedStatement pstmt = dbConnection.prepareStatement(sql)) {
        pstmt.setInt(1, getBackground().getRGB());
        pstmt.setString(2, backgroundImagePath);
        pstmt.setString(3, noteId);
        pstmt.executeUpdate();
        System.out.println("Note color/image updated in database: " + noteId); // Optional: for debugging
      }
      catch (SQLException e) {
        e.printStackTrace();
      }
    }

    private void updateNoteZOrderInDatabase() {
      String sql = "UPDATE notes SET z_order = ? WHERE id = ?";
      try (PreparedStatement pstmt = dbConnection.prepareStatement(sql)) {
        pstmt.setInt(1, parentPanel.getComponentZOrder(NotePanel.this));
        pstmt.setString(2, noteId);
        pstmt.executeUpdate();
        System.out.println("Note Z-order updated in database: " + noteId); // Optional: for debugging
      }
      catch (SQLException e) {
        e.printStackTrace();
      }
    }

    private void deleteNoteFromDatabase() {
      String sql = "DELETE FROM notes WHERE id = ?";
      try (PreparedStatement pstmt = dbConnection.prepareStatement(sql)) {
        pstmt.setString(1, noteId);
        pstmt.executeUpdate();
        System.out.println("Note deleted from database: " + noteId);
      }
      catch (SQLException e) {
        e.printStackTrace();
        JOptionPane.showMessageDialog(parentFrame,
            "Error deleting note from database: " + e.getMessage(),
            "Database Error", JOptionPane.ERROR_MESSAGE);
      }
    }
  }

  // Helper class to hold note data loaded from the database
  private static class NoteData {
    private final String id;
    private final String content;
    private final int x;
    private final int y;
    private final Color color;
    private final String imagePath;
    private final int zOrder;

    public NoteData(String id, String content, int x, int y, Color color, String imagePath, int zOrder) {
      this.id = id;
      this.content = content;
      this.x = x;
      this.y = y;
      this.color = color;
      this.imagePath = imagePath;
      this.zOrder = zOrder;
    }

    public String getId() {
      return id;
    }

    public String getContent() {
      return content;
    }

    public int getX() {
      return x;
    }

    public int getY() {
      return y;
    }

    public Color getColor() {
      return color;
    }

    public String getImagePath() {
      return imagePath;
    }

    public int getZOrder() {
      return zOrder;
    }
  }


  public static void main(String[] args) {

    FlatDarkLaf.setup();

    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        new GoogleKeepSwingApp().setVisible(true);
      }
    });
  }
}
