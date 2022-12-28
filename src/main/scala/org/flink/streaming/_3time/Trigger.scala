package org.flink.streaming._3time


import org.flink.streaming._3time.AggregateFunction.AverageAggregate
import org.flink.streaming.utils.stock.{StockPrice, StockSource}
import org.apache.flink.api.common.state.{ValueState, ValueStateDescriptor}
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.api.windowing.assigners.TumblingProcessingTimeWindows
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.streaming.api.windowing.triggers.Trigger.TriggerContext
import org.apache.flink.streaming.api.windowing.triggers.{Trigger, TriggerResult}
import org.apache.flink.streaming.api.windowing.windows.TimeWindow

object Trigger {

  def main(args: Array[String]): Unit = {

    val senv = StreamExecutionEnvironment.getExecutionEnvironment

    val stockStream = senv.addSource(new StockSource("stock/stock-tick-20200108.csv"))

    val average = stockStream
      .keyBy(s => s.symbol)
      .window(TumblingProcessingTimeWindows.of(Time.seconds(60)))
      .trigger(new MyTrigger)
      .aggregate(new AverageAggregate)

    average.print()

    senv.execute("Trigger")
  }

  class MyTrigger extends Trigger[StockPrice, TimeWindow] {

    override def onElement(element: StockPrice,
                           time: Long,
                           window: TimeWindow,
                           triggerContext: TriggerContext): TriggerResult = {
      val lastPriceState: ValueState[Double] = triggerContext.getPartitionedState(new ValueStateDescriptor[Double]("lastPriceState", classOf[Double]))

      var triggerResult: TriggerResult = TriggerResult.CONTINUE

      if (Option(lastPriceState.value()).isDefined) {
        if ((lastPriceState.value() - element.price) > lastPriceState.value() * 0.05) {
          triggerResult = TriggerResult.FIRE_AND_PURGE
        } else if ((lastPriceState.value() - element.price) > lastPriceState.value() * 0.01) {
          val t = triggerContext.getCurrentProcessingTime + (10 * 1000 - (triggerContext.getCurrentProcessingTime % 10 * 1000))
          triggerContext.registerProcessingTimeTimer(t)
        }
      }
      lastPriceState.update(element.price)
      triggerResult
    }

    override def onEventTime(time: Long, window: TimeWindow, triggerContext: TriggerContext): TriggerResult = {
      TriggerResult.CONTINUE
    }

    override def onProcessingTime(time: Long, window: TimeWindow, triggerContext: TriggerContext): TriggerResult = {
      TriggerResult.FIRE_AND_PURGE
    }

    override def clear(window: TimeWindow, triggerContext: TriggerContext): Unit = {
      val lastPrice: ValueState[Double] = triggerContext.getPartitionedState(new ValueStateDescriptor[Double]("lastPriceState", classOf[Double]))
      lastPrice.clear()
    }
  }
}
