package xxx.com.calendar.advanced;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Element;
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

public class IcsReaderPanel extends JPanel implements ActionListener {
  private static final long serialVersionUID = 1L;
  private final JTextPane calendarTextPane;
  private final JTextField searchField;
  private final JButton searchButton;
  private final JButton loadButton;
  private final JButton clearButton;
  private final JButton prevMonthButton;
  private final JButton nextMonthButton;
  private final JScrollPane scrollPane;
  private final JLabel vEventCountLabel;
  private final JLabel fileNameLabel;
  private final JPanel mainContentPane;
  private final CardLayout cardLayout;
  private final LoadingAnimationPanel loadingPanel;
  private final JSplitPane splitPaneView;
  private final CalendarViewPanel calendarViewPanel;
  private net.fortuna.ical4j.model.Calendar loadedCalendar;
  private final StyledDocument styledDocument;
  private final Style defaultStyle;
  private final Style highlightStyle;
  private final List<LocalDate> entryDates = new ArrayList<>(); // Dates corresponding to VEVENTs in JTextPane order
  private final List<DateClickedListener> dateClickedListeners = new ArrayList<>();

  private static final String FLOPPY_ICON_PATH = "/resources/saveDisk.png";
  private static final String LOADING_VIEW = "LoadingView";
  private static final String CALENDAR_SPLIT_VIEW = "CalendarSplitView";

