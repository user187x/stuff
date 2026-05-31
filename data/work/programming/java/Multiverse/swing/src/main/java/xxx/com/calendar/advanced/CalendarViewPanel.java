package xxx.com.calendar.advanced;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.temporal.Temporal;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import javax.swing.JPanel;
import javax.swing.border.LineBorder;
import net.fortuna.ical4j.model.component.CalendarComponent;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.property.DtEnd;
import net.fortuna.ical4j.model.property.DtStart;

class CalendarViewPanel extends JPanel {
  private static final long serialVersionUID = 1L;
  private net.fortuna.ical4j.model.Calendar calendar;
  private final Set<LocalDate> eventDays;
  private LocalDate displayMonth;
  private LocalDate highlightedDate = null;

  private static final Color EVENT_DAY_COLOR = new Color(173, 216, 230, 150);
  private static final Color WEEKEND_COLOR = new Color(240, 240, 240);
  private static final Color TODAY_BORDER_COLOR = Color.BLUE;
  private static final Color HIGHLIGHT_BORDER_COLOR = Color.RED;

  private int cellWidth = 0, cellHeight = 0, startX = 0, startY = 0;
  private int calendarAreaWidth = 0, calendarAreaHeight = 0;
  private final int padding = 10;
  private final int headerHeight = 30;
  private final int dayHeaderHeight = 25;

  public CalendarViewPanel() {
    setBackground(Color.WHITE);
    setBorder(new LineBorder(Color.LIGHT_GRAY, 1));
    setPreferredSize(new Dimension(250, 220));
    setMinimumSize(new Dimension(200, 180));
    eventDays = new HashSet<>();
    displayMonth = LocalDate.now();
  }

  public void setCalendar(net.fortuna.ical4j.model.Calendar calendar) {
    this.calendar = calendar;
    if (calendar == null) {
      displayMonth = LocalDate.now();
      eventDays.clear();
    }
    else {
      processEvents();
      if (this.highlightedDate == null && !eventDays.isEmpty()) {
        displayMonth = eventDays.stream().min(LocalDate::compareTo).orElse(displayMonth).withDayOfMonth(1);
      }
      else if (this.highlightedDate != null) {
        displayMonth = this.highlightedDate.withDayOfMonth(1);
      }
      else {
        displayMonth = LocalDate.now().withDayOfMonth(1);
      }
    }
    repaint();
  }

  public void clearCalendar() {
    setCalendar(null);
    clearHighlight();
  }

  private void processEvents() {
    if (calendar == null)
      return;
    eventDays.clear();
    for (CalendarComponent component : calendar.getComponents()) {
      if (component instanceof VEvent event) {
        DtStart<Temporal> dtStartProp = event.getDateTimeStart();
        DtEnd<Temporal> dtEndProp = event.getDateTimeEnd();
        LocalDate startDate = null, endDate = null;
        if (dtStartProp != null && dtStartProp.getDate() != null) {
          Temporal t = dtStartProp.getDate();
          if (t instanceof LocalDate)
            startDate = (LocalDate) t;
          else if (t instanceof ZonedDateTime)
            startDate = ((ZonedDateTime) t).toLocalDate();
        }
        if (dtEndProp != null && dtEndProp.getDate() != null) {
          Temporal t = dtEndProp.getDate();
          if (t instanceof LocalDate)
            endDate = (LocalDate) t;
          else if (t instanceof ZonedDateTime)
            endDate = ((ZonedDateTime) t).toLocalDate();
        }
        if (startDate != null) {
          if (endDate == null || endDate.isBefore(startDate)) {
            eventDays.add(startDate);
          }
          else {
            LocalDate effectiveEndDate = endDate;
            if (dtStartProp != null && dtStartProp.getDate() instanceof LocalDate && endDate.isAfter(startDate)) {
              effectiveEndDate = endDate.minusDays(1);
            }
            LocalDate currentDay = startDate;
            while (!currentDay.isAfter(effectiveEndDate)) {
              eventDays.add(currentDay);
              currentDay = currentDay.plusDays(1);
            }
          }
        }
      }
    }
  }

  public void setHighlightedDate(LocalDate date) {
    this.highlightedDate = date;
    if (date != null)
      setDisplayMonth(date);
    repaint();
  }

