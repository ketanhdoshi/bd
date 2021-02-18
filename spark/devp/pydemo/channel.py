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

  flatDf = explodedDf.select("channel", "channel_id", "day", "show.*", concat(col("day"), lit(" "), col("show.start")).alias("start_time"))
  flatDf = flatDf.withColumn("day_date", to_date("day", "dd-MM-yyyy"))

  fmt = "dd-MM-yyyy HH:mm:ss"
  flatDf = flatDf.transform (partial(util.StrToTimestamp, strColName="start_time", tsColName="start_ts", fmt=fmt))
  flatDf.printSchema()
  flatDf.show()

  # +-------+----------+----------+-------------+----------+--------+-------------------+----------+-------------------+
  # |channel|channel_id|       day|      program|program_id|   start|         start_time|  day_date|           start_ts|
  # +-------+----------+----------+-------------+----------+--------+-------------------+----------+-------------------+
  # |    BBC|        57|01-02-2021|Animal Planet|        12|07:00:00|01-02-2021 07:00:00|2021-02-01|2021-02-01 07:00:00|
  # |    BBC|        57|01-02-2021|Living Planet|        13|07:45:00|01-02-2021 07:45:00|2021-02-01|2021-02-01 07:45:00|
  # |    BBC|        57|02-02-2021| Blue Kingdom|        14|07:00:00|02-02-2021 07:00:00|2021-02-02|2021-02-02 07:00:00|
  # |    BBC|        57|02-02-2021|   Wild China|        15|08:30:00|02-02-2021 08:30:00|2021-02-02|2021-02-02 08:30:00|
  # |    HBO|        58|01-02-2021|    Westworld|        12|07:00:00|01-02-2021 07:00:00|2021-02-01|2021-02-01 07:00:00|
  # |    HBO|        58|01-02-2021| Wonder Woman|        13|07:30:00|01-02-2021 07:30:00|2021-02-01|2021-02-01 07:30:00|
  # |    HBO|        58|01-02-2021|         Dune|        14|09:10:00|01-02-2021 09:10:00|2021-02-01|2021-02-01 09:10:00|
  # +-------+----------+----------+-------------+----------+--------+-------------------+----------+-------------------+


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
  return ndf

  # +-------+----------+----------+-------------+----------+--------+-------------------+----------+-------------------+-------------------+-------------------+-------------------+
  # |channel|channel_id|       day|      program|program_id|   start|         start_time|  day_date|           start_ts|            end_tmp|            end_day|             end_ts|
  # +-------+----------+----------+-------------+----------+--------+-------------------+----------+-------------------+-------------------+-------------------+-------------------+
  # |    BBC|        57|01-02-2021|Animal Planet|        12|07:00:00|01-02-2021 07:00:00|2021-02-01|2021-02-01 07:00:00|2021-02-01 07:45:00|2021-02-02 00:00:00|2021-02-01 07:45:00|
  # |    BBC|        57|01-02-2021|Living Planet|        13|07:45:00|01-02-2021 07:45:00|2021-02-01|2021-02-01 07:45:00|               null|2021-02-02 00:00:00|2021-02-02 00:00:00|
  # |    BBC|        57|02-02-2021| Blue Kingdom|        14|07:00:00|02-02-2021 07:00:00|2021-02-02|2021-02-02 07:00:00|2021-02-02 08:30:00|2021-02-03 00:00:00|2021-02-02 08:30:00|
  # |    BBC|        57|02-02-2021|   Wild China|        15|08:30:00|02-02-2021 08:30:00|2021-02-02|2021-02-02 08:30:00|               null|2021-02-03 00:00:00|2021-02-03 00:00:00|
  # |    HBO|        58|01-02-2021|    Westworld|        12|07:00:00|01-02-2021 07:00:00|2021-02-01|2021-02-01 07:00:00|2021-02-01 07:30:00|2021-02-02 00:00:00|2021-02-01 07:30:00|
  # |    HBO|        58|01-02-2021| Wonder Woman|        13|07:30:00|01-02-2021 07:30:00|2021-02-01|2021-02-01 07:30:00|2021-02-01 09:10:00|2021-02-02 00:00:00|2021-02-01 09:10:00|
  # |    HBO|        58|01-02-2021|         Dune|        14|09:10:00|01-02-2021 09:10:00|2021-02-01|2021-02-01 09:10:00|               null|2021-02-02 00:00:00|2021-02-02 00:00:00|
  # +-------+----------+----------+-------------+----------+--------+-------------------+----------+-------------------+-------------------+-------------------+-------------------+


