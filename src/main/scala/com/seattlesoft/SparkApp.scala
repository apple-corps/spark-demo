package com.seattlesoft
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.types.StructType
import org.apache.spark.sql.types.StructField
import org.apache.spark.sql.types.StringType
import org.apache.spark.sql.functions.explode
import org.apache.spark.sql.functions.col
import org.apache.spark.sql.functions.stddev_pop
import org.apache.spark.sql.functions.stddev
import org.apache.spark.sql.functions.mean
import org.apache.spark.sql.Row
import org.apache.spark.sql.Column
import org.apache.spark.sql.types.FloatType

  
object SparkApp {
  def main(args: Array[String]){
    val spark = SparkSession.builder().master("local")
      // Must provide aws profile with access to s3 path
      .config("fs.s3a.aws.credentials.provider","com.amazonaws.auth.profile.ProfileCredentialsProvider")
      .appName("spark session").getOrCreate

    // Must provide valid S3 path which aws-profile has access to
    val s3Path = "s3a://rearc-demo-bucket/datausa.json"
    val dataUSADF = spark.read.option("multiline", true).json(s3Path)
    spark.sparkContext.hadoopConfiguration.set("fs.s3a.aws.credentials.provider","com.amazonaws.auth.profile.ProfileCredentialsProvider")
    
    //val rdd = spark.sparkContext.textFile(s3Path)
    val rdd = spark.sparkContext.textFile("s3a://rearc-demo-bucket/pub/time.series/pr/pr.data.0.Current").map(line=>line.split("\\s+"))
    val headerColumns = rdd.first()
    
    // create the schema from a string, splitting by delimiter
    val headerSchema = StructType(headerColumns.map(fieldName => StructField(fieldName, StringType, true)))
    headerSchema.printTreeString()
    // create a row from a string, splitting by delimiter
    
    val data = rdd
        .mapPartitionsWithIndex((index, it) => if (index == 0) it.drop(1) else it, true) // skip header
        .map(arr => 
          if (arr.length == 5) Row(arr(0), arr(1), arr(2), arr(3), arr(4))
          else  Row(arr(0), arr(1), arr(2), arr(3), null)
          )
    
    
    val blsDataFrame = spark.createDataFrame(data, headerSchema).withColumn("valueF",col("value").cast(FloatType)).alias("blsDF")
    //blsDataFrame.show()
    //dataUSADF.show()
    //dataUSADF.printSchema()
    import spark.implicits._
    val dataUSAexploded = dataUSADF.select(explode(col("data"))).select("col.*")//,explode(col("source")))
    dataUSAexploded.printSchema()
    System.out.println(s"count: ${dataUSAexploded.count()}")
    dataUSAexploded.take(5).foreach(println(_))

    val popQueryUSA = dataUSAexploded.select($"Population").where($"ID Nation" === "01000US" and $"Year".isin("2013","2014","2015","2016","2017","2018"))
    val populationStdDev = popQueryUSA.agg(stddev_pop($"Population"))
    populationStdDev.show()
    populationStdDev.take(3).foreach(println(_))
    System.out.println(s"count: ${populationStdDev.count()}")
    
    val populationMean = popQueryUSA.agg(mean($"Population"))
    populationMean.show()
    populationMean.take(3).foreach(println(_))
    System.out.println(s"count: ${populationMean.count()}")

    blsDataFrame.printSchema()
    val report = blsDataFrame.groupBy($"series_id").max("valueF").alias("left").join(blsDataFrame, $"left.series_id" === $"blsDF.series_id" && $"left.max(valueF)" === $"blsDF.valueF").select($"left.series_id",$"year",$"value", $"valueF")
    report.show()

    dataUSAexploded.printSchema()

    dataUSAexploded.show()
    blsDataFrame.printSchema()
    
    // series_id, year, period, value, population
    val right = dataUSAexploded.select($"Population", $"ID Year").where($"ID Nation" === "01000US")
    val step3report = blsDataFrame.select($"series_id",$"year",$"period",$"value").where($"series_id"==="PRS30006032").join(right, $"year" === $"ID Year").select($"series_id",$"year",$"period",$"value",$"Population")
    step3report.show()
    
    //dataUSAexploded.join(blsDataFrame,"series_id")

    //blsDataFrame.select("*").where($"series_id" === "PRS30006032").show()
    //dataUSAexploded.select($"Population").where($"ID Nation" === "01000US" and $"Year".
    
    
    //popQueryUSA.take(10).foreach(println(_))
    //System.out.println(s"data USA DF : ${dataUSADF.count()} ")
    //val rdd = spark.sparkContext.textFile("s3a://rearc-demo-bucket/compact.json")
    //
    //rdd.cache()


  }
}
