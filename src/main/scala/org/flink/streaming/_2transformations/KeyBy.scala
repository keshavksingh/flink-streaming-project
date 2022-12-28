package org.flink.streaming._2transformations
import org.apache.flink.api.java.functions.KeySelector
import org.apache.flink.streaming.api.scala._

object KeyBy {

  def main(args: Array[String]): Unit = {

    val senv: StreamExecutionEnvironment = StreamExecutionEnvironment.getExecutionEnvironment
    val dataStream: DataStream[(Int, Double)] = senv.fromElements((1, 1.0), (2, 3.2), (1, 5.5), (3, 10.0), (3, 12.5))

    val keyedStream: DataStream[(Int, Double)] = dataStream.keyBy(dataStream=>dataStream._1).sum(1)
    /*Results
    2> (1,1.0)
    2> (2,3.2)
    2> (1,6.5)
    2> (3,10.0)
    2> (3,22.5)
     */
    val lambdaKeyedStream: DataStream[(Int, Double)] = dataStream.keyBy(x => x._1).sum(1)
    /*Results
    2> (1,1.0)
    2> (2,3.2)
    2> (1,6.5)
    2> (3,10.0)
    2> (3,22.5)
     */
    val keySelectorStream: DataStream[(Int, Double)] = dataStream.keyBy(new MyKeySelector).sum(1)
    keySelectorStream.print()
    /*Results
    2> (1,1.0)
    2> (2,3.2)
    2> (1,6.5)
    2> (3,10.0)
    2> (3,22.5)
     */
    senv.execute("Basic KeyBy Transformation")
  }

  class MyKeySelector extends KeySelector[(Int, Double), (Int)] {
    override def getKey(in: (Int, Double)): Int = {
      return in._1
    }
  }

}