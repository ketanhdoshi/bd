from functools import partial
import datetime
import sys

from pyspark.sql import SparkSession
from pyspark.sql.types import *
from pyspark.sql.functions import *
from pyspark.sql.window import Window

import util

#-------------------------------------------
#-------------------------------------------
def readFileStream(spark, dataDir):
  adSchema = StructType() \
          .add("ad_id", IntegerType()) \
          .add("channel_id", IntegerType()) \
          .add("start_ts", TimestampType()) \
          .add("end_ts", TimestampType())

  dataDir = sys.argv[1]
  inputPath = dataDir + "/ad*.json"
  adDf = (spark
      .readStream                 
      .schema(adSchema)
      .json(inputPath)
  )

  # Output stream to console
  adDf.printSchema()
  adStream = (adDf
    .writeStream
    .outputMode("append")
    .option("forceDeleteTempCheckpointLocation", "true")
    .format("console")
    .start()
  )

  # +-----+----------+-------------------+-------------------+
  # |ad_id|channel_id|           start_ts|             end_ts|
  # +-----+----------+-------------------+-------------------+
  # |   17|        57|2021-02-01 07:16:24|2021-02-01 07:43:51|
  # |   13|        58|2021-02-01 08:19:26|2021-02-01 08:43:18|
  # |   14|        58|2021-02-01 09:07:03|2021-02-01 09:24:41|
  # |   17|        57|2021-02-02 07:22:27|2021-02-02 07:24:38|
  # |   18|        57|2021-02-02 08:47:06|2021-02-02 08:55:36|
  # +-----+----------+-------------------+-------------------+

  return adDf

#-------------------------------------------
# Read from Kafka Ad JSON topic
#-------------------------------------------
def readKafkaStream(spark, brokers, topic, offset):
  # 
  schema = StructType() \
          .add("ad_id", IntegerType()) \
          .add("channel_id", IntegerType()) \
          .add("start_ts", TimestampType()) \
          .add("duration_secs", IntegerType())

  adDf = util.readKafkaJson(spark, brokers, topic, schema, offset=offset)
  # Get the 'end_ts' by adding the 'start_ts' and 'duration_secs'. Since 'start_ts' is a timestamp column
  # cast it to integer, add the duration, and then convert back to timestamp.
  adDf = adDf.withColumn("end_ts", (col("start_ts").cast("integer") + col("duration_secs")).cast("timestamp"))
  util.showStream(adDf)
  return adDf

#-------------------------------------------
# Get Ad data stream
#-------------------------------------------
def doAd(spark, dataDir, brokers, topic, offset, fromKafka):
  if (fromKafka):
    adDf = readKafkaStream(spark, brokers, topic, offset)
  else:
    adDf = readFileStream(spark, dataDir)
  return adDf


#-------------------------------------------
#-------------------------------------------
def getAdOverlap(adDf, sessionDf):
  adDf = adDf.withColumnRenamed("channel_id", "ad_channel_id") \
             .withColumnRenamed("start_ts", "ad_start_ts") \
             .withColumnRenamed("end_ts", "ad_end_ts")
  
  sessionDf = sessionDf.withColumnRenamed("channel_id", "session_channel_id") \
                       .withColumnRenamed("start_ts", "session_start_ts") \
                       .withColumnRenamed("end_ts", "session_end_ts")

  adWithWatermark = adDf.withWatermark("ad_start_ts", "1 minutes")
  sessionWithWatermark = sessionDf.withWatermark("session_start_ts", "1 minutes")

  overlapDf = adWithWatermark.join(sessionWithWatermark,
    (sessionWithWatermark.session_channel_id == adWithWatermark.ad_channel_id) &
    (sessionWithWatermark.session_start_ts <= adWithWatermark.ad_end_ts) &
    (sessionWithWatermark.session_end_ts >= adWithWatermark.ad_start_ts), "left_outer")

  overlapDf = overlapDf.withColumn("over_start_ts", greatest(overlapDf.session_start_ts, overlapDf.ad_start_ts)) \
                       .withColumn("over_end_ts", least(overlapDf.session_end_ts, overlapDf.ad_end_ts))

  # NB: The Outer NULL results (for Ad ID 18) should be here but are missing!!
  # From the docs, my feeling is that they will be output after a delay, when the next batch of stream data come in.
  #
  # +-----+-------------+-------------------+-------------------+-------+---------+------------------+-------------------+-------------------+-------------------+-------------------+
  # |ad_id|ad_channel_id|        ad_start_ts|          ad_end_ts|user_id|device_id|session_channel_id|   session_start_ts|     session_end_ts|      over_start_ts|        over_end_ts|
  # +-----+-------------+-------------------+-------------------+-------+---------+------------------+-------------------+-------------------+-------------------+-------------------+
  # |   17|           57|2021-02-01 07:16:24|2021-02-01 07:43:51|     45|       14|                57|2021-02-01 07:12:35|2021-02-01 08:19:35|2021-02-01 07:16:24|2021-02-01 07:43:51|
  # |   17|           57|2021-02-02 07:22:27|2021-02-02 07:24:38|     45|       15|                57|2021-02-02 07:19:35|2021-02-02 07:57:35|2021-02-02 07:22:27|2021-02-02 07:24:38|
  # |   13|           58|2021-02-01 08:19:26|2021-02-01 08:43:18|     46|       17|                58|2021-02-01 08:32:51|2021-02-01 09:20:16|2021-02-01 08:32:51|2021-02-01 08:43:18|
  # |   14|           58|2021-02-01 09:07:03|2021-02-01 09:24:41|     46|       17|                58|2021-02-01 08:32:51|2021-02-01 09:20:16|2021-02-01 09:07:03|2021-02-01 09:20:16|
  # |   13|           58|2021-02-01 08:19:26|2021-02-01 08:43:18|     46|       16|                58|2021-02-01 07:00:35|2021-02-01 09:05:17|2021-02-01 08:19:26|2021-02-01 08:43:18|
  # +-----+-------------+-------------------+-------------------+-------+---------+------------------+-------------------+-------------------+-------------------+-------------------+

  overlapDf.printSchema()
  overlapStream = (overlapDf
    .writeStream
    .outputMode("append")
    .option("forceDeleteTempCheckpointLocation", "true")
    .format("console")
    .start()
  )

  # Use only the relevant columns and rename them as needed
  overlapDf = overlapDf.select(
                  "ad_id", col("ad_channel_id").alias("channel_id"), 
                  "user_id", "device_id",
                  col("over_start_ts").alias("start_ts"), col("over_end_ts").alias("end_ts"))
  return overlapDf

