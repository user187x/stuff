package xxx.com.gps.gui;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListener;
import com.fazecast.jSerialComm.SerialPortEvent;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Stream;

public class GPSReader {

  public enum FixStatus {

    INVALID(0),
    STANDARD(1),
    DIFFERENTIAL(2),
    PRECISE_POSITIONING(3),
    REAL_TIME_KINEMATIC(4),
    FLOAT_REAL_TIME_KINEMATIC(5),
    ESTIMATED_DEAD_RECKONING(6);

    private int fixQuality;

    FixStatus(int fixQuality) {
      this.fixQuality = fixQuality;
    }

    public int getFixQuality() {
      return fixQuality;
    }

    public static FixStatus of(int value) {

      return Stream.of(FixStatus.values())
          .filter(s -> s.getFixQuality() == value)
          .findAny()
          .orElse(INVALID);
    }
  }

  public enum NMEAType {

    GPTXT(true, 4), GPVTG(false, 12), GPGGA(true, 16), GPGSA(false, 18), GPGSV(false, 21),
    GPGLL(false, 9), GPRMC(false, 14);

    private boolean enabled;
    private int fields;

    private NMEAType(boolean enabled, int fields) {
      this.enabled = enabled;
      this.fields = fields;
    }

    public boolean isEnabled() {
      return enabled;
    }

    public int getTotalFields() {
      return fields;
    }

    public static Optional<NMEAType> of(String raw) {

      return Stream.of(NMEAType.values())
          .filter(sf -> raw.startsWith("$" + sf.name()))
          .findAny();
    }
  }

  public static void runTest(){

    System.out.println("--- Starting GPS Data Simulation ---");

    // Convert the test string to a byte array, which is what the processData method expects.
    byte[] testDataBytes = SIMULATED_GPS_DATA.getBytes(StandardCharsets.UTF_8);

    // Call the processData method from the GPSReader class to parse the simulated data.
    GPSReader.processData(testDataBytes);

    System.out.println("\n--- GPS Data Simulation Finished ---");
    System.exit(0);
  }


  public static void main(String[] args) {

    boolean testMode = true;

    if(testMode) {
      runTest();
    }

    Optional<SerialPort> optional = searchCommPort();

    if (optional.isEmpty()) {
      System.out.println("No GPS Receiver found.");
      return;
    }

    SerialPort comPort = optional.get();
    comPort.openPort();

    comPort.addDataListener(new SerialPortDataListener() {

      @Override
      public int getListeningEvents() {
        return SerialPort.LISTENING_EVENT_DATA_RECEIVED;
      }

      @Override
      public void serialEvent(SerialPortEvent event) {
        processData(event.getReceivedData());
      }
    });
  }

  private static void processData(byte[] bytes) {

    String data = new String(bytes, StandardCharsets.UTF_8);

    try (Scanner scanner = new Scanner(data)) {

      while (scanner.hasNextLine()) {
        parse(scanner.nextLine());
      }
    }
    catch (Exception exception) {
      System.out.println("Failure processing GPS Data " + exception.getMessage());
    }
  }

  private static Optional<SerialPort> searchCommPort() {

    List<SerialPort> availableCommPorts = Arrays.asList(SerialPort.getCommPorts());

    return availableCommPorts.stream().peek(s -> System.out.println("Searching Serial Ports..."))
        .filter(s -> s.getDescriptivePortName().contains("GPS"))
        .peek(s -> System.out.println("GPS Receiver Found : " + s.getSystemPortName()))
        .peek(s -> System.out.println("Device : " + s.getDescriptivePortName().toUpperCase()))
        .findAny();
  }

