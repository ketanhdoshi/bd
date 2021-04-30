name := "Sentiment Analysis"
version := "1.0"
scalaVersion := "2.12.12"

val sparkVersion = "3.1.1"

libraryDependencies ++= Seq(
  "org.apache.spark" %% "spark-sql" % sparkVersion,
  "org.apache.spark" %% "spark-mllib" % sparkVersion
)