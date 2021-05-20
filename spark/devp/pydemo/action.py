from functools import partial
import datetime
import sys

from pyspark.sql import SparkSession
from pyspark.sql.types import *
from pyspark.sql.functions import *
from pyspark.sql.window import Window

import util

def getActionData(spark):
  actionSchema = StructType() \
          .add("user", StringType()) \
          .add("user_id", IntegerType()) \
          .add("channel_id", IntegerType()) \
          .add("device_id", IntegerType()) \
          .add("action", IntegerType()) \
          .add("action_ts", TimestampType())

  # A single file cannot be read as a stream. You have to read multiple files from
  # a folder. So even though there is only one file, we have to add the wildcard
  # to the file name ie. "action*.json"
  inputPath = dataDir + "/action*.json"
  actionDf = (spark
      .readStream                 
      .schema(actionSchema)
      .json(inputPath)
  )

  #.option("maxFilesPerTrigger", 1)  # Treat a sequence of files as a stream by picking one file at a time

  # Output stream to console
  actionDf.printSchema()
  actionStream = (actionDf
    .writeStream
    .outputMode("append")
    .option("forceDeleteTempCheckpointLocation", "true")
    .format("console")
    .start()
  )

#-------------------------------------------
#-------------------------------------------
def getData(spark):
  actionSchema = StructType() \
          .add("user", StringType()) \
          .add("user_id", IntegerType()) \
          .add("channel_id", IntegerType()) \
          .add("device_id", IntegerType()) \
          .add("action", IntegerType()) \
          .add("action_ts", TimestampType())

  # A single file cannot be read as a stream. You have to read multiple files from
  # a folder. So even though there is only one file, we have to add the wildcard
  # to the file name ie. "action*.json"
  dataDir = sys.argv[1]
  inputPath = dataDir + "/action*.json"
  actionDf = (spark
      .readStream                 
      .schema(actionSchema)
      .json(inputPath)
  )

  #.option("maxFilesPerTrigger", 1)  # Treat a sequence of files as a stream by picking one file at a time

  # Output stream to console
  actionDf.printSchema()
  actionStream = (actionDf
    .writeStream
    .outputMode("append")
    .option("forceDeleteTempCheckpointLocation", "true")
    .format("console")
    .start()
  )

  # +------+-------+----------+---------+------+-------------------+
  # |  user|user_id|channel_id|device_id|action|          action_ts|
  # +------+-------+----------+---------+------+-------------------+
  # | ketan|     45|        57|       14|     1|2021-02-01 07:12:35|
  # | ketan|     45|        57|       14|     2|2021-02-01 08:19:35|
  # | ketan|     45|        57|       15|     1|2021-02-02 07:19:35|
  # | ketan|     45|        57|       15|     2|2021-02-02 07:57:35|
  # |vihaan|     46|        58|       16|     1|2021-02-01 07:00:35|
  # |vihaan|     46|        58|       16|     2|2021-02-01 09:05:17|
  # |vihaan|     46|        58|       17|     1|2021-02-01 08:32:51|
  # |vihaan|     46|        57|       17|     3|2021-02-01 09:20:16|
  # |vihaan|     46|        57|       17|     2|2021-02-01 09:36:56|
  # +------+-------+----------+---------+------+-------------------+

  outDir = dataDir + "/out_action"
  actionFileStream = (actionDf
    # If the df has many partitions, it will still write a separate file per partition 
    # despite using the trigger below. So coalesce to one partition
    .coalesce(1)
    .writeStream
    .outputMode("append")
    .format("json")
    # only write a new file every 10 seconds, so we don't generate hundreds of files
    .trigger(processingTime="10 seconds")
    .option("path", outDir)
    .option("checkpointLocation", outDir + "/checkpoint_action")
    .start()
  )

  # File output only supports Append mode, which cannot be used if you have any aggregations.
  # In that case, write aggregates to in an in-memory table using Complete mode, and then 
  # query that table using spark.sql.
  actionMemStream = (actionDf
    .writeStream
    .queryName("action_mem")    # this query name will be the table name
    .outputMode("append")
    .format("memory")
    .start()
  )

  # Since the stream starts asynchronously while the sql runs immediately, simply calling
  # the sql below will output an empty table. So we wait a few seconds to allow the stream
  # time to execute and populate the table before querying it.
  # Think that the main thread launches another thread in which the streamingquery logic runs.
  # While the sql below executes on the main thread
  import time
  while(actionMemStream.isActive):
    time.sleep (10)
    spark.sql("select * from action_mem").show()   # query in-memory table

  return actionDf

