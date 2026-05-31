package xxx.com.gps.nmea;

public class GptxtSentence implements NmeaSentence {

  private String message;

  @Override
  public void parse(String[] data) {
    if (data.length > 1) {
      this.message = data[4];
    }
  }

  @Override
  public void print() {
    System.out.println("--- GPTXT ---");
    System.out.println("Message: " + message);
  }
}
