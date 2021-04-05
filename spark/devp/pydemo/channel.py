from functools import partial
import datetime
import sys

from pyspark.sql import SparkSession
from pyspark.sql.types import *
from pyspark.sql.functions import *
from pyspark.sql.window import Window

import util
import session
import action
import ad
import user
import device_loc

#-------------------------------------------
#-------------------------------------------
def getData(spark):
  channelSchema = StructType() \
          .add("channel", StringType()) \
          .add("channel_id", IntegerType()) \
          .add("schedule", \
            MapType( \
              StringType(), \
              ArrayType( \
                StructType() \
                    .add("program", StringType()) \
                    .add("program_id", IntegerType()) \
                    .add("start", StringType()) \
              ) \
            ) \
          )

  channelJson = """[
    {
      "channel": "BBC",
	    "channel_id": 57,
	    "schedule": {
		    "01-02-2021": [
          {
            "program": "Animal Planet",
            "program_id": 12,
            "start": "07:00:00"
          },
          {
            "program": "Living Planet",
            "program_id": 13,
            "start": "07:45:00"
          }
        ],
		    "02-02-2021": [
          {
            "program": "Blue Kingdom",
            "program_id": 14,
            "start": "07:00:00"
          },
          {
            "program": "Wild China",
            "program_id": 15,
            "start": "08:30:00"
          }
        ]
      }
    },
    {
      "channel": "HBO",
	    "channel_id": 58,
	    "schedule": {
        "01-02-2021": [
          {
            "program": "Westworld",
            "program_id": 12,
            "start": "07:00:00"
          },
          {
            "program": "Wonder Woman",
            "program_id": 13,
            "start": "07:30:00"
          },
          {
            "program": "Dune",
            "program_id": 14,
            "start": "09:10:00"
          }
        ]
      }
    }
  ]"""

  channelDf = util.jsonToDataFrame(spark, channelJson, channelSchema)
  channelDf.printSchema()
  channelDf.show()

  # root
  # |-- channel: string (nullable = true)
  # |-- channel_id: integer (nullable = true)
  # |-- schedule: map (nullable = true)
  # |    |-- key: string
  # |    |-- value: array (valueContainsNull = true)
  # |    |    |-- element: struct (containsNull = true)
  # |    |    |    |-- program: string (nullable = true)
  # |    |    |    |-- program_id: integer (nullable = true)
  # |    |    |    |-- start: string (nullable = true)

  # +-------+----------+--------------------+
  # |channel|channel_id|            schedule|
  # +-------+----------+--------------------+
  # |    BBC|        57|[01-02-2021 -> [[...|
  # |    HBO|        58|[01-02-2021 -> [[...|
  # +-------+----------+--------------------+

  return channelDf

#-------------------------------------------
#-------------------------------------------
def getParquet(jsonDf, dataDir):
  # DataFrames can be saved as Parquet files, maintaining the schema information.
  parquetFile = dataDir + "channel.parquet"
  jsonDf.write.parquet(parquetFile, mode="overwrite")

  # Read in the Parquet file created above.
  # Parquet files are self-describing so the schema is preserved.
  # The result of loading a parquet file is also a DataFrame.
  parquetDf = spark.read.parquet(parquetFile)
  parquetDf.show()

  return parquetDf
  
