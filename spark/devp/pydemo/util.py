from functools import partial
import datetime;
import sys

from pyspark.sql import SparkSession
from pyspark.sql.types import *
from pyspark.sql.functions import *

#-------------------------------------------
#-------------------------------------------


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
