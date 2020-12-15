package com.kd.kdc;

import org.apache.kafka.common.config.AbstractConfig;
import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.common.config.ConfigDef.Type;
import org.apache.kafka.common.config.ConfigDef.Importance;
import com.github.jcustenborder.kafka.connect.utils.config.ConfigKeyBuilder;
import java.util.Map;



public class MySourceConnectorConfig extends AbstractConfig {

  public static final String API_URL_CONFIG = "api.url";
  private static final String API_URL_DOC = "API URL";

  public static final String TOPIC_CONFIG = "topic";
  private static final String TOPIC_DOC = "Topic to write to";

  public static final String SLEEP_CONFIG = "sleep.seconds";
  private static final String SLEEP_DOC = "Time in seconds that connector will wait until querying api again";

  private final String url;
  private final String topic;
  private final int sleepInSeconds;

  public MySourceConnectorConfig(Map<?, ?> originals) {
    super(config(), originals);
 
    this.url = this.getString(API_URL_CONFIG);
    this.topic = this.getString(TOPIC_CONFIG);
    this.sleepInSeconds = this.getInt(SLEEP_CONFIG);
  }

  public static ConfigDef config() {
    return new ConfigDef()
        .define(
          ConfigKeyBuilder.of(API_URL_CONFIG, Type.STRING)
          .documentation(API_URL_DOC)
          .importance(Importance.HIGH)
          .build()
  )
  .define(
      ConfigKeyBuilder.of(TOPIC_CONFIG, Type.STRING)
          .documentation(TOPIC_DOC)
          .importance(Importance.HIGH)
          .build()
  )
  .define(
    ConfigKeyBuilder.of(SLEEP_CONFIG, Type.INT)
          .documentation(SLEEP_DOC)
          .importance(Importance.MEDIUM)
          .build()
  );
  }
}