#-------------------------------------------
#-------------------------------------------
def flattenShows(parquetDf):
  # Explode each day of the schedule as a separate row. It creates two columns 'key' and 'value'
  explodedDf = parquetDf.select("channel", "channel_id", explode("schedule"))
  # Explode each show in the day as a separate row
  explodedDf = explodedDf.select("channel", "channel_id", col("key").alias("day"), explode("value").alias("show"))
  explodedDf.printSchema()

  # Select all the fields from the show. 
  # For practice, add a 'day_date' date column by converting the 'day' string column.
  flatDf = explodedDf.select("channel", "channel_id", "day", "show.*")
  flatDf = flatDf.withColumn("day_date", to_date("day", "dd-MM-yyyy"))

  # 'day' and 'start' are strings. Concat them to get the 'start_time' of the show as a string.
  # Then apply the format to convert that to a 'start_ts' timestamp column
  fmt = "dd-MM-yyyy HH:mm:ss"
  flatDf = flatDf.withColumn("start_time", concat(col("day"), lit(" "), col("start")))
  flatDf = flatDf.transform (partial(util.StrToTimestamp, strColName="start_time", tsColName="start_ts", fmt=fmt))
  flatDf.printSchema()
  flatDf.show()

  # +-------+----------+----------+-------------+----------+--------+----------+-------------------+-------------------+
  # |channel|channel_id|       day|      program|program_id|   start|  day_date|         start_time|           start_ts|
  # +-------+----------+----------+-------------+----------+--------+----------+-------------------+-------------------+
  # |    BBC|        57|01-02-2021|Animal Planet|        12|07:00:00|2021-02-01|01-02-2021 07:00:00|2021-02-01 07:00:00|
  # |    BBC|        57|01-02-2021|Living Planet|        13|07:45:00|2021-02-01|01-02-2021 07:45:00|2021-02-01 07:45:00|
  # |    BBC|        57|02-02-2021| Blue Kingdom|        14|07:00:00|2021-02-02|02-02-2021 07:00:00|2021-02-02 07:00:00|
  # |    BBC|        57|02-02-2021|   Wild China|        15|08:30:00|2021-02-02|02-02-2021 08:30:00|2021-02-02 08:30:00|
  # |    HBO|        58|01-02-2021|    Westworld|        12|07:00:00|2021-02-01|01-02-2021 07:00:00|2021-02-01 07:00:00|
  # |    HBO|        58|01-02-2021| Wonder Woman|        13|07:30:00|2021-02-01|01-02-2021 07:30:00|2021-02-01 07:30:00|
  # |    HBO|        58|01-02-2021|         Dune|        14|09:10:00|2021-02-01|01-02-2021 09:10:00|2021-02-01 09:10:00|
  # +-------+----------+----------+-------------+----------+--------+----------+-------------------+-------------------+

  # Use just the relevant fields
  flatDf = flatDf.select ("program_id", "program", "channel_id", "channel", "day", "day_date", "start_ts")
  return flatDf

#-------------------------------------------
#-------------------------------------------
def getEndTime(df):
  # Create a Window Spec
  # # Define a Partition per Channel and Day, and order by Start time
  windowSpec = Window \
        .partitionBy(df['channel_id'], df['day']) \
        .orderBy(df['start_ts'].asc())

  # Define a ROW frame with the current row and the next row
  windowSpec.rowsBetween(Window.currentRow, 1)

  # The end time is the start time of the next row
  #flatDf = flatDf.withColumn("day_date", to_date("day", "dd-MM-yyyy"))
  #to_timestamp(date_add("day_date", 1))
  ndf = df.withColumn("end_tmp", lead("start_ts", 1, None).over(windowSpec))
  ndf = ndf.withColumn("end_day", to_timestamp(date_add("day_date", 1)))
  ndf = ndf.select("*", coalesce("end_tmp", "end_day").alias("end_ts"))
  ndf.show()

  # +----------+-------------+----------+-------+----------+----------+-------------------+-------------------+-------------------+-------------------+
  # |program_id|      program|channel_id|channel|       day|  day_date|           start_ts|            end_tmp|            end_day|             end_ts|
  # +----------+-------------+----------+-------+----------+----------+-------------------+-------------------+-------------------+-------------------+
  # |        12|Animal Planet|        57|    BBC|01-02-2021|2021-02-01|2021-02-01 07:00:00|2021-02-01 07:45:00|2021-02-02 00:00:00|2021-02-01 07:45:00|
  # |        13|Living Planet|        57|    BBC|01-02-2021|2021-02-01|2021-02-01 07:45:00|               null|2021-02-02 00:00:00|2021-02-02 00:00:00|
  # |        14| Blue Kingdom|        57|    BBC|02-02-2021|2021-02-02|2021-02-02 07:00:00|2021-02-02 08:30:00|2021-02-03 00:00:00|2021-02-02 08:30:00|
  # |        15|   Wild China|        57|    BBC|02-02-2021|2021-02-02|2021-02-02 08:30:00|               null|2021-02-03 00:00:00|2021-02-03 00:00:00|
  # |        12|    Westworld|        58|    HBO|01-02-2021|2021-02-01|2021-02-01 07:00:00|2021-02-01 07:30:00|2021-02-02 00:00:00|2021-02-01 07:30:00|
  # |        13| Wonder Woman|        58|    HBO|01-02-2021|2021-02-01|2021-02-01 07:30:00|2021-02-01 09:10:00|2021-02-02 00:00:00|2021-02-01 09:10:00|
  # |        14|         Dune|        58|    HBO|01-02-2021|2021-02-01|2021-02-01 09:10:00|               null|2021-02-02 00:00:00|2021-02-02 00:00:00|
  # +----------+-------------+----------+-------+----------+----------+-------------------+-------------------+-------------------+-------------------+

  # Use just the relevant fields
  ndf = ndf.select ("program_id", "program", "channel_id", "channel", "day_date", "start_ts", "end_ts")
  return ndf

