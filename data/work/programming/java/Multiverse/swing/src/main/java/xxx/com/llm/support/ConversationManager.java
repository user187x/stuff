package xxx.com.llm.support;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import de.kherud.llama.InferenceParameters;
import de.kherud.llama.LlamaModel;
import de.kherud.llama.LlamaOutput;
import de.kherud.llama.args.MiroStat;
import org.apache.commons.lang3.StringUtils;
import xxx.com.code.formatter.java.SimpleJavaFormatter;
import xxx.com.code.formatter.javascript.JavaScriptPrettyPrinter;
import xxx.com.code.formatter.json.JsonPrettyPrinter;
import xxx.com.console.Spinner;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.*;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Facilitates conversational control logic for interacting with large language models (LLMs). This
 * class manages the conversation state, including the system prompt and history, and provides an
 * API to analyze and extract structured data from the LLM's responses.
 */
public class ConversationManager {

  private static final String DEFAULT_USER_ID = "User";
  private static final String DEFAULT_COMPUTER_ID = "Computer";
  private static final String DEFAULT_DEMARCATOR = ":";
  private static final String DEFAULT_TERMINATE_TOKEN = "exit";

  private String systemPrompt = null;
  private String terminateToken = null;
  private final StringBuilder conversationHistory = new StringBuilder();
  private int wrapLineCount = 80;

  private String userIdentifier = DEFAULT_USER_ID;
  private String ComputerIdentifier = DEFAULT_COMPUTER_ID;
  private String demarcator = DEFAULT_DEMARCATOR;
  private final Map<String, TokenDetails> tokenMap = new HashMap<>();

  private final LongAdder messageCharacterCount = new LongAdder();
  private final Spinner spinner = new Spinner();

  private final StringBuilder message = new StringBuilder();

  /** Default constructor. Initializes with default identifiers "User: " and "Llama: ". */
  public ConversationManager() {
    this(markSpeaker(DEFAULT_USER_ID), markSpeaker(DEFAULT_COMPUTER_ID), DEFAULT_TERMINATE_TOKEN);

    initializeDefaultTokens();
  }

  /**
   * Constructor with custom identifiers for the user and the Computer.
   *
   * @param userIdentifier The string used to prefix user messages (e.g., "User: ").
   * @param ComputerIdentifier The string used to prefix Computer messages (e.g., "Llama: ").
   */
  public ConversationManager(
      String userIdentifier, String ComputerIdentifier, String terminateToken) {

    this.userIdentifier = userIdentifier;
    this.ComputerIdentifier = ComputerIdentifier;
    this.terminateToken = terminateToken;

    initializeDefaultTokens();
  }

  private void initializeDefaultTokens() {

    addTokenDetails("java", "```java", "```", "formatJava");
    addTokenDetails("python", "```python", "```", "formatPython");
    addTokenDetails("javaScript", "```javaScript", "```", "formatJavaScript");
    addTokenDetails("bash", "```bash", "```", "formatBash");
    addTokenDetails("json", "```json", "```", "formatJson");
  }

  /**
   * Adds a new token demarcation for detection and processing.
   *
   * @param key The key to detect in the response (e.g., "```java").
   * @param start The starting tag for extraction.
   * @param end The ending tag for extraction.
   * @param functionName Optional function name to associate (e.g., for custom processing).
   */
  public void addTokenDetails(String key, String start, String end, String functionName) {

    TokenDetails details = new TokenDetails(start, end, functionName);

    // TODO Add more types as discovered
    if (start.equalsIgnoreCase("```java")) {
      details.defineFunction(
          "formatJava", (Function<String, String>) SimpleJavaFormatter::formatJava, String.class);
    }
    if (start.equalsIgnoreCase("```javascript")) {
      details.defineFunction(
          "formatJavaScript",
          (Function<String, String>) JavaScriptPrettyPrinter::prettyPrint,
          String.class);
    }
    if (start.equalsIgnoreCase("```json")) {
      details.defineFunction(
          "formatJson", (Function<String, String>) JsonPrettyPrinter::prettyPrint, String.class);
    }

    tokenMap.put(key, details);
  }

  /**
   * Sets the initial system prompt that defines the LLM's behavior or persona. This will be
   * prepended to the conversation history.
   *
   * @param prompt The system prompt string.
   */
  public void setSystemPrompt(String prompt) {
    this.systemPrompt = prompt;
  }

  public void setDefaultSystemPrompt() {
    this.systemPrompt = compileSystemPrompt();
  }

