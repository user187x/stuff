package xxx.com.calendar.advanced;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;
import java.util.UUID; // Added for populateEventFromForm potentially new UID
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class CalendarEventCreatorGUI extends JFrame implements DateClickedListener {
  private static final long serialVersionUID = 1L;

  private final JTextField txtTitle;
  private final JSpinner spnStartDay;
  private final JSpinner spnStartMonth;
  private final JSpinner spnStartYear;
  private final JSpinner spnEndDay;
  private final JSpinner spnEndMonth;
  private final JSpinner spnEndYear;
  private final JSpinner spnStartHour;
  private final JSpinner spnStartMinute;
  private final JSpinner spnEndHour;
  private final JSpinner spnEndMinute;
  private JCheckBox chkAllDay;
  private final JTextField txtLocation;
  private final JTextArea txaDescription;
  private final JComboBox<String> cmbCategory;
  private final JButton btnPickColor;
  private final JPanel pnlColorPreview;
  private Color selectedEventColor;
  private static final Color DEFAULT_EVENT_COLOR = Color.LIGHT_GRAY;

  private final JTextField txtNotificationDetails;
  private final JComboBox<String> cmbStartTimeZone;
  private final JCheckBox chkSeparateEndTimeZone;
  private final JComboBox<String> cmbEndTimeZone;
  private final JLabel lblEndTimeZoneLabel;
  private final JButton btnSave;

  private final DefaultListModel<GoogleCalendarEvent> eventListModel;
  private final List<GoogleCalendarEvent> allManagedEvents;
  private JList<GoogleCalendarEvent> eventDisplayList;
  private GoogleCalendarEvent currentlyEditingEvent = null;
  private LocalDate currentCalendarFilterDate;

  private final JPopupMenu listPopupMenu;
  private final JMenuItem editMenuItem;
  private final JMenuItem deleteMenuItem;
  private IcsReaderPanel icsReaderPanel;

  public static String colorToHex(Color color) {
    if (color == null)
      return null;
    return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
  }

  public static Color hexToColor(String hex) {
    if (hex == null || !hex.matches("#[0-9a-fA-F]{6}")) {
      return DEFAULT_EVENT_COLOR;
    }
    try {
      return Color.decode(hex);
    }
    catch (NumberFormatException e) {
      return DEFAULT_EVENT_COLOR;
    }
  }

  public CalendarEventCreatorGUI() {
    setTitle("Comprehensive Calendar Application");
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    setSize(1280, 800);
    setLocationRelativeTo(null);
    selectedEventColor = DEFAULT_EVENT_COLOR;
    allManagedEvents = new ArrayList<>();

    JPanel mainFormPanel = new JPanel(new GridBagLayout());
    mainFormPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.insets = new Insets(6, 6, 6, 6);
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.anchor = GridBagConstraints.WEST;

    int yPos = 0;
    gbc.gridx = 0;
    gbc.gridy = yPos++;
    gbc.gridwidth = 2;
    JLabel formTitleLabel = new JLabel("Create / Edit Event");
    formTitleLabel.setFont(formTitleLabel.getFont().deriveFont(Font.BOLD, 16f));
    mainFormPanel.add(formTitleLabel, gbc);
    gbc.gridy = yPos++;
    mainFormPanel.add(new JSeparator(), gbc);
    gbc.gridwidth = 1;

    gbc.gridx = 0;
    gbc.gridy = yPos;
    mainFormPanel.add(new JLabel("Title:"), gbc);
    gbc.gridx = 1;
    gbc.gridy = yPos++;
    txtTitle = new JTextField(25);
    mainFormPanel.add(txtTitle, gbc);

    gbc.gridx = 0;
    gbc.gridy = yPos;
    mainFormPanel.add(new JLabel("Start Date (D/M/Y):"), gbc);
    JPanel startDatePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
    spnStartDay = new JSpinner(new SpinnerNumberModel(LocalDate.now().getDayOfMonth(), 1, 31, 1));
    spnStartMonth = new JSpinner(new SpinnerNumberModel(LocalDate.now().getMonthValue(), 1, 12, 1));
    spnStartYear = new JSpinner(new SpinnerNumberModel(LocalDate.now().getYear(), 2000, 2100, 1));
    startDatePanel.add(spnStartDay);
    startDatePanel.add(new JLabel("/"));
    startDatePanel.add(spnStartMonth);
    startDatePanel.add(new JLabel("/"));
    startDatePanel.add(spnStartYear);
    gbc.gridx = 1;
    gbc.gridy = yPos++;
    mainFormPanel.add(startDatePanel, gbc);

    gbc.gridx = 0;
    gbc.gridy = yPos;
    mainFormPanel.add(new JLabel("Start Time (HH:MM):"), gbc);
    JPanel startTimePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
    spnStartHour = new JSpinner(new SpinnerNumberModel(LocalTime.now().getHour(), 0, 23, 1));
    spnStartMinute = new JSpinner(new SpinnerNumberModel(LocalTime.now().getMinute(), 0, 59, 1));
    startTimePanel.add(spnStartHour);
    startTimePanel.add(new JLabel(":"));
    startTimePanel.add(spnStartMinute);
    gbc.gridx = 1;
    gbc.gridy = yPos++;
    mainFormPanel.add(startTimePanel, gbc);

    gbc.gridx = 0;
    gbc.gridy = yPos;
    mainFormPanel.add(new JLabel("Start Time Zone:"), gbc);
    gbc.gridx = 1;
    gbc.gridy = yPos++;
    String[] availableZoneIds = TimeZone.getAvailableIDs();
    ArrayList<String> sortedZoneIds = new ArrayList<>(Arrays.asList(availableZoneIds));
    Collections.sort(sortedZoneIds);
    cmbStartTimeZone = new JComboBox<>(sortedZoneIds.toArray(new String[0]));
    cmbStartTimeZone.setSelectedItem(ZoneId.systemDefault().getId());
    mainFormPanel.add(cmbStartTimeZone, gbc);

    gbc.gridx = 0;
    gbc.gridy = yPos;
    mainFormPanel.add(new JLabel("End Date (D/M/Y):"), gbc);
    JPanel endDatePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
    spnEndDay = new JSpinner(new SpinnerNumberModel(LocalDate.now().getDayOfMonth(), 1, 31, 1));
    spnEndMonth = new JSpinner(new SpinnerNumberModel(LocalDate.now().getMonthValue(), 1, 12, 1));
    spnEndYear = new JSpinner(new SpinnerNumberModel(LocalDate.now().getYear(), 2000, 2100, 1));
    endDatePanel.add(spnEndDay);
    endDatePanel.add(new JLabel("/"));
    endDatePanel.add(spnEndMonth);
    endDatePanel.add(new JLabel("/"));
    endDatePanel.add(spnEndYear);
    gbc.gridx = 1;
    gbc.gridy = yPos++;
    mainFormPanel.add(endDatePanel, gbc);

    gbc.gridx = 0;
    gbc.gridy = yPos;
    mainFormPanel.add(new JLabel("End Time (HH:MM):"), gbc);
    JPanel endTimePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
    spnEndHour = new JSpinner(new SpinnerNumberModel(LocalTime.now().plusHours(1).getHour(), 0, 23, 1));
    spnEndMinute = new JSpinner(new SpinnerNumberModel(LocalTime.now().getMinute(), 0, 59, 1));
    endTimePanel.add(spnEndHour);
    endTimePanel.add(new JLabel(":"));
    endTimePanel.add(spnEndMinute);
    gbc.gridx = 1;
    gbc.gridy = yPos++;
    mainFormPanel.add(endTimePanel, gbc);

    gbc.gridx = 0;
    gbc.gridy = yPos;
    gbc.gridwidth = 2;
    chkSeparateEndTimeZone = new JCheckBox("Use separate End Time Zone");
    mainFormPanel.add(chkSeparateEndTimeZone, gbc);
    yPos++;
    gbc.gridwidth = 1;

    lblEndTimeZoneLabel = new JLabel("End Time Zone:");
    gbc.gridx = 0;
    gbc.gridy = yPos;
    mainFormPanel.add(lblEndTimeZoneLabel, gbc);
    cmbEndTimeZone = new JComboBox<>(sortedZoneIds.toArray(new String[0]));
    cmbEndTimeZone.setSelectedItem(ZoneId.systemDefault().getId());
    gbc.gridx = 1;
    gbc.gridy = yPos++;
    mainFormPanel.add(cmbEndTimeZone, gbc);

    chkSeparateEndTimeZone.addItemListener(e -> {
      boolean selected = e.getStateChange() == ItemEvent.SELECTED;
      lblEndTimeZoneLabel.setEnabled(selected && !chkAllDay.isSelected());
      cmbEndTimeZone.setEnabled(selected && !chkAllDay.isSelected());
      if (!selected) {
        cmbEndTimeZone.setSelectedItem(cmbStartTimeZone.getSelectedItem());
      }
    });
    lblEndTimeZoneLabel.setEnabled(false);
    cmbEndTimeZone.setEnabled(false);

    gbc.gridx = 0;
    gbc.gridy = yPos++;
    gbc.gridwidth = 2;
    chkAllDay = new JCheckBox("All-day event");
    chkAllDay.addItemListener(e -> {
      boolean isAllDay = e.getStateChange() == ItemEvent.SELECTED;
      spnStartHour.setEnabled(!isAllDay);
      spnStartMinute.setEnabled(!isAllDay);
      spnEndHour.setEnabled(!isAllDay);
      spnEndMinute.setEnabled(!isAllDay);
      chkSeparateEndTimeZone.setEnabled(!isAllDay);
      if (isAllDay)
        chkSeparateEndTimeZone.setSelected(false);
      boolean canEnableEndTimeZone = chkSeparateEndTimeZone.isSelected() && !isAllDay;
      lblEndTimeZoneLabel.setEnabled(canEnableEndTimeZone);
      cmbEndTimeZone.setEnabled(canEnableEndTimeZone);
      if (isAllDay) {
        spnStartHour.setValue(0);
        spnStartMinute.setValue(0);
        spnEndHour.setValue(0);
        spnEndMinute.setValue(0);
      }
    });
    mainFormPanel.add(chkAllDay, gbc);
    gbc.gridwidth = 1;

    gbc.gridx = 0;
    gbc.gridy = yPos;
    mainFormPanel.add(new JLabel("Location:"), gbc);
    gbc.gridx = 1;
    gbc.gridy = yPos++;
    txtLocation = new JTextField(25);
    mainFormPanel.add(txtLocation, gbc);

    gbc.gridx = 0;
    gbc.gridy = yPos;
    gbc.anchor = GridBagConstraints.NORTHWEST;
    mainFormPanel.add(new JLabel("Description:"), gbc);
    gbc.gridx = 1;
    gbc.gridy = yPos++;
    gbc.fill = GridBagConstraints.BOTH;
    gbc.weightx = 1.0;
    gbc.weighty = 1.0;
    txaDescription = new JTextArea(5, 25);
    txaDescription.setLineWrap(true);
    txaDescription.setWrapStyleWord(true);
    JScrollPane descriptionScrollPane = new JScrollPane(txaDescription);
    mainFormPanel.add(descriptionScrollPane, gbc);
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.weightx = 0;
    gbc.weighty = 0;
    gbc.anchor = GridBagConstraints.WEST;

    gbc.gridx = 0;
    gbc.gridy = yPos;
    mainFormPanel.add(new JLabel("Category:"), gbc);
    gbc.gridx = 1;
    gbc.gridy = yPos++;
    cmbCategory = new JComboBox<>(new String[] {"Work", "Personal", "Appointments", "Bills", "Travel", "Default"});
    mainFormPanel.add(cmbCategory, gbc);

    gbc.gridx = 0;
    gbc.gridy = yPos;
    mainFormPanel.add(new JLabel("Color:"), gbc);
    JPanel colorPickerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
    pnlColorPreview = new JPanel();
    pnlColorPreview.setPreferredSize(new Dimension(24, 24));
    pnlColorPreview.setBackground(selectedEventColor);
    pnlColorPreview.setBorder(BorderFactory.createLineBorder(Color.BLACK));
    colorPickerPanel.add(pnlColorPreview);
    btnPickColor = new JButton("Choose...");
    btnPickColor.addActionListener(e -> {
      Color newColor = JColorChooser.showDialog(CalendarEventCreatorGUI.this, "Choose Event Color", selectedEventColor);
      if (newColor != null) {
        selectedEventColor = newColor;
        pnlColorPreview.setBackground(selectedEventColor);
      }
    });
    colorPickerPanel.add(btnPickColor);
    gbc.gridx = 1;
    gbc.gridy = yPos++;
    mainFormPanel.add(colorPickerPanel, gbc);

    gbc.gridx = 0;
    gbc.gridy = yPos;
    mainFormPanel.add(new JLabel("Notification Text:"), gbc);
    gbc.gridx = 1;
    gbc.gridy = yPos++;
    txtNotificationDetails = new JTextField(25);
    mainFormPanel.add(txtNotificationDetails, gbc);

    gbc.gridy = yPos++;
    gbc.gridwidth = 2;
    mainFormPanel.add(new JSeparator(), gbc);
    JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    JButton btnClear = new JButton("Clear Form");
    btnClear.addActionListener(e -> {
      clearForm();
      eventDisplayList.clearSelection();
    });
    this.btnSave = new JButton("Save Event");
    this.btnSave.addActionListener(this::saveEvent);
    buttonPanel.add(btnClear);
    buttonPanel.add(this.btnSave);
    gbc.gridx = 0;
    gbc.gridy = yPos++;
    gbc.gridwidth = 2;
    gbc.anchor = GridBagConstraints.EAST;
    mainFormPanel.add(buttonPanel, gbc);

    JPanel eastPanel = new JPanel(new BorderLayout());
    eventListModel = new DefaultListModel<>();
    eventDisplayList = new JList<>(eventListModel);
    eventDisplayList.setCellRenderer(new EventListCellRenderer());
    eventDisplayList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    eventDisplayList.setFixedCellHeight(75);
    eventDisplayList.addListSelectionListener(e -> {
      if (!e.getValueIsAdjusting()) {
        GoogleCalendarEvent selectedEventFromList = eventDisplayList.getSelectedValue();
        if (selectedEventFromList != null) {
          currentlyEditingEvent = selectedEventFromList;
          loadEventIntoForm(selectedEventFromList);
          btnSave.setText("Update Event");
          if (selectedEventFromList.getDtstart() != null && icsReaderPanel != null) {
            icsReaderPanel.highlightDayInCalendar(selectedEventFromList.getDtstart().toLocalDate());
          }
        }
        else {
          clearForm();
          if (icsReaderPanel != null) {
            icsReaderPanel.clearCalendarHighlight();
          }
        }
      }
    });
    JScrollPane eventListScrollPane = new JScrollPane(eventDisplayList);
    eventListScrollPane.setBorder(BorderFactory.createTitledBorder("Managed Events"));
    eventListScrollPane.setPreferredSize(new Dimension(300, 250));
    listPopupMenu = new JPopupMenu();
    editMenuItem = new JMenuItem("Edit Event");
    deleteMenuItem = new JMenuItem("Delete Event");
    editMenuItem.addActionListener(this::editSelectedEvent);
    deleteMenuItem.addActionListener(this::deleteSelectedEvent);
    listPopupMenu.add(editMenuItem);
    listPopupMenu.add(deleteMenuItem);
    eventDisplayList.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseReleased(MouseEvent e) {
        if (SwingUtilities.isRightMouseButton(e) && e.getClickCount() == 1) {
          int index = eventDisplayList.locationToIndex(e.getPoint());
          if (index != -1 && eventDisplayList.getCellBounds(index, index).contains(e.getPoint())) {
            eventDisplayList.setSelectedIndex(index);
            listPopupMenu.show(eventDisplayList, e.getX(), e.getY());
          }
        }
      }
    });
    icsReaderPanel = new IcsReaderPanel();
    icsReaderPanel.addDateClickedListener(this);
    icsReaderPanel.setBorder(BorderFactory.createTitledBorder("ICS File Viewer"));
    JSplitPane rightVerticalSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, eventListScrollPane, icsReaderPanel);
    rightVerticalSplit.setDividerLocation(300);
    rightVerticalSplit.setResizeWeight(0.35);
    eastPanel.add(rightVerticalSplit, BorderLayout.CENTER);
    JSplitPane mainSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(mainFormPanel), eastPanel);
    mainSplitPane.setDividerLocation(600);
    mainSplitPane.setResizeWeight(0.50);
    add(mainSplitPane);

    clearForm();
    addSampleEvents();
    this.currentCalendarFilterDate = LocalDate.now();
    filterAndDisplayManagedEvents();
  }

  private boolean isAllDay(GoogleCalendarEvent event) {
    if (event == null || event.getDtstart() == null) {
      return false;
    }
    if ("P1D".equals(event.getDuration()) && event.getDtend() == null) {
      return true;
    }
    if (event.getDtend() != null) {
      ZonedDateTime dtStart = event.getDtstart();
      ZonedDateTime dtEnd = event.getDtend();
      return dtStart.toLocalTime().equals(LocalTime.MIDNIGHT) &&
          dtEnd.toLocalTime().equals(LocalTime.MIDNIGHT) &&
          dtEnd.toLocalDate().isAfter(dtStart.toLocalDate());
    }
    return false;
  }

  private boolean doesEventOccurOnDate(GoogleCalendarEvent event, LocalDate queryDate) {
    if (event == null || event.getDtstart() == null || queryDate == null) {
      return false;
    }
    ZonedDateTime dtStart = event.getDtstart();
    ZonedDateTime dtEnd = event.getDtend();
    String duration = event.getDuration();
    LocalDate eventStartDate = dtStart.toLocalDate();
    LocalDate inclusiveEventEndDate;

    if (isAllDay(event)) {
      if ("P1D".equals(duration) && dtEnd == null) {
        inclusiveEventEndDate = eventStartDate;
      }
      else if (dtEnd != null && dtStart.toLocalTime().equals(LocalTime.MIDNIGHT) && dtEnd.toLocalTime().equals(LocalTime.MIDNIGHT)
          && dtEnd.toLocalDate().isAfter(dtStart.toLocalDate())) {
        inclusiveEventEndDate = dtEnd.toLocalDate().minusDays(1);
      }
      else if (dtStart.toLocalTime().equals(LocalTime.MIDNIGHT) && dtEnd == null) {
        inclusiveEventEndDate = eventStartDate;
      }
      else {
        inclusiveEventEndDate = eventStartDate;
      }
    }
    else {
      if (dtEnd != null) {
        inclusiveEventEndDate = dtEnd.toLocalDate();
      }
      else {
        inclusiveEventEndDate = eventStartDate;
      }
    }
    if (inclusiveEventEndDate.isBefore(eventStartDate)) {
      inclusiveEventEndDate = eventStartDate;
    }
    return !queryDate.isBefore(eventStartDate) && !queryDate.isAfter(inclusiveEventEndDate);
  }

  private void filterAndDisplayManagedEvents() {
    eventListModel.clear();
    LocalDate dateToFilterBy = (currentCalendarFilterDate != null) ? currentCalendarFilterDate : LocalDate.now();
    System.out.println("[Filter] Filtering events for date: " + dateToFilterBy + ". Total managed events: " + allManagedEvents.size());
    int count = 0;
    for (GoogleCalendarEvent event : allManagedEvents) {
      if (doesEventOccurOnDate(event, dateToFilterBy)) {
        eventListModel.addElement(event);
        count++;
      }
    }
    System.out.println("[Filter] Found " + count + " events for " + dateToFilterBy);
  }

  @Override
  public void onDateClicked(LocalDate clickedDate) {
    // THIS IS THE METHOD WHERE THE CONSOLE MESSAGE IS ADDED/CONFIRMED
    if (clickedDate != null) {
      System.out.println(
          "Calendar date clicked: " + clickedDate.format(DateTimeFormatter.ISO_LOCAL_DATE) + " (from " + getClass().getSimpleName() + ")");
      this.currentCalendarFilterDate = clickedDate;
    }
    else {
      System.out.println("Calendar selection cleared or invalid. Filtering for today. (from " + getClass().getSimpleName() + ")");
      this.currentCalendarFilterDate = LocalDate.now();
    }
    filterAndDisplayManagedEvents();
    eventDisplayList.clearSelection();
  }

  private void addSampleEvents() {
    GoogleCalendarEvent event1 = new GoogleCalendarEvent();
    event1.setSummary("Team Stand-up MAY 22 (Timed)");
    event1.setDtstart(ZonedDateTime.of(2025, 5, 22, 9, 0, 0, 0, ZoneId.systemDefault()));
    event1.setDtend(ZonedDateTime.of(2025, 5, 22, 9, 15, 0, 0, ZoneId.systemDefault()));
    event1.setCategories(List.of("Work"));
    event1.setEventColorHex(colorToHex(new Color(0xD93025)));
    allManagedEvents.add(event1);

    GoogleCalendarEvent event2 = new GoogleCalendarEvent();
    event2.setSummary("Doctor's - MAY 23 (Timed)");
    event2.setDtstart(ZonedDateTime.of(2025, 5, 23, 14, 30, 0, 0, ZoneId.systemDefault()));
    event2.setDtend(ZonedDateTime.of(2025, 5, 23, 15, 30, 0, 0, ZoneId.systemDefault()));
    event2.setCategories(List.of("Appointments"));
    event2.setEventColorHex(colorToHex(new Color(0xF29900)));
    allManagedEvents.add(event2);

    GoogleCalendarEvent event3 = new GoogleCalendarEvent();
    event3.setSummary("All Day Project MAY 23 (Single All-Day)");
    event3.setDtstart(ZonedDateTime.of(LocalDate.of(2025, 5, 23), LocalTime.MIDNIGHT, ZoneId.systemDefault()));
    event3.setDtend(ZonedDateTime.of(LocalDate.of(2025, 5, 24), LocalTime.MIDNIGHT, ZoneId.systemDefault()));
    event3.setCategories(List.of("Work"));
    event3.setEventColorHex(colorToHex(DEFAULT_EVENT_COLOR));
    allManagedEvents.add(event3);

    GoogleCalendarEvent event4 = new GoogleCalendarEvent();
    event4.setSummary("Multi-day All Day MAY 22-23");
    event4.setDtstart(ZonedDateTime.of(LocalDate.of(2025, 5, 22), LocalTime.MIDNIGHT, ZoneId.systemDefault()));
    event4.setDtend(ZonedDateTime.of(LocalDate.of(2025, 5, 24), LocalTime.MIDNIGHT, ZoneId.systemDefault()));
    event4.setCategories(List.of("Travel"));
    event4.setEventColorHex(colorToHex(Color.CYAN));
    allManagedEvents.add(event4);

    GoogleCalendarEvent event5 = new GoogleCalendarEvent();
    event5.setSummary("Today's Sample Event (Timed)");
    event5.setDtstart(ZonedDateTime.of(LocalDate.now(), LocalTime.of(10, 0), ZoneId.systemDefault()));
    event5.setDtend(ZonedDateTime.of(LocalDate.now(), LocalTime.of(11, 0), ZoneId.systemDefault()));
    event5.setCategories(List.of("Personal"));
    event5.setEventColorHex(colorToHex(Color.GREEN));
    allManagedEvents.add(event5);
  }

  private void editSelectedEvent(ActionEvent e) {
    int selectedIndexInView = eventDisplayList.getSelectedIndex();
    if (selectedIndexInView != -1) {
      GoogleCalendarEvent eventToEdit = eventListModel.getElementAt(selectedIndexInView);
      String uidToEdit = eventToEdit.getUid();
      GoogleCalendarEvent masterInstanceToEdit = null;
      for (GoogleCalendarEvent masterEvent : allManagedEvents) {
        if (masterEvent.getUid().equals(uidToEdit)) {
          masterInstanceToEdit = masterEvent;
          break;
        }
      }
      if (masterInstanceToEdit != null) {
        currentlyEditingEvent = masterInstanceToEdit;
      }
      else { // Should not happen if data is consistent
        currentlyEditingEvent = eventToEdit; // Fallback
        System.err.println("Warning: Could not find master instance for event UID: " + uidToEdit + " during edit context menu.");
      }
      loadEventIntoForm(currentlyEditingEvent);
      btnSave.setText("Update Event");
    }
    else {
      JOptionPane.showMessageDialog(this, "No event selected to edit from context menu.", "Edit Error", JOptionPane.WARNING_MESSAGE);
    }
  }

  private void deleteSelectedEvent(ActionEvent e) {
    int selectedIndexInView = eventDisplayList.getSelectedIndex();
    if (selectedIndexInView != -1) {
      GoogleCalendarEvent eventInViewToDelete = eventListModel.getElementAt(selectedIndexInView);
      int response = JOptionPane.showConfirmDialog(this, "Delete '" + eventInViewToDelete.getSummary() + "'?", "Confirm Delete",
          JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

      if (response == JOptionPane.YES_OPTION) {
        String uidToDelete = eventInViewToDelete.getUid();
        boolean removed = allManagedEvents.removeIf(evt -> evt.getUid().equals(uidToDelete));
        if (removed) {
          if (currentlyEditingEvent != null && currentlyEditingEvent.getUid().equals(uidToDelete)) {
            clearForm();
          }
          filterAndDisplayManagedEvents();
        }
        if (icsReaderPanel != null)
          icsReaderPanel.clearCalendarHighlight();
      }
    }
    else {
      JOptionPane.showMessageDialog(this, "No event selected to delete.", "Delete Error", JOptionPane.WARNING_MESSAGE);
    }
  }

  private void clearForm() {
    txtTitle.setText("");
    LocalDate today = LocalDate.now();
    LocalTime nextH = LocalTime.now().plusHours(1).withMinute(0).withSecond(0).withNano(0);
    spnStartDay.setValue(today.getDayOfMonth());
    spnStartMonth.setValue(today.getMonthValue());
    spnStartYear.setValue(today.getYear());
    spnStartHour.setValue(nextH.getHour());
    spnStartMinute.setValue(nextH.getMinute());
    cmbStartTimeZone.setSelectedItem(ZoneId.systemDefault().getId());
    spnEndDay.setValue(today.getDayOfMonth());
    spnEndMonth.setValue(today.getMonthValue());
    spnEndYear.setValue(today.getYear());
    spnEndHour.setValue(nextH.plusHours(1).getHour());
    spnEndMinute.setValue(nextH.getMinute());
    chkSeparateEndTimeZone.setSelected(false);
    cmbEndTimeZone.setSelectedItem(ZoneId.systemDefault().getId());
    chkAllDay.setSelected(false);
    txtLocation.setText("");
    txaDescription.setText("");
    if (cmbCategory.getItemCount() > 0)
      cmbCategory.setSelectedIndex(0);
    selectedEventColor = DEFAULT_EVENT_COLOR;
    if (pnlColorPreview != null)
      pnlColorPreview.setBackground(selectedEventColor);
    txtNotificationDetails.setText("");
    currentlyEditingEvent = null;
    btnSave.setText("Save Event");
  }

  private void loadEventIntoForm(GoogleCalendarEvent event) {
    if (event == null) {
      clearForm();
      return;
    }
    txtTitle.setText(event.getSummary() != null ? event.getSummary() : "");
    ZonedDateTime dtStart = event.getDtstart();
    ZonedDateTime dtEnd = event.getDtend();
    boolean isAllDayEv = isAllDay(event);
    chkAllDay.setSelected(isAllDayEv);

    if (dtStart != null) {
      spnStartYear.setValue(dtStart.getYear());
      spnStartMonth.setValue(dtStart.getMonthValue());
      spnStartDay.setValue(dtStart.getDayOfMonth());
      cmbStartTimeZone.setSelectedItem(dtStart.getZone().getId());
      if (!isAllDayEv) {
        spnStartHour.setValue(dtStart.getHour());
        spnStartMinute.setValue(dtStart.getMinute());
        if (dtEnd != null) {
          spnEndYear.setValue(dtEnd.getYear());
          spnEndMonth.setValue(dtEnd.getMonthValue());
          spnEndDay.setValue(dtEnd.getDayOfMonth());
          spnEndHour.setValue(dtEnd.getHour());
          spnEndMinute.setValue(dtEnd.getMinute());
          if (!dtStart.getZone().equals(dtEnd.getZone())) {
            chkSeparateEndTimeZone.setSelected(true);
            cmbEndTimeZone.setSelectedItem(dtEnd.getZone().getId());
          }
          else {
            chkSeparateEndTimeZone.setSelected(false);
            cmbEndTimeZone.setSelectedItem(dtStart.getZone().getId());
          }
        }
        else {
          LocalDateTime ldtEndDef = dtStart.toLocalDateTime().plusHours(1);
          spnEndYear.setValue(ldtEndDef.getYear());
          spnEndMonth.setValue(ldtEndDef.getMonthValue());
          spnEndDay.setValue(ldtEndDef.getDayOfMonth());
          spnEndHour.setValue(ldtEndDef.getHour());
          spnEndMinute.setValue(ldtEndDef.getMinute());
          chkSeparateEndTimeZone.setSelected(false);
          cmbEndTimeZone.setSelectedItem(dtStart.getZone().getId());
        }
      }
      else {
        spnStartHour.setValue(0);
        spnStartMinute.setValue(0);
        LocalDate endDateToSet = dtStart.toLocalDate();
        if (isAllDayEv && dtEnd != null && dtEnd.toLocalDate().isAfter(dtStart.toLocalDate())) {
          endDateToSet = dtEnd.toLocalDate().minusDays(1);
        }
        spnEndYear.setValue(endDateToSet.getYear());
        spnEndMonth.setValue(endDateToSet.getMonthValue());
        spnEndDay.setValue(endDateToSet.getDayOfMonth());
        spnEndHour.setValue(0);
        spnEndMinute.setValue(0);
        chkSeparateEndTimeZone.setSelected(false);
        cmbEndTimeZone.setSelectedItem(dtStart.getZone().getId());
      }
    }
    else {
      clearForm();
      return;
    }

    boolean canEnableEndTimeZoneFields = !chkAllDay.isSelected() && chkSeparateEndTimeZone.isSelected();
    lblEndTimeZoneLabel.setEnabled(canEnableEndTimeZoneFields);
    cmbEndTimeZone.setEnabled(canEnableEndTimeZoneFields);
    chkSeparateEndTimeZone.setEnabled(!chkAllDay.isSelected());

    txtLocation.setText(event.getLocation() != null ? event.getLocation() : "");
    txaDescription.setText(event.getDescription() != null ? event.getDescription() : "");
    if (event.getCategories() != null && !event.getCategories().isEmpty())
      cmbCategory.setSelectedItem(event.getCategories().get(0));
    else if (cmbCategory.getItemCount() > 0)
      cmbCategory.setSelectedIndex(0);
    selectedEventColor = hexToColor(event.getEventColorHex());
    if (pnlColorPreview != null)
      pnlColorPreview.setBackground(selectedEventColor);
    txtNotificationDetails.setText(event.getNotificationDetails() != null ? event.getNotificationDetails() : "");
  }

  private void populateEventFromForm(GoogleCalendarEvent eventToPopulate) {
    eventToPopulate.setSummary(txtTitle.getText().trim());
    int sD = (int) spnStartDay.getValue();
    int sM = (int) spnStartMonth.getValue();
    int sY = (int) spnStartYear.getValue();
    int sH = chkAllDay.isSelected() ? 0 : (int) spnStartHour.getValue();
    int sMin = chkAllDay.isSelected() ? 0 : (int) spnStartMinute.getValue();
    int eD = (int) spnEndDay.getValue();
    int eM = (int) spnEndMonth.getValue();
    int eY = (int) spnEndYear.getValue();
    int eH = chkAllDay.isSelected() ? 0 : (int) spnEndHour.getValue();
    int eMin = chkAllDay.isSelected() ? 0 : (int) spnEndMinute.getValue();
    ZoneId selStartZone = ZoneId.of((String) cmbStartTimeZone.getSelectedItem());
    eventToPopulate.setTimeZoneId(selStartZone.getId());
    ZoneId selEndZone = selStartZone;
    if (!chkAllDay.isSelected() && chkSeparateEndTimeZone.isSelected() && cmbEndTimeZone.isEnabled()) {
      selEndZone = ZoneId.of((String) cmbEndTimeZone.getSelectedItem());
    }

    if (chkAllDay.isSelected()) {
      LocalDate startDate = LocalDate.of(sY, sM, sD);
      eventToPopulate.setDtstart(ZonedDateTime.of(startDate, LocalTime.MIDNIGHT, selStartZone));
      LocalDate endDate = LocalDate.of(eY, eM, eD);
      if (endDate.isBefore(startDate)) {
        endDate = startDate;
        spnEndDay.setValue(sD);
        spnEndMonth.setValue(sM);
        spnEndYear.setValue(sY);
        JOptionPane.showMessageDialog(this, "End date for all-day event cannot be before start date. Adjusted.", "Date Warning",
            JOptionPane.WARNING_MESSAGE);
      }
      if (endDate.equals(startDate)) {
        eventToPopulate.setDuration("P1D");
        eventToPopulate.setDtend(null);
      }
      else {
        eventToPopulate.setDtend(ZonedDateTime.of(endDate.plusDays(1), LocalTime.MIDNIGHT, selStartZone));
        eventToPopulate.setDuration(null);
      }
    }
    else {
      LocalDateTime ldtS = LocalDateTime.of(sY, sM, sD, sH, sMin);
      eventToPopulate.setDtstart(ZonedDateTime.of(ldtS, selStartZone));
      LocalDateTime ldtE = LocalDateTime.of(eY, eM, eD, eH, eMin);
      ZonedDateTime zonedStart = eventToPopulate.getDtstart();
      ZonedDateTime zonedEndCandidate = ZonedDateTime.of(ldtE, selEndZone);
      if (zonedEndCandidate.isBefore(zonedStart)) {
        ldtE = ldtS.plusHours(1);
        zonedEndCandidate = ZonedDateTime.of(ldtE, selEndZone);
        spnEndDay.setValue(zonedEndCandidate.getDayOfMonth());
        spnEndMonth.setValue(zonedEndCandidate.getMonthValue());
        spnEndYear.setValue(zonedEndCandidate.getYear());
        spnEndHour.setValue(zonedEndCandidate.getHour());
        spnEndMinute.setValue(zonedEndCandidate.getMinute());
        if (!selStartZone.equals(selEndZone) && cmbEndTimeZone.isEnabled())
          cmbEndTimeZone.setSelectedItem(selEndZone.getId());
        JOptionPane.showMessageDialog(this, "End time cannot be before start time. Adjusted.", "Time Warning", JOptionPane.WARNING_MESSAGE);
      }
      eventToPopulate.setDtend(zonedEndCandidate);
      eventToPopulate.setDuration(null);
    }

    eventToPopulate.setAttendees(null);
    eventToPopulate.setUrl(null);
    eventToPopulate.setLocation(txtLocation.getText().trim().isEmpty() ? null : txtLocation.getText().trim());
    eventToPopulate.setDescription(txaDescription.getText().trim().isEmpty() ? null : txaDescription.getText().trim());
    String selCat = (String) cmbCategory.getSelectedItem();
    eventToPopulate.setCategories(selCat != null && !selCat.equals("Default") ? List.of(selCat) : null);
    eventToPopulate.setEventColorHex(colorToHex(selectedEventColor));
    eventToPopulate
        .setNotificationDetails(txtNotificationDetails.getText().trim().isEmpty() ? null : txtNotificationDetails.getText().trim());

    ZoneId effectiveStampZone = selStartZone;
    boolean isNewEvent = (eventToPopulate.getSequence() == null || eventToPopulate.getUid() == null
        || (currentlyEditingEvent != null && !currentlyEditingEvent.getUid().equals(eventToPopulate.getUid())));
    // If currentlyEditingEvent exists and its UID matches eventToPopulate's UID, it's an update.
    if (currentlyEditingEvent != null && currentlyEditingEvent.getUid().equals(eventToPopulate.getUid())) {
      isNewEvent = false;
    }


    if (isNewEvent) {
      eventToPopulate.setUid(UUID.randomUUID().toString());
      eventToPopulate.setDtstamp(ZonedDateTime.now(effectiveStampZone));
      eventToPopulate.setSequence(0);
    }
    else { // It's an update
      eventToPopulate.setDtstamp(ZonedDateTime.now(effectiveStampZone));
      eventToPopulate.setSequence(eventToPopulate.getSequence() == null ? 1 : eventToPopulate.getSequence() + 1);
    }
  }

  private void saveEvent(ActionEvent eventParam) {
    GoogleCalendarEvent eventToProcess;
    boolean isAnUpdate = (currentlyEditingEvent != null);
    int masterListIndex = -1;

    if (isAnUpdate) {
      eventToProcess = currentlyEditingEvent;
      masterListIndex = -1; // find index again to be safe
      for (int i = 0; i < allManagedEvents.size(); i++) {
        if (allManagedEvents.get(i).getUid().equals(eventToProcess.getUid())) {
          masterListIndex = i;
          break;
        }
      }
      if (masterListIndex == -1) {
        System.err.println("Error: currentlyEditingEvent (UID: " + eventToProcess.getUid()
            + ") not found in allManagedEvents during update! Treating as new.");
        isAnUpdate = false;
        eventToProcess = new GoogleCalendarEvent();
      }
    }
    else {
      eventToProcess = new GoogleCalendarEvent();
    }

    populateEventFromForm(eventToProcess);

    String dialogTitle;
    if (isAnUpdate) {
      // Object already updated in allManagedEvents if eventToProcess was a direct reference
      // If it was a copy, then allManagedEvents.set(masterListIndex, eventToProcess);
      // Assuming populateEventFromForm modified the instance from allManagedEvents
      dialogTitle = "Event Updated";
    }
    else {
      allManagedEvents.add(eventToProcess);
      dialogTitle = "Event Saved";
    }

    currentCalendarFilterDate = eventToProcess.getDtstart().toLocalDate();
    filterAndDisplayManagedEvents();

    int viewIndex = -1;
    for (int i = 0; i < eventListModel.getSize(); i++) {
      if (eventListModel.getElementAt(i).getUid().equals(eventToProcess.getUid())) {
        viewIndex = i;
        break;
      }
    }

    if (viewIndex != -1) {
      eventDisplayList.setSelectedIndex(viewIndex);
      eventDisplayList.ensureIndexIsVisible(viewIndex);
    }
    else {
      eventDisplayList.clearSelection();
    }

    String prettyOut = getPrettyPrintedEvent(eventToProcess);
    JTextArea resArea = new JTextArea(prettyOut, 25, 60);
    resArea.setEditable(false);
    resArea.setCaretPosition(0);
    JOptionPane.showMessageDialog(this, new JScrollPane(resArea), dialogTitle, JOptionPane.INFORMATION_MESSAGE);
  }

  private String getPrettyPrintedEvent(GoogleCalendarEvent event) {
    StringBuilder sb = new StringBuilder("Google Calendar Event:\n");
    String indent = "  ";
    appendIfNotNull(sb, indent, "UID", event.getUid());
    appendIfNotNull(sb, indent, "Timestamp", event.getDtstamp(), DateTimeFormatter.ISO_ZONED_DATE_TIME);
    appendIfNotNull(sb, indent, "Sequence", event.getSequence());
    appendIfNotNull(sb, indent, "Start DateTime", event.getDtstart(), DateTimeFormatter.ISO_ZONED_DATE_TIME);
    appendIfNotNull(sb, indent, "Summary", event.getSummary());
    appendIfNotNull(sb, indent, "End DateTime", event.getDtend(), DateTimeFormatter.ISO_ZONED_DATE_TIME);
    appendIfNotNull(sb, indent, "Duration", event.getDuration());
    appendIfNotNull(sb, indent, "Description", event.getDescription());
    appendIfNotNull(sb, indent, "Location", event.getLocation());
    appendIfNotNull(sb, indent, "Status", event.getStatus());
    appendIfNotNull(sb, indent, "Primary Timezone ID (Start)", event.getTimeZoneId());
    if (event.getCategories() != null && !event.getCategories().isEmpty())
      sb.append(indent).append("Categories: ").append(String.join(", ", event.getCategories())).append("\n");
    appendIfNotNull(sb, indent, "Event Color Hex", event.getEventColorHex());
    appendIfNotNull(sb, indent, "Created", event.getCreated(), DateTimeFormatter.ISO_ZONED_DATE_TIME);
    appendIfNotNull(sb, indent, "Last Modified", event.getLastModified(), DateTimeFormatter.ISO_ZONED_DATE_TIME);
    appendIfNotNull(sb, indent, "Organizer Email", event.getOrganizerEmail());
    appendIfNotNull(sb, indent, "Organizer CN", event.getOrganizerCN());
    appendIfNotNull(sb, indent, "Notification Details", event.getNotificationDetails());
    if (event.getAttendees() != null && !event.getAttendees().isEmpty()) {
      sb.append(indent).append("Attendees:\n");
      for (GoogleCalendarEvent.Attendee att : event.getAttendees())
        sb.append(attendeeToPrettyString(att, indent + "  "));
    }
    appendIfNotNull(sb, indent, "RRULE", event.getRrule());
    appendIfNotNull(sb, indent, "EXDATE", event.getExdate());
    appendIfNotNull(sb, indent, "RDATE", event.getRdate());
    appendIfNotNull(sb, indent, "URL", event.getUrl());
    return sb.toString();
  }

  private String attendeeToPrettyString(GoogleCalendarEvent.Attendee att, String indent) {
    if (att == null)
      return "";
    StringBuilder sb = new StringBuilder();
    String idin = indent + "  ";
    boolean hc = false;
    StringBuilder attSb = new StringBuilder();
    if (att.getEmail() != null) {
      attSb.append(idin).append("Email: ").append(att.getEmail()).append("\n");
      hc = true;
    }
    if (att.getCommonName() != null) {
      attSb.append(idin).append("CN: ").append(att.getCommonName()).append("\n");
      hc = true;
    }
    if (att.getParticipationStatus() != null) {
      attSb.append(idin).append("Status: ").append(att.getParticipationStatus()).append("\n");
      hc = true;
    }
    if (att.getRole() != null) {
      attSb.append(idin).append("Role: ").append(att.getRole()).append("\n");
      hc = true;
    }
    if (hc)
      sb.append(indent).append("Attendee:\n").append(attSb);
    return sb.toString();
  }

  private void appendIfNotNull(StringBuilder sb, String i, String l, Object v) {
    if (v != null) {
      String s = v.toString();
      if (!s.trim().isEmpty() || !(v instanceof String))
        sb.append(i).append(l).append(": ").append(s).append("\n");
    }
  }

  private void appendIfNotNull(StringBuilder sb, String i, String l, ZonedDateTime v, DateTimeFormatter f) {
    if (v != null)
      sb.append(i).append(l).append(": ").append(f.format(v)).append("\n");
  }

  public static void main(String[] args) {
    SwingUtilities.invokeLater(() -> {
      try {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
      }
      catch (Exception e) {
        e.printStackTrace();
      }
      new CalendarEventCreatorGUI().setVisible(true);
    });
  }
}
