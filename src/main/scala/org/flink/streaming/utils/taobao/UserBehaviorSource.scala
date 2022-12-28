package org.flink.streaming.utils.taobao

import java.io.InputStream

import org.apache.flink.streaming.api.functions.source.RichSourceFunction
import org.apache.flink.streaming.api.functions.source.SourceFunction.SourceContext

class UserBehaviorSource(path: String) extends RichSourceFunction[UserBehavior] {

  var isRunning: Boolean = true
  var streamSource: InputStream = _

  override def run(sourceContext: SourceContext[UserBehavior]): Unit = {
    streamSource = this.getClass.getClassLoader.getResourceAsStream(path)
    val lines: Iterator[String] = scala.io.Source.fromInputStream(streamSource).getLines
    var isFirstLine: Boolean = true
    var timeDiff: Long = 0
    var lastEventTs: Long = 0
    while (isRunning && lines.hasNext) {
      val line = lines.next()
      val itemStrArr = line.split(",")
      val eventTs: Long = itemStrArr(4).toLong
      if (isFirstLine) {
        lastEventTs = eventTs
        isFirstLine = false
      }
      val userBehavior = UserBehavior(itemStrArr(0).toLong, itemStrArr(1).toLong, itemStrArr(2).toInt, itemStrArr(3), eventTs)
      timeDiff = eventTs - lastEventTs
      if (timeDiff > 0)
        Thread.sleep(timeDiff * 1000)
      sourceContext.collect(userBehavior)
      lastEventTs = eventTs
    }
  }

  override def cancel(): Unit = {
    streamSource.close()
    isRunning = false
  }
}
