package mcp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Register {

  public static void withLmStudio(String serverName, String sseUrl) {
    
    String userHome = System.getProperty("user.home");
    Path mcpConfigPath = Paths.get(userHome, ".lmstudio", "mcp.json");

    // Use Gson with pretty printing so the file remains human-readable
    Gson gson = new GsonBuilder().setPrettyPrinting().create();
    JsonObject rootConfig = new JsonObject();

    try {
      // 1. Read existing config if it exists
      if (Files.exists(mcpConfigPath)) {
        try (Reader reader = Files.newBufferedReader(mcpConfigPath, StandardCharsets.UTF_8)) {
          rootConfig = JsonParser.parseReader(reader).getAsJsonObject();
        }
      } else {
        // Ensure the .lmstudio directory exists
        Files.createDirectories(mcpConfigPath.getParent());
      }

      // 2. Ensure the "mcpServers" parent object exists
      if (!rootConfig.has("mcpServers")) {
        rootConfig.add("mcpServers", new JsonObject());
      }
      JsonObject mcpServers = rootConfig.getAsJsonObject("mcpServers");

      // 3. Define our server's configuration
      JsonObject serverConfig = new JsonObject();
      serverConfig.addProperty("url", sseUrl);

      // 4. Inject or update our server config
      mcpServers.add(serverName, serverConfig);

      // 5. Write the changes back to disk
      try (Writer writer = Files.newBufferedWriter(mcpConfigPath, StandardCharsets.UTF_8)) {
        gson.toJson(rootConfig, writer);
      }

      System.out.println("✅ Successfully registered '" + serverName + "' to LM Studio at: " + mcpConfigPath);

    } catch (Exception e) {
      System.err.println("❌ Failed to automatically register with LM Studio: " + e.getMessage());
    }
  }
}
