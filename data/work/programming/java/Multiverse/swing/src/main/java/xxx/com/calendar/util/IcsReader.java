package xxx.com.calendar.util;

import java.awt.BorderLayout;
import java.awt.CardLayout; // Import CardLayout
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point; // Import Point
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.AdjustmentEvent; // Import AdjustmentEvent
import java.awt.event.AdjustmentListener; // Import AdjustmentListener
import java.awt.event.MouseAdapter; // Import MouseAdapter
import java.awt.event.MouseEvent; // Import MouseEvent
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.temporal.Temporal; // Import Temporal
import java.util.ArrayList;
import java.util.HashSet; // To store event days efficiently
import java.util.List;
import java.util.Locale; // For calendar display
import java.util.Set;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane; // Import JSplitPane
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document; // Import Document
import javax.swing.text.Element; // Import Element
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.data.ParserException;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.component.CalendarComponent;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.property.DtEnd;
import net.fortuna.ical4j.model.property.DtStart;


// Custom JPanel for the loading animation (Existing)
class LoadingAnimationPanel extends JPanel implements ActionListener {
  /**
   *
   */
  private static final long serialVersionUID = 1L;
  private final Timer timer;
  private int angle = 0;
  private final int DELAY = 50; // Milliseconds between timer ticks

  public LoadingAnimationPanel() {
    setBackground(Color.WHITE); // Background color for the panel
    setOpaque(true); // Ensure the panel is opaque for proper rendering
    setBorder(new LineBorder(Color.RED, 2)); // Example border
    setPreferredSize(new Dimension(100, 100)); // Set a small preferred size
    timer = new Timer(DELAY, this);
  }

  public void startAnimation() {
    if (!timer.isRunning()) {
      timer.start();
    }
  }

  public void stopAnimation() {
    if (timer.isRunning()) {
      timer.stop();
    }
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    angle = (angle + 10) % 360;
    repaint();
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    Graphics2D g2d = (Graphics2D) g.create();
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

    int width = getWidth();
    int height = getHeight();

    if (width > 0 && height > 0) {
      int centerX = width / 2;
      int centerY = height / 2;
      int radius = Math.min(width, height) / 8;

      g2d.setColor(Color.BLUE);
      g2d.drawArc(centerX - radius, centerY - radius, radius * 2, radius * 2, angle, 90);
    }

    g2d.dispose();
  }
}


// New JPanel to display the mini-calendar view
class CalendarViewPanel extends JPanel {
  /**
   *
   */
  private static final long serialVersionUID = 1L;
  private net.fortuna.ical4j.model.Calendar calendar;
  private final Set<LocalDate> eventDays; // Stores days with events
  private LocalDate displayMonth; // The month being displayed

  private static final Color EVENT_DAY_COLOR = new Color(173, 216, 230); // Light blue
  private static final Color WEEKEND_COLOR = new Color(240, 240, 240); // Light grey

  // Store calculated cell dimensions for mouse click handling
  private int cellWidth = 0;
  private int cellHeight = 0;
  private int startX = 0;
  private int startY = 0;
  private int calendarAreaWidth = 0;
  private int calendarAreaHeight = 0;
  private int padding = 10;
  private int headerHeight = 30;
  private int dayHeaderHeight = 20;


  public CalendarViewPanel() {
    setBackground(Color.WHITE);
    setBorder(new LineBorder(Color.BLACK, 1)); // Add a border
    setPreferredSize(new Dimension(200, 0)); // Give it an initial preferred width, height can be 0 for vertical expansion
    setMinimumSize(new Dimension(150, 100)); // Set a minimum size
    eventDays = new HashSet<>();
    displayMonth = LocalDate.now(); // Default to current month
  }

  /**
   * Sets the calendar data to be displayed.
   *
   * @param calendar The loaded calendar or null to clear.
   */
  public void setCalendar(net.fortuna.ical4j.model.Calendar calendar) {
    this.calendar = calendar;
    eventDays.clear();
    displayMonth = LocalDate.now(); // Default display month to now
    if (calendar != null) {
      processEvents();
      // Try to set the display month to the month of the first event, if any
      if (!eventDays.isEmpty()) {
        // Find the earliest event day to set the initial display month
        LocalDate firstEventDay = eventDays.stream().min(LocalDate::compareTo).orElse(LocalDate.now());
        displayMonth = firstEventDay.withDayOfMonth(1);
      }
    }
    repaint(); // Request a repaint to draw the new calendar data
  }

  /**
   * Clears the displayed calendar data.
   */
  public void clearCalendar() {
    setCalendar(null);
  }

