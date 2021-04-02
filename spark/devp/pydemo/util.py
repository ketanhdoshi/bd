from functools import partial
import datetime;
import sys

from pyspark.sql import SparkSession
from pyspark.sql.types import *
from pyspark.sql.functions import *

#-------------------------------------------
# Read a streaming dataframe from a set of JSON files in a folder, given a schema.
# A single file cannot be read as a stream. You have to read multiple files from
# a folder. So even though there is only one file, we have to add the wildcard
# to the file name in the inputPath ie. "action*.json"
#-------------------------------------------
def getFileStream(spark, schema, inputPath):
  # Read the dataframe as a stream from all the JSON files 
  df = (spark
      .readStream                 
      .schema(schema)
      .option("maxFilesPerTrigger", 1)  # Treat a sequence of files as a stream by picking one file at a time
      #.trigger(ProcessingTime("10 seconds")) # check for files every 10s
      .json(inputPath)
  )

  # Print schema
  df.printSchema()

  # Return the streaming dataframe
  return df

#-------------------------------------------
# Write a streaming dataframe as a stream to the console
#-------------------------------------------
def showStream(df):
  # Output stream to console
  outStream = (df
      .writeStream
      .outputMode("append")
      .option("forceDeleteTempCheckpointLocation", "true")
      .format("console")
      .start()
    )

#-------------------------------------------
# Write a streaming dataframe to a folder. Since it is a stream, a new file will
# be written in the folder at regular intervals
#-------------------------------------------
def writeFileStream(df, outDir, checkpointDir):
  outStream = (df
    # If the df has many partitions, it will still write a separate file per partition 
    # despite using the trigger below. So coalesce to one partition
    .coalesce(1)
    .writeStream
    .outputMode("append")
    .format("json")
    # only write a new file every 10 seconds, so we don't generate hundreds of files
    .trigger(processingTime="10 seconds")
    .option("path", outDir)
    .option("checkpointLocation", checkpointDir)
    .start()
  )

#-------------------------------------------
# Read a streaming dataframe from a Kafka topic in JSON
#-------------------------------------------
def readKafkaJson(spark, brokers, topic, schema, offset=0, jsonKeySchema=None):

  # Read from the beginning (ie. 'earliest' offset) or from a specific number offset
  startingOffsets = "earliest" if (offset == 0) else f"""{{ "{topic}": {{ "0": {offset} }} }}"""

  # Subscribe to 1 topic
  # read data from the start of the stream
  df = spark \
    .readStream \
    .format("kafka") \
    .option("kafka.bootstrap.servers", brokers) \
    .option("subscribe", topic) \
    .option("startingOffsets", startingOffsets) \
    .option("kafka.sasl.mechanism", "PLAIN") \
    .option("kafka.security.protocol", "SASL_PLAINTEXT") \
    .option("kafka.sasl.jaas.config", """org.apache.kafka.common.security.plain.PlainLoginModule required username="test" password="test123";""") \
    .load()

  # The Key and Value are binary data, so deserialise them into strings
  # The Value gets deserialised into a string which we know is in JSON format
  df = df.selectExpr("CAST(key AS STRING)", \
                "CAST(value AS STRING)", \
                "offset", \
                "CAST(timestamp AS TIMESTAMP)")

  if (jsonKeySchema):
    # Use keySchema only for JSON formatted keys
    df = df.select( from_json("key", jsonKeySchema).alias("key"), \
                    from_json("value", schema).alias("data"), \
                    col("offset").alias("msgoffset"), \
                    col("timestamp").alias("msgtime"))

    # Flatten it out so that those columns now appear at the top-level
    df = df.selectExpr("key.*", "data.*", "msgoffset", "msgtime")
  else:
    # Deserialise the JSON into its fields using the given schema. 
    # The dataframe will now contain columns one level down, nested 
    # under a structure named 'data'
    df = df.select(from_json("value", schema).alias("data"), \
                    col("offset").alias("msgoffset"), \
                    col("timestamp").alias("msgtime"))

    # Flatten it out so that those columns now appear at the top-level
    df = df.selectExpr("data.*", "msgoffset", "msgtime")

  df.printSchema()
  return df

