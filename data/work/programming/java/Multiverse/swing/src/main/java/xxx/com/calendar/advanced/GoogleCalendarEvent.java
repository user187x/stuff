package xxx.com.calendar.advanced;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
// No AWT Color import needed here if we store as hex

public class GoogleCalendarEvent {

  private String uid;
  private ZonedDateTime dtstamp;
  private ZonedDateTime dtstart;
  private String summary;
  private ZonedDateTime dtend;
  private String duration;
  private String description;
  private String location;
  private String status;
  private List<String> categories;
  // private String transparency; // Removed
  private String eventColorHex; // Added to store event color as hex string
  private Integer sequence;
  private ZonedDateTime created;
  private ZonedDateTime lastModified;
  private String organizerEmail;
  private String organizerCN;
  private List<Attendee> attendees;
  private String rrule;
  private String exdate;
  private String rdate;
  private String url;
  private String timeZoneId;
  private String notificationDetails;

  public GoogleCalendarEvent() {
    this.uid = UUID.randomUUID().toString();
    this.dtstamp = ZonedDateTime.now();
    this.sequence = 0;
    // Default color can be set here or handled by UI/renderer if null
  }

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

  // public String getTransparency() { // Removed
  // return transparency;
  // }
  //
  // public void setTransparency(String transparency) { // Removed
  // this.transparency = transparency;
  // }

  public String getEventColorHex() { // Added
    return eventColorHex;
  }

  public void setEventColorHex(String eventColorHex) { // Added
    this.eventColorHex = eventColorHex;
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

  public String getNotificationDetails() {
    return notificationDetails;
  }

  public void setNotificationDetails(String notificationDetails) {
    this.notificationDetails = notificationDetails;
  }

  public static class Attendee {
    private String email;
    private String commonName;
    private String participationStatus;
    private String role;

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

    @Override
    public String toString() {
      return "Attendee{email='" + email + '\'' + ", CN='" + commonName + '\'' + ", status='" + participationStatus + '\'' + '}';
    }
  }

  @Override
  public String toString() {
    return "Event: " + summary + " (Starts: " + (dtstart != null ? dtstart.format(DateTimeFormatter.ISO_LOCAL_DATE) : "N/A") + ")";
  }
}
