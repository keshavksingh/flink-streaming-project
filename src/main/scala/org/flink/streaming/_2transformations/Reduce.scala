package org.flink.streaming._2transformations
import org.apache.flink.api.common.functions.ReduceFunction
import org.apache.flink.streaming.api.scala._

object Reduce {

  def main(args: Array[String]): Unit = {

    val senv: StreamExecutionEnvironment = StreamExecutionEnvironment.getExecutionEnvironment

    val dataStream: DataStream[Score] = senv.fromElements(
      Score("Danny", "English", 90), Score("Ronny", "English", 88), Score("Danny", "Math", 85),
      Score("Ronny", "Math", 92), Score("Charlie", "Math", 91), Score("Charlie", "English", 87))

    val sumReduceFunctionStream: DataStream[Score] = dataStream
      .keyBy(item => item.name)
      .reduce(new MyReduceFunction)

    /* Results
    1> Score(Danny,English,90)
    1> Score(Danny,Sum,175)
    1> Score(Charlie,Math,91)
    1> Score(Charlie,Sum,178)
    2> Score(Ronny,English,88)
    2> Score(Ronny,Sum,180)
     */
    val sumLambdaStream: DataStream[Score] = dataStream
    .keyBy(item => item.name)
    .reduce((s1, s2) => Score(s1.name, "Sum", s1.score + s2.score))
    sumLambdaStream.print()
    /*Results
    1> Score(Danny,English,90)
    1> Score(Danny,Sum,175)
    1> Score(Charlie,Math,91)
    1> Score(Charlie,Sum,178)
    2> Score(Ronny,English,88)
    2> Score(Ronny,Sum,180)
     */
    senv.execute("Basic Reduce Transformation")
  }

  case class Score(name: String, course: String, score: Int)

  class MyReduceFunction() extends ReduceFunction[Score] {
    override def reduce(s1: Score, s2: Score): Score = {
      Score(s1.name, "Sum", s1.score + s2.score)
    }
  }

}