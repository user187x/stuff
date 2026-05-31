package xxx.com.calendar.ics;

import com.formdev.flatlaf.FlatDarkLaf;

import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

public class ICSViewer extends JFrame {

  // --- Configuration Constants ---
  private static final String DB_URL = "jdbc:h2:mem:ics_viewer_db;DB_CLOSE_DELAY=-1";
  static final int BUFFER_SIZE = 500;
  private static final int HIT_RADIUS = 5;

  // --- UI Components ---
  final JList<String> list;
  final VirtualListModel listModel;
  final JScrollPane scrollPane;
  final JLabel statusLabel;
  final JLabel memoryLabel;
  final Timer memoryTimer;
  private final JTextField seekField;
  private final JTextField searchField;
  private final JPanel markerPanel;
  private final JProgressBar progressBar;

  // --- State Management ---
  // FIX: Added final and a lock object for thread-safe DB access.
  final Connection dbConnection;
  private final Object dbLock = new Object();
  private List<Integer> matchIndices = new ArrayList<>();
  // FIX: Made this volatile as it's replaced by a new list, not modified concurrently.
  volatile List<Integer> filteredIndices = null;
  private Set<Integer> matchSet = new HashSet<>();
  volatile int totalRecords = 0;
  private int expandedIndex = -1;

  // FIX: Added a simple data class to hold pre-parsed event info for the cache.
  // This avoids DB calls on the EDT.
  record EventData(int id, String displayString, Map<String, String> fields) {}


