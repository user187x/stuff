package xxx.com.gps.nmea;

import java.util.Optional;
import org.apache.commons.lang3.StringUtils;

public class GpggaSentence implements NmeaSentence {

  private String time;
  private double latitude;
  private String latRef;
  private double longitude;
  private String lonRef;
  private int fixQuality;
  private int numSatellites;
  private double altitude;
  private String altitudeUnits;


  @Override
  public void parse(String[] data) {
    if (data.length > 1) time = data[1];
    if (data.length > 2 && !data[2].isEmpty()) latitude = parseLatitude(data[2]);
    if (data.length > 3) latRef = data[3];
    if (data.length > 4 && !data[4].isEmpty()) longitude = parseLongitude(data[4]);
    if (data.length > 5) lonRef = data[5];
    if (data.length > 6 && !data[6].isEmpty()) fixQuality = Integer.parseInt(data[6]);
    if (data.length > 7 && !data[7].isEmpty()) numSatellites = Integer.parseInt(data[7]);
    if (data.length > 9 && !data[9].isEmpty()) altitude = Double.parseDouble(data[9]);
    if (data.length > 10) altitudeUnits = data[10];
  }

  @Override
  public void print() {
    System.out.println("--- GPGGA ---");
    System.out.println("Time: " + time);
    System.out.println("Latitude: " + latitude + " " + latRef);
    System.out.println("Longitude: " + longitude + " " + lonRef);
    System.out.println("Fix Quality: " + fixQuality);
    System.out.println("Number of Satellites: " + numSatellites);
    String altitudeFt = Optional.of(altitudeUnits)
        .filter(unit -> unit.equalsIgnoreCase("M"))
        .map(unit -> altitude * 3.28084)
        .map(value -> "Altitude : " + Math.round(value) + " Ft")
        .orElse("Altitude : " + altitude + " " + altitudeUnits);
    System.out.println(altitudeFt);
  }

  private double parseLatitude(String lat) {
    double degrees = Double.parseDouble(lat.substring(0, 2));
    double minutes = Double.parseDouble(lat.substring(2)) / 60.0;
    return degrees + minutes;
  }

  private double parseLongitude(String lon) {
    double degrees = Double.parseDouble(lon.substring(0, 3));
    double minutes = Double.parseDouble(lon.substring(3)) / 60.0;
    return -(degrees + minutes);
  }
}
