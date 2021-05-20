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
            "start": "23:55:00"
          },
          {
            "program": "Living Planet",
            "program_id": 13,
            "start": "23:57:00"
          },
          {
            "program": "Planet Earth",
            "program_id": 14,
            "start": "23:58:00"
          }
        ],
		    "02-02-2021": [
          {
            "program": "Blue Kingdom",
            "program_id": 15,
            "start": "00:00:00"
          },
          {
            "program": "Wild China",
            "program_id": 16,
            "start": "00:01:00"
          },
          {
            "program": "Wild Africa",
            "program_id": 17,
            "start": "00:04:00"
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
            "start": "23:55:00"
          },
          {
            "program": "Wonder Woman",
            "program_id": 13,
            "start": "23:56:30"
          },
          {
            "program": "Dune",
            "program_id": 14,
            "start": "23:58:00"
          }
        ],
        "02-02-2021": [
          {
            "program": "Avengers",
            "program_id": 15,
            "start": "00:00:00"
          }
        ]
      }
    }
  ]"""

  channelJsonDf = util.jsonToDataFrame(spark, channelJson, channelSchema)
  # channelJsonDf.printSchema()
  # channelJsonDf.show()

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

  return channelJsonDf

#-------------------------------------------
#-------------------------------------------
def getParquet(spark, jsonDf, dataDir):
  # DataFrames can be saved as Parquet files, maintaining the schema information.
  parquetFile = dataDir + "channel.parquet"
  jsonDf.write.parquet(parquetFile, mode="overwrite")

  # Read in the Parquet file created above.
  # Parquet files are self-describing so the schema is preserved.
  # The result of loading a parquet file is also a DataFrame.
  channelDf = spark.read.parquet(parquetFile)
  # channelDf.show()

  return channelDf

#-------------------------------------------
# Get Channels data
#-------------------------------------------
def doChannel(spark):
  # Process channels
  channelJsonDf = getData(spark)
  dataDir = sys.argv[1] + "/"
  channelDf = getParquet(spark, channelJsonDf, dataDir)

  return channelDf