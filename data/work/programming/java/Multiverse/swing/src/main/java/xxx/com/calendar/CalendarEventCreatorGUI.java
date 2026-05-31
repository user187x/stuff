package xxx.com.calendar;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.List;
import javax.swing.*;

// Assuming CalendarEvent.java (with Attendee inner class and printPretty)
// and EventListCellRenderer.java are in the same package or correctly imported.

public class CalendarEventCreatorGUI extends JFrame {

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
  private final JCheckBox chkAllDay;
  private final JTextField txtGuests;
  private final JTextField txtMeetLink;
  private final JTextField txtLocation;
  private final JTextArea txaDescription;
  private final JComboBox<String> cmbCategory;
  private final JComboBox<String> cmbTransparency;
  private final JTextField txtNotificationDetails;
  private final JComboBox<String> cmbTimeZone;

  private final DefaultListModel<CalendarEvent> eventListModel;
  private final JList<CalendarEvent> eventDisplayList;

  public CalendarEventCreatorGUI() {
    setTitle("Calendar Application");
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    setSize(1000, 750);
    setLocationRelativeTo(null);

    JPanel mainFormPanel = new JPanel(new GridBagLayout());
    mainFormPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.insets = new Insets(5, 5, 5, 5);
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.anchor = GridBagConstraints.WEST;

    // --- Title ---
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.gridwidth = 2;
    mainFormPanel.add(new JLabel("Add title and time"), gbc);
    gbc.gridy++;
    txtTitle = new JTextField(30);
    txtTitle.setText("Sample Event Title");
    mainFormPanel.add(txtTitle, gbc);

    // --- Date and Time ---
    gbc.gridwidth = 1; // Reset gridwidth
    // Start Date
    gbc.gridy++;
    gbc.gridx = 0;
    mainFormPanel.add(new JLabel("Start Date (D/M/Y):"), gbc);
    JPanel startDatePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    spnStartDay = new JSpinner(new SpinnerNumberModel(LocalDate.now().getDayOfMonth(), 1, 31, 1));
    spnStartMonth = new JSpinner(new SpinnerNumberModel(LocalDate.now().getMonthValue(), 1, 12, 1));
    spnStartYear = new JSpinner(new SpinnerNumberModel(LocalDate.now().getYear(), 2000, 2100, 1));
    startDatePanel.add(spnStartDay);
    startDatePanel.add(new JLabel("/"));
    startDatePanel.add(spnStartMonth);
    startDatePanel.add(new JLabel("/"));
    startDatePanel.add(spnStartYear);
    gbc.gridx = 1;
    mainFormPanel.add(startDatePanel, gbc);

    // Start Time
    gbc.gridy++;
    gbc.gridx = 0;
    mainFormPanel.add(new JLabel("Start Time (HH:MM):"), gbc);
    JPanel startTimePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    spnStartHour = new JSpinner(new SpinnerNumberModel(LocalTime.now().getHour(), 0, 23, 1));
    spnStartMinute = new JSpinner(new SpinnerNumberModel(LocalTime.now().getMinute(), 0, 59, 1));
    startTimePanel.add(spnStartHour);
    startTimePanel.add(new JLabel(":"));
    startTimePanel.add(spnStartMinute);
    gbc.gridx = 1;
    mainFormPanel.add(startTimePanel, gbc);

    // End Date
    gbc.gridy++;
    gbc.gridx = 0;
    mainFormPanel.add(new JLabel("End Date (D/M/Y):"), gbc);
    JPanel endDatePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    spnEndDay = new JSpinner(new SpinnerNumberModel(LocalDate.now().getDayOfMonth(), 1, 31, 1));
    spnEndMonth = new JSpinner(new SpinnerNumberModel(LocalDate.now().getMonthValue(), 1, 12, 1));
    spnEndYear = new JSpinner(new SpinnerNumberModel(LocalDate.now().getYear(), 2000, 2100, 1));
    endDatePanel.add(spnEndDay);
    endDatePanel.add(new JLabel("/"));
    endDatePanel.add(spnEndMonth);
    endDatePanel.add(new JLabel("/"));
    endDatePanel.add(spnEndYear);
    gbc.gridx = 1;
    mainFormPanel.add(endDatePanel, gbc);

    // End Time
    gbc.gridy++;
    gbc.gridx = 0;
    mainFormPanel.add(new JLabel("End Time (HH:MM):"), gbc);
    JPanel endTimePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    spnEndHour =
        new JSpinner(new SpinnerNumberModel(LocalTime.now().plusHours(1).getHour(), 0, 23, 1));
    spnEndMinute = new JSpinner(new SpinnerNumberModel(LocalTime.now().getMinute(), 0, 59, 1));
    endTimePanel.add(spnEndHour);
    endTimePanel.add(new JLabel(":"));
    endTimePanel.add(spnEndMinute);
    gbc.gridx = 1;
    mainFormPanel.add(endTimePanel, gbc);

    // All Day Checkbox
    gbc.gridy++;
    gbc.gridx = 0;
    gbc.gridwidth = 2;
    chkAllDay = new JCheckBox("All-day event");
    chkAllDay.addItemListener(
        e -> {
          boolean isAllDay = e.getStateChange() == ItemEvent.SELECTED;
          spnStartHour.setEnabled(!isAllDay);
          spnStartMinute.setEnabled(!isAllDay);
          spnEndHour.setEnabled(!isAllDay);
          spnEndMinute.setEnabled(!isAllDay);
          if (isAllDay) {
            spnStartHour.setValue(0);
            spnStartMinute.setValue(0);
            spnEndHour.setValue(0);
            spnEndMinute.setValue(0); // Or 23:59 based on preference
          }
        });
    mainFormPanel.add(chkAllDay, gbc);

    // Guests
    gbc.gridy++;
    gbc.gridwidth = 1;
    gbc.gridx = 0;
    mainFormPanel.add(new JLabel("Guests (CSV emails):"), gbc);
    gbc.gridx = 1;
    txtGuests = new JTextField(20);
    txtGuests.setText("guest@example.com");
    mainFormPanel.add(txtGuests, gbc);

    // Meet Link
    gbc.gridy++;
    gbc.gridx = 0;
    mainFormPanel.add(new JLabel("Meet Link (URL):"), gbc);
    gbc.gridx = 1;
    txtMeetLink = new JTextField(20);
    txtMeetLink.setText("https://meet.google.com/");
    mainFormPanel.add(txtMeetLink, gbc);

    // Location
    gbc.gridy++;
    gbc.gridx = 0;
    mainFormPanel.add(new JLabel("Location:"), gbc);
    gbc.gridx = 1;
    txtLocation = new JTextField(20);
    txtLocation.setText("Office Conference Room");
    mainFormPanel.add(txtLocation, gbc);

    // Description
    gbc.gridy++;
    gbc.gridx = 0;
    gbc.anchor = GridBagConstraints.NORTHWEST;
    mainFormPanel.add(new JLabel("Description:"), gbc);
    gbc.gridx = 1;
    gbc.fill = GridBagConstraints.BOTH;
    gbc.weightx = 1.0;
    gbc.weighty = 1.0;
    txaDescription = new JTextArea(5, 20);
    txaDescription.setText("Detailed description of the event.");
    txaDescription.setLineWrap(true);
    txaDescription.setWrapStyleWord(true);
    JScrollPane descriptionScrollPane = new JScrollPane(txaDescription);
    mainFormPanel.add(descriptionScrollPane, gbc);
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.weightx = 0;
    gbc.weighty = 0;
    gbc.anchor = GridBagConstraints.WEST; // Reset

    // Category
    gbc.gridy++;
    gbc.gridx = 0;
    mainFormPanel.add(new JLabel("Category:"), gbc);
    gbc.gridx = 1;
    cmbCategory =
        new JComboBox<>(new String[] {"Bills", "Personal", "Work", "Appointments", "Travel"});
    mainFormPanel.add(cmbCategory, gbc);

    // Transparency
    gbc.gridy++;
    gbc.gridx = 0;
    mainFormPanel.add(new JLabel("Show as:"), gbc);
    gbc.gridx = 1;
    cmbTransparency = new JComboBox<>(new String[] {"Busy (Opaque)", "Free (Transparent)"});
    mainFormPanel.add(cmbTransparency, gbc);

    // Notification
    gbc.gridy++;
    gbc.gridx = 0;
    mainFormPanel.add(new JLabel("Notification Text:"), gbc);
    gbc.gridx = 1;
    txtNotificationDetails = new JTextField(20);
    txtNotificationDetails.setText("Notify 1 day before at 9 AM");
    mainFormPanel.add(txtNotificationDetails, gbc);

    // TimeZone
    gbc.gridy++;
    gbc.gridx = 0;
    mainFormPanel.add(new JLabel("Time Zone:"), gbc);
    gbc.gridx = 1;
    String[] availableZoneIds = TimeZone.getAvailableIDs();
    ArrayList<String> sortedZoneIds = new ArrayList<>(Arrays.asList(availableZoneIds));
    Collections.sort(sortedZoneIds);
    cmbTimeZone = new JComboBox<>(sortedZoneIds.toArray(new String[0]));
    cmbTimeZone.setSelectedItem(ZoneId.systemDefault().getId());
    mainFormPanel.add(cmbTimeZone, gbc);

    // Save Button
    gbc.gridy++;
    gbc.gridx = 0;
    gbc.gridwidth = 2;
    gbc.anchor = GridBagConstraints.CENTER;
    JButton btnSave = new JButton("Save Event");
    btnSave.addActionListener(this::saveEvent);
    mainFormPanel.add(btnSave, gbc);

    // --- Event List Panel (Right Side) ---
    eventListModel = new DefaultListModel<>();
    eventDisplayList = new JList<>(eventListModel);
    eventDisplayList.setCellRenderer(new EventListCellRenderer());
    eventDisplayList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    eventDisplayList.setFixedCellHeight(70);

    JScrollPane listScrollPane = new JScrollPane(eventDisplayList);
    JPanel rightPanel = new JPanel(new BorderLayout());
    rightPanel.setBorder(BorderFactory.createTitledBorder("Upcoming Events"));
    rightPanel.add(listScrollPane, BorderLayout.CENTER);

    JSplitPane splitPane =
        new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(mainFormPanel), rightPanel);
    splitPane.setDividerLocation(480); // Adjusted for potentially wider form
    splitPane.setResizeWeight(0.45);

