from pyspark.sql import SparkSession
from pyspark.sql.types import *
from pyspark.sql.functions import *
import datetime;

spark = SparkSession.builder.appName("Kafka Integration").getOrCreate()

brokers = "kafka:29092"
topic = "UNHAPPY_PLATINUM_CUSTOMERS"

# Subscribe to 1 topic
# read data from the start of the stream
df = spark \
  .readStream \
  .format("kafka") \
  .option("kafka.bootstrap.servers", brokers) \
  .option("subscribe", topic) \
  .option("startingOffsets", "earliest") \
  .load()

# The Key and Value are binary data, so deserialise them into strings
# The Value gets deserialised into a string which we know is in JSON format
df1 = df.selectExpr("CAST(key AS STRING)", \
               "CAST(value AS STRING)", \
               "offset", \
               "CAST(timestamp AS TIMESTAMP)")

df1.printSchema()

# Define the structure of the data so we can deserialise that JSON into its fields
struct = StructType() \
      .add("FULL_NAME", StringType()) \
      .add("CLUB_STATUS", StringType()) \
      .add("EMAIL", StringType()) \
      .add("MESSAGE", StringType())

# The dataframe will now contain columns one level down, nested 
# under a "cust" structure
df2 = df1.select(from_json("value", struct).alias("cust"), \
                col("offset").alias("msgoffset"), \
                col("timestamp").alias("msgtime"))

df2.printSchema()

#Flatten it out so that those columns now appear at the top-level
df3 = df2.selectExpr("cust.FULL_NAME", "cust.MESSAGE", "msgoffset", "msgtime")

df3.printSchema()

def agoFunc (msgtime):
   nowtime = datetime.datetime.now()
   ago = int((nowtime - msgtime).total_seconds())
   return ago
# spark.udf.register ("agoUdf", agoFunc, LongType())
agoUdf = udf(agoFunc, LongType())

# Apply the UDF to the 'msgtime' column to create a new column 'ago'
dfflat = df3.withColumn("ago", agoUdf("msgtime"))

dfflat.printSchema()

#Compute aggregates
dfcount = dfflat.groupBy ("FULL_NAME").count()

# A dataframe must have string or binary columns named 'key' and 'value'
# so that it can be written to a Kafka topic. 
# The 'value' is set to a JSON string serialised from all fields in the dataframe 
dfout = dfcount.selectExpr( \
        "CAST(FULL_NAME AS STRING) AS key", \
        "to_json(struct(*)) AS value")

#Write the dataframe to a Kafka topic. A checkpoint location must be specified
kafkaOutput = dfout.writeStream \
        .format("kafka") \
        .option("kafka.bootstrap.servers", brokers) \
        .option("topic", "kdcount") \
        .option("checkpointLocation", "/data/checkpoints") \
        .outputMode("complete") \
        .start()

# We cannot call .show() on a streaming dataframe. Instead we
# write a streaming query that outputs the content of the Dataframe to the console
consoleOutput = dfflat.writeStream \
        .outputMode("append") \
        .format("console") \
        .start()

spark.streams.awaitAnyTermination(30000)

print("========== DONE ==========" )
