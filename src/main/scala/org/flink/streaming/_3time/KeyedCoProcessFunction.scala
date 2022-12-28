package org.flink.streaming._3time

import org.apache.flink.api.common.eventtime.{SerializableTimestampAssigner, WatermarkStrategy}
import org.apache.flink.api.common.state.{ValueState, ValueStateDescriptor}
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.functions.co.KeyedCoProcessFunction
import org.apache.flink.streaming.api.scala._
import org.apache.flink.util.Collector
import org.flink.streaming.utils.stock.{StockPrice, StockSource,Media,MediaSource}


object KeyedCoProcessFunction {

  def main(args: Array[String]) {

    val env = StreamExecutionEnvironment.getExecutionEnvironment

    env.setParallelism(1)

    val stockStream: DataStream[StockPrice] = env
      .addSource(new StockSource("stock/stock-tick-20200108.csv"))
      .assignTimestampsAndWatermarks(
        WatermarkStrategy
          .forMonotonousTimestamps()
          .withTimestampAssigner(new SerializableTimestampAssigner[StockPrice] {
            override def extractTimestamp(t: StockPrice, l: Long): Long = t.ts
          })
      )

    val mediaStream: DataStream[Media] = env
      .addSource(new MediaSource)
      .assignTimestampsAndWatermarks(
        WatermarkStrategy
          .forMonotonousTimestamps()
          .withTimestampAssigner(new SerializableTimestampAssigner[Media] {
            override def extractTimestamp(t: Media, l: Long): Long = t.ts
          })
      )

    val joinStream: DataStream[StockPrice] = stockStream.connect(mediaStream)
      .keyBy(0, 0)
      .process(new JoinStockMediaProcessFunction())

    joinStream.print()

    env.execute("Stock Tick Data")
  }

  class JoinStockMediaProcessFunction extends KeyedCoProcessFunction[String, StockPrice, Media, StockPrice] {

    // mediaState
    private var mediaState: ValueState[String] = _

    override def open(parameters: Configuration): Unit = {
      mediaState = getRuntimeContext.getState(
        new ValueStateDescriptor[String]("mediaStatusState", classOf[String]))

    }

    override def processElement1(stock: StockPrice,
                                 context: KeyedCoProcessFunction[String, StockPrice, Media, StockPrice]#Context,
                                 collector: Collector[StockPrice]): Unit = {
      val mediaStatus = mediaState.value()
      if (null != mediaStatus) {
        val newStock = stock.copy(mediaStatus = mediaStatus)
        collector.collect(newStock)
      }
    }

    override def processElement2(media: Media,
                                 context: KeyedCoProcessFunction[String, StockPrice, Media, StockPrice]#Context,
                                 collector: Collector[StockPrice]): Unit = {
      mediaState.update(media.status)
    }
  }
}