    add(splitPane);
    addSampleEvents();
  }

  private void addSampleEvents() {
    CalendarEvent event1 = new CalendarEvent();
    event1.setSummary("Team Stand-up");
    event1.setDtstart(
        ZonedDateTime.of(LocalDate.now().plusDays(1), LocalTime.of(9, 0), ZoneId.systemDefault()));
    event1.setDtend(
        ZonedDateTime.of(LocalDate.now().plusDays(1), LocalTime.of(9, 15), ZoneId.systemDefault()));
    event1.setCategories(List.of("Work"));
    event1.setLocation("Virtual");
    eventListModel.addElement(event1);

    CalendarEvent event2 = new CalendarEvent();
    event2.setSummary("Doctor's Appointment");
    event2.setDtstart(
        ZonedDateTime.of(
            LocalDate.now().plusDays(2), LocalTime.of(14, 30), ZoneId.systemDefault()));
    event2.setDtend(
        ZonedDateTime.of(
            LocalDate.now().plusDays(2), LocalTime.of(15, 30), ZoneId.systemDefault()));
    event2.setCategories(List.of("Appointments"));
    event2.setLocation("Greenway Medical Center");
    eventListModel.addElement(event2);

    CalendarEvent event3 = new CalendarEvent();
    event3.setSummary("Pay Monthly Bills");
    event3.setDtstart(
        ZonedDateTime.of(LocalDate.now().plusDays(3), LocalTime.MIDNIGHT, ZoneId.systemDefault()));
    event3.setDuration("P1D"); // All day event
    event3.setCategories(List.of("Bills"));
    eventListModel.addElement(event3);
  }

  // Changed parameter from ActionEvent e to be explicit for the method reference
  private void saveEvent(ActionEvent eventParam) {
    CalendarEvent event = new CalendarEvent();
    event.setSummary(txtTitle.getText());

    try {
      int startDay = (int) spnStartDay.getValue();
      int startMonth = (int) spnStartMonth.getValue();
      int startYear = (int) spnStartYear.getValue();
      int startHour = chkAllDay.isSelected() ? 0 : (int) spnStartHour.getValue();
      int startMinute = chkAllDay.isSelected() ? 0 : (int) spnStartMinute.getValue();

      int endDay = (int) spnEndDay.getValue();
      int endMonth = (int) spnEndMonth.getValue();
      int endYear = (int) spnEndYear.getValue();
      // For all-day, DTEND is often the start of the next day if using DTEND, or duration is used.
      // If not all-day, use spinner values.
      int endHour = chkAllDay.isSelected() ? 0 : (int) spnEndHour.getValue();
      int endMinute = chkAllDay.isSelected() ? 0 : (int) spnEndMinute.getValue();

      ZoneId selectedZoneId = ZoneId.of((String) cmbTimeZone.getSelectedItem());
      event.setTimeZoneId(selectedZoneId.getId());

      LocalDateTime ldtStart =
          LocalDateTime.of(startYear, startMonth, startDay, startHour, startMinute);

      if (chkAllDay.isSelected()) {
        // For all-day events, DTSTART is a DATE value (time part is effectively ignored or
        // midnight)
        event.setDtstart(
            ZonedDateTime.of(
                LocalDate.of(startYear, startMonth, startDay), LocalTime.MIDNIGHT, selectedZoneId));
        event.setDuration("P1D"); // RFC5545: DTEND MUST NOT occur if DURATION is present
        event.setDtend(null);
      } else {
        event.setDtstart(ZonedDateTime.of(ldtStart, selectedZoneId));
        LocalDateTime ldtEnd = LocalDateTime.of(endYear, endMonth, endDay, endHour, endMinute);
        event.setDtend(ZonedDateTime.of(ldtEnd, selectedZoneId));
        event.setDuration(null); // Ensure duration is null if DTEND is used
      }
    } catch (Exception ex) {
      JOptionPane.showMessageDialog(
          this,
          "Error parsing date/time: " + ex.getMessage(),
          "Input Error",
          JOptionPane.ERROR_MESSAGE);
      return;
    }

    String guestsText = txtGuests.getText();
    if (!guestsText.trim().isEmpty()) {
      List<CalendarEvent.Attendee> attendees = new ArrayList<>();
      String[] emails = guestsText.split(",");
      for (String email : emails) {
        if (!email.trim().isEmpty()) {
          // Assuming CalendarEvent.Attendee is a public static inner class
          attendees.add(new CalendarEvent.Attendee(email.trim()));
        }
      }
      if (!attendees.isEmpty()) event.setAttendees(attendees);
    }

    if (!txtMeetLink.getText().trim().isEmpty()) event.setUrl(txtMeetLink.getText().trim());
    event.setLocation(txtLocation.getText().trim());
    event.setDescription(txaDescription.getText().trim());
    String selectedCategory = (String) cmbCategory.getSelectedItem();
    if (selectedCategory != null) event.setCategories(List.of(selectedCategory));
    event.setTransparency(
        "Free (Transparent)".equals(cmbTransparency.getSelectedItem()) ? "TRANSPARENT" : "OPAQUE");

    // Direct call, assuming POJO has this method
    event.setNotificationDetails(txtNotificationDetails.getText().trim());

    eventListModel.addElement(event);
    eventDisplayList.ensureIndexIsVisible(eventListModel.getSize() - 1);

    // Optional: Show dialog confirmation (can be removed if list update is enough)
    String prettyOutput = getPrettyPrintedEvent(event);
    JTextArea resultArea = new JTextArea(prettyOutput, 20, 50); // Adjusted size
    resultArea.setEditable(false);
    resultArea.setCaretPosition(0); // Scroll to top
    JOptionPane.showMessageDialog(
        this,
        new JScrollPane(resultArea),
        "Event Saved & Added to List",
        JOptionPane.INFORMATION_MESSAGE);
  }

  private String getPrettyPrintedEvent(CalendarEvent event) {
    StringBuilder sb = new StringBuilder();
    sb.append("Google Calendar Event:\n");
    String indent = "  ";

    appendIfNotNull(sb, indent, "UID", event.getUid());
    appendIfNotNull(
        sb, indent, "Timestamp", event.getDtstamp(), DateTimeFormatter.ISO_ZONED_DATE_TIME);
    appendIfNotNull(
        sb, indent, "Start DateTime", event.getDtstart(), DateTimeFormatter.ISO_ZONED_DATE_TIME);
    appendIfNotNull(sb, indent, "Summary", event.getSummary());
    appendIfNotNull(
        sb, indent, "End DateTime", event.getDtend(), DateTimeFormatter.ISO_ZONED_DATE_TIME);
    appendIfNotNull(sb, indent, "Duration", event.getDuration());
    appendIfNotNull(sb, indent, "Description", event.getDescription());
    appendIfNotNull(sb, indent, "Location", event.getLocation());
    appendIfNotNull(sb, indent, "Status", event.getStatus());
    appendIfNotNull(sb, indent, "Timezone ID", event.getTimeZoneId());

    if (event.getCategories() != null && !event.getCategories().isEmpty()) {
      sb.append(indent)
          .append("Categories: ")
          .append(String.join(", ", event.getCategories()))
          .append("\n");
    }

    appendIfNotNull(sb, indent, "Transparency", event.getTransparency());
    appendIfNotNull(sb, indent, "Sequence", event.getSequence());
    appendIfNotNull(
        sb, indent, "Created DateTime", event.getCreated(), DateTimeFormatter.ISO_ZONED_DATE_TIME);
    appendIfNotNull(
        sb,
        indent,
        "Last Modified DateTime",
        event.getLastModified(),
        DateTimeFormatter.ISO_ZONED_DATE_TIME);
    appendIfNotNull(sb, indent, "Organizer Email", event.getOrganizerEmail());
    appendIfNotNull(sb, indent, "Organizer CN", event.getOrganizerCN());

    // Direct call, assuming POJO has this method
    appendIfNotNull(sb, indent, "Notification Details", event.getNotificationDetails());

    if (event.getAttendees() != null && !event.getAttendees().isEmpty()) {
      sb.append(indent).append("Attendees:\n");
      for (CalendarEvent.Attendee attendee : event.getAttendees()) {
        sb.append(attendeeToPrettyString(attendee, indent + "  "));
      }
    } else {
      appendIfNotNull(sb, indent, "Attendees", "None"); // Or just skip if null/empty
    }

    appendIfNotNull(sb, indent, "Recurrence Rule (RRULE)", event.getRrule());
    appendIfNotNull(sb, indent, "Exception Dates (EXDATE)", event.getExdate());
    appendIfNotNull(sb, indent, "Recurrence Dates (RDATE)", event.getRdate());
    appendIfNotNull(sb, indent, "URL", event.getUrl());

    return sb.toString();
  }

  private String attendeeToPrettyString(CalendarEvent.Attendee attendee, String indent) {
    StringBuilder sb = new StringBuilder();
    // Check if attendee is null before trying to access its methods
    if (attendee == null) {
      sb.append(indent).append("Attendee: null\n");
      return sb.toString();
    }
    sb.append(indent).append("Attendee:\n");
    String innerIndent = indent + "  ";
    if (attendee.getEmail() != null)
      sb.append(innerIndent).append("Email: ").append(attendee.getEmail()).append("\n");
    if (attendee.getCommonName() != null)
      sb.append(innerIndent).append("Common Name: ").append(attendee.getCommonName()).append("\n");
    if (attendee.getParticipationStatus() != null)
      sb.append(innerIndent)
          .append("Participation Status: ")
          .append(attendee.getParticipationStatus())
          .append("\n");
    if (attendee.getRole() != null)
      sb.append(innerIndent).append("Role: ").append(attendee.getRole()).append("\n");
    return sb.toString();
  }

  private void appendIfNotNull(StringBuilder sb, String indent, String label, Object value) {
    if (value != null
        && !(value instanceof String
            && ((String) value).trim().isEmpty())) { // Also check for empty strings
      sb.append(indent).append(label).append(": ").append(value).append("\n");
    }
  }

  private void appendIfNotNull(
      StringBuilder sb,
      String indent,
      String label,
      ZonedDateTime value,
      DateTimeFormatter formatter) {
    if (value != null) {
      sb.append(indent).append(label).append(": ").append(formatter.format(value)).append("\n");
    }
  }

  public static class EventListCellRenderer extends JPanel
      implements ListCellRenderer<CalendarEvent> {

    /** */
    private static final long serialVersionUID = 1L;

    private final JPanel colorDotPanel;
    private final JLabel lblDateTime;
    private final JLabel lblSummary;
    private final JLabel lblDetails; // For location or other short details

    private static final Map<String, Color> categoryColors = new HashMap<>();
    private static final Color DEFAULT_COLOR = Color.GRAY;

    // Define some category colors (expand as needed)
    static {
      categoryColors.put("Bills", new Color(0x4285F4)); // Google Blue
      categoryColors.put("Work", new Color(0xD93025)); // Google Red
      categoryColors.put("Personal", new Color(0x188038)); // Google Green
      categoryColors.put("Appointments", new Color(0xF29900)); // Google Yellow
      categoryColors.put("Travel", new Color(0xA142F4)); // Purple
    }

    public EventListCellRenderer() {
      setLayout(new BorderLayout(5, 5));
      setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

      colorDotPanel =
          new JPanel() {
            private Color dotColor = DEFAULT_COLOR;

            public void setDotColor(Color color) {
              this.dotColor = color;
              repaint();
            }

            @Override
            protected void paintComponent(Graphics g) {
              super.paintComponent(g);
              Graphics2D g2d = (Graphics2D) g.create();
              g2d.setRenderingHint(
                  RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
              int diameter = Math.min(getWidth(), getHeight()) - 2; // Adjust diameter
              if (diameter < 5) diameter = 5;
              int x = (getWidth() - diameter) / 2;
              int y = (getHeight() - diameter) / 2;
              g2d.setColor(dotColor);
              g2d.fillOval(x, y, diameter, diameter);
              g2d.dispose();
            }

            @Override
            public Dimension getPreferredSize() {
              return new Dimension(15, 15); // Small square panel for the dot
            }
          };

      JPanel textPanel = new JPanel();
      textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
      textPanel.setOpaque(false); // Make transparent to show selection color of parent

      lblDateTime = new JLabel();
      lblDateTime.setFont(lblDateTime.getFont().deriveFont(Font.BOLD, 13f));
      lblSummary = new JLabel();
      lblSummary.setFont(lblSummary.getFont().deriveFont(Font.PLAIN, 14f));
      lblDetails = new JLabel();
      lblDetails.setFont(lblDetails.getFont().deriveFont(Font.ITALIC, 12f));
      lblDetails.setForeground(Color.DARK_GRAY);

      textPanel.add(lblDateTime);
      textPanel.add(lblSummary);
      textPanel.add(lblDetails);

      add(colorDotPanel, BorderLayout.WEST);
      add(textPanel, BorderLayout.CENTER);
    }

    @Override
    public Component getListCellRendererComponent(
        JList<? extends CalendarEvent> list,
        CalendarEvent event,
        int index,
        boolean isSelected,
        boolean cellHasFocus) {
      if (event == null) { // Should not happen with DefaultListModel if no nulls added
        lblSummary.setText("No Event Data");
        lblDateTime.setText("");
        lblDetails.setText("");
        colorDotPanel.setToolTipText("Default");
        if (colorDotPanel instanceof JPanel
            && colorDotPanel.getComponentCount() == 0) { // a bit of a hack to set color
          try {
            java.lang.reflect.Method m =
                colorDotPanel.getClass().getMethod("setDotColor", Color.class);
            m.invoke(colorDotPanel, DEFAULT_COLOR);
          } catch (Exception e) {
          }
        }
        return this;
      }

      // Date and Time Formatting
      DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("MMM dd, EEE");
      DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("h:mma");
      String dateStr = "";
      String timeStr = "All day";

      if (event.getDtstart() != null) {
        dateStr = event.getDtstart().format(dateFormatter).toUpperCase();
        // Check for all-day (simplified: duration "P1D" or start time is midnight)
        boolean isAllDay =
            "P1D".equals(event.getDuration())
                || (event.getDtstart().toLocalTime().equals(java.time.LocalTime.MIDNIGHT)
                    && (event.getDtend() == null
                        || (event.getDtend() != null
                            && event.getDtend().toLocalTime().equals(java.time.LocalTime.MIDNIGHT)
                            && event.getDtstart().until(event.getDtend(), ChronoUnit.DAYS) >= 1)));

        if (!isAllDay) {
          timeStr = event.getDtstart().format(timeFormatter);
          if (event.getDtend() != null
              && event.getDtend().toLocalDate().equals(event.getDtstart().toLocalDate())
              && // Same day
              !event
                  .getDtend()
                  .toLocalTime()
                  .equals(event.getDtstart().toLocalTime())) { // Different time
            timeStr += " - " + event.getDtend().format(timeFormatter);
          } else if (event.getDtend() != null
              && !event.getDtend().toLocalDate().equals(event.getDtstart().toLocalDate())) {
            // Ends on a different day
            timeStr +=
                " - "
                    + event.getDtend().format(dateFormatter)
                    + " "
                    + event.getDtend().format(timeFormatter);
          }
        }
      }
      lblDateTime.setText(dateStr + "  |  " + timeStr);
      lblSummary.setText(event.getSummary() != null ? event.getSummary() : "(No summary)");

      // Details (e.g., location)
      if (event.getLocation() != null && !event.getLocation().trim().isEmpty()) {
        lblDetails.setText(event.getLocation());
        lblDetails.setVisible(true);
      } else {
        lblDetails.setText("");
        lblDetails.setVisible(false);
      }

      // Color Dot
      Color eventColor = DEFAULT_COLOR;
      String categoryTooltip = "Default";
      if (event.getCategories() != null && !event.getCategories().isEmpty()) {
        String firstCategory = event.getCategories().get(0); // Use first category for color
        eventColor = categoryColors.getOrDefault(firstCategory, DEFAULT_COLOR);
        categoryTooltip = firstCategory;
      }
      if (colorDotPanel instanceof JPanel
          && colorDotPanel.getComponentCount() == 0) { // a bit of a hack to set color
        try {
          java.lang.reflect.Method m =
              colorDotPanel.getClass().getMethod("setDotColor", Color.class);
          m.invoke(colorDotPanel, eventColor);
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
      colorDotPanel.setToolTipText("Category: " + categoryTooltip);

      // Handle selection background/foreground
      if (isSelected) {
        setBackground(list.getSelectionBackground());
        setForeground(list.getSelectionForeground());
        lblDateTime.setForeground(list.getSelectionForeground());
        lblSummary.setForeground(list.getSelectionForeground());
        lblDetails.setForeground(
            list.getSelectionForeground().darker()); // slightly dimmer for details
      } else {
        setBackground(list.getBackground());
        setForeground(list.getForeground());
        lblDateTime.setForeground(list.getForeground());
        lblSummary.setForeground(list.getForeground());
        lblDetails.setForeground(Color.DARK_GRAY);
      }
      setOpaque(true); // Make sure background color is painted

      return this;
    }
  }

  //////////////////////////////////////////////////
  ///
  ///
  /// ///////////////////////////////////
  ///
  public static class CalendarEvent {

    // Required properties
    private String uid; // Unique Identifier
    private ZonedDateTime dtstamp; // DateTime Stamp - when the event was created/modified
    private ZonedDateTime dtstart; // DateTime Start
    private String summary; // Event title or summary

    // Optional but common properties
    private ZonedDateTime
        dtend; // DateTime End (either dtend or duration should be present if not an all-day event)
    private String duration; // Duration (e.g., "PT1H" for 1 hour)
    private String description; // Event description
    private String location; // Event location
    private String status; // Event status (e.g., CONFIRMED, TENTATIVE, CANCELLED)
    private List<String> categories; // Event categories or tags
    private String transparency; // Whether the event is OPAQUE (busy) or TRANSPARENT (free)
    private Integer sequence; // Revision sequence number
    private ZonedDateTime created; // DateTime Created
    private ZonedDateTime lastModified; // DateTime Last Modified
    private String organizerEmail; // Organizer's email
    private String organizerCN; // Organizer's common name
    private List<Attendee> attendees; // List of attendees
    private String rrule; // Recurrence rule (e.g., FREQ=WEEKLY;BYDAY=MO;UNTIL=20251231T000000Z)
    private String exdate; // Exception dates for recurring events
    private String rdate; // Recurrence dates
    private String url; // URL associated with the event
    private String timeZoneId; // Timezone ID (e.g., "America/New_York")
    private String notificationDetails;

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
        DateTimeFormatter.ISO_ZONED_DATE_TIME;

    // Constructor (optional, can use default or add one with required fields)
    public CalendarEvent() {
      // Generate a default UID if not set
      this.uid = UUID.randomUUID().toString();
      this.dtstamp = ZonedDateTime.now();
    }

    // Add its getter and setter
    public String getNotificationDetails() {
      return notificationDetails;
    }

    public void setNotificationDetails(String notificationDetails) {
      this.notificationDetails = notificationDetails;
    }

    // --- Accessors (Getters) and Mutators (Setters) ---

    public String getUid() {
      return uid;
    }

    public void setUid(String uid) {
      this.uid = uid;
    }

    public ZonedDateTime getDtstamp() {
      return dtstamp;
    }

    public void setDtstamp(ZonedDateTime dtstamp) {
      this.dtstamp = dtstamp;
    }

    public ZonedDateTime getDtstart() {
      return dtstart;
    }

    public void setDtstart(ZonedDateTime dtstart) {
      this.dtstart = dtstart;
    }

    public String getSummary() {
      return summary;
    }

    public void setSummary(String summary) {
      this.summary = summary;
    }

    public ZonedDateTime getDtend() {
      return dtend;
    }

    public void setDtend(ZonedDateTime dtend) {
      this.dtend = dtend;
    }

    public String getDuration() {
      return duration;
    }

    public void setDuration(String duration) {
      this.duration = duration;
    }

    public String getDescription() {
      return description;
    }

    public void setDescription(String description) {
      this.description = description;
    }

    public String getLocation() {
      return location;
    }

    public void setLocation(String location) {
      this.location = location;
    }

    public String getStatus() {
      return status;
    }

    public void setStatus(String status) {
      this.status = status;
    }

    public List<String> getCategories() {
      return categories;
    }

    public void setCategories(List<String> categories) {
      this.categories = categories;
    }

    public String getTransparency() {
      return transparency;
    }

    public void setTransparency(String transparency) {
      this.transparency = transparency;
    }

    public Integer getSequence() {
      return sequence;
    }

    public void setSequence(Integer sequence) {
      this.sequence = sequence;
    }

    public ZonedDateTime getCreated() {
      return created;
    }

    public void setCreated(ZonedDateTime created) {
      this.created = created;
    }

    public ZonedDateTime getLastModified() {
      return lastModified;
    }

    public void setLastModified(ZonedDateTime lastModified) {
      this.lastModified = lastModified;
    }

    public String getOrganizerEmail() {
      return organizerEmail;
    }

    public void setOrganizerEmail(String organizerEmail) {
      this.organizerEmail = organizerEmail;
    }

    public String getOrganizerCN() {
      return organizerCN;
    }

    public void setOrganizerCN(String organizerCN) {
      this.organizerCN = organizerCN;
    }

    public List<Attendee> getAttendees() {
      return attendees;
    }

    public void setAttendees(List<Attendee> attendees) {
      this.attendees = attendees;
    }

    public String getRrule() {
      return rrule;
    }

    public void setRrule(String rrule) {
      this.rrule = rrule;
    }

    public String getExdate() {
      return exdate;
    }

    public void setExdate(String exdate) {
      this.exdate = exdate;
    }

    public String getRdate() {
      return rdate;
    }

    public void setRdate(String rdate) {
      this.rdate = rdate;
    }

    public String getUrl() {
      return url;
    }

    public void setUrl(String url) {
      this.url = url;
    }

    public String getTimeZoneId() {
      return timeZoneId;
    }

    public void setTimeZoneId(String timeZoneId) {
      this.timeZoneId = timeZoneId;
    }

    // Inner class for Attendee
    public static class Attendee {
      private String email;
      private String commonName; // CN parameter
      private String participationStatus; // PARTSTAT (e.g., NEEDS-ACTION, ACCEPTED, DECLINED)
      private String role; // ROLE (e.g., CHAIR, REQ-PARTICIPANT)

      public Attendee(String email) {
        this.email = email;
      }

      public String getEmail() {
        return email;
      }

      public void setEmail(String email) {
        this.email = email;
      }

      public String getCommonName() {
        return commonName;
      }

      public void setCommonName(String commonName) {
        this.commonName = commonName;
      }

      public String getParticipationStatus() {
        return participationStatus;
      }

      public void setParticipationStatus(String participationStatus) {
        this.participationStatus = participationStatus;
      }

      public String getRole() {
        return role;
      }

      public void setRole(String role) {
        this.role = role;
      }

      public String toPrettyString(String indent) {
        StringBuilder sb = new StringBuilder();
        sb.append(indent).append("Attendee:\n");
        String innerIndent = indent + "  ";
        if (email != null) sb.append(innerIndent).append("Email: ").append(email).append("\n");
        if (commonName != null)
          sb.append(innerIndent).append("Common Name: ").append(commonName).append("\n");
        if (participationStatus != null)
          sb.append(innerIndent)
              .append("Participation Status: ")
              .append(participationStatus)
              .append("\n");
        if (role != null) sb.append(innerIndent).append("Role: ").append(role).append("\n");
        return sb.toString();
      }

      @Override
      public String toString() { // Kept for general purpose, printPretty uses toPrettyString
        return "Attendee{"
            + "email='"
            + email
            + '\''
            + ", commonName='"
            + commonName
            + '\''
            + ", participationStatus='"
            + participationStatus
            + '\''
            + ", role='"
            + role
            + '\''
            + '}';
      }
    }

    public void printPretty() {
      StringBuilder sb = new StringBuilder();
      sb.append("Google Calendar Event:\n");
      String indent = "  ";

      appendIfNotNull(sb, indent, "UID", uid);
      appendIfNotNull(sb, indent, "Timestamp", dtstamp, DATE_TIME_FORMATTER);
      appendIfNotNull(sb, indent, "Start DateTime", dtstart, DATE_TIME_FORMATTER);
      appendIfNotNull(sb, indent, "Summary", summary);
      appendIfNotNull(sb, indent, "End DateTime", dtend, DATE_TIME_FORMATTER);
      appendIfNotNull(sb, indent, "Duration", duration);
      appendIfNotNull(sb, indent, "Description", description);
      appendIfNotNull(sb, indent, "Location", location);
      appendIfNotNull(sb, indent, "Status", status);
      appendIfNotNull(sb, indent, "Timezone ID", timeZoneId);

      if (categories != null && !categories.isEmpty()) {
        sb.append(indent).append("Categories: ").append(String.join(", ", categories)).append("\n");
      }

      appendIfNotNull(sb, indent, "Transparency", transparency);
      appendIfNotNull(sb, indent, "Sequence", sequence);
      appendIfNotNull(sb, indent, "Created DateTime", created, DATE_TIME_FORMATTER);
      appendIfNotNull(sb, indent, "Last Modified DateTime", lastModified, DATE_TIME_FORMATTER);
      appendIfNotNull(sb, indent, "Organizer Email", organizerEmail);
      appendIfNotNull(sb, indent, "Organizer CN", organizerCN);

      if (attendees != null && !attendees.isEmpty()) {
        sb.append(indent).append("Attendees:\n");
        for (Attendee attendee : attendees) {
          sb.append(attendee.toPrettyString(indent + "  "));
        }
      } else {
        appendIfNotNull(sb, indent, "Attendees", "None");
      }

      appendIfNotNull(sb, indent, "Recurrence Rule (RRULE)", rrule);
      appendIfNotNull(sb, indent, "Exception Dates (EXDATE)", exdate);
      appendIfNotNull(sb, indent, "Recurrence Dates (RDATE)", rdate);
      appendIfNotNull(sb, indent, "URL", url);

      System.out.println(sb);
    }

    private void appendIfNotNull(StringBuilder sb, String indent, String label, Object value) {
      if (value != null) {
        sb.append(indent).append(label).append(": ").append(value).append("\n");
      }
    }

    private void appendIfNotNull(
        StringBuilder sb,
        String indent,
        String label,
        ZonedDateTime value,
        DateTimeFormatter formatter) {
      if (value != null) {
        sb.append(indent).append(label).append(": ").append(formatter.format(value)).append("\n");
      }
    }

    @Override
    public String toString() { // Standard toString, printPretty is for formatted output
      return "CalendarEvent{"
          + "uid='"
          + uid
          + '\''
          + ", dtstamp="
          + (dtstamp != null ? DATE_TIME_FORMATTER.format(dtstamp) : "null")
          + ", dtstart="
          + (dtstart != null ? DATE_TIME_FORMATTER.format(dtstart) : "null")
          + ", summary='"
          + summary
          + '\''
          + ", dtend="
          + (dtend != null ? DATE_TIME_FORMATTER.format(dtend) : "null")
          + ", duration='"
          + duration
          + '\''
          + ", description='"
          + description
          + '\''
          + ", location='"
          + location
          + '\''
          + ", status='"
          + status
          + '\''
          + ", categories="
          + categories
          + ", transparency='"
          + transparency
          + '\''
          + ", sequence="
          + sequence
          + ", created="
          + (created != null ? DATE_TIME_FORMATTER.format(created) : "null")
          + ", lastModified="
          + (lastModified != null ? DATE_TIME_FORMATTER.format(lastModified) : "null")
          + ", organizerEmail='"
          + organizerEmail
          + '\''
          + ", organizerCN='"
          + organizerCN
          + '\''
          + ", attendees="
          + attendees
          + ", rrule='"
          + rrule
          + '\''
          + ", exdate='"
          + exdate
          + '\''
          + ", rdate='"
          + rdate
          + '\''
          + ", url='"
          + url
          + '\''
          + ", timeZoneId='"
          + timeZoneId
          + '\''
          + '}';
    }

    // Main method for basic testing
    public static void main(String[] args) {
      CalendarEvent event = new CalendarEvent();
      event.setSummary("Team Meeting & Project Sync-Up");
      event.setDtstart(ZonedDateTime.parse("2025-06-15T10:00:00-04:00[America/New_York]"));
      event.setDtend(ZonedDateTime.parse("2025-06-15T11:30:00-04:00[America/New_York]"));
      // event.setDuration("PT1H30M"); // Alternative to dtend
      event.setLocation("Conference Room A / Virtual (See URL)");
      event.setDescription(
          "Discuss project updates for Q3 goals. Review timeline and address blockers.");
      event.setStatus("CONFIRMED");
      event.setOrganizerEmail("organizer.lead@example.com");
      event.setOrganizerCN("John Lead Doe (Organizer)");
      event.setCategories(List.of("PROJECTS", "TEAM", "Q3"));
      event.setTransparency("OPAQUE");
      event.setSequence(2); // Second revision
      event.setCreated(ZonedDateTime.parse("2025-05-10T09:00:00Z"));
      event.setLastModified(ZonedDateTime.now().minusHours(1)); // Example last modified
      event.setUrl("https://meet.example.com/team-meeting");
      event.setTimeZoneId("America/New_York");
      event.setRrule("FREQ=WEEKLY;BYDAY=MO;UNTIL=20250815T000000Z");

      CalendarEvent.Attendee attendee1 = new CalendarEvent.Attendee("alice.p@example.com");
      attendee1.setCommonName("Alice ProjectManager");
      attendee1.setParticipationStatus("ACCEPTED");
      attendee1.setRole("REQ-PARTICIPANT");

      CalendarEvent.Attendee attendee2 = new CalendarEvent.Attendee("bob.d@example.com");
      attendee2.setCommonName("Bob Developer");
      attendee2.setParticipationStatus("NEEDS-ACTION");
      attendee2.setRole("REQ-PARTICIPANT");

      CalendarEvent.Attendee attendee3 = new CalendarEvent.Attendee("charlie.o@example.com");
      attendee3.setCommonName("Charlie Observer");
      attendee3.setParticipationStatus("ACCEPTED");
      attendee3.setRole("OPT-PARTICIPANT");

      event.setAttendees(List.of(attendee1, attendee2, attendee3));

      // System.out.println("--- Standard toString() ---");
      // System.out.println(event);
      // System.out.println("\n--- printPretty() ---");
      event.printPretty();

      System.out.println("\n--- Event with fewer fields for printPretty() ---");
      CalendarEvent simpleEvent = new CalendarEvent();
      simpleEvent.setSummary("Quick Chat");
      simpleEvent.setDtstart(ZonedDateTime.parse("2025-07-01T14:00:00-07:00[America/Los_Angeles]"));
      simpleEvent.setDuration("PT30M");
      simpleEvent.printPretty();
    }
  }

  public static void main(String[] args) {
    SwingUtilities.invokeLater(
        () -> {
          CalendarEventCreatorGUI gui = new CalendarEventCreatorGUI();
          gui.setVisible(true);
        });
  }
}
