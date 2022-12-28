package org.flink.streaming.utils.stock

/**
  * case class StockPrice
  * symbol
  * ts
  * price
  * volume
  * mediaStatus
  * */
case class StockPrice(symbol: String = "",
                      price: Double = 0d,
                      ts: Long = 0,
                      volume: Int = 0,
                      mediaStatus: String = "")
