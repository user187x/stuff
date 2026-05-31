package xxx.com.pagenate;

import com.formdev.flatlaf.FlatDarkLaf;
import net.datafaker.Faker;
import javax.swing.*;
import javax.swing.Timer;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.sql.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class Paginator extends JFrame {

  // --- Configuration Constants ---
  private static final String DB_URL = "jdbc:h2:mem:paginator_db;DB_CLOSE_DELAY=-1";
  static final int TOTAL_RECORDS = 1_000_000;
  static final int BUFFER_SIZE = 500;
  private final int cellHeight = 20;
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
  volatile Connection dbConnection;
  private List<Integer> matchIndices = new ArrayList<>();
  private Set<Integer> matchSet = new HashSet<>();

  public Paginator() {
    super("Bi-directional Paginator");

    // North panel for seek and search
    JPanel northPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
    JLabel seekLabel = new JLabel("Seek to ID:");
    seekField = new JTextField(10);
    seekField.addActionListener(e -> performSeek());
    JButton seekButton = new JButton("Go");
    seekButton.addActionListener(e -> performSeek());
    northPanel.add(seekLabel);
    northPanel.add(seekField);
    northPanel.add(seekButton);

    JLabel searchLabel = new JLabel("Search:");
    searchField = new JTextField(20);
    searchField.addActionListener(e -> performSearch(searchField.getText()));
    JButton searchButton = new JButton("Search");
    searchButton.addActionListener(e -> performSearch(searchField.getText()));
    JButton clearButton = new JButton("Clear");
    clearButton.addActionListener(e -> {
      searchField.setText("");
      performSearch("");
    });
    northPanel.add(searchLabel);
    northPanel.add(searchField);
    northPanel.add(searchButton);
    northPanel.add(clearButton);

    listModel = new VirtualListModel(this);
    list = new JList<>(listModel);
    list.setFont(new Font("Monospaced", Font.PLAIN, 14));
    list.setFixedCellHeight(cellHeight);
    list.setPrototypeCellValue("ID: 00000000 | Sample text here");
    list.setCellRenderer(new DefaultListCellRenderer() {
      @Override
      public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        if (matchSet.contains(index)) {
          setBackground(Color.YELLOW);
        } else if (!isSelected) {
          setBackground(list.getBackground());
        }
        return this;
      }
    });
    scrollPane = new JScrollPane(list);

    statusLabel = new JLabel("Initializing...");
    statusLabel.setHorizontalAlignment(SwingConstants.LEFT);
    memoryLabel = new JLabel();
    memoryLabel.setHorizontalAlignment(SwingConstants.RIGHT);
    progressBar = new JProgressBar();
    progressBar.setIndeterminate(true);
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
        if (matchIndices.isEmpty()) return;
        g.setColor(Color.RED);
        double panelHeight = getHeight();
        for (int idx : matchIndices) {
          int y = (int) ((idx / (double) TOTAL_RECORDS) * panelHeight);
          g.fillRect(0, y - 1, 10, 3); // Short tick marks
        }
      }
    };
    markerPanel.setPreferredSize(new Dimension(20, 0));
    markerPanel.setBackground(Color.LIGHT_GRAY);
    markerPanel.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        int clickY = e.getY();
        double panelHeight = markerPanel.getHeight();
        int closestDist = Integer.MAX_VALUE;
        int closestIndex = -1;
        for (int idx : matchIndices) {
          int tickY = (int) ((idx / (double) TOTAL_RECORDS) * panelHeight);
          int dist = Math.abs(tickY - clickY);
          if (dist < closestDist) {
            closestDist = dist;
            closestIndex = idx;
          }
        }
        if (closestDist <= HIT_RADIUS && closestIndex != -1) {
          list.ensureIndexIsVisible(closestIndex);
          list.setSelectedIndex(closestIndex);
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

    scrollPane.getViewport().addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent e) {
        updateStatus();
      }
    });

    memoryTimer = new Timer(2000, new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        updateMemoryLabel();
      }
    });

    initializeAndLoadData();
  }

  void updateMemoryLabel() {
    Runtime rt = Runtime.getRuntime();
    long used = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
    memoryLabel.setText("Memory used: " + used + " MB");
  }

  private void updateStatus() {
    Point p = scrollPane.getViewport().getViewPosition();
    int first = list.locationToIndex(p);
    if (first >= 0) {
      statusLabel.setText(String.format("Displaying records starting from ID: %d", first + 1));
    }
  }

  private void initializeAndLoadData() {
    new SwingWorker<Boolean, String>() {
      @Override
      protected Boolean doInBackground() throws Exception {
        publish("Connecting to database...");
        dbConnection = DriverManager.getConnection(DB_URL, "sa", "");
        try (Statement stmt = dbConnection.createStatement()) {
          stmt.execute("CREATE TABLE IF NOT EXISTS records (id INT PRIMARY KEY, content VARCHAR(255))");
          try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM records")) {
            rs.next();
            if (rs.getInt(1) == 0) {
              publish("Seeding database with " + TOTAL_RECORDS + " records... Please wait.");
              String sql = "INSERT INTO records (id, content) VALUES (?, ?)";
              try (PreparedStatement ps = dbConnection.prepareStatement(sql)) {
                Faker faker = new Faker();
                for (int i = 1; i <= TOTAL_RECORDS; i++) {
                  ps.setInt(1, i);
                  ps.setString(2, String.format("ID: %-8d | %s", i, faker.lorem().sentence(5, 5)));
                  ps.addBatch();
                  if (i % 5000 == 0) {
                    ps.executeBatch();
                    if (i % 100000 == 0) {
                      publish("Seeding... " + i + " / " + TOTAL_RECORDS);
                    }
                  }
                }
                ps.executeBatch();
              }
            }
          }
        }
        return true;
      }

      @Override
      protected void process(List<String> chunks) {
        statusLabel.setText(chunks.get(chunks.size() - 1));
      }

      @Override
      protected void done() {
        try {
          if (get()) {
            statusLabel.setText("Database is ready. Loading initial view...");
            memoryTimer.start();
            loadInitialData();
          } else {
            statusLabel.setText("FATAL: Could not initialize database!");
          }
        } catch (Exception e) {
          statusLabel.setText("FATAL: Could not initialize database!");
          e.printStackTrace();
          JOptionPane.showMessageDialog(Paginator.this,
              "Failed to initialize the database. See console for errors.",
              "Database Error", JOptionPane.ERROR_MESSAGE);
        } finally {
          setVisible(true);
        }
      }
    }.execute();
  }

  private void loadInitialData() {
    listModel.loadCache(0);
  }

  private void performSeek() {
    try {
      int id = Integer.parseInt(seekField.getText());
      if (id < 1 || id > TOTAL_RECORDS) return;
      int index = id - 1;
      list.ensureIndexIsVisible(index);
      list.setSelectedIndex(index);
    } catch (Exception ex) {
      // Ignore invalid input
    }
  }

  private void performSearch(String query) {
    matchIndices.clear();
    matchSet.clear();
    markerPanel.repaint();
    list.repaint();
    if (query.isEmpty()) {
      return;
    }
    statusLabel.setText("Searching...");
    progressBar.setVisible(true);
    new SwingWorker<List<Integer>, Void>() {
      @Override
      protected List<Integer> doInBackground() throws Exception {
        List<Integer> ids = new ArrayList<>();
        String sql = "SELECT id FROM records WHERE content LIKE ? ORDER BY id";
        try (PreparedStatement ps = dbConnection.prepareStatement(sql)) {
          ps.setString(1, "%" + query + "%");
          try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
              ids.add(rs.getInt(1));
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
          matchIndices = ids.stream().map(id -> id - 1).collect(Collectors.toList());
          matchSet = new HashSet<>(matchIndices);
          markerPanel.repaint();
          list.repaint();
          statusLabel.setText("Search complete. Found " + matchIndices.size() + " matches.");
        } catch (Exception e) {
          statusLabel.setText("Error during search.");
          e.printStackTrace();
        }
      }
    }.execute();
  }

  public List<Integer> getMatchIndices() {
    return matchIndices;
  }

  public int getCellHeight() {
    return cellHeight;
  }

  public static void main(String[] args) {
    FlatDarkLaf.setup();
    SwingUtilities.invokeLater(Paginator::new);
  }
}