  public ICSViewer() throws SQLException {
    super("ICS Viewer");

    // FIX: Initialize database connection once and handle potential failure.
    this.dbConnection = DriverManager.getConnection(DB_URL, "sa", "");

    // North panel for seek and search
    JPanel northPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
    northPanel.add(new JLabel("Seek to ID:"));
    seekField = new JTextField(10);
    seekField.addActionListener(e -> performSeek());
    JButton seekButton = new JButton("Go");
    seekButton.addActionListener(e -> performSeek());
    northPanel.add(seekField);
    northPanel.add(seekButton);

    northPanel.add(new JLabel("Search:"));
    searchField = new JTextField(20);
    searchField.addKeyListener(new KeyAdapter() {
      @Override
      public void keyReleased(KeyEvent e) {
        performSearch(searchField.getText());
      }
    });
    JButton clearButton = new JButton("Clear");
    clearButton.addActionListener(e -> {
      searchField.setText("");
      performSearch("");
    });
    northPanel.add(searchField);
    northPanel.add(clearButton);

    listModel = new VirtualListModel(this);
    list = new JList<>(listModel);
    list.setFont(new Font("Monospaced", Font.PLAIN, 14));
    // FIX: A fixed cell height is better for performance, but variable height is needed for expansion.
    // -1 means variable height, which is correct for this design.
    list.setFixedCellHeight(-1);
    list.setPrototypeCellValue("ID: 00000000 | Date: h:mm a M-d-yyyy Z | Sample title here");
    list.setCellRenderer(new CustomCellRenderer(this));
    list.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        handleListClick(e);
      }
    });
    scrollPane = new JScrollPane(list);

    statusLabel = new JLabel("Please drag and drop ICS file(s) or directory here.");
    statusLabel.setHorizontalAlignment(SwingConstants.LEFT);
    memoryLabel = new JLabel();
    memoryLabel.setHorizontalAlignment(SwingConstants.RIGHT);
    progressBar = new JProgressBar();
    progressBar.setVisible(false);
    progressBar.setPreferredSize(new Dimension(100, 20));

    JPanel southPanel = new JPanel(new BorderLayout());
    southPanel.add(statusLabel, BorderLayout.WEST);
    southPanel.add(progressBar, BorderLayout.CENTER);
    southPanel.add(memoryLabel, BorderLayout.EAST);

    markerPanel = new JPanel() {
      @Override
      protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (matchIndices.isEmpty() || totalRecords == 0) return;
        g.setColor(Color.RED);
        double panelHeight = getHeight();
        for (int idx : matchIndices) {
          int y = (int) ((idx / (double) totalRecords) * panelHeight);
          g.fillRect(0, y, 10, 2); // Thin tick marks
        }
      }
    };
    markerPanel.setPreferredSize(new Dimension(10, 0)); // Thinner bar
    markerPanel.setBackground(Color.DARK_GRAY);
    markerPanel.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (totalRecords == 0) return;
        int clickY = e.getY();
        double panelHeight = markerPanel.getHeight();
        int closestDist = Integer.MAX_VALUE;
        int closestIndex = -1;

        // Find the search result marker closest to the click
        for (int idx : matchIndices) {
          int tickY = (int) ((idx / (double) totalRecords) * panelHeight);
          int dist = Math.abs(tickY - clickY);
          if (dist < closestDist) {
            closestDist = dist;
            closestIndex = idx;
          }
        }

        if (closestDist <= HIT_RADIUS && closestIndex != -1) {
          int pos = filteredIndices != null ? filteredIndices.indexOf(closestIndex) : closestIndex;
          if (pos >= 0) {
            list.ensureIndexIsVisible(pos);
            list.setSelectedIndex(pos);
          }
        }
      }
    });

    getContentPane().add(northPanel, BorderLayout.NORTH);
    getContentPane().add(markerPanel, BorderLayout.WEST);
    getContentPane().add(scrollPane, BorderLayout.CENTER);
    getContentPane().add(southPanel, BorderLayout.SOUTH);
    setSize(800, 600);
    setLocationRelativeTo(null);
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

    scrollPane.getViewport().addChangeListener(e -> updateStatus());

    memoryTimer = new Timer(2000, e -> updateMemoryLabel());

    // FIX: Set TransferHandler on a component that actually exists (`scrollPane`).
    scrollPane.setTransferHandler(new TransferHandler() {
      @Override
      public boolean canImport(TransferSupport support) {
        return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
      }

      @Override
      public boolean importData(TransferSupport support) {
        if (!canImport(support)) return false;
        Transferable t = support.getTransferable();
        try {
          @SuppressWarnings("unchecked")
          List<File> files = (List<File>) t.getTransferData(DataFlavor.javaFileListFlavor);
          List<File> icsFiles = collectICSFiles(files);
          if (icsFiles.isEmpty()) {
            JOptionPane.showMessageDialog(ICSViewer.this, "No ICS files found.");
            return false;
          }
          // Disable further drops to prevent re-loading.
          scrollPane.setTransferHandler(null);
          initializeAndLoadData(icsFiles);
          return true;
        } catch (Exception e) {
          e.printStackTrace();
          return false;
        }
      }
    });

    setVisible(true);
  }

  /**
   * FIX: Replaced the unstable editor-on-a-JList with a stable JDialog.
   * This logic identifies which field was clicked and prompts for a new value.
   */
  private void handleListClick(MouseEvent e) {
    int index = list.locationToIndex(e.getPoint());
    if (index == -1) return;

    // Toggle expansion state on any click
    if (e.getButton() == MouseEvent.BUTTON1) {
      expandedIndex = (index == expandedIndex) ? -1 : index;
      // Force the list to re-calculate this cell's bounds and repaint
      listModel.fireContentsChanged(index, index);
      list.ensureIndexIsVisible(index);
    }

    // Handle editing only if the cell is already expanded
    if (index != expandedIndex) return;

    EventData data = listModel.getEventDataAt(index);
    if (data == null) return; // Data not loaded yet

    // Determine which field (key-value pair) was clicked
    String clickedKey = findClickedKey(e.getPoint(), index, data.fields());
    if (clickedKey == null) return;

    String oldValue = data.fields().get(clickedKey);
    boolean isDescription = "DESCRIPTION".equals(clickedKey);

    String newValue;
    if (isDescription) {
      JTextArea textArea = new JTextArea(oldValue, 10, 50);
      textArea.setLineWrap(true);
      textArea.setWrapStyleWord(true);
      int result = JOptionPane.showConfirmDialog(this, new JScrollPane(textArea), "Edit Description", JOptionPane.OK_CANCEL_OPTION);
      if (result == JOptionPane.OK_OPTION) {
        newValue = textArea.getText();
      } else {
        return;
      }
    } else {
      newValue = JOptionPane.showInputDialog(this, "Enter new value for " + clickedKey + ":", oldValue);
    }

    if (newValue != null && !newValue.equals(oldValue)) {
      updateField(data.id(), clickedKey, newValue);
    }
  }

  private String findClickedKey(Point clickPoint, int index, Map<String, String> fields) {
    // This helper method calculates the layout of the cell's fields to find which one was clicked.
    Rectangle cellBounds = list.getCellBounds(index, index);
    if (cellBounds == null) return null;

    // Simulate the layout of the CustomCellRenderer
    int yOffset = list.getFontMetrics(list.getFont()).getHeight() + 4; // +4 for border/insets
    final int inset = 2;
    int currentY = cellBounds.y + yOffset + inset;

    FontMetrics keyMetrics = getFontMetrics(new Font("Default", Font.PLAIN, 12));
    FontMetrics valMetrics = getFontMetrics(new Font("Default", Font.BOLD, 12));

    for (Map.Entry<String, String> entry : fields.entrySet()) {
      int fieldHeight = Math.max(keyMetrics.getHeight(), valMetrics.getHeight());
      Rectangle fieldBounds = new Rectangle(cellBounds.x, currentY, cellBounds.width, fieldHeight);
      if (fieldBounds.contains(clickPoint)) {
        return entry.getKey();
      }
      currentY += fieldHeight + inset * 2;
    }
    return null;
  }


  /**
   * FIX: Database update operations are now synchronized and trigger a UI refresh.
   */
  private void updateField(int id, String fullKey, String newValue) {
    new SwingWorker<Void, Void>() {
      @Override
      protected Void doInBackground() throws Exception {
        // Load the full original content
        String content;
        synchronized (dbLock) {
          content = loadContentSync(id);
        }
        if (content.isEmpty()) return null;

        String[] lines = content.split("\r?\n");
        StringBuilder sb = new StringBuilder();
        boolean updated = false;
        for (String line : lines) {
          if (line.startsWith(fullKey + ":") || line.matches("^" + fullKey + ";.*:.*")) {
            // Rebuild the line with the new value, preserving parameters (like TZID)
            int colonIndex = line.indexOf(':');
            String properties = line.substring(0, colonIndex);
            sb.append(properties).append(":").append(escapeLine(newValue)).append("\r\n");
            updated = true;
          } else {
            sb.append(line).append("\r\n");
          }
        }

        // If the key wasn't found (should not happen), don't change anything
        if (!updated) return null;

        String newContent = sb.toString().trim();

        synchronized (dbLock) {
          try (PreparedStatement ps = dbConnection.prepareStatement("UPDATE records SET content = ? WHERE id = ?")) {
            ps.setString(1, newContent);
            ps.setInt(2, id);
            ps.executeUpdate();
          }
        }
        return null;
      }

      @Override
      protected void done() {
        // FIX: Invalidate the model's cache to force a reload of the updated item.
        int viewIndex = findViewIndexForId(id);
        if (viewIndex != -1) {
          listModel.invalidateCache();
          listModel.fireContentsChanged(viewIndex, viewIndex);
        }
      }
    }.execute();
  }

  private int findViewIndexForId(int id) {
    if (filteredIndices != null) {
      return filteredIndices.indexOf(id - 1);
    }
    return id - 1;
  }


  Map<String, String> parseFields(String content) {
    Map<String, String> map = new LinkedHashMap<>();
    if (content == null) return map;
    String[] lines = content.split("\r?\n");
    for (String line : lines) {
      String trimmed = line.trim();
      if (trimmed.isEmpty() || trimmed.startsWith("BEGIN:") || trimmed.startsWith("END:")) continue;

      int colon = line.indexOf(':');
      if (colon != -1) {
        String key = line.substring(0, colon);
        String value = unescapeLine(line.substring(colon + 1));
        // Handle properties with parameters, like "DTSTART;TZID=America/New_York"
        if (key.contains(";")) {
          key = key.substring(0, key.indexOf(';'));
        }
        map.put(key, value);
      }
    }
    return map;
  }

  /**
   * FIX: Synchronized for thread safety. This is still called from the EDT in some old paths,
   * but the new renderer logic avoids that. The synchronization makes it safe regardless.
   */
  String loadContentSync(int id) {
    synchronized (dbLock) {
      try (PreparedStatement ps = dbConnection.prepareStatement("SELECT content FROM records WHERE id = ?")) {
        ps.setInt(1, id);
        try (ResultSet rs = ps.executeQuery()) {
          if (rs.next()) {
            return rs.getString(1);
          }
        }
      } catch (SQLException ex) {
        ex.printStackTrace();
      }
    }
    return "";
  }

  private List<File> collectICSFiles(List<File> dropped) {
    List<File> ics = new ArrayList<>();
    for (File f : dropped) {
      if (f.isDirectory()) {
        collectRecursive(f, ics);
      } else if (f.getName().toLowerCase().endsWith(".ics")) {
        ics.add(f);
      }
    }
    return ics;
  }

  private void collectRecursive(File dir, List<File> ics) {
    File[] files = dir.listFiles();
    if (files != null) {
      for (File f : files) {
        if (f.isDirectory()) {
          collectRecursive(f, ics);
        } else if (f.getName().toLowerCase().endsWith(".ics")) {
          ics.add(f);
        }
      }
    }
  }

  void updateMemoryLabel() {
    Runtime rt = Runtime.getRuntime();
    long used = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
    memoryLabel.setText(String.format("Memory: %d MB", used));
  }

  private void updateStatus() {
    Point p = scrollPane.getViewport().getViewPosition();
    int first = list.locationToIndex(p);
    if (first >= 0) {
      int origFirst = getOriginalIndex(first);
      statusLabel.setText(String.format("Displaying records starting from ID: %d", origFirst + 1));
    }
  }

  private void initializeAndLoadData(List<File> icsFiles) {
    progressBar.setVisible(true);
    progressBar.setIndeterminate(true);
    new SwingWorker<Boolean, String>() {
      @Override
      protected Boolean doInBackground() throws Exception {
        publish("Connecting to database...");
        synchronized (dbLock) {
          try (Statement stmt = dbConnection.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS records (id INT PRIMARY KEY, content TEXT)");
            // Only parse and load if the table is empty.
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM records")) {
              if (rs.next() && rs.getInt(1) == 0) {
                publish("Parsing ICS files...");
                List<String> allEvents = parseAllEvents(icsFiles);
                if (allEvents.isEmpty()) {
                  throw new IOException("No VEVENT found in ICS files.");
                }
                publish("Seeding start:" + allEvents.size());
                seedDatabase(allEvents);
              }
              // Get final count
              try (ResultSet countRs = stmt.executeQuery("SELECT COUNT(*) FROM records")) {
                if (countRs.next()) {
                  totalRecords = countRs.getInt(1);
                }
              }
            }
          }
        }
        return true;
      }

      private List<String> parseAllEvents(List<File> icsFiles) throws IOException {
        List<String> allEvents = new ArrayList<>();
        for (File file : icsFiles) {
          String content = Files.readString(file.toPath());
          String[] lines = content.split("\r?\n");
          StringBuilder unfoldedContent = new StringBuilder();
          for (String line : lines) {
            if (line.startsWith(" ") || line.startsWith("\t")) {
              unfoldedContent.append(line.substring(1));
            } else {
              if (!unfoldedContent.isEmpty()) {
                unfoldedContent.append("\r\n");
              }
              unfoldedContent.append(line);
            }
          }

          boolean inEvent = false;
          StringBuilder eventBuilder = null;
          for (String unfoldedLine : unfoldedContent.toString().split("\r?\n")) {
            if (unfoldedLine.equals("BEGIN:VEVENT")) {
              inEvent = true;
              eventBuilder = new StringBuilder("BEGIN:VEVENT\r\n");
            } else if (unfoldedLine.equals("END:VEVENT")) {
              if (eventBuilder != null) {
                eventBuilder.append("END:VEVENT\r\n");
                allEvents.add(eventBuilder.toString());
              }
              inEvent = false;
            } else if (inEvent) {
              eventBuilder.append(unfoldedLine).append("\r\n");
            }
          }
        }
        return allEvents;
      }

      private void seedDatabase(List<String> allEvents) throws SQLException {
        String sql = "INSERT INTO records (id, content) VALUES (?, ?)";
        try (PreparedStatement ps = dbConnection.prepareStatement(sql)) {
          for (int i = 0; i < allEvents.size(); i++) {
            ps.setInt(1, i + 1);
            ps.setString(2, allEvents.get(i));
            ps.addBatch();
            if ((i + 1) % 5000 == 0 || i == allEvents.size() - 1) {
              ps.executeBatch();
              publish(String.format("Seeding... %d / %d", i + 1, allEvents.size()));
            }
          }
        }
      }


      @Override
      protected void process(List<String> chunks) {
        String last = chunks.get(chunks.size() - 1);
        statusLabel.setText(last);
        if (last.startsWith("Seeding start:")) {
          int total = Integer.parseInt(last.substring(14));
          progressBar.setIndeterminate(false);
          progressBar.setMaximum(total);
        } else if (last.startsWith("Seeding... ")) {
          String[] parts = last.substring(11).split(" / ");
          int current = Integer.parseInt(parts[0]);
          progressBar.setValue(current);
        }
      }

      @Override
      protected void done() {
        progressBar.setVisible(false);
        try {
          if (get()) {
            statusLabel.setText("Database is ready. Total records: " + totalRecords);
            memoryTimer.start();
            listModel.loadCache(0); // Load initial view
          }
        } catch (Exception e) {
          statusLabel.setText("FATAL: Could not initialize database!");
          e.printStackTrace();
          JOptionPane.showMessageDialog(ICSViewer.this,
              "Failed to initialize the database: " + e.getMessage(),
              "Database Error", JOptionPane.ERROR_MESSAGE);
        }
      }
    }.execute();
  }


  private void performSeek() {
    try {
      int id = Integer.parseInt(seekField.getText());
      if (id < 1 || id > totalRecords) return;
      int origIndex = id - 1;
      int pos = filteredIndices != null ? filteredIndices.indexOf(origIndex) : origIndex;
      if (pos >= 0) {
        list.ensureIndexIsVisible(pos);
        list.setSelectedIndex(pos);
      }
    } catch (NumberFormatException ex) {
      // Ignore invalid input
    }
  }

  private void performSearch(String query) {
    // If query is cleared, reset the view
    if (query == null || query.trim().isEmpty()) {
      filteredIndices = null;
      matchIndices.clear();
      matchSet.clear();
      listModel.invalidateCache();
      listModel.fireContentsChanged(0, totalRecords > 0 ? totalRecords - 1 : 0);
      markerPanel.repaint();
      statusLabel.setText("Showing all " + totalRecords + " events.");
      return;
    }

    statusLabel.setText("Searching...");
    progressBar.setVisible(true);
    progressBar.setIndeterminate(true);

    new SwingWorker<List<Integer>, Void>() {
      @Override
      protected List<Integer> doInBackground() throws Exception {
        List<Integer> ids = new ArrayList<>();
        // Note: LIKE '%...%' is inefficient and will cause a full table scan.
        // For very large datasets, a full-text search index would be required.
        String sql = "SELECT id FROM records WHERE UPPER(content) LIKE UPPER(?) ORDER BY id";
        synchronized (dbLock) {
          try (PreparedStatement ps = dbConnection.prepareStatement(sql)) {
            ps.setString(1, "%" + query + "%");
            try (ResultSet rs = ps.executeQuery()) {
              while (rs.next()) {
                ids.add(rs.getInt(1));
              }
            }
          }
        }
        return ids;
      }

      @Override
      protected void done() {
        progressBar.setVisible(false);
        try {
          List<Integer> ids = get();
          // Convert 1-based IDs to 0-based indices
          matchIndices = ids.stream().map(id -> id - 1).collect(Collectors.toList());
          filteredIndices = new ArrayList<>(matchIndices);
          matchSet = new HashSet<>(matchIndices);

          listModel.invalidateCache();
          listModel.fireContentsChanged(0, filteredIndices.size());
          markerPanel.repaint();
          statusLabel.setText("Found " + matchIndices.size() + " matching events.");
          if (!filteredIndices.isEmpty()) {
            list.ensureIndexIsVisible(0);
          }
        } catch (InterruptedException | ExecutionException e) {
          statusLabel.setText("Error during search.");
          e.printStackTrace();
        }
      }
    }.execute();
  }


  int getOriginalIndex(int index) {
    if (filteredIndices != null) {
      if (index >= 0 && index < filteredIndices.size()) {
        return filteredIndices.get(index);
      }
      return -1; // Index out of bounds
    }
    return index;
  }

  // --- Getters for child classes ---
  public int getExpandedIndex() {
    return expandedIndex;
  }

  public Set<Integer> getMatchSet() {
    return matchSet;
  }

  public Object getDbLock() {
    return dbLock;
  }

  public static void main(String[] args) {
    FlatDarkLaf.setup();
    SwingUtilities.invokeLater(() -> {
      try {
        new ICSViewer();
      } catch (SQLException e) {
        e.printStackTrace();
        JOptionPane.showMessageDialog(null, "Could not connect to the in-memory database.", "Fatal Error", JOptionPane.ERROR_MESSAGE);
        System.exit(1);
      }
    });
  }

  // --- Static Utility Methods ---
  static String unescapeLine(String value) {
    return value.replace("\\\\", "\\").replace("\\n", "\n").replace("\\,", ",").replace("\\;", ";");
  }

  private static String escapeLine(String value) {
    return value.replace("\\", "\\\\").replace("\n", "\\n").replace(",", "\\,").replace(";", "\\;");
  }

  public static String formatICalDate(String dateLine) {
    if (dateLine == null) return "N/A";
    try {
      int colonIndex = dateLine.indexOf(':');
      String prop = dateLine.substring(0, colonIndex);
      String value = dateLine.substring(colonIndex + 1).trim();

      // Handle date-only values (e.g., DTSTART;VALUE=DATE:20250821)
      if (prop.contains("VALUE=DATE")) {
        LocalDate date = LocalDate.parse(value, DateTimeFormatter.ofPattern("yyyyMMdd"));
        return date.format(DateTimeFormatter.ofPattern("MMM d, yyyy"));
      }

      String tzid = null;
      if (prop.contains("TZID=")) {
        tzid = prop.substring(prop.indexOf("TZID=") + 5).split("[;:]")[0];
      }

      boolean isUtc = value.endsWith("Z");
      if (isUtc) value = value.substring(0, value.length() - 1);

      LocalDateTime ldt = LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"));
      ZonedDateTime zdt;
      if (isUtc) {
        zdt = ZonedDateTime.of(ldt, ZoneOffset.UTC);
      } else if (tzid != null) {
        try {
          zdt = ZonedDateTime.of(ldt, ZoneId.of(tzid));
        } catch (DateTimeException e) {
          // Fallback to system default if TZID is invalid
          zdt = ZonedDateTime.of(ldt, ZoneId.systemDefault());
        }
      } else {
        // Floating time, assume system default
        zdt = ZonedDateTime.of(ldt, ZoneId.systemDefault());
      }
      // Convert to user's local timezone for display
      return zdt.withZoneSameInstant(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("h:mm a, M-d-yyyy Z"));
    } catch (DateTimeParseException e) {
      return "Invalid date format";
    }
  }
}

