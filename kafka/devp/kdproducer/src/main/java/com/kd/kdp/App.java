package com.kd.kdp;

import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;
import java.util.concurrent.ExecutionException;

/**
 * Hello world!
 *
 */
public class App 
{
    public static void main( String[] args ) throws ExecutionException, InterruptedException
    {
        System.out.println( "Hello World!" );
        String server = "192.168.65.3:30220";
        String topic = "kdprod";
    
        Producer producer = new Producer(server);
        producer.put(topic, "user1", "John");
        producer.put(topic, "user2", "Peter");
        producer.close();
      }
}

class Producer {
    // Variables
  
    private final KafkaProducer<String, String> mProducer;
    private final Logger mLogger = LoggerFactory.getLogger(Producer.class);
  
    // Constructors
  
    Producer(String bootstrapServer) {
      Properties props = producerProps(bootstrapServer);
      mProducer = new KafkaProducer<>(props);
  
      mLogger.info("Producer initialized");
    }
  
    // Public
  
    void put(String topic, String key, String value) throws ExecutionException, InterruptedException {
      mLogger.info("Put value: " + value + ", for key: " + key);
  
      ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, value);
      mProducer.send(record, (recordMetadata, e) -> {
        if (e != null) {
          mLogger.error("Error while producing", e);
          return;
        }
  
        mLogger.info("Received new meta. Topic: " + recordMetadata.topic()
            + "; Partition: " + recordMetadata.partition()
            + "; Offset: " + recordMetadata.offset()
            + "; Timestamp: " + recordMetadata.timestamp());
      }).get();
    }
  
    void close() {
      mLogger.info("Closing producer's connection");
      mProducer.close();
    }
  
    // Private
  
    private Properties producerProps(String bootstrapServer) {
      String serializer = StringSerializer.class.getName();
      Properties props = new Properties();
      props.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServer);
      props.setProperty(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, serializer);
      props.setProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, serializer);

      String username = "test";
      String password = "test123";
      String jaasTemplate = "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"%s\" password=\"%s\";";
      String jaasCfg = String.format(jaasTemplate, username, password);
      props.setProperty("sasl.jaas.config", jaasCfg);

      props.setProperty("security.protocol", "SASL_PLAINTEXT");
      props.setProperty("sasl.mechanism", "PLAIN");

      //props.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_SSL");
      //props.put(SaslConfigs.SASL_MECHANISM, "PLAIN");
      //props.put(SaslConfigs.SASL_JAAS_CONFIG, "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"" + username + "\" password=\"" + password + "\";");

      return props;
    }
  }
