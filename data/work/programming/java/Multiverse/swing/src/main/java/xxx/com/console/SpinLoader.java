package xxx.com.console;

import com.google.gson.*;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * A utility for showing a loading spinner animation in the console.
 * This class is AutoCloseable to ensure its resources are properly managed.
 */
// --- CHANGE: Implemented AutoCloseable for proper resource management ---
public class SpinLoader implements AutoCloseable {

  private ScheduledExecutorService executor;
  private Map<String, Spinner> spinners;
  private ScheduledFuture<?> currentTaskFuture;

  private Spinner currentSpinner;

  // Using a record for immutable data transfer is a good modern practice.
  private record Spinner(String name, long interval, String[] frames) {}

  /**
   * The task that renders the spinner animation frame by frame.
   * This remains a static nested class as it doesn't need access to instance state.
   */
  public static class SpinnerTask implements Runnable {
    private int index = 0;
    private final String[] frames;
    private final String message;

    public SpinnerTask(Spinner spinner) {
      this.frames = spinner.frames();
      this.message = String.format("Loading with %s", spinner.name());
    }

    @Override
    public void run() {
      System.out.print(message + " " + frames[index] + "\r");
      index = (index + 1) % frames.length;
    }
  }

  /**
   * Initializes the ConsoleSpinner, loading spinner definitions and preparing the executor.
   */
  public SpinLoader() {
    init();
    this.currentSpinner = spinners.values().iterator().next();
  }

  public List<String> listAll() {

    spinners.forEach((key, spinner) -> System.out.println(spinner.name()));
    return spinners.values().stream().map(Spinner::name).toList();
  }

  private void init(){

    try {
      this.executor = Executors.newSingleThreadScheduledExecutor();
      this.spinners = loadSpinners();

      if(spinners.isEmpty()){
        throw new Exception("No spinner found!");
      }

    } catch (Exception e) {

      System.out.println("Error loading Spinners: " + e.getMessage());
      System.exit(1);
    }
  }

  public SpinLoader(String spinnerName){

    init();

    if(!spinners.containsKey(spinnerName)) {

      System.out.println("Spinner " + spinnerName + " not found.");
      this.currentSpinner = this.spinners.get(spinnerName);
      System.out.println("Defaulting to spinner : " + currentSpinner.name);
    }
    else{
      this.currentSpinner = spinners.get(spinnerName);
    }
  }

  /**
   * Starts a spinner animation by its name. If another animation is running,
   * it is stopped first.
   *
   * @param @SpinnerData The POJO of the spinner to display (e.g., "dots", "line").
   */
  public synchronized void start(Spinner spinner) {
    // Stop any previously running task before starting a new one.
    stopAndClear();

    SpinnerTask task = new SpinnerTask(spinner);
    // --- CHANGE: Schedule the task and store its Future to allow for cancellation ---
    this.currentTaskFuture =
        executor.scheduleAtFixedRate(task, 0, spinner.interval(), TimeUnit.MILLISECONDS);
  }

  public synchronized void start() {
    // Stop any previously running task before starting a new one.
    stopAndClear();

    if(this.currentSpinner == null){
      this.currentSpinner = spinners.values().iterator().next();
    }

    SpinnerTask task = new SpinnerTask(currentSpinner);
    // --- CHANGE: Schedule the task and store its Future to allow for cancellation ---
    this.currentTaskFuture =
        executor.scheduleAtFixedRate(task, 0, currentSpinner.interval(), TimeUnit.MILLISECONDS);
  }

  public float getDurationSeconds() {
    return (float) (currentSpinner.frames().length * currentSpinner.interval()) / 1000.0f;
  }

  public long getDurationMs() {
    return (long) currentSpinner.frames().length * currentSpinner.interval();
  }

  public synchronized void start(String spinnerName) {
    // Stop any previously running task before starting a new one.
    stopAndClear();

    if(!spinners.containsKey(spinnerName)) {

      System.out.println("Spinner " + spinnerName + " not found.");
      System.exit(-1);
    }

    Spinner spinner = spinners.get(spinnerName);

    SpinnerTask task = new SpinnerTask(spinner);
    // --- CHANGE: Schedule the task and store its Future to allow for cancellation ---
    this.currentTaskFuture =
        executor.scheduleAtFixedRate(task, 0, spinner.interval(), TimeUnit.MILLISECONDS);
  }

  /**
   * Stops the currently running animation and displays a final message.
   *
   * @param finalMessage The message to display after stopping the animation.
   */
  public synchronized void stop(String finalMessage) {

    stopAndClear();

    // Clear the line by overwriting with spaces and print the final message.
    String cleanup = " ".repeat(100);
    System.out.print("\r" + cleanup + "\r");
    System.out.println(finalMessage);
  }

  public synchronized void stop() {

    stopAndClear();

    // Clear the line by overwriting with spaces and print the final message.
    String cleanup = " ".repeat(100);
    System.out.print("\r" + cleanup + "\r");
  }

  /**
   * Internal method to stop and cancel the current running task.
   */
  private void stopAndClear() {

    if (currentTaskFuture != null && !currentTaskFuture.isDone()) {
      currentTaskFuture.cancel(true);
      currentTaskFuture = null;
    }
  }

  public Map<String, Spinner> getSpinnerMap() {
    return spinners;
  }

  /**
   * --- CHANGE: This method is now responsible for shutting down the executor ---
   * It is automatically called when the object is used in a try-with-resources block.
   */
  @Override
  public void close() {
    stopAndClear(); // Stop any running task first
    executor.shutdown();
    try {
      if (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
        executor.shutdownNow();
      }
    } catch (InterruptedException e) {
      executor.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }

  private static Map<String, Spinner> loadSpinners() {

    Gson gson = new Gson();
    Map<String, Spinner> spinnerMap = new TreeMap<>();
    String resourcePath = "/spinners/spinners.json";

    try (InputStream inputStream = Spinner.class.getResourceAsStream(resourcePath)) {

      Objects.requireNonNull(inputStream, "Cannot find resource: " + resourcePath);
      InputStreamReader reader = new InputStreamReader(inputStream);

      JsonObject jsonObject = JsonParser.parseReader(reader).getAsJsonObject();

      for (Map.Entry<String, JsonElement> entry : jsonObject.asMap().entrySet()) {
        JsonObject spinner = entry.getValue().getAsJsonObject();
        Spinner spinnerData = gson.fromJson(spinner, Spinner.class);

        spinnerMap.put(entry.getKey(), new Spinner(entry.getKey(), spinnerData.interval(), spinnerData.frames()));
      }
    } catch (Exception e) {
      throw new IllegalStateException("Failed to load spinners from " + resourcePath, e);
    }
    return spinnerMap;
  }

  public static void display() throws InterruptedException {

    try (SpinLoader spinLoader = new SpinLoader()) {
      for (Spinner spinner : spinLoader.getSpinnerMap().values()) {
        System.out.println("\n--- Demonstrating " + spinner.name() + " ---");
        spinLoader.start(spinner);
        Thread.sleep(3000);
        spinLoader.stop("Done. ✅");
      }
    }
  }

  public static void main(String[] args) throws InterruptedException {

    try(SpinLoader loader = new SpinLoader("explosion")) {

      System.out.println("Available spinners:");
      loader.listAll();
      System.out.println("\nStarting " + loader.currentSpinner.name + " spinner for " + loader.getDurationSeconds() + " seconds");
      loader.start();
      Thread.sleep(loader.getDurationMs());
      loader.stop("Task complete! ✨");
    }
  }
}
