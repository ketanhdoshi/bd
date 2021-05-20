package com.ketan

import scala.collection.mutable.ListBuffer

import org.apache.spark.sql.SparkSession
import org.apache.spark.sql._
import org.apache.spark.sql.types._
import org.apache.spark.sql.functions._
import org.apache.spark.sql.streaming._

// Stateful Processing - input action record
// This indicates an input action by a user on a particular device, along with the timestamp of the action.
// There are three possible actions - start watching a channel, stop watching a channel or switch channels
case class DeviceAction(user_id: Int, device_id: Int, channel_id: Int, action:Int, action_ts:java.sql.Timestamp)

// Stateful Processing - state data record
// This is the output session created from the input actions. It indicates a 'watching session' by a user
// on a particular device, watching a channel with start and stop times.
case class DeviceSession(user_id: Int, device_id: Int, channel_id: Int, var start:java.sql.Timestamp, var end:java.sql.Timestamp)

object StatefulSession {
  //-------------------------------------------
  // We received a Session Start action, so create a new active session and initialise it
  // with the session start time
  //-------------------------------------------
  def startSession(
    user_id: Int, 
    device_id: Int, 
    channel_id: Int, 
    start:java.sql.Timestamp
  ): Option[DeviceSession] = {

    val session = Some (DeviceSession (user_id, device_id, channel_id, start, new java.sql.Timestamp(0L)))
    return session
  }

  //-------------------------------------------
  // When we receive a Session End action, end the currently active session
  // if there is one. That completed session also gets added to the output list. 
  //-------------------------------------------
  def endSession(
    currentSession:Option[DeviceSession], 
    listBuf: ListBuffer[DeviceSession],
    end:java.sql.Timestamp
  ): Unit = {

    if (!currentSession.isEmpty) {
      val session:DeviceSession = currentSession.get
      session.end = end
      listBuf += session
    }
  }

