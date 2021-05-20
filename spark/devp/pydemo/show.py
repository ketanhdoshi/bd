from functools import partial
import datetime
import sys

from pyspark.sql import SparkSession
from pyspark.sql.types import *
from pyspark.sql.functions import *
from pyspark.sql.window import Window

import util
  
#-------------------------------------------
# Convert the nested Channel data into a flat dataframe that has one row per Show
#-------------------------------------------
def flattenShows(parquetDf):
  # Explode each day of the schedule as a separate row. It creates two columns 'key' and 'value'
  explodedDf = parquetDf.select("channel", "channel_id", explode("schedule"))
  # Explode each show in the day as a separate row
  explodedDf = explodedDf.select("channel", "channel_id", col("key").alias("day"), explode("value").alias("show"))
  # explodedDf.printSchema()

  # Select all the fields from the show. 
  # For practice, add a 'day_date' date column by converting the 'day' string column.
  flatDf = explodedDf.select("channel", "channel_id", "day", "show.*")
  flatDf = flatDf.withColumn("day_date", to_date("day", "dd-MM-yyyy"))

  # 'day' and 'start' are strings. Concat them to get the 'start_time' of the show as a string.
  # Then apply the format to convert that to a 'start_ts' timestamp column
  fmt = "dd-MM-yyyy HH:mm:ss"
  flatDf = flatDf.withColumn("start_time", concat(col("day"), lit(" "), col("start")))
  flatDf = flatDf.transform (partial(util.StrToTimestamp, strColName="start_time", tsColName="start_ts", fmt=fmt))
  # flatDf.printSchema()
  # flatDf.show()

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
# Show data only has 'start' time. The 'end' time is whenever the next show starts.
# Convert the implicit 'end' time to an explicit 'end' time, by checking the next
# # show on the schedule. Each Show record should now have both 'start' and 'end' times.
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
  ndf = ndf.select ("channel_id", "channel", "program_id", "program", "day_date", "start_ts", "end_ts")
  ndf.show()
  return ndf

#-------------------------------------------
# We are given a static Dataframe of Shows with start and end times, and 
# a streaming Dataframe of user watching Sessions with start and end times.
# The Sessions contain only the Channel watched, not the Show. Join these
# Dataframes by checking the overlap between Show start/end times and
# Session start/end times, to get the user watching Sessions that include
# both Show and Channel.
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

  # Rename columns that have the same name in both Dataframes
  showDf = showDf.withColumnRenamed("channel_id", "show_channel_id") \
             .withColumnRenamed("start_ts", "show_start_ts") \
             .withColumnRenamed("end_ts", "show_end_ts")
  
  # Rename columns that have the same name in both Dataframes
  sessionDf = sessionDf.withColumnRenamed("channel_id", "session_channel_id") \
                       .withColumnRenamed("start_ts", "session_start_ts") \
                       .withColumnRenamed("end_ts", "session_end_ts")

  # Join the two Dataframes based on the Channel, such that the Session watching
  # start/end times overlap with the Show start/end times. This is a static-stream
  # join. We would like to use Left Outer join, so that Shows with no watching 
  # Sessions also get included, but that is not supported.
  overlapDf = sessionDf.join(showDf,
    (sessionDf.session_channel_id == showDf.show_channel_id) &
    (sessionDf.session_start_ts <= showDf.show_end_ts) &
    (sessionDf.session_end_ts >= showDf.show_start_ts), "inner")

  # From the overlap, compute the start and end time that the user watched each show.
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

  # overlapDf.printSchema()

  # Use only the relevant columns and rename them as needed
  overlapDf = overlapDf.select(
                "program_id", "program", col("show_channel_id").alias("channel_id"), "channel",
                "user_id", "device_id",
                col("over_start_ts").alias("start_ts"), col("over_end_ts").alias("end_ts"))

  util.showStream(overlapDf)
  return overlapDf

#-------------------------------------------
# Join the Show watching Sessions with User demographic information
#-------------------------------------------
def getShowDemographic (showByUserDf, userDf):

  # Dfs on both sides of the join need an event timestamp field as a watermark, so that 
  # late-arriving data can be discarded, to prevent unbounded waiting.
  # The user dataframe has no time column, so we add current time as an artificial column
  showByUserDf = showByUserDf.withWatermark("start_ts", "1 minutes")
  # userDf = userDf \
  #             .withColumn("current_timestamp", current_timestamp()) \
  #             .withWatermark("current_timestamp", "1 minutes")

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

  util.showStream(joinDf)
  return joinDf

