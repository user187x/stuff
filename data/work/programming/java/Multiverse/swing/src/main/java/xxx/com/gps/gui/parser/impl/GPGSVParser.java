package xxx.com.gps.gui.parser.impl;

import xxx.com.gps.gui.parser.GpsParsable;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class GPGSVParser extends GpsParsable {

  public enum GPGSV implements GpsType {

    TOTAL_MESSAGES,
    MESSAGE_NUMBER,
    SATELLITES_IN_VIEW,
    SATELLITE_PRN_1,
    ELEVATION_1,
    AZIMUTH_1,
    SNR_1,
    SATELLITE_PRN_2,
    ELEVATION_2,
    AZIMUTH_2,
    SNR_2,
    SATELLITE_PRN_3,
    ELEVATION_3,
    AZIMUTH_3,
    SNR_3,
    SATELLITE_PRN_4,
    ELEVATION_4,
    AZIMUTH_4,
    SNR_4,
    CHECKSUM;

    @Override
    public String getName() {
      return name();
    }

    @Override
    public List<String> getValues() {
      return Arrays.stream(values()).map(GPGSV::getName).collect(Collectors.toList());
    }

    @Override
    public int getOrdinal() {
      return ordinal();
    }
  }

  private GPGSV gpgsv;

  public GPGSVParser() {}

  @Override
  public GpsType getGpsType() {
    return gpgsv;
  }
}