  /**
   * Processes the loaded calendar to find days with events.
   */
  private void processEvents() {
    if (calendar == null) {
      return;
    }
    for (CalendarComponent component : calendar.getComponents()) {
      if (component instanceof VEvent event) {

        DtStart<Temporal> dtStart = event.getDateTimeStart();
        DtEnd<Temporal> dtEnd = event.getDateTimeEnd();

        LocalDate startDate = null;
        LocalDate endDate = null;


        if (dtStart != null && dtStart.getDate() != null) {
          Temporal startTemporal = dtStart.getDate();
          if (startTemporal instanceof LocalDate) {
            startDate = (LocalDate) startTemporal;
          }
          else if (startTemporal instanceof ZonedDateTime) {
            startDate = ((ZonedDateTime) startTemporal).toLocalDate();
          }
          // Handle other Temporal types if necessary, though LocalDate and ZonedDateTime are most common
        }

        if (dtEnd != null && dtEnd.getDate() != null) {
          Temporal endTemporal = dtEnd.getDate();
          if (endTemporal instanceof LocalDate) {
            endDate = (LocalDate) endTemporal;
          }
          else if (endTemporal instanceof ZonedDateTime) {
            // Note: When converting ZonedDateTime to LocalDate for the end date,
            // we lose time information. For multi-day events with times,
            // this might simplify the range check to be day-based inclusive.
            endDate = ((ZonedDateTime) endTemporal).toLocalDate();
          }
        }


        if (startDate != null) {
          // If no end date, it's a single-day event
          if (endDate == null) {
            eventDays.add(startDate);
          }
          else {
            // Determine the effective end date for iteration.
            // iCal4j DATE end is exclusive (day after). DATE-TIME end is inclusive.
            LocalDate effectiveEndDateForIteration = endDate;

            boolean isAllDayEvent = (dtStart.getDate() instanceof LocalDate); // Simple check based on type


            if (isAllDayEvent && endDate.isAfter(startDate)) {
              // For all-day events, the end date is the day *after* the event ends.
              // The iteration range should go up to and include the actual last day.
              effectiveEndDateForIteration = endDate.minusDays(1);
            }
            // else: for timed events or single-day all-day, endDate is inclusive.

            LocalDate currentDay = startDate;
            while (!currentDay.isAfter(effectiveEndDateForIteration)) {
              eventDays.add(currentDay);
              currentDay = currentDay.plusDays(1);
            }
          }
        }
      }
    }
  }


  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    Graphics2D g2d = (Graphics2D) g.create();
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

    int width = getWidth();
    int height = getHeight();

    if (width <= 0 || height <= 0) {
      g2d.dispose();
      return; // Nothing to draw if size is invalid
    }

    // Calculate dimensions (store in class variables)
    padding = 10;
    headerHeight = 30;
    dayHeaderHeight = 20;
    calendarAreaHeight = height - padding * 2 - headerHeight - dayHeaderHeight;
    calendarAreaWidth = width - padding * 2;

    if (calendarAreaHeight <= 0 || calendarAreaWidth <= 0) {
      g2d.dispose();
      return; // Not enough space
    }


    // Calculate cell size (store in class variables)
    int cols = 7;
    int rows = 6; // Max rows needed for a month grid
    cellWidth = calendarAreaWidth / cols;
    cellHeight = calendarAreaHeight / rows;

    if (cellWidth <= 0 || cellHeight <= 0) {
      g2d.dispose();
      return; // Not enough space per cell
    }

    startX = padding;
    startY = padding + headerHeight + dayHeaderHeight;

    // Declare variables outside conditional blocks to avoid duplicates
    int textWidth;
    int textX;
    int textY; // Declare textY here


    // --- Draw Month and Year Header ---
    g2d.setColor(Color.BLACK);
    g2d.setFont(getFont().deriveFont(getFont().getSize() + 2f)); // Slightly larger font
    FontMetrics fm = g2d.getFontMetrics();
    String monthYearStr = "";
    if (displayMonth != null) {
      monthYearStr = displayMonth.format(java.time.format.DateTimeFormatter.ofPattern("MMMM uuuu", Locale.getDefault())); // Use uuuu for
                                                                                                                          // year
    }
    textWidth = fm.stringWidth(monthYearStr);
    textX = startX + (calendarAreaWidth - textWidth) / 2;
    textY = padding + fm.getAscent(); // Assign value to the declared textY
    g2d.drawString(monthYearStr, textX, textY);

    // --- Draw Day of Week Headers ---
    String[] dayNames = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
    g2d.setFont(getFont().deriveFont(getFont().getSize() - 1f)); // Slightly smaller font
    fm = g2d.getFontMetrics();
    int dayHeaderY = padding + headerHeight + fm.getAscent();
    for (int i = 0; i < cols; i++) {
      textWidth = fm.stringWidth(dayNames[i]);
      textX = startX + i * cellWidth + (cellWidth - textWidth) / 2;
      g2d.drawString(dayNames[i], textX, dayHeaderY);
    }


