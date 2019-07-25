from pyspark.sql import SparkSession
from pyspark.sql.types import *
from pyspark.sql.functions import *
import datetime;

spark = SparkSession.builder.appName("Nested Json").getOrCreate()

simple = StructType() \
  .add("id", LongType()) \
  .add("jstring", StringType()) 

def jsonToDataFrame(json, schema=None):
  print ("+++++++++++++++", json)
  sc = spark.sparkContext
  df = spark.read.schema(schema).json(sc.parallelize([json]))
  return df

simpleDF = jsonToDataFrame( """
  {
    "id": 0,
    "jstring": "shit \n great"
  }
  {
    "id": 1,
    "jstring": "hello"   
  }
  """, simple)

simpleDF.printSchema()
simpleDF.show(2)

nested = StructType() \
  .add("dc_id", StringType()) \
  .add("source", MapType(StringType(), StructType() \
     .add("description", StringType()) \
     .add("ip", StringType()) \
     .add("id", LongType()) \
     .add("temp", LongType()) \
     .add("c02_level", LongType()) \
     .add("geo", StructType() \
        .add("lat", DoubleType()) \
        .add("long", DoubleType()))))

dataDF = jsonToDataFrame( """{

    "dc_id": "dc-101",
    "source": {
        "sensor-igauge": {
        "id": 10,
        "ip": "68.28.91.22",
        "description": "Sensor attached to the container ceilings",
        "temp":35,
        "c02_level": 1475,
        "geo": {"lat":38.00, "long":97.00}                        
      },
      "sensor-ipad": {
        "id": 13,
        "ip": "67.185.72.1",
        "description": "Sensor ipad attached to carbon cylinders",
        "temp": 34,
        "c02_level": 1370,
        "geo": {"lat":47.41, "long":-122.00}
      },
      "sensor-inest": {
        "id": 8,
        "ip": "208.109.163.218",
        "description": "Sensor attached to the factory ceilings",
        "temp": 40,
        "c02_level": 1346,
        "geo": {"lat":33.61, "long":-111.89}
      },
      "sensor-istick": {
        "id": 5,
        "ip": "204.116.105.67",
        "description": "Sensor embedded in exhaust pipes in the ceilings",
        "temp": 40,
        "c02_level": 1574,
        "geo": {"lat":35.93, "long":-85.46}
      }
    }
  }""", nested)

dataDF.printSchema()

tot = dataDF.count()
print("============= Total: %i" % (tot))

# The explode() function creates a new row for each element in the given map column.
# select from DataFrame with a single entry, and explode its column source, which 
# is Map, with nested structure.
explodedDF = dataDF.select(col("dc_id"), explode(col("source")))
explodedDF.printSchema()
explodedDF.show(4)

notifydevicesDF = explodedDF.select (
   col ("dc_id").alias ("dcId"),
   col ("key").alias("deviceType"),
   col ("value").getItem("ip").alias("ip"),
   col ("value").getItem("id").alias("deviceId"),
   col ("value").getItem("c02_level").alias("c02_level"),
   col ("value").getItem("temp").alias("temp"),
   col ("value").getItem("geo").getItem("lat").alias("lat"),
   col ("value").getItem("geo").getItem("long").alias("lon"))
notifydevicesDF.printSchema()
notifydevicesDF.show(4)

def f(device):
   # NB: On a cluster this print doesn't do anything as it prints to the stdout
   # on the Executor nodes, but not on the driver
   print("----------------------------- ", device.ip)

notifydevicesDF.foreach(f)

print("========== DONE ==========" )
