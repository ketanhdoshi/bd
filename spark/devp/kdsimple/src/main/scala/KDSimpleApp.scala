package com.example
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions._

object KDSimpleApp {
  def main(args: Array[String]) {
    val SPARK_HOME = sys.env("SPARK_HOME")
    val logFile = s"${SPARK_HOME}/README.md"
    val spark = SparkSession.builder
      .appName("My First Scala")
      .getOrCreate()
    val logData = spark.read.textFile(logFile).cache()
    val totalLines = logData.count()
    val firstLine = logData.first()
    println(s"========= Total Lines : $totalLines, First line : $firstLine ==========")
    val filterLines = logData.filter(line => line.contains("Spark")).count()
    println(s"========= Spark Lines : $filterLines ===============")
    val numAs = logData.filter(line => line.contains("a")).count()
    val numBs = logData.filter(line => line.contains("b")).count()
    println(s"========= Lines with a: $numAs, Lines with b: $numBs =========")

    // Read a CSV file
    val filePath = args(0)
    val csvData = spark.read
         .option("header", true)
         .option("inferSchema", true)
         .option("timestampFormat", "dd/MM/yyyy")
         .csv(filePath)
    val csvLines = csvData.count()
    println(s"========== CSV Lines : $csvLines ==========" )

    csvData.printSchema

    val orderedData = csvData.orderBy(desc("Date of Payment"))
    orderedData.show(5)

    println(s"========== DONE ==========" )

    spark.stop()
  }
}
