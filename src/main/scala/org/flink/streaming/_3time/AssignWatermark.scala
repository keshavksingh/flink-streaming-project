package org.flink.streaming._3time

import scala.math._
import org.apache.flink.api.common.eventtime.{SerializableTimestampAssigner, Watermark, WatermarkGenerator, WatermarkGeneratorSupplier, WatermarkOutput, WatermarkStrategy}
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.api.scala.function.WindowFunction
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.streaming.api.windowing.windows.TimeWindow
import org.apache.flink.streaming.runtime.operators.util.AssignerWithPeriodicWatermarksAdapter
import org.apache.flink.util.Collector
import org.flink.streaming._1setupintro.{SensorReading, SensorSource}

import java.time.Duration

object AssignWatermark {
  def main(args: Array[String]) {

    val env = StreamExecutionEnvironment.getExecutionEnvironment

    val SensorData:DataStream[SensorReading]=env.addSource(new SensorSource)
    //Writing a Periodic WatermarkGenerator
    val watermarkGen:DataStream[SensorReading]=SensorData
      .assignTimestampsAndWatermarks(
        WatermarkStrategy
          .forGenerator[SensorReading](
            new WatermarkGeneratorSupplier[SensorReading] {
              override def createWatermarkGenerator(context: WatermarkGeneratorSupplier.Context): WatermarkGenerator[SensorReading] =
                new MyTimeLagWatermarkGenerator
            })
          .withTimestampAssigner(new SerializableTimestampAssigner[SensorReading] {
            override def extractTimestamp(event: SensorReading, timestamp: Long): Long =
              event.timestamp
          }))

    val avgTemp:DataStream[SensorReading]=watermarkGen
      .map(r =>
        SensorReading(r.id, r.timestamp, (r.temperature - 32) * (5.0 / 9.0)))
      .keyBy(_.id)
      .window(TumblingEventTimeWindows.of(Time.seconds(5)))
      .apply(new TemperatureAverager)
    avgTemp.print()
    env.execute("Compute average sensor temperature")

  }
}

/**
 * This generator generates watermarks assuming that elements arrive out of order,
 * but only to a certain degree. The latest elements for a certain timestamp t will arrive
 * at most n milliseconds after the earliest elements for timestamp t.
 */
class MyBoundedOutOfOrdernessGenerator extends WatermarkGenerator[SensorReading] {

  val maxOutOfOrderness = 4000L // 4 seconds
  var currentMaxTimestamp: Long = _

  override def onEvent(element: SensorReading, eventTimestamp: Long, output: WatermarkOutput): Unit = {
    currentMaxTimestamp = max(eventTimestamp, currentMaxTimestamp)
  }
  override def onPeriodicEmit(output: WatermarkOutput): Unit = {
    // emit the watermark as current highest timestamp minus the out-of-orderness bound
    output.emitWatermark(new Watermark(currentMaxTimestamp - maxOutOfOrderness - 1))
  }
}
/**
 * This generator generates watermarks that are lagging behind processing
 * time by a fixed amount. It assumes that elements arrive in Flink after
 * a bounded delay.
 */
class MyTimeLagWatermarkGenerator extends WatermarkGenerator[SensorReading] {
  val maxTimeLag = 5000L // 5 seconds
  override def onEvent(element: SensorReading, eventTimestamp: Long, output: WatermarkOutput): Unit = {
    // don't need to do anything because we work on processing time
  }
  override def onPeriodicEmit(output: WatermarkOutput): Unit = {
    output.emitWatermark(new Watermark(System.currentTimeMillis() - maxTimeLag))
  }
}
/* Implemented with The inherent datastream which has a watermarker
class MyPunctuatedGenerator extends WatermarkGenerator[SensorReading] {
  override def onEvent(event: SensorReading, eventTimestamp: Long, output: WatermarkOutput): Unit = {
    if (event.hasWatermarkMarker()) {
      output.emitWatermark(new Watermark(event.getWatermarkTimestamp()))
    }
  }

  override def onPeriodicEmit(output: WatermarkOutput): Unit = {
  }
}*/

/** User-defined WindowFunction to compute the average temperature of SensorReadings */
class TemperatureAverager extends WindowFunction[SensorReading, SensorReading, String, TimeWindow] {

  /** apply() is invoked once for each window */
  override def apply(
                      sensorId: String,
                      window: TimeWindow,
                      vals: Iterable[SensorReading],
                      out: Collector[SensorReading]): Unit = {

    // compute the average temperature
    val (cnt, sum) = vals.foldLeft((0, 0.0))((c, r) => (c._1 + 1, c._2 + r.temperature))
    val avgTemp = sum / cnt

    // emit a SensorReading with the average temperature
    out.collect(SensorReading(sensorId, window.getEnd, avgTemp))
  }
}
