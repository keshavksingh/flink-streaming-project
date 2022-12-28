package org.flink.streaming._2transformations
import org.apache.flink.streaming.api.functions.co.CoMapFunction
import org.apache.flink.streaming.api.scala._

object SimpleConnect {

  def main(args: Array[String]): Unit = {

    val senv: StreamExecutionEnvironment = StreamExecutionEnvironment.getExecutionEnvironment

    val intStream: DataStream[Int] = senv.fromElements(1, 0, 9, 2, 3, 6)
    val stringStream: DataStream[String] = senv.fromElements("LOW", "HIGH", "LOW", "LOW")

    val connectedStream: ConnectedStreams[Int, String] = intStream.connect(stringStream)

    val mapResult = connectedStream.map(new MyCoMapFunction)
    mapResult.print()
    senv.execute("simple connect transformation")
/*Results
1> 1
1> LOW
1> 9
1> LOW
1> 3
2> 0
2> HIGH
2> 2
2> LOW
2> 6
 */
  }

  class MyCoMapFunction extends CoMapFunction[Int, String, String] {

    override def map1(input1: Int): String = input1.toString

    override def map2(input2: String): String = input2
  }
}
