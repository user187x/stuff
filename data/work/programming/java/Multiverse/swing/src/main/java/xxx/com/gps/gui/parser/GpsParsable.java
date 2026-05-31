package xxx.com.gps.gui.parser;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.reflections.Reflections;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public abstract class GpsParsable {

  private static Map<String, GpsParsable> parserMap = getAllParsers();

  private Gson gson = new GsonBuilder().setPrettyPrinting().create();

  private Map<GpsType, String> fieldValueMap = new HashMap<>();

  public interface GpsType {

    public String getName();

    public int getOrdinal();

    public List<String> getValues();
  }

  public abstract GpsType getGpsType();

  public List<String> getFields() {
    return getGpsType().getValues();
  }

  public String getTypeName() {
    return getGpsType().getName();
  }

  public int getOrdinal() {
    return getGpsType().getOrdinal();
  }

  public String getValue(GpsType field) {
    return fieldValueMap.get(field);
  }

  @Override
  public String toString() {
    return gson.toJson(fieldValueMap);
  }

  public static List<String> toRawMessages(byte[] bytes) {

    String raw = new String(bytes, StandardCharsets.UTF_8);
    return Arrays.asList(raw.split(Message.delimiter()));
  }

  public static List<String> segmentSplit(String message) {

    String[] messageParts = message.split(Message.DELIMITER);
    return Arrays.asList(messageParts);
  }

  public static List<Map<String, String>> process(byte[] data) {

    List<Map<String, String>> messages = new ArrayList<>();

    for (String message : toRawMessages(data)) {

      String messageType = Message.getNormalizedMessageId(message);
      GpsParsable gpsParser = parserMap.getOrDefault(messageType, null);

      List<String> messageParts = segmentSplit(messageType);
      GpsType gpsType = gpsParser.getGpsType();

      Map<String, String> entry =
          IntStream.range(0, gpsType.getValues().size())
              .mapToObj(
                  index -> {
                    String field = gpsType.getValues().get(index);
                    String value = messageParts.get(index);

                    return Map.entry(field, value);
                  })
              .collect(Collectors.toMap(Entry::getKey, Entry::getValue));

      messages.add(entry);
    }

    return messages;
  }

  public static Map<String, GpsParsable> getAllParsers() {

    Map<String, GpsParsable> parserTypeMap = new HashMap<>();

    try {

      Reflections reflections = new Reflections("xxx.com.gps.parser.impl");

      for (Class<? extends GpsParsable> clazz : reflections.getSubTypesOf(GpsParsable.class)) {

        GpsParsable gpsParsable = clazz.getDeclaredConstructor().newInstance();
        String typeName = gpsParsable.getTypeName();

        parserTypeMap.put(typeName, gpsParsable);

        System.out.println("Added GPS Parser : " + typeName);
      }
    } catch (Exception e) {

      System.out.println("Failure Instantiating implementation : " + e.getMessage());
    }

    return parserTypeMap;
  }

  public static final class Message {

    public static final int ID_IDX = 0;
    public static final String DELIMITER = ",";
    public static final String DEMARCATOR = "$";
    public static final String ENTRY_MARKER = "\\R";

    public static int identityIndex() {
      return ID_IDX;
    }

    public static String delimiter() {
      return DELIMITER;
    }

    public static String demarcator() {
      return DEMARCATOR;
    }

    public static String sentenceMarker() {
      return ENTRY_MARKER;
    }

    public static String toMessageType(GpsType enumType) {
      return demarcator().concat(((Enum<?>) enumType).name());
    }

    public static String getMessageId(String entry) {
      return entry.split(Message.delimiter())[Message.identityIndex()];
    }

    public static String getNormalizedMessageId(String entry) {
      return getMessageId(entry).substring(1);
    }
  }
}
