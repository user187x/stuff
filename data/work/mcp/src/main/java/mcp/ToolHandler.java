package mcp;

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
import java.time.Duration;
import java.util.Set;

public class ToolHandler {

  private final HttpClient httpClient;

  public ToolHandler() {
    this.httpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(10))
        .build();
  }

  // Now accepts the disabledTools set from the headers
  public JsonObject getToolsList(Set<String> disabledTools) {
    JsonObject result = new JsonObject();
    JsonArray tools = new JsonArray();

    // 1. Web Fetch Tool - Only add if not disabled
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

    // 2. Execute Java Tool - Only add if not disabled
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

    result.add("tools", tools);
    return result;
  }

  // Now accepts the disabledTools set to prevent unauthorized execution
  public JsonObject executeTool(JsonObject params, Set<String> disabledTools) throws Exception {
    String toolName = params.get("name").getAsString();
    JsonObject args = params.get("arguments").getAsJsonObject();

    // Security check: Block execution if the tool is disabled in the headers
    if (disabledTools.contains(toolName)) {
      throw new IllegalStateException("Tool '" + toolName + "' is disabled by server configuration.");
    }

    return switch (toolName) {
      case "web_fetch" -> handleWebFetch(args);
      case "execute_java" -> handleExecuteJava(args);
      default -> throw new IllegalArgumentException("Unknown tool: " + toolName);
    };
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
