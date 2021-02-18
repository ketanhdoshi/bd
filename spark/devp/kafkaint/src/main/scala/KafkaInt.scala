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
object MyUtil {
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
    //val brokers = "kafka:29092"
    //val schemaRegistryUrl = "http://schema-registry:8090"

    val brokers = args(0)
    val schemaRegistryUrl = args(1)
    val eventsTopic = args(2) // "demo-events"
    val dataDir = args(3) + "/"

    // Initialise Spark
    val spark: SparkSession = SparkSession.builder
      .appName("Kafka Integration")
      .getOrCreate()

    new StreamsApp(spark, brokers, schemaRegistryUrl, eventsTopic, dataDir).process()
  }
}

// Stateful Processing - input event record
case class UserEvent(user_id: Int, event_ts:java.sql.Timestamp, event_type:String)
// Stateful Processing - state data record
case class UserSession(user_id: Int, var type_ints: Array[Int], var start:java.sql.Timestamp, var end:java.sql.Timestamp)

object StatefulFunc {
  //-------------------------------------------
  // Stateful Processing - Update Function for mapGroups..
  // It takes the grouping key (ie. user_id), the input records (ie. user events) for the group within
  // this micro-batch and the previous state (ie. user session) and takes two actions:
  //    1. Updates the state and saves it for the next micro-batch
  //    2. Returns a single output record for the whole group
  //
  // NB: Right now the output record type is the same as the state record type, so we're updating
  // state and returning the output using the same data variable
  //-------------------------------------------
  def mapSession(
    user_id: Int,
    inputs: Iterator[UserEvent],
    oldState: GroupState[UserSession]): UserSession = {

      // oldState is a handle (ie. a GroupState object) to the previous state. Use it to get the data 
      // for the previous state if it exists, else create a new data object for the state, initialised with
      // empty data
      var state:UserSession = if (oldState.exists) oldState.get 
                              else UserSession (
                                  user_id,
                                  Array.empty[Int],
                                  new java.sql.Timestamp(0L),
                                  new java.sql.Timestamp(0L))
      
      // Timeout for this user, due to no activity (so no input event records)
      if (oldState.hasTimedOut) {
        // Set some flag value in the output record
        state.type_ints :+= 500

        // Since we're closing the user's session, remove the state for this user
        oldState.remove()
      } else {
          // No timeout, so do normal processing of input events

          // Go through each input record
          for (event <- inputs) {      
            // Ignore events without a timestamp
            if (!Option(event.event_ts).isEmpty) {
              // event_type is a string in the format "type_<number>"
              // Parse and extract the event type number
              val event_type = event.event_type
              val idx = event_type.indexOf("type_") + 5
              val len = event_type.length()
              val type_str = event_type.substring(idx, len)
              val type_int = type_str.toInt;

              // Keep a list of all event types received so far
              state.type_ints :+= type_int

              // Update the user session start and end times
              if ((type_int < 4) && (event.event_ts.after(state.start))) {
                // We track the start time if we receive event types < 4
                // If we receive multiple such events, we take the latest one
                state.start = event.event_ts
              } else if ((type_int >= 7) && (event.event_ts.after(state.end))) {
                // We track  the end time if we receive event types >= 7
                // If we receive multiple such events, we take the latest one
                state.end = event.event_ts
              }
            }
            
            // Re-compute a new timeout value for this user
            oldState.setTimeoutDuration("30 seconds")

            // Save the updated state
            oldState.update(state)
          }
      }
    
    // Return the output record
    return state
  }

