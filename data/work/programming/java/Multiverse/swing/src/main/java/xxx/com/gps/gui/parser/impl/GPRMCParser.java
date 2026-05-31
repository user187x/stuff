package xxx.com.gps.gui.parser.impl;

import xxx.com.gps.gui.parser.GpsParsable;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class GPRMCParser extends GpsParsable {

  public enum GPRMC implements GpsType {

    UTC_TIME,
    STATUS,
    LATITUDE,
    LATITUDE_DIRECTION,
    LONGITUDE,
    LONGITUDE_DIRECTION,
    SPEED_OVER_GROUND,
    COURSE_OVER_GROUND,
    DATE,
    MAGNETIC_VARIATION,
    MAGNETIC_VARIATION_DIRECTION,
    MODE_INDICATOR,
    CHECKSUM;

    @Override
    public String getName() {
      return name();
    }

    @Override
    public List<String> getValues() {
      return Arrays.stream(values()).map(GPRMC::getName).collect(Collectors.toList());
    }

    @Override
    public int getOrdinal() {
      return ordinal();
    }
  }

  private GPRMC gprmc;

  public GPRMCParser() {}

  @Override
  public GpsType getGpsType() {
    return gprmc;
  }
}