class CustomCellRenderer extends JPanel implements ListCellRenderer<String> {

  private final ICSViewer parent;
  private final JLabel title = new JLabel();
  private final JPanel fieldsPanel = new JPanel();
  private final Font keyFont = new Font("Default", Font.PLAIN, 12);
  private final Font valueFont = new Font("Default", Font.BOLD, 12);

  public CustomCellRenderer(ICSViewer parent) {
    this.parent = parent;
    setLayout(new BorderLayout());
    title.setFont(new Font("Monospaced", Font.PLAIN, 14));
    title.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
    add(title, BorderLayout.NORTH);

    fieldsPanel.setLayout(new GridBagLayout());
    add(fieldsPanel, BorderLayout.CENTER);
  }

  @Override
  public Component getListCellRendererComponent(JList<? extends String> list, String value, int index, boolean isSelected, boolean cellHasFocus) {
    title.setText(value);
    fieldsPanel.removeAll();
    fieldsPanel.setVisible(false);

    // FIX: This now gets pre-parsed data from the model's cache, avoiding any DB calls on the EDT.
    if (index == parent.getExpandedIndex()) {
      ICSViewer.EventData data = ((VirtualListModel) list.getModel()).getEventDataAt(index);
      if (data != null) {
        fieldsPanel.setVisible(true);
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(1, 4, 1, 4);
        int y = 0;
        for (Map.Entry<String, String> entry : data.fields().entrySet()) {
          c.gridx = 0;
          c.gridy = y;
          c.weightx = 0;
          JLabel keyLabel = new JLabel(entry.getKey() + ":");
          keyLabel.setFont(keyFont);
          fieldsPanel.add(keyLabel, c);

          c.gridx = 1;
          c.weightx = 1.0;
          c.fill = GridBagConstraints.HORIZONTAL;
          // Use HTML to allow for word wrapping in the JLabel
          JLabel valueLabel = new JLabel("<html>" + entry.getValue().replace("\n", "<br>") + "</html>");
          valueLabel.setFont(valueFont);
          fieldsPanel.add(valueLabel, c);
          y++;
        }
      }
    }

    if (isSelected) {
      setBackground(list.getSelectionBackground());
      setForeground(list.getSelectionForeground());
    } else {
      // Highlight search results with a different background color
      if (parent.getMatchSet().contains(parent.getOriginalIndex(index))) {
        setBackground(new Color(0x3A537A)); // A subtle dark blue highlight
      } else {
        setBackground(list.getBackground());
      }
      setForeground(list.getForeground());
    }
    return this;
  }
}

