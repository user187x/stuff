package xxx.com.gps.nmea;

public interface NmeaSentence {

  void parse(String[] data);

  void print();
}
