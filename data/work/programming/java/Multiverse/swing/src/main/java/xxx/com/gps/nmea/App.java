package xxx.com.gps.nmea;

import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class App {

  public static void main(String... args) {

    ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    executor.scheduleAtFixedRate(App::generateMockData, 0, 1, TimeUnit.SECONDS);
  }

  private static void generateMockData() {
    // You can add more mock data here to test other sentence types
    String[] nmeaSentences = {
        "$GPGGA,123519,4807.038,N,01131.000,E,1,08,0.9,545.4,M,46.9,M,,*47",
        "$GPRMC,123519,A,4807.038,N,01131.000,E,022.4,084.4,230394,003.1,W*6A",
        "$GPGSV,2,1,08,01,40,083,46,02,17,308,41,12,07,344,39,14,22,228,45*75",
        "$GPTXT,01,01,02,u-blox AG - www.u-blox.com*5C"
    };

    Arrays.stream(nmeaSentences).forEach(System.out::println);
  }
}


