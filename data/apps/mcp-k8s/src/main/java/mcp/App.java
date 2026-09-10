package mcp;

import com.google.gson.*;
import io.javalin.Javalin;
import io.javalin.http.sse.SseClient;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class App {
 private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
 private static final ConcurrentHashMap<String, SseClient> clients = new ConcurrentHashMap<>();

 public static void main(String[] args) {
  Javalin app = Javalin.create(config -> {
   config.router.ignoreTrailingSlashes = true;
   config.http.asyncTimeout = 0L; // Prevent SSE disconnects

   // 1. Establish the persistent SSE connection
   config.routes.sse("/sse", client -> {
    String sessionId = UUID.randomUUID().toString();
    clients.put(sessionId, client);
    client.keepAlive();

    System.out.println("\n[🌐 ENDPOINT HIT] GET /sse");
    System.out.println(" -> New MCP client connected. Session ID: " + sessionId);

    // Required by MCP: Send the client the POST endpoint for incoming JSON-RPC calls
    client.sendEvent("endpoint", "/messages?sessionId=" + sessionId);

    client.onClose(() -> {
     clients.remove(sessionId);
     System.out.println("\n[❌ DISCONNECT] Client disconnected. Session ID: " + sessionId);
    });
   });

   // 2. Handle incoming JSON-RPC commands
   config.routes.post("/messages", ctx -> {
    String sessionId = ctx.queryParam("sessionId");
    SseClient client = clients.get(sessionId);

    System.out.println("\n[📩 ENDPOINT HIT] POST /messages?sessionId=" + sessionId);

    if (client == null) {
     System.out.println(" -> ⚠️ ERROR: Session not found.");
     ctx.status(404).result("Session not found");
     return;
    }

    JsonObject request = JsonParser.parseString(ctx.body()).getAsJsonObject();
    String method = request.has("method") ? request.get("method").getAsString() : "unknown_method";
    JsonElement id = request.get("id");

    System.out.println(" -> RPC Method: " + method);

    JsonObject response = new JsonObject();
    response.addProperty("jsonrpc", "2.0");
    if (id != null) response.add("id", id);

    try {
     switch (method) {
      case "initialize":
       System.out.println(" -> Model is initializing the connection/capabilities.");
       JsonObject result = new JsonObject();
       result.addProperty("protocolVersion", "2024-11-05");

       JsonObject capabilities = new JsonObject();
       capabilities.add("tools", new JsonObject());
       result.add("capabilities", capabilities);

       JsonObject serverInfo = new JsonObject();
       serverInfo.addProperty("name", "minikube-diagnostics");
       serverInfo.addProperty("version", "1.0.0");
       result.add("serverInfo", serverInfo);

       response.add("result", result);
       break;
      case "notifications/initialized":
       System.out.println(" -> Handshake complete.");
       ctx.status(202); // No response needed for notifications
       return;
      case "tools/list":
       System.out.println(" -> Model requested the list of available tools.");
       response.add("result", getToolsList());
       break;
      case "tools/call":
       JsonObject params = request.getAsJsonObject("params");
       String toolName = params.get("name").getAsString();
       JsonObject argsNode = params.getAsJsonObject("arguments");

       System.out.println(" -> 🛠️  MODEL CALLING TOOL: " + toolName);
       System.out.println(" -> 📦 Arguments: " + gson.toJson(argsNode));

       JsonObject toolResult = executeTool(toolName, argsNode);
       response.add("result", toolResult);

       System.out.println(" -> ✅ Tool executed successfully.");
       break;
      default:
       System.out.println(" -> ⚠️  Unknown method called.");
       JsonObject error = new JsonObject();
       error.addProperty("code", -32601);
       error.addProperty("message", "Method not found: " + method);
       response.add("error", error);
       break;
     }

     // Return the JSON-RPC response down the open SSE pipeline
     client.sendEvent("message", gson.toJson(response));
     ctx.status(202).result("Accepted");

    } catch (Exception e) {
     System.out.println(" -> ❌ Tool Execution Error: " + e.getMessage());
     JsonObject error = new JsonObject();
     error.addProperty("code", -32000);
     error.addProperty("message", e.getMessage());
     response.add("error", error);
     client.sendEvent("message", gson.toJson(response));
     ctx.status(500);
    }
   });
  }).start(8080);

  System.out.println("=================================================");
  System.out.println("Minikube MCP Server Started!");
  System.out.println("Connect your MCP client to: http://localhost:8080/sse");
  System.out.println("=================================================");
 }

 private static JsonObject getToolsList() {
  JsonObject result = new JsonObject();
  JsonArray tools = new JsonArray();

  // 1. Minikube Status
  tools.add(createToolDef("minikube_status", "Get the local Minikube cluster status.", new JsonObject()));

  // 2. Discover API Resources
  tools.add(createToolDef("kubectl_get_api_resources", "List all supported API resources (native and CRDs).", new JsonObject()));

  // 3. Generic Get
  JsonObject getProps = new JsonObject();
  getProps.add("resource", createStringProp("Resource type (e.g., pods, deployments, applications, httproutes, all)"));
  getProps.add("namespace", createStringProp("Namespace (leave empty for all namespaces)"));
  getProps.add("name", createStringProp("Specific resource name (optional)"));
  getProps.add("output_format", createStringProp("Output format (e.g., yaml, json, wide). Default is standard table."));
  tools.add(createToolDef("kubectl_get", "Get any Kubernetes resource. Use output_format='yaml' to see full configurations.", getProps, "resource"));

  // 4. Generic Describe
  JsonObject describeProps = new JsonObject();
  describeProps.add("resource", createStringProp("Resource type"));
  describeProps.add("name", createStringProp("Specific resource name"));
  describeProps.add("namespace", createStringProp("Namespace"));
  tools.add(createToolDef("kubectl_describe", "Describe a resource to see detailed events and state.", describeProps, "resource", "name"));

  // 5. Get Events
  JsonObject eventsProps = new JsonObject();
  eventsProps.add("namespace", createStringProp("Namespace (leave empty for all)"));
  tools.add(createToolDef("kubectl_get_events", "Get recent cluster events sorted by timestamp.", eventsProps));

  // 6. Logs
  JsonObject logProps = new JsonObject();
  logProps.add("pod_name", createStringProp("Name of the pod"));
  logProps.add("namespace", createStringProp("Namespace"));
  logProps.add("container", createStringProp("Specific container name (optional, if multi-container pod)"));
  JsonObject prevProp = createStringProp("Set to true to get logs from a previous crashed container");
  prevProp.addProperty("type", "boolean");
  logProps.add("previous", prevProp);
  tools.add(createToolDef("kubectl_logs", "Get logs for a pod/container.", logProps, "pod_name", "namespace"));

  // 7. Apply YAML
  JsonObject applyProps = new JsonObject();
  applyProps.add("yaml_manifest", createStringProp("The full YAML configuration string to apply."));
  tools.add(createToolDef("kubectl_apply", "Create or update resources by passing a raw YAML manifest.", applyProps, "yaml_manifest"));

  // 8. Delete Resource
  JsonObject deleteProps = new JsonObject();
  deleteProps.add("resource", createStringProp("Resource type"));
  deleteProps.add("name", createStringProp("Resource name"));
  deleteProps.add("namespace", createStringProp("Namespace"));
  tools.add(createToolDef("kubectl_delete", "Delete a Kubernetes resource.", deleteProps, "resource", "name"));

  // 9. Scale
  JsonObject scaleProps = new JsonObject();
  scaleProps.add("resource", createStringProp("Resource type (deployment, statefulset, replicaset)"));
  scaleProps.add("name", createStringProp("Resource name"));
  scaleProps.add("namespace", createStringProp("Namespace"));
  scaleProps.add("replicas", createStringProp("Number of replicas (integer as string)"));
  tools.add(createToolDef("kubectl_scale", "Scale a deployment, replicaset, or statefulset.", scaleProps, "resource", "name", "namespace", "replicas"));

  // 10. Exec Command in Pod
  JsonObject execProps = new JsonObject();
  execProps.add("pod_name", createStringProp("Name of the pod"));
  execProps.add("namespace", createStringProp("Namespace"));
  execProps.add("container", createStringProp("Container name (optional)"));
  execProps.add("command", createStringProp("The shell command to run (e.g., 'curl http://service:8080' or 'ls -la')"));
  tools.add(createToolDef("kubectl_exec", "Execute a command inside a running pod container to troubleshoot connectivity or file state.", execProps, "pod_name", "namespace", "command"));

  result.add("tools", tools);
  return result;
 }

 // Helper methods to keep getToolsList clean
 private static JsonObject createStringProp(String description) {
  JsonObject prop = new JsonObject();
  prop.addProperty("type", "string");
  prop.addProperty("description", description);
  return prop;
 }

 private static JsonObject createToolDef(String name, String description, JsonObject properties, String... required) {
  JsonObject tool = new JsonObject();
  tool.addProperty("name", name);
  tool.addProperty("description", description);
  JsonObject schema = new JsonObject();
  schema.addProperty("type", "object");
  schema.add("properties", properties);
  if (required.length > 0) {
   JsonArray reqArray = new JsonArray();
   for (String r : required) reqArray.add(r);
   schema.add("required", reqArray);
  }
  tool.add("inputSchema", schema);
  return tool;
 }

 private static JsonObject executeTool(String name, JsonObject arguments) throws Exception {
  String output;
  java.util.List<String> cmd = new java.util.ArrayList<>();

  // Use a temporary file for kubectl apply
  java.io.File tempYamlFile = null;

  try {
   switch (name) {
    case "minikube_status":
     cmd.addAll(java.util.Arrays.asList("minikube", "status"));
     break;

    case "kubectl_get_api_resources":
     cmd.addAll(java.util.Arrays.asList("kubectl", "api-resources"));
     break;

    case "kubectl_get":
     cmd.addAll(java.util.Arrays.asList("kubectl", "get", arguments.get("resource").getAsString()));
     if (arguments.has("name") && !arguments.get("name").getAsString().isEmpty()) {
      cmd.add(arguments.get("name").getAsString());
     }
     if (arguments.has("namespace") && !arguments.get("namespace").getAsString().isEmpty()) {
      cmd.add("-n"); cmd.add(arguments.get("namespace").getAsString());
     } else {
      cmd.add("-A");
     }
     if (arguments.has("output_format") && !arguments.get("output_format").getAsString().isEmpty()) {
      cmd.add("-o"); cmd.add(arguments.get("output_format").getAsString());
     }
     break;

    case "kubectl_describe":
     cmd.addAll(java.util.Arrays.asList("kubectl", "describe", arguments.get("resource").getAsString(), arguments.get("name").getAsString()));
     if (arguments.has("namespace") && !arguments.get("namespace").getAsString().isEmpty()) {
      cmd.add("-n"); cmd.add(arguments.get("namespace").getAsString());
     }
     break;

    case "kubectl_get_events":
     cmd.addAll(java.util.Arrays.asList("kubectl", "get", "events", "--sort-by=.metadata.creationTimestamp"));
     if (arguments.has("namespace") && !arguments.get("namespace").getAsString().isEmpty()) {
      cmd.add("-n"); cmd.add(arguments.get("namespace").getAsString());
     } else {
      cmd.add("-A");
     }
     break;

    case "kubectl_logs":
     cmd.addAll(java.util.Arrays.asList("kubectl", "logs", arguments.get("pod_name").getAsString(), "-n", arguments.get("namespace").getAsString(), "--tail=300"));
     if (arguments.has("container") && !arguments.get("container").getAsString().isEmpty()) {
      cmd.add("-c"); cmd.add(arguments.get("container").getAsString());
     }
     if (arguments.has("previous") && arguments.get("previous").getAsBoolean()) {
      cmd.add("-p");
     }
     break;

    case "kubectl_apply":
     tempYamlFile = java.io.File.createTempFile("mcp_apply_", ".yaml");
     java.nio.file.Files.writeString(tempYamlFile.toPath(), arguments.get("yaml_manifest").getAsString());
     cmd.addAll(java.util.Arrays.asList("kubectl", "apply", "-f", tempYamlFile.getAbsolutePath()));
     break;

    case "kubectl_delete":
     cmd.addAll(java.util.Arrays.asList("kubectl", "delete", arguments.get("resource").getAsString(), arguments.get("name").getAsString()));
     if (arguments.has("namespace") && !arguments.get("namespace").getAsString().isEmpty()) {
      cmd.add("-n"); cmd.add(arguments.get("namespace").getAsString());
     }
     break;

    case "kubectl_scale":
     cmd.addAll(java.util.Arrays.asList("kubectl", "scale", arguments.get("resource").getAsString(), arguments.get("name").getAsString(), "--replicas=" + arguments.get("replicas").getAsString()));
     if (arguments.has("namespace") && !arguments.get("namespace").getAsString().isEmpty()) {
      cmd.add("-n"); cmd.add(arguments.get("namespace").getAsString());
     }
     break;

    case "kubectl_exec":
     cmd.addAll(java.util.Arrays.asList("kubectl", "exec", arguments.get("pod_name").getAsString(), "-n", arguments.get("namespace").getAsString()));
     if (arguments.has("container") && !arguments.get("container").getAsString().isEmpty()) {
      cmd.add("-c"); cmd.add(arguments.get("container").getAsString());
     }
     cmd.add("--");
     cmd.addAll(java.util.Arrays.asList("sh", "-c", arguments.get("command").getAsString()));
     break;

    default:
     throw new IllegalArgumentException("Unknown tool: " + name);
   }

   output = runCommand(cmd.toArray(new String[0]));

  } finally {
   if (tempYamlFile != null && tempYamlFile.exists()) {
    tempYamlFile.delete(); // Cleanup temp YAML file immediately
   }
  }

  JsonObject result = new JsonObject();
  JsonArray content = new JsonArray();
  JsonObject textContent = new JsonObject();
  textContent.addProperty("type", "text");
  textContent.addProperty("text", output);
  content.add(textContent);
  result.add("content", content);
  result.addProperty("isError", output.toLowerCase().contains("error:"));
  return result;
 }

 private static String runCommand(String... command) {
  try {
   System.out.println(" -> 💻 Executing command: " + String.join(" ", command));
   ProcessBuilder pb = new ProcessBuilder(command);
   pb.redirectErrorStream(true);
   Process process = pb.start();

   try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
    String result = reader.lines().collect(Collectors.joining("\n"));
    process.waitFor();
    return result.isEmpty() ? "Command executed successfully (no output)." : result;
   }
  } catch (Exception e) {
   return "Error executing command: " + e.getMessage();
  }
 }
}