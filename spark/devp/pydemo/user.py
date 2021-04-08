from functools import partial
import datetime
import sys

from pyspark.sql import SparkSession
from pyspark.sql.types import *
from pyspark.sql.functions import *
from pyspark.sql.window import Window

import util

#-------------------------------------------
# Read a streaming dataframe of user data from JSON files
#-------------------------------------------
def readFileStream(spark, dataDir):
  userSchema = StructType() \
          .add("user_id", IntegerType()) \
          .add("name", StringType()) \
          .add("age", IntegerType()) \
          .add("gender", StringType())

  inputPath = dataDir + "/user*.json"
  userDf = util.getFileStream(spark, userSchema, inputPath)
  util.showStream(userDf)

  # +-------+-------+---+------+
  # |user_id|   name|age|gender|
  # +-------+-------+---+------+
  # |     45|  ketan| 55|     M|
  # |     46| vihaan| 18|     M|
  # |     47|meghana| 51|     F|
  # +-------+-------+---+------+

  return userDf


#-------------------------------------------
# Read from Kafka Customer JSON topic
#-------------------------------------------
def readKafkaStream(spark, brokers, topic, offset):
  # 
  schema = StructType() \
          .add("ID", IntegerType()) \
          .add("user_name", StringType()) \
          .add("age", IntegerType()) \
          .add("gender", StringType())

  userDf = util.readKafkaJson(spark, brokers, topic, schema, offset=offset)
  userDf = userDf.withColumnRenamed("ID", "user_id") \
                 .withColumnRenamed("user_name", "name")
  util.showStream(userDf)
  return userDf

#-------------------------------------------
# Get User data stream
#-------------------------------------------
def doUser(spark, dataDir, brokers, topic, offset, fromKafka):
  if (fromKafka):
    userDf = readKafkaStream(spark, brokers, topic, offset)
  else:
    userDf = readFileStream(spark, dataDir)
  return userDf