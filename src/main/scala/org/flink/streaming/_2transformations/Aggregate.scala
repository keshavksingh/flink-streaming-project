package org.flink.streaming._2transformations

import org.apache.flink.streaming.api.scala._

object Aggregate {
  def main(args: Array[String]): Unit = {
    val senv: StreamExecutionEnvironment = StreamExecutionEnvironment.getExecutionEnvironment
    val tupleStream = senv.fromElements(
      (0, 0, 0), (0, 2, 2),(0, 1, 1),
      (1, 0, 6), (1, 1, 9), (1, 2, 8)
    )

    val sumStream = tupleStream.keyBy(tupleStream=>tupleStream._1).sum(1)
    sumStream.print()
  /* Returns Sum for 2nd, Streaming on KeyBy 1st - it doest not change the 3rd while doing so.
  2> (0,0,0)
  2> (0,2,0)
  2> (0,3,0)
  2> (1,0,6)
  2> (1,1,6)
  2> (1,3,6)
   */
    val maxStream = tupleStream.keyBy(tupleStream=>tupleStream._1).max(2)
    maxStream.print()
  /* Returns max value for 3rd, Streaming on KeyBy 1st - it doest not change the 2nd while doing so.
  2> (0,0,0)
  2> (0,0,2)
  2> (0,0,2)
  2> (1,0,6)
  2> (1,0,9)
  2> (1,0,9)
   */
    val maxByStream = tupleStream.keyBy(tupleStream=>tupleStream._1).maxBy(2)
    maxByStream.print()
    /*
    Returns max by 3rd value, Streaming on the KeyBy 1st
    2> (0,0,0)
    2> (0,2,2)
    2> (0,2,2)
    2> (1,0,6)
    2> (1,1,9)
    2> (1,1,9)
     */

    senv.execute("basic aggregation transformation")

  }

}
