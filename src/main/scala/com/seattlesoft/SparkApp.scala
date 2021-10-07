package com.seattlesoft
import org.apache.spark.sql.SparkSession

object SparkApp {
  def main(args: Array[String]){
    val spark = SparkSession.builder().master("local")
      //.config("spark.jars.repositories", "https://repository.cloudera.com/artifactory/cloudera-repos/")
      //.config("spark.jars.packages", "org.apache.spark:spark-hadoop-cloud_2.12:3.1.1.3.1.7270.0-253")
      .appName("spark session").getOrCreate

    val jsonDF = spark.read.json("s3a://path-to-bucket/compact.json")
    val csvDF = spark.read.format("csv").load("s3a://path-to-bucket/some.csv")
    jsonDF.show()
    csvDF.show()
  }
}