  public void clearHighlight() {
    this.highlightedDate = null;
    repaint();
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    Graphics2D g2d = (Graphics2D) g.create();
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    int width = getWidth();
    int height = getHeight();
    if (width <= 20 || height <= 50) {
      g2d.dispose();
      return;
    }

    calendarAreaHeight = height - padding * 2 - headerHeight - dayHeaderHeight;
    calendarAreaWidth = width - padding * 2;
    if (calendarAreaHeight <= 20 || calendarAreaWidth <= 20) {
      g2d.dispose();
      return;
    }

    int cols = 7;
    int rows = 6;
    cellWidth = calendarAreaWidth / cols;
    cellHeight = calendarAreaHeight / rows;
    if (cellWidth <= 5 || cellHeight <= 5) {
      g2d.dispose();
      return;
    }

    startX = padding;
    startY = padding + headerHeight + dayHeaderHeight;
    FontMetrics fm;
    String monthYearStr =
        displayMonth != null ? displayMonth.format(java.time.format.DateTimeFormatter.ofPattern("MMMM uuuu", Locale.getDefault()))
            : "No Date";
    g2d.setColor(Color.BLACK);
    g2d.setFont(getFont().deriveFont(Font.BOLD, 13f));
    fm = g2d.getFontMetrics();
    g2d.drawString(monthYearStr, startX + (calendarAreaWidth - fm.stringWidth(monthYearStr)) / 2, padding + fm.getAscent());

    String[] dayNames = {"S", "M", "T", "W", "T", "F", "S"};
    g2d.setFont(getFont().deriveFont(11f));
    fm = g2d.getFontMetrics();
    int dayHeaderY = padding + headerHeight + fm.getAscent() + 5;
    for (int i = 0; i < cols; i++)
      g2d.drawString(dayNames[i], startX + i * cellWidth + (cellWidth - fm.stringWidth(dayNames[i])) / 2, dayHeaderY);

    if (displayMonth != null) {
      int firstDayOfWeek = displayMonth.withDayOfMonth(1).getDayOfWeek().getValue();
      int startCol = (firstDayOfWeek % 7);
      int daysInMonth = displayMonth.lengthOfMonth();
      LocalDate today = LocalDate.now();
      int dayCounter = 1;
      for (int r = 0; r < rows; r++) {
        for (int c = 0; c < cols; c++) {
          if (!((r == 0 && c < startCol) || dayCounter > daysInMonth)) {
            int cellX = startX + c * cellWidth;
            int cellY = startY + r * cellHeight;
            LocalDate currentDay = displayMonth.withDayOfMonth(dayCounter);
            g2d.setColor(Color.WHITE);
            g2d.fillRect(cellX, cellY, cellWidth, cellHeight);
            if (currentDay.getDayOfWeek().getValue() >= 6) {
              g2d.setColor(WEEKEND_COLOR);
              g2d.fillRect(cellX, cellY, cellWidth, cellHeight);
            }
            if (eventDays.contains(currentDay)) {
              g2d.setColor(EVENT_DAY_COLOR);
              g2d.fillRect(cellX + 2, cellY + 2, cellWidth - 4, cellHeight - 4);
            }

            g2d.setStroke(new BasicStroke(1f));
            g2d.setColor(Color.LIGHT_GRAY);
            g2d.drawRect(cellX, cellY, cellWidth, cellHeight);

            if (currentDay.equals(highlightedDate)) {
              g2d.setColor(HIGHLIGHT_BORDER_COLOR);
              g2d.setStroke(new BasicStroke(2f));
              g2d.drawRect(cellX + 1, cellY + 1, cellWidth - 2, cellHeight - 2);
            }
            else if (currentDay.equals(today)) {
              g2d.setColor(TODAY_BORDER_COLOR);
              g2d.setStroke(new BasicStroke(1.5f));
              g2d.drawRect(cellX + 1, cellY + 1, cellWidth - 2, cellHeight - 2);
            }

            g2d.setColor(Color.BLACK);
            g2d.setFont(getFont().deriveFont(10f));
            fm = g2d.getFontMetrics();
            String dayStr = String.valueOf(dayCounter);
            g2d.drawString(dayStr, cellX + (cellWidth - fm.stringWidth(dayStr)) / 2,
                cellY + fm.getAscent() + (cellHeight - fm.getHeight()) / 2 + 2);
            dayCounter++;
          }
        }
      }
    }
    else {
      g2d.setColor(Color.GRAY);
      fm = g2d.getFontMetrics();
      String msg = "Load ICS";
      g2d.drawString(msg, startX + (calendarAreaWidth - fm.stringWidth(msg)) / 2, startY + calendarAreaHeight / 2 + fm.getAscent() / 2);
    }
    g2d.dispose();
  }

  public void setDisplayMonth(LocalDate month) {
    if (month != null) {
      this.displayMonth = month.withDayOfMonth(1);
      repaint();
    }
  }

  public void previousMonth() {
    if (displayMonth != null)
      setDisplayMonth(displayMonth.minusMonths(1));
  }

  public void nextMonth() {
    if (displayMonth != null)
      setDisplayMonth(displayMonth.plusMonths(1));
  }

  public LocalDate getDateAtPoint(Point p) {
    if (displayMonth == null || cellWidth <= 0 || cellHeight <= 0)
      return null;
    if (p.x < startX || p.x >= startX + calendarAreaWidth || p.y < startY || p.y >= startY + calendarAreaHeight)
      return null;
    int c = (p.x - startX) / cellWidth;
    int r = (p.y - startY) / cellHeight;
    int firstDayVal = displayMonth.withDayOfMonth(1).getDayOfWeek().getValue();
    int startC = (firstDayVal % 7);
    int dayNum = r * 7 + c - startC + 1;
    if (dayNum >= 1 && dayNum <= displayMonth.lengthOfMonth())
      return displayMonth.withDayOfMonth(dayNum);
    return null;
  }
}
