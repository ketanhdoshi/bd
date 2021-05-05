name := "Kafka Integration"
version := "1.0"
scalaVersion := "2.12.12"

val sparkVersion = "3.1.1"

// Add different Maven repository for Confluent libraries
// Needed for Avro and Schema Registry integration
resolvers += "confluent" at "https://packages.confluent.io/maven/"

libraryDependencies ++= Seq(
  "org.apache.spark" %% "spark-sql" % sparkVersion,

  // For Kafka integration
  "org.apache.spark" %% "spark-sql-kafka-0-10" % sparkVersion,

  // For Avro and Schema Registry integration in Confluent format
  "za.co.absa" %% "abris" % "4.0.1"
)