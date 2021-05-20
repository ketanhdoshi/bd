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
                  .select("device_id", "location", "visibility", "lon", "lat") \
                  .groupBy("device_id", "location") \
                  .agg(avg("lon").alias("lon"), \
                       avg("lat").alias("lat"), \
                       max("visibility").alias("visibility")) \
                  .select("device_id", "location", "lon", "lat", "visibility")
  return deviceLocDf

def oldprocessData(deviceLocDf):
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

  # {"id":2963597}#{"lon":-8.0,"lat":53.0,"visibility":10000,"name":"Ireland","kdct":"2021/05/06 06:16:43"}
  # {"id":4924733}#{"lon":-86.0689,"lat":40.7537,"visibility":10000,"name":"Peru","kdct":"2021/05/06 06:16:44"}
  # {"id":4254884}#{"lon":-87.125,"lat":39.5237,"visibility":10000,"name":"Brazil","kdct":"2021/05/06 06:16:44"}
  # {"id":3865483}#{"lon":-64.0,"lat":-34.0,"visibility":10000,"name":"Argentina","kdct":"2021/05/06 06:16:44"}
  # {"id":3895114}#{"lon":-71.0,"lat":-30.0,"visibility":10000,"name":"Chile","kdct":"2021/05/06 06:16:44"}
  # {"id":2963597}#{"lon":-8.0,"lat":53.0,"visibility":10000,"name":"Ireland","kdct":"2021/05/06 06:17:45"}
  # {"id":4924733}#{"lon":-86.0689,"lat":40.7537,"visibility":10000,"name":"Peru","kdct":"2021/05/06 06:17:45"}
  # {"id":4254884}#{"lon":-87.125,"lat":39.5237,"visibility":10000,"name":"Brazil","kdct":"2021/05/06 06:17:45"}
  # {"id":3865483}#{"lon":-64.0,"lat":-34.0,"visibility":10000,"name":"Argentina","kdct":"2021/05/06 06:17:45"}
  # {"id":3895114}#{"lon":-71.0,"lat":-30.0,"visibility":10000,"name":"Chile","kdct":"2021/05/06 06:17:45"}

  deviceLocDf = util.readKafkaJson(spark, brokers, topic, schema, offset=offset, jsonKeySchema=jsonKeySchema)
  deviceLocDf = deviceLocDf.withColumnRenamed("id", "device_id") \
                           .withColumnRenamed("name", "location")
  fmt = "yyyy/MM/dd HH:mm:ss"
  deviceLocDf = deviceLocDf.transform (partial(util.StrToTimestamp, strColName="kdct", tsColName="kdctts", fmt=fmt)) \
                      .withColumn("kdctmin", lit("2021/05/18 10:30:12")) \
                      .withColumn("kdbeg", lit("2021/02/01 23:55:00")) \
                      .withColumn("kdts", to_timestamp(col("kdbeg"), fmt) + (col("kdctts") - to_timestamp(col("kdctmin"), fmt)))

  # Map the device_id values to simpler values that are used in the rest of the data
  deviceDict = {2963597: 14, 4924733: 15, 4254884: 16, 3865483: 17, 3895114: 18}
  deviceLocDf = deviceLocDf.na.replace(deviceDict)

  # +---------+-------------------+-------+--------+----------+
  # |device_id|               kdts|    lat|     lon|visibility|
  # +---------+-------------------+-------+--------+----------+
  # |       14|2021-05-06 06:16:43|   53.0|    -8.0|     10000|
  # |       15|2021-05-06 06:16:44|40.7537|-86.0689|     10000|
  # |       16|2021-05-06 06:16:44|39.5237| -87.125|     10000|
  # |       17|2021-05-06 06:16:44|  -34.0|   -64.0|     10000|
  # |       18|2021-05-06 06:16:44|  -30.0|   -71.0|     10000|
  # |       14|2021-05-06 06:17:45|   53.0|    -8.0|     10000|
  # |       15|2021-05-06 06:17:45|40.7537|-86.0689|     10000|
  # |       16|2021-05-06 06:17:45|39.5237| -87.125|     10000|
  # |       17|2021-05-06 06:17:45|  -34.0|   -64.0|     10000|
  # |       18|2021-05-06 06:17:45|  -30.0|   -71.0|     10000|
  # +---------+-------------------+-------+--------+----------+

  # Use just the relevant fields
  deviceLocDf = deviceLocDf.select ("device_id", "kdts", "lat", "lon", "visibility")
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