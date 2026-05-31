package xxx.com.gps.gui.parser.impl;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import xxx.com.gps.gui.parser.GpsParsable;

public class GPGLLParser extends GpsParsable {

  public enum GPGLL implements GpsType {

    LATITUDE,
    LATITUDE_DIRECTION,
    LONGITUDE,
    LONGITUDE_DIRECTION,
    UTC_TIME,
    STATUS,
    MODE_INDICATOR,
    CHECKSUM;

    @Override
    public String getName() {
      return name();
    }

    @Override
    public List<String> getValues() {
      return Arrays.asList(values()).stream().map(s -> s.getName()).collect(Collectors.toList());
    }

    @Override
    public int getOrdinal() {
      return ordinal();
    }
  }

  private GPGLL gpgll;

  public GPGLLParser() {}

  @Override
  public GpsType getGpsType() {
    return gpgll;
  }
}
