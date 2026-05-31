package xxx.com.ai.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.GetPromptResult;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceResult;
import io.modelcontextprotocol.spec.McpSchema.Resource;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import io.modelcontextprotocol.spec.McpSchema.TextResourceContents;
import io.modelcontextprotocol.server.McpServer;
import java.util.List;

/**
 * A simple educational example demonstrating how to use the MCP Java SDK.
 * This program sends a single message to a model and prints the response.
 */
public class ModelContextProtocol {

  public static void main(String[] args) {
    // Initialize transport provider
    StdioServerTransportProvider transportProvider = new StdioServerTransportProvider(new ObjectMapper());

    // Sync completion specification
    var syncCompletionSpecification = new McpServerFeatures.SyncCompletionSpecification(
        new McpSchema.PromptReference("code_review"),
        (exchange, request) -> {
          // Simple completion implementation
          return new McpSchema.CompleteResult(
              new McpSchema.CompleteResult.CompleteCompletion(
                  List.of("python", "pytorch", "pyside"),
                  10, // total
                  false // hasMore
              )
          );
        }
    );

    // Sync tool specification
    var toolSchema = """
            {
              "type": "object",
              "id": "urn:jsonschema:Operation",
              "properties": {
                "operation": { "type": "string" },
                "a": { "type": "number" },
                "b": { "type": "number" }
              }
            }
            """;
    var syncToolSpecification = new McpServerFeatures.SyncToolSpecification(
        new Tool("operation_tool", "Perform an operation", toolSchema),
        (exchange, request) -> {
          // Simple tool call implementation - return CallToolResult with proper constructor
          return CallToolResult.builder()
              .addTextContent("Operation completed")
              .isError(false)
              .build();
        }
    );

    // Sync resource specification
    var syncResourceSpecification = new McpServerFeatures.SyncResourceSpecification(
        new Resource("custom://resource", "name", "description", "text/plain", null),
        (exchange, request) -> {
          // Resource read implementation - return proper ResourceContents
          TextResourceContents contents = new TextResourceContents(
              "custom://resource",
              "text/plain",
              "Sample resource content"
          );
          return new ReadResourceResult(List.of(contents));
        }
    );

    // Sync prompt specification
    var syncPromptSpecification = new McpServerFeatures.SyncPromptSpecification(
        new McpSchema.Prompt("code_review", "Code review prompt", null, List.of()),
        (exchange, request) -> {
          // Simple prompt response - return GetPromptResult
          return new GetPromptResult(
              "Code Review", // description
              List.of(new McpSchema.PromptMessage(
                  McpSchema.Role.USER,
                  new McpSchema.TextContent("Please review this code")
              ))
          );
        }
    );

    // Create a sync server with capabilities
    McpSyncServer syncServer = McpServer.sync(transportProvider)
        .serverInfo("my-server", "1.0.0")
        .capabilities(ServerCapabilities.builder()
            .resources(false, true) // Resource support with list changes notifications
            .tools(true)           // Tool support with list changes notifications
            .prompts(true)        // Prompt support with list changes notifications
            .completions()         // Enable completions support
            .logging()            // Enable logging support
            .build())
        .build();

    // For STDIO transport, the server needs to keep the process alive
    try {
      System.out.println("Server started and handling requests...");
      // Keep the server running indefinitely for STDIO transport
      // The server will handle requests through stdin/stdout
      Thread.currentThread().join();
    } catch (InterruptedException e) {
      System.err.println("Server interrupted: " + e.getMessage());
      Thread.currentThread().interrupt();
    } catch (Exception e) {
      System.err.println("Server error: " + e.getMessage());
    } finally {
      // Close the server
      syncServer.close();
    }
  }
}