class VirtualListModel extends AbstractListModel<String> {

  private List<String> cache = new ArrayList<>();
  private int cacheStart = 0;
  private boolean loading = false;
  private final Paginator parent;
  private final int bufferSize = Paginator.BUFFER_SIZE;

  public VirtualListModel(Paginator parent) {
    this.parent = parent;
  }

  @Override
  public int getSize() {
    return Paginator.TOTAL_RECORDS;
  }

  @Override
  public String getElementAt(int index) {
    if (index < 0 || index >= getSize()) {
      return null;
    }
    if (parent.dbConnection == null) {
      return "Initializing database...";
    }
    int relIndex = index - cacheStart;
    if (relIndex >= 0 && relIndex < cache.size()) {
      return cache.get(relIndex);
    } else {
      if (!loading && parent.dbConnection != null) {
        loading = true;
        loadCache(index);
      }
      return "Loading...";
    }
  }

  protected void loadCache(final int centerIndex) {
    new SwingWorker<List<String>, Void>() {
      @Override
      protected List<String> doInBackground() throws Exception {
        int start = Math.max(0, centerIndex - bufferSize / 2);
        start = Math.min(start, getSize() - bufferSize);
        int limit = Math.min(bufferSize, getSize() - start);

        List<String> results = new ArrayList<>(limit);
        String sql = "SELECT content FROM records WHERE id >= ? AND id <= ? ORDER BY id ASC";

        try (PreparedStatement ps = parent.dbConnection.prepareStatement(sql)) {
          ps.setInt(1, start + 1);
          ps.setInt(2, start + limit);
          try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
              results.add(rs.getString("content"));
            }
          }
        }
        return results;
      }

      @Override
      protected void done() {
        try {
          List<String> data = get();
          int newStart = Math.max(0, centerIndex - bufferSize / 2);
          newStart = Math.min(newStart, getSize() - bufferSize);
          cache = data;
          cacheStart = newStart;
          fireContentsChanged(VirtualListModel.this, cacheStart, cacheStart + data.size() - 1);
          parent.updateMemoryLabel();
        } catch (Exception e) {
          parent.statusLabel.setText("Error fetching data. See console for details.");
          e.printStackTrace();
        } finally {
          loading = false;
        }
      }
    }.execute();
  }
}
