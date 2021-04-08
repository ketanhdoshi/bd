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

  # +----------+----------+-------------+-------+-------+------+---+------+---------+------+------+----------+-------------------+-------------------+
  # |program_id|channel_id|      program|channel|user_id|  name|age|gender|device_id|   lon|   lat|visibility|           start_ts|             end_ts|
  # +----------+----------+-------------+-------+-------+------+---+------+---------+------+------+----------+-------------------+-------------------+
  # |        13|        58| Wonder Woman|    HBO|     46|vihaan| 18|     M|       16| 83.91|429.17|      1773|2021-02-01 07:30:00|2021-02-01 09:05:17|
  # |        12|        58|    Westworld|    HBO|     46|vihaan| 18|     M|       16| 83.91|429.17|      1773|2021-02-01 07:00:35|2021-02-01 07:30:00|
  # |        13|        58| Wonder Woman|    HBO|     46|vihaan| 18|     M|       16| 84.91|418.68|      1618|2021-02-01 07:30:00|2021-02-01 09:05:17|
  # |        12|        58|    Westworld|    HBO|     46|vihaan| 18|     M|       16| 84.91|418.68|      1618|2021-02-01 07:00:35|2021-02-01 07:30:00|
  # |        13|        58| Wonder Woman|    HBO|     46|vihaan| 18|     M|       16| 86.03|411.22|      1596|2021-02-01 07:30:00|2021-02-01 09:05:17|
  # |        12|        58|    Westworld|    HBO|     46|vihaan| 18|     M|       16| 86.03|411.22|      1596|2021-02-01 07:00:35|2021-02-01 07:30:00|
  # |        14|        57| Blue Kingdom|    BBC|     45| ketan| 55|     M|       15|453.68| 85.02|      1295|2021-02-02 07:19:35|2021-02-02 07:57:35|
  # |        13|        57|Living Planet|    BBC|     46|vihaan| 18|     M|       17| 92.02|  4.86|       392|2021-02-01 09:20:16|2021-02-01 09:36:56|
  # |        14|        58|         Dune|    HBO|     46|vihaan| 18|     M|       17| 92.02|  4.86|       392|2021-02-01 09:10:00|2021-02-01 09:20:16|
  # |        13|        58| Wonder Woman|    HBO|     46|vihaan| 18|     M|       17| 92.02|  4.86|       392|2021-02-01 08:32:51|2021-02-01 09:10:00|
  # |        13|        57|Living Planet|    BBC|     45| ketan| 55|     M|       14| 37.29| 21.33|      5534|2021-02-01 07:45:00|2021-02-01 08:19:35|
  # |        12|        57|Animal Planet|    BBC|     45| ketan| 55|     M|       14| 37.29| 21.33|      5534|2021-02-01 07:12:35|2021-02-01 07:45:00|
  # +----------+----------+-------------+-------+-------+------+---+------+---------+------+------+----------+-------------------+-------------------+

  return joinDf

#-------------------------------------------
# Get Show data from Channels
#-------------------------------------------
def doShow(channelDf):
  # Process shows (from channels)
  flatDf = flattenShows(channelDf)
  showDf = getEndTime(flatDf)
  return showDf

#-------------------------------------------
# Get Show Sessions with Demographic and Device Locations
#-------------------------------------------
def doShowDemographicLoc(showDf, sessionDf, userDf, deviceLocDf):
  # Show by User Session
  showByUserDf = getShowOverlap(showDf, sessionDf)

  # Show by User Session, enriched with Demographic info
  showDemographicDf = getShowDemographic (showByUserDf, userDf)

  # Show by User Session, enriched with Demographic and Device Location info
  showDemographicLocDf = getShowDemographicLocation(showDemographicDf, deviceLocDf)

  return showDemographicLocDf
