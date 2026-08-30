package mcp.support;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.jsoup.Jsoup;
import jdk.jshell.JShell;
import jdk.jshell.SnippetEvent;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Set;

public class ToolHandler {

  private final HttpClient httpClient;
  private final BrowserHandler browserHelper;
  // Track the current coding directory
  private String currentProjectDirectory = System.getProperty("user.dir");

  public ToolHandler() {
    this.httpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(10))
        .build();
    this.browserHelper = new BrowserHandler();
  }

  public JsonObject getToolsList(Set<String> disabledTools) {
    JsonObject result = new JsonObject();
    JsonArray tools = new JsonArray();

    // 1. Web Fetch Tool
    if (!disabledTools.contains("web_fetch")) {
      JsonObject webFetchTool = new JsonObject();
      webFetchTool.addProperty("name", "web_fetch");
      webFetchTool.addProperty("description", "Fetch content from a URL and process it.");

      JsonObject webSchema = new JsonObject();
      webSchema.addProperty("type", "object");

      JsonObject webProps = new JsonObject();
      JsonObject urlProp = new JsonObject();
      urlProp.addProperty("type", "string");
      urlProp.addProperty("description", "The URL to fetch content from");
      webProps.add("url", urlProp);

      JsonObject promptProp = new JsonObject();
      promptProp.addProperty("type", "string");
      promptProp.addProperty("description", "Instructions for extracting info");
      webProps.add("prompt", promptProp);

      JsonObject formatProp = new JsonObject();
      formatProp.addProperty("type", "string");
      formatProp.addProperty("description", "auto, markdown, html, or text");
      webProps.add("format", formatProp);

      webSchema.add("properties", webProps);

      JsonArray webRequired = new JsonArray();
      webRequired.add("url");
      webRequired.add("prompt");
      webSchema.add("required", webRequired);

      webFetchTool.add("inputSchema", webSchema);
      tools.add(webFetchTool);
    }

    // 2. Execute Java Tool
    if (!disabledTools.contains("execute_java")) {
      JsonObject javaTool = new JsonObject();
      javaTool.addProperty("name", "execute_java");
      javaTool.addProperty("description", "Execute raw Java code snippets dynamically and return the console output or evaluation result.");

      JsonObject javaSchema = new JsonObject();
      javaSchema.addProperty("type", "object");

      JsonObject javaProps = new JsonObject();
      JsonObject codeProp = new JsonObject();
      codeProp.addProperty("type", "string");
      codeProp.addProperty("description", "The Java code snippet to execute. E.g., java.time.LocalDateTime.now();");
      javaProps.add("code", codeProp);

      javaSchema.add("properties", javaProps);

      JsonArray javaRequired = new JsonArray();
      javaRequired.add("code");
      javaSchema.add("required", javaRequired);

      javaTool.add("inputSchema", javaSchema);
      tools.add(javaTool);
    }

    // 3. Web Search (JS Enabled) Tool
    if (!disabledTools.contains("web_search")) {
      JsonObject searchTool = new JsonObject();
      searchTool.addProperty("name", "web_search");
      searchTool.addProperty("description", "Search Google/DuckDuckGo or fetch URLs that require JavaScript rendering. Bypasses basic bot protection.");

      JsonObject searchSchema = new JsonObject();
      searchSchema.addProperty("type", "object");

      JsonObject searchProps = new JsonObject();
      JsonObject queryProp = new JsonObject();
      queryProp.addProperty("type", "string");
      queryProp.addProperty("description", "The URL or Search query to execute");
      searchProps.add("query", queryProp);

      searchSchema.add("properties", searchProps);

      JsonArray searchRequired = new JsonArray();
      searchRequired.add("query");
      searchSchema.add("required", searchRequired);

      searchTool.add("inputSchema", searchSchema);
      tools.add(searchTool);
    }

    // 4. Set Project Directory Tool
    if (!disabledTools.contains("set_project_directory")) {
      JsonObject setDirTool = new JsonObject();
      setDirTool.addProperty("name", "set_project_directory");
      setDirTool.addProperty("description", "Set the working directory for the current coding project.");

      JsonObject setDirSchema = new JsonObject();
      setDirSchema.addProperty("type", "object");
      JsonObject setDirProps = new JsonObject();

      JsonObject dirProp = new JsonObject();
      dirProp.addProperty("type", "string");
      dirProp.addProperty("description", "The absolute or relative path to the project directory");
      setDirProps.add("directory", dirProp);

      setDirSchema.add("properties", setDirProps);

      JsonArray setDirRequired = new JsonArray();
      setDirRequired.add("directory");
      setDirSchema.add("required", setDirRequired);

      setDirTool.add("inputSchema", setDirSchema);
      tools.add(setDirTool);
    }

    result.add("tools", tools);
    return result;
  }

  public JsonObject executeTool(JsonObject params, Set<String> disabledTools) throws Exception {
    String toolName = params.get("name").getAsString();
    JsonObject args = params.get("arguments").getAsJsonObject();

    if (disabledTools.contains(toolName)) {
      throw new IllegalStateException("Tool '" + toolName + "' is disabled by server configuration.");
    }

    return switch (toolName) {
      case "web_fetch" -> handleWebFetch(args);
      case "execute_java" -> handleExecuteJava(args);
      case "web_search" -> handleWebSearch(args, "google");
      case "set_project_directory" -> handleSetProjectDirectory(args);
      default -> throw new IllegalArgumentException("Unknown tool: " + toolName);
    };
  }

  private JsonObject handleSetProjectDirectory(JsonObject args) {
    String directory = args.get("directory").getAsString();
    Path path = Paths.get(directory).toAbsolutePath();

    if (Files.exists(path) && Files.isDirectory(path)) {
      this.currentProjectDirectory = path.toString();
      return buildTextContentResponse("Project directory successfully set to: " + this.currentProjectDirectory);
    } else {
      return buildTextContentResponse("Error: Directory does not exist or is not a valid directory: " + path);
    }
  }

  private JsonObject handleWebSearch(JsonObject args, String provider) {
    JsonObject response = null;
    if (provider.equalsIgnoreCase("google")) {
      response = handleGoogleWebSearch(args);
    } else {
      response = handleDuckDuckGoWebSearch(args);
    }
    return response;
  }

  private JsonObject handleDuckDuckGoWebSearch(JsonObject args) {
    String query = args.get("query").getAsString();
    String targetUrl = query;

    if (!query.startsWith("http")) {
      targetUrl = "https://html.duckduckgo.com/html/?q=" + query.replace(" ", "+");
    }
    try {
      String body = browserHelper.fetchWithJavascript(targetUrl);
      if (body.length() > 15000) {
        body = body.substring(0, 15000) + "... [Content Truncated]";
      }
      String finalContent = "Search Query: " + query + "\n\n--- Rendered Content ---\n\n" + body;
      return buildTextContentResponse(finalContent);
    } catch (Exception e) {
      return buildTextContentResponse("Error executing headless search: " + e.getMessage());
    }
  }

  private JsonObject handleGoogleWebSearch(JsonObject args) {
    String query = args.get("query").getAsString();
    String targetUrl = query;

    if (!query.startsWith("http")) {
      targetUrl = "https://www.google.com/search?q=" + query.replace(" ", "+") + "&hl=en&gl=us";
    }
    try {
      String body = browserHelper.fetchWithJavascript(targetUrl);
      if (body.contains("Our systems have detected unusual traffic") || body.contains("showing this page to check if you're a real person")) {
        System.out.println("  Google served a CAPTCHA. Your server's IP address might be flagged.");
      }
      if (body.length() > 15000) {
        body = body.substring(0, 15000) + "... [Content Truncated]";
      }
      String finalContent = "Search Query: " + query + "\n\n--- Rendered Content ---\n\n" + body;
      return buildTextContentResponse(finalContent);
    } catch (Exception e) {
      return buildTextContentResponse("Error executing headless Google search: " + e.getMessage());
    }
  }

  private JsonObject handleWebFetch(JsonObject args) throws Exception {
    String url = args.get("url").getAsString();
    String prompt = args.get("prompt").getAsString();
    String format = args.has("format") ? args.get("format").getAsString() : "auto";

    if (url.startsWith("http://")) {
      url = url.replaceFirst("http://", "https://");
    }
    if (url.contains("github.com") && url.contains("/blob/")) {
      url = url.replace("github.com", "raw.githubusercontent.com").replace("/blob/", "/");
    }

    String acceptHeader = switch (format.toLowerCase()) {
      case "markdown" -> "text/markdown, */*;q=0.1";
      case "html" -> "text/html, */*;q=0.1";
      case "text" -> "text/plain, */*;q=0.1";
      default -> "text/markdown, text/html;q=0.9, text/plain;q=0.8, */*;q=0.1";
    };

    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .header("Accept", acceptHeader)
        .header("User-Agent", "MCP-Agent/1.0")
        .GET()
        .build();

    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    String body = response.body();
    String contentType = response.headers().firstValue("Content-Type").orElse("").toLowerCase();

    if (contentType.contains("text/html") || format.equalsIgnoreCase("html")) {
      body = Jsoup.parse(body).text();
    }
    String finalContent = "Extraction Prompt: " + prompt + "\n\n--- Source Content ---\n\n" + body;
    return buildTextContentResponse(finalContent);
  }

  private JsonObject handleExecuteJava(JsonObject args) {
    String code = args.get("code").getAsString();
    StringBuilder outputBuilder = new StringBuilder();
    ByteArrayOutputStream baos = new ByteArrayOutputStream();

    try (PrintStream ps = new PrintStream(baos, true, StandardCharsets.UTF_8);
         JShell jshell = JShell.builder().out(ps).err(ps).build()) {

      Iterable<SnippetEvent> events = jshell.eval(code);
      ps.flush();
      String consoleOutput = baos.toString(StandardCharsets.UTF_8);

      if (!consoleOutput.isBlank()) {
        outputBuilder.append("--- Console Output ---\n").append(consoleOutput).append("\n");
      }

      outputBuilder.append("--- Evaluation Results ---\n");
      boolean hasResults = false;

      for (SnippetEvent event : events) {
        if (event.exception() != null) {
          outputBuilder.append("Exception: ").append(event.exception().getMessage()).append("\n");
          hasResults = true;
        } else if (event.value() != null) {
          outputBuilder.append(event.value()).append("\n");
          hasResults = true;
        }
      }
      if (!hasResults && consoleOutput.isBlank()) {
        outputBuilder.append("Code executed successfully with no output.");
      }
    } catch (Exception e) {
      outputBuilder.append("Execution failure: ").append(e.getMessage());
    }
    return buildTextContentResponse(outputBuilder.toString().trim());
  }

  private JsonObject buildTextContentResponse(String text) {
    JsonObject textContent = new JsonObject();
    textContent.addProperty("type", "text");
    textContent.addProperty("text", text);

    JsonArray contentArray = new JsonArray();
    contentArray.add(textContent);

    JsonObject result = new JsonObject();
    result.add("content", contentArray);
    return result;
  }
}