#-------------------------------------------
# Write a streaming dataframe to a Kafka topic in JSON
#-------------------------------------------
def writeKafkaJson(brokers, topic, df, keyField, checkpointDir):
  # A dataframe must have string or binary columns named 'key' and 'value'
  # so that it can be written to a Kafka topic. 
  # The 'value' is set to a JSON string serialised from all fields in the dataframe
  if (keyField):
    dfout = df.selectExpr( \
            f'CAST({keyField} AS STRING) AS key', \
            "to_json(struct(*)) AS value")
  else:
     dfout = df.selectExpr("to_json(struct(*)) AS value")

  #Write the dataframe to a Kafka topic. A checkpoint location must be specified
  kafkaOutput = dfout.writeStream \
          .format("kafka") \
          .option("kafka.bootstrap.servers", brokers) \
          .option("topic", topic) \
          .option("kafka.sasl.mechanism", "PLAIN") \
          .option("kafka.security.protocol", "SASL_PLAINTEXT") \
          .option("kafka.sasl.jaas.config", """org.apache.kafka.common.security.plain.PlainLoginModule required username="test" password="test123";""") \
          .option("checkpointLocation", checkpointDir) \
          .outputMode("append") \
          .start()

#-------------------------------------------
# Read a JSON string into a DataFrame based on a provided schema
#-------------------------------------------
def jsonToDataFrame(spark, jsonStr, schema=None):
  print ("+++++++++++++++", jsonStr)
  sc = spark.sparkContext
  df = spark.read.schema(schema).json(sc.parallelize([jsonStr]))
  return df

#-------------------------------------------
# Append a Timestamp column after converting an epoch-time Long value in milliseconds 
#-------------------------------------------
def LongToTimestamp(df, longColName, tsColName):
  # The given column contains a Unix timestamp in milliseconds, but is a long
  # datatype in the input data. So convert it to seconds which is what Spark expects
  # timestamps to be, and then cast it to a timestamp
  ndf = df.withColumn(tsColName, (col(longColName) / 1000).cast(TimestampType()))
  return ndf

#-------------------------------------------
# Append a Timestamp column after converting a String time value 
#-------------------------------------------
def StrToTimestamp(df, strColName, tsColName, fmt):
  # Use the given format codes to parse the string and convert it
  ndf = df.withColumn(tsColName, to_timestamp(col(strColName), fmt))
  return ndf

#-------------------------------------------
# Given a Timestamp column, extract different date/time components
# from it add a column for each one: a Date column, a string formatted 
# time, as well as month, day, hour, minute etc
#-------------------------------------------
def ExpandTimestamp(df, tsColName, prefixStr= ""):
  # Extract a prefix from the name of the Timestamp column.
  # If prefix is not specified, then assume that the timestamp column name
  # is of the form "<prefix>_ts", and find that substring in the column name.
  prefix = tsColName.partition ("_ts")[0] if (prefixStr == "") else prefixStr

  # Use the prefix to name the new columns. Use built-in Spark datetime functions
  # to extract different components like Date, Time, Month etc.
  tsCol = col(tsColName)
  ndf = df.withColumn (prefix + "_date", tsCol.cast("date")) \
      .withColumn (prefix + "_time", date_format(tsCol, "H:m:s")) \
      .withColumn (prefix + "_month", month(tsCol)) \
      .withColumn (prefix + "_day", dayofmonth(tsCol)) \
      .withColumn (prefix + "_hour", hour(tsCol)) \
      .withColumn (prefix + "_min", minute(tsCol))
  return ndf

#-------------------------------------------
# 'colName' is a Struct field and 'schema' is the schema of 
# that Struct field sub-fields ie. all the sub-fields within that 
# Struct field. Return a list of Column objects of all those sub-fields.
#-------------------------------------------
def ExpandStruct (df, colName, schema):
  # List of sub-field names with the schema for the Struct field
  names = schema.fieldNames()
  print(f"========== Schema ========== : {names}" )

  # Go through the list of sub-fields in the schema and print their
  # names and data types
  list(map(lambda s: print ("+++", s.name, s.dataType), schema))

  # Take each sub-field name and get its Column object.
  # df[colName] gets the Column object for the Struct field
  # .getItem() gets the Column object for the sub-field inside the Struct
  # .alias() renames the sub-field Column object
  namefn = lambda name : df[colName].getItem(name).alias(name)
  colList = list(map(namefn, names))

  # Returnt the list of Column objects
  return colList

#-------------------------------------------
# Similar to the ExpandStruct function above, but for Map fields. The Map
# field contains sub-fields for the key and the value. We use this function
# when the value sub-field is a Struct rather than a simple field. Return
# a list of Column objects of the key and the sub-fields within the value Struct.
# 'keyName' is not the Map field itself, but the key sub-field within that Map.
# 'schema' is the schema of the Map field.
#-------------------------------------------
def ExpandMap (df, keyName, schema):
  # Get the schema of the 'value' sub-field within the Map
  structSchema = schema.valueType
  # Then use that 'value' schema to get the list of Columns within the value Struct
  structList = ExpandStruct (df, "value", structSchema)

  # Get the Column object for the key field and prepend it to the list of value Columns.
  keyCol = df["key"].alias(keyName)
  colList = [keyCol] + structList

  return colList
