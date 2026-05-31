package xxx.com.calendar.advanced;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
// import java.util.HashMap; // No longer needed for category colors
import java.util.Locale;
// import java.util.Map; // No longer needed for category colors
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;
import javax.swing.UIManager;

public class EventListCellRenderer extends JPanel implements ListCellRenderer<GoogleCalendarEvent> {
  private static final long serialVersionUID = 1L;
  private final ColorDotDisplay colorDotPanel;
  private final JLabel lblDateTime;
  private final JLabel lblSummary;
  private final JLabel lblDetails;

  // private static final Map<String, Color> categoryColors = new HashMap<>(); // Removed
  private static final Color DEFAULT_RENDER_COLOR = Color.GRAY; // Fallback color

  // static { // Removed category color map initialization
  // categoryColors.put("Bills", new Color(0x4285F4));
  // categoryColors.put("Work", new Color(0xD93025));
  // categoryColors.put("Personal", new Color(0x188038));
  // categoryColors.put("Appointments", new Color(0xF29900));
  // categoryColors.put("Travel", new Color(0x795548));
  // categoryColors.put("Default", DEFAULT_COLOR);
  // }

  private static class ColorDotDisplay extends JPanel {
    private static final long serialVersionUID = 1L;
    private Color dotColor = DEFAULT_RENDER_COLOR;

    public void setDotColor(Color color) {
      this.dotColor = (color == null) ? DEFAULT_RENDER_COLOR : color;
      repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      Graphics2D g2d = (Graphics2D) g.create();
      g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      int diameter = Math.min(getWidth(), getHeight()) - 4;
      if (diameter < 6)
        diameter = 6;
      int x = (getWidth() - diameter) / 2;
      int y = (getHeight() - diameter) / 2;
      g2d.setColor(dotColor);
      g2d.fillOval(x, y, diameter, diameter);
      g2d.dispose();
    }

    @Override
    public Dimension getPreferredSize() {
      return new Dimension(18, 18);
    }
  }

  public EventListCellRenderer() {
    setLayout(new BorderLayout(8, 3));
    setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
    colorDotPanel = new ColorDotDisplay();
    JPanel textPanel = new JPanel();
    textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
    textPanel.setOpaque(false);
    lblDateTime = new JLabel();
    lblDateTime.setFont(lblDateTime.getFont().deriveFont(Font.BOLD, 12f));
    lblSummary = new JLabel();
    lblSummary.setFont(lblSummary.getFont().deriveFont(Font.PLAIN, 13f));
    lblDetails = new JLabel();
    lblDetails.setFont(lblDetails.getFont().deriveFont(Font.ITALIC, 11f));
    lblDetails.setForeground(Color.GRAY);
    textPanel.add(lblDateTime);
    textPanel.add(Box.createRigidArea(new Dimension(0, 2)));
    textPanel.add(lblSummary);
    textPanel.add(Box.createRigidArea(new Dimension(0, 1)));
    textPanel.add(lblDetails);
    add(colorDotPanel, BorderLayout.WEST);
    add(textPanel, BorderLayout.CENTER);
  }

  @Override
  public Component getListCellRendererComponent(JList<? extends GoogleCalendarEvent> list, GoogleCalendarEvent event, int index, boolean isSelected, boolean cellHasFocus) {
    if (event == null) {
      lblSummary.setText("No Event Data");
      lblDateTime.setText("");
      lblDetails.setText("");
      colorDotPanel.setDotColor(DEFAULT_RENDER_COLOR);
      colorDotPanel.setToolTipText("Event Color");
      return this;
    }

    DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("MMM dd, EEE", Locale.ENGLISH);
    DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("h:mma", Locale.ENGLISH);
    String dateStr = "", timeStr = "All day";

    if (event.getDtstart() != null) {
      dateStr = event.getDtstart().format(dateFormatter).toUpperCase();
      boolean isAllDay = "P1D".equals(event.getDuration()) ||
          (event.getDtstart().toLocalTime().equals(java.time.LocalTime.MIDNIGHT) &&
              (event.getDtend() == null ||
                  (event.getDtend() != null &&
                      event.getDtend().toLocalTime().equals(java.time.LocalTime.MIDNIGHT) &&
                      ChronoUnit.DAYS.between(event.getDtstart().toLocalDate(), event.getDtend().toLocalDate()) >= 1)));
      if (!isAllDay) {
        timeStr = event.getDtstart().format(timeFormatter);
        if (event.getDtend() != null) {
          if (event.getDtend().toLocalDate().equals(event.getDtstart().toLocalDate()) &&
              !event.getDtend().toLocalTime().equals(event.getDtstart().toLocalTime())) {
            timeStr += " - " + event.getDtend().format(timeFormatter);
          }
          else if (!event.getDtend().toLocalDate().equals(event.getDtstart().toLocalDate())) {
            timeStr += " \u2192 " + event.getDtend().format(dateFormatter) + " " + event.getDtend().format(timeFormatter);
          }
        }
      }
    }
    lblDateTime.setText(dateStr + "  |  " + timeStr);
    lblSummary.setText("<html>" + (event.getSummary() != null ? escapeHtml(event.getSummary()) : "(No summary)") + "</html>");

    if (event.getLocation() != null && !event.getLocation().trim().isEmpty()) {
      lblDetails.setText(escapeHtml(event.getLocation()));
      lblDetails.setVisible(true);
    }
    else {
      lblDetails.setVisible(false);
    }

    // Use eventColorHex for the dot color
    Color eventColor = CalendarEventCreatorGUI.hexToColor(event.getEventColorHex());
    if (eventColor == null) { // Should be handled by hexToColor returning default
      eventColor = DEFAULT_RENDER_COLOR;
    }
    colorDotPanel.setDotColor(eventColor);
    colorDotPanel.setToolTipText("Event Color: " + (event.getEventColorHex() != null ? event.getEventColorHex() : "Default"));


    if (isSelected) {
      setBackground(list.getSelectionBackground());
      lblDateTime.setForeground(list.getSelectionForeground());
      lblSummary.setForeground(list.getSelectionForeground());
      lblDetails.setForeground(list.getSelectionForeground().darker());
    }
    else {
      setBackground(list.getBackground());
      lblDateTime.setForeground(UIManager.getColor("Label.foreground"));
      lblSummary.setForeground(UIManager.getColor("Label.foreground"));
      lblDetails.setForeground(Color.GRAY);
    }
    setOpaque(true);
    return this;
  }

  private String escapeHtml(String text) {
    if (text == null)
      return "";
    return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }
}
