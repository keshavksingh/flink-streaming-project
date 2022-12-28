package org.flink.streaming._2transformations
import org.apache.flink.api.common.JobExecutionResult
import org.apache.flink.api.common.accumulators.IntCounter
import org.apache.flink.api.common.functions.{FlatMapFunction, RichFlatMapFunction}
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.api.scala.extensions._
import org.apache.flink.util.Collector

object FlatMap {

  def main(args: Array[String]): Unit = {

    val senv: StreamExecutionEnvironment = StreamExecutionEnvironment.getExecutionEnvironment
    val dataStream: DataStream[String] = senv.fromElements("Hello World", "Hello this is Flink")
    val words: DataStream[String] = dataStream.flatMap ( input => input.split(" ") )
    /* FlatMap is a super-set of Map i.e. it can do everything Map does but while Map is 1:1 FlatMap can Return 1,0 or Many outputs,
    in no particular order the results below from a parallelism of 2
    1> Hello
    2> Hello
    2> this
    2> is
    2> Flink
    1> World
    */
    val words2: DataStream[String] = dataStream.flatMap{ _.split(" ") }
    /* Results
    1> Hello
    2> Hello
    1> this
    1> is
    1> Flink
    2> World
    */

    val longSentenceWords: DataStream[String] = dataStream.flatMap {
      input => {
        if (input.size > 15) {
          input.split(" ").toSeq
        } else {
          Seq.empty
        }
      }
    }
    /*
    Result, Splits only the longer than 15 characters Sentence
    2> Hello
    2> this
    2> is
    2> Flink
     */

    val flatMapWithStream: DataStream[String] = dataStream.flatMapWith {
      case (sentence: String) => {
        if (sentence.size > 15) {
          sentence.split(" ").toSeq
        } else {
          Seq.empty
        }
      }
    }
    /* Results
    2> Hello
    2> this
    2> is
    2> Flink
     */
    val functionStream: DataStream[String] = dataStream.flatMap(new WordSplitFlatMap(15))
    /* Results
    2> Hello
    2> this
    2> is
    2> Flink
     */

    val lambda: DataStream[String] = dataStream.flatMap{
      (value: String, out: Collector[String]) => {
        if (value.size < 15) {
          value.split(" ").foreach(out.collect)
        }
      }
    }
    /* Results
    2> Hello
    2> World
     */

    val richFunctionStream: DataStream[String] = dataStream.flatMap(new WordSplitRichFlatMap(10))
    /* Results
    2> Hello
    2> World
     */
    val jobExecuteResult: JobExecutionResult = senv.execute("basic flatMap transformation")
    val lines: Int = jobExecuteResult.getAccumulatorResult("num-of-lines")
    println("num of lines: " + lines)
    /*Results
    num of lines: 2
    */
  }
  class WordSplitFlatMap(limit: Int) extends FlatMapFunction[String, String] {
    override def flatMap(value: String, out: Collector[String]): Unit = {
      if (value.size > limit) {
        value.split(" ").foreach(out.collect)
      }
    }
  }

  class WordSplitRichFlatMap(limit: Int) extends RichFlatMapFunction[String, String] {
    val numOfLines: IntCounter = new IntCounter(0)

    override def open(parameters: Configuration): Unit = {
      getRuntimeContext.addAccumulator("num-of-lines", this.numOfLines)
    }

    override def flatMap(value: String, out: Collector[String]): Unit = {
      this.numOfLines.add(1)
      if(value.size > limit) {
        value.split(" ").foreach(out.collect)
      }
    }
  }

}
