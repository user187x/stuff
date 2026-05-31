package xxx.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

@Service
public class Junk {

  // Define ShoppingItem as a nested record
  public record JunkItem(String name, int quantity) {}

  // Use a ConcurrentHashMap to store the shopping list items in memory
  private final Map<String, JunkItem> junklist = new ConcurrentHashMap<>();

  @Tool(name = "getJunk", description = "Get all junk details")
  public List<JunkItem> getJunk() {
    return new ArrayList<>(junklist.values());
  }
}
