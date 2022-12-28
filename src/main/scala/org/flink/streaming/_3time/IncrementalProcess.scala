package org.flink.streaming._3time


import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.api.scala.function.ProcessWindowFunction
import org.apache.flink.streaming.api.windowing.assigners.TumblingProcessingTimeWindows
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.streaming.api.windowing.windows.TimeWindow
import org.apache.flink.util.Collector
import org.flink.streaming.utils.stock.StockSource

object IncrementalProcess {

  def main(args: Array[String]): Unit = {

    val senv = StreamExecutionEnvironment.getExecutionEnvironment

    val input = senv.addSource(new StockSource("stock/stock-tick-20200108.csv"))

    val maxMin = input
      .map(s => (s.symbol, s.price, s.price))
      .keyBy(s => s._1)
      .window(TumblingProcessingTimeWindows.of(Time.seconds(10)))
      .reduce(
        ((s1: (String, Double, Double), s2: (String, Double, Double)) => (s1._1, Math.max(s1._2, s2._2), Math.min(s1._3, s2._3))),
        new WindowEndProcessFunction
      )

    maxMin.print()
    /*Results
    1> MaxMinPrice(US2.AAPL,297.8,297.26,1672187320000)
    2> MaxMinPrice(US1.AMZN,1897.63,1897.63,1672187320000)
    1> MaxMinPrice(US2.AAPL,297.81,297.69,1672187330000)
    2> MaxMinPrice(US1.AMZN,1897.44,1897.44,1672187330000)
    1> MaxMinPrice(US2.AAPL,297.82,297.62,1672187340000)
     */

    senv.execute("Combine Reduce And Process Function")
  }

  case class MaxMinPrice(symbol: String, max: Double, min: Double, windowEndTs: Long)

  class WindowEndProcessFunction extends ProcessWindowFunction[(String, Double, Double), MaxMinPrice, String, TimeWindow] {

    override def process(key: String,
                         context: Context,
                         elements: Iterable[(String, Double, Double)],
                         out: Collector[MaxMinPrice]): Unit = {
      val maxMinItem = elements.head
      val windowEndTs = context.window.getEnd
      out.collect(MaxMinPrice(key, maxMinItem._2, maxMinItem._3, windowEndTs))
    }
  }
}
