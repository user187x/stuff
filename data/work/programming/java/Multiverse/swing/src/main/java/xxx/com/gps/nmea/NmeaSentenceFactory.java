package xxx.com.gps.nmea;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class NmeaSentenceFactory {

  private static final Map<String, Class<? extends NmeaSentence>> sentenceHandlers = new HashMap<>();

  static {
    sentenceHandlers.put("$GPGGA", GpggaSentence.class);
    sentenceHandlers.put("$GPRMC", GprmcSentence.class);
    sentenceHandlers.put("$GPGSV", GpgsvSentence.class);
    sentenceHandlers.put("$GPTXT", GptxtSentence.class);
  }

  public static Optional<NmeaSentence> createSentence(String sentence) {
    if (sentence == null || sentence.trim().isEmpty()) {
      return Optional.empty();
    }

    String[] parts = sentence.split(",");
    String sentenceId = parts[0];

    if (sentenceHandlers.containsKey(sentenceId)) {
      try {
        NmeaSentence nmeaSentence = sentenceHandlers.get(sentenceId).getDeclaredConstructor().newInstance();
        nmeaSentence.parse(parts);
        return Optional.of(nmeaSentence);
      } catch (Exception e) {
        System.err.println("Error creating sentence handler for " + sentenceId + ": " + e.getMessage());
      }
    }

    return Optional.empty();
  }
}
