package mcp.active;

import com.google.gson.*;
import io.javalin.Javalin;
import io.javalin.http.sse.SseClient;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.awt.*;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AssistedBrowserMcpServer {

 private static final Map<String, SessionContext> sessions = new ConcurrentHashMap<>();
 private static final Gson gson = new Gson();

 static void main(String[] args) {
  Javalin.create(config -> {

   // 1. Establish SSE Connection
   config.routes.sse("/sse", client -> {
    String sessionId = UUID.randomUUID().toString();
    sessions.put(sessionId, new SessionContext(client));
    client.keepAlive();

    // Tell the LLM harness where to POST JSON-RPC messages
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
      if (idNode != null) response.add("id", idNode);

      switch (method) {
       case "initialize" -> response.add("result", handleInitialize());

       case "tools/list" -> response.add("result", getToolsList());

       case "tools/call" -> {
        try {
         JsonObject params = request.getAsJsonObject("params");
         String toolName = params.get("name").getAsString();

         if ("web_search_with_fallback".equals(toolName)) {
          JsonObject argsObj = params.getAsJsonObject("arguments");
          String query = argsObj.has("query") ? argsObj.get("query").getAsString() : "";

          String searchResult = executeAssistedSearch(query);

          JsonObject content = new JsonObject();
          content.addProperty("type", "text");
          content.addProperty("text", searchResult);
          JsonArray contentArray = new JsonArray();
          contentArray.add(content);

          JsonObject result = new JsonObject();
          result.add("content", contentArray);
          response.add("result", result);
         } else {
          throw new IllegalStateException("Unknown tool: " + toolName);
         }
        } catch (Exception e) {
         JsonObject error = new JsonObject();
         error.addProperty("code", -32600);
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

  }).start(8999);

  System.out.println("🤖 Assisted Browser MCP Server running on http://localhost:8999/sse");
 }

 private static JsonObject handleInitialize() {
  JsonObject result = new JsonObject();
  result.addProperty("protocolVersion", "2024-11-05");
  result.add("capabilities", new JsonObject());

  JsonObject serverInfo = new JsonObject();
  serverInfo.addProperty("name", "AssistedBrowser");
  serverInfo.addProperty("version", "1.0.0");
  result.add("serverInfo", serverInfo);

  return result;
 }

 private static JsonObject getToolsList() {
  JsonObject queryProp = new JsonObject();
  queryProp.addProperty("type", "string");
  queryProp.addProperty("description", "The search query to execute on Google.");

  JsonObject properties = new JsonObject();
  properties.add("query", queryProp);

  JsonArray required = new JsonArray();
  required.add("query");

  JsonObject inputSchema = new JsonObject();
  inputSchema.addProperty("type", "object");
  inputSchema.add("properties", properties);
  inputSchema.add("required", required);

  JsonObject tool = new JsonObject();
  tool.addProperty("name", "web_search_with_fallback");
  tool.addProperty("description", "Executes a web search on an active browser. Pauses for human intervention if a captcha is detected.");
  tool.add("inputSchema", inputSchema);

  JsonArray toolsArray = new JsonArray();
  toolsArray.add(tool);

  JsonObject result = new JsonObject();
  result.add("tools", toolsArray);

  return result;
 }

 private static String executeAssistedSearch(String query) {
  ChromeOptions options = new ChromeOptions();
  options.setExperimentalOption("debuggerAddress", "127.0.0.1:9222");
  WebDriver driver = new ChromeDriver(options);

  try {
   driver.get("https://www.google.com");
   WebDriverWait standardWait = new WebDriverWait(driver, Duration.ofSeconds(15));

   WebElement searchBox = standardWait.until(ExpectedConditions.elementToBeClickable(By.name("q")));
   searchBox.sendKeys(query);
   searchBox.sendKeys(Keys.RETURN);

   standardWait.until(ExpectedConditions.or(
       ExpectedConditions.presenceOfElementLocated(By.id("search")),
       ExpectedConditions.urlContains("/sorry/index")
   ));

   if (driver.getCurrentUrl().contains("/sorry/index")) {
    System.out.println("CAPTCHA detected! Alerting human...");
    Toolkit.getDefaultToolkit().beep();
    JavascriptExecutor js = (JavascriptExecutor) driver;
    js.executeScript("window.focus();");

    js.executeScript(
        "var banner = document.createElement('div');" +
            "banner.innerHTML = '<h2 style=\"color:white; margin:0;\">🤖 MCP Server Halted: LLM needs you to solve this CAPTCHA to continue.</h2>';" +
            "banner.style.cssText = 'position:fixed; top:0; left:0; width:100%; background:red; z-index:99999; text-align:center; padding:15px; font-family:sans-serif; box-shadow: 0 4px 6px rgba(0,0,0,0.3);';" +
            "document.body.insertBefore(banner, document.body.firstChild);"
    );

    WebDriverWait humanWait = new WebDriverWait(driver, Duration.ofMinutes(5));
    humanWait.until(ExpectedConditions.presenceOfElementLocated(By.id("search")));
    System.out.println("CAPTCHA cleared!");
   }

   return "Search successful for: " + query + ". Data is ready on the active browser tab.";

  } catch (Exception e) {
   return "Search failed or human did not solve captcha in time: " + e.getMessage();
  }
 }

 record SessionContext(SseClient client) {
 }
}