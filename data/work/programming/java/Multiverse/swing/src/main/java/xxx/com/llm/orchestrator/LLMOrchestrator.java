package xxx.com.llm.orchestrator;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class LLMOrchestrator {

    // --- CONFIGURATION ---
    // TODO: Replace with your SerpApi key from https://serpapi.com/manage-api-key
    static final String SERP_API_KEY = "YOUR_SERP_API_KEY_HERE";

    public static void main(String[] args) throws Exception {
        System.out.println("LLM Internet Orchestrator Initialized.");
        System.out.println("Ask a question, or type 'exit' to quit.");
        System.out.println("Try asking about a recent event, e.g., 'What was the headline news in Baltimore yesterday?'");

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            var orchestrator = new Orchestrator(new LocalLLMClient(), new SerpApiSearchTool());
            while (true) {
                System.out.print("\n> ");
                String userInput = reader.readLine();
                if (userInput == null || "exit".equalsIgnoreCase(userInput.trim())) {
                    System.out.println("Exiting...");
                    break;
                }
                orchestrator.processUserInput(userInput);
            }
        }
    }
}

/**
 * The main orchestrator that manages the workflow.
 */
class Orchestrator {
    private final LocalLLMClient llmClient;
    private final InternetSearchTool searchTool;
    private static final String KNOWLEDGE_CUTOFF = "September 2024";

    public Orchestrator(LocalLLMClient llmClient, InternetSearchTool searchTool) {
        this.llmClient = llmClient;
        this.searchTool = searchTool;
    }

    public void processUserInput(String input) {
        System.out.println("Orchestrator: Thinking...");

        // 1. First pass to the LLM to see if it needs to use a tool
        String initialPrompt = createInitialPrompt(input);
        String llmResponse = llmClient.generateResponse(initialPrompt);

        // 2. Check if the LLM requested a tool
        Optional<ToolCall> toolCall = parseToolCall(llmResponse);

        if (toolCall.isPresent()) {
            System.out.println("Orchestrator: LLM requested an internet search for: '" + toolCall.get().query() + "'");
            System.out.println("Orchestrator: Searching the web...");

            // 3. Execute the search
            String searchResult = searchTool.search(toolCall.get().query());

            // 4. Second pass to the LLM with the search context
            System.out.println("Orchestrator: Sending search results to LLM for final answer...");
            String augmentedPrompt = createAugmentedPrompt(input, searchResult);
            String finalAnswer = llmClient.generateResponse(augmentedPrompt);

            System.out.println("\nLLM Response:\n" + finalAnswer);
        } else {
            // The LLM answered directly
            System.out.println("\nLLM Response:\n" + llmResponse);
        }
    }

    private String createInitialPrompt(String query) {
        // Using StringBuilder for string construction.
        String separator = System.lineSeparator();
        return new StringBuilder()
                .append("You are a helpful assistant with access to a tool. Your internal knowledge is cut off at ").append(KNOWLEDGE_CUTOFF).append(".").append(separator)
                .append("If you need to know about current events, recent information, or specific facts you don't possess, you MUST use the internet search tool.").append(separator)
                .append("To use the tool, respond ONLY with a JSON object in the following format, with no other text or explanation:").append(separator)
                .append("{\"tool\": \"internet_search\", \"query\": \"your concise search query here\"}").append(separator)
                .append(separator)
                .append("Current date is: ").append(LocalDate.now()).append(".").append(separator)
                .append("If you can answer from your existing knowledge, provide the answer directly.").append(separator)
                .append(separator)
                .append("User's question: ").append(query)
                .toString();
    }

    private String createAugmentedPrompt(String originalQuery, String context) {
        String separator = System.lineSeparator();
        return new StringBuilder()
                .append("You are a helpful assistant. Based ONLY on the provided context below, answer the user's question.").append(separator)
                .append("Do not use any of your prior knowledge. If the context does not contain the answer, say that you couldn't find the information.").append(separator)
                .append(separator)
                .append("Context from web search:").append(separator)
                .append("---").append(separator)
                .append(context).append(separator)
                .append("---").append(separator)
                .append(separator)
                .append("User's original question: ").append(originalQuery)
                .toString();
    }

