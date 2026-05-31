package xxx.com.code.formatter.json;

import com.google.gson.GsonBuilder;

public class JsonPrettyPrinter {

  public static String prettyPrint(String jsCode) {
    try {
      return new GsonBuilder().setPrettyPrinting().create().toJson(jsCode);
    } catch (Exception e) {

      System.out.println(e.getMessage());
      return jsCode;
    }
  }
}
