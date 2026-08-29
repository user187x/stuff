package mcp;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import io.javalin.Javalin;
import io.javalin.http.sse.SseClient;
import org.jsoup.Jsoup;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class App {

  private static final Map<String, SseClient> clients = new ConcurrentHashMap<>();

  private static final String MCP_URL = "http://localhost:8080/sse";
  private static final String MCP_NAME = "webfetch";

  private static final Gson gson = new Gson();

  private static final HttpClient httpClient = HttpClient.newBuilder()
      .followRedirects(HttpClient.Redirect.NORMAL)
      .connectTimeout(Duration.ofSeconds(10))
      .build();

  public static void main(String[] args) {

    Register.withLmStudio(MCP_NAME, MCP_URL);

    Javalin.create(config -> {

      // 1. Establish SSE Connection
      config.routes.sse("/sse", client -> {
        String sessionId = UUID.randomUUID().toString();
        clients.put(sessionId, client);
        client.keepAlive();

        client.sendEvent("endpoint", "/mcp/message?sessionId=" + sessionId);
        client.onClose(() -> clients.remove(sessionId));
      });

      // 2. Handle JSON-RPC 2.0 requests
      config.routes.post("/mcp/message", ctx -> {
        String sessionId = ctx.queryParam("sessionId");
        SseClient client = clients.get(sessionId);

        if (client == null) {
          ctx.status(404).result("Session not found");
          return;
        }

        JsonObject request;
        try {
          request = JsonParser.parseString(ctx.body()).getAsJsonObject();
        } catch (JsonSyntaxException | IllegalStateException e) {
          ctx.status(400).result("Invalid JSON");
          return;
        }

        String method = request.has("method") ? request.get("method").getAsString() : "";
        JsonElement idNode = request.get("id");

        ctx.status(202);

        Thread.startVirtualThread(() -> {
          try {
            JsonObject response = new JsonObject();
            response.addProperty("jsonrpc", "2.0");
            if (idNode != null)
              response.add("id", idNode);

            switch (method) {
              case "initialize" -> response.add("result", handleInitialize());
              case "tools/list" -> response.add("result", handleToolsList());
              case "tools/call" -> response.add("result", handleToolsCall(request.get("params")));
              case "ping" -> response.add("result", new JsonObject());
              case "notifications/initialized" -> {
                return;
              }
              default -> {
                JsonObject error = new JsonObject();
                error.addProperty("code", -32601);
                error.addProperty("message", "Method not found");
                response.add("error", error);
              }
            }
            client.sendEvent("message", gson.toJson(response));
          } catch (Exception e) {
            e.printStackTrace();
          }
        });
      });

    }).start(8080);

    System.out.println("MCP Server running on " + MCP_URL);
  }

  private static JsonObject handleInitialize() {

    JsonObject result = new JsonObject();
    result.addProperty("protocolVersion", "2024-11-05");
    result.add("capabilities", new JsonObject());

    JsonObject serverInfo = new JsonObject();
    serverInfo.addProperty("name", "QwenWebFetchServer");
    serverInfo.addProperty("version", "1.0.0");
    result.add("serverInfo", serverInfo);

    return result;
  }

  private static JsonObject handleToolsList() {
    JsonObject result = new JsonObject();
    JsonArray tools = new JsonArray();

    JsonObject tool = new JsonObject();
    tool.addProperty("name", "web_fetch");
    tool.addProperty("description", "Fetch content from a URL and process it using an AI model. Handles markdown content negotiation automatically.");

    JsonObject inputSchema = new JsonObject();
    inputSchema.addProperty("type", "object");
    JsonObject properties = new JsonObject();

    JsonObject urlProp = new JsonObject();
    urlProp.addProperty("type", "string");
    urlProp.addProperty("description", "The URL to fetch content from");
    properties.add("url", urlProp);

    JsonObject promptProp = new JsonObject();
    promptProp.addProperty("type", "string");
    promptProp.addProperty("description", "Instructions for extracting info");
    properties.add("prompt", promptProp);

    JsonObject formatProp = new JsonObject();
    formatProp.addProperty("type", "string");
    formatProp.addProperty("description", "auto, markdown, html, or text");
    properties.add("format", formatProp);

    inputSchema.add("properties", properties);
    JsonArray required = new JsonArray();
    required.add("url");
    required.add("prompt");
    inputSchema.add("required", required);

    tool.add("inputSchema", inputSchema);
    tools.add(tool);
    result.add("tools", tools);

    return result;
  }

  private static JsonObject handleToolsCall(JsonElement params) throws Exception {
    JsonObject args = params.getAsJsonObject().get("arguments").getAsJsonObject();
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
        .header("User-Agent", "MCP-WebFetchAgent/1.0")
        .GET()
        .build();

    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    String body = response.body();
    String contentType = response.headers().firstValue("Content-Type").orElse("").toLowerCase();

    if (contentType.contains("text/html") || format.equalsIgnoreCase("html")) {
      body = Jsoup.parse(body).text();
    }

    String finalContent = "Extraction Prompt: " + prompt + "\n\n--- Source Content ---\n\n" + body;

    JsonObject textContent = new JsonObject();
    textContent.addProperty("type", "text");
    textContent.addProperty("text", finalContent);

    JsonArray contentArray = new JsonArray();
    contentArray.add(textContent);

    JsonObject result = new JsonObject();
    result.add("content", contentArray);
    return result;
  }
}
