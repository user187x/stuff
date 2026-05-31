package xxx.com.kafka;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.kafka.client.producer.KafkaProducer;
import io.vertx.kafka.client.producer.KafkaProducerRecord;
import java.util.HashMap;
import java.util.Map;

public class KafkaProducerVerticle extends AbstractVerticle {

  private static final String KAFKA_TOPIC = "test-topic";

  @Override
  public void start(Promise<Void> startPromise) {

    // Configure the Kafka producer
    Map<String, String> config = new HashMap<>();
    config.put("bootstrap.servers", "localhost:9092");
    config.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
    config.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
    config.put("acks", "1");

    // Create the producer
    KafkaProducer<String, String> producer = KafkaProducer.create(vertx, config);

    // Send a message every 5 seconds
    vertx.setPeriodic(5000, id -> {
      String message = "Hello from Vert.x Kafka Producer at " + System.currentTimeMillis();
      KafkaProducerRecord<String, String> record =
          KafkaProducerRecord.create(KAFKA_TOPIC, message);

      // Use the Future-based API with onSuccess and onFailure
      producer.write(record)
          .onSuccess(metadata -> {
            // The success handler being called is confirmation the message was sent.
            System.out.println("Producer -> Message sent successfully to topic: " + KAFKA_TOPIC);
          })
          .onFailure(err -> {
            System.err.println("Producer -> Failed to send message: " + err.getMessage());
          });
    });

    System.out.println("Kafka Producer Verticle started.");
    startPromise.complete();
  }
}
