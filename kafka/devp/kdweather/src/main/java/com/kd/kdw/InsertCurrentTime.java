package com.kd.kdw;

import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.connect.connector.ConnectRecord;
import org.apache.kafka.connect.data.Field;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.apache.kafka.connect.data.Struct;

import org.apache.kafka.connect.transforms.Transformation;
import org.apache.kafka.connect.transforms.util.SchemaUtil;
import org.apache.kafka.connect.transforms.util.SimpleConfig;

import com.github.jcustenborder.kafka.connect.utils.config.Description;
import com.github.jcustenborder.kafka.connect.utils.config.Title;

import java.time.format.DateTimeFormatter;  
import java.time.LocalDateTime;  
import java.util.HashMap;
import java.util.Map;

import static org.apache.kafka.connect.transforms.util.Requirements.requireMap;
import static org.apache.kafka.connect.transforms.util.Requirements.requireStruct;

/* 
  Example of a Custom Transformer which adds an extra field with the current formatted
  time, to the Connect data. It is based on the Confluent blog article to write a
  custom transformer to insert a UUID field.
*/

@Title("Super Cool Transformation")
@Description("This transformation will change one record to another record.")
public abstract class InsertCurrentTime<R extends ConnectRecord<R>> implements Transformation<R> {

  public static final String OVERVIEW_DOC =
    "Insert the current time into a connect record";

  // Constants for the name of the configuration property and the default
  // value of that property
  private interface ConfigName {
    String CT_FIELD_NAME = "ct.field.name";
    String CT_FIELD_DEFAULT = "myct";
  }

  /* 
    Define the configuration properties for the Transform.
    It takes a single property for the field name to be added.
  */
  public static final ConfigDef CONFIG_DEF = new ConfigDef()
    .define(ConfigName.CT_FIELD_NAME, ConfigDef.Type.STRING, 
            ConfigName.CT_FIELD_DEFAULT, ConfigDef.Importance.HIGH,
            "Field name for Current Time");

  private static final String PURPOSE = "Adding current time to record";

  private String fieldName;

  /* 
    Save the value from the configuration property
  */
  @Override
  public void configure(Map<String, ?> props) {
    // Initialise a configuration object from the map of properties provided
    // by the user, and from it get the name of the field to be added.
    final SimpleConfig config = new SimpleConfig(CONFIG_DEF, props);
    fieldName = config.getString(ConfigName.CT_FIELD_NAME);
  }

  /* 
    This is the primary method to be implemented in the custom Transform. We have different
    flows depending on whether the data had a schema defined or not.
  */
  @Override
  public R apply(R record) {
    if (operatingSchema(record) == null) {
      return applySchemaless(record);
    } else {
      return applyWithSchema(record);
    }
  }

  /* 
    Add a new date-time field if the data has no schema
  */
  private R applySchemaless(R record) {
    // Get a map of the current field values and clone it
    final Map<String, Object> value = requireMap(operatingValue(record), PURPOSE);
    final Map<String, Object> updatedValue = new HashMap<>(value);

    // Add the new field into the map
    updatedValue.put(fieldName, getCurrentTime());

    // Create a new data record with the updated field values
    return newRecord(record, null, updatedValue);
  }

  /* 
    Add a new date-time field if the data has a schema
  */
  private R applyWithSchema(R record) {
    // Get a map of the current schema fields and clone them. Then
    // add the new field into the schema
    final Struct value = requireStruct(operatingValue(record), PURPOSE);
    Schema updatedSchema = makeUpdatedSchema(value.schema());
    
    // Create an empty struct of field values from the schema and
    // copy the old field values into it
    final Struct updatedValue = new Struct(updatedSchema);
    for (Field field : value.schema().fields()) {
      updatedValue.put(field.name(), value.get(field));
    }

    // Add the new field into field values
    updatedValue.put(fieldName, getCurrentTime());

    // Create a new data record with the updated schema and field values
    return newRecord(record, updatedSchema, updatedValue);
  }

  /* 
    Configuration definition
  */
  @Override
  public ConfigDef config() {
    return CONFIG_DEF;
  }

  @Override
  public void close() {
  }

  /* 
    Get the current time and format it based on a format string.
    NB: there are many date-time utilities in Java, but this is now the recommended
    approach in Java 8 onwards.
  */
  private String getCurrentTime() {
    // Define date-time format
    DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
    // Get an object representing the current time
    LocalDateTime now = LocalDateTime.now();
    
    // Format the time using the defined format
    return dtf.format(now);
  }

  /* 
    Update the Schema (for the Key or Value) by cloning the old Schema object and copying
    all the old field definitions. Then we update that with any new field definitions.
  */
  private Schema makeUpdatedSchema(Schema schema) {
    // Create a new Schema object with metadata copied from the old Schema
    final SchemaBuilder builder = SchemaUtil.copySchemaBasics(schema, SchemaBuilder.struct());

    // Copy all the old field definitions
    for (Field field: schema.fields()) {
      builder.field(field.name(), field.schema());
    }

    // Add the new field for the current date-time
    builder.field(fieldName, Schema.STRING_SCHEMA);

    return builder.build();
  }

  /* 
    A transform can be applied to the Key or the Value of the message. So the custom Transform
    class is extended with two subclasses, one for the Key and another for the Value. These
    subclasses are quite simple, and have methods to return the schema, the data value or to
    clone a new Key/Value with updated schema and data values.
  */

  protected abstract Schema operatingSchema(R record);

  protected abstract Object operatingValue(R record);

  protected abstract R newRecord(R record, Schema updatedSchema, Object updatedValue);

  /* 
    Sub-class for the Key
  */
  public static class Key<R extends ConnectRecord<R>> extends InsertCurrentTime<R> {

    // Get the Key's schema
    @Override
    protected Schema operatingSchema(R record) {
      return record.keySchema();
    }

    // Get the Key's data value
    @Override
    protected Object operatingValue(R record) {
      return record.key();
    }

    // Create a new Key from the old Key object and the updated schema and data value
    @Override
    protected R newRecord(R record, Schema updatedSchema, Object updatedValue) {
      return record.newRecord(record.topic(), record.kafkaPartition(), updatedSchema, updatedValue, record.valueSchema(), record.value(), record.timestamp());
    }

  }

  /* 
    Subclass for the Value
  */
  public static class Value<R extends ConnectRecord<R>> extends InsertCurrentTime<R> {

    // Get the Value's schema
    @Override
    protected Schema operatingSchema(R record) {
      return record.valueSchema();
    }

    // Get the Value's data value
    @Override
    protected Object operatingValue(R record) {
      return record.value();
    }

    // Create a new Value from the old Value object and the updated schema and data value
    @Override
    protected R newRecord(R record, Schema updatedSchema, Object updatedValue) {
      return record.newRecord(record.topic(), record.kafkaPartition(), record.keySchema(), record.key(), updatedSchema, updatedValue, record.timestamp());
    }

  }
}