class VirtualListModel extends AbstractListModel<String> {

  // FIX: Cache now stores rich EventData objects instead of just strings.
  private final Map<Integer, ICSViewer.EventData> cache = new HashMap<>();
  private volatile boolean loading = false;
  private final ICSViewer parent;

  public VirtualListModel(ICSViewer parent) {
    this.parent = parent;
  }

  @Override
  public int getSize() {
    List<Integer> localFiltered = parent.filteredIndices;
    return localFiltered != null ? localFiltered.size() : parent.totalRecords;
  }

  @Override
  public String getElementAt(int index) {
    if (index < 0 || index >= getSize()) {
      return null;
    }
    // FIX: Get data from the new object-based cache.
    ICSViewer.EventData data = cache.get(index);
    if (data != null) {
      return data.displayString();
    } else {
      // If data is not in cache, trigger a background load.
      if (!loading) {
        loadCache(index);
      }
      return "Loading...";
    }
  }

  /**
   * FIX: This method allows the renderer to get the full, pre-parsed data for an
   * expanded cell without hitting the database.
   */
  public ICSViewer.EventData getEventDataAt(int index) {
    return cache.get(index);
  }

  protected void loadCache(final int centerIndex) {
    loading = true;
    new SwingWorker<Map<Integer, ICSViewer.EventData>, Void>() {
      @Override
      protected Map<Integer, ICSViewer.EventData> doInBackground() throws Exception {
        int localSize = getSize();
        if (localSize == 0) return Collections.emptyMap();

        int start = Math.max(0, centerIndex - ICSViewer.BUFFER_SIZE / 2);
        start = Math.min(start, Math.max(0, localSize - ICSViewer.BUFFER_SIZE));
        int end = Math.min(localSize, start + ICSViewer.BUFFER_SIZE);

        List<Integer> idsToFetch = new ArrayList<>();
        for (int i = start; i < end; i++) {
          idsToFetch.add(parent.getOriginalIndex(i) + 1);
        }

        if (idsToFetch.isEmpty()) return Collections.emptyMap();

        Map<Integer, ICSViewer.EventData> results = new HashMap<>();
        String placeholders = String.join(",", Collections.nCopies(idsToFetch.size(), "?"));
        String sql = "SELECT id, content FROM records WHERE id IN (" + placeholders + ")";

        Map<Integer, String> contentMap = new HashMap<>();
        synchronized (parent.getDbLock()) {
          try (PreparedStatement ps = parent.dbConnection.prepareStatement(sql)) {
            for (int i = 0; i < idsToFetch.size(); i++) {
              ps.setInt(i + 1, idsToFetch.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
              while (rs.next()) {
                contentMap.put(rs.getInt("id"), rs.getString("content"));
              }
            }
          }
        }
        // FIX: Pre-parse everything needed for display in the background.
        for (int i = start; i < end; i++) {
          int originalId = parent.getOriginalIndex(i) + 1;
          String content = contentMap.get(originalId);
          String summary = extractSummary(content);
          String dateStr = extractDate(content);
          String display = String.format("ID: %-8d | Date: %-28s | %s", originalId, dateStr, summary);
          Map<String, String> fields = parent.parseFields(content);
          results.put(i, new ICSViewer.EventData(originalId, display, fields));
        }
        return results;
      }

      private String extractSummary(String content) {
        if (content == null) return "Untitled Event";
        for (String line : content.split("\r?\n")) {
          if (line.startsWith("SUMMARY")) {
            return ICSViewer.unescapeLine(line.substring(line.indexOf(':') + 1));
          }
        }
        return "Untitled Event";
      }

      private String extractDate(String content) {
        if (content == null) return "N/A";
        for (String line : content.split("\r?\n")) {
          if (line.startsWith("DTSTART")) {
            return ICSViewer.formatICalDate(line);
          }
        }
        return "N/A";
      }

      @Override
      protected void done() {
        try {
          Map<Integer, ICSViewer.EventData> data = get();
          cache.putAll(data);
          // Refresh the range that was just loaded
          if (!data.isEmpty()) {
            int min = data.keySet().stream().min(Integer::compareTo).get();
            int max = data.keySet().stream().max(Integer::compareTo).get();
            fireContentsChanged(min, max);
          }
          parent.updateMemoryLabel();
        } catch (Exception e) {
          parent.statusLabel.setText("Error fetching data.");
          e.printStackTrace();
        } finally {
          loading = false;
        }
      }
    }.execute();
  }

  // FIX: Added methods to invalidate the cache when data changes.
  public void invalidateCache() {
    cache.clear();
  }

  public void fireContentsChanged(int index0, int index1) {
    super.fireContentsChanged(this, index0, index1);
  }
}
