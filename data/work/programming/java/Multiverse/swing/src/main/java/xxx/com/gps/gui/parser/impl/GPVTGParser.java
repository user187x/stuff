package xxx.com.gps.gui.parser.impl;

import xxx.com.gps.gui.parser.GpsParsable;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class GPVTGParser extends GpsParsable {

  public enum GPVTG implements GpsType {

    TRACK_TRUE,
    TRACK_TRUE_UNITS,
    TRACK_MAGNETIC,
    TRACK_MAGNETIC_UNITS,
    SPEED_KNOTS,
    SPEED_KNOTS_UNITS,
    SPEED_KILOMETERS,
    SPEED_KILOMETERS_UNITS,
    MODE_INDICATOR,
    CHECKSUM;

    @Override
    public String getName() {
      return name();
    }

    @Override
    public List<String> getValues() {
      return Arrays.stream(values()).map(GPVTG::getName).collect(Collectors.toList());
    }

    @Override
    public int getOrdinal() {
      return ordinal();
    }
  }

  private GPVTG gpvtg;

  public GPVTGParser() {}

  @Override
  public GpsType getGpsType() {
    return gpvtg;
  }
}
