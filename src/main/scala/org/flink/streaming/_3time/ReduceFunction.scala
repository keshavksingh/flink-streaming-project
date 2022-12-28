package org.flink.streaming._3time

import org.flink.streaming.utils.stock.{StockPrice, StockSource}
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.api.windowing.assigners.TumblingProcessingTimeWindows
import org.apache.flink.streaming.api.windowing.time.Time

object ReduceFunction {

  def main(args: Array[String]): Unit = {

    val senv = StreamExecutionEnvironment.getExecutionEnvironment

    val stockStream: DataStream[StockPrice] = senv.addSource(new StockSource("stock/stock-tick-20200108.csv"))

    val sum = stockStream
      .keyBy(s => s.symbol)
      .window(TumblingProcessingTimeWindows.of(Time.seconds(10)))
      .reduce((s1, s2) => StockPrice(s1.symbol, s2.price, s2.ts,s1.volume + s2.volume))

    sum.print()

    senv.execute("Window Reduce Function")
  }

}