/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.flink.streaming._1setupintro

import org.apache.flink.api.common.ExecutionConfig

import scala.math._
import org.apache.flink.api.common.eventtime.{SerializableTimestampAssigner, Watermark, WatermarkGenerator, WatermarkGeneratorSupplier, WatermarkOutput, WatermarkStrategy}
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.api.scala.function.WindowFunction
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.streaming.api.windowing.windows.TimeWindow
import org.apache.flink.streaming.runtime.operators.util.AssignerWithPeriodicWatermarksAdapter
import org.apache.flink.util.Collector

import java.time.Duration

/**
 * Skeleton for a Flink DataStream Job.
 *
 * <p>For a tutorial how to write a Flink application, check the
 * tutorials and examples on the <a href="https://flink.apache.org">Flink Website</a>.
 *
 * <p>To package your application into a JAR file for execution, run
 * 'mvn clean package' on the command line.
 *
 * <p>If you change the name of the main class (with the public static void main(String[] args))
 * method, change the respective entry in the POM.xml file (simply search for 'mainClass').
 */
object DataStreamJob3 {
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