  /**
   * A string containing sample NMEA sentences to be used for testing.
   * This data mimics the output of a real GPS receiver.
   */
  private static final String SIMULATED_GPS_DATA =
      """
          $GPGGA,011347.00,3917.42341,N,07636.72163,W,1,09,1.01,44.9,M,-33.3,M,,*51\r
          $GPRMC,011347.00,A,3917.42341,N,07636.72163,W,0.052,,280825,,,A*73\r
          $GPGSV,3,1,12,01,00,000,,02,39,147,30,03,08,233,26,05,43,296,32*7B\r
          $GPGSV,3,2,12,08,01,053,,11,24,078,24,14,41,194,34,16,12,311,22*7E\r
          $GPGSV,3,3,12,21,38,036,29,24,07,115,22,25,23,323,28,31,01,188,*40\r
          $GPTXT,01,01,02,u-blox AG - www.u-blox.com*50\r
          $GPTXT,01,01,02,HW UBX-G7020-KT 00070000*14\r
          $GPTXT,01,01,02,ROM CORE 1.00 (59573) Jun 27 2012 17:43:52*45\r
          """;

  private static void parse(String line) {

    Optional<NMEAType> optional = NMEAType.of(line);

    if (optional.isEmpty())
      return;

    Stack<String> data = new Stack<>();
    data.addAll(Arrays.asList(line.split(",")));

    switch (optional.get()) {

      case GPTXT:
        readGPTXT(data);
        break;
      case GPGSV:
        readGPGSV(data);
        break;
      case GPGGA:
        readGPGGA(data);
        break;
      case GPRMC:
        readGPRMC(data);
        break;
      case GPGLL:
        // Not implemented
        break;
      case GPGSA:
        // Not implemented
        break;
      case GPVTG:
        // Not implemented
        break;
      default:
        break;
    }
  }

  private static void readGPTXT(Stack<String> stack) {
    System.out.println("GPTXT: " + stack);
  }

  private static void readGPGGA(Stack<String> stack) {

    if (stack.size() > 1) {

      String locationTime = stack.get(1);
      System.out.println("Time : " + convertTime(locationTime));
    }

    if (stack.size() > 2) {

      String hemisphere = Optional.of(stack)
          .filter(s -> s.size() > 3)
          .map(s -> s.get(3))
          .filter(StringUtils::isNotEmpty)
          .map(cord -> " (" + cord + ")")
          .orElse(StringUtils.EMPTY);

      double latitude = parseLatitude(stack.get(2));

      System.out.println("Latitude : " + latitude + hemisphere);
    }

    if (stack.size() > 4) {

      String hemisphere = Optional.of(stack)
          .filter(s -> s.size() > 5)
          .map(s -> s.get(5))
          .filter(StringUtils::isNotEmpty)
          .map(cord -> " (" + cord + ")")
          .orElse(StringUtils.EMPTY);

      double longitude = parseLongitude(stack.get(4));
      System.out.println("Longitude : " + longitude + hemisphere);
    }

    if (stack.size() > 6) {

      int fixValue = Integer.parseInt(stack.get(6));
      FixStatus fixStatus = FixStatus.of(fixValue);

      if (fixStatus == FixStatus.INVALID) {
        System.out.println("No GPS Lock");
      }
      else {
        System.out.println("GPS Lock : " + fixStatus.getFixQuality());
      }
    }

    if (stack.size() > 7) {

      int numSats = Integer.parseInt(stack.get(7));
      System.out.println("Number of Satellites: " + numSats);
    }

    if (stack.size() > 8) {

      Double horizontalPrecision = Double.parseDouble(stack.get(8));
      System.out.println("Horizontal Dilution of Precision (HDOP): " + horizontalPrecision);
    }

    if (stack.size() > 9) {

      Double altitude = Double.parseDouble(stack.get(9));
      System.out.println("Altitude: " + altitude);
    }

    if (stack.size() > 10) {

      String altitudeUnits = stack.get(10);
      System.out.println("Altitude Units: " + altitudeUnits);

      double altitude = Double.parseDouble(stack.get(9));

      String altitudeFt = Optional.of(altitudeUnits)
          .filter(unit -> unit.equalsIgnoreCase("M"))
          .map(unit -> altitude * 3.28084)
          .map(value -> "Altitude : " + Math.round(value) + " Ft")
          .orElse("Altitude : " + altitudeUnits);

      System.out.println(altitudeFt);
    }

    if (stack.size() > 13) {

      String timeSince = stack.get(13);
      System.out.println("Time Since Last DGPS Updae: " + timeSince);

    }
    if (stack.size() > 15) {

      String checkSum = stack.get(15);
      System.out.println("Checksum: " + checkSum);
    }
  }

