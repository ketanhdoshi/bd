package com.kd.kdw;

import org.apache.kafka.connect.data.Field;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.errors.ConnectException;
import org.apache.kafka.connect.source.SourceRecord;
import org.apache.kafka.connect.source.SourceTask;
import com.github.jcustenborder.kafka.connect.utils.VersionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.kd.kdw.MySourceConnectorConfig.TOPIC_CONFIG;
import static com.kd.kdw.MySourceConnectorConfig.SLEEP_CONFIG;

/* 
The Task class does the actual work of the connector. It receives its configuration
from the Connector class, which gives it the work assigned to it. It is responsible
for fetching data from the source system and preparing it for writing into Kafka.

It populates three pieces of information for Kafka Connect:
1) Schema - the structure of the data written to Kafka with fields and data types
2) Struct - the data itself to be written to Kafka with values of each field
3) SourceRecord - this packages the Schema and Struct data with metadata such as 
    the Kafka topic etc
*/

public class MySourceTask extends SourceTask {
  /*
    Your connector should never use System.out for logging. All of your classes should use slf4j
    for logging
  */
  static final Logger log = LoggerFactory.getLogger(MySourceTask.class);

  // Define the structure of the schema for the message Key to be written to Kafka
static final Schema KEY_SCHEMA = SchemaBuilder
            .struct().name("com.kd.kdw.weather_key_schema")
            .field("id", Schema.INT64_SCHEMA)
            .build();

            // Define the structure of the schema for the message Value to be written to Kafka
  static final Schema VALUE_SCHEMA = SchemaBuilder
            .struct().name("com.kd.kdw.weather_schema")
            .field("lon", Schema.FLOAT32_SCHEMA)
            .field("lat", Schema.FLOAT32_SCHEMA)
            .field("visibility", Schema.INT64_SCHEMA)
            .field("name", Schema.STRING_SCHEMA)
            .build();

  private MyOpenWeather client;
  private MySourceConnectorConfig config;
  private int sleepInSeconds;
  private String topic;
  private CountDownLatch stopLatch = new CountDownLatch(1);
  private boolean shouldWait = false;

  @Override
  public String version() {
    return VersionUtil.version(this.getClass());
  }

  // Use the map of configuration properties to initialise its local variables
  @Override
  public void start(Map<String, String> map) {
    log.info("Starting source task with properties {}", map);

    // Create a configuration object and initialise local variables
    config = new MySourceConnectorConfig(map);
    sleepInSeconds = config.getInt(SLEEP_CONFIG);
    topic = config.getString(TOPIC_CONFIG);
    
    // Pass the remaining configuration properties to the OpenWeather API client
    client = new MyOpenWeather(config);
  }
  
  /* 
    This is where the real work is done. This method gets called at periodic intervals
    so that it can fetch data from the source system and return a list of message records
    to be written to Kafka.
  */
  @Override
  public List<SourceRecord> poll() throws InterruptedException {
    boolean shouldStop = false;
    log.info("kdinfo in poll");
    if (shouldWait) {
        // Wait for the configured number of seconds
        log.info("kdinfo Waiting for {} seconds for the next poll", sleepInSeconds);
        shouldStop = stopLatch.await(sleepInSeconds, TimeUnit.SECONDS);
    }
    if (!shouldStop) {
        // Wait timer has expired, so its time to do the work
        log.info("kdinfo Started new polling");

        // Fetch weather data from OpenWeather. We get back a list of result data
        List<Weather> res = client.getCurrentWeather();

        shouldWait = true;
        // Format the result data into Source Records as expected by the 
        // Kafka Connect framework
        return getSourceRecords(res);
    } else {
        // We've been asked to terminate
        log.info("Received signal to stop, didn't poll anything");
        return null;
    }
  }

  @Override
  public void stop() {
    log.info("kdinfo Stopping source task");
    stopLatch.countDown();
  }

  /* 
    Populate a list of SourceRecords using the weather data result
  */
  private List<SourceRecord> getSourceRecords(List<Weather> res) {
    log.info("kdinfo got record");
    // Fill a SourceRecord from each weather result object and gather them 
    // into a list
    return res.stream().map(weatherInfo -> getWeatherRecord(weatherInfo))
                       .collect(Collectors.toList());
  }

  /* 
    Populate one SourceRecord from one weather data result
  */
  private SourceRecord getWeatherRecord(Weather winfo) {
    // Fill a Struct object with the values from the weather data
    // This is the data that will be written into Kafka and that conforms
    // to the Schema defined earlier
    Struct key_struct = new Struct(KEY_SCHEMA)
      .put("id", winfo.getId());
    
    Struct val_struct = new Struct(VALUE_SCHEMA)
      .put("name", winfo.getName())
      .put("lon", winfo.getCoord().getLon())
      .put("lat", winfo.getCoord().getLat())
      .put("visibility", winfo.getVisibility());
    
    // Fill a Source Record object using the schema definition, data values and metadata such as
    // Kafka topic
    SourceRecord record = new SourceRecord(
            sourcePartition(winfo), null, topic,
            KEY_SCHEMA, key_struct,
            VALUE_SCHEMA, val_struct);
    return record;
  }

  private Map<String, ?> sourceOffset() {
		return new HashMap<>();
  }
  
  private Map<String, ?> sourcePartition(Weather weather) {
		Map<String, String> sourcePartition = new HashMap<>();
		sourcePartition.put("location", weather.getName());
		return sourcePartition;
	}
}