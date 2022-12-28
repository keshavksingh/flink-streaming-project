package org.flink.streaming._2transformations

import org.apache.flink.api.common.functions.RichMapFunction
import org.apache.flink.streaming.api.scala._

object Map {

  def main(args: Array[String]): Unit = {

    val senv: StreamExecutionEnvironment = StreamExecutionEnvironment.getExecutionEnvironment

    val dataStream: DataStream[Int] = senv.fromElements(1, 2, -3, 0, 5, -9, 8)
    val lambda = dataStream.map ( input => ("lambda Input : " + input.toString + ", Output : " + (input * 2).toString) )
      /* Results
      1> lambda Input : 1, Output : 2
      1> lambda Input : -3, Output : -6
      1> lambda Input : 5, Output : 10
      1> lambda Input : 8, Output : 16
      2> lambda Input : 2, Output : 4
      2> lambda Input : 0, Output : 0
      2> lambda Input : -9, Output : -18
       */
    val lambda2 = dataStream.map { _ * 2 }
    /* Results
    1> 4
    1> 0
    1> -18
    2> 2
    2> -6
    2> 10
    2> 16
     */

    class DoubleMapFunction extends RichMapFunction[Int, String] {
      override def map(input: Int): String =
        ("overide map Input : " + input.toString + ", Output : " + (input * 2).toString)
    }

    val richFunctionDataStream = dataStream.map {new DoubleMapFunction()}
    /* Results
    1> overide map Input : 2, Output : 4
    1> overide map Input : 0, Output : 0
    1> overide map Input : -9, Output : -18
    2> overide map Input : 1, Output : 2
    2> overide map Input : -3, Output : -6
    2> overide map Input : 5, Output : 10
    2> overide map Input : 8, Output : 16
     */
    val anonymousDataStream = dataStream.map {new RichMapFunction[Int, String] {
      override def map(input: Int): String = {
        ("overide map Input : " + input.toString + ", Output : " + (input * 2).toString)
      }
    }}
    anonymousDataStream.print()
    /* Results
    1> overide map Input : 1, Output : 2
    1> overide map Input : -3, Output : -6
    1> overide map Input : 5, Output : 10
    1> overide map Input : 8, Output : 16
    2> overide map Input : 2, Output : 4
    2> overide map Input : 0, Output : 0
    2> overide map Input : -9, Output : -18
     */
    senv.execute("Basic Map Transformation")
  }

}
