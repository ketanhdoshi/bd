from functools import partial
import datetime
import sys

from pyspark.sql import SparkSession
from pyspark.sql.types import *
from pyspark.sql.functions import *
from pyspark.sql.window import Window

import util

#-------------------------------------------
# Read a streaming dataframe of session data from JSON files
#-------------------------------------------
def readFileStream(spark, dataDir):
  sessionSchema = StructType() \
          .add("user_id", IntegerType()) \
          .add("device_id", IntegerType()) \
          .add("channel_id", IntegerType()) \
          .add("start_ts", TimestampType()) \
          .add("end_ts", TimestampType())

  dataDir = sys.argv[1]
  inputPath = dataDir + "/session*.json"
  sessionDf = (spark
      .readStream                 
      .schema(sessionSchema)
      .json(inputPath)
  )

  # Output stream to console
  sessionDf.printSchema()
  sessionStream = (sessionDf
    .writeStream
    .outputMode("append")
    .option("forceDeleteTempCheckpointLocation", "true")
    .format("console")
    .start()
  )

  # +-------+---------+----------+-------------------+-------------------+
  # |user_id|device_id|channel_id|           start_ts|             end_ts|
  # +-------+---------+----------+-------------------+-------------------+
  # |     46|       17|        57|2021-02-01 09:20:16|2021-02-01 09:36:56|
  # |     46|       17|        58|2021-02-01 08:32:51|2021-02-01 09:20:16|
  # |     46|       16|        58|2021-02-01 07:00:35|2021-02-01 09:05:17|
  # |     45|       14|        57|2021-02-01 07:12:35|2021-02-01 08:19:35|
  # |     45|       15|        57|2021-02-02 07:19:35|2021-02-02 07:57:35|
  # +-------+---------+----------+-------------------+-------------------+

  return sessionDf

#-------------------------------------------
# Read from Kafka Session JSON topic
#-------------------------------------------
def readKafkaStream(spark, brokers, topic, offset):
  # 
  schema = StructType() \
          .add("user_id", IntegerType()) \
          .add("device_id", IntegerType()) \
          .add("channel_id", IntegerType()) \
          .add("start", StringType()) \
          .add("end", StringType())

  sessionDf = util.readKafkaJson(spark, brokers, topic, schema, offset=offset)

  fmt = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
  sessionDf = sessionDf.transform (partial(util.StrToTimestamp, strColName="start", tsColName="start_ts", fmt=fmt))
  sessionDf = sessionDf.transform (partial(util.StrToTimestamp, strColName="end", tsColName="end_ts", fmt=fmt))

  util.showStream(sessionDf)
  return sessionDf

#-------------------------------------------
# Get Session data stream
#-------------------------------------------
def doSession(spark, dataDir, brokers, topic, offset, fromKafka):
  # Note that Actions will be processed from Scala

  if (fromKafka):
    sessionDf = readKafkaStream(spark, brokers, topic, offset)
  else:
    sessionDf = readFileStream(spark, dataDir)
  return sessionDf

#-------------------------------------------
# Read from Kafka Action JSON topic
# Action data will be read and processed to Sessions from Scala.
# This is here only to quickly test the Action data in the Kafka topic.
#-------------------------------------------
def readKafkaAction(spark, brokers, topic, offset):
  # 
  schema = StructType() \
          .add("user", StringType()) \
          .add("user_id", IntegerType()) \
          .add("channel_id", IntegerType()) \
          .add("device_id", IntegerType()) \
          .add("action", IntegerType()) \
          .add("action_ts", TimestampType())

  actionDf = util.readKafkaJson(spark, brokers, topic, schema, offset=offset)
  
  util.showStream(actionDf)
  return actionDf

#-------------------------------------------
# Obsolete: Sessions Static data (non-streaming)
#-------------------------------------------
def getDataStatic(spark):
  sessionJson = """[
    {
      "user": "ketan",
      "user_id": 45,
      "channel_id": 57,
      "start_ts": 1612163555,
      "end_ts": 1612167575
    },
    {
      "user": "ketan",
      "user_id": 45,
      "channel_id": 57,
      "start_ts": 1612250375,
      "end_ts": 1612252655
    },
    {
      "user": "vihaan",
      "user_id": 46,
      "channel_id": 58,
      "start_ts": 1612162835,
      "end_ts": ````````````````````````````````````````````````````` `````````````````````````````````````````````````````
    }
  ]"""

  sessionDf = util.jsonToDataFrame(spark, sessionJson, sessionSchema)
  sessionDf.printSchema()
  sessionDf.show()

  return sessionDf

