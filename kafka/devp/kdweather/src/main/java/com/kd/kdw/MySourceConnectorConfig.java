package com.kd.kdw;

import org.apache.kafka.common.config.AbstractConfig;
import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.common.config.ConfigDef.Type;
import org.apache.kafka.common.config.ConfigDef.Importance;
import com.github.jcustenborder.kafka.connect.utils.config.ConfigKeyBuilder;

import java.util.List;
import java.util.Map;

/* 
The Config class defines the structure of the configuration parameters for the 
connector and holds the user-provided configuration values

The configuration structure is defined in a ConfigDef object.
*/

public class MySourceConnectorConfig extends AbstractConfig {

  public static final String TOPIC_CONFIG = "topic";
  private static final String TOPIC_DOC = "Topic to write to";

  public static final String SLEEP_CONFIG = "sleep.seconds";
  private static final String SLEEP_DOC = "Time in seconds that connector will wait until querying api again";

  public static final String API_KEY_CONFIG = "api.key";
  private static final String API_KEY_DOC = "API key for OpenWeatherMap";

  public static final String CITIES_CONFIG = "cities";
  private static final String CITIES_DOC = "List of cities for weather";

  private final String topic;
  private final int sleepInSeconds;
  private final String apiKey;
  private final List<String> cities;

  /* 
    Constructor to create a Config object from a map of configuration properties
  */
  public MySourceConnectorConfig(Map<?, ?> originals) {
    super(config(), originals);
 
    this.topic = this.getString(TOPIC_CONFIG);
    this.sleepInSeconds = this.getInt(SLEEP_CONFIG);

    this.apiKey = this.getString(API_KEY_CONFIG);
    this.cities = this.getList(CITIES_CONFIG);
  }

  /* 
    Define the structure of the configuration data with fields, types, default values,
    help text etc.

    We use configuration properties for OpenWeather API Key, Polling Sleep interval,
    Kafka topic and list of cities/countries for which weather data should be fetched.
  */
  public static ConfigDef config() {
    // My API Key for the Open Weather API. 
    // NB: In a real connector, we would use the user provided key and not 
    // provide any default value
    String defaultAppKey = "ac7def1286e4bfa2d2a65eca9cd87c35";
    Integer defaultSleep = 60;

    return new ConfigDef()
        // Example of long-form of specifying a configuration field
        .define(
          ConfigKeyBuilder.of(TOPIC_CONFIG, Type.STRING)
          .documentation(TOPIC_DOC)
          .importance(Importance.HIGH)
          .build()
        )
        // Other configuration fields are defined more concisely
        .define(SLEEP_CONFIG, Type.INT, defaultSleep, Importance.MEDIUM, SLEEP_DOC)
        .define(API_KEY_CONFIG, Type.STRING, defaultAppKey, Importance.HIGH, API_KEY_DOC)
        // We define the cities field in the Config as a List, which means the user provides
        // it as a comma-separated string. But when we fetch it from the config we use the
        // getList() method and get it as a Java list of strings.
        .define(CITIES_CONFIG, Type.LIST, Importance.MEDIUM, CITIES_DOC);
  }

  public String getApiKey() {
    return apiKey;
  }

  public List<String> getCities() {
    return cities;
  }
}
