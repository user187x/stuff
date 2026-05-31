package xxx.service;

import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ToolService {

  private static final Logger log = LoggerFactory.getLogger(ToolService.class);

  private final Map<String, Resource> resourceMap = new HashMap<String, Resource>();

  @Tool(name = "get_app_version", description = "Returns the application version")
  public String getAppVersion() {
    return "1.0.0";
  }

  @Tool(
      name = "get_available_external_web_sources",
      description = "Gets a list of available external webservices to query")
  public List<String> getAvailableSources() {
    return resourceMap.keySet().stream().toList();
  }

  public static void main(String[] args) {

    ToolService toolService = new ToolService();
    toolService.queryWeb("What happened today in the news?");
  }

  @Tool(name = "query_web", description = "Queries Google for additional answers")
  public String queryWeb(String query) {

    try (HttpClient client = HttpClient.newHttpClient()) {

      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(
                  URI.create(
                      "https://www.google.com/search?q="
                          + URLEncoder.encode(query, StandardCharsets.UTF_8)))
              .header(
                  "User-Agent",
                  "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.36")
              .build();

      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

      return response.body();
    } catch (Exception e) {

      log.error("Error querying Google", e);
      return "Error querying Google: " + e.getMessage();
    }
  }

  @Tool(name = "get_local_time", description = "Get the actual local time")
  public String getTime() {
    return LocalDate.now().toString();
  }

  @Tool(name = "get_local_area_name", description = "Get the name of the local area")
  public String getLocalArea() {
    return "Baltimore, Maryland";
  }

  @Tool(name = "get_users_name", description = "Returns the users name")
  public String getMyName() {
    return "Grey";
  }

  @PostConstruct
  public void init() {
    resourceMap.put("Google", new Resource("Google", "https://www.google.com"));
  }

  public static record Resource(String title, String url) {}
}