    // --- Draw Calendar Grid and Days ---
    if (displayMonth != null) {
      // Get the day of the week for the 1st of the month
      int firstDayOfWeek = displayMonth.withDayOfMonth(1).getDayOfWeek().getValue(); // 1=Monday, 7=Sunday
      // Convert to grid column index (0=Sunday, 6=Saturday)
      // If DayOfWeek.MONDAY (1), needs to be col 1. DayOfWeek.SUNDAY (7), needs to be col 0.
      int startCol = (firstDayOfWeek == 7) ? 0 : firstDayOfWeek;


      int daysInMonth = displayMonth.lengthOfMonth();

      int dayCounter = 1;
      for (int row = 0; row < rows; row++) {
        for (int col = 0; col < cols; col++) {

          // Determine if this cell corresponds to a day in the current month
          boolean isBeforeFirstDay = (row == 0 && col < startCol);
          boolean isAfterLastDay = (dayCounter > daysInMonth);

          if (isBeforeFirstDay || isAfterLastDay) {
            // Cell is before the 1st day or after the last day of the month
            continue;
          }

          int cellX = startX + col * cellWidth;
          int cellY = startY + row * cellHeight;

          LocalDate currentDay = displayMonth.withDayOfMonth(dayCounter);

          // Determine if it's a weekend day based on currentDay's day of week
          int dayOfWeekValue = currentDay.getDayOfWeek().getValue(); // 1=Monday, 7=Sunday
          boolean isWeekend = (dayOfWeekValue == 6 || dayOfWeekValue == 7); // Saturday (6) or Sunday (7)

          // Draw cell background (e.g., for weekends or event days)
          if (isWeekend) {
            g2d.setColor(WEEKEND_COLOR);
            g2d.fillRect(cellX, cellY, cellWidth, cellHeight);
          }
          if (eventDays.contains(currentDay)) {
            g2d.setColor(EVENT_DAY_COLOR);
            g2d.fillRect(cellX, cellY, cellWidth, cellHeight); // Draw over weekend color
          }

          // Draw cell border (optional)
          g2d.setColor(Color.LIGHT_GRAY);
          g2d.drawRect(cellX, cellY, cellWidth, cellHeight);

          // Draw day number
          g2d.setColor(Color.BLACK);
          g2d.setFont(getFont()); // Reset to default font for day numbers
          fm = g2d.getFontMetrics();
          String dayStr = String.valueOf(dayCounter);
          textWidth = fm.stringWidth(dayStr);
          textX = cellX + (cellWidth - textWidth) / 2;
          textY = cellY + fm.getAscent() + (cellHeight - fm.getHeight()) / 2; // Assign value to the declared textY
          g2d.drawString(dayStr, textX, textY);

          dayCounter++;
        }
      }
    }
    else {
      // No calendar loaded - display a message
      g2d.setColor(Color.GRAY);
      g2d.setFont(getFont().deriveFont(getFont().getSize() + 1f));
      fm = g2d.getFontMetrics();
      String message = "Load an ICS file";
      textWidth = fm.stringWidth(message);
      textX = startX + (calendarAreaWidth - textWidth) / 2;
      textY = startY + (calendarAreaHeight - fm.getHeight()) / 2 + fm.getAscent(); // Assign value to the declared textY
      g2d.drawString(message, textX, textY);
    }


    g2d.dispose();
  }

  /**
   * Sets the display month for the calendar view.
   *
   * @param month The LocalDate representing the month to display (day of month is ignored).
   */
  public void setDisplayMonth(LocalDate month) {
    if (month != null) {
      this.displayMonth = month.withDayOfMonth(1);
      repaint();
    }
  }

  /**
   * Navigates the calendar display to the previous month.
   */
  public void previousMonth() {
    if (displayMonth != null) {
      displayMonth = displayMonth.minusMonths(1);
      repaint();
    }
  }

  /**
   * Navigates the calendar display to the next month.
   */
  public void nextMonth() {
    if (displayMonth != null) {
      displayMonth = displayMonth.plusMonths(1);
      repaint();
    }
  }

  /**
   * Gets the LocalDate corresponding to the given mouse coordinates, if it's within a valid day cell.
   *
   * @param p The mouse coordinates relative to this panel.
   * @return The LocalDate of the clicked day, or null if outside a valid day cell or no month is
   *         displayed.
   */
  public LocalDate getDateAtPoint(Point p) {
    if (displayMonth == null || calendarAreaWidth <= 0 || calendarAreaHeight <= 0 || cellWidth <= 0 || cellHeight <= 0) {
      return null; // Cannot determine date if not initialized or sized
    }

    // Check if the click is within the calendar grid area
    if (p.x < startX || p.x >= startX + calendarAreaWidth ||
        p.y < startY || p.y >= startY + calendarAreaHeight) {
      return null; // Click is outside the grid area
    }

    // Calculate the clicked row and column
    int col = (p.x - startX) / cellWidth;
    int row = (p.y - startY) / cellHeight;

    // Get the day of the week for the 1st of the month (0=Sunday, 6=Saturday)
    int firstDayOfWeek = displayMonth.withDayOfMonth(1).getDayOfWeek().getValue(); // 1=Monday, 7=Sunday
    int startCol = (firstDayOfWeek == 7) ? 0 : firstDayOfWeek; // Convert to 0-6 index


    // Calculate the day number based on row, col, and the starting column
    int dayNumber = row * 7 + col - startCol + 1;

    // Check if the calculated day number is valid for the current month
    int daysInMonth = displayMonth.lengthOfMonth();
    if (dayNumber >= 1 && dayNumber <= daysInMonth) {
      return displayMonth.withDayOfMonth(dayNumber);
    }

    return null; // Clicked on a cell outside the current month's days
  }
}


public class IcsReader extends JFrame implements ActionListener {

  /**
  *
  */
  private static final long serialVersionUID = 1L;
  private final JTextPane calendarTextPane;
  private final JTextField searchField;
  private final JButton searchButton;
  private final JButton loadButton;
  private final JButton clearButton;
  private final JScrollPane scrollPane;
  private final JLabel vEventCountLabel;
  private final JLabel fileNameLabel; // New JLabel for displaying file name


  private final JPanel mainContentPane; // Panel using CardLayout to swap views
  private final CardLayout cardLayout; // Layout manager for mainContentPane
  private final LoadingAnimationPanel loadingPanel;
  private final JSplitPane splitPane; // Split pane for text and calendar views
  private final CalendarViewPanel calendarViewPanel; // The new calendar panel

  private net.fortuna.ical4j.model.Calendar loadedCalendar;

  private final StyledDocument styledDocument;
  private final Style defaultStyle;
  private final Style highlightStyle;

  private static final String FLOPPY_ICON_PATH = "/resources/saveDisk.png";

  // CardLayout names for the mainContentPane
  private static final String LOADING_VIEW = "LoadingView";
  private static final String CALENDAR_SPLIT_VIEW = "CalendarSplitView";

  // Add navigation buttons for the calendar view
  private final JButton prevMonthButton;
  private final JButton nextMonthButton;