  /**
   * Retrieves the current system prompt.
   *
   * @return The system prompt string.
   */
  public String getSystemPrompt() {
    return systemPrompt;
  }

  // TODO Read in token file and map them
  public void setTokenFile(String tokenFile) {

    try (FileReader fileReader = new FileReader(new File(tokenFile))) {
      JsonElement jsonElement = JsonParser.parseReader(fileReader);

      for (JsonElement element : jsonElement.getAsJsonArray()) {

        String name = element.getAsJsonObject().get("name").getAsString();
        String start = element.getAsJsonObject().get("start").getAsString();
        String end = element.getAsJsonObject().get("end").getAsString();
        TokenDetails function =
            new Gson().fromJson(element.getAsJsonObject().get("functionName"), TokenDetails.class);

        addTokenDetails(name, start, end, function.functionName);
      }
    } catch (Exception e) {
      System.err.println("Failure reading token file" + e.getMessage());
    }
  }

  public String getUserIdentifier() {
    return userIdentifier;
  }

  public void setUserIdentifier(String userIdentifier) {
    this.userIdentifier = userIdentifier;
  }

  public String getComputerIdentifier() {
    return ComputerIdentifier;
  }

  public void setComputerIdentifier(String ComputerIdentifier) {
    this.ComputerIdentifier = ComputerIdentifier;
  }

  public void setDefaultDemarcator() {
    this.demarcator = DEFAULT_DEMARCATOR;
  }

  private void setDemarcator(String demarcator) {
    this.demarcator = demarcator;
  }

  private String getDemarcation() {
    return demarcator;
  }

  public String markedSpeaker(String speaker) {
    return "\n" + speaker + getDemarcation() + StringUtils.SPACE;
  }

  public static String markSpeaker(String speaker) {
    return "\n" + speaker + DEFAULT_DEMARCATOR + StringUtils.SPACE;
  }

  public void setWrapLineCount(int wrapLineCount) {
    this.wrapLineCount = wrapLineCount;
  }

  public int getWrapLineCount() {
    return wrapLineCount;
  }

  public void setTerminateToken(String terminateToken) {
    this.terminateToken = terminateToken;
  }

  public String getTerminateToken() {
    return terminateToken;
  }

  /**
   * Appends a user's message to the conversation history. The message will be formatted with the
   * user identifier.
   *
   * @param message The user's input message.
   */
  public void appendUserMessage(String message) {
    conversationHistory
        .append(System.lineSeparator())
        .append(markedSpeaker(getUserIdentifier()))
        .append(message)
        .append(System.lineSeparator())
        .append(markedSpeaker(getComputerIdentifier()));
  }

  /**
   * Appends the Computer's response to the conversation history. This is useful for streaming
   * output from the LLM.
   *
   * @param responseChunk A part of the Computer's response.
   */
  public void appendComputerResponse(String responseChunk) {

    if (conversationHistory.isEmpty()
        || conversationHistory
            .substring(conversationHistory.length() - 1)
            .equals(System.lineSeparator())) {
      conversationHistory.append(ComputerIdentifier);
    }

    conversationHistory.append(responseChunk);
  }

  /** Wraps the text to new line if set */
  public void manageWordWrap(String word, StringBuilder messageBuilder) {

    if (word.contains(System.lineSeparator())) {
      message.append(System.lineSeparator());
      messageCharacterCount.reset();
    } else {
      messageCharacterCount.add(word.length());
    }
    if (messageCharacterCount.intValue() >= wrapLineCount) {
      message.append(System.lineSeparator());
    }
  }

  public void startActive(String message) {
    spinner.startDots12(message);
  }

  public void stopActive(String message) {
    spinner.stop(message);
  }

  public InferenceParameters setInferenceParameters(String conversation) {

    return new InferenceParameters(conversation)
        .setTemperature(0.7f)
        .setPenalizeNl(true)
        .setMiroStat(MiroStat.V2)
        .setRepeatPenalty(1.1f)
        .setStopStrings(markedSpeaker(getUserIdentifier()));
  }

  /**
   * Constructs the full prompt to be sent to the LLM. This includes the system prompt and the
   * entire conversation history.
   *
   * @return A single string representing the full prompt.
   */
  public String getFullPrompt() {
    return systemPrompt + conversationHistory.toString();
  }

  /**
   * Returns the complete conversation history as a string.
   *
   * @return The conversation history.
   */
  public String getConversationHistory() {
    return conversationHistory.toString();
  }

