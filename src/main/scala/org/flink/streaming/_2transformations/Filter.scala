package org.flink.streaming._2transformations
import org.apache.flink.api.common.functions.RichFilterFunction
import org.apache.flink.streaming.api.scala._


object Filter {

  def main(args: Array[String]): Unit = {

    val senv: StreamExecutionEnvironment = StreamExecutionEnvironment.getExecutionEnvironment
    val dataStream: DataStream[Int] = senv.fromElements(1, 2, -3, 0, 5, -9, 8)

    val lambda = dataStream.filter ( input => input > 0 ) //Filters all <=0 Values from Input
    val lambda2 = dataStream.map { _ > 0 }//Maps all > 0 values
    val richFunctionDataStream = dataStream.filter(new MyFilterFunction(2)) //Filters all Values >2 So we get ouput only 5 and 8
    richFunctionDataStream.print()

    senv.execute("Basic Filter Transformation")
  }

  class MyFilterFunction(limit: Int) extends RichFilterFunction[Int] {

    override def filter(input: Int): Boolean = {
      if (input > limit) {
        true
      } else {
        false
      }
    }

  }

}