#-------------------------------------------
#-------------------------------------------
def getShowOverlap(showDf, sessionDf):
  # Alternate syntax for Joins
  #
  # overlapDf = sessionDf.join(df1, 
  #     expr(""" 
  #         show_channel_id = channel_id AND
  #         start_ts <= show_end_ts AND
  #         end_ts >= show_start_ts
  #       """
  #     ))

  showDf = showDf.withColumnRenamed("channel_id", "show_channel_id") \
             .withColumnRenamed("start_ts", "show_start_ts") \
             .withColumnRenamed("end_ts", "show_end_ts")
  
  sessionDf = sessionDf.withColumnRenamed("channel_id", "session_channel_id") \
                       .withColumnRenamed("start_ts", "session_start_ts") \
                       .withColumnRenamed("end_ts", "session_end_ts")

  overlapDf = sessionDf.join(showDf,
    (sessionDf.session_channel_id == showDf.show_channel_id) &
    (sessionDf.session_start_ts <= showDf.show_end_ts) &
    (sessionDf.session_end_ts >= showDf.show_start_ts), "left_outer")

  overlapDf = overlapDf.withColumn("over_start_ts", greatest(overlapDf.session_start_ts, overlapDf.show_start_ts)) \
                       .withColumn("over_end_ts", least(overlapDf.session_end_ts, overlapDf.show_end_ts))

  # +-------+---------+------------------+-------------------+-------------------+----------+-------------+---------------+-------+----------+-------------------+-------------------+-------------------+-------------------+
  # |user_id|device_id|session_channel_id|   session_start_ts|     session_end_ts|program_id|      program|show_channel_id|channel|  day_date|      show_start_ts|        show_end_ts|      over_start_ts|        over_end_ts|
  # +-------+---------+------------------+-------------------+-------------------+----------+-------------+---------------+-------+----------+-------------------+-------------------+-------------------+-------------------+
  # |     46|       17|                57|2021-02-01 09:20:16|2021-02-01 09:36:56|        13|Living Planet|             57|    BBC|2021-02-01|2021-02-01 07:45:00|2021-02-02 00:00:00|2021-02-01 09:20:16|2021-02-01 09:36:56|
  # |     46|       17|                58|2021-02-01 08:32:51|2021-02-01 09:20:16|        14|         Dune|             58|    HBO|2021-02-01|2021-02-01 09:10:00|2021-02-02 00:00:00|2021-02-01 09:10:00|2021-02-01 09:20:16|
  # |     46|       17|                58|2021-02-01 08:32:51|2021-02-01 09:20:16|        13| Wonder Woman|             58|    HBO|2021-02-01|2021-02-01 07:30:00|2021-02-01 09:10:00|2021-02-01 08:32:51|2021-02-01 09:10:00|
  # |     46|       16|                58|2021-02-01 07:00:35|2021-02-01 09:05:17|        13| Wonder Woman|             58|    HBO|2021-02-01|2021-02-01 07:30:00|2021-02-01 09:10:00|2021-02-01 07:30:00|2021-02-01 09:05:17|
  # |     46|       16|                58|2021-02-01 07:00:35|2021-02-01 09:05:17|        12|    Westworld|             58|    HBO|2021-02-01|2021-02-01 07:00:00|2021-02-01 07:30:00|2021-02-01 07:00:35|2021-02-01 07:30:00|
  # |     45|       14|                57|2021-02-01 07:12:35|2021-02-01 08:19:35|        13|Living Planet|             57|    BBC|2021-02-01|2021-02-01 07:45:00|2021-02-02 00:00:00|2021-02-01 07:45:00|2021-02-01 08:19:35|
  # |     45|       14|                57|2021-02-01 07:12:35|2021-02-01 08:19:35|        12|Animal Planet|             57|    BBC|2021-02-01|2021-02-01 07:00:00|2021-02-01 07:45:00|2021-02-01 07:12:35|2021-02-01 07:45:00|
  # |     45|       15|                57|2021-02-02 07:19:35|2021-02-02 07:57:35|        14| Blue Kingdom|             57|    BBC|2021-02-02|2021-02-02 07:00:00|2021-02-02 08:30:00|2021-02-02 07:19:35|2021-02-02 07:57:35|
  # +-------+---------+------------------+-------------------+-------------------+----------+-------------+---------------+-------+----------+-------------------+-------------------+-------------------+-------------------+

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
                "program_id", "program", col("show_channel_id").alias("channel_id"), "channel",
                "user_id", "device_id",
                col("over_start_ts").alias("start_ts"), col("over_end_ts").alias("end_ts"))
  return overlapDf

