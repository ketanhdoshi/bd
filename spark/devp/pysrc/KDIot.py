from pyspark.sql import SparkSession
from pyspark.sql.types import *
from pyspark.sql.functions import *
import datetime;
import sys

spark = SparkSession.builder.appName("Kafka Iot").getOrCreate()

# Oddly, owing to the way Spark invokes Python programs via a Python Runner,
# the first argument argv[0] is the program name. So the real arguments start
# from argv[1]
brokers = sys.argv[1] # "kafka:29092"
topic = sys.argv[2] # "mqtt.temperature"
outTopic = sys.argv[3] # "kdcount"
dataDir = sys.argv[4] + "/" # /tmp/data

print("args are ", len(sys.argv), sys.argv[0], brokers, topic, outTopic, dataDir)

# Read from the beginning (ie. 'earliest' offset) or from a specific number offset
offset=42
startingOffsets = "earliest" if (offset == 0) else f"""{{ "{topic}": {{ "0": {offset} }} }}"""

# Subscribe to 1 topic
# read data from the start of the stream
df = spark \
  .readStream \
  .format("kafka") \
  .option("kafka.bootstrap.servers", brokers) \
  .option("subscribe", topic) \
  .option("startingOffsets", startingOffsets) \
  .option("kafka.sasl.mechanism", "PLAIN") \
  .option("kafka.security.protocol", "SASL_PLAINTEXT") \
  .option("kafka.sasl.jaas.config", """org.apache.kafka.common.security.plain.PlainLoginModule required username="test" password="test123";""") \
  .load()

# The Key and Value are binary data, so deserialise them into strings
# The Value gets deserialised into a string which we know is in JSON format
df1 = df.selectExpr("CAST(key AS STRING)", \
               "CAST(value AS STRING)", \
               "offset", \
               "CAST(timestamp AS TIMESTAMP)")

df1.printSchema()

# Define the structure of the data so we can deserialise that JSON into its fields
nested = StructType() \
  .add("dc_id", StringType()) \
  .add("source", MapType(StringType(), StructType() \
     .add("description", StringType()) \
     .add("ip", StringType()) \
     .add("id", LongType()) \
     .add("temp", LongType()) \
     .add("c02_level", LongType()) \
     .add("geo", StructType() \
        .add("lat", DoubleType()) \
        .add("long", DoubleType()))))

# The dataframe will now contain columns one level down, nested 
# under a "cust" structure
df2 = df1.select(from_json("value", nested).alias("iot"), \
                col("offset").alias("msgoffset"), \
                col("timestamp").alias("msgtime"))
df2.printSchema()

#Flatten it out so that those columns now appear at the top-level
df3 = df2.selectExpr("iot.dc_id", "msgoffset", "msgtime")
df3.printSchema()

# A dataframe must have string or binary columns named 'key' and 'value'
# so that it can be written to a Kafka topic. 
# The 'value' is set to a JSON string serialised from all fields in the dataframe 
dfout = df3.selectExpr( \
        "CAST(dc_id AS STRING) AS key", \
        "to_json(struct(*)) AS value")

#Write the dataframe to a Kafka topic. A checkpoint location must be specified
kafkaOutput = dfout.writeStream \
        .format("kafka") \
        .option("kafka.bootstrap.servers", brokers) \
        .option("topic", outTopic) \
        .option("kafka.sasl.mechanism", "PLAIN") \
        .option("kafka.security.protocol", "SASL_PLAINTEXT") \
        .option("kafka.sasl.jaas.config", """org.apache.kafka.common.security.plain.PlainLoginModule required username="test" password="test123";""") \
        .option("checkpointLocation", dataDir + "checkpoints_iot") \
        .outputMode("append") \
        .start()

# We cannot call .show() on a streaming dataframe. Instead we
# write a streaming query that outputs the content of the Dataframe to the console
consoleOutput = df1.writeStream \
        .outputMode("append") \
        .format("console") \
        .start()

spark.streams.awaitAnyTermination(30000)

print("========== DONE ==========" )
