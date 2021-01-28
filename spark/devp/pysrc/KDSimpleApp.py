from pyspark.sql import SparkSession
import os
import sys

SPARK_HOME = os.environ['SPARK_HOME']
logFile = sys.argv[0]  # Should be some file on your system
spark = SparkSession.builder.appName("SimpleApp").getOrCreate()
logData = spark.read.text(logFile).cache()

numAs = logData.filter(logData.value.contains('a')).count()
numBs = logData.filter(logData.value.contains('b')).count()

print("============= Lines with a: %i, lines with b: %i" % (numAs, numBs))

spark.stop()