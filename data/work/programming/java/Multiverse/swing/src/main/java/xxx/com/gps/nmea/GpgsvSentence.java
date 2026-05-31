package xxx.com.gps.nmea;

public class GpgsvSentence implements NmeaSentence {

  private int numSatellites;
  private String[] rawData;

  @Override
  public void parse(String[] data) {
    this.rawData = data;
    if (data.length > 3) {
      numSatellites = Integer.parseInt(data[3]);
    }
  }

  @Override
  public void print() {
    System.out.println("--- GPGSV ---");
    System.out.println("Number of Satellites in view: " + numSatellites);
    try {
      for (int i = 0; i < numSatellites; i++) {
        int satelliteIndex = 4 + i * 4;
        int signalIndex = 7 + i * 4;

        if (signalIndex < rawData.length) {
          String satelliteId = rawData[satelliteIndex];
          String snr_value = rawData[signalIndex];

          int snr = Integer.parseInt(snr_value);
          String value = String.format("%02d", snr);
          String strength;

          if (snr < 20) strength = "WEAK";
          else if (snr >= 20 && snr < 30) strength = "FAIR";
          else if (snr >= 30) strength = "GOOD";
          else strength = "UNKNOWN";

          System.out.println("→ Satellite ID: " + satelliteId + " SNR: " + value + "db ► " + strength);
        }
      }
    } catch (Exception e) {
      // Ignoring exceptions for mock data parsing
    }
  }
}
