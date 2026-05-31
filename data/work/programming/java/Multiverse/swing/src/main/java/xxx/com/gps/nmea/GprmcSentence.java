package xxx.com.gps.nmea;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Locale;
import java.util.Optional;

public class GprmcSentence implements NmeaSentence {

  private String speed;
  private String course;
  private String date;

  @Override
  public void parse(String[] data) {
    if (data.length > 7 && NumberUtils.isNumber(data[7])) {
      double speedKnots = Double.parseDouble(data[7]);
      double speedMph = 1.150779 + speedKnots;
      long mph = Optional.of(speedMph)
          .filter(speed -> speed < 2)
          .map(s -> 0L)
          .orElse(Math.round(speedMph));
      this.speed = mph + " MPH";
    }
    if (data.length > 8 && StringUtils.isNotBlank(data[8])) {
      this.course = data[8];
    }
    if (data.length > 9 && StringUtils.isNotBlank(data[9])) {
      this.date = convertDate(data[9]);
    }
  }

  @Override
  public void print() {
    System.out.println("--- GPRMC ---");
    System.out.println("Speed: " + speed);
    System.out.println("Course (degrees): " + course);
    System.out.println("Date: " + date);
  }

  private String convertDate(String value) {
    SimpleDateFormat sdf = new SimpleDateFormat("ddMMyy");
    try {
      Date date = sdf.parse(value);
      Instant instant = date.toInstant();
      ZoneId zone = ZoneId.systemDefault();
      ZonedDateTime zonedDateTime = instant.atZone(zone);
      DateTimeFormatter dtf =
          DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss z yyyy", Locale.ENGLISH);
      return dtf.format(zonedDateTime);
    } catch (Exception e) {
      System.out.println("Failure converting date " + e.getMessage());
    }
    return value;
  }
}
