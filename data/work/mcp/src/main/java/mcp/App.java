package mcp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import io.javalin.Javalin;
import io.javalin.http.sse.SseClient;
import mcp.support.ToolHandler;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class App {

 private static final Map<String, SessionContext> sessions = new ConcurrentHashMap<>();
 private static final Gson gson = new Gson();
 private static final ToolHandler toolHandler = new ToolHandler();

 public static void main(String[] args) {

  // Register default endpoint with LM Studio automatically
  registerWithLmStudio("my-java-tools", "http://localhost:8080/sse");

  Javalin.create(config -> {

   // 1. Establish SSE Connection and parse headers
   config.routes.sse("/sse", client -> {
    String sessionId = UUID.randomUUID().toString();

    // Read configuration from the HTTP Header: X-Disabled-Tools
    String disabledHeader = client.ctx().header("X-Disabled-Tools");
    Set<String> disabledTools = new HashSet<>();

    if (disabledHeader != null && !disabledHeader.isBlank()) {
     disabledTools = Arrays.stream(disabledHeader.split(","))
         .map(String::trim)
         .collect(Collectors.toSet());
    }

    sessions.put(sessionId, new SessionContext(client, disabledTools));
    client.keepAlive();

    // Tell the client where to POST JSON-RPC messages
    client.sendEvent("endpoint", "/mcp/message?sessionId=" + sessionId);
    client.onClose(() -> sessions.remove(sessionId));
   });

   // 2. Handle incoming JSON-RPC requests
   config.routes.post("/mcp/message", ctx -> {
    String sessionId = ctx.queryParam("sessionId");
    SessionContext session = sessions.get(sessionId);

    if (session == null) {
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

    ctx.status(202); // MCP requires early 202 Accepted for SSE transport

    // Process asynchronously using Java Virtual Threads
    Thread.startVirtualThread(() -> {
     try {
      JsonObject response = new JsonObject();
      response.addProperty("jsonrpc", "2.0");
      if (idNode != null)
       response.add("id", idNode);

      switch (method) {
       case "initialize" -> response.add("result", handleInitialize());

       // Pass the disabled tools list so they are omitted from LM Studio's UI
       case "tools/list" -> response.add("result", toolHandler.getToolsList(session.disabledTools()));

       // Pass the disabled tools list to enforce security during execution
       case "tools/call" -> {
        try {
         JsonObject toolResult = toolHandler.executeTool(request.getAsJsonObject("params"), session.disabledTools());
         response.add("result", toolResult);
        } catch (IllegalStateException e) {
         JsonObject error = new JsonObject();
         error.addProperty("code", -32600); // Invalid request
         error.addProperty("message", e.getMessage());
         response.add("error", error);
        }
       }
       case "ping" -> response.add("result", new JsonObject());
       case "notifications/initialized" -> {
        return; // No response expected
       }
       default -> {
        JsonObject error = new JsonObject();
        error.addProperty("code", -32601);
        error.addProperty("message", "Method not found");
        response.add("error", error);
       }
      }
      session.client().sendEvent("message", gson.toJson(response));
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
  serverInfo.addProperty("name", "JavaToolingServer");
  serverInfo.addProperty("version", "1.0.0");
  result.add("serverInfo", serverInfo);

  return result;
 }

 /**
  * Programmatically registers this MCP server with LM Studio via mcp.json. Appends if missing,
  * leaves alone if present.
  */
 private static void registerWithLmStudio(String serverName, String sseUrl) {
  String userHome = System.getProperty("user.home");
  Path mcpConfigPath = Paths.get(userHome, ".lmstudio", "mcp.json");
  Gson prettyGson = new GsonBuilder().setPrettyPrinting().create();
  JsonObject rootConfig = new JsonObject();

  try {
   // Read existing config if it exists
   if (Files.exists(mcpConfigPath)) {
    try (Reader reader = Files.newBufferedReader(mcpConfigPath, StandardCharsets.UTF_8)) {
     rootConfig = JsonParser.parseReader(reader).getAsJsonObject();
    }
   } else {
    Files.createDirectories(mcpConfigPath.getParent());
   }

   // Ensure "mcpServers" object exists
   if (!rootConfig.has("mcpServers")) {
    rootConfig.add("mcpServers", new JsonObject());
   }
   JsonObject mcpServers = rootConfig.getAsJsonObject("mcpServers");

   // Leave alone if already present
   if (mcpServers.has(serverName)) {
    System.out.println("⚡ LM Studio config already contains '" + serverName + "'. No changes made.");
    return;
   }

   // Define server configuration
   JsonObject serverConfig = new JsonObject();
   serverConfig.addProperty("url", sseUrl);

   // Add custom headers block with execute_java disabled by default as a safety precaution
   JsonObject headersConfig = new JsonObject();
   headersConfig.addProperty("X-Disabled-Tools", "execute_java");
   serverConfig.add("headers", headersConfig);

   mcpServers.add(serverName, serverConfig);

   // Write back to file
   try (Writer writer = Files.newBufferedWriter(mcpConfigPath, StandardCharsets.UTF_8)) {
    prettyGson.toJson(rootConfig, writer);
   }
   System.out.println("✅ Automatically registered '" + serverName + "' to LM Studio at: " + mcpConfigPath);
   System.out.println("ℹ️ Note: 'execute_java' tool is disabled by default in mcp.json for security.");

  } catch (Exception e) {
   System.err.println("❌ Failed to register with LM Studio: " + e.getMessage());
  }
 }

 // Record to track a session and its configured tool permissions
 record SessionContext(SseClient client, Set<String> disabledTools) {
 }
}