  public IcsReaderPanel() {
    setLayout(new BorderLayout(5, 5));
    setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

    JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 3));
    ImageIcon floppyIcon = loadIcon(FLOPPY_ICON_PATH);
    loadButton = new JButton(floppyIcon != null ? "" : "Load ICS");
    if (floppyIcon != null)
      loadButton.setIcon(floppyIcon);
    loadButton.setToolTipText("Load ICS File");
    loadButton.addActionListener(this);
    topPanel.add(loadButton);
    searchField = new JTextField(15);
    searchField.addActionListener(this);
    topPanel.add(searchField);
    searchButton = new JButton("Search");
    searchButton.addActionListener(this);
    topPanel.add(searchButton);
    clearButton = new JButton("Clear");
    clearButton.setToolTipText("Clear Search and Reload");
    clearButton.addActionListener(this);
    topPanel.add(clearButton);
    topPanel.add(new JSeparator(SwingConstants.VERTICAL));
    prevMonthButton = new JButton("<");
    prevMonthButton.setToolTipText("Previous Month");
    prevMonthButton.addActionListener(this);
    topPanel.add(prevMonthButton);
    nextMonthButton = new JButton(">");
    nextMonthButton.setToolTipText("Next Month");
    nextMonthButton.addActionListener(this);
    topPanel.add(nextMonthButton);
    add(topPanel, BorderLayout.NORTH);

    cardLayout = new CardLayout();
    mainContentPane = new JPanel(cardLayout);
    loadingPanel = new LoadingAnimationPanel();
    mainContentPane.add(loadingPanel, LOADING_VIEW);
    calendarTextPane = new JTextPane();
    calendarTextPane.setEditable(false);
    styledDocument = calendarTextPane.getStyledDocument();
    defaultStyle = styledDocument.addStyle("Default", null);
    StyleConstants.setFontFamily(defaultStyle, "SansSerif");
    StyleConstants.setFontSize(defaultStyle, 12);
    highlightStyle = styledDocument.addStyle("Highlight", defaultStyle);
    StyleConstants.setBackground(highlightStyle, Color.YELLOW);
    scrollPane = new JScrollPane(calendarTextPane);
    scrollPane.getVerticalScrollBar().addAdjustmentListener(e -> {
      if (!e.getValueIsAdjusting())
        updateCalendarMonthFromScroll();
    });
    calendarViewPanel = new CalendarViewPanel();
    calendarViewPanel.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        handleCalendarClick(e);
      }
    });
    splitPaneView = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, scrollPane, calendarViewPanel);
    splitPaneView.setResizeWeight(0.7);
    splitPaneView.setDividerLocation(350);
    splitPaneView.setContinuousLayout(true);
    mainContentPane.add(splitPaneView, CALENDAR_SPLIT_VIEW);
    cardLayout.show(mainContentPane, CALENDAR_SPLIT_VIEW);
    add(mainContentPane, BorderLayout.CENTER);

    JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 3));
    vEventCountLabel = new JLabel("Entries: 0");
    bottomPanel.add(vEventCountLabel);
    fileNameLabel = new JLabel("File: None");
    bottomPanel.add(fileNameLabel);
    add(bottomPanel, BorderLayout.SOUTH);
    clearSearchAndReload();
    updateFileNameDisplay(null);
  }

  public void addDateClickedListener(DateClickedListener listener) {
    if (listener != null)
      this.dateClickedListeners.add(listener);
  }

  public void highlightDayInCalendar(LocalDate date) {
    if (date != null)
      calendarViewPanel.setHighlightedDate(date);
  }

  public void clearCalendarHighlight() {
    calendarViewPanel.clearHighlight();
  }

  private ImageIcon loadIcon(String path) {
    URL u = getClass().getResource(path);
    return (u != null) ? new ImageIcon(u) : null;
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    Object s = e.getSource();
    if (s == loadButton)
      loadFile();
    else if (s == searchButton || s == searchField)
      performSearch();
    else if (s == clearButton)
      clearSearchAndReload();
    else if (s == prevMonthButton)
      calendarViewPanel.previousMonth();
    else if (s == nextMonthButton)
      calendarViewPanel.nextMonth();
  }

  private void handleCalendarClick(MouseEvent e) {
    if (loadedCalendar == null)
      return;
    LocalDate clickedDate = calendarViewPanel.getDateAtPoint(e.getPoint());
    if (clickedDate != null) {
      for (DateClickedListener listener : dateClickedListeners)
        listener.onDateClicked(clickedDate);
      displayEventsForDay(clickedDate);
    }
    else {
      displayCalendarEntries();
      clearCalendarHighlight();
      for (DateClickedListener listener : dateClickedListeners)
        listener.onDateClicked(null);
    }
  }

  private void loadFile() {
    JFileChooser fc = new JFileChooser();
    fc.setFileFilter(new FileNameExtensionFilter("ICS Files (*.ics)", "ics"));
    if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
      File f = fc.getSelectedFile();
      cardLayout.show(mainContentPane, LOADING_VIEW);
      loadingPanel.startAnimation();
      updateFileNameDisplay("Loading: " + f.getName());
      new SwingWorker<net.fortuna.ical4j.model.Calendar, Void>() {
        @Override
        protected net.fortuna.ical4j.model.Calendar doInBackground() throws IOException, ParserException {
          try (InputStream in = new FileInputStream(f)) {
            return new CalendarBuilder().build(in);
          }
        }

        @Override
        protected void done() {
          loadingPanel.stopAnimation();
          cardLayout.show(mainContentPane, CALENDAR_SPLIT_VIEW);
          try {
            loadedCalendar = get();
            clearSearchAndReload();
            updateFileNameDisplay(f.getName());
            updateCalendarMonthFromScroll();
          }
          catch (Exception ex) {
            Throwable c = (ex instanceof java.util.concurrent.ExecutionException) ? ex.getCause() : ex;
            JOptionPane.showMessageDialog(IcsReaderPanel.this, "Error loading file: " + c.getMessage(), "Load Error",
                JOptionPane.ERROR_MESSAGE);
            c.printStackTrace();
            loadedCalendar = null;
            clearSearchAndReload();
            updateFileNameDisplay("Error");
          }
        }
      }.execute();
    }
    else {
      if (loadedCalendar == null) {
        clearSearchAndReload();
        updateFileNameDisplay(null);
      }
    }
  }

  private void displayCalendarEntries() {
    entryDates.clear();
    try {
      styledDocument.remove(0, styledDocument.getLength());
    }
    catch (BadLocationException e) { /* Ignore */ }
    if (loadedCalendar == null) {
      try {
        styledDocument.insertString(0, "No calendar loaded. Click 'Load ICS' icon.", defaultStyle);
      }
      catch (BadLocationException e) {
        e.printStackTrace();
      }
      updateVEventCountDisplay(0);
      calendarViewPanel.clearCalendar();
      return;
    }
    try {
      int vEventCount = 0;
      for (CalendarComponent comp : loadedCalendar.getComponents()) {
        styledDocument.insertString(styledDocument.getLength(), "--- " + comp.getName() + " ---\n", defaultStyle);
        if (comp instanceof VEvent ev) {
          vEventCount++;
          DtStart<Temporal> ds = ev.getDateTimeStart();
          LocalDate ed = null;
          if (ds != null && ds.getDate() != null) {
            Temporal t = ds.getDate();
            if (t instanceof LocalDate)
              ed = (LocalDate) t;
            else if (t instanceof ZonedDateTime)
              ed = ((ZonedDateTime) t).toLocalDate();
          }
          entryDates.add(ed);
        }
        else {
          entryDates.add(null);
        }
        for (Property prop : comp.getProperties())
          styledDocument.insertString(styledDocument.getLength(), prop.getName() + ": " + prop.getValue() + "\n", defaultStyle);
        styledDocument.insertString(styledDocument.getLength(), "\n", defaultStyle);
      }
      calendarTextPane.setCaretPosition(0);
      updateVEventCountDisplay(vEventCount);
      calendarViewPanel.setCalendar(loadedCalendar);
    }
    catch (BadLocationException e) {
      e.printStackTrace();
    }
  }

  private void displayEventsForDay(LocalDate selectedDate) {
    if (loadedCalendar == null || selectedDate == null) {
      displayCalendarEntries();
      if (calendarViewPanel != null)
        calendarViewPanel.clearHighlight();
      return;
    }
    calendarViewPanel.setHighlightedDate(selectedDate);
    System.out.println(selectedDate);

    try {
      styledDocument.remove(0, styledDocument.getLength());
      entryDates.clear();
      styledDocument.insertString(0, "--- Events on " + selectedDate.format(DateTimeFormatter.ISO_LOCAL_DATE) + " ---\n\n", defaultStyle);
      int eventsOnDayCount = 0;
      for (CalendarComponent comp : loadedCalendar.getComponents()) {
        if (comp instanceof VEvent ev) {
          DtStart<Temporal> dsProp = ev.getDateTimeStart();
          DtEnd<Temporal> deProp = ev.getDateTimeEnd();
          LocalDate sDate = null, eDate = null;
          if (dsProp != null && dsProp.getDate() != null) {
            Temporal t = dsProp.getDate();
            if (t instanceof LocalDate)
              sDate = (LocalDate) t;
            else if (t instanceof ZonedDateTime)
              sDate = ((ZonedDateTime) t).toLocalDate();
          }
          if (deProp != null && deProp.getDate() != null) {
            Temporal t = deProp.getDate();
            if (t instanceof LocalDate)
              eDate = (LocalDate) t;
            else if (t instanceof ZonedDateTime)
              eDate = ((ZonedDateTime) t).toLocalDate();
          }
          if (sDate != null) {
            LocalDate effectiveEndDate = (eDate == null || eDate.isBefore(sDate)) ? sDate : eDate;
            if (dsProp != null && dsProp.getDate() instanceof LocalDate && eDate != null && eDate.isAfter(sDate))
              effectiveEndDate = eDate.minusDays(1);
            if (!selectedDate.isBefore(sDate) && !selectedDate.isAfter(effectiveEndDate)) {
              eventsOnDayCount++;
              entryDates.add(selectedDate);
              styledDocument.insertString(styledDocument.getLength(), "--- VEVENT ---\n", defaultStyle);
              for (Property prop : ev.getProperties())
                styledDocument.insertString(styledDocument.getLength(), prop.getName() + ": " + prop.getValue() + "\n", defaultStyle);
              styledDocument.insertString(styledDocument.getLength(), "\n", defaultStyle);
            }
          }
        }
      }
      if (eventsOnDayCount == 0)
        styledDocument.insertString(styledDocument.getLength(), "No events found on this day.\n", defaultStyle);
      calendarTextPane.setCaretPosition(0);
      updateVEventCountDisplay(eventsOnDayCount);
    }
    catch (BadLocationException e) {
      e.printStackTrace();
    }
  }

  private void performSearch() {
    if (loadedCalendar == null) {
      try {
        styledDocument.remove(0, styledDocument.getLength());
        styledDocument.insertString(0, "No calendar loaded.", defaultStyle);
      }
      catch (BadLocationException e) {
        e.printStackTrace();
      }
      updateVEventCountDisplay(0);
      entryDates.clear();
      return;
    }
    String searchTerm = searchField.getText().toLowerCase().trim();
    try {
      styledDocument.remove(0, styledDocument.getLength());
      entryDates.clear();
      if (searchTerm.isEmpty()) {
        clearSearchAndReload();
        return;
      }
      List<VEvent> matchingEvents = new ArrayList<>();
      for (CalendarComponent comp : loadedCalendar.getComponents()) {
        if (comp instanceof VEvent ev) {
          boolean matches = false;
          for (Property prop : ev.getProperties())
            if (prop.getValue() != null && prop.getValue().toLowerCase().contains(searchTerm)) {
              matches = true;
              break;
            }
          if (matches) {
            matchingEvents.add(ev);
            DtStart<Temporal> ds = ev.getDateTimeStart();
            LocalDate ed = null;
            if (ds != null && ds.getDate() != null) {
              Temporal t = ds.getDate();
              if (t instanceof LocalDate)
                ed = (LocalDate) t;
              else if (t instanceof ZonedDateTime)
                ed = ((ZonedDateTime) t).toLocalDate();
            }
            entryDates.add(ed);
          }
        }
      }
      if (matchingEvents.isEmpty()) {
        styledDocument.insertString(0, "No VEVENTs matching '" + searchTerm + "'.", defaultStyle);
        updateVEventCountDisplay(0);
      }
      else {
        styledDocument.insertString(0, "Found " + matchingEvents.size() + " VEVENT(s) matching '" + searchTerm + "':\n\n", defaultStyle);
        for (VEvent ev : matchingEvents) {
          styledDocument.insertString(styledDocument.getLength(), "--- VEVENT ---\n", defaultStyle);
          for (Property prop : ev.getProperties()) {
            String pVal = prop.getValue();
            styledDocument.insertString(styledDocument.getLength(), prop.getName() + ": ", defaultStyle);
            if (pVal != null) {
              int lastIdx = 0;
              String lowVal = pVal.toLowerCase();
              int foundIdx = lowVal.indexOf(searchTerm, lastIdx);
              while (foundIdx != -1) {
                styledDocument.insertString(styledDocument.getLength(), pVal.substring(lastIdx, foundIdx), defaultStyle);
                styledDocument.insertString(styledDocument.getLength(), pVal.substring(foundIdx, foundIdx + searchTerm.length()),
                    highlightStyle);
                lastIdx = foundIdx + searchTerm.length();
                foundIdx = lowVal.indexOf(searchTerm, lastIdx);
              }
              styledDocument.insertString(styledDocument.getLength(), pVal.substring(lastIdx) + "\n", defaultStyle);
            }
            else {
              styledDocument.insertString(styledDocument.getLength(), "\n", defaultStyle);
            }
          }
          styledDocument.insertString(styledDocument.getLength(), "\n", defaultStyle);
        }
        if (!entryDates.isEmpty() && entryDates.get(0) != null)
          calendarViewPanel.setDisplayMonth(entryDates.get(0));
      }
      calendarTextPane.setCaretPosition(0);
      updateVEventCountDisplay(matchingEvents.size());
    }
    catch (BadLocationException e) {
      e.printStackTrace();
    }
  }

  private void clearSearchAndReload() {
    searchField.setText("");
    displayCalendarEntries();
    calendarViewPanel.clearHighlight();
    if (loadedCalendar != null) {
      LocalDate dateToSet = LocalDate.now();
      // Try to find the first valid date from the displayed entries to set the calendar month
      boolean foundDate = false;
      for (LocalDate d : entryDates)
        if (d != null) {
          dateToSet = d;
          foundDate = true;
          break;
        }
      calendarViewPanel.setDisplayMonth(dateToSet);
    }
  }

  private void updateVEventCountDisplay(int c) {
    vEventCountLabel.setText("Entries: " + c);
  }

  private void updateFileNameDisplay(String fName) {
    fileNameLabel.setText("File: " + (fName != null && !fName.isEmpty() ? new File(fName).getName() : "None"));
  }

  private void updateCalendarMonthFromScroll() {
    if (loadedCalendar == null || entryDates.isEmpty() || !scrollPane.getVerticalScrollBar().isVisible()
        || calendarTextPane.getDocument().getLength() == 0)
      return;
    SwingUtilities.invokeLater(() -> {
      LocalDate firstVisibleDate = null;
      try {
        Point vp = scrollPane.getViewport().getViewPosition();
        int mp = calendarTextPane.viewToModel2D(vp); // Use viewToModel2D for better accuracy with styled text
        Document doc = calendarTextPane.getDocument();
        Element root = doc.getDefaultRootElement();
        int elIdx = root.getElementIndex(mp);
        int vEventListIndex = -1;
        int currentVEVENTMarkerCount = 0;

        for (int i = 0; i <= elIdx; i++) {
          Element elem = root.getElement(i);
          String lt = doc.getText(elem.getStartOffset(), elem.getEndOffset() - elem.getStartOffset()).trim();
          if (lt.startsWith("--- VEVENT ---")) {
            // This is the (currentVEVENTMarkerCount)-th VEVENT in the text pane
            // We want the date associated with THIS one if it's the one at the top of the viewport
            if (i == elIdx || (mp >= elem.getStartOffset() && mp < elem.getEndOffset())) { // If this VEVENT line is at the top
              vEventListIndex = currentVEVENTMarkerCount;
            }
            currentVEVENTMarkerCount++;
          }
        }

        // If no VEVENT marker was exactly at the top, but we scrolled past some,
        // vEventListIndex might still be the last one encountered before elIdx
        if (vEventListIndex == -1 && currentVEVENTMarkerCount > 0) {
          // This could happen if the top line is not a VEVENT marker itself but is within a VEVENT's
          // properties
          // We need a more robust way to map text position to the VEVENT it belongs to.
          // For now, this logic might pick the VEVENT just before the current view if the top isn't a marker.
          // A simpler approach for now: if we are in the middle of text, just find the *first* VEVENT's date.
          for (int i = 0; i < entryDates.size(); ++i) {
            if (entryDates.get(i) != null) {
              vEventListIndex = i;
              break;
            }
          }
        }


        if (vEventListIndex != -1 && vEventListIndex < entryDates.size()) {
          firstVisibleDate = entryDates.get(vEventListIndex);
        }
        else if (!entryDates.isEmpty()) {
          for (LocalDate d : entryDates)
            if (d != null) {
              firstVisibleDate = d;
              break;
            }
        }
      }
      catch (BadLocationException ble) {
        ble.printStackTrace();
      }
      if (firstVisibleDate != null)
        calendarViewPanel.setDisplayMonth(firstVisibleDate);
    });
  }
}