#-------------------------------------------
#-------------------------------------------
def getShowDemographic (showByUserDf, userDf):

  # Dfs on both sides of the join need an event timestamp field as a watermark, so that 
  # late-arriving data can be discarded, to prevent unbounded waiting.
  # The user dataframe has no time column, so we add current time as an artificial column
  showByUserDf = showByUserDf.withWatermark("start_ts", "1 minutes")
  userDf = userDf \
              .withColumn("current_timestamp", current_timestamp()) \
              .withWatermark("current_timestamp", "1 minutes")

  showByUserDf = showByUserDf.withColumnRenamed("user_id", "show_user_id")
  joinDf = showByUserDf.join(userDf, (showByUserDf.show_user_id == userDf.user_id), "inner")
  joinDf = joinDf.select ("program_id", "channel_id", "program", "channel", "user_id", "device_id", "name", "age", "gender", "start_ts", "end_ts")

  # +----------+----------+-------------+-------+-------+---------+------+---+------+-------------------+-------------------+
  # |program_id|channel_id|      program|channel|user_id|device_id|  name|age|gender|           start_ts|             end_ts|
  # +----------+----------+-------------+-------+-------+---------+------+---+------+-------------------+-------------------+
  # |        13|        57|Living Planet|    BBC|     45|       14| ketan| 55|     M|2021-02-01 07:45:00|2021-02-01 08:19:35|
  # |        12|        57|Animal Planet|    BBC|     45|       14| ketan| 55|     M|2021-02-01 07:12:35|2021-02-01 07:45:00|
  # |        14|        57| Blue Kingdom|    BBC|     45|       15| ketan| 55|     M|2021-02-02 07:19:35|2021-02-02 07:57:35|
  # |        13|        57|Living Planet|    BBC|     46|       17|vihaan| 18|     M|2021-02-01 09:20:16|2021-02-01 09:36:56|
  # |        14|        58|         Dune|    HBO|     46|       17|vihaan| 18|     M|2021-02-01 09:10:00|2021-02-01 09:20:16|
  # |        13|        58| Wonder Woman|    HBO|     46|       17|vihaan| 18|     M|2021-02-01 08:32:51|2021-02-01 09:10:00|
  # |        13|        58| Wonder Woman|    HBO|     46|       16|vihaan| 18|     M|2021-02-01 07:30:00|2021-02-01 09:05:17|
  # |        12|        58|    Westworld|    HBO|     46|       16|vihaan| 18|     M|2021-02-01 07:00:35|2021-02-01 07:30:00|
  # +----------+----------+-------------+-------+-------+---------+------+---+------+-------------------+-------------------+

  joinDf.printSchema()
  joinStream = (joinDf
    .writeStream
    .outputMode("append")
    .option("forceDeleteTempCheckpointLocation", "true")
    .format("console")
    .start()
  )

  return joinDf

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
# This is a temporary convenience function only