    private Optional<ToolCall> parseToolCall(String llmResponse) {
        try {
            // A simple check to see if the response looks like JSON.
            String trimmedResponse = llmResponse.trim();
            if (trimmedResponse.startsWith("{") && trimmedResponse.endsWith("}")) {
                Gson gson = new Gson();
                ToolCall call = gson.fromJson(trimmedResponse, ToolCall.class);
                if ("internet_search".equals(call.tool()) && call.query() != null) {
                    return Optional.of(call);
                }
            }
        } catch (JsonSyntaxException e) {
            // It's not a valid JSON tool call, so we treat it as a normal response.
            return Optional.empty();
        }
        return Optional.empty();
    }

    // A record to represent the structured tool call from the LLM.
    private record ToolCall(String tool, String query) {}
}

/**
 * A client to interact with your local LLM.
 * TODO: This is a placeholder! You need to replace this with your actual LLM calling logic.
 */
class LocalLLMClient {
    public String generateResponse(String prompt) {
        // --- THIS IS WHERE YOU INTEGRATE YOUR LLM ---
        // You would replace this entire method with calls to your `de.kherud.llama.LlamaModel`.
        // For demonstration, this simulates the LLM's behavior.

        System.out.println("\n--- Sending to LLM ---");
        System.out.println(prompt);
        System.out.println("----------------------\n");

        if (prompt.contains("headline news in Baltimore yesterday")) {
            // Simulate the LLM deciding it needs to search the web
            return "{\"tool\": \"internet_search\", \"query\": \"headline news Baltimore September 4 2025\"}";
        } else if (prompt.contains("Java 21 features")) {
            return "{\"tool\": \"internet_search\", \"query\": \"new features in Java 21\"}";
        } else {
            // Simulate a direct answer for a question it should know
            return "Java 21, released in September 2023, introduced several key features such as Virtual Threads, String Templates, and Sequenced Collections, enhancing performance and developer productivity.";
        }
    }
}

/**
 * Interface for any search tool.
 */
interface InternetSearchTool {
    String search(String query);
}

/**
 * An implementation of the search tool using SerpApi.
 */
class SerpApiSearchTool implements InternetSearchTool {
    private final HttpClient httpClient;
    private final Gson gson = new Gson();

    public SerpApiSearchTool() {
        // Using a virtual thread per task executor for non-blocking I/O calls.
        this.httpClient = HttpClient.newBuilder()
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .build();
    }

    @Override
    public String search(String query) {
        if (LLMOrchestrator.SERP_API_KEY == null || "YOUR_SERP_API_KEY_HERE".equals(LLMOrchestrator.SERP_API_KEY)) {
            return "Error: SerpApi key is not configured in LLMOrchestrator.java.";
        }
        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = new StringBuilder("https://serpapi.com/search.json?q=")
                    .append(encodedQuery)
                    .append("&api_key=")
                    .append(LLMOrchestrator.SERP_API_KEY)
                    .toString();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept", "application/json")
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return parseAndSummarizeResults(response.body());
            } else {
                return new StringBuilder("Error: Received status code ")
                        .append(response.statusCode())
                        .append(" from search API.")
                        .toString();
            }
        } catch (Exception e) {
            return new StringBuilder("Error during web search: ")
                    .append(e.getMessage())
                    .toString();
        }
    }

    private String parseAndSummarizeResults(String jsonResponse) {
        // Extracts and concatenates snippets from the search results for context.
        try {
            JsonObject root = gson.fromJson(jsonResponse, JsonObject.class);
            return root.getAsJsonArray("organic_results").asList().stream()
                    .filter(element -> element.isJsonObject() && element.getAsJsonObject().has("snippet"))
                    .map(element -> element.getAsJsonObject().get("snippet").getAsString())
                    .limit(5) // Limit to the top 5 snippets to keep the context concise
                    .collect(Collectors.joining(" "));
        } catch (Exception e) {
            return "Error: Could not parse search results.";
        }
    }
}

