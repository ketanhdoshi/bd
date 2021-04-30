package com.ketan

import org.apache.spark.sql.SparkSession
import org.apache.spark.sql._
import org.apache.spark.sql.types._
import org.apache.spark.sql.functions._
import org.apache.spark.sql.streaming._

// Stateful Processing - input action record
case class DeviceAction(user_id: Int, device_id: Int, channel_id: Int, action:Int, action_ts:java.sql.Timestamp)
// Stateful Processing - state data record
case class DeviceSession(user_id: Int, device_id: Int, channel_id: Int, var start:java.sql.Timestamp, var end:java.sql.Timestamp)

object StatefulSession {
  //-------------------------------------------
  // Stateful Processing - Update Function for flatMapGroups..
  // It is similar to the update function for mapGroups, except it returns multiple output records
  // 
  // It takes the grouping key (ie. user_id), the input records (ie. user events) for the group within
  // this micro-batch and the previous state (ie. user session) and takes two actions:
  //    1. Updates the state and saves it for the next micro-batch
  //    2. Returns an iterator of multiple output records for the whole group
  //
  // NB: Right now the output record type is the same as the state record type, so we're updating
  // state and returning the output using the same data variable
  //-------------------------------------------
  def flatmapSession(
    user_device: String,
    inputs: Iterator[DeviceAction],
    oldState: GroupState[DeviceSession]): Iterator[DeviceSession] = {

      val keys: Array[String] = user_device.split("\\.")
      val user_id: Int = keys(0).toInt
      val device_id: Int = keys(1).toInt

      import scala.collection.mutable.ListBuffer
      var myListBuf = new ListBuffer[DeviceSession]()

      // Timeout for this user, due to no activity (so no input event records)
      if (oldState.hasTimedOut) {
        // Create an expired session with saved values from the old state
        val oldStateSession = oldState.get
        val expiredSession = DeviceSession (user_id, device_id, oldStateSession.channel_id, 
                                  oldStateSession.start, oldStateSession.end)        
        // Add the expired session to our list
        myListBuf += expiredSession

        // Since we're closing the user's session, remove the state for this user
        oldState.remove()
      } else {
          // No timeout, so do normal processing of input events

          // oldState is a handle (ie. a GroupState object) to the previous state. Use it to get the data 
          // for the previous state if it exists, else create a new data object for the state, initialised with
          // empty data
          var currentSession:DeviceSession =  if (oldState.exists) oldState.get 
                                              else null

          // Go through each input record
          for (input <- inputs) {      
            // Ignore actions without a timestamp
            if (!Option(input.action_ts).isEmpty) {
              if (input.action == 1) {
                // Session started
                // If currentSession already exists it is discarded
                currentSession = DeviceSession (user_id, device_id, input.channel_id,
                                  input.action_ts, new java.sql.Timestamp(0L))
              }
              else if (input.action == 2) {
                // Session ended
                // If currentSession didn't exist, ignore the event
                // We also ignore the channel_id in the input and don't 
                // check that (currentSession.channel_id == input.channel_id)
                if (currentSession != null) {
                    currentSession.end = input.action_ts
                    myListBuf += currentSession
                    currentSession = null
                }
              }
              else if (input.action == 3) {
                // Old session ended and new session started
                if (currentSession != null) {
                  currentSession.end = input.action_ts
                  myListBuf += currentSession
                  currentSession = null
                }

                currentSession = DeviceSession (user_id, device_id, input.channel_id,
                                  input.action_ts, new java.sql.Timestamp(0L))
              }
            }
          }
            
          // Re-compute a new timeout value for this user
          oldState.setTimeoutDuration("30 seconds")

          // Save the updated state
          if (currentSession == null)
            if (oldState.exists)
              oldState.remove()
          else
            if (oldState.exists)
              oldState.update(currentSession)
      }
    
    val myList = myListBuf.toList

    // Return an iterator for the output records
    return myList.iterator
  }
}

object Session {
    def foo(){
        println("Hello Foo!")
    }    

    def getData(
        spark: SparkSession,
        brokers: String,
        topic: String,
        dataDir:String
    ) : DataFrame = {    

        val actionSchema = new StructType()
            .add("user", DataTypes.StringType)
            .add("user_id", DataTypes.IntegerType)
            .add("channel_id", DataTypes.IntegerType)
            .add("device_id", DataTypes.IntegerType)
            .add("action", DataTypes.IntegerType)
            .add("action_ts", DataTypes.TimestampType)

        if (brokers != null) {
          val actionDf = Util.readKafkaJson (spark, brokers, topic, actionSchema, offset=9);
          return actionDf;
        } else {
          val inputPath = dataDir + "action*.json"
          val actionDf = spark
              .readStream                       // `readStream` instead of `read` for creating streaming DataFrame
              .schema(actionSchema)             // Set the schema of the JSON data
              .option("maxFilesPerTrigger", 1)  // Treat a sequence of files as a stream by picking one file at a time
              .json(inputPath)
          return actionDf;
        }
    }

    def doStateful (spark: SparkSession, actionDf: DataFrame): DataFrame = {
        import spark.implicits._

        val dfState = actionDf.select ("user_id", "device_id", "channel_id", "action", "action_ts").as[DeviceAction]
            //.withWatermark("event_ts", "30 minutes")  // required for Event Time Timeouts
            .groupByKey((x:DeviceAction) => x.user_id.toString + "." + x.device_id.toString) // group based on user, device
            .flatMapGroupsWithState(OutputMode.Append,GroupStateTimeout.ProcessingTimeTimeout)(StatefulSession.flatmapSession)
            .toDF // cast the Dataset to a Dataframe

        return dfState
    }
}