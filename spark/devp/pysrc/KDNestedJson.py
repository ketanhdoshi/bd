from functools import partial
import datetime;
import sys

from pyspark.sql import SparkSession
from pyspark.sql.types import *
from pyspark.sql.functions import *

#-------------------------------------------
# Several examples of processing nested JSON data. The data is either
# hard-coded as strings with the program, or read from a file. The overall
# approach is to read the JSON data into a Dataframe, using a defined schema.
# We then use Spark Dataframe operations to flatten out the data - repeating
# sub-items like Arrays and Maps are exploded into separate rows, and nested
# sub-fields are expanded into separate columns. We can then use Spark Dataframe
# operations as well as Spark SQL to process the data by doing aggregates,
# filtering rows and so on.
#-------------------------------------------


#-------------------------------------------
# Read a JSON string into a DataFrame based on a provided schema
#-------------------------------------------
def jsonToDataFrame(jsonStr, schema=None):
  print ("+++++++++++++++", jsonStr)
  sc = spark.sparkContext
  df = spark.read.schema(schema).json(sc.parallelize([jsonStr]))
  return df

#-------------------------------------------
# Taken from the official Spark examples. Read a simple JSON file into a Dataframe
# and run some Spark SQL queries
#-------------------------------------------
def doPeople():
  # A JSON dataset is pointed to by path.
  # The path can be either a single text file or a directory storing text files
  path = sys.argv[1] + "/pysrc/person.json"
  peopleDF = spark.read.json(path)

  # The inferred schema can be visualized using the printSchema() method
  peopleDF.printSchema()
  # root
  #  |-- age: long (nullable = true)
  #  |-- name: string (nullable = true)

  # Creates a temporary view using the DataFrame so that we can run Spark SQL queries.
  peopleDF.createOrReplaceTempView("people")

  # SQL statements can be run by using the sql methods provided by spark
  teenagerNamesDF = spark.sql("SELECT name FROM people WHERE age BETWEEN 13 AND 19")
  teenagerNamesDF.show()

#-------------------------------------------
# Simple example of reading a hard-coded JSON string with a couple of fields
#-------------------------------------------
def doSimple():
  # Define the schema as a structure with a couple of fields
  simple = StructType() \
    .add("id", LongType()) \
    .add("jstring", StringType()) 

  # Read a hardcoded JSON string into a Dataframe based on the schema. The JSON has enclosing
  # square brackets (ie. []) since it has two rows.
  simpleDF = jsonToDataFrame( """
    [{
      "id": 0,
      "jstring": "shit great"
    },
    {
      "id": 1,
      "jstring": "hello"   
    }]
    """, simple)

  # Output the schema, the records in the Dataframe and the count
  simpleDF.printSchema()
  simpleDF.show(2)
  # Count the number of records
  numSimple = simpleDF.count()
  print("=============Simple count %i" % (numSimple))
  
#-------------------------------------------
# Nested hard-coded IoT JSON string with a Map and nested structs. Explode the
# sub-items into their own rows.
#-------------------------------------------
def doIot():
  # Define the schema. The 'source' field is a Map which is like a dictionary 
  # that contains multiple items. Each item has a String key and a value which
  # is another Struct with multiple sub-fields of different types. One of the 
  # sub-fields, 'geo' is itself another Struct with its own sub-fields.
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

  # Read the JSON string into a Dataframe using the schema. It contains one record.
  # The 'source' Map field contains four sub-items with keys 'sensor-igauge', 'sensor-ipad' 
  # and so on. Each key has its corresponding value Struct.
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

  # Print the schema and the count of records
  dataDF.printSchema()
  tot = dataDF.count()
  print("============= Total: %i" % (tot))

  # The explode() function creates a new row for each element in the given Map column.
  # We select the single row of the Dataframe and explode the 'source' Map column into four
  # separate rows. A Map field gets exploded into two columns called 'key' and 'value'.
  explodedDF = dataDF.select(col("dc_id"), explode(col("source")))
  explodedDF.printSchema()
  explodedDF.show(4)

  # Now take the exploded Dataframe and expand out the sub-fields of the Map into
  # separate columns. The 'key' column is renamed to 'deviceType' and the 'value'
  # column is flattened by expanding all its sub-fields into separate columns, including 
  # the nested 'geo' sub-fields
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

  # Use 'foreach' to loop through each of the four rows and apply this function to
  # print out the 'ip' field.
  def fn(device):
    # NB: On a cluster this print doesn't do anything as it prints to the stdout
    # on the Executor nodes, but not on the driver
    print("----------------------------- ", device.ip)
  notifydevicesDF.foreach(fn)

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

