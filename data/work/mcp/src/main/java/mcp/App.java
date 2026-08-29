package mcp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import io.javalin.Javalin;
import io.javalin.http.sse.SseClient;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class App {

  private static final Map<String, SseClient> clients = new ConcurrentHashMap<>();
  private static final Gson gson = new Gson();
  private static final ToolHandler toolHandler = new ToolHandler(); // Dedicated Tool Handler

  public static void main(String[] args) {
    registerWithLmStudio("webfetch", "http://localhost:8080/sse");

    Javalin.create(config -> {

      config.routes.sse("/sse", client -> {
        String sessionId = UUID.randomUUID().toString();
        clients.put(sessionId, client);
        client.keepAlive();

        client.sendEvent("endpoint", "/mcp/message?sessionId=" + sessionId);
        client.onClose(() -> clients.remove(sessionId));
      });

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
              case "tools/list" -> response.add("result", toolHandler.getToolsList()); // Delegated
              case "tools/call" -> response.add("result", toolHandler.executeTool(request.getAsJsonObject("params"))); // Delegated
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

    System.out.println("MCP Server running on http://localhost:8080/sse");
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

  private static void registerWithLmStudio(String serverName, String sseUrl) {
    String userHome = System.getProperty("user.home");
    Path mcpConfigPath = Paths.get(userHome, ".lmstudio", "mcp.json");
    Gson prettyGson = new GsonBuilder().setPrettyPrinting().create();
    JsonObject rootConfig = new JsonObject();

    try {
      // 1. Read existing config if it exists
      if (Files.exists(mcpConfigPath)) {
        try (Reader reader = Files.newBufferedReader(mcpConfigPath, StandardCharsets.UTF_8)) {
          rootConfig = JsonParser.parseReader(reader).getAsJsonObject();
        }
      } else {
        Files.createDirectories(mcpConfigPath.getParent());
      }

      // 2. Ensure the "mcpServers" parent object exists
      if (!rootConfig.has("mcpServers")) {
        rootConfig.add("mcpServers", new JsonObject());
      }
      JsonObject mcpServers = rootConfig.getAsJsonObject("mcpServers");

      // 3. LEAVE ALONE IF PRESENT: Check if our server is already registered
      if (mcpServers.has(serverName)) {
        System.out.println("⚡ LM Studio config already contains '" + serverName + "'. No changes made.");
        return; // Exit immediately without touching the file on disk
      }

      // 4. APPEND IF MISSING: Define our server's configuration
      JsonObject serverConfig = new JsonObject();
      serverConfig.addProperty("url", sseUrl);
      mcpServers.add(serverName, serverConfig);

      // 5. CREATE/WRITE: Save the changes back to disk, preserving all other existing entries
      try (Writer writer = Files.newBufferedWriter(mcpConfigPath, StandardCharsets.UTF_8)) {
        prettyGson.toJson(rootConfig, writer);
      }
      System.out.println("✅ Automatically registered '" + serverName + "' to LM Studio at: " + mcpConfigPath);

    } catch (Exception e) {
      System.err.println("❌ Failed to register with LM Studio: " + e.getMessage());
    }
  }
}
