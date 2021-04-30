package com.ketan

import java.sql.Timestamp
import java.util.Date

import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.expressions.{UserDefinedFunction, Window}
import org.apache.spark.sql._
import org.apache.spark.sql.types._
import org.apache.spark.sql.functions._
import org.apache.spark.sql.streaming._
import org.apache.avro.Schema

// For Processing Avro Data
import za.co.absa.abris.avro.read.confluent.SchemaManagerFactory
import za.co.absa.abris.avro.read.confluent.SchemaManager
import za.co.absa.abris.avro.registry.SchemaSubject
import za.co.absa.abris.config.AbrisConfig
import org.apache.spark.sql.avro.SchemaConverters.toAvroType


/* import za.co.absa.abris.avro.read.confluent.SchemaManager
import za.co.absa.abris.avro.schemas.policy.SchemaRetentionPolicies.{RETAIN_ORIGINAL_SCHEMA, RETAIN_SELECTED_COLUMN_ONLY}
import za.co.absa.abris.avro.AvroSerDe._ */

//-------------------------------------------
// Utility Functions
//-------------------------------------------
object Util {
  //-------------------------------------------
  // Read a stream from Kafka in Json format
  //-------------------------------------------
  def readKafkaJson( 
      spark: SparkSession,  
      brokers: String, 
      topic: String, 
      jsonSchema: StructType,
      offset: Integer = 0) : DataFrame = {

    import spark.implicits._
    
    // If no starting offset for the topic is specified, use the default value to set
    // the offset to "earliest". If an offset is specified, format the value to read from that
    // offset within that topic
    val startingOffsets = if (offset == 0) "earliest" else s"""{"$topic": {"0": $offset}}"""
  
    // Read a stream from Kafka
    val df = spark
      .readStream
      .format("kafka")
      .option("kafka.bootstrap.servers", brokers)
      .option("subscribe", topic)
      .option("startingOffsets", startingOffsets)
      //.option("startingOffsets", """ {"test-file-string-four":{"0":4}} """)
      // .option("startingOffsets", "earliest") // read data from the start of the stream
      .option("kafka.sasl.mechanism", "PLAIN")
      .option("kafka.security.protocol", "SASL_PLAINTEXT")
      .option("kafka.sasl.jaas.config", """org.apache.kafka.common.security.plain.PlainLoginModule required username="test" password="test123";""")
      .load()
 
    // The Key and Value are binary data, so deserialise them into strings
    // The Value gets deserialised into a string which we know is in JSON format
    val df1 = df.selectExpr("CAST(key AS STRING)", 
                    "CAST(value AS STRING)", 
                    "offset", 
                    "CAST(timestamp AS TIMESTAMP)")
      .as[(String, String, Long, java.sql.Timestamp)]

    // val jsonOptions = Map("timestampFormat" -> "yyyy-MM-dd'T'HH:mm:ss.sss'Z'")
    // We don't use any options while parsing the JSON. We are not using the option
    // to parse time-value strings based on a format and convert to timestamp but have 
    // left the commented code here as an example in case we want to use it later. For 
    // now, we will read such values as a string and let it create a string column in the
    // dataframe. Then, later, We will parse that column and convert to timestamp
    val jsonOptions = Map.empty[String, String]

    // jsonSchema defines the structure of the data so we can deserialise that JSON into its fields
    // The dataframe will now contain columns one level down, nested under a "struct" structure
    val df2 = df1.select(from_json($"value", jsonSchema, jsonOptions).as("struct"),
                $"offset".as("msgoffset"),
                $"timestamp".as("msgtime"))

    // Flatten it out so that those columns now appear at the top-level
    val df3 = df2.selectExpr("struct.*", "msgoffset", "msgtime")
    df3.printSchema

    return df3
  }