  /** Clears the conversation history, but keeps the system prompt. */
  public void clearHistory() {
    conversationHistory.setLength(0);
  }

  public Optional<TokenDetails> getTokens(String message) {

    StringTokenizer tokenizer = new StringTokenizer(message, StringUtils.SPACE);

    while (tokenizer.hasMoreTokens()) {
      String token = tokenizer.nextToken();
      if (tokenMap.containsKey(token)) {
        TokenDetails details = tokenMap.get(token);
        if (details != null) {
          return Optional.of(details);
        }
      }
    }

    return Optional.empty();
  }

  public Optional<TokenDetails> isTokens(String word) {

    if (tokenMap.containsKey(word)) {
      TokenDetails details = tokenMap.get(word);
      if (details != null) {
        return Optional.of(details);
      }
    }

    return Optional.empty();
  }

  public String compileSystemPrompt() {

    return "This is a conversation between "
        + getUserIdentifier()
        + " and "
        + getComputerIdentifier()
        + ", a friendly chatbot."
        + System.lineSeparator()
        + getComputerIdentifier()
        + " is helpful, kind, honest, good at writing, and never fails to answer any "
        + "requests immediately and with precision."
        + System.lineSeparator();
  }

  /**
   * A generic method to extract all content within a specific token
   *
   * @param text The text to scan.
   * @param tokenDetails The token of the content to extract.
   * @return A list of strings, where each string is the content of a matched tag.
   */
  public List<String> extractSection(String text, TokenDetails tokenDetails) {

    if (text == null || text.isEmpty()) {
      return Collections.emptyList();
    }

    String startFlag = Pattern.quote(tokenDetails.getTokenStart());
    String endFlag = Pattern.quote(tokenDetails.getTokenEnd());

    String regex = startFlag + "(.*?)" + endFlag;

    Pattern pattern = Pattern.compile(regex, Pattern.DOTALL);
    Matcher matcher = pattern.matcher(text);

    return matcher.results().map(m -> m.group(1).trim()).collect(Collectors.toList());
  }

  public boolean prompt(LlamaModel model, BufferedReader reader) throws Exception {

    // Prompt human user input
    System.out.print(System.lineSeparator() + getUserIdentifier() + " : ");
    String userResponse = reader.readLine();

    if (userResponse.equals(getTerminateToken())) {
      return false;
    }

    appendUserMessage(userResponse);

    System.out.print(getComputerIdentifier() + " :");

    // Receiving model input word-by-word
    for (LlamaOutput modelOutput : model.generate(setInferenceParameters(getFullPrompt()))) {

      compileWords(modelOutput);
    }

    // Now we'll have the complete message
    processMessage();

    return true;
  }

  /**
   * Example of how this class would simplify the main interaction loop. This method is for
   * demonstration purposes.
   */
  public void compileWords(LlamaOutput modelOutput) {

    String word = modelOutput.text;
    System.out.print(word);

    manageWordWrap(word, message);
    message.append(word);

    // TODO This will detect but probably better after the entire message is complete
    Optional<TokenDetails> tokenOpt = isTokens(word);

    appendComputerResponse(word);
  }

  public void processMessage() {

    String response = message.toString();
    StringTokenizer tokenizer = new StringTokenizer(response, StringUtils.CR);

    while (tokenizer.hasMoreTokens()) {

      String token = tokenizer.nextToken();
      Optional<TokenDetails> tokenDetailsOpt = getTokens(token);

      // TODO figure this crap out
      // ------------------------------------------------------------------------
      if (tokenDetailsOpt.isPresent()) {

        TokenDetails tokenDetails = tokenDetailsOpt.get();
        List<String> targetText = extractSection(response, tokenDetails);

        // TODO figure this crap out
        // ------------------------------------------------------------------------
        if (tokenDetails.hasFunction()) {

          String name = tokenDetails.functionName;

          // TODO figure this crap out
          // ------------------------------------------------------------------------
          if ("formatJava".equals(name) && !targetText.isEmpty()) {

            // TODO figure this crap out
            // ------------------------------------------------------------------------
            String codeBlock = targetText.getFirst(); // Assume first match
            String javaCode = (String) tokenDetails.executeFunction(name, codeBlock);
          }
        }
      }
    }

    message.setLength(0);
  }