#-------------------------------------------
#-------------------------------------------
def getShowOverlap(showDf, sessionDf):
  # df1 = showDf.withColumnRenamed("channel_id", "show_channel_id") \
  #             .withColumnRenamed("start_ts", "show_start_ts") \
  #             .withColumnRenamed("end_ts", "show_end_ts")

  # overlapDf = sessionDf.join(df1, 
  #     expr(""" 
  #         show_channel_id = channel_id AND
  #         start_ts <= show_end_ts AND
  #         end_ts >= show_start_ts
  #       """
  #     ))

  overlapDf = sessionDf.join(showDf,
    (sessionDf.channel_id == showDf.channel_id) &
    (sessionDf.start_ts <= showDf.end_ts) &
    (sessionDf.end_ts >= showDf.start_ts), "left_outer")

  # !!!!!!!!!! NB: Still need to rename columns and use the least/greatest as done in getAdOverlap

  overlapDf.printSchema()
  overlapStream = (overlapDf
    .writeStream
    .outputMode("append")
    .option("forceDeleteTempCheckpointLocation", "true")
    .format("console")
    .start()
  )

  # +-------+---------+----------+-------------------+-------------------+-------+----------+----------+-------------+----------+--------+-------------------+----------+-------------------+-------------------+-------------------+-------------------+
  # |user_id|device_id|channel_id|           start_ts|             end_ts|channel|channel_id|       day|      program|program_id|   start|         start_time|  day_date|           start_ts|            end_tmp|            end_day|             end_ts|
  # +-------+---------+----------+-------------------+-------------------+-------+----------+----------+-------------+----------+--------+-------------------+----------+-------------------+-------------------+-------------------+-------------------+
  # |     46|       17|        57|2021-02-01 09:20:16|2021-02-01 09:36:56|    BBC|        57|01-02-2021|Living Planet|        13|07:45:00|01-02-2021 07:45:00|2021-02-01|2021-02-01 07:45:00|               null|2021-02-02 00:00:00|2021-02-02 00:00:00|
  # |     46|       17|        58|2021-02-01 08:32:51|2021-02-01 09:20:16|    HBO|        58|01-02-2021|         Dune|        14|09:10:00|01-02-2021 09:10:00|2021-02-01|2021-02-01 09:10:00|               null|2021-02-02 00:00:00|2021-02-02 00:00:00|
  # |     46|       17|        58|2021-02-01 08:32:51|2021-02-01 09:20:16|    HBO|        58|01-02-2021| Wonder Woman|        13|07:30:00|01-02-2021 07:30:00|2021-02-01|2021-02-01 07:30:00|2021-02-01 09:10:00|2021-02-02 00:00:00|2021-02-01 09:10:00|
  # |     46|       16|        58|2021-02-01 07:00:35|2021-02-01 09:05:17|    HBO|        58|01-02-2021| Wonder Woman|        13|07:30:00|01-02-2021 07:30:00|2021-02-01|2021-02-01 07:30:00|2021-02-01 09:10:00|2021-02-02 00:00:00|2021-02-01 09:10:00|
  # |     46|       16|        58|2021-02-01 07:00:35|2021-02-01 09:05:17|    HBO|        58|01-02-2021|    Westworld|        12|07:00:00|01-02-2021 07:00:00|2021-02-01|2021-02-01 07:00:00|2021-02-01 07:30:00|2021-02-02 00:00:00|2021-02-01 07:30:00|
  # |     45|       14|        57|2021-02-01 07:12:35|2021-02-01 08:19:35|    BBC|        57|01-02-2021|Living Planet|        13|07:45:00|01-02-2021 07:45:00|2021-02-01|2021-02-01 07:45:00|               null|2021-02-02 00:00:00|2021-02-02 00:00:00|
  # |     45|       14|        57|2021-02-01 07:12:35|2021-02-01 08:19:35|    BBC|        57|01-02-2021|Animal Planet|        12|07:00:00|01-02-2021 07:00:00|2021-02-01|2021-02-01 07:00:00|2021-02-01 07:45:00|2021-02-02 00:00:00|2021-02-01 07:45:00|
  # |     45|       15|        57|2021-02-02 07:19:35|2021-02-02 07:57:35|    BBC|        57|02-02-2021| Blue Kingdom|        14|07:00:00|02-02-2021 07:00:00|2021-02-02|2021-02-02 07:00:00|2021-02-02 08:30:00|2021-02-03 00:00:00|2021-02-02 08:30:00|
  # +-------+---------+----------+-------------------+-------------------+-------+----------+----------+-------------+----------+--------+-------------------+----------+-------------------+-------------------+-------------------+-------------------+

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

  overlapDf.printSchema()
  overlapStream = (overlapDf
    .writeStream
    .outputMode("append")
    .option("forceDeleteTempCheckpointLocation", "true")
    .format("console")
    .start()
  )

  # NB: The Outer NULL results (for Ad ID 18) should be here but are missing!!
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



#-------------------------------------------
# Create a Spark Session and process the Channel data
#-------------------------------------------
spark = SparkSession.builder.appName("Nested Json").getOrCreate()

# Process ads
adDf = ad.getData(spark)

# Process actions
# actionDf = action.getData(spark)

# Process sessions
sessionDf = session.getData(spark)

""" # Process channels
jsonDf = getData(spark)
dataDir = sys.argv[1] + "/"
parquetDf = getParquet(jsonDf, dataDir)

# Process shows (from channels)
flatDf = flattenShows(parquetDf)
showDf = getEndTime(flatDf)

# Sessions with shows
getShowOverlap(showDf, sessionDf) """

getAdOverlap(adDf, sessionDf)

spark.streams.awaitAnyTermination(10000)
print("========== DONE ==========" )
