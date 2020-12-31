package com.kd.kdw;

import org.apache.kafka.common.header.Header;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaAndValue;
import org.apache.kafka.connect.storage.Converter;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.connect.errors.DataException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.Map;

import com.github.jcustenborder.kafka.connect.utils.config.Description;
import com.github.jcustenborder.kafka.connect.utils.config.Title;
import com.github.jcustenborder.kafka.connect.utils.config.DocumentationImportant;
import com.github.jcustenborder.kafka.connect.utils.config.DocumentationNote;
import com.github.jcustenborder.kafka.connect.utils.config.DocumentationTip;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/* 
  Example of a Custom Converter which is based on the code of the built-in String Converter.
  A converter converts from the data structure produced by the connector into its
  own format eg. a Json converter converts from a data structure into a Json string.

  Then finally, a Serializer converts from the converter's format into bytes for output
  to Kafka.
*/

@Description("This is a description of this connector and will show up in the documentation")
@DocumentationImportant("This is a important information that will show up in the documentation.")
@DocumentationTip("This is a tip that will show up in the documentation.")
@Title("Super Converter") //This is the display name that will show up in the documentation.
@DocumentationNote("This is a note that will show up in the documentation")
public class MyConverter implements Converter {
  private static Logger log = LoggerFactory.getLogger(MyConverter.class);
  
  // Use built-in serializers and deserializers to convert from String to byte
  // for output to Kafka and vice versa.
  private StringSerializer serializer = new StringSerializer();
  private StringDeserializer deserializer = new StringDeserializer();

  /* 
    Pass configuration settings to the built-in serializers.
  */  
  @Override
  public void configure(Map<String, ?> settings, boolean isKey) {
    serializer.configure(settings, isKey);
    deserializer.configure(settings, isKey);
  }

  /* 
    From Connect Data to Kafka

    Convert the Connect data structure into a string, and then, just for 
    the purpose of this example, add some characters ('@' and '*') before
    and after the string. 

    Then use the built-in Serializer to convert from string into bytes as
    required by Kafka
  */  
  @Override
  public byte[] fromConnectData(String topic, Schema schema, Object value) {
    String convertedValue = "@@" + value.toString() + "**";
    try {
      return serializer.serialize(topic, value == null ? null : convertedValue);
    } catch (SerializationException e) {
      throw new DataException("Serialization to String failed: ", e);
    }
  }

  /* 
    From Kafka to Connect Data

    Use the built-in Deserializer to convert from Kafka bytes to string. The convert
    that string into a data structure to be passed into a connector. Rightly, here we
    should strip out the characters ('@') we added in the fromConnectData(), but since
    this is just a demo, am skipping it.
  */  
  @Override
  public SchemaAndValue toConnectData(String topic, byte[] value) {
    try {
      return new SchemaAndValue(Schema.OPTIONAL_STRING_SCHEMA, deserializer.deserialize(topic, value));
    } catch (SerializationException e) {
      throw new DataException("Serialization to String failed: ", e);
    }
  }
}