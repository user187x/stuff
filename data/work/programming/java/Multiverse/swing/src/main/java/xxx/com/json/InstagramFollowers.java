package xxx.com.json;

import java.io.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class InstagramFollowers {

  public static void main(String[] args) throws FileNotFoundException {

    /** TODO Add the appropriate files here*/
    File current = null;
    File previous = null;

    Set<String> previousFollowers = getPreviousFollowers(previous);
    System.out.println("Previous Total Followers : " + previousFollowers.size());

    Map<String, String> currentFollowers = getCurrentFollowers(current);
    System.out.println("Current Total Followers  : " + currentFollowers.size());

    Set<String> missingFollowers = new HashSet<String>(previousFollowers);
    missingFollowers.removeAll(currentFollowers.keySet());

    System.out.println("");
    System.out.println("Removed Followers : " + missingFollowers.size());
    System.out.println("=====================");
    System.out.println("");

    int counter = 0;
    for (String name : missingFollowers) {

      counter++;
      System.out.println(counter + ". " + name);
    }

    Set<String> newFollowers = new HashSet<String>(currentFollowers.keySet());
    newFollowers.removeAll(previousFollowers);

    System.out.println("");
    System.out.println("Added Followers : " + newFollowers.size());
    System.out.println("====================");
    System.out.println("");

    counter = 0;
    for (String name : newFollowers) {

      counter++;
      System.out.println(counter + ". " + name);
    }
  }

  public static Map<String, String> getCurrentFollowers(File file) {

    JsonElement jsonElement = null;

    try (FileInputStream fis = new FileInputStream(file)) {
      jsonElement = JsonParser.parseReader(new InputStreamReader(fis));
    } catch (Exception e) {

      System.out.println(e.getMessage());
      System.exit(1);
    }
    Map<String, String> followers = new HashMap<>();

    for (JsonElement element : jsonElement.getAsJsonArray()) {

      JsonObject jsonObject = element.getAsJsonObject();

      JsonArray entryData = jsonObject.getAsJsonArray("string_list_data");
      JsonObject entry = entryData.get(0).getAsJsonObject();

      String name = entry.get("value").getAsString();
      String href = entry.get("href").getAsString();

      followers.put(name, href);
    }

    return followers;
  }

  public static Set<String> getPreviousFollowers(File file) {

    Set<String> names = new HashSet<>();

    try(Scanner scanner = new Scanner(file)) {
      while (scanner.hasNextLine()) names.add(scanner.nextLine());
    }
    catch (FileNotFoundException e) {
      throw new RuntimeException(e);
    }

    return names;
  }
}
