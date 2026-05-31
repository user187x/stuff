package xxx.com.gps.gui.parser.impl;

import xxx.com.gps.gui.parser.GpsParsable;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class GPTXTParser extends GpsParsable {

  public enum GPTXT implements GpsType {

    MESSAGE_ID,
    MESSAGE_NUMBER,
    TOTAL_MESSAGES,
    TEXT_MESSAGE,
    CHECKSUM;

    @Override
    public String getName() {
      return name();
    }

    @Override
    public List<String> getValues() {
      return Arrays.stream(values()).map(GPTXT::getName).collect(Collectors.toList());
    }

    @Override
    public int getOrdinal() {
      return ordinal();
    }
  }

  private GPTXT gptxt;

  public GPTXTParser() {}

  @Override
  public GpsType getGpsType() {
    return gptxt;
  }
}