#-------------------------------------------
# This is really the most important example. It takes a fairly complicated nested JSON and
# dissects it step-by-step. It flattens out each nested sub-field and tries out different
# Dataframe and SQL operations on them.
#-------------------------------------------
def doNested():
  # Define the schema. It is a Struct field that contains other nested structures:
  #   event_time - Long field that contains an epoch datetime in milliseconds
  #   start_time_str - String field that contains a formatted datetime value
  #   readings - An Array of Integers
  #   readings_array - An Array of Arrays of Integers
  #   struct_array - An Array of Structs
  #   source - Map of Structs, that contains 'temp' which is an Array of Integers
  #   geo - Struct of Doubles
  nested = StructType() \
    .add("dc_id", StringType()) \
    .add("event_time", LongType()) \
    .add("start_time_str", StringType()) \
    .add("readings", ArrayType(IntegerType())) \
    .add("readings_array", ArrayType(ArrayType(IntegerType()))) \
    .add("struct_array", ArrayType( \
        StructType() \
            .add("s", IntegerType()) \
            .add("t", IntegerType()) \
    )) \
    .add("source", \
        MapType( \
            StringType(), \
            StructType() \
              .add("id", IntegerType()) \
              .add("ip", StringType()) \
              .add("temp", ArrayType(IntegerType())) \
        ) \
    ) \
    .add("geo", \
        StructType() \
            .add("lat", DoubleType()) \
            .add("long", DoubleType()) \
    )

  # Nested JSON with two rows. Each row contains:
  #  'struct_array' with two items each
  #  'source' with a Map with two items each
  nestedDF = jsonToDataFrame( """[
    {
      "dc_id": "666",
      "event_time": 1487715775521,
      "start_time_str": "2019-06-24 12:01:19.000",
      "readings": [2, 5, 7],
      "readings_array": [[2, 5], [9, 3]],
      "struct_array": [
        {"s": 12, "t": 19},
        {"s": 17, "t": 28}
      ],
      "source": {
        "sensor-igauge": {
          "id": 10,
          "ip": "68.28.91.22",
          "temp":[35, 2, 41]
        },
        "sensor-ipad": {
            "id": 13,
            "ip": "67.185.72.1",
            "temp": [6, 19, 4, 51]
        }
      },
      "geo": {"lat": 28.3939, "long": 16.39101}
    },
    {
      "dc_id": "777",
      "event_time": 1487715772911,
      "start_time_str": "2019-08-21 09:01:45.000",
      "readings": [2, 0, 8, 7],
      "readings_array": [[7, 16], [2, 8], [0, 6, 4]],
      "struct_array": [
        {"s": 54, "t": 37},
        {"s": 42, "t": 83}
      ],
      "source": {
        "sensor-igauge": {
          "id": 18,
          "ip": "68.29.12.7",
          "temp": [84, 2, 27, 55]
        },
        "sensor-ipad": {
            "id": 22,
            "ip": "67.142.71.5",
            "temp": [67, 66, 3, 9]
        }
      },
      "geo": {"lat": 42.3833, "long": 79.1638}
    }
  ]""", nested)
  
  nestedDF.printSchema()

  # Extract the two date time fields into Timestamp fields using .transform(). Could have also used .map() instead.
  # Extract 'evt_ts' from 'event_time' and then split out all its components like date, month etc
  # Extract 'start_ts' from 'start_time_str' using the given format.
  # +-----+-------------+--------------------+------------+--------------------+--------------------+--------------------+-------------------+--------------------+--------+----------+--------+---------+-------+--------+-------+
  # |dc_id|   event_time|      start_time_str|    readings|      readings_array|        struct_array|              source|                geo|              evt_ts|start_ts|  evt_date|evt_time|evt_month|evt_day|evt_hour|evt_min|
  # +-----+-------------+--------------------+------------+--------------------+--------------------+--------------------+-------------------+--------------------+--------+----------+--------+---------+-------+--------+-------+
  # |  666|1487715775521|2019-06-24 12:01:...|   [2, 5, 7]|    [[2, 5], [9, 3]]|[[12, 19], [17, 28]]|[sensor-igauge ->...|[28.3939, 16.39101]|2017-02-21 22:22:...|    null|2017-02-21|22:22:55|        2|     21|      22|     22|
  # |  777|1487715772911|2019-08-21 09:01:...|[2, 0, 8, 7]|[[7, 16], [2, 8],...|[[54, 37], [42, 83]]|[sensor-igauge ->...| [42.3833, 79.1638]|2017-02-21 22:22:...|    null|2017-02-21|22:22:52|        2|     21|      22|     22|
  # +-----+-------------+--------------------+------------+--------------------+--------------------+--------------------+-------------------+--------------------+--------+----------+--------+---------+-------+--------+-------+

  fmt = "MM-dd-yyyy HH:mm:ss.SSS"
  timeDf = nestedDF \
              .transform (partial(LongToTimestamp, longColName="event_time", tsColName="evt_ts")) \
              .transform (partial(StrToTimestamp, strColName="start_time_str", tsColName="start_ts", fmt=fmt)) \
              .transform (partial(ExpandTimestamp, tsColName="evt_ts"))
  timeDf.printSchema
  timeDf.show()

  # Apply Spark SQL operations on the two array fields.
  # Use SQL aggregate to total the numbers in the 'readings' array
  # Use SQL transform to add 10 to each value in the 'readings_array' array
  # +-----+------------+---+--------------------+
  # |dc_id|    readings|sum|  new_readings_array|
  # +-----+------------+---+--------------------+
  # |  666|   [2, 5, 7]| 14|[[12, 15], [19, 13]]|
  # |  777|[2, 0, 8, 7]| 17|[[17, 26], [12, 1...|
  # +-----+------------+---+--------------------+
  arrayDf = nestedDF.select("dc_id", 
                        "readings",
                        "readings_array")
  arrayDf.createOrReplaceTempView("array_table")
  sqlArraySum = """select dc_id, readings, 
                      aggregate(readings, 0, (acc, value) -> acc + value) as sum,
                      transform(readings_array, z -> transform(z, value -> value + 10)) as new_readings_array
                      from array_table"""
  arraySumDf = spark.sql(sqlArraySum)
  arraySumDf.show()

  # Apply Spark SQL operations on the array of structs
  # Use SQL transform to multiply the two sub-fields in the struct, for each array item
  # Use SQL exists to compute a boolean value if any array item has two sub-fields that add up greater than some value
  # +-----+--------------------+------------+-------------+
  # |dc_id|        struct_array| mult_struct|exists_struct|
  # +-----+--------------------+------------+-------------+
  # |  666|[[12, 19], [17, 28]]|  [228, 476]|         true|
  # |  777|[[54, 37], [42, 83]]|[1998, 3486]|         true|
  # +-----+--------------------+------------+-------------+
  structArrayDf = nestedDF.select("dc_id",
                              "struct_array")
  structArrayDf.createOrReplaceTempView("struct_array_table")
  sqlStructArray = """select *, 
                        transform (struct_array, z -> z.s * z.t) as mult_struct,
                        exists (struct_array, z -> z.s + z.t > 10) as exists_struct
                        from struct_array_table"""
  structArrayCalcDf = spark.sql(sqlStructArray)
  structArrayCalcDf.show()

  # +-----+-------+--------+-------+-------+
  # |dc_id|    lat|    long| geolat|geolong|
  # +-----+-------+--------+-------+-------+
  # |  666|28.3939|16.39101|2839.39| 1639.1|
  # |  777|42.3833| 79.1638|4238.33|7916.38|
  # +-----+-------+--------+-------+-------+
  # Flatten the 'geo' struct by extract its sub-fields and applying a computation to them.
  geoSchema = nested["geo"].dataType
  geoList = ExpandStruct (nestedDF, "geo", geoSchema)    
  # Multiply lat and long by 100 and round to 2 decimals
  geoFunc = lambda geoCol: round (geoCol * 100, 2)
  geoDf = nestedDF.select("dc_id", \
                          geoList[0], \
                          geoList[1]) \
                          .withColumn ("geolat", geoFunc (col("lat"))) \
                          .withColumn ("geolong", geoFunc (col("long")))
  geoDf.show()

  # +----+-------------+---+-----------+---------------+------------+
  # |dcId|   deviceType| id|         ip|           temp|   filt_temp|
  # +----+-------------+---+-----------+---------------+------------+
  # | 666|sensor-igauge| 10|68.28.91.22|    [35, 2, 41]|    [35, 41]|
  # | 666|  sensor-ipad| 13|67.185.72.1| [6, 19, 4, 51]| [6, 19, 51]|
  # | 777|sensor-igauge| 18| 68.29.12.7|[84, 2, 27, 55]|[84, 27, 55]|
  # | 777|  sensor-ipad| 22|67.142.71.5| [67, 66, 3, 9]| [67, 66, 9]|
  # +----+-------------+---+-----------+---------------+------------+
  # Explode 'source', which is a nested map of structs, into separate rows
  explodedDF = nestedDF.select("dc_id", explode("source"))
  # Expand out all the sub-fields of the nested 'source' struct into separate columns.
  # First, get all the sub-fields
  sourceSchema = nested["source"].dataType
  sourceList = ExpandMap (explodedDF, "deviceType", sourceSchema)
  # Then select all those fields into separate columns to flatten the Dataframe out
  dcCol = explodedDF["dc_id"].alias("dcId")
  colList = [dcCol] + sourceList
  flatDf = explodedDF.select(*colList)
  # Use Spark SQL on the 'temp' array of numbers to filter only those temperature values that are 
  # greater than some amount
  flatDf.createOrReplaceTempView("flat_table")
  sqlArrayFilter = """select *, 
                      filter (temp, t -> t > 5) as filt_temp
                      from flat_table"""
  arrayFilterDf = spark.sql(sqlArrayFilter)
  # Finally, print out the flattened Dataframe
  tot = arrayFilterDf.count()
  print("============= Total: %i" % (tot))
  arrayFilterDf.show()

#-------------------------------------------
# Create a Spark Session and run several JSON processing examples
#-------------------------------------------
spark = SparkSession.builder.appName("Nested Json").getOrCreate()
doPeople()
doSimple()
doIot()
doNested()
print("========== DONE ==========" )
