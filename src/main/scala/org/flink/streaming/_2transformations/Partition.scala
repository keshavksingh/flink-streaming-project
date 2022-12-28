package org.flink.streaming._2transformations
import org.apache.flink.api.common.functions.Partitioner
import org.apache.flink.streaming.api.scala._

object Partition {

  def main(args: Array[String]): Unit = {

    val senv: StreamExecutionEnvironment = StreamExecutionEnvironment.getExecutionEnvironment

    val defaultParalleism = senv.getParallelism
    senv.setParallelism(4)
    val dataStream: DataStream[(Int, String)] = senv.fromElements((1, "123"), (2, "abc"), (3, "256"), (4, "zyx")
      , (5, "bcd"), (6, "666"))
    val partitioned: DataStream[(Int, String)] = dataStream.partitionCustom(new MyPartitioner, 1)
    partitioned.print()
    /* Results when Parallelism = 4
    1> (3,256)
    2> (1,123)
    2> (6,666)
    3> (4,zyx)
    3> (5,bcd)
    4> (2,abc)
     */
    senv.execute("partition custom transformation")
  }
  /**
   * Partitioner[T]
   * */
  class MyPartitioner extends Partitioner[String] {

    val rand = scala.util.Random

    override def partition(key: String, numPartitions: Int): Int = {
      var randomNum = rand.nextInt(numPartitions / 2)

      if (key.exists(_.isDigit)) {
        return randomNum
      } else {
        return randomNum + numPartitions / 2
      }
    }
  }
}