#-------------------------------------------
#-------------------------------------------
def getAdDemographic (adByUserDf, userDf):

  # Dfs on both sides of the join need an event timestamp field as a watermark, so that 
  # late-arriving data can be discarded, to prevent unbounded waiting.
  # The user dataframe has no time column, so we add current time as an artificial column
  adByUserDf = adByUserDf.withWatermark("start_ts", "1 minutes")
  userDf = userDf \
              .withColumn("current_timestamp", current_timestamp()) \
              .withWatermark("current_timestamp", "1 minutes")

  adByUserDf = adByUserDf.withColumnRenamed("user_id", "ad_user_id")
  joinDf = adByUserDf.join(userDf, (adByUserDf.ad_user_id == userDf.user_id), "inner")

  # +-----+----------+-------+---------+-------------------+-------------------+-------+------+---+------+--------------------+
  # |ad_id|channel_id|user_id|device_id|           start_ts|             end_ts|user_id|  name|age|gender|   current_timestamp|
  # +-----+----------+-------+---------+-------------------+-------------------+-------+------+---+------+--------------------+
  # |   17|        57|     45|       14|2021-02-01 07:16:24|2021-02-01 07:43:51|     45| ketan| 55|     M|2021-02-25 06:07:...|
  # |   17|        57|     45|       15|2021-02-02 07:22:27|2021-02-02 07:24:38|     45| ketan| 55|     M|2021-02-25 06:07:...|
  # |   13|        58|     46|       17|2021-02-01 08:32:51|2021-02-01 08:43:18|     46|vihaan| 18|     M|2021-02-25 06:07:...|
  # |   14|        58|     46|       17|2021-02-01 09:07:03|2021-02-01 09:20:16|     46|vihaan| 18|     M|2021-02-25 06:07:...|
  # |   13|        58|     46|       16|2021-02-01 08:19:26|2021-02-01 08:43:18|     46|vihaan| 18|     M|2021-02-25 06:07:...|
  # +-----+----------+-------+---------+-------------------+-------------------+-------+------+---+------+--------------------+

  joinDf.printSchema()
  joinStream = (joinDf
    .writeStream
    .outputMode("append")
    .option("forceDeleteTempCheckpointLocation", "true")
    .format("console")
    .start()
  )

  # Use only the relevant fields
  joinDf = joinDf.select ("ad_id", "channel_id", "user_id", "device_id", "name", "age", "gender", "start_ts", "end_ts")
  return joinDf

#-------------------------------------------
# Get Ad Sessions with Demographic and Device Locations
#-------------------------------------------
def doAdDemographicLoc(adDf, sessionDf, userDf, deviceLocDf):
  # Show by User Session
  adByUserDf = getAdOverlap(adDf, sessionDf)

  # Show by User Session, enriched with Demographic info
  adDemographicDf = getAdDemographic (adByUserDf, userDf)

  # Ad by User Session, enriched with Demographic and Device Location info
  #adDemographicLocDf = getAdDemographicLocation(adDemographicDf, deviceLocDf)
  adDemographicLocDf = adDemographicDf

  return adDemographicLocDf
