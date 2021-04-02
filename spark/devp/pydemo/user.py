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
def getData(spark, dataDir):
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