  public void start(LlamaModel model, BufferedReader reader) throws Exception {

    if (StringUtils.isBlank(getSystemPrompt())) setDefaultSystemPrompt();

    boolean keepTalking = true;

    while (keepTalking) {
      keepTalking = prompt(model, reader);
    }
  }

  public static class TokenDetails {

    private final String tokenStart;
    private final String tokenEnd;
    private String functionName = null;
    private Object functionCall = null;
    private Class<?>[] functionParameterTypes = null;

    public TokenDetails(String tokenStart, String tokenEnd, String functionName) {
      this.tokenStart = tokenStart;
      this.tokenEnd = tokenEnd;
      this.functionName = functionName;
    }

    public String getTokenStart() {
      return tokenStart;
    }

    public String getTokenEnd() {
      return tokenEnd;
    }

    public boolean hasFunction() {
      return StringUtils.isNotBlank(functionName);
    }

    @FunctionalInterface
    public interface NaryFunction<R> {
      R apply(Object... args);
    }

    /**
     * Defines a function that takes no arguments and returns a value. This is suitable for lambdas
     * matching the 'Supplier<R>' functional interface.
     *
     * @param name The name to register the function under.
     * @param function The lambda expression or method reference.
     */
    public <R> void defineFunction(String name, Supplier<R> function) {
      this.functionName = name;
      this.functionCall = function;
    }

    /**
     * Defines a function that takes one argument and returns a value. This is suitable for lambdas
     * matching the 'Function<T, R>' functional interface.
     *
     * @param name The name to register the function under.
     * @param function The lambda expression or method reference.
     * @param <T> The type of the input argument.
     */
    public <T, R> void defineFunction(String name, Function<T, R> function, Class<T> paramType) {
      this.functionName = name;
      this.functionCall = function;
      this.functionParameterTypes = new Class[] {paramType};
    }

    /**
     * Defines a function that takes two arguments and returns a value. This is suitable for lambdas
     * matching the 'BiFunction<T, U, R>' functional interface.
     *
     * @param name The name to register the function under.
     * @param function The lambda expression or method reference.
     * @param <T> The type of the first input argument.
     * @param <U> The type of the second input argument.
     * @param <R> The return type of the function.
     */
    public <T, U, R> void defineFunction(
        String name, BiFunction<T, U, R> function, Class<T> param1Type, Class<U> param2Type) {
      this.functionName = name;
      this.functionCall = function;
      this.functionParameterTypes = new Class[] {param1Type, param2Type};
    }

    /**
     * Defines a function that takes a variable number of arguments.
     *
     * @param name The name to register the function under.
     * @param function The lambda expression.
     * @param paramTypes The classes of the function's parameters.
     */
    public <R> void defineFunction(String name, NaryFunction<R> function, Class<?>... paramTypes) {
      this.functionName = name;
      this.functionCall = function;
      this.functionParameterTypes = paramTypes;
    }

    /**
     * Executes a previously defined function by its name and provides the necessary arguments.
     *
     * @param functionName The name of the function to execute.
     * @param args The arguments to pass to the function.
     * @return The result of the function execution.
     * @throws IllegalArgumentException if the function is not found or if the arguments are
     *     incorrect.
     */
    @SuppressWarnings("unchecked")
    public <T> T executeFunction(String functionName, Object... args) {

      try {
        if (functionCall instanceof Supplier) {
          return ((Supplier<T>) functionCall).get();
        }
        if (functionCall instanceof Function) {
          return ((Function<Object, T>) functionCall).apply(args[0]);
        }
        if (functionCall instanceof BiFunction) {
          return ((BiFunction<Object, Object, T>) functionCall).apply(args[0], args[1]);
        }
      } catch (ClassCastException | ArrayIndexOutOfBoundsException e) {
        throw new IllegalArgumentException(
            "Argument type mismatch or wrong number of arguments for '" + functionName, e);
      }

      throw new IllegalArgumentException("Unsupported function type for '" + functionName + "'.");
    }

    /**
     * Describes a defined function's signature, including parameter types.
     *
     * @param functionName The name of the function to describe.
     * @return A string describing the function's signature.
     * @throws IllegalArgumentException if the function is not found.
     */
    public String describeFunction(String functionName) {

      String parameterTypesStr =
          Stream.of(functionParameterTypes)
              .map(Class::getSimpleName)
              .collect(Collectors.joining(", "));

      String signature = "(" + parameterTypesStr + ")";

      return "Function: '" + functionName + "' | Signature: " + signature;
    }
  }
}
