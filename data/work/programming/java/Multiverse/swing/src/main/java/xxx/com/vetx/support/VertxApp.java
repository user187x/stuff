package xxx.com.vetx.support;

import io.vertx.core.Vertx;

import java.util.function.BiConsumer;

/** Adds shutdown support straight into chained function call */
public record VertxApp(Vertx vertx) {

  public static VertxApp create() {
    return new VertxApp(Vertx.vertx());
  }

  public VertxApp withShutdownHook() {

    Runtime.getRuntime().addShutdownHook(new Thread( () -> {
      System.out.println("Shutting down Vert.x...");
      vertx.close();
    }));

    return this;
  }

  public void start(int port, BiConsumer<Vertx, Integer> serverLogic) {
    serverLogic.accept(vertx, port);
  }
}
