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
  adSchema = StructType() \
          .add("ad_id", IntegerType()) \
          .add("channel_id", IntegerType()) \
          .add("start_ts", TimestampType()) \
          .add("end_ts", TimestampType())

  dataDir = sys.argv[1]
  inputPath = dataDir + "/ad*.json"
  adDf = (spark
      .readStream                 
      .schema(adSchema)
      .json(inputPath)
  )

  # Output stream to console
  adDf.printSchema()
  adStream = (adDf
    .writeStream
    .outputMode("append")
    .option("forceDeleteTempCheckpointLocation", "true")
    .format("console")
    .start()
  )

  # +-----+----------+-------------------+-------------------+
  # |ad_id|channel_id|           start_ts|             end_ts|
  # +-----+----------+-------------------+-------------------+
  # |   17|        57|2021-02-01 07:16:24|2021-02-01 07:43:51|
  # |   13|        58|2021-02-01 08:19:26|2021-02-01 08:43:18|
  # |   14|        58|2021-02-01 09:07:03|2021-02-01 09:24:41|
  # |   17|        57|2021-02-02 07:22:27|2021-02-02 07:24:38|
  # |   18|        57|2021-02-02 08:47:06|2021-02-02 08:55:36|
  # +-----+----------+-------------------+-------------------+

  return adDf