  //-------------------------------------------
  // Stateful Processing - Update Function for flatMapGroups..
  // It is similar to the update function for mapGroups, except it returns multiple output records
  // 
  // It takes the grouping key (ie. user_id), the input records (ie. user events) for the group within
  // this micro-batch and the previous state (ie. user session) and takes two actions:
  //    1. Updates the state and saves it for the next micro-batch
  //    2. Returns an iterator of multiple output records for the whole group
  //
  // NB: Right now the output record type is the same as the state record type, so we're updating
  // state and returning the output using the same data variable
  //-------------------------------------------
  def flatmapSession(
    user_id: Int,
    inputs: Iterator[UserEvent],
    oldState: GroupState[UserSession]): Iterator[UserSession] = {

      // oldState is a handle (ie. a GroupState object) to the previous state. Use it to get the data 
      // for the previous state if it exists, else create a new data object for the state, initialised with
      // empty data
      var state:UserSession = if (oldState.exists) oldState.get 
                                else UserSession (
                                  user_id,
                                  Array.empty[Int],
                                  new java.sql.Timestamp(0L),
                                  new java.sql.Timestamp(0L))

      // NB: Right now, we are keeping the code around for this list, but not using it for anything
      import scala.collection.mutable.ListBuffer
      var myListBuf = new ListBuffer[UserSession]()

      // We will return a list of user sessions, with start and end times for each event type
      // Create an empty Map object to map event type -> user session
      var myMap = Map[Int, UserSession]()

      // Timeout for this user, due to no activity (so no input event records)
      if (oldState.hasTimedOut) {
        // Create a dummy session with some flag value which we will return within the iterator
        val dummySession = UserSession (user_id, Array(999), new java.sql.Timestamp(0L),
                                  new java.sql.Timestamp(0L))
        
        // Add the dummy session to our map
        myListBuf += dummySession
        myMap = myMap + (999 -> dummySession)

        // Since we're closing the user's session, remove the state for this user
        oldState.remove()
      } else {
          // No timeout, so do normal processing of input events

          // Go through each input record
          for (event <- inputs) {      
            // Ignore events without a timestamp
            if (!Option(event.event_ts).isEmpty) {

              // event_type is a string in the format "type_<number>"
              // Parse and extract the event type number
              val event_type = event.event_type
              val idx = event_type.indexOf("type_") + 5
              val len = event_type.length()
              val type_str = event_type.substring(idx, len)
              val type_int = type_str.toInt;

              // Lookup a session for this event type value in our map
              // If it doesn't exist, create a new session object for this event type and add it
              // to the map. We initialise the start time to a MaxValue and the end time to 0, so
              // that we can compare them with this event's timestamp
              var mySession = myMap.get(type_int) match {
                case Some(s) => s
                case None => {
                  val newSession = UserSession (user_id, Array(type_int), 
                                  new java.sql.Timestamp(Long.MaxValue),
                                  new java.sql.Timestamp(0L))
                  myMap = myMap + (type_int -> newSession)
                  newSession
                }
              }

              if (event.event_ts.before(mySession.start)) {
                // Update the session start time if this event's timestamp is earlier
                mySession.start = event.event_ts
              } else if (event.event_ts.after(mySession.end)) {
                // Update the session end time if this event's timestamp is later
                mySession.end = event.event_ts
              }
            }
            
            // Re-compute a new timeout value for this user
            oldState.setTimeoutDuration("30 seconds")

            // Save the updated state
            oldState.update(state)
          }
      }
    
    val myList = myListBuf.toList

    // Return an iterator for the output records
    return myMap.valuesIterator
  }
}

