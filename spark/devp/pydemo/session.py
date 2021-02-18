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
def getData(spark):
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