  // Store a mapping from document positions to event start dates (or other relevant date)
  // This is a simplified approach; a more robust solution might map line numbers or VEVENT start
  // markers
  // to their corresponding dates.
  private final List<LocalDate> entryDates = new ArrayList<>();


  public IcsReader() {
    super("ICS File Reader and Search");
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    setSize(800, 600); // Increased size to accommodate split pane
    setLayout(new BorderLayout());

    // --- Top Panel (Load, Search, and Clear) ---
    JPanel topPanel = new JPanel(new FlowLayout());
    ImageIcon floppyIcon = loadIcon(FLOPPY_ICON_PATH);
    loadButton = new JButton();
    if (floppyIcon != null) {
      loadButton.setIcon(floppyIcon);
      loadButton.setToolTipText("Load ICS File");
    }
    else {
      loadButton.setText("Load ICS");
      System.err.println("Warning: Floppy disk icon not found at " + FLOPPY_ICON_PATH + ". Using text button instead.");
    }
    loadButton.addActionListener(this);
    topPanel.add(loadButton);

    searchField = new JTextField(20);
    searchField.addActionListener(this);
    topPanel.add(searchField);

    searchButton = new JButton("Search");
    searchButton.addActionListener(this);
    topPanel.add(searchButton);

    clearButton = new JButton("Clear Search");
    clearButton.addActionListener(this);
    topPanel.add(clearButton);

    // Add calendar navigation buttons to the top panel
    prevMonthButton = new JButton("< Previous Month");
    prevMonthButton.addActionListener(this);
    topPanel.add(prevMonthButton);

    nextMonthButton = new JButton("Next Month >");
    nextMonthButton.addActionListener(this);
    topPanel.add(nextMonthButton);


    add(topPanel, BorderLayout.NORTH);

    // --- Center Area (Swappable Content: Loading or Split Pane) ---
    cardLayout = new CardLayout();
    mainContentPane = new JPanel(cardLayout);

    // 1. Loading Animation Panel
    loadingPanel = new LoadingAnimationPanel();
    mainContentPane.add(loadingPanel, LOADING_VIEW);

    // 2. Calendar Split View Panel
    calendarTextPane = new JTextPane();
    calendarTextPane.setEditable(false);

    styledDocument = (StyledDocument) calendarTextPane.getDocument();
    defaultStyle = styledDocument.addStyle("DefaultStyle", null);
    StyleConstants.setFontFamily(defaultStyle, "SansSerif");
    StyleConstants.setFontSize(defaultStyle, 12);

    highlightStyle = styledDocument.addStyle("HighlightStyle", defaultStyle);
    StyleConstants.setBackground(highlightStyle, Color.YELLOW);

    scrollPane = new JScrollPane(calendarTextPane);

    // Add AdjustmentListener to the vertical scrollbar
    scrollPane.getVerticalScrollBar().addAdjustmentListener(new AdjustmentListener() {
      @Override
      public void adjustmentValueChanged(AdjustmentEvent e) {
        if (!e.getValueIsAdjusting()) { // Only act when scrolling stops
          updateCalendarMonthFromScroll();
        }
      }
    });


    calendarViewPanel = new CalendarViewPanel();
    // Add MouseListener to the calendar panel
    calendarViewPanel.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        handleCalendarClick(e);
      }
    });


    splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, scrollPane, calendarViewPanel);
    splitPane.setResizeWeight(1.0); // Calendar panel gets extra space
    splitPane.setDividerLocation(400); // Initial divider position (adjust as needed)
    splitPane.setContinuousLayout(true); // Update display while dragging


    mainContentPane.add(splitPane, CALENDAR_SPLIT_VIEW);

    // Show the split pane initially (can change to loading if desired)
    cardLayout.show(mainContentPane, CALENDAR_SPLIT_VIEW);


    add(mainContentPane, BorderLayout.CENTER);


    // --- Bottom Panel (VEVENT Count and File Name) ---
    JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 5)); // Added horizontal gap
    vEventCountLabel = new JLabel("Calendar Entries : 0");
    vEventCountLabel.setHorizontalAlignment(SwingConstants.LEFT); // Align left in FlowLayout
    bottomPanel.add(vEventCountLabel);

    fileNameLabel = new JLabel("File: None Loaded"); // Initialize file name label
    fileNameLabel.setHorizontalAlignment(SwingConstants.RIGHT); // Align right in FlowLayout
    bottomPanel.add(fileNameLabel);

    bottomPanel.setBorder(new EmptyBorder(5, 0, 5, 0));

    add(bottomPanel, BorderLayout.SOUTH);

    // Initial state: Clear text pane and calendar view
    clearSearchAndReload();
    updateFileNameDisplay(null); // Set initial file name display

    setVisible(true);
  }


  private ImageIcon loadIcon(String path) {
    URL imgURL = getClass().getResource(path);
    if (imgURL != null) {
      return new ImageIcon(imgURL);
    }
    else {
      return null;
    }
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    if (e.getSource() == loadButton) {
      loadFile();
    }
    else if (e.getSource() == searchButton || e.getSource() == searchField) {
      performSearch();
    }
    else if (e.getSource() == clearButton) {
      clearSearchAndReload();
    }
    else if (e.getSource() == prevMonthButton) {
      calendarViewPanel.previousMonth();
    }
    else if (e.getSource() == nextMonthButton) {
      calendarViewPanel.nextMonth();
    }
  }

  /**
   * Handles a mouse click event on the calendar view panel. Determines the clicked date and displays
   * events for that day.
   *
   * @param e The MouseEvent.
   */
  private void handleCalendarClick(MouseEvent e) {
    if (loadedCalendar == null) {
      return; // No calendar loaded
    }

    // Get the LocalDate at the clicked point
    LocalDate clickedDate = calendarViewPanel.getDateAtPoint(e.getPoint());

    if (clickedDate != null) {
      displayEventsForDay(clickedDate);
      // When a day is clicked in the calendar, update the text pane to show only events for that day.
      // We don't want scrolling the text pane at this point to change the calendar month,
      // as the user explicitly selected a day. If the user then scrolls significantly,
      // they can use "Clear Search" to return to the full view with scrolling month updates.
      // Or, we could potentially add logic here to disable the scroll listener temporarily.
      // For now, we'll keep the scroll listener active, but the calendar month will jump
      // back to the scrolled month if the user scrolls away from the clicked day's entries.
    }
    else {
      // If click is within the calendar panel but not on a valid day (e.g., padding, empty cell)
      // optionally clear the day view and show the full calendar or a message
      displayCalendarEntries(); // Go back to full view (resets calendar view month too, based on first entry)
    }
  }


  private void loadFile() {
    JFileChooser fileChooser = new JFileChooser();
    FileNameExtensionFilter filter = new FileNameExtensionFilter("ICS Files (*.ics)", "ics");
    fileChooser.setFileFilter(filter);

    int returnValue = fileChooser.showOpenDialog(this);

    if (returnValue == JFileChooser.APPROVE_OPTION) {
      File selectedFile = fileChooser.getSelectedFile();

      // --- Start the loading animation and SwingWorker ---
      cardLayout.show(mainContentPane, LOADING_VIEW);
      loadingPanel.startAnimation();
      updateFileNameDisplay("Loading..."); // Indicate loading in file name label


      SwingWorker<net.fortuna.ical4j.model.Calendar, Void> worker = new SwingWorker<>() {
        @Override
        protected net.fortuna.ical4j.model.Calendar doInBackground() throws IOException, ParserException {
          try (InputStream fin = new FileInputStream(selectedFile)) {
            CalendarBuilder builder = new CalendarBuilder();
            return builder.build(fin);
          }
        }

        @Override
        protected void done() {
          loadingPanel.stopAnimation();
          cardLayout.show(mainContentPane, CALENDAR_SPLIT_VIEW); // Switch back to calendar view


          try {
            loadedCalendar = get(); // Get the result
            clearSearchAndReload(); // Display the loaded calendar in both views
            updateFileNameDisplay(selectedFile.getName()); // Display the loaded file name

            // After loading and displaying, trigger an initial calendar month update
            // based on the top of the JTextPane (which should be the first entry)
            updateCalendarMonthFromScroll();

          }
          catch (java.util.concurrent.ExecutionException ex) {
            Throwable cause = ex.getCause();
            JOptionPane.showMessageDialog(IcsReader.this, "Error loading or parsing file: " + cause.getMessage(), "Error",
                JOptionPane.ERROR_MESSAGE);
            cause.printStackTrace();
            loadedCalendar = null; // Ensure calendar is null on error
            updateVEventCountDisplay(0);
            calendarViewPanel.clearCalendar(); // Clear calendar view on error
            try {
              styledDocument.remove(0, styledDocument.getLength());
              styledDocument.insertString(0, "Error loading or parsing file: " + cause.getMessage(), defaultStyle);
            }
            catch (BadLocationException ble) {
              ble.printStackTrace();
            }
            updateFileNameDisplay("Error Loading"); // Indicate error in file name label


          }
          catch (InterruptedException ex) {
            JOptionPane.showMessageDialog(IcsReader.this, "File loading was interrupted.", "Error", JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
            loadedCalendar = null;
            updateVEventCountDisplay(0);
            calendarViewPanel.clearCalendar(); // Clear calendar view on interruption
            try {
              styledDocument.remove(0, styledDocument.getLength());
              styledDocument.insertString(0, "File loading was interrupted.", defaultStyle);
            }
            catch (BadLocationException ble) {
              ble.printStackTrace();
            }
            updateFileNameDisplay("Load Interrupted"); // Indicate interruption in file name label

          }
        }
      };

      worker.execute();

    }
    else {
      // File selection cancelled
      loadedCalendar = null; // Ensure calendar is null
      updateVEventCountDisplay(0);
      calendarViewPanel.clearCalendar(); // Clear calendar view
      try {
        styledDocument.remove(0, styledDocument.getLength());
        styledDocument.insertString(0, "File selection cancelled.", defaultStyle);
      }
      catch (BadLocationException ble) {
        ble.printStackTrace();
      }
      updateFileNameDisplay(null); // Reset file name display on cancellation
    }
  }

  private void displayCalendarEntries() {
    if (loadedCalendar == null) {
      try {
        styledDocument.remove(0, styledDocument.getLength());
        styledDocument.insertString(0, "No calendar file loaded or file selection cancelled.", defaultStyle);
        updateVEventCountDisplay(0);
        calendarViewPanel.clearCalendar(); // Ensure calendar view is also cleared
        updateFileNameDisplay(null); // Clear file name display
      }
      catch (BadLocationException e) {
        e.printStackTrace();
      }
      entryDates.clear(); // Clear cached entry dates
      return;
    }

    try {
      styledDocument.remove(0, styledDocument.getLength());
      entryDates.clear(); // Clear previous entry dates
      StringBuilder sb = new StringBuilder();
      int vEventCount = 0;

      for (CalendarComponent component : loadedCalendar.getComponents()) {
        // Store date only for VEVENTs to align with what we'll look for on scroll
        if (component instanceof VEvent event) {
          vEventCount++;
          DtStart<Temporal> dtStart = event.getDateTimeStart();
          LocalDate eventDate = null;

          if (dtStart != null && dtStart.getDate() != null) {
            Temporal startTemporal = dtStart.getDate();
            if (startTemporal instanceof LocalDate) {
              eventDate = (LocalDate) startTemporal;
            }
            else if (startTemporal instanceof ZonedDateTime) {
              eventDate = ((ZonedDateTime) startTemporal).toLocalDate();
            }
          }
          // Add the date to the list. Null if no valid start date was found.
          entryDates.add(eventDate);

        }


        sb.append("--- ").append(component.getName()).append(" ---\n");
        styledDocument.insertString(styledDocument.getLength(), sb.toString(), defaultStyle);
        sb.setLength(0);

        List<Property> properties = component.getProperties();
        for (Property property : properties) {
          String entry = property.getName() + ": " + property.getValue();
          sb.append(entry).append("\n");
          styledDocument.insertString(styledDocument.getLength(), sb.toString(), defaultStyle);
          sb.setLength(0);
        }
        sb.append("\n");
        styledDocument.insertString(styledDocument.getLength(), sb.toString(), defaultStyle);
        sb.setLength(0);
      }
      calendarTextPane.setCaretPosition(0);
      updateVEventCountDisplay(vEventCount);
      // Pass the loaded calendar to the calendar view panel
      calendarViewPanel.setCalendar(loadedCalendar); // This sets the initial month based on events

      // File name is updated in the loadFile success path
    }
    catch (BadLocationException e) {
      e.printStackTrace();
    }
  }

  /**
   * Displays the events that occur on a specific selected date.
   *
   * @param selectedDate The date for which to display events.
   */
  private void displayEventsForDay(LocalDate selectedDate) {
    if (loadedCalendar == null || selectedDate == null) {
      try {
        styledDocument.remove(0, styledDocument.getLength());
        styledDocument.insertString(0, "No calendar loaded or invalid date selected.", defaultStyle);
        updateVEventCountDisplay(0);
      }
      catch (BadLocationException e) {
        e.printStackTrace();
      }
      entryDates.clear(); // Clear cached entry dates for day view
      return;
    }

    // --- Update calendar view to the selected month ---
    calendarViewPanel.setDisplayMonth(selectedDate);

    try {
      styledDocument.remove(0, styledDocument.getLength());
      entryDates.clear(); // Clear previous entry dates for day view
      StringBuilder sb = new StringBuilder();
      List<VEvent> eventsForDay = new ArrayList<>();

      sb.append("--- Events on ").append(selectedDate).append(" ---\n\n");
      styledDocument.insertString(styledDocument.getLength(), sb.toString(), defaultStyle);
      sb.setLength(0);


      for (CalendarComponent component : loadedCalendar.getComponents()) {
        if (component instanceof VEvent event) {
          DtStart<Temporal> dtStart = event.getDateTimeStart();
          DtEnd<Temporal> dtEnd = event.getDateTimeEnd();

          LocalDate startDate = null;
          LocalDate endDate = null;

          if (dtStart != null && dtStart.getDate() != null) {
            Temporal startTemporal = dtStart.getDate();
            if (startTemporal instanceof LocalDate) {
              startDate = (LocalDate) startTemporal;
            }
            else if (startTemporal instanceof ZonedDateTime) {
              startDate = ((ZonedDateTime) startTemporal).toLocalDate();
            }
          }

          if (dtEnd != null && dtEnd.getDate() != null) {
            Temporal endTemporal = dtEnd.getDate();
            if (endTemporal instanceof LocalDate) {
              endDate = (LocalDate) endTemporal;
            }
            else if (endTemporal instanceof ZonedDateTime) {
              endDate = ((ZonedDateTime) endTemporal).toLocalDate();
            }
          }

          // Check if the event occurs on the selectedDate
          if (startDate != null) {
            if (endDate == null) {
              // Single day event
              if (startDate.equals(selectedDate)) {
                eventsForDay.add(event);
                entryDates.add(selectedDate); // Add date for this displayed event
              }
            }
            else {
              // Multi-day or timed event
              // For date-only events, DTEND is the day AFTER the event ends.
              // For date-time events, DTEND is the inclusive end time.
              // A simple way for day view is to check if the selectedDate is within the [startDate,
              // effectiveEndDate] range.
              LocalDate effectiveEndDate = endDate;
              if (dtStart.getDate() instanceof LocalDate && dtEnd.getDate() instanceof LocalDate && endDate.isAfter(startDate)) {
                // Adjust end date for date-only multi-day events
                effectiveEndDate = endDate.minusDays(1);
              }

              // Check if selectedDate is on or after startDate AND on or before effectiveEndDate
              if (!selectedDate.isBefore(startDate) && !selectedDate.isAfter(effectiveEndDate)) {
                eventsForDay.add(event);
                entryDates.add(selectedDate); // Add date for this displayed event
              }
            }
          }
        }
        // For non-VEVENT components in day view, we don't add a date as they aren't tied to a specific day.
        // This means the entryDates list will only contain dates for the VEVENTs displayed.
      }

      if (eventsForDay.isEmpty()) {
        sb.append("No events found on ").append(selectedDate);
        styledDocument.insertString(styledDocument.getLength(), sb.toString(), defaultStyle);
        entryDates.clear(); // Ensure empty list if no events
      }
      else {
        sb.setLength(0); // Clear the buffer
        for (VEvent event : eventsForDay) {
          sb.append("--- VEVENT ---\n");
          styledDocument.insertString(styledDocument.getLength(), sb.toString(), defaultStyle);
          sb.setLength(0);

          List<Property> properties = event.getProperties();
          for (Property property : properties) {
            String propertyName = property.getName();
            String propertyValue = property.getValue();

            if (propertyName != null) {
              styledDocument.insertString(styledDocument.getLength(), propertyName + ": ", defaultStyle);
            }
            else {
              styledDocument.insertString(styledDocument.getLength(), ": ", defaultStyle);
            }

            if (propertyValue != null) {
              // No highlighting for day view, just insert
              styledDocument.insertString(styledDocument.getLength(), propertyValue, defaultStyle);
            }
            styledDocument.insertString(styledDocument.getLength(), "\n", defaultStyle);
          }
          styledDocument.insertString(styledDocument.getLength(), "\n", defaultStyle);
        }
      }

      calendarTextPane.setCaretPosition(0);
      updateVEventCountDisplay(eventsForDay.size());

    }
    catch (BadLocationException e) {
      e.printStackTrace();
    }
  }


  private void performSearch() {
    if (loadedCalendar == null) {
      try {
        styledDocument.remove(0, styledDocument.getLength());
        styledDocument.insertString(0, "No calendar file loaded.", defaultStyle);
      }
      catch (BadLocationException e) {
        e.printStackTrace();
      }
      updateVEventCountDisplay(0);
      entryDates.clear(); // Clear cached entry dates for search view
      // Calendar view panel remains unchanged during text search unless a match is found and its month is
      // used.
      return;
    }

    String searchTerm = searchField.getText().toLowerCase();

    try {
      styledDocument.remove(0, styledDocument.getLength());
      entryDates.clear(); // Clear previous entry dates for search view


      if (searchTerm.trim().isEmpty()) {
        // If search field is empty, display all entries
        clearSearchAndReload(); // This will reload everything including the calendar view to full display
        return;
      }

      StringBuilder sb = new StringBuilder();
      List<VEvent> matchingEvents = new ArrayList<>();

      // Collect matching events and their dates
      for (CalendarComponent component : loadedCalendar.getComponents()) {
        if (component instanceof VEvent event) {
          boolean eventMatches = false;
          for (Property property : event.getProperties()) {
            if (property.getValue() != null && property.getValue().toLowerCase().contains(searchTerm)) {
              eventMatches = true;
              break;
            }
          }
          if (eventMatches) {
            matchingEvents.add(event);

            DtStart<Temporal> dtStart = event.getDateTimeStart();
            LocalDate eventDate = null;
            if (dtStart != null && dtStart.getDate() != null) {
              Temporal startTemporal = dtStart.getDate();
              if (startTemporal instanceof LocalDate) {
                eventDate = (LocalDate) startTemporal;
              }
              else if (startTemporal instanceof ZonedDateTime) {
                eventDate = ((ZonedDateTime) startTemporal).toLocalDate();
              }
            }
            entryDates.add(eventDate); // Store the date for this matching event
          }
        }
        // For non-VEVENT components, we don't add a date to the entryDates list
        // to keep it aligned with the displayed VEVENTs in the search results.
      }

      if (matchingEvents.isEmpty()) {
        sb.append("No VEVENT entries found matching '").append(searchTerm).append("'");
        styledDocument.insertString(styledDocument.getLength(), sb.toString(), defaultStyle);
        updateVEventCountDisplay(0);
        entryDates.clear(); // Ensure empty list if no results
        // Optionally reset calendar view to current month if no search results
        calendarViewPanel.setDisplayMonth(LocalDate.now());
      }
      else {
        // Display matching events in the text pane with highlighting
        sb.append("Found ").append(matchingEvents.size()).append(" matching VEVENT(s) for '").append(searchTerm).append("':\n\n");
        styledDocument.insertString(styledDocument.getLength(), sb.toString(), defaultStyle);
        sb.setLength(0);

        for (VEvent event : matchingEvents) {
          sb.append("--- VEVENT ---\n");
          styledDocument.insertString(styledDocument.getLength(), sb.toString(), defaultStyle);
          sb.setLength(0);

          List<Property> properties = event.getProperties();
          for (Property property : properties) {
            String propertyName = property.getName();
            String propertyValue = property.getValue();

            if (propertyName != null) {
              styledDocument.insertString(styledDocument.getLength(), propertyName + ": ", defaultStyle);
            }
            else {
              styledDocument.insertString(styledDocument.getLength(), ": ", defaultStyle);
            }


            if (propertyValue != null) {
              String lowerCaseValue = propertyValue.toLowerCase();
              int lastIndex = 0;
              int searchTermLength = searchTerm.length();

              int index = lowerCaseValue.indexOf(searchTerm, lastIndex);
              while (index != -1) {
                styledDocument.insertString(styledDocument.getLength(), propertyValue.substring(lastIndex, index), defaultStyle);
                styledDocument.insertString(styledDocument.getLength(), propertyValue.substring(index, index + searchTermLength),
                    highlightStyle);

                lastIndex = index + searchTermLength;
                index = lowerCaseValue.indexOf(searchTerm, lastIndex);
              }
              if (lastIndex < propertyValue.length()) {
                styledDocument.insertString(styledDocument.getLength(), propertyValue.substring(lastIndex), defaultStyle);
              }
            }

            styledDocument.insertString(styledDocument.getLength(), "\n", defaultStyle);
          }
          styledDocument.insertString(styledDocument.getLength(), "\n", defaultStyle);
        }

        // --- Update Calendar View to Month of First Matching Event ---
        // This logic is already present and synchronizes the calendar view with the first result's month.
        if (!matchingEvents.isEmpty()) {
          VEvent firstMatchingEvent = matchingEvents.get(0);
          DtStart<Temporal> firstEventDtStart = firstMatchingEvent.getDateTimeStart();

          if (firstEventDtStart != null && firstEventDtStart.getDate() != null) {
            Temporal firstEventTemporal = firstEventDtStart.getDate();
            LocalDate firstEventDate = null;

            if (firstEventTemporal instanceof LocalDate) {
              firstEventDate = (LocalDate) firstEventTemporal;
            }
            else if (firstEventTemporal instanceof ZonedDateTime) {
              firstEventDate = ((ZonedDateTime) firstEventTemporal).toLocalDate();
            }

            if (firstEventDate != null) {
              calendarViewPanel.setDisplayMonth(firstEventDate);
            }
          }
        }


        calendarTextPane.setCaretPosition(0);
        updateVEventCountDisplay(matchingEvents.size());
      }
    }
    catch (BadLocationException e) {
      e.printStackTrace();
    }
  }

  /**
   * Clears the search field and reloads the full calendar display in both panels.
   */
  private void clearSearchAndReload() {
    searchField.setText("");
    displayCalendarEntries(); // This method now updates both text pane and calendar view (and its initial month)
    // File name is updated in the loadFile success path or cleared on error/cancel
    // After clearing and reloading, update calendar month based on the top of the text pane
    updateCalendarMonthFromScroll();
  }

  /**
   * Updates the label displaying the count of VEvents.
   *
   * @param count The number of VEvents to display.
   */
  private void updateVEventCountDisplay(int count) {
    vEventCountLabel.setText("Calendar Entries: " + count);
  }

  /**
   * Updates the label displaying the loaded file name, truncated by the first underscore.
   *
   * @param fullFileName The full name of the loaded file, or null/empty to show default.
   */
  private void updateFileNameDisplay(String fullFileName) {
    String displayFileName = "File: None Loaded";
    if (fullFileName != null && !fullFileName.trim().isEmpty()) {
      String[] parts = fullFileName.split("_", 2); // Split by the first underscore
      if (parts.length > 0) {
        // Use the part before the first underscore, or the whole name if no underscore
        displayFileName = "File: " + parts[0];
      }
      else {
        // Should not happen with split, but as a fallback
        displayFileName = "File: " + fullFileName;
      }
    }
    fileNameLabel.setText(displayFileName);
  }

  /**
   * Determines the date of the first visible calendar entry in the JTextPane and updates the
   * CalendarViewPanel to display that month.
   */
  private void updateCalendarMonthFromScroll() {
    if (loadedCalendar == null || entryDates.isEmpty()) {
      return; // No calendar loaded or no entries with dates
    }

    SwingUtilities.invokeLater(() -> {
      // Declare firstVisibleDate outside the try block
      LocalDate firstVisibleDate = null;

      try {
        Point viewPosition = scrollPane.getViewport().getViewPosition();
        int modelPosition = calendarTextPane.viewToModel2D(viewPosition);

        Document doc = calendarTextPane.getDocument();
        Element root = doc.getDefaultRootElement();
        int elementIndex = root.getElementIndex(modelPosition);

        // Iterate backward from this element to find the nearest VEVENT start
        int nearestVEVENTElementIndex = -1;
        for (int i = elementIndex; i >= 0; i--) {
          Element elem = root.getElement(i);
          String lineText = doc.getText(elem.getStartOffset(), elem.getEndOffset() - elem.getStartOffset()).trim();
          if (lineText.startsWith("--- VEVENT ---")) {
            nearestVEVENTElementIndex = i;
            break;
          }
        }

        if (nearestVEVENTElementIndex != -1) {
          // Now, count how many VEVENT markers are from the start of the document
          // up to this `nearestVEVENTElementIndex`. This count will be used as
          // an approximate index into the `entryDates` list.
          int vEventEntryIndex = 0;
          for (int i = 0; i <= nearestVEVENTElementIndex; i++) {
            Element elem = root.getElement(i);
            String lineText = doc.getText(elem.getStartOffset(), elem.getEndOffset() - elem.getStartOffset()).trim();
            if (lineText.startsWith("--- VEVENT ---")) {
              vEventEntryIndex++; // This counts the VEVENT markers
            }
          }

          // The vEventEntryIndex calculated above is 1-based based on the marker count.
          // The `entryDates` list is 0-based and contains dates for the VEVENTs displayed
          // in the text pane in their display order.

          if (vEventEntryIndex > 0 && vEventEntryIndex - 1 < entryDates.size()) {
            firstVisibleDate = entryDates.get(vEventEntryIndex - 1);
          }
        }
      }
      catch (BadLocationException ble) {
        ble.printStackTrace();
        // firstVisibleDate remains null in case of error
      }

      // This check is now valid because firstVisibleDate is declared outside the try block
      if (firstVisibleDate != null) {
        // Update the calendar view to the month of the first visible event
        calendarViewPanel.setDisplayMonth(firstVisibleDate);
      }
      // If firstVisibleDate is null, it means no VEVENT marker was found near the top,
      // or the mapping failed. The calendar view will retain its current month.
    });
  }


  public static void main(String[] args) {
    SwingUtilities.invokeLater(() -> new IcsReader());
  }
}