//-------------------------------------------
// Class for our Streaming Application
//-------------------------------------------
class StreamsApp(
  spark: SparkSession, 
  brokers: String, 
  schemaRegistryUrl: String, 
  eventsTopic: String,
  dataDir:String) {

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
    val agoUdf: UserDefinedFunction = udf(agoFunc)

    // Apply the UDF to the 'msgtime' column to create a new column 'ago'
    val df1 = df.withColumn("ago", agoUdf.apply(col("msgtime")))
    return df1
  }

  //-------------------------------------------
  // End-to-end example of reading and writing a Batch (non-streaming) dataframe
  //-------------------------------------------
  private def doBatch (): Unit = {
    import spark.implicits._

    // Create Sample Dataframe
    val empDF = spark.createDataFrame(Seq(
          (7369, "SMITH", "CLERK", 7902, "17-Dec-80", 800, 20, 10),
          (7499, "ALLEN", "SALESMAN", 7698, "20-Feb-81", 1600, 300, 30),
          (7521, "WARD", "SALESMAN", 7698, "22-Feb-81", 1250, 500, 30),
          (7566, "JONES", "MANAGER", 7839, "2-Apr-81", 2975, 0, 20),
          (7654, "MARTIN", "SALESMAN", 7698, "28-Sep-81", 1250, 1400, 30),
          (7698, "BLAKE", "MANAGER", 7839, "1-May-81", 2850, 0, 30),
          (7782, "CLARK", "MANAGER", 7839, "9-Jun-81", 2450, 0, 10),
          (7788, "SCOTT", "ANALYST", 7566, "19-Apr-87", 3000, 0, 20),
          (7839, "KING", "PRESIDENT", 0, "17-Nov-81", 5000, 0, 10),
          (7844, "TURNER", "SALESMAN", 7698, "8-Sep-81", 1500, 0, 30),
          (7876, "ADAMS", "CLERK", 7788, "23-May-87", 1100, 0, 20)
        )).toDF("empno", "ename", "job", "mgr", "hiredate", "sal", "comm", "deptno")

    val partitionWindow = Window.partitionBy($"deptno").orderBy($"sal".desc)
    val rankTest = rank().over(partitionWindow)
    val denseRankTest = dense_rank().over(partitionWindow)
    val rowNumberTest = row_number().over(partitionWindow)
    val sumTest = sum($"sal").over(partitionWindow)
    val leadTest = lead($"sal", 1, 0).over(partitionWindow)
    val lagTest = lag($"sal", 1, 0).over(partitionWindow)
    val firstValTest = first($"sal").over(partitionWindow)
    empDF.select($"*",
      sumTest as "running_total",
      leadTest as "next_val", 
      lagTest as "prev_val",
      firstValTest as "first_val",
      rankTest as "rank", 
      denseRankTest as "denseRank", 
      rowNumberTest as "row_number").show

    val partitionWindowWithUnboundedFollowing = Window.partitionBy($"deptno").orderBy($"sal".desc).rowsBetween(Window.currentRow, Window.unboundedFollowing)
    val lastValTest2 = last($"sal").over(partitionWindowWithUnboundedFollowing)
    empDF.select($"*", lastValTest2 as "last_val").show
  }

  //-------------------------------------------
  // Example of stateful processing where the computation for a row
  // depends on other rows
  //-------------------------------------------
  private def doStateful (dfEvents: DataFrame): DataFrame = {
    import spark.implicits._

    val dfState = dfEvents.select (
        $"customer_id".as("user_id"),
        $"event_ts", $"event_type").as[UserEvent]
      //.withWatermark("event_ts", "30 minutes")  // required for Event Time Timeouts
      .groupByKey(_.user_id) // group based on user
      //.mapGroupsWithState(GroupStateTimeout.ProcessingTimeTimeout)(StatefulFunc.mapSession)
      .flatMapGroupsWithState(OutputMode.Append,GroupStateTimeout.ProcessingTimeTimeout)(StatefulFunc.flatmapSession)
      .toDF // cast the Dataset to a Dataframe

    return dfState
  }

  //-------------------------------------------
  // Example of window aggregates
  //-------------------------------------------
  private def doAggregate (dfEvents: DataFrame): DataFrame = {
    import spark.implicits._
  
    // Group the data by time window and user and compute the count of each group
    val dfAgg = dfEvents.groupBy(
          window($"event_ts", "5 minutes", "2 minutes"),
          $"event_type"
    ).agg (avg("event_amount") as "avg", 
            count("event_amount") as "count", 
            sum("event_amount") as "sum")
    // .orderBy("window") ***** OrderBy increases processing time a lot, so skipping it for now
    
    return dfAgg
  }

  //-------------------------------------------
  // Example of stream-stream join
  //-------------------------------------------
  private def doJoin (dfEvents: DataFrame): DataFrame = {
    import spark.implicits._
  
    var dfCust = MyUtil.readKafkaAvro (spark, brokers, "asgard.demo.CUSTOMERS", schemaRegistryUrl)

    // Join events with customer data. Events can arrive max 30 seconds late
    val dfJoin = dfEvents.withWatermark("event_ts", "30 seconds ")
                        .join(dfCust, dfEvents("customer_id") === dfCust("id"), "inner")
    return dfJoin
 }

  //-------------------------------------------
  // Example of advanced structured streaming dataframe operations like 
  // window aggregates, joins and stateful operations
  //-------------------------------------------
  private def doStreaming (): Unit = {
    import spark.implicits._

    var dfEvents = MyUtil.readKafkaAvro (spark, brokers, eventsTopic, schemaRegistryUrl, offset=5)
    dfEvents = dfEvents.transform (MyUtil.LongToTimestamp ("event_time", "event_ts"))

    //val dfJoin = doJoin(dfEvents)
    //val dfAgg = doAggregate(dfEvents)
    val dfStateful = doStateful(dfEvents)
    val dfOut = dfStateful

    val userDf = dfEvents.select($"customer_id", $"event_type", $"event_ts").where("customer_id > 8 and customer_id < 10")
    val kafkaOutputJson = MyUtil.writeKafkaJson (brokers, "testDoStreamingOut", "append", 
                                      dataDir + "checkpoints_stream", dfStateful, "user_id")
    
    // Temporarily comment it out because writing a stream to the console causes exceptions for some strange reason
    //val consoleOutput = MyUtil.writeConsole ("append", dfOut)
    //val consoleOutputInp = MyUtil.writeConsole ("append", userDf)
  }

  //-------------------------------------------
  // End-to-end example of reading and writing a Kafka Avro topic
  //-------------------------------------------
  private def doAvro (): Unit = {
    import spark.implicits._

    var dfAvro = MyUtil.readKafkaAvro (spark, brokers, eventsTopic, schemaRegistryUrl, offset=5)
    dfAvro = dfAvro.transform (MyUtil.LongToTimestamp ("event_time", "event_ts"))

    val kafkaOutputAvro = MyUtil.writeKafkaAvro (brokers, "test5", 
                  schemaRegistryUrl, "append", dataDir + "checkpoints_avro", dfAvro)
    val consoleOutputAvro = MyUtil.writeConsole ("append", dfAvro)
  }

  //-------------------------------------------
  // End-to-end example of reading and writing a Kafka Json topic
  //-------------------------------------------
  private def doJson (): Unit = {
    import spark.implicits._

    // Define the structure of the data so we can deserialise that JSON into its fields
    val custSchema = new StructType()
      .add("FULL_NAME", DataTypes.StringType)
      .add("CLUB_STATUS", DataTypes.StringType)
      .add("EMAIL", DataTypes.StringType)
      .add("MESSAGE", DataTypes.StringType)

    val dfJson = MyUtil.readKafkaJson (spark, brokers, "testDoJsonInp", custSchema)
    val dfflat = calcAgo (dfJson)

    // Compute aggregates
    val dfcount = dfflat.groupBy ($"FULL_NAME").count()

    println(s"========== SHOW ==========" )
    val kafkaOutputJson = MyUtil.writeKafkaJson (brokers, "testDoJsonOut", "complete", 
                                      dataDir + "checkpoints_json", dfcount, "FULL_NAME")
    val consoleOutputJson = MyUtil.writeConsole ("append", dfflat)
  }

  //-------------------------------------------
  // End-to-end example of reading a complex nested Kafka Json topic,
  // processing all the datatypes and writing the results to the console
  //-------------------------------------------
  private def doNestedJson (): Unit = {
    import spark.implicits._

    // Define the structure of the data so we can deserialise that JSON into its fields
    val nestedSchema = new StructType()
       .add("event_time", LongType)
       .add("start_time_str", StringType)
       .add("readings", ArrayType(IntegerType))
       .add("readings_array", ArrayType(ArrayType(IntegerType)))
       .add("struct_array", ArrayType(
          new StructType()
            .add("s", IntegerType)
            .add("t", IntegerType)
       ))
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
               .add("temp", ArrayType(IntegerType))
          )
       )
       .add("dc_id", StringType)

    // Read the JSON data, passing in a starting offset to skip the first few messages
    val dfJson = MyUtil.readKafkaJson (spark, brokers, "test-file-string", nestedSchema, offset=4)

    // Process the time-based columns
    val fmt = "yyyy-MM-dd'T'HH:mm:ss.sss'Z'"
    var timeDf = dfJson
                    .transform (MyUtil.LongToTimestamp ("event_time", "evt_ts"))
                    .transform (MyUtil.StrToTimestamp ("start_time_str", "start_ts", fmt))
                    .transform (MyUtil.ExpandTimestamp ("evt_ts"))
    timeDf.printSchema

    val arrayDf = dfJson.select($"dc_id", 
                        $"readings",
                        $"readings_array")
    arrayDf.createOrReplaceTempView("array_table")
    val sqlArraySum = """select dc_id, readings, 
                          aggregate(readings, 0, (acc, value) -> acc + value) as sum,
                          transform(readings_array, z -> transform(z, value -> value + 10)) as new_readings_array
                          from array_table"""
    //val sqlArraySum = "SELECT dc_id, TRANSFORM(readings_array, z -> TRANSFORM(z, value -> value + 10)) AS new_readings FROM array_table"
    //val sqlArraySum = """select dc_id, transform(readings_array, z -> transform(z, value -> value + 50)) as new_readings from array_table"""
    //val sqlArraySum = """select dc_id, transform(readings_array, xyz -> transform(xyz, value -> value + 50)) as new_readings from array_table"""
    val arraySumDf = spark.sql(sqlArraySum)

    val structArrayDf = dfJson.select($"dc_id",
                              $"struct_array")
    structArrayDf.createOrReplaceTempView("struct_array_table")
    val sqlStructArray = """select *, 
                          transform (struct_array, z -> z.s * z.t) as mult_struct,
                          exists (struct_array, z -> z.s + z.t > 10) as exists_struct
                          from struct_array_table"""
    val structArrayCalcDf = spark.sql(sqlStructArray)

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
 
    flatDf.createOrReplaceTempView("flat_table")
    val sqlArrayFilter = """select *, 
                          filter (temp, t -> t > 5) as filt_temp
                          from flat_table"""
    val arrayFilterDf = spark.sql(sqlArrayFilter)


    // Write the data to the console
    val consoleOutputJson = MyUtil.writeConsole ("append", timeDf)
  }

  def process(): Unit = {

    import spark.implicits._

    // ***** TODO - Can the checkpoints directory for two different streaming queries be
    // the same??
    // ***** TODO - Make sure we get output for both the Avro and Json Streaming Queries

    //doAvro()
    // doJson()
    //doNestedJson()
    // doBatch()
    //doStreaming()

    Session.foo()
    val actionDf = Session.getData(spark, dataDir)
    val sessionsDf = Session.doStateful (spark, actionDf)

    /* For some reason neither ther output to memory nor the output to file
    produces any output. Both are empty. The output to Kafka works fine so we'll
    continue to use that

    val debugOutput = sessionsDf
            .writeStream
            .queryName("device_sessions")
            .format("memory")
            .outputMode("append")
            .start()

    // Since the stream write is asynchronous, we may have to wait a little
    // before data starts appearing in the in-memory table
    while (debugOutput.isActive) {
      Thread.sleep (20000)
      spark.sql("select * from device_sessions").show()
    }

    val outDir = dataDir + "/out_session"
    val sessionsFile = sessionsDf
          .writeStream
          .outputMode("append")
          .format("json")
          .trigger(Trigger.ProcessingTime("120 seconds"))
          .option("path", outDir)
          .option("checkpointLocation", outDir + "/checkpoint_session")
          .start() */

    val kafkaOutputJson = MyUtil.writeKafkaJson (brokers, "json_topic", "append", 
                                       dataDir + "checkpoints_json", sessionsDf, "user_id")

    // Read a Kafka Avro stream and write it out to a Kafka JSON stream so that it can
    // be ingested by ElasticSearch for a Kibana dashboard
    //var dfESAvro = MyUtil.readKafkaAvro (spark, brokers, "demo-events", schemaRegistryUrl)
    //val kafkaOutputESJson = MyUtil.writeKafkaJson (brokers, "demo-es", 
    //              "append", "/data/checkpoints", dfESAvro, "event_time")

    spark.streams.awaitAnyTermination(80000)

    println(s"========== DONE ==========" )
  }
}

      //val dfout = dfcount.select(
      //  concat($"FULL_NAME", lit("-KD")).as("key"),
      //  dfcount.col("count").cast(DataTypes.StringType).as("value")
