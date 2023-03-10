package org.flink.streaming._3time

import java.lang

import org.apache.flink.api.common.functions.CoGroupFunction
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.api.windowing.assigners.TumblingProcessingTimeWindows
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.util.Collector

import scala.collection.JavaConverters._

object CoGroup {

  def main(args: Array[String]): Unit = {

    val senv = StreamExecutionEnvironment.getExecutionEnvironment

    val socketSource1 = senv.socketTextStream("localhost", 9000)
    val socketSource2 = senv.socketTextStream("localhost", 9001)

    val input1: DataStream[(String, Int)] = socketSource1.flatMap {
      (line: String, out: Collector[(String, Int)]) => {
        val array = line.split(" ")
        if (array.size == 2) {
          out.collect((array(0), array(1).toInt))
        }
      }
    }

    val input2: DataStream[(String, Int)] = socketSource2.flatMap {
      (line: String, out: Collector[(String, Int)]) => {
        val array = line.split(" ")
        if (array.size == 2) {
          out.collect((array(0), array(1).toInt))
        }
      }
    }

    val coGroupResult = input1.coGroup(input2)
      .where(i1 => i1._1)
      .equalTo(i2 => i2._1)
      .window(TumblingProcessingTimeWindows.of(Time.seconds(60)))
      .apply(new MyCoGroupFunction)

    coGroupResult.print()

    senv.execute("Window CoGroup Function")
  }

  class MyCoGroupFunction extends CoGroupFunction[(String, Int), (String, Int), String] {

    override def coGroup(input1: lang.Iterable[(String, Int)], input2: lang.Iterable[(String, Int)], out: Collector[String]): Unit = {
      input1.asScala.foreach(element => out.collect("input1 :" + element.toString()))
      input2.asScala.foreach(element => out.collect("input2 :" + element.toString()))
    }
  }

}