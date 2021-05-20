from functools import partial
import datetime
import sys

from pyspark.sql import SparkSession
from pyspark.sql.types import *
from pyspark.sql.functions import *
from pyspark.sql.window import Window

import util
import channel
import show
import session
import action
import ad
import user
import device_loc

#-------------------------------------------
# This is a temporary convenience function only

# Save the showDemographic dataframe as an intermediate result to a Kafka topic
# as a way to save time, so we can simply read those results for subsequent processing
# instead of running all the processing from scratch each time
#-------------------------------------------
def saveShowDemographicKafka (showDemographicDf, dataDir):
  brokers = "kafka.kd-confluent.svc.cluster.local:9071"
  topic = "testShowOut1"
  util.writeKafkaJson(brokers, topic, showDemographicDf, None, dataDir + "/checkpoint_show")

#-------------------------------------------
# Save our output Ad Demographic data to Kafka
#-------------------------------------------
def saveAdDemographicKafka (adDemographicDf, dataDir):
  brokers = "kafka.kd-confluent.svc.cluster.local:9071"
  topic = "testAdOut1"
  util.writeKafkaJson(brokers, topic, adDemographicDf, None, dataDir + "/checkpoint_ad")

#-------------------------------------------
# This is a temporary convenience function only
# Load the previously saved intermediate data
#-------------------------------------------
def loadShowDemographicKafka ():
  # {"program_id":13,"channel_id":57,"program":"Living Planet","channel":"BBC","user_id":46,"device_id":17,"name":"vihaan","age":18,"gender":"M","start_ts":"2021-02-01T09:20:16.000Z","end_ts":"2021-02-01T09:36:56.000Z"}    
  schema = StructType() \
          .add("program_id", IntegerType()) \
          .add("channel_id", IntegerType()) \
          .add("program", StringType()) \
          .add("channel", StringType()) \
          .add("user_id", IntegerType()) \
          .add("device_id", IntegerType()) \
          .add("name", StringType()) \
          .add("age", IntegerType()) \
          .add("gender", StringType()) \
          .add("start_ts", StringType()) \
          .add("end_ts", StringType())

  brokers = "kafka.kd-confluent.svc.cluster.local:9071"
  topic = "testShowOut1"
  showDemographicDf = util.readKafkaJson(spark, brokers, topic, schema, offset=36)
  return showDemographicDf

#-------------------------------------------
# Read and Write to a Kafka JSON topic for testing Kafka Integration
#-------------------------------------------
def doKafkaTest(spark, dataDir):
  # {"id":1,"firstname":"James ","middlename":"","lastname":"Smith","dob_year":2018,"dob_month":1,"gender":"M","salary":3000}
  schema = StructType() \
          .add("id", IntegerType()) \
          .add("firstname", StringType()) \
          .add("middlename", StringType()) \
          .add("lastname", StringType()) \
          .add("dob_year", IntegerType()) \
          .add("dob_month", IntegerType()) \
          .add("gender", StringType()) \
          .add("salary", IntegerType())

  brokers = "kafka.kd-confluent.svc.cluster.local:9071"
  topic = "json_spark"

  # +---+---------+----------+--------+--------+---------+------+------+---------+--------------------+
  # | id|firstname|middlename|lastname|dob_year|dob_month|gender|salary|msgoffset|             msgtime|
  # +---+---------+----------+--------+--------+---------+------+------+---------+--------------------+
  # |  1|   James |          |   Smith|    2018|        1|     M|  3000|        0|2021-01-22 05:53:...|
  # |  2| Michael |      Rose|        |    2010|        3|     M|  4000|        1|2021-01-22 05:53:...|
  # |  3|  Robert |          |Williams|    2010|        3|     M|  4000|        2|2021-01-22 07:32:...|
  # +---+---------+----------+--------+--------+---------+------+------+---------+--------------------+

  kdf = util.readKafkaJson(spark, brokers, topic, schema, offset=0)
  util.showStream(kdf)
  util.writeKafkaJson(brokers, topic, kdf, "id", dataDir + "/checkpoint_kafka")

#-------------------------------------------
# Create a Spark Session and process all the data sources
#-------------------------------------------
def main(spark, dataDir, fromKafka=False, toKafka=False):
  sessionDf = session.doSession(spark, dataDir, brokers, sessionTopic, offset=0, fromKafka=fromKafka)
  channelDf = channel.doChannel(spark)

  # Process Shows and Ads
  showDf = show.doShow(channelDf)
  adDf = ad.doAd(spark, dataDir, brokers, adTopic, offset=0, fromKafka=fromKafka)

  # Get user demographics and device locations
  userDf = user.doUser(spark, dataDir, brokers, userTopic, offset=0, fromKafka=fromKafka)
  deviceLocDf = device_loc.doDeviceLoc(spark, dataDir, brokers, deviceLocTopic, offset=10, fromKafka=fromKafka)

  # Get Show by User Sessions, enriched with Demographic and Device Location
  showDemographicLocDf = show.doShowDemographicLoc(showDf, sessionDf, userDf, deviceLocDf)
  util.showStream(showDemographicLocDf)

  # Get Ad by User Sessions, enriched with Demographic and Device Location
  adDemographicLocDf = ad.doAdDemographicLoc(adDf, sessionDf, userDf, deviceLocDf)
  util.showStream(adDemographicLocDf)

  if (toKafka):
    util.writeKafkaJson(brokers, showOutTopic, showDemographicLocDf, None, dataDir + "/checkpoint_show")
    util.writeKafkaJson(brokers, adOutTopic, adDemographicLocDf, None, dataDir + "/checkpoint_ad")

  # Obsolete
  if (False):
    # Save intermediate state to Kafka as a shortcut for use by downstream processing
    # saveShowDemographicKafka (showDemographicDf, dataDir)
    # saveAdDemographicKafka (adDemographicDf, dataDir)
    #showDemographicDf = loadShowDemographicKafka()
    #util.showStream(showDemographicDf)
    pass

spark = SparkSession.builder.appName("KD Demo").getOrCreate()
# Turn off INFO and DEBUG logging
spark.sparkContext.setLogLevel("ERROR")
dataDir = sys.argv[1]

# Parameters for Kafka Connect Datasources
#doKafkaTest(spark, dataDir) # This is just a test that reading/writing some data to Kafka works
brokers = "kafka.kd-confluent.svc.cluster.local:9071"
sessionTopic = "json_topic"
userTopic = "kdserver1.kddb.user"
adTopic = "testad1"
deviceLocTopic = "testloc1"
actionTopic = "testaction1"

showOutTopic = "testShowOut1"
adOutTopic = "testAdOut1"

main(spark, dataDir, fromKafka=True, toKafka=True)

spark.streams.awaitAnyTermination(900000)
print("========== DONE ==========" )


# --- Notes
# Session stateful should be event time based (not processing time based)
# Reorder the Action data so that it is sorted by time. That will ensure even Sessions are sorted by time.
# Reduce the frequency of Location updates to 30 seconds in the Custom connector config??
# If there are multiple locations for a single Show, take the average location