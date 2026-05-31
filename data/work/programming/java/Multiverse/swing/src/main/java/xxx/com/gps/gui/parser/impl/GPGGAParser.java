package xxx.com.gps.gui.parser.impl;

import xxx.com.gps.gui.parser.GpsParsable;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class GPGGAParser extends GpsParsable {

  public enum GPGGA implements GpsType {

    UTC_TIME,
    LATITUDE,
    LATITUDE_DIRECTION,
    LONGITUDE,
    LONGITUDE_DIRECTION,
    GPS_QUALITY_INDICATOR,
    NUMBER_OF_SATELLITES,
    HORIZONTAL_DILUTION_OF_PRECISION,
    ALTITUDE,
    ALTITUDE_UNITS,
    GEOIDAL_SEPARATION,
    GEOIDAL_SEPARATION_UNITS,
    AGE_OF_DATA_CORRECTIONS,
    DIFFERENTIAL_REFERENCE_STATION_ID,
    CHECKSUM;

    @Override
    public String getName() {
      return name();
    }

    @Override
    public List<String> getValues() {
      return Arrays.stream(values()).map(Enum::name).collect(Collectors.toList());
    }

    @Override
    public int getOrdinal() {
      return ordinal();
    }
  }

  private GPGGA gpgga = GPGGA.UTC_TIME; // Initialized the field

  public GPGGAParser() {}

  @Override
  public GpsType getGpsType() {
    return gpgga;
  }
}
