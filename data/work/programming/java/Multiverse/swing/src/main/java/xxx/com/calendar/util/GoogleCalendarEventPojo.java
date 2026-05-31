package xxx.com.calendar.util;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter; // For toString, if needed
import java.util.List;
import java.util.UUID;

public class GoogleCalendarEventPojo {

  // Required properties
  private String uid; // Unique Identifier
  private ZonedDateTime dtstamp; // DateTime Stamp - when the event was created/modified
  private ZonedDateTime dtstart; // DateTime Start
  private String summary; // Event title or summary

  // Optional but common properties
  private ZonedDateTime dtend; // DateTime End
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
  private String rrule; // Recurrence rule
  private String exdate; // Exception dates
  private String rdate; // Recurrence dates
  private String url; // URL associated with the event
  private String timeZoneId; // Timezone ID (e.g., "America/New_York")
  private String notificationDetails; // Simple text for notification

  // Constructor
  public GoogleCalendarEventPojo() {
    this.uid = UUID.randomUUID().toString();
    this.dtstamp = ZonedDateTime.now(); // Default to now, can be updated
    this.sequence = 0; // Initial sequence
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

  public String getNotificationDetails() {
    return notificationDetails;
  }

  public void setNotificationDetails(String notificationDetails) {
    this.notificationDetails = notificationDetails;
  }

  // Inner class for Attendee
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
    // Basic toString for quick debugging, GUI will use more detailed formatting
    return "GoogleCalendarEvent{" +
        "uid='" + uid + '\'' +
        ", summary='" + summary + '\'' +
        ", dtstart=" + (dtstart != null ? dtstart.format(DateTimeFormatter.ISO_ZONED_DATE_TIME) : "null") +
        '}';
  }
}
