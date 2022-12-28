package org.flink.streaming._3time

import java.text.SimpleDateFormat
import java.time.Duration
import java.util.Calendar
import org.apache.flink.api.common.eventtime.{SerializableTimestampAssigner, WatermarkStrategy}
import org.apache.flink.api.common.functions.AggregateFunction
import org.apache.flink.api.common.state.ValueStateDescriptor
import org.apache.flink.api.scala.typeutils.Types
import org.apache.flink.streaming.api.functions.source.RichSourceFunction
import org.apache.flink.streaming.api.functions.source.SourceFunction.SourceContext
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.api.scala.function.ProcessWindowFunction
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.streaming.api.windowing.windows.TimeWindow
import org.apache.flink.util.Collector

import scala.util.Random

object Late {

  def main(args: Array[String]): Unit = {

    val senv = StreamExecutionEnvironment.getExecutionEnvironment
    senv.setParallelism(1)
    senv.getConfig.setAutoWatermarkInterval(2000L)

    val socketSource = senv.socketTextStream("localhost", 9000)

    val input: DataStream[(String, Long, Int)] = senv
      .addSource(new MySource)
      .assignTimestampsAndWatermarks(
        WatermarkStrategy
          .forBoundedOutOfOrderness(Duration.ofSeconds(5))
          .withTimestampAssigner(new SerializableTimestampAssigner[(String, Long, Int)] {
            override def extractTimestamp(t: (String, Long, Int), l: Long): Long = t._2
          })
      )

    val mainStream = input.keyBy(item => item._1)
      .window(TumblingEventTimeWindows.of(Time.seconds(10)))
      .sideOutputLateData(new OutputTag[(String, Long, Int)]("late-elements"))
      .aggregate(new CountAggregate)

    val lateStream: DataStream[(String, Long, Int)] = mainStream
      .getSideOutput(new OutputTag[(String, Long, Int)]("late-elements"))

    // mainStream.print()
    // lateStream.print()

    val allowedLatenessStream: DataStream[(String, String, Int, String)] = input.keyBy(item => item._1)
      .window(TumblingEventTimeWindows.of(Time.seconds(10)))
      .allowedLateness(Time.seconds(5))
      .process(new AllowedLatenessFunction)

    allowedLatenessStream.print()
    senv.execute("late elements")
  }}

  class CountAggregate extends AggregateFunction[(String, Long, Int), (String, Int), (String, Int)] {

    override def createAccumulator() = ("", 0)

    override def add(item: (String, Long, Int), accumulator: (String, Int)) =
      (item._1, accumulator._2 + 1)

    override def getResult(accumulator:(String, Int)) = accumulator

    override def merge(a: (String, Int), b: (String, Int)) =
      (a._1 ,a._2 + b._2)
  }

  class MySource extends RichSourceFunction[(String, Long, Int)]{
    var isRunning: Boolean = true
    val rand = new Random()

    override def run(srcCtx: SourceContext[(String, Long, Int)]): Unit = {

      var count  = 0

      while (isRunning) {

        val curTime = Calendar.getInstance.getTimeInMillis
        val eventTime = curTime + rand.nextInt(10000)
        srcCtx.collect(("1", eventTime, rand.nextInt()))
        Thread.sleep(100)
      }
    }

    override def cancel(): Unit = {
      isRunning = false
    }
  }

  class AllowedLatenessFunction extends ProcessWindowFunction[
    (String, Long, Int), (String, String, Int, String), String, TimeWindow] {

    override def process(key: String,
                         context: Context,
                         elements: Iterable[(String, Long, Int)],
                         out: Collector[(String, String, Int, String)]): Unit = {

      val isUpdated = context.windowState.getState(
        new ValueStateDescriptor[Boolean]("isUpdated", Types.of[Boolean]))
      val count = elements.size
      val format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

      if (isUpdated.value() == false) {
        out.collect((key, format.format(Calendar.getInstance().getTime), count, "first"))
        isUpdated.update(true)
      } else {

        out.collect((key, format.format(Calendar.getInstance().getTime), count, "updated"))
      }

    }
  }
