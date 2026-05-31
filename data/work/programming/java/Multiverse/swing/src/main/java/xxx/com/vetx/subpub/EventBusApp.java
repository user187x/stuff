package xxx.com.vetx.subpub;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A minimal Vert.x application demonstrating a publish-subscribe pattern. This version uses
 * separate inner classes for the Publisher and Subscriber to better illustrate component
 * separation.
 */
public class EventBusApp {

  /**
   * The main entry point to start the Vert.x application. It creates a Vert.x instance and deploys
   * the Publisher and Subscriber verticles.
   *
   * @param args Command line arguments (not used).
   */
  public static void main(String[] args) {

    Vertx vertx = Vertx.vertx();
    vertx.deployVerticle(new Publisher());
    vertx.deployVerticle(new Subscriber());
  }

  /** The Publisher verticle periodically sends a message to the event bus. */
  public static class Publisher extends AbstractVerticle {

    private EventBus eventBus;
    private final String address = "news.updates";
    private final AtomicInteger messageCounter = new AtomicInteger();

    @Override
    public void start() throws Exception {
      this.eventBus = vertx.eventBus();
      // Start the publishing loop
      publishNext();
    }

    private void publishNext() {
      String message = "Update #" + messageCounter.incrementAndGet();
      // At this high speed, we don't print to the console because
      // I/O is slow and would become the bottleneck.
      // System.out.println("PUBLISHING MESSAGE: '" + message + "'");
      eventBus.publish(address, message);

      // Schedule the next execution on the event loop.
      // This runs the next publication as soon as the current one is done,
      // creating a continuous, non-blocking loop.
      vertx.runOnContext(v -> publishNext());
    }
  }

  /** The Subscriber verticle listens for messages on the event bus and prints them. */
  public static class Subscriber extends AbstractVerticle {
    @Override
    public void start() throws Exception {

      EventBus eventBus = vertx.eventBus();
      String address = "news.updates";

      eventBus.consumer(
          address, message -> System.out.println("RECEIVED MESSAGE: '" + message.body() + "'"));

      System.out.println("Subscriber listening on address: " + address);
    }
  }
}
