package org.flink.streaming._3time

import java.text.SimpleDateFormat

import org.flink.streaming.utils.stock.{StockPrice, StockSource}
import org.apache.flink.api.common.eventtime.{SerializableTimestampAssigner, WatermarkStrategy}
import org.apache.flink.api.common.state.{ValueState, ValueStateDescriptor}
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.functions.KeyedProcessFunction
import org.apache.flink.streaming.api.scala._
import org.apache.flink.util.Collector

object ProcessFunction {

  def main(args: Array[String]) {

    val env = StreamExecutionEnvironment.getExecutionEnvironment

    env.setParallelism(1)

    val inputStream: DataStream[StockPrice] = env
      .addSource(new StockSource("stock/stock-tick-20200108.csv"))
      .assignTimestampsAndWatermarks(
        WatermarkStrategy
          .forMonotonousTimestamps()
          .withTimestampAssigner(new SerializableTimestampAssigner[StockPrice] {
            override def extractTimestamp(t: StockPrice, l: Long): Long = t.ts
          })
      )

    val warnings: DataStream[String] = inputStream
      .keyBy(stock => stock.symbol)
      .process(new IncreaseAlertFunction(10000))

    warnings.print()
    val outputTag: OutputTag[StockPrice] = OutputTag[StockPrice]("high-volume-trade")
    val sideOutputStream: DataStream[StockPrice] = warnings.getSideOutput(outputTag)
    sideOutputStream.print()
    env.execute("stock tick data")
  }

  class IncreaseAlertFunction(intervalMills: Long)
    extends KeyedProcessFunction[String, StockPrice, String] {

    private var lastPrice: ValueState[Double] = _
    private var currentTimer: ValueState[Long] = _

    override def open(parameters: Configuration): Unit = {

      lastPrice = getRuntimeContext.getState(new ValueStateDescriptor[Double]("lastPrice", classOf[Double]))
      currentTimer = getRuntimeContext.getState(new ValueStateDescriptor[Long]("timer", classOf[Long]))
    }

    override def processElement(stock: StockPrice,
                                context: KeyedProcessFunction[String, StockPrice, String]#Context,
                                out: Collector[String]): Unit = {

      val prevPrice = lastPrice.value()
      lastPrice.update(stock.price)
      val curTimerTimestamp = currentTimer.value()
      if (prevPrice == 0.0) {
      } else if (stock.price < prevPrice) {
        context.timerService().deleteEventTimeTimer(curTimerTimestamp)
        currentTimer.clear()
      } else if (stock.price >= prevPrice && curTimerTimestamp == 0) {
        val timerTs = context.timestamp() + intervalMills

        context.timerService().registerEventTimeTimer(timerTs)
        currentTimer.update(timerTs)
      }

      val highVolumeOutput: OutputTag[StockPrice] = new OutputTag[StockPrice]("high-volume-trade")

      if (stock.volume > 1000) {
        context.output(highVolumeOutput, stock)
      }
    }

    override def onTimer(ts: Long,
                         ctx: KeyedProcessFunction[String, StockPrice, String]#OnTimerContext,
                         out: Collector[String]): Unit = {

      val formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS")

      out.collect(formatter.format(ts) + ", symbol: " + ctx.getCurrentKey +
        " monotonically increased for " + intervalMills + " millisecond.")
      currentTimer.clear()
    }
  }
}
