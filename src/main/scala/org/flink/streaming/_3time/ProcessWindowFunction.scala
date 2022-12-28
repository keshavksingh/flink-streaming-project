package org.flink.streaming._3time

import org.flink.streaming.utils.stock.{StockPrice, StockSource}
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.api.scala.function.ProcessWindowFunction
import org.apache.flink.streaming.api.windowing.assigners.TumblingProcessingTimeWindows
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.streaming.api.windowing.windows.TimeWindow
import org.apache.flink.util.Collector


object ProcessWindowFunction {

  def main(args: Array[String]): Unit = {

    val senv = StreamExecutionEnvironment.getExecutionEnvironment

    val input = senv.addSource(new StockSource("stock/stock-tick-20200108.csv"))

    val frequency = input
      .keyBy(s => s.symbol)
      .window(TumblingProcessingTimeWindows.of(Time.seconds(10)))
      .process(new FrequencyProcessFunction)

    frequency.print()

    senv.execute("window process function")
  }

  /**
   *
   * IN: StockPrice
   * OUT: (String, Double)
   * KEY：Key String
   * W: TimeWindow
   */
  class FrequencyProcessFunction extends ProcessWindowFunction[StockPrice, (String, Double), String, TimeWindow] {

    override def process(key: String, context: Context, elements: Iterable[StockPrice], out: Collector[(String, Double)]): Unit = {
      var countMap = scala.collection.mutable.Map[Double, Int]()

      for(element <- elements) {
        val count = countMap.getOrElse(element.price, 0)
        countMap(element.price) = count + 1
      }
      val sortedMap = countMap.toSeq.sortWith(_._2 > _._2)
      if (sortedMap.size > 0) out.collect((key, sortedMap(0)._1))
    }
  }
}