  //-------------------------------------------
  // Read a streaming dataframe to Kafka in Json format
  //-------------------------------------------
  def writeKafkaJson( 
      brokers: String, 
      topic: String, 
      mode: String,
      checkpointLocation: String, 
      df:DataFrame,
      keyName: String) : StreamingQuery = {

    // A dataframe must have string or binary columns named 'key' and 'value'
    // so that it can be written to a Kafka topic. 
    // The 'value' is set to a JSON string serialised from all fields in the dataframe 
    // struct(*) returns a list of all the column structTypes in the dataframe
    val dfout = df.select(
      col(keyName).cast(StringType).as("key"),
      to_json(struct("*")).as("value")
    )

    // Write the dataframe to a Kafka topic. A checkpoint location must be specified
    val kafkaOutput = dfout.writeStream
      .format("kafka")
      .option("kafka.bootstrap.servers", brokers)
      .option("topic", topic)
      .option("kafka.sasl.mechanism", "PLAIN")
      .option("kafka.security.protocol", "SASL_PLAINTEXT")
      .option("kafka.sasl.jaas.config", """org.apache.kafka.common.security.plain.PlainLoginModule required username="test" password="test123";""")
      .option("checkpointLocation", checkpointLocation)
      .outputMode(mode)
      .start()

     return kafkaOutput
  }

  //-------------------------------------------
  // Read a streaming dataframe to the Console
  //-------------------------------------------
  def writeConsole( 
      mode: String,
      df:DataFrame) : StreamingQuery = {

    // We cannot call .show() on a streaming dataframe. Instead we
    // write a streaming query that outputs the content of the Dataframe to the console
    val consoleOutput = df.writeStream
      .outputMode(mode)
      .format("console")
      .start()

    return consoleOutput
  }

  //-------------------------------------------
  // Read a stream from Kafka in Avro format
  //-------------------------------------------
  def readKafkaAvro(
      spark: SparkSession,  
      brokers: String, 
      topic: String,
      schemaRegistryUrl: String,
      offset: Integer = 0) : DataFrame = {

    val schemaManager = SchemaManagerFactory.create(Map(AbrisConfig.SCHEMA_REGISTRY_URL -> schemaRegistryUrl))
    val schemaExists = schemaManager.exists(SchemaSubject.usingTopicNameStrategy("foo"))

    // If no starting offset for the topic is specified, use the default value to set
    // the offset to "earliest". If an offset is specified, format the value to read from that
    // offset within that topic
    val startingOffsets = if (offset == 0) "earliest" else s"""{"$topic": {"0": $offset}}"""

    // Read a stream from Kafka and deserialise it in Confluent's Avro format by fetching
    // the schema definition from the Schema Registry
    val df = spark
      .readStream
      .format("kafka")
      .option("kafka.bootstrap.servers", brokers)
      .option("subscribe", topic)
      .option("startingOffsets", startingOffsets)
      .option("kafka.sasl.mechanism", "PLAIN")
      .option("kafka.security.protocol", "SASL_PLAINTEXT")
      .option("kafka.sasl.jaas.config", """org.apache.kafka.common.security.plain.PlainLoginModule required username="test" password="test123";""")
      .load()

    val abrisConfig = AbrisConfig
      .fromConfluentAvro
      .downloadReaderSchemaByLatestVersion
      .andTopicNameStrategy(topic)
      .usingSchemaRegistry(schemaRegistryUrl)

    import za.co.absa.abris.avro.functions.from_avro
    val dfser = df.select(from_avro(col("value"), abrisConfig) as 'data)
    val dfflat = dfser.selectExpr("data.*")

    return dfflat
  }

  // generate schema for dataframe
  def generateSchema(dataFrame: DataFrame): Schema = {
    val allColumns = struct(dataFrame.columns.map(c => dataFrame(c)): _*)
    val expression = allColumns.expr
    toAvroType(expression.dataType, expression.nullable)
  }

  // register schema with topic name strategy
  def registerSchema(topic: String, schema: Schema, schemaManager: SchemaManager): Int = {
    val subject = SchemaSubject.usingTopicNameStrategy(topic, isKey=false) // Use isKey=true for the key schema and isKey=false for the value schema
    schemaManager.register(subject, schema)
  }


  //-------------------------------------------
  // Write a streaming dataframe to Kafka in Avro format
  //-------------------------------------------
  def writeKafkaAvro(
      brokers: String, 
      topic: String,
      schemaRegistryUrl: String,
      mode: String,
      checkpointLocation: String, 
      df:DataFrame) : StreamingQuery = {

    val schemaRegistryClientConfig = Map(AbrisConfig.SCHEMA_REGISTRY_URL -> schemaRegistryUrl)
    val schemaManager = SchemaManagerFactory.create(schemaRegistryClientConfig)

    val schema = generateSchema(df)
    val schemaId = registerSchema(topic, schema, schemaManager)

    val abrisConfig = AbrisConfig
      .toConfluentAvro
      .downloadSchemaById(schemaId)
      .usingSchemaRegistry(schemaRegistryUrl)

    import za.co.absa.abris.avro.functions.to_avro

     // to serialize all columns in dataFrame we need to put them in a spark struct
    val allColumns = struct(df.columns.head, df.columns.tail: _*)
    val dfavro = df.select(to_avro(allColumns, abrisConfig) as 'value)

    // Write the stream to Kafka, after serialising it in Confluent's Avro format. Create the
    // schema definition in the Schema Registry if it doesn't exist
    val kafkaOutput = dfavro
      .writeStream
      .format("kafka")
      .outputMode(mode)
      .option("kafka.bootstrap.servers", brokers)
      .option("topic", topic)
      .option("kafka.sasl.mechanism", "PLAIN")
      .option("kafka.security.protocol", "SASL_PLAINTEXT")
      .option("kafka.sasl.jaas.config", """org.apache.kafka.common.security.plain.PlainLoginModule required username="test" password="test123";""")
      .option("checkpointLocation", checkpointLocation)
      .start()

    return kafkaOutput
  }

  //-------------------------------------------
  // Append a Timestamp column after converting an epoch-time Long value in milliseconds 
  //-------------------------------------------
  def LongToTimestamp(longColName: String, tsColName: String)
                     (df: DataFrame): DataFrame = {
    // The given column contains a Unix timestamp in milliseconds, but is a long
    // datatype in the input data. So convert it to seconds which is what Spark expects
    // timestamps to be, and then cast it to a timestamp
    df.withColumn(tsColName, (col(longColName) / 1000).cast(TimestampType))
  }

  //-------------------------------------------
  // Append a Timestamp column after converting a String time value 
  //-------------------------------------------
  def StrToTimestamp(strColName: String, tsColName: String, fmt: String)
                    (df: DataFrame): DataFrame = {
    // Use the given format codes to parse the string and convert it
    df.withColumn(tsColName, to_timestamp(col(strColName), fmt))
  }

  //-------------------------------------------
  // Given a Timestamp column, extract different date/time components
  // from it add a column for each one: a Date column, a string formatted 
  // time, as well as month, day, hour, minute etc
  //-------------------------------------------
  def ExpandTimestamp(tsColName: String, prefixStr: String = "")
                     (df: DataFrame) : DataFrame = {
    // If prefix is not specified, then assume that the timestamp column name
    // is of the form "<prefix>_ts"
    val prefix = if (prefixStr == "") tsColName.substring (0, tsColName.indexOf ("_ts")) 
                 else prefixStr
    val tsCol = col(tsColName)
    df.withColumn (prefix + "_date", tsCol.cast("date"))
      .withColumn (prefix + "_time", date_format(tsCol, "H:m:s"))
      .withColumn (prefix + "_month", month(tsCol))
      .withColumn (prefix + "_day", dayofmonth(tsCol))
      .withColumn (prefix + "_hour", hour(tsCol))
      .withColumn (prefix + "_min", minute(tsCol))
  }

  def ExpandStruct (df:DataFrame, colName: String, schema:StructType) : List[Column] = {
    val names = schema.fieldNames.toList
    println(s"========== Schema ========== : $names" )
    schema.map(s => (println (s"+++", s.name, s.dataType)))

    val colList = names.map(name => {
      df.col(colName).getItem(name).as(name)
    })

    return colList
  }

  def ExpandMap (df:DataFrame, keyName: String, schema:MapType) : List[Column] = {
    val structSchema = schema.valueType.asInstanceOf[StructType]
    val structList = Util.ExpandStruct (df, "value", structSchema)
    val keyCol:Column = df.col("key").as(keyName)
    val colList = keyCol :: structList
    return colList
  }
}
