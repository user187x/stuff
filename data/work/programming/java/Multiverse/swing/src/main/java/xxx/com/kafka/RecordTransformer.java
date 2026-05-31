package xxx.com.kafka;


import io.vertx.kafka.client.consumer.KafkaConsumerRecord;

// This class is a stateless utility for transformations.
public class RecordTransformer {

  /**
   * Transforms the value of a Kafka record into an uppercase string.
   * This method is static, making it a pure function that can be used as a method reference.
   * @param record The incoming Kafka record.
   * @return A new string with the transformed message.
   */
  public static String processRecord(KafkaConsumerRecord<String, String> record) {
    String originalValue = record.value();
    // Example transformation: make uppercase and add a prefix.
    return "PROCESSED::" + originalValue.toUpperCase();
  }
}
