package com.kd.kdw;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.connect.connector.Task;
import org.apache.kafka.connect.errors.ConnectException;
import org.apache.kafka.connect.source.SourceConnector;
import org.apache.kafka.connect.util.ConnectorUtils;

import com.github.jcustenborder.kafka.connect.utils.VersionUtil;
import com.github.jcustenborder.kafka.connect.utils.config.Description;
import com.github.jcustenborder.kafka.connect.utils.config.DocumentationImportant;
import com.github.jcustenborder.kafka.connect.utils.config.DocumentationNote;
import com.github.jcustenborder.kafka.connect.utils.config.DocumentationTip;
import com.github.jcustenborder.kafka.connect.utils.config.TaskConfigs;
import com.github.jcustenborder.kafka.connect.utils.config.Title;

import static com.kd.kdw.MySourceConnectorConfig.CITIES_CONFIG;

/* 
Source Connector to fetch weather data for different cities/countries from the OpenWeather 
REST API and write that data as messages into Kafka.

A Kafka Connect connector consists of three classes
1) Connector class - entry point for the connector, does simple overall lifecycle management
2) Task class - does the real work of transferring data from source to Kafka
3) Config class - manages user-provided configuration

The job of the Connector class is to initialise the Connector, receive the user configuration
and divide the workload among all the tasks for this connector, which do the actual work.
 */

@Description("This is a description of this connector and will show up in the documentation")
@DocumentationImportant("This is a important information that will show up in the documentation.")
@DocumentationTip("This is a tip that will show up in the documentation.")
@Title("Super Source Connector") //This is the display name that will show up in the documentation.
@DocumentationNote("This is a note that will show up in the documentation")
public class MySourceConnector extends SourceConnector {
  /*
    Your connector should never use System.out for logging. All of your classes should use slf4j
    for logging
 */
  private static Logger log = LoggerFactory.getLogger(MySourceConnector.class);
  private MySourceConnectorConfig config;

  @Override
  public String version() {
    return VersionUtil.version(this.getClass());
  }

  @Override
  public void start(Map<String, String> map) {
    // Initialise config object from map of properties
    config = new MySourceConnectorConfig(map);

    // Add things you need to do to setup your connector.
  }

  @Override
  public Class<? extends Task> taskClass() {
    // Return the task implementation.
    return MySourceTask.class;
  }

  /* Take the configuration provided by the user and divide up the
  work among the tasks. Partition the given cities into groups so that
  they can be passed to different tasks.
  */
  @Override
  public List<Map<String, String>> taskConfigs(int maxTasks) {
    // Get the cities input by the user. Since we have defined the cities field in 
    // the config definition as a list, the user is expected to provide the values
    // as a comma-separated string. The config object's getList() method then returns
    // it to us as a Java list of strings.
    List<String> cities = config.getList(CITIES_CONFIG);

    // Partition the cities into groups based on the number of available tasks and
    // number of cities requested
    int numGroups = Math.min(cities.size(), maxTasks);
    List<List<String>> groupedCities = ConnectorUtils.groupPartitions(cities, numGroups);

    // Populate a list of maps, one map per task. Each map contains all the
    // configured properties required by that task.
    List<Map<String, String>> tconfigs = new ArrayList<>(numGroups);
    for (List<String> taskCities: groupedCities) {
        // Create a map for a task and initialise it with all user-configured properties
        Map<String, String> tconfig = new HashMap<>(config.originalsStrings());
        // Then override just the cities property with one group of cities for this task
        // The config object expects the cities to be a comma-separated string.
        tconfig.put(CITIES_CONFIG, String.join(",", taskCities));
        // Add the map to the list
        tconfigs.add(tconfig);
    }
    return tconfigs;
  }

  @Override
  public void stop() {
    //TODO: Do things that are necessary to stop your connector.
  }

  @Override
  public ConfigDef config() {
    return MySourceConnectorConfig.config();
  }
}
