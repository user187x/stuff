package xxx.com.gps.gui.parser.impl;

import xxx.com.gps.gui.parser.GpsParsable;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class GPGSAParser extends GpsParsable {

  public enum GPGSA implements GpsType {

    MODE1,
    MODE2,
    SV_ID_1,
    SV_ID_2,
    SV_ID_3,
    SV_ID_4,
    SV_ID_5,
    SV_ID_6,
    SV_ID_7,
    SV_ID_8,
    SV_ID_9,
    SV_ID_10,
    SV_ID_11,
    SV_ID_12,
    PDOP,
    HDOP,
    VDOP,
    CHECKSUM;

    @Override
    public String getName() {
      return name();
    }

    @Override
    public List<String> getValues() {
      return Arrays.stream(values()).map(GPGSA::getName).collect(Collectors.toList());
    }

    @Override
    public int getOrdinal() {
      return ordinal();
    }
  }

  private GPGSA gpgsa;

  public GPGSAParser() {}

  @Override
  public GpsType getGpsType() {
    return gpgsa;
  }
}