# Save the showDemographic dataframe as an intermediate result to a Kafka topic
# as a way to save time, so we can simply read those results for subsequent processing
# instead of running all the processing from scratch each time
#-------------------------------------------
def saveShowDemographicKafka (showDemographicDf, dataDir):
  brokers = "kafka.kd-confluent.svc.cluster.local:9071"
  topic = "testShowOut1"
  util.writeKafkaJson(brokers, topic, showDemographicDf, None, dataDir + "/checkpoint_show")

#-------------------------------------------
# Save our output Ad Demographic data to Kafka
#-------------------------------------------
def saveAdDemographicKafka (adDemographicDf, dataDir):
  brokers = "kafka.kd-confluent.svc.cluster.local:9071"
  topic = "testAdOut1"
  util.writeKafkaJson(brokers, topic, adDemographicDf, None, dataDir + "/checkpoint_ad")

#-------------------------------------------
# This is a temporary convenience function only
# Load the previously saved intermediate data
#-------------------------------------------
def loadShowDemographicKafka ():
  # {"program_id":13,"channel_id":57,"program":"Living Planet","channel":"BBC","user_id":46,"device_id":17,"name":"vihaan","age":18,"gender":"M","start_ts":"2021-02-01T09:20:16.000Z","end_ts":"2021-02-01T09:36:56.000Z"}    
  schema = StructType() \
          .add("program_id", IntegerType()) \
          .add("channel_id", IntegerType()) \
          .add("program", StringType()) \
          .add("channel", StringType()) \
          .add("user_id", IntegerType()) \
          .add("device_id", IntegerType()) \
          .add("name", StringType()) \
          .add("age", IntegerType()) \
          .add("gender", StringType()) \
          .add("start_ts", StringType()) \
          .add("end_ts", StringType())

  brokers = "kafka.kd-confluent.svc.cluster.local:9071"
  topic = "testShowOut1"
  showDemographicDf = util.readKafkaJson(spark, brokers, topic, schema, offset=36)
  return showDemographicDf

#-------------------------------------------
#-------------------------------------------
def getShowDemographicLocation(showDemographicDf, deviceLocDf):
  # Comment it out for now because start_ts is a string in the Kafka intermediate.
  # But in reality it will be a timestamp, so uncomment it when we are not loading
  # the intermediate from Kafka.
  #showDemographicDf = showDemographicDf.withWatermark("start_ts", "10 seconds")
  showDemographicDf = showDemographicDf \
                      .withColumn("current_timestamp", current_timestamp()) \
                      .withWatermark("current_timestamp", "10 seconds")
  deviceLocDf = deviceLocDf \
              .withColumn("current_timestamp", current_timestamp()) \
              .withWatermark("current_timestamp", "3 minutes")

  showDemographicDf = showDemographicDf.withColumnRenamed("device_id", "show_device_id")
  joinDf = showDemographicDf.join(deviceLocDf, (showDemographicDf.show_device_id == deviceLocDf.device_id), "inner")
  joinDf = joinDf.select ("program_id", "channel_id", "program", "channel", 
                          "user_id", "name", "age", "gender", 
                          "device_id", "lon", "lat", "visibility",
                          "start_ts", "end_ts")
  return joinDf

#-------------------------------------------
# Read and Write to a Kafka JSON topic for testing Kafka Integration
#-------------------------------------------
def doKafkaTest(spark, dataDir):
  # {"id":1,"firstname":"James ","middlename":"","lastname":"Smith","dob_year":2018,"dob_month":1,"gender":"M","salary":3000}
  schema = StructType() \
          .add("id", IntegerType()) \
          .add("firstname", StringType()) \
          .add("middlename", StringType()) \
          .add("lastname", StringType()) \
          .add("dob_year", IntegerType()) \
          .add("dob_month", IntegerType()) \
          .add("gender", StringType()) \
          .add("salary", IntegerType())

  brokers = "kafka.kd-confluent.svc.cluster.local:9071"
  topic = "json_spark"

  # +---+---------+----------+--------+--------+---------+------+------+---------+--------------------+
  # | id|firstname|middlename|lastname|dob_year|dob_month|gender|salary|msgoffset|             msgtime|
  # +---+---------+----------+--------+--------+---------+------+------+---------+--------------------+
  # |  1|   James |          |   Smith|    2018|        1|     M|  3000|        0|2021-01-22 05:53:...|
  # |  2| Michael |      Rose|        |    2010|        3|     M|  4000|        1|2021-01-22 05:53:...|
  # |  3|  Robert |          |Williams|    2010|        3|     M|  4000|        2|2021-01-22 07:32:...|
  # +---+---------+----------+--------+--------+---------+------+------+---------+--------------------+

  kdf = util.readKafkaJson(spark, brokers, topic, schema, offset=0)
  util.showStream(kdf)
  util.writeKafkaJson(brokers, topic, kdf, "id", dataDir + "/checkpoint_kafka")

#-------------------------------------------
# Read from Kafka Customer JSON topic
#-------------------------------------------
def readKafkaCustomer(spark, offset):
  # 
  schema = StructType() \
          .add("ID", IntegerType()) \
          .add("FIRST_NAME", StringType()) \
          .add("LAST_NAME", StringType()) \
          .add("EMAIL", StringType()) \
          .add("CLUB_STATUS", StringType())

  brokers = "kafka.kd-confluent.svc.cluster.local:9071"
  topic = "kdserver1.kddb.customer"

  kdf = util.readKafkaJson(spark, brokers, topic, schema, offset=offset)
  util.showStream(kdf)
  return kdf

#-------------------------------------------
# Read from Kafka Ad JSON topic
#-------------------------------------------
def readKafkaAd(spark, offset):
  # 
  schema = StructType() \
          .add("ad_id", IntegerType()) \
          .add("channel_id", IntegerType()) \
          .add("start_ts", TimestampType()) \
          .add("duration_secs", IntegerType())

  brokers = "kafka.kd-confluent.svc.cluster.local:9071"
  topic = "testad1"

  kdf = util.readKafkaJson(spark, brokers, topic, schema, offset=offset)
  # Get the 'end_ts' by adding the 'start_ts' and 'duration_secs'. Since 'start_ts' is a timestamp column
  # cast it to integer, add the duration, and then convert back to timestamp.
  kdf = kdf.withColumn("end_ts", (col("start_ts").cast("integer") + col("duration_secs")).cast("timestamp"))
  util.showStream(kdf)
  return kdf

#-------------------------------------------
# Read from Kafka Dev Location JSON topic
#-------------------------------------------
def readKafkaLoc(spark, offset):
  # 
  schema = StructType() \
          .add("kdct", StringType()) \
          .add("name", StringType()) \
          .add("visibility", IntegerType()) \
          .add("lon", FloatType()) \
          .add("lat", FloatType())

  jsonKeySchema = StructType() \
          .add("id", IntegerType())

  brokers = "kafka.kd-confluent.svc.cluster.local:9071"
  topic = "testloc1"

  kdf = util.readKafkaJson(spark, brokers, topic, schema, offset=offset, jsonKeySchema=jsonKeySchema)
  fmt = "yyyy/MM/dd HH:mm:ss"
  kdf = kdf.transform (partial(util.StrToTimestamp, strColName="kdct", tsColName="kdts", fmt=fmt))

  util.showStream(kdf)
  return kdf

#-------------------------------------------
# Read from Kafka Action JSON topic
#-------------------------------------------
def readKafkaAction(spark, offset):
  # 
  schema = StructType() \
          .add("user", StringType()) \
          .add("user_id", IntegerType()) \
          .add("channel_id", IntegerType()) \
          .add("device_id", IntegerType()) \
          .add("action", IntegerType()) \
          .add("action_ts", TimestampType())

  brokers = "kafka.kd-confluent.svc.cluster.local:9071"
  topic = "testaction1"

  kdf = util.readKafkaJson(spark, brokers, topic, schema, offset=offset)
  util.showStream(kdf)
  return kdf

#-------------------------------------------
# Get Actions and Sessions data
#-------------------------------------------
def doActionSession():
  # Process actions - Commented out because Actions will be processed from Scala
  # actionDf = action.getData(spark)

  # Process sessions
  sessionDf = session.getData(spark)
  return sessionDf

#-------------------------------------------
# Get Channels and Show data
#-------------------------------------------
def doChannelShow():
  # Process channels
  jsonDf = getData(spark)
  dataDir = sys.argv[1] + "/"
  parquetDf = getParquet(jsonDf, dataDir)

  # Process shows (from channels)
  flatDf = flattenShows(parquetDf)
  showDf = getEndTime(flatDf)
  return showDf

#-------------------------------------------
# Get Show with Demographic
#-------------------------------------------
def doShowDemographic(showDf, sessionDf):
  # Process users
  userDf = user.getData(spark, dataDir)

  # Sessions with shows
  showByUserDf = getShowOverlap(showDf, sessionDf)

  # Shows by User, enriched with Demographic info
  showDemographicDf = getShowDemographic (showByUserDf, userDf)

  return showDemographicDf

#-------------------------------------------
# Get Ad with Demographic
#-------------------------------------------
def doAdDemographic(sessionDf):
  # Process users
  userDf = user.getData(spark, dataDir)

  # Process ads
  adDf = ad.getData(spark)

  # Sessions with ads
  adByUserDf = getAdOverlap(adDf, sessionDf)

  # Ads by User, enriched with Demographic info
  adDemographicDf = getAdDemographic (adByUserDf, userDf)

  return adDemographicDf

#-------------------------------------------
# Create a Spark Session and process all the data sources
#-------------------------------------------
def main(spark, dataDir):

  sessionDf = doActionSession()
  showDf = doChannelShow()
  showDemographicDf = doShowDemographic(showDf, sessionDf)
  adDemographicDf = doAdDemographic(sessionDf)

  # Save intermediate state to Kafka as a shortcut for use by downstream processing
  saveShowDemographicKafka (showDemographicDf, dataDir)
  saveAdDemographicKafka (adDemographicDf, dataDir)
  #showDemographicDf = loadShowDemographicKafka()
  #util.showStream(showDemographicDf)

  # Process device locations
"""   deviceLocDf = device_loc.getData(spark, dataDir)
  showDemographicLocDf = getShowDemographicLocation(showDemographicDf, deviceLocDf)
  util.showStream(showDemographicLocDf) """

spark = SparkSession.builder.appName("KD Demo").getOrCreate()
# Turn off INFO and DEBUG logging
spark.sparkContext.setLogLevel("ERROR")
dataDir = sys.argv[1]

main(spark, dataDir)

# Check that we can read data generated by Kafka Connect Datasources
#doKafkaTest(spark, dataDir) # This is just a test that reading/writing some data to Kafka works
#readKafkaCustomer(spark, offset=6)
#readKafkaAd(spark, offset=3)
#readKafkaLoc(spark, offset=20)
#readKafkaAction(spark, offset=1)

spark.streams.awaitAnyTermination(900000)
print("========== DONE ==========" )
