package xxx.com.web.rest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class Requester {

  private static final String TARGET_URL = "http://localhost:8080/"; // Change to your server's URL
  private static final int THREAD_COUNT = 50; // Number of concurrent threads
  private static final int REQUESTS_PER_THREAD = 100; // Requests per thread
  private static final AtomicInteger requestCount = new AtomicInteger(0);

  public static void main(String[] args) {
    ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);

    // Create multiple threads to simulate concurrent clients
    for (int i = 0; i < THREAD_COUNT; i++) {
      executor.submit(() -> {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(TARGET_URL))
            .GET()
            .build();

        // Send repeated requests
        for (int j = 0; j < REQUESTS_PER_THREAD; j++) {
          try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            int count = requestCount.incrementAndGet();
            System.out.println("Request " + count + " from thread " + Thread.currentThread().getName() +
                " - Status: " + response.statusCode());
            // Optional: Small delay to simulate realistic request pacing
            Thread.sleep(10);
          } catch (Exception e) {
            System.err.println("Request failed: " + e.getMessage());
          }
        }
      });
    }

    // Shutdown executor after tasks complete
    executor.shutdown();
    try {
      if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
        executor.shutdownNow();
      }
    } catch (InterruptedException e) {
      executor.shutdownNow();
    }
    System.out.println("Total requests sent: " + requestCount.get());
  }
}