  private static void readGPGSV(Stack<String> stack) {

    if (stack.size() > 3) {

      int numSatelites = Integer.parseInt(stack.get(3));

      try {

        for (int i = 0; i < numSatelites; i++) {

          int sateliteIndex = 4 + i * 4;
          int signalIndex = 7 + i * 4;

          if (stack.size() > signalIndex) {
            String sateliteId = stack.get(sateliteIndex);
            String snr_value = stack.get(signalIndex);

            if (NumberUtils.isParsable(snr_value)) {
              int snr = Integer.parseInt(snr_value);
              String value = String.format("%02d", snr);

              String strength;

              if (snr < 20)
                strength = "WEAK";
              else if (snr >= 20 && snr < 30)
                strength = "FAIR";
              else if (snr >= 30)
                strength = "GOOD";
              else
                strength = "UNKNOWN";

              System.out
                  .println("→ Satelite ID: " + sateliteId + " SNR: " + value + "db ► " + strength);
            }
          }
        }
      }
      catch (Exception e) {
        System.err.println("Error parsing GPGSV: " + e.getMessage());
      }
    }
  }

  private static void readGPRMC(Stack<String> stack) {

    if (stack.size() > 7) {

      if (NumberUtils.isNumber(stack.get(7))) {

        double speedKnots = Double.parseDouble(stack.get(7));
        double speedMph = 1.150779 * speedKnots;

        long mph = Optional.of(speedMph)
            .filter(speed -> speed < 2)
            .map(speed -> 0L)
            .orElse(Math.round(speedMph));

        System.out.println("Speed : " + mph + " MPH");
      }
    }

    if (stack.size() > 8 && StringUtils.isNotBlank(stack.get(8))) {
      System.out.println("Course (degrees): " + stack.get(8));
    }

    if (stack.size() > 9 && StringUtils.isNotBlank(stack.get(9))) {
      System.out.println("Date: " + convertDate(stack.get(9)));
    }
  }

  private static String convertDate(String value) {

    SimpleDateFormat sdf = new SimpleDateFormat("ddMMyy");

    try {

      Date date = sdf.parse(value);

      Instant instant = date.toInstant();
      ZoneId zone = ZoneId.systemDefault();

      ZonedDateTime zonedDateTime = instant.atZone(zone);
      DateTimeFormatter dtf =
          DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss z yyyy", Locale.ENGLISH);

      return dtf.format(zonedDateTime);
    }
    catch (Exception e) {

      System.out.println("Failure converting date " + e.getMessage());
    }

    return value;
  }

  private static String convertTime(String epoch) {

    try {

      Long time = Math.round(Double.valueOf(epoch));
      String formatted = String.format("%06d", time);

      DateTimeFormatter dtf = DateTimeFormatter.ofPattern("HHmmss");
      LocalTime localTime = LocalTime.parse(formatted, dtf);

      LocalDate localDate = LocalDate.now();
      LocalDateTime localTimeDate = LocalDateTime.of(localDate, localTime);
      ZonedDateTime zdt = localTimeDate.atZone(ZoneId.systemDefault());

      DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");

      return zdt.minusHours(5).format(formatter);
    }
    catch (Exception e) {

      System.out.println("Failure converting Time : " + epoch);

      return StringUtils.EMPTY;
    }
  }

  private static double parseLatitude(String lat) {
    if (StringUtils.isBlank(lat) || lat.length() < 2) return 0.0;
    double degrees = Double.parseDouble(lat.substring(0, 2));
    double minutes = Double.parseDouble(lat.substring(2)) / 60.0;
    return degrees + minutes;
  }

  private static double parseLongitude(String lon) {
    if (StringUtils.isBlank(lon) || lon.length() < 3) return 0.0;
    double degrees = Double.parseDouble(lon.substring(0, 3));
    double minutes = Double.parseDouble(lon.substring(3)) / 60.0;
    return -(degrees + minutes);
  }
}
