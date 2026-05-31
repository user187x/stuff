package xxx.com.calendar.ics;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel; // Using JLabel might be better for static text + dynamic count
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTextPane; // Switched from JTextArea
import javax.swing.SwingConstants; // For centering JLabel text
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder; // For padding
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.BadLocationException;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.data.ParserException;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.component.CalendarComponent;
import net.fortuna.ical4j.model.component.VEvent;

public class IcsReader extends JFrame implements ActionListener {

  /**
  *
  */
  private static final long serialVersionUID = 1L;
  private final JTextPane calendarTextPane; // Switched to JTextPane
  private final JTextField searchField;
  private final JButton searchButton;
  private final JButton loadButton; // Using a button for "Load File" as it's simpler than a menu for this example
  private final JButton clearButton; // New button for clearing search
  private final JScrollPane scrollPane;
  private final JLabel vEventCountLabel; // Use a JLabel for the count display

  private net.fortuna.ical4j.model.Calendar loadedCalendar;

  private final StyledDocument styledDocument;
  private final Style defaultStyle;
  private final Style highlightStyle;

  public IcsReader() {
    super("ICS File Reader and Search");
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    setSize(600, 500);
    setLayout(new BorderLayout());

    // --- Top Panel (Load, Search, and Clear) ---
    JPanel topPanel = new JPanel(new FlowLayout());
    loadButton = new JButton("Load File");
    loadButton.addActionListener(this);
    topPanel.add(loadButton);

    searchField = new JTextField(20);
    // Add an ActionListener to the searchField for Enter key press
    searchField.addActionListener(this);
    topPanel.add(searchField);

    searchButton = new JButton("Search");
    searchButton.addActionListener(this);
    topPanel.add(searchButton);

    // Add the new Clear Search button
    clearButton = new JButton("Clear Search");
    clearButton.addActionListener(this);
    topPanel.add(clearButton);

    add(topPanel, BorderLayout.NORTH);

    // --- Center Panel (Scrollable Text Pane) ---
    calendarTextPane = new JTextPane(); // Switched to JTextPane
    calendarTextPane.setEditable(false); // Make it read-only

    // Initialize the styled document and styles
    styledDocument = (StyledDocument) calendarTextPane.getDocument();
    defaultStyle = styledDocument.addStyle("DefaultStyle", null);
    StyleConstants.setFontFamily(defaultStyle, "SansSerif"); // Optional: Set a default font
    StyleConstants.setFontSize(defaultStyle, 12); // Optional: Set a default font size

    highlightStyle = styledDocument.addStyle("HighlightStyle", defaultStyle);
    StyleConstants.setBackground(highlightStyle, Color.YELLOW);

    scrollPane = new JScrollPane(calendarTextPane); // Use JTextPane in scroll pane
    add(scrollPane, BorderLayout.CENTER);

    // --- Bottom Panel (VEVENT Count) ---
    JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER)); // Center alignment
    vEventCountLabel = new JLabel("VEvents: 0");
    vEventCountLabel.setHorizontalAlignment(SwingConstants.CENTER); // Center text within the label
    bottomPanel.add(vEventCountLabel);
    bottomPanel.setBorder(new EmptyBorder(5, 0, 5, 0)); // Add some vertical padding

    add(bottomPanel, BorderLayout.SOUTH);


    setVisible(true);
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    if (e.getSource() == loadButton) {
      loadFile();
    }
    else if (e.getSource() == searchButton) {
      performSearch();
    }
    else if (e.getSource() == clearButton) { // Handle the clear button action
      clearSearchAndReload();
    }
    else if (e.getSource() == searchField) { // Handle Enter key press in searchField
      performSearch();
    }
  }

  private void loadFile() {
    JFileChooser fileChooser = new JFileChooser();
    FileNameExtensionFilter filter = new FileNameExtensionFilter("ICS Files (*.ics)", "ics");
    fileChooser.setFileFilter(filter);

    int returnValue = fileChooser.showOpenDialog(this);

    if (returnValue == JFileChooser.APPROVE_OPTION) {
      java.io.File selectedFile = fileChooser.getSelectedFile();
      try (InputStream fin = new FileInputStream(selectedFile)) {
        CalendarBuilder builder = new CalendarBuilder();
        loadedCalendar = builder.build(fin);
        clearSearchAndReload(); // Automatically clear search and display all after loading
      }
      catch (IOException | ParserException ex) {
        JOptionPane.showMessageDialog(this, "Error loading or parsing file: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        ex.printStackTrace();
        loadedCalendar = null; // Ensure calendar is null on error
        updateVEventCountDisplay(0); // Reset count on error
      }
    }
    else {
      updateVEventCountDisplay(0); // Reset count if file selection is cancelled
    }
  }

  private void displayCalendarEntries() {
    if (loadedCalendar == null) {
      try {
        styledDocument.remove(0, styledDocument.getLength()); // Clear the text pane
        styledDocument.insertString(0, "No calendar file loaded.", defaultStyle);
        updateVEventCountDisplay(0); // Update count to 0
      }
      catch (BadLocationException e) {
        e.printStackTrace();
      }
      return;
    }

    try {
      styledDocument.remove(0, styledDocument.getLength()); // Clear the text pane
      StringBuilder sb = new StringBuilder();
      int vEventCount = 0; // Counter for VEvents

      // Iterate through components (like VEVENT, VTODO, etc.)
      for (CalendarComponent component : loadedCalendar.getComponents()) {
        if (component instanceof VEvent) {
          vEventCount++; // Increment count for VEvents
        }
        sb.append("--- ").append(component.getName()).append(" ---\n");
        styledDocument.insertString(styledDocument.getLength(), sb.toString(), defaultStyle);
        sb.setLength(0); // Clear the builder

        List<Property> properties = component.getProperties();
        for (Property property : properties) {
          String entry = property.getName() + ": " + property.getValue();
          sb.append(entry).append("\n");
          styledDocument.insertString(styledDocument.getLength(), sb.toString(), defaultStyle);
          sb.setLength(0); // Clear the builder
        }
        sb.append("\n");
        styledDocument.insertString(styledDocument.getLength(), sb.toString(), defaultStyle);
        sb.setLength(0); // Clear the builder
      }
      calendarTextPane.setCaretPosition(0); // Scroll to the top
      updateVEventCountDisplay(vEventCount); // Update the display with the total VEvent count
    }
    catch (BadLocationException e) {
      e.printStackTrace();
    }
  }

  private void performSearch() {
    if (loadedCalendar == null) {
      return; // Cannot search if no calendar is loaded
    }

    String searchTerm = searchField.getText().toLowerCase();

    try {
      styledDocument.remove(0, styledDocument.getLength()); // Clear the text pane

      if (searchTerm.trim().isEmpty()) {
        // If search field is empty, display all entries (VEVENTS and others)
        displayCalendarEntries(); // This will also update the count
        return;
      }

      StringBuilder sb = new StringBuilder();
      List<VEvent> matchingEvents = new ArrayList<>();

      // Iterate through components and find matching VEVENTS
      for (CalendarComponent component : loadedCalendar.getComponents()) {
        if (component instanceof VEvent event) {
          boolean eventMatches = false;
          // Check if any property in the VEVENT contains the search term
          for (Property property : event.getProperties()) {
            if (property.getValue() != null && property.getValue().toLowerCase().contains(searchTerm)) {
              eventMatches = true;
              break; // Found a match in this VEVENT, no need to check other properties
            }
          }
          if (eventMatches) {
            matchingEvents.add(event);
          }
        }
        // Optional: If you want to search non-VEVENT components, add similar logic here
      }

      // Display the matching VEVENTS with highlighting
      if (matchingEvents.isEmpty()) {
        sb.append("No VEVENT entries found matching '").append(searchTerm).append("'");
        styledDocument.insertString(styledDocument.getLength(), sb.toString(), defaultStyle);
      }
      else {
        sb.append("Found ").append(matchingEvents.size()).append(" matching VEVENT(s) for '").append(searchTerm).append("':\n\n");
        styledDocument.insertString(styledDocument.getLength(), sb.toString(), defaultStyle);
        sb.setLength(0); // Clear the builder

        for (VEvent event : matchingEvents) {
          sb.append("--- VEVENT ---\n");
          styledDocument.insertString(styledDocument.getLength(), sb.toString(), defaultStyle);
          sb.setLength(0); // Clear the builder

          List<Property> properties = event.getProperties();
          for (Property property : properties) {
            String propertyName = property.getName();
            String propertyValue = property.getValue();

            if (propertyName != null) {
              styledDocument.insertString(styledDocument.getLength(), propertyName + ": ", defaultStyle);
            }
            else {
              styledDocument.insertString(styledDocument.getLength(), ": ", defaultStyle); // Handle null property name
            }


            if (propertyValue != null) {
              String lowerCaseValue = propertyValue.toLowerCase();
              int lastIndex = 0;
              int searchTermLength = searchTerm.length();

              // Find and highlight all occurrences of the search term in the value
              int index = lowerCaseValue.indexOf(searchTerm, lastIndex);
              while (index != -1) {
                // Append the text before the match
                styledDocument.insertString(styledDocument.getLength(), propertyValue.substring(lastIndex, index), defaultStyle);

                // Append the matching text with highlight style
                styledDocument.insertString(styledDocument.getLength(), propertyValue.substring(index, index + searchTermLength),
                    highlightStyle);

                lastIndex = index + searchTermLength;
                index = lowerCaseValue.indexOf(searchTerm, lastIndex);
              }
              // Append any remaining text after the last match
              if (lastIndex < propertyValue.length()) {
                styledDocument.insertString(styledDocument.getLength(), propertyValue.substring(lastIndex), defaultStyle);
              }
            }

            styledDocument.insertString(styledDocument.getLength(), "\n", defaultStyle); // Newline after property
          }
          styledDocument.insertString(styledDocument.getLength(), "\n", defaultStyle); // Blank line after VEVENT
        }
      }
      calendarTextPane.setCaretPosition(0); // Scroll to the top of search results
      updateVEventCountDisplay(matchingEvents.size()); // Update the display with the count of matching VEvents
    }
    catch (BadLocationException e) {
      e.printStackTrace();
    }
  }

  /**
   * Clears the search field and reloads the full calendar display.
   */
  private void clearSearchAndReload() {
    searchField.setText(""); // Clear the search text field
    displayCalendarEntries(); // Reload and display the full calendar content (this will update the count)
  }

  /**
   * Updates the label displaying the count of VEvents.
   * 
   * @param count The number of VEvents to display.
   */
  private void updateVEventCountDisplay(int count) {
    vEventCountLabel.setText("VEvents: " + count);
  }


  public static void main(String[] args) {
    SwingUtilities.invokeLater(() -> new IcsReader());
  }
}
