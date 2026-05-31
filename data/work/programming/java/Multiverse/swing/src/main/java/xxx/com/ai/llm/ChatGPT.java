//import dev.langchain4j.model.chat.*;
//import dev.langchain4j.memory.chat.MessageWindowChatMemory;
//import dev.langchain4j.*;
//import dev.langchain4j.service.AiServices;
//import dev.langchain4j.service.SystemMessage;
//import dev.langchain4j.service.UserMessage;
//import dev.langchain4j.service.V;
//
//import java.util.Scanner;
//
//public class ChatGPT {
//
//  // Define the conversation interface
//  interface Assistant {
//    String chat(@UserMessage String message);
//  }
//
//  // Define a persona-specific assistant
//  interface PersonaAssistant {
//    @SystemMessage("You are a friendly pirate captain named {{name}}. " +
//        "Always respond in pirate speak and end with 'Arrr!'")
//    String chat(@UserMessage String message, @V("name") String assistantName);
//  }
//
//  public static void main(String[] args) {
//    // Initialize the LLM (replace with your API key)
//    ChatLanguageModel model = OpenAiChatModel.builder()
//        .apiKey(System.getenv("OPENAI_API_KEY")) // Set your API key as environment variable
//        .modelName("gpt-3.5-turbo")
//        .temperature(0.7)
//        .build();
//
//    System.out.println("=== Basic Conversation Demo ===");
//    basicConversationDemo(model);
//
//    System.out.println("\n=== Persona-based Conversation Demo ===");
//    personaConversationDemo(model);
//
//    System.out.println("\n=== Interactive Conversation Demo ===");
//    interactiveConversationDemo(model);
//  }
//
//  private static void basicConversationDemo(ChatLanguageModel model) {
//    // Create a simple assistant with conversation memory
//    Assistant assistant = AiServices.builder(Assistant.class)
//        .chatLanguageModel(model)
//        .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
//        .build();
//
//    // Have a conversation
//    System.out.println("User: Hello, what's your name?");
//    String response1 = assistant.chat("Hello, what's your name?");
//    System.out.println("Assistant: " + response1);
//
//    System.out.println("\nUser: Can you remember what I just asked?");
//    String response2 = assistant.chat("Can you remember what I just asked?");
//    System.out.println("Assistant: " + response2);
//  }
//
//  private static void personaConversationDemo(ChatLanguageModel model) {
//    // Create a persona-based assistant
//    PersonaAssistant pirateAssistant = AiServices.builder(PersonaAssistant.class)
//        .chatLanguageModel(model)
//        .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
//        .build();
//
//    System.out.println("User: Tell me about your ship");
//    String response = pirateAssistant.chat("Tell me about your ship", "Captain Blackbeard");
//    System.out.println("Pirate Assistant: " + response);
//  }
//
//  private static void interactiveConversationDemo(ChatLanguageModel model) {
//    // Create an interactive assistant with custom persona
//    Assistant helpfulAssistant = AiServices.builder(Assistant.class)
//        .chatLanguageModel(model)
//        .chatMemory(MessageWindowChatMemory.withMaxMessages(20))
//        .build();
//
//    // Set initial context
//    helpfulAssistant.chat("You are a helpful programming assistant named CodeBot. " +
//        "Always be concise and practical in your responses.");
//
//    Scanner scanner = new Scanner(System.in);
//    System.out.println("Chat with CodeBot! (type 'quit' to exit)");
//
//    while (true) {
//      System.out.print("\nYou: ");
//      String userInput = scanner.nextLine();
//
//      if ("quit".equalsIgnoreCase(userInput.trim())) {
//        break;
//      }
//
//      try {
//        String response = helpfulAssistant.chat(userInput);
//        System.out.println("CodeBot: " + response);
//      } catch (Exception e) {
//        System.out.println("Error: " + e.getMessage());
//      }
//    }
//
//    scanner.close();
//    System.out.println("Goodbye!");
//  }
//}
//
//// Alternative approach using direct ChatLanguageModel
//class DirectLLMExample {
//  public static void directConversationExample() {
//    ChatLanguageModel model = OpenAiChatModel.builder()
//        .apiKey(System.getenv("OPENAI_API_KEY"))
//        .modelName("gpt-3.5-turbo")
//        .build();
//
//    // Direct model usage without conversation memory
//    String systemPrompt = "You are a helpful assistant named Alex.";
//    String userMessage = "Hello, how can you help me today?";
//
//    String response = model.generate(
//        dev.langchain4j.data.message.SystemMessage.from(systemPrompt),
//        dev.langchain4j.data.message.UserMessage.from(userMessage)
//    ).content().text();
//
//    System.out.println("Response: " + response);
//  }
//}