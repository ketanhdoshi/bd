package com.ketan

import java.sql.Timestamp
import java.util.Date

import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.expressions.UserDefinedFunction
import org.apache.spark.sql._
import org.apache.spark.sql.types._
import org.apache.spark.sql.functions._
import org.apache.spark.sql.streaming._

// For Processing Avro Data
import za.co.absa.abris.avro.read.confluent.SchemaManager
import za.co.absa.abris.avro.schemas.policy.SchemaRetentionPolicies.{RETAIN_ORIGINAL_SCHEMA, RETAIN_SELECTED_COLUMN_ONLY}
import za.co.absa.abris.avro.AvroSerDe._

//-------------------------------------------
// Utility Functions
//-------------------------------------------
object MyUtil {
  //-------------------------------------------
  // Read a stream from Kafka in Json format
  //-------------------------------------------
  def readKafkaJson( 
      spark: SparkSession,  
      brokers: String, 
      topic: String, 
      jsonSchema: StructType) : DataFrame = {

    import spark.implicits._
    
    // Read a stream from Kafka
    val df = spark
      .readStream
      .format("kafka")
      .option("kafka.bootstrap.servers", brokers)
      .option("subscribe", topic)
      .option("startingOffsets", """ {"kd-file-string-topic":{"0":4}} """)
      // .option("startingOffsets", "earliest") // read data from the start of the stream
      .load()
 
    // The Key and Value are binary data, so deserialise them into strings
    // The Value gets deserialised into a string which we know is in JSON format
    val df1 = df.selectExpr("CAST(key AS STRING)", 
                    "CAST(value AS STRING)", 
                    "offset", 
                    "CAST(timestamp AS TIMESTAMP)")
      .as[(String, String, Long, java.sql.Timestamp)]

    // val jsonOptions = Map("timestampFormat" -> "yyyy-MM-dd'T'HH:mm:ss.sss'Z'")
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
      df:DataFrame) : StreamingQuery = {

    // A dataframe must have string or binary columns named 'key' and 'value'
    // so that it can be written to a Kafka topic. 
    // The 'value' is set to a JSON string serialised from all fields in the dataframe 
    // struct(*) returns a list of all the column structTypes in the dataframe
    // ***** TDOD **** FULL_NAME is hardcoded
    val dfout = df.selectExpr(
      "CAST(FULL_NAME AS STRING) AS key", 
      "to_json(struct(*)) AS value")

    // Write the dataframe to a Kafka topic. A checkpoint location must be specified
    val kafkaOutput = dfout.writeStream
      .format("kafka")
      .option("kafka.bootstrap.servers", brokers)
      .option("topic", topic)
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
      schemaRegistryUrl: String) : DataFrame = {

    val schemaRegistryConfs = Map(
      SchemaManager.PARAM_SCHEMA_REGISTRY_URL          -> schemaRegistryUrl,
      SchemaManager.PARAM_SCHEMA_REGISTRY_TOPIC        -> topic,
      SchemaManager.PARAM_VALUE_SCHEMA_NAMING_STRATEGY -> SchemaManager.SchemaStorageNamingStrategies.TOPIC_NAME,
      SchemaManager.PARAM_VALUE_SCHEMA_ID              -> "latest" // otherwise, just specify an id
    )

    // Read a stream from Kafka and deserialise it in Confluent's Avro format by fetching
    // the schema definition from the Schema Registry
    val df = spark
      .readStream
      .format("kafka")
      .option("kafka.bootstrap.servers", brokers)
      .option("subscribe", topic)
      .option("startingOffsets", "earliest") // read data from the start of the stream
      // .load()
      .fromConfluentAvro("value", None, Some(schemaRegistryConfs))(RETAIN_SELECTED_COLUMN_ONLY)

    return df
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

    // Name of schema and namespace to create under the subject
    val destSchema = "kdschema"
    val destSchemaNamespace = "kdnamespace"

    val schemaRegistryConfs = Map(
      SchemaManager.PARAM_SCHEMA_REGISTRY_URL          -> schemaRegistryUrl,
      SchemaManager.PARAM_VALUE_SCHEMA_NAMING_STRATEGY -> SchemaManager.SchemaStorageNamingStrategies.TOPIC_NAME
    )

    // Write the stream to Kafka, after serialising it in Confluent's Avro format. Create the
    // schema definition in the Schema Registry if it doesn't exist
    val kafkaOutput = df.toConfluentAvro(topic, "kdis", "kdisns")(schemaRegistryConfs)
      .writeStream
      .format("kafka")
      .outputMode(mode)
      .option("kafka.bootstrap.servers", brokers)
      .option("topic", topic)
      .option("checkpointLocation", checkpointLocation)
      .start()

     return kafkaOutput
  }

  //-------------------------------------------
  // Convert a epoch-time value in milliseconds from a Long to a Timestamp
  //-------------------------------------------
  def LongToTimestamp(timeCol: Column) : Column = {
     // The given column contains a Unix timestamp in milliseconds, but is a long
     // datatype in the input data. So convert it to seconds which is what Spark expects
     // timestamps to be, and then cast it to a timestamp
     return (timeCol / 1000).cast(TimestampType)
  }

  //-------------------------------------------
  // Convert a time value from a String to a Timestamp
  //-------------------------------------------
  def StrToTimestamp(timeCol: Column, fmt: String) : Column = {
     // Use the given format codes to parse the string and convert it
     return (to_timestamp(timeCol, fmt))
  }

  //-------------------------------------------
  // Given a Dataframe and a Timestamp column, expand the Dataframe
  // by extracting info from the Timestamp and adding columns for: 
  // a Date column, a string formatted time, and components like 
  // month, day, hour, minute etc
  //-------------------------------------------
  def ExpandTimestamp(df:DataFrame, timeCol: Column) : DataFrame = {
    val timeDf = df.withColumn ("evt_date", timeCol.cast("date"))
                   .withColumn ("evt_time", date_format(timeCol, "H:m:s"))
                   .withColumn ("evt_month", month(timeCol))
                   .withColumn ("evt_day", dayofmonth(timeCol))
                   .withColumn ("evt_hour", hour(timeCol))
                   .withColumn ("evt_min", minute(timeCol))
    return timeDf
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
    val structList = MyUtil.ExpandStruct (df, "value", structSchema)
    val keyCol:Column = df.col("key").as(keyName)
    val colList = keyCol :: structList
    return colList
  }
}

//-------------------------------------------
// Main Function
//-------------------------------------------
object KafkaInt {
  def main(args: Array[String]): Unit = {
    new StreamsApp("kafka:29092", "http://schema-registry:8090").process()
  }
}

//-------------------------------------------
// Class for our Streaming Application
//-------------------------------------------
class StreamsApp(brokers: String, schemaRegistryUrl: String) {

//-------------------------------------------
// Define and register a UDF to calculate the elapsed time difference in seconds
// between the current time and the message time
//-------------------------------------------
private def calcAgo (df:DataFrame) : DataFrame = {

    // Define a UDF to calculate how long ago (in seconds) the message timestamp was
    // Define a regular Scala lambda function
    val agoFunc: java.sql.Timestamp => Long = msgtime => {
      val date: Date = new Date()
      val nowTime: java.sql.Timestamp = new Timestamp (date.getTime())
      val ago: Long = (nowTime.getTime() - msgtime.getTime()) / 1000
      ago
    }

    // and wrap that function into a Spark UDF
    val agoUdf: UserDefinedFunction = udf(agoFunc, DataTypes.LongType)

    // Apply the UDF to the 'msgtime' column to create a new column 'ago'
    val df1 = df.withColumn("ago", agoUdf.apply(col("msgtime")))
    return df1
  }

//-------------------------------------------
// End-to-end example of reading and writing a Kafka Avro topic
//-------------------------------------------
private def doAvro (spark: SparkSession): Unit = {
    import spark.implicits._

    var dfAvro = MyUtil.readKafkaAvro (spark, brokers, "kd-impressions", schemaRegistryUrl)

    dfAvro = dfAvro.withColumn("PosTime", MyUtil.LongToTimestamp($"impresssiontime"))

    // Group the data by time window and user and compute the count of each group
    val windowedCounts = dfAvro.groupBy(
          window($"PosTime", "10 minutes", "5 minutes"),
          $"userid"
    ).count()
    // .orderBy("window") ***** OrderBy increases processing time a lot, so skipping it for now
 
    //val kafkaOutputAvro = MyUtil.writeKafkaAvro (brokers, "kd-impressions-spark", 
    //              schemaRegistryUrl, "append", "/data/checkpoints_avro", dfAvro)
    val consoleOutputAvro = MyUtil.writeConsole ("complete", windowedCounts)
  }

//-------------------------------------------
// End-to-end example of reading and writing a Kafka Json topic
//-------------------------------------------
private def doJson (spark: SparkSession): Unit = {
    import spark.implicits._

    // Define the structure of the data so we can deserialise that JSON into its fields
    val custSchema = new StructType()
      .add("FULL_NAME", DataTypes.StringType)
      .add("CLUB_STATUS", DataTypes.StringType)
      .add("EMAIL", DataTypes.StringType)
      .add("MESSAGE", DataTypes.StringType)

    val dfJson = MyUtil.readKafkaJson (spark, brokers, "UNHAPPY_PLATINUM_CUSTOMERS", custSchema)
    val dfflat = calcAgo (dfJson)

    // Compute aggregates
    val dfcount = dfflat.groupBy ($"FULL_NAME").count()

    println(s"========== SHOW ==========" )
    val kafkaOutputJson = MyUtil.writeKafkaJson (brokers, "kdcount", "complete", "/data/checkpoints", dfcount)
    val consoleOutputJson = MyUtil.writeConsole ("complete", dfcount)
  }

//-------------------------------------------
// End-to-end example of reading a complex nested Kafka Json topic,
// processing all the datatypes and writing the results to the console
//-------------------------------------------
private def doNestedJson (spark: SparkSession): Unit = {
    import spark.implicits._

    // Define the structure of the data so we can deserialise that JSON into its fields
    val nestedSchema = new StructType()
       .add("event_time", LongType)
       .add("register_time", LongType) 
       .add("start_time_str", StringType)
       .add("readings", ArrayType(IntegerType))
       .add("geo", 
          new StructType()
            .add("lat", DoubleType)
            .add("long", DoubleType)
       )
       .add("source",
          MapType(
             StringType,
             new StructType()
               .add("id", IntegerType)
               .add("ip", StringType)
               .add("temp", IntegerType)
          )
       )
       .add("dc_id", StringType)

    // Read the JSON data
    val dfJson = MyUtil.readKafkaJson (spark, brokers, "kd-file-string-topic", nestedSchema)

    // Process the time-based columns
    val fmt = "yyyy-MM-dd'T'HH:mm:ss.sss'Z'"
    var timeDf = dfJson.select(
                      $"event_time" as "evt_long",
                      MyUtil.StrToTimestamp ($"start_time_str", fmt) as "start_time",
                      MyUtil.LongToTimestamp ($"event_time") as "evt_timestamp")
    timeDf = MyUtil.ExpandTimestamp (timeDf, $"evt_timestamp")
    timeDf.printSchema

    var arrayDf = dfJson.select($"dc_id", 
                        $"readings")
    arrayDf.createOrReplaceTempView("array_table")
    val sqlArraySum = "select dc_id, readings, aggregate(readings, 0, (acc, value) -> acc + value) as sum from array_table"
    arrayDf = spark.sql(sqlArraySum)

    val foo = nestedSchema("geo").dataType.asInstanceOf[StructType]
    //MyUtil.GetSchemaFields (nestedSchema)
    val geoList = MyUtil.ExpandStruct (dfJson, "geo", foo)
    //MyUtil.GetSchemaFields (dfJson.schema(Set("dc_id")))
    //MyUtil.GetSchemaFields (timeDf.schema)
    
    // This is just a hack, because we're generating the input geo data where the lat and long
    // are doubles between 0 and 1. So we convert it here into decimal values with 2 decimals
    val geoFunc = (geoCol: Column) => {
       round (geoCol * 100, 2)
    }

    val geoDf = dfJson.select($"dc_id",
                        geoList(0),
                        geoList(1))
                        //$"geo".getItem("lat") as "lat",
                        //$"geo".getItem("long") as "long")
                        .withColumn ("geolat", geoFunc ($"lat"))
                        .withColumn ("geolong", geoFunc ($"long"))


    // Explode the column 'source', which is a nested Map structure, into separate rows
    val explodedDF = dfJson.select($"dc_id", explode($"source"))
    val bar = nestedSchema("source").dataType.asInstanceOf[MapType]
    val sourceList = MyUtil.ExpandMap (explodedDF, "deviceType", bar)

    val dcCol = explodedDF.col("dc_id").as("dcId")
    val colList = dcCol :: sourceList
    val flatDf = explodedDF.select(colList:_*)
    //val flatDf = explodedDF.select( $"dc_id" as "dcId", sourceList(0), sourceList(1), sourceList(2), sourceList(3))
                    //$"key" as "deviceType",
                    //$"value".getItem("ip") as "ip",
                    //$"value".getItem("id") as "deviceId",
                    //$"value".getItem("temp") as "temp")
                    //'value.getItem("ip") as 'ip,
                    //'value.getItem("id") as 'deviceId,
                    //'value.getItem("temp") as 'temp)
 
    // Write the data to the console
    val consoleOutputJson = MyUtil.writeConsole ("append", geoDf)
  }

  def process(): Unit = {

    // Initialise Spark
    val spark: SparkSession = SparkSession.builder
      .appName("Kafka Integration")
      .getOrCreate()
    import spark.implicits._

    // ***** TODO - Can the checkpoints directory for two different streaming queries be
    // the same??
    // ***** TODO - Make sure we get output for both the Avro and Json Streaming Queries

    //doAvro(spark)
    //doJson(spark)
    doNestedJson(spark)

    spark.streams.awaitAnyTermination(30000)

    println(s"========== DONE ==========" )
  }
}

      //val dfout = dfcount.select(
      //  concat($"FULL_NAME", lit("-KD")).as("key"),
      //  dfcount.col("count").cast(DataTypes.StringType).as("value")
