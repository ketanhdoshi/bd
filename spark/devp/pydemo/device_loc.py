from functools import partial
import datetime
import sys

from pyspark.sql import SparkSession
from pyspark.sql.types import *
from pyspark.sql.functions import *
from pyspark.sql.window import Window

import util

#-------------------------------------------
# Read a streaming dataframe of device location data from JSON files
#-------------------------------------------
def readFileStream(spark, dataDir):
  deviceLocSchema = StructType() \
          .add("device_id", IntegerType()) \
          .add("name", StringType()) \
          .add("visibility", IntegerType()) \
          .add("coord", StructType() \
                .add("lon", FloatType()) \
                .add("lat", FloatType()) \
          )

  inputPath = dataDir + "/device_loc*.json"
  deviceLocDf = util.getFileStream(spark, deviceLocSchema, inputPath)
  deviceLocDf = deviceLocDf.select("device_id", col("name").alias("location"), "visibility", "coord.*")
  #deviceLocDf = processData(deviceLocDf)
  util.showStream(deviceLocDf)

      # +---------+-----------+----------+------+------+
      # |device_id|       name|visibility|   lon|   lat|
      # +---------+-----------+----------+------+------+
      # |       14|     Brazil|      5534| 37.29| 21.33|
      # |       15|  Argentina|      1295|453.68| 85.02|
      # |       16|       Peru|      1773| 83.91|429.17|
      # |       17|Switzerland|       392| 92.02|  4.86|
      # |       16|       Peru|      1618| 84.91|418.68|
      # |       16|       Peru|      1596| 86.03|411.22|
      # +---------+-----------+----------+------+------+

  return deviceLocDf

def processData(deviceLocDf):
  # The windowing aggregation only closes its window and outputs results when it receives
  # the next batch of data. So we keep several "device_loc*.json" files in the folder.
  deviceLocDf = deviceLocDf \
                  .select("device_id", "location", "visibility", "coord.*") \
                  .withColumn("timestamp", current_timestamp()) \
                  .withWatermark("timestamp", "5 seconds") \
                  .groupBy( \
                        window("timestamp", "10 seconds", "10 seconds"), \
                        "device_id", "location") \
                  .agg(avg("lon").alias("lon"), \
                       avg("lat").alias("lat"), \
                       max("visibility").alias("visibility")) \
                  .select("device_id", "location", "lon", "lat", "visibility")

      # +--------------------+---------+-----------+------------------+------------------+---------------+
      # |              window|device_id|       name|          avg(lon)|          avg(lat)|max(visibility)|
      # +--------------------+---------+-----------+------------------+------------------+---------------+
      # |[2021-02-25 11:14...|       14|     Brazil|37.290000915527344|21.329999923706055|           5534|
      # |[2021-02-25 11:14...|       17|Switzerland|  92.0199966430664| 4.860000133514404|            392|
      # |[2021-02-25 11:14...|       16|       Peru|  84.9500020345052|419.69000244140625|           1773|
      # |[2021-02-25 11:14...|       15|  Argentina|453.67999267578125|  85.0199966430664|           1295|
      # +--------------------+---------+-----------+------------------+------------------+---------------+
  
  return deviceLocDf

#-------------------------------------------
# Read from Kafka Device Location JSON topic
#-------------------------------------------
def readKafkaStream(spark, brokers, topic, offset):
  # 
  schema = StructType() \
          .add("kdct", StringType()) \
          .add("name", StringType()) \
          .add("visibility", IntegerType()) \
          .add("lon", FloatType()) \
          .add("lat", FloatType())

  jsonKeySchema = StructType() \
          .add("id", IntegerType())

  deviceLocDf = util.readKafkaJson(spark, brokers, topic, schema, offset=offset, jsonKeySchema=jsonKeySchema)
  deviceLocDf = deviceLocDf.withColumnRenamed("id", "device_id") \
                           .withColumnRenamed("name", "location")
  fmt = "yyyy/MM/dd HH:mm:ss"
  deviceLocDf = deviceLocDf.transform (partial(util.StrToTimestamp, strColName="kdct", tsColName="kdts", fmt=fmt))

  util.showStream(deviceLocDf)
  return deviceLocDf

#-------------------------------------------
# Get Device Location data stream
#-------------------------------------------
def doDeviceLoc(spark, dataDir, brokers, topic, offset, fromKafka):
  if (fromKafka):
    deviceLocDf = readKafkaStream(spark, brokers, topic, offset)
  else:
    deviceLocDf = readFileStream(spark, dataDir)
  return deviceLocDf