  //-------------------------------------------
  // Stateful Processing - Update Function for flatMapGroups..
  // It is similar to the update function for mapGroups, except it returns multiple output records
  // 
  // It takes the grouping key (ie. user-device combo), the input records (ie. user actions) for the group within
  // this micro-batch and the previous state (ie. user session) and takes two actions:
  //    1. Updates the state and saves it for the next micro-batch
  //    2. Returns an iterator of multiple output records for the whole group
  //
  // NB: Right now the output record type is the same as the state record type, so we're updating
  // state and returning the output using the same data variable
  //
  // This function gets invoked at each micro-batch, for a particular user-device combination. The inputs
  // are the actions received during this micro-batch interval for this user-device. The outputs returned
  // are the completed user watching sessions.
  //-------------------------------------------
  def flatmapSession(
    user_device: String,
    inputs: Iterator[DeviceAction],
    oldState: GroupState[DeviceSession]): Iterator[DeviceSession] = {

      // oldState is a handle (ie. a GroupState object) to the previous state. The handle has
      // methods that we can use to check if the state actually exists (ie. is not empty) or if
      // we've received a Timeout event.

      val keys: Array[String] = user_device.split("\\.")
      val user_id: Int = keys(0).toInt
      val device_id: Int = keys(1).toInt

      var myListBuf = new ListBuffer[DeviceSession]()

      // Timeout for this user, due to no activity (so no input event records)
      if (oldState.hasTimedOut) {
        if (oldState.exists) {
          // Create an expired session with saved values from the old state
          val oldStateSession = oldState.get
          val expiredSession = DeviceSession (user_id, device_id, oldStateSession.channel_id, 
                                    oldStateSession.start, oldStateSession.end)        
          // Add the expired session to our list
          myListBuf += expiredSession
        }

        // Since we're closing the user's session, remove the state for this user
        oldState.remove()
      } else {
          // No timeout, so do normal processing of input events

          // Object for the current active session.
          // Get the saved session from the previous state if it exists (ie. is not empty), else set
          // it to None to indicate that there is no active session. We use a Scala Option for the
          // session because it can be either None or have a value.
          var currentSession:Option[DeviceSession] = if (oldState.exists) Some(oldState.get)
                                              else None

          // Go through each input record
          for (input <- inputs) {      
            // Ignore actions without a timestamp
            if (!Option(input.action_ts).isEmpty) {
              if (input.action == 1) {
                // Session started, so set the start time
                // If currentSession already exists it is discarded
                currentSession = startSession(user_id, device_id, input.channel_id, input.action_ts)
                // currentSession = Some(DeviceSession (user_id, device_id, input.channel_id,
                //                   input.action_ts, new java.sql.Timestamp(0L)))
              }
              else if (input.action == 2) {
                // Session ended, so set the end time and close it. The completed session gets
                // added to our output list.
                // If currentSession didn't exist, ignore the event
                // We also ignore the channel_id in the input and don't 
                // check that (currentSession.channel_id == input.channel_id)
                endSession(currentSession, myListBuf, input.action_ts)
                currentSession = None

               /*  if (!currentSession.isEmpty) {
                    val session:DeviceSession = currentSession.get
                    session.end = input.action_ts
                    myListBuf += session
                    currentSession = None
                } */
              }
              else if (input.action == 3) {
                // We switched channels so end the old session and start a new session. The completed
                // session gets added to our output list.
                endSession(currentSession, myListBuf, input.action_ts)

              /*   if (!currentSession.isEmpty) {
                  val session:DeviceSession = currentSession.get
                  session.end = input.action_ts
                  myListBuf += session
                  currentSession = None
                } */

                currentSession = startSession(user_id, device_id, input.channel_id, input.action_ts)
                // currentSession = Some(DeviceSession (user_id, device_id, input.channel_id,
                //                   input.action_ts, new java.sql.Timestamp(0L)))
              }
            }
          }
            
          // Re-compute a new timeout value for this user
          oldState.setTimeoutDuration("90 seconds")

          // If there is a current active session, save it by updating the state, so that
          // we can restore it at the next batch interval. If there is no active session
          // then clear out the state by removing any previously saved session.
          if (currentSession.isEmpty) {
            if (oldState.exists) {
              oldState.remove()
            }
          }             
          else {
            oldState.update(currentSession.get)
          }
      }
    
      // Return an iterator for the output records
      val myList = myListBuf.toList
      return myList.iterator
    }
}

//-------------------------------------------
// A user watches a TV channel on some device, and performs actions to start and stop
// watching. These actions are converted into sessions which track which channel the
// user watched, with start and stop times.
//-------------------------------------------
object Session {
    def foo(){
        println("Hello Foo!")
    }    

    //-------------------------------------------
    // Get the stream of user actions from Kafka
    //-------------------------------------------
    def getData(
        spark: SparkSession,
        brokers: String,
        topic: String,
        dataDir:String,
        offset: Integer = 0
    ) : DataFrame = {    

        // Define the schema for the user action stream
        val actionSchema = new StructType()
            .add("user", DataTypes.StringType)
            .add("user_id", DataTypes.IntegerType)
            .add("channel_id", DataTypes.IntegerType)
            .add("device_id", DataTypes.IntegerType)
            .add("action", DataTypes.IntegerType)
            .add("action_ts", DataTypes.TimestampType)

        if (brokers != null) {
          // Get streaming data from Kafka topic
          val actionDf = Util.readKafkaJson (spark, brokers, topic, actionSchema, offset);
          return actionDf;
        } else {
          // For testing only - get the streaming data from a set of JSON files
          val inputPath = dataDir + "action*.json"
          val actionDf = spark
              .readStream                       // `readStream` instead of `read` for creating streaming DataFrame
              .schema(actionSchema)             // Set the schema of the JSON data
              .option("maxFilesPerTrigger", 1)  // Treat a sequence of files as a stream by picking one file at a time
              .json(inputPath)
          return actionDf;
        }

        // {"user": "ketan", "user_id": 45, "channel_id": 57, "device_id": 14, "action": 1, "action_ts": 1612223787}
        // {"user": "ketan", "user_id": 45, "channel_id": 57, "device_id": 14, "action": 2, "action_ts": 1612223954}
        // {"user": "ketan", "user_id": 45, "channel_id": 57, "device_id": 15, "action": 1, "action_ts": 1612224091}
        // {"user": "ketan", "user_id": 45, "channel_id": 57, "device_id": 15, "action": 2, "action_ts": 1612224172}
        // {"user": "vihaan", "user_id": 46, "channel_id": 58, "device_id": 16, "action": 1, "action_ts": 1612223747}
        // {"user": "vihaan", "user_id": 46, "channel_id": 58, "device_id": 16, "action": 2, "action_ts": 1612223899}
        // {"user": "vihaan", "user_id": 46, "channel_id": 58, "device_id": 17, "action": 1, "action_ts": 1612224008}
        // {"user": "vihaan", "user_id": 46, "channel_id": 57, "device_id": 17, "action": 3, "action_ts": 1612224113}
        // {"user": "vihaan", "user_id": 46, "channel_id": 57, "device_id": 17, "action": 2, "action_ts": 1612224252}
    }

