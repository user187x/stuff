package xxx.com.datetime;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class UniversalDateTimeParser {

  // A list to hold common date and time format patterns.
  private static final List<DateTimeFormatter> formatters = new ArrayList<>();

  /**
   * The static block initializes the list of formatters.
   * More formats can be added here to expand parsing capabilities.
   */
  static {
    // Common ISO formats (highly recommended)
    formatters.add(DateTimeFormatter.ISO_ZONED_DATE_TIME);
    formatters.add(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    formatters.add(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    formatters.add(DateTimeFormatter.ISO_LOCAL_DATE);
    formatters.add(DateTimeFormatter.RFC_1123_DATE_TIME);

    // Custom patterns (most common first)
    // Added a new pattern to support the custom flight time format after transformation.
    formatters.add(DateTimeFormatter.ofPattern("h:mm a M/d/yyyy Z"));
    formatters.add(DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm:ss"));
    formatters.add(DateTimeFormatter.ofPattern("MM/dd/yyyy"));
    formatters.add(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss"));
    formatters.add(DateTimeFormatter.ofPattern("dd-MM-yyyy"));
    formatters.add(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    formatters.add(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ"));
    formatters.add(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    formatters.add(DateTimeFormatter.ofPattern("MMMM dd, yyyy h:mm:ss a"));
    formatters.add(DateTimeFormatter.ofPattern("MMMM dd, yyyy"));
    formatters.add(DateTimeFormatter.ofPattern("dd MMM yyyy"));
    formatters.add(DateTimeFormatter.ofPattern("E, MMM dd yyyy HH:mm:ss z"));
  }

  /**
   * Attempts to parse a date string from various formats, including Unix epoch
   * and a special "UTC-X" format for flights.
   *
   * @param dateString The string representation of the date to parse.
   * @return A ZonedDateTime object if parsing is successful, otherwise null.
   */
  public static ZonedDateTime parseDate(String dateString) {
    if (dateString == null || dateString.trim().isEmpty()) {
      return null;
    }

    // --- NEW: Special handling for "h:mm a M/d/yyyy UTC-X" format ---
    if (dateString.contains(" UTC")) {
      try {
        String[] parts = dateString.split(" UTC");
        if (parts.length == 2) {
          String dateTimePart = parts[0].trim();
          String offsetPart = parts[1].trim(); // e.g., "-4" or "+7"
          int offsetHours = Integer.parseInt(offsetPart);

          // Transform the offset into a standard format like "-0400" or "+0700"
          String formattedOffset = String.format("%+03d00", offsetHours);
          String transformedString = dateTimePart + " " + formattedOffset;

          // Use a specific formatter for this transformed string
          DateTimeFormatter customFormatter = DateTimeFormatter.ofPattern("h:mm a M/d/yyyy Z");
          return ZonedDateTime.parse(transformedString, customFormatter);
        }
      } catch (Exception e) {
        // If this special parsing fails, we'll ignore the error and
        // fall through to the standard parsing logic below.
      }
    }

    // First, try to parse it as a Unix epoch timestamp (seconds or milliseconds)
    try {
      long epoch = Long.parseLong(dateString);
      Instant instant;
      // Heuristic: if length > 10, it's likely milliseconds.
      if (dateString.length() > 10) {
        instant = Instant.ofEpochMilli(epoch);
      } else {
        instant = Instant.ofEpochSecond(epoch);
      }
      return ZonedDateTime.ofInstant(instant, ZoneId.systemDefault());
    } catch (NumberFormatException e) {
      // It's not a simple number, so we proceed to pattern matching.
    }

    // Iterate through the list of formatters
    for (DateTimeFormatter formatter : formatters) {
      try {
        TemporalAccessor temporalAccessor = formatter.parseBest(dateString, ZonedDateTime::from, LocalDateTime::from, LocalDate::from);
        if (temporalAccessor instanceof ZonedDateTime) {
          return (ZonedDateTime) temporalAccessor;
        } else if (temporalAccessor instanceof LocalDateTime) {
          return ((LocalDateTime) temporalAccessor).atZone(ZoneId.systemDefault());
        } else {
          return ((LocalDate) temporalAccessor).atStartOfDay(ZoneId.systemDefault());
        }
      } catch (DateTimeParseException e) {
        // This pattern didn't match, continue to the next one.
      }
    }
    return null; // Return null if no format matches.
  }

  // --- Date/Time Manipulation Methods ---

  public static ZonedDateTime addDays(ZonedDateTime dateTime, long days) {
    return dateTime.plusDays(days);
  }

  public static ZonedDateTime addHours(ZonedDateTime dateTime, long hours) {
    return dateTime.plusHours(hours);
  }

  public static ZonedDateTime addMinutes(ZonedDateTime dateTime, long minutes) {
    return dateTime.plusMinutes(minutes);
  }

  public static ZonedDateTime addSeconds(ZonedDateTime dateTime, long seconds) {
    return dateTime.plusSeconds(seconds);
  }

  /**
   * Calculates the exact duration between two points in time, correctly
   * handling time zone differences.
   *
   * @param start The start date-time (e.g., departure).
   * @param end   The end date-time (e.g., arrival).
   * @return A Duration object.
   */
  public static Duration calculateDuration(ZonedDateTime start, ZonedDateTime end) {
    return Duration.between(start, end);
  }

  /**
   * Formats a Duration object into a human-readable string.
   * e.g., "2 days, 10 hours, 30 minutes, 5 seconds"
   */
  public static String formatDuration(Duration duration) {
    long days = duration.toDays();
    long hours = duration.toHoursPart();
    long minutes = duration.toMinutesPart();
    long seconds = duration.toSecondsPart();
    return String.format("%d days, %d hours, %d minutes, %d seconds", days, hours, minutes, seconds);
  }

  /**
   * The main method provides an interactive CLI to test the helper's features.
   */
  public static void main(String[] args) {
    Scanner scanner = new Scanner(System.in);
    System.out.println("Welcome to the Universal Date & Time Helper!");

    while (true) {
      System.out.println("\n--- Main Menu ---");
      System.out.println("1. Parse a Date String");
      System.out.println("2. Calculate Flight Duration (Cross-Timezone Example)");
      System.out.println("3. Exit");
      System.out.print("Choose an option: ");
      String choice = scanner.nextLine();

      if ("1".equals(choice)) {
        System.out.print("Enter a date string to parse: ");
        String input = scanner.nextLine();
        ZonedDateTime parsedDate = parseDate(input);

        if (parsedDate != null) {
          System.out.println("\n✅ Successfully parsed date:");
          System.out.println("   Standard ISO Format: " + parsedDate.format(DateTimeFormatter.ISO_ZONED_DATE_TIME));
          System.out.println("   Your System's ZoneID: " + parsedDate.getZone());
          System.out.println("   Unix Epoch (seconds): " + parsedDate.toEpochSecond());
          System.out.println("   Unix Epoch (millis):  " + parsedDate.toInstant().toEpochMilli());
        } else {
          System.out.println("❌ Could not parse the date. None of the known formats matched.");
        }
      } else if ("2".equals(choice)) {
        System.out.println("\n--- Flight Duration Calculator ---");
        System.out.println("Enter departure and arrival times. Two formats are supported:");
        System.out.println("  1. Standard ISO: 2023-10-27T10:00:00-04:00[America/New_York]");
        System.out.println("  2. Custom UTC:   7:30 PM 7/16/2025 UTC-4");

        System.out.print("\nEnter departure date/time: ");
        String departureStr = scanner.nextLine();
        ZonedDateTime departure = parseDate(departureStr);

        System.out.print("Enter arrival date/time: ");
        String arrivalStr = scanner.nextLine();
        ZonedDateTime arrival = parseDate(arrivalStr);

        if (departure != null && arrival != null) {
          Duration flightDuration = calculateDuration(departure, arrival);
          System.out.println("\n✈️ Calculated Flight Duration: " + formatDuration(flightDuration));
        } else {
          System.out.println("❌ Error parsing one or both dates. Please use a valid format.");
        }
      } else if ("3".equals(choice)) {
        break;
      } else {
        System.out.println("Invalid option. Please try again.");
      }
    }

    System.out.println("Goodbye!");
    scanner.close();
  }
}
