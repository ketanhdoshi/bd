package com.ketan

import java.nio.file.{Files, Paths}
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.types._
import org.apache.spark.sql.functions._
import org.apache.spark.sql.avro._

//-------------------------------------------
// Read and Write Avro data from Kafka
// 
// NOTE: This can only read/write data in Spark's Avro format not 
// Confluent's Avro format!!! Spark's Avro format is the "typical" Avro format
// where the entire schema definition is embedded in each Kafka message. On the
// other hand, Confluent's Avro format embeds only the schema ID in each Kafka
// message and the schema definition is looked up from the Schema Registry.
//
// The two formats are not compatible.
//
// Here, we use Spark's built-in spark-avro library and the from_avro() and to_avro()
// functions.
//-------------------------------------------
object KafkaAvro {
  def main(args: Array[String]): Unit = {
    //val brokers = "kafka:29092"
    val brokers = args(0)
    val dataDir = args(1) + "/"
    new StreamsProcessor(brokers, dataDir).process()
  }
}

//-------------------------------------------
// To run this, first create a topic in Kafka (called 'json_spark').
// Then submit "person" data using kafkacat to that topic. Feed one 
// line at a time by copying and pasting the data from the URL below:
// https://raw.githubusercontent.com/sparkbyexamples/spark-examples/master/spark-streaming/src/main/resources/person.json
// 
// That data will converted into Avro format and written back to another
// topic in Kafka (called 'avro_topic'), so monitor it using kafkacat
// Finally that topic will be read and output to the console
//-------------------------------------------
class StreamsProcessor(brokers: String, dataDir: String) {

def process(): Unit = {

    // Initialise Spark
    val spark: SparkSession = SparkSession.builder
      .appName("Kafka Avro")
      .getOrCreate()

    // Read JSON data from a stream from Kafka
    val df = spark
      .readStream
      .format("kafka")
      .option("kafka.bootstrap.servers", brokers)
      .option("subscribe", "json_spark")
      .option("startingOffsets", "earliest") // read data from the start of the stream
      .option("kafka.sasl.mechanism", "PLAIN")
      .option("kafka.security.protocol", "SASL_PLAINTEXT")
      .option("kafka.sasl.jaas.config", """org.apache.kafka.common.security.plain.PlainLoginModule required username="test" password="test123";""")
      .load()

    // That JSON data is structured per the 'Person' schema
    val schema = new StructType()
      .add("id",IntegerType)
      .add("firstname",StringType)
      .add("middlename",StringType)
      .add("lastname",StringType)
      .add("dob_year",IntegerType)
      .add("dob_month",IntegerType)
      .add("gender",StringType)
      .add("salary",IntegerType)

   // Convert the JSON data to a Dataframe
   val personDF = df.selectExpr("CAST(value AS STRING)")
      .select(from_json(col("value"), schema).as("data"))
    personDF.printSchema()    

    // And output it to the console
    personDF.select(to_json(struct("data.*")).as("value"))
      .writeStream
        .format("console")
        .outputMode("append")
        .start()

    // Also convert that data into Avro format and write it back to
    // a different topic in Kafka
    personDF.select(to_avro(struct("data.*")) as "value")
      .writeStream
      .format("kafka")
      .outputMode("append")
      .option("kafka.bootstrap.servers", brokers)
      .option("topic", "avro_topic")
      .option("kafka.sasl.mechanism", "PLAIN")
      .option("kafka.security.protocol", "SASL_PLAINTEXT")
      .option("kafka.sasl.jaas.config", """org.apache.kafka.common.security.plain.PlainLoginModule required username="test" password="test123";""")
      .option("checkpointLocation", dataDir + "checkpoints")
      .start()

    // Read the Avro data from Kafka that we just wrote
    val adf = spark
      .readStream
      .format("kafka")
      .option("kafka.bootstrap.servers", brokers)
      .option("subscribe", "avro_topic")
      .option("startingOffsets", "earliest") // read data from the start of the stream
      .option("kafka.sasl.mechanism", "PLAIN")
      .option("kafka.security.protocol", "SASL_PLAINTEXT")
      .option("kafka.sasl.jaas.config", """org.apache.kafka.common.security.plain.PlainLoginModule required username="test" password="test123";""")
      .load()
    adf.printSchema

    // Now read the same 'Person' schema from a file 
    val jsonFormatSchema = new String(
        Files.readAllBytes(Paths.get(dataDir + "person.avsc")))

    // Convert the Avro data to DataFrame
    val apersonDF = adf.select(from_avro(col("value"), jsonFormatSchema).as("person"))
        .select("person.*")
    apersonDF.printSchema

    // and write it to the console
    apersonDF.writeStream
        .format("console")
        .outputMode("append")
        .start()

    spark.streams.awaitAnyTermination(30000)
    println(s"========== DONE ==========" )
  }
}