    //-------------------------------------------
    // Use Spark Stateful Processing to track user watching actions and derive completed user
    // watching sessions, with start and stop times.
    //-------------------------------------------
    def doStateful (spark: SparkSession, actionDf: DataFrame): DataFrame = {
        import spark.implicits._

        // +------+-------+----------+---------+------+-------------------+
        // |  user|user_id|channel_id|device_id|action|          action_ts|
        // +------+-------+----------+---------+------+-------------------+
        // | ketan|     45|        57|       14|     1|2021-02-01 23:56:27|
        // | ketan|     45|        57|       14|     2|2021-02-01 23:59:14|
        // | ketan|     45|        57|       15|     1|2021-02-02 00:01:31|
        // | ketan|     45|        57|       15|     2|2021-02-02 00:02:52|
        // |vihaan|     46|        58|       16|     1|2021-02-01 23:55:47|
        // |vihaan|     46|        58|       16|     2|2021-02-01 23:58:19|
        // |vihaan|     46|        58|       17|     1|2021-02-02 00:00:08|
        // |vihaan|     46|        57|       17|     3|2021-02-02 00:01:53|
        // |vihaan|     46|        57|       17|     2|2021-02-02 00:04:12|
        // +------+-------+----------+---------+------+-------------------+

        val dfState = actionDf.select ("user_id", "device_id", "channel_id", "action", "action_ts").as[DeviceAction]
            //.withWatermark("event_ts", "30 minutes")  // required for Event Time Timeouts
            .groupByKey((x:DeviceAction) => x.user_id.toString + "." + x.device_id.toString) // group based on user, device
            // Stateful processing to map from user actions to user sessions
            .flatMapGroupsWithState(OutputMode.Update,GroupStateTimeout.ProcessingTimeTimeout)(StatefulSession.flatmapSession)
            .toDF // cast the Dataset to a Dataframe

        // {"user_id":45,"device_id":14,"channel_id":57,"start":"2021-02-01T23:56:27.000Z","end":"2021-02-01T23:59:14.000Z"}
        // {"user_id":45,"device_id":15,"channel_id":57,"start":"2021-02-02T00:01:31.000Z","end":"2021-02-02T00:02:52.000Z"}
        // {"user_id":46,"device_id":16,"channel_id":58,"start":"2021-02-01T23:55:47.000Z","end":"2021-02-01T23:58:19.000Z"}
        // {"user_id":46,"device_id":17,"channel_id":58,"start":"2021-02-02T00:00:08.000Z","end":"2021-02-02T00:01:53.000Z"}
        // {"user_id":46,"device_id":17,"channel_id":57,"start":"2021-02-02T00:01:53.000Z","end":"2021-02-02T00:04:12.000Z"}

        return dfState
    }
}