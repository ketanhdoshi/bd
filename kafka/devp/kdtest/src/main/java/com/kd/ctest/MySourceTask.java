package com.kd.ctest;

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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.kd.ctest.MySourceConnectorConfig.API_URL_CONFIG;
import static com.kd.ctest.MySourceConnectorConfig.TOPIC_CONFIG;
import static com.kd.ctest.MySourceConnectorConfig.SLEEP_CONFIG;

public class MySourceTask extends SourceTask {
  /*
    Your connector should never use System.out for logging. All of your classes should use slf4j
    for logging
 */
  static final Logger log = LoggerFactory.getLogger(MySourceTask.class);
  static final Schema VALUE_SCHEMA = SchemaBuilder.struct().name("kd_schema")
            .field("name", Schema.STRING_SCHEMA)
            .field("age", Schema.INT32_SCHEMA)
            .build();
        
  private MySourceConnectorConfig config;
  private int sleepInSeconds;
  private String apiUrl;
  private String topic;
  private CountDownLatch stopLatch = new CountDownLatch(1);
  private boolean shouldWait = false;
  private int randNum = 0;

  @Override
  public String version() {
    return VersionUtil.version(this.getClass());
  }

  @Override
  public void start(Map<String, String> map) {
    log.info("Starting source task with properties {}", map);

    config = new MySourceConnectorConfig(map);
    apiUrl = config.getString(API_URL_CONFIG);
    sleepInSeconds = config.getInt(SLEEP_CONFIG);
    topic = config.getString(TOPIC_CONFIG);  
  }

  @Override
  public List<SourceRecord> poll() throws InterruptedException {
    boolean shouldStop = false;
    log.info("kdinfo in poll");
    if (shouldWait) {
        log.info("kdinfo Waiting for {} seconds for the next poll", sleepInSeconds);
        shouldStop = stopLatch.await(sleepInSeconds, TimeUnit.SECONDS);
    }
    if (!shouldStop) {
        log.info("kdinfo Started new polling");
        shouldWait = true;
        return getSourceRecords();
    } else {
        log.info("Received signal to stop, didn't poll anything");
        return null;
    }
  }

  @Override
  public void stop() {
    log.info("kdinfo Stopping source task");
    stopLatch.countDown();
  }

  private List<SourceRecord> getSourceRecords() {
    SourceRecord record = new SourceRecord(
            null,
            null,
            topic,
            VALUE_SCHEMA,
            getRandomLongFromApi());
    log.info("kdinfo got record");
    return Collections.singletonList(record);
  }

  private Object getRandomLongFromApi() {
    randNum += 25;
    log.info("kdinfo - rand num is {}", randNum);

    Struct struct = new Struct(VALUE_SCHEMA)
      .put("name", "Bobby Liskov")
      .put("age", randNum);

    return struct;
  }
}