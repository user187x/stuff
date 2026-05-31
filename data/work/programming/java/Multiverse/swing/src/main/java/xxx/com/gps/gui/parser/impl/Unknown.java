package xxx.com.gps.gui.parser.impl;

import java.util.List;
import xxx.com.gps.gui.parser.GpsParsable;

public class Unknown extends GpsParsable {

  @Override
  public GpsType getGpsType() {
    return new GpsType() {

      @Override
      public List<String> getValues() {
        return List.of();
      }

      @Override
      public int getOrdinal() {
        return 0;
      }

      @Override
      public String getName() {
        return "UNKNOWN";
      }
    };
  }
}
