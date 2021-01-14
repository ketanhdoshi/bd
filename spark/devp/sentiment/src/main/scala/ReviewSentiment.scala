package com.ketan
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions._
import org.apache.spark.ml._
import org.apache.spark.ml.feature._
import org.apache.spark.ml.classification._
import org.apache.spark.ml.evaluation._
import org.apache.spark.sql.functions.{ concat, lit }

object Sentiment {
  def main(args: Array[String]) {

    val spark: SparkSession = SparkSession.builder
      .appName("Sentiment Analysis")
      .getOrCreate()

    var file = args(0) + '/' + "reviews_Sports.json"

    import spark.implicits._

    // Read data from the file into a Dataset
    val df0 = spark.read.format("json")
      .option("inferSchema", "true")
      .load(file)

    // add column combining summary and review text, drop some others 
    val df = df0.withColumn("reviewTS", 
      concat($"summary", lit(" "), $"reviewText"))
      .drop("helpful")
      .drop("reviewerID")
      .drop("reviewerName")
      .drop("reviewTime")

    df.printSchema
    df.show(5)

    // Filter out the neutral ratings (= 3)
    val df1 = df.filter("overall !=3")
    df1.show
    df1.describe("overall").show

    // Add a 'label' column (ie. 1/0) for Positive (overall rating >= 4) and
    // not-Positive (overall rating < 4)
    val bucketizer = new Bucketizer()
      .setInputCol("overall")
      .setOutputCol("label")
      .setSplits(Array(Double.NegativeInfinity, 4.0, Double.PositiveInfinity))
    val df2 = bucketizer.transform(df1)

    // Get counts by Rating and Label
    df2.cache
    df2.groupBy("overall", "label").count.show

    // Since there are 13 times as many Positive ratings, we use
    // Stratified Sampling to put both Positive and Negative ratings on
    // an equal footing. sampleBy() does this by downsampling the Positive ratings
    // to 10%. The final argument is a random seed
    val fractions = Map(1.0 -> .1, 0.0 -> 1.0)
    val df3 = df2.stat.sampleBy("label", fractions, 36L)
    df3.groupBy("label").count.show

    // split into training and test dataset
    val splitSeed = 5043
    val Array(trainingData, testData) = df3.randomSplit(Array(0.8, 0.2), splitSeed)
    trainingData.cache

    // Split the input text column into an array of words by using the provided regex pattern
    // and add that to an extra output column
    val tokenizer = new RegexTokenizer()
      .setInputCol("reviewTS")
      .setOutputCol("reviewTokensUf")
      .setPattern("\\s+|[,.()\"]")

    // filter out words which should be excluded
    val remover = new StopWordsRemover()
      .setStopWords(StopWordsRemover.loadDefaultStopWords("english"))
      .setInputCol("reviewTokensUf")
      .setOutputCol("reviewTokens")

    // convert the array of word tokens to vectors of word token counts. 
    // This is the TF part of TF-IDF feature extraction
    val cv = new CountVectorizer()
      .setInputCol("reviewTokens")
      .setOutputCol("cv")
      .setVocabSize(200000)

    // IDF takes feature vectors created from the CountVectorizer and down-weights features 
    // which appear frequently in a collection of texts (the IDF part of TF-IDF feature extraction). 
    // The output 'features' column is the TF-IDF features vector, which the ML model will use
    val idf = new IDF()
      .setInputCol("cv")
      .setOutputCol("features")

    //  regularizer parameters to encourage simple models and avoid overfitting.
    val lpar = 0.02
    val apar = 0.3

    // The final element in the ML pipeline is the Logistic Regression estimator which will
    // train on the vector of labels and features and return a (transformer) model
    val lr = new LogisticRegression()
      .setMaxIter(100)
      .setRegParam(lpar)
      .setElasticNetParam(apar)

    // Put the Tokenizer, CountVectorizer, IDF, and Logistic Regression Classifier in 
    // a pipeline. The pipeline chains multiple transformers and estimators together to specify 
    // an ML workflow
    val steps = Array(tokenizer, remover, cv, idf, lr)
    val pipeline = new Pipeline().setStages(steps)

    // train the logistic regression model with elastic net regularization
    val model = pipeline.fit(trainingData)

    // get vocabulary from the Count Vectorizer
    val vocabulary = model.stages(2).asInstanceOf[CountVectorizerModel].vocabulary
    // get the logistic regression model 
    val lrModel = model.stages.last.asInstanceOf[LogisticRegressionModel]
    // Get array of coefficient weights
    val weights = lrModel.coefficients.toArray
    // get array of words and corresponding weights
    val word_weight = vocabulary.zip(weights)

    word_weight.sortBy(-_._2).take(5).foreach {
      case (word, weight) =>
        println(s"feature: $word, importance: $weight")
    }
    word_weight.sortBy(_._2).take(5).foreach {
      case (word, weight) =>
        println(s"feature: $word, importance: $weight")
    }

    //transform the test set with the model pipeline
    val predictions = model.transform(testData)

    // Evaluate the model using the RoC metric 
    val evaluator = new BinaryClassificationEvaluator()
    val areaUnderROC = evaluator.evaluate(predictions)
    println("areaUnderROC " + areaUnderROC)

    // Calculate False/True Positives/Negatives
    val lp = predictions.select("prediction", "label")
    val counttotal = predictions.count().toDouble
    val correct = lp.filter("label == prediction").count().toDouble
    val wrong = lp.filter("label != prediction").count().toDouble
    val ratioWrong = wrong / counttotal
    val ratioCorrect = correct / counttotal
    val truen = (lp.filter($"label" === 0.0)
      .filter($"label" === $"prediction")
      .count()) / counttotal
    val truep = (lp.filter($"label" === 1.0)
      .filter($"label" === $"prediction")
      .count()) / counttotal
    val falsen = (lp.filter($"label" === 0.0)
      .filter(not($"label" === $"prediction"))
      .count()) / counttotal
    val falsep = (lp.filter($"label" === 1.0)
      .filter(not($"label" === $"prediction"))
      .count()) / counttotal
    
    // Calculate Precision, Recall, F-Score and Accuracy
    val precision= truep / (truep + falsep)
    val recall= truep / (truep + falsen)
    val fmeasure = (2 * precision * recall) / (precision + recall)
    val accuracy = (truep + truen) / (truep + truen + falsep + falsen)

    println("ratio correct", ratioCorrect)
    println("true positive", truep)
    println("false positive", falsep)
    println("true negative", truen)
    println("false negative", falsen)
    println("Precision", precision)
    println("Recall", recall)
    println("fmeasure", fmeasure)
    println("Accuracy", accuracy)

    // print out the token words for reviews with the highest probability of a negative sentiment
    predictions.filter($"prediction" === 0.0)
       .select("summary","reviewTokens","overall","prediction")
       .orderBy(desc("rawPrediction")).show(5)

    // ... and a positive sentiment
    predictions.filter($"prediction" === 1.0)
       .select("summary","reviewTokens","overall","prediction")
       .orderBy(desc("rawPrediction")).show(5)

    // save our fitted pipeline model to the distributed file store for later use in production
    // The result of saving the pipeline model is a JSON file for metadata and Parquet files for 
    // model data. We can reload the model with the load command
    var modeldirectory: String = args(0) + '/' + "sentimentModel/"
    model.write.overwrite().save(modeldirectory)
    // val sameModel = org.apache.spark.ml.PipelineModel.load(modeldirectory)

    println(s"========== DONE ==========" )

    spark.stop()
  }
}