#-------------------------------------------
# Join the Show watching Sessions (including Demographic) with a streaming
# Dataframe of Device Location information. The device might be in motion
# and sends out location updates periodically along with a timestamp. 
# The Sessions contain a device ID which is used to join it with the Device 
# Location. We also must overlap the time of the location update with 
# the start/end time of the watching Session.
#-------------------------------------------
def getShowDemographicLocation(showDemographicDf, deviceLocDf):
  # Comment it out for now because start_ts is a string in the Kafka intermediate.
  # But in reality it will be a timestamp, so uncomment it when we are not loading
  # the intermediate from Kafka.
  showDemographicDf = showDemographicDf.withWatermark("start_ts", "90 seconds")
  deviceLocDf = deviceLocDf.withWatermark("kdts", "60 seconds")

  showDemographicDf = showDemographicDf.withColumnRenamed("device_id", "show_device_id")
  joinDf = showDemographicDf.join(deviceLocDf, 
      (showDemographicDf.show_device_id == deviceLocDf.device_id) &
      (showDemographicDf.start_ts <= deviceLocDf.kdts) &
      (showDemographicDf.end_ts >= deviceLocDf.kdts), "left_outer")
  joinDf = joinDf.select ("program_id", "channel_id", "program", "channel", 
                          "user_id", "name", "age", "gender", 
                          "device_id", "lon", "lat", "visibility",
                          "start_ts", "end_ts", "kdts")

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

  # +----------+----------+-------------+-------+-------+------+---+------+---------+--------+-------+----------+-------------------+-------------------+-------------------+
  # |program_id|channel_id|      program|channel|user_id|  name|age|gender|device_id|     lon|    lat|visibility|           start_ts|             end_ts|               kdts|
  # +----------+----------+-------------+-------+-------+------+---+------+---------+--------+-------+----------+-------------------+-------------------+-------------------+
  # |        12|        58|    Westworld|    HBO|     46|vihaan| 18|     M|       16| -87.125|39.5237|     10000|2021-02-01 23:55:47|2021-02-01 23:56:30|2021-02-01 23:56:02|
  # |        13|        58| Wonder Woman|    HBO|     46|vihaan| 18|     M|       16| -87.125|39.5237|     10000|2021-02-01 23:56:30|2021-02-01 23:58:00|2021-02-01 23:57:03|
  # |        14|        58|         Dune|    HBO|     46|vihaan| 18|     M|       16| -87.125|39.5237|     10000|2021-02-01 23:58:00|2021-02-01 23:58:19|2021-02-01 23:58:04|
  # |        16|        57|   Wild China|    BBC|     45| ketan| 55|     M|       15|-86.0689|40.7537|     10000|2021-02-02 00:01:31|2021-02-02 00:02:52|2021-02-02 00:02:08|
  # |        15|        58|     Avengers|    HBO|     46|vihaan| 18|     M|       17|   -64.0|  -34.0|     10000|2021-02-02 00:00:08|2021-02-02 00:01:53|2021-02-02 00:01:07|
  # |        16|        57|   Wild China|    BBC|     46|vihaan| 18|     M|       17|   -64.0|  -34.0|     10000|2021-02-02 00:01:53|2021-02-02 00:04:00|2021-02-02 00:02:08|
  # |        16|        57|   Wild China|    BBC|     46|vihaan| 18|     M|       17|   -64.0|  -34.0|     10000|2021-02-02 00:01:53|2021-02-02 00:04:00|2021-02-02 00:03:09|
  # |        13|        57|Living Planet|    BBC|     45| ketan| 55|     M|       14|    -8.0|   53.0|     10000|2021-02-01 23:57:00|2021-02-01 23:58:00|2021-02-01 23:57:03|
  # |        14|        57| Planet Earth|    BBC|     45| ketan| 55|     M|       14|    -8.0|   53.0|     10000|2021-02-01 23:58:00|2021-02-01 23:59:14|2021-02-01 23:58:04|
  # |        14|        57| Planet Earth|    BBC|     45| ketan| 55|     M|       14|    -8.0|   53.0|     10000|2021-02-01 23:58:00|2021-02-01 23:59:14|2021-02-01 23:59:05|
  # +----------+----------+-------------+-------+-------+------+---+------+---------+--------+-------+----------+-------------------+-------------------+-------------------+

  # +----------+----------+-------------+-------+-------+-----+---+------+---------+----+----+----------+-------------------+-------------------+----+
  # |program_id|channel_id|      program|channel|user_id| name|age|gender|device_id| lon| lat|visibility|           start_ts|             end_ts|kdts|
  # +----------+----------+-------------+-------+-------+-----+---+------+---------+----+----+----------+-------------------+-------------------+----+
  # |        12|        57|Animal Planet|    BBC|     45|ketan| 55|     M|     null|null|null|      null|2021-02-01 23:56:27|2021-02-01 23:57:00|null|
  # +----------+----------+-------------+-------+-------+-----+---+------+---------+----+----+----------+-------------------+-------------------+----+