package xxx.com.kafka;

import io.vertx.core.Vertx;
import io.vertx.kafka.client.consumer.KafkaConsumer;
import java.util.HashMap;
import java.util.Map;

public class App {

  private static final String KAFKA_TOPIC = "test-topic";

  public static void main(String[] args) {
    Vertx vertx = Vertx.vertx();

    // Deploy the producer verticle
    vertx.deployVerticle(new KafkaProducerVerticle());

    // Configure the consumer
    Map<String, String> config = new HashMap<>();
    config.put("bootstrap.servers", "localhost:9092");
    config.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
    config.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
    config.put("group.id", "vertx-consumer-group-mapped");
    config.put("auto.offset.reset", "earliest");
    config.put("enable.auto.commit", "true");

    // Create the consumer and set up the stream processing pipeline
    KafkaConsumer<String, String> consumer = KafkaConsumer.create(vertx, config);

    // Set the handler to receive the raw record and then transform it
    consumer.handler(record -> {
      // The transformation happens inside the handler
      String transformedMessage = RecordTransformer.processRecord(record);
      System.out.println("Mapped Consumer -> Received transformed message: " + transformedMessage);
    });

    // Now, subscribe to the topic to start the stream
    consumer.subscribe(KAFKA_TOPIC)
    .onSuccess(v -> System.out.println("Mapped consumer subscribed successfully to topic: " + KAFKA_TOPIC))
    .onFailure(Throwable::printStackTrace);
  }
}
