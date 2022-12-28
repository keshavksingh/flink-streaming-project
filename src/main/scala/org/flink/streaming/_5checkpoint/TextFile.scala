package org.flink.streaming._5checkpoint

import org.apache.flink.api.java.io.TextInputFormat
import org.apache.flink.streaming.api.functions.source.FileProcessingMode
import org.apache.flink.streaming.api.scala._

object TextFile {

  def main(args: Array[String]): Unit = {

    val env: StreamExecutionEnvironment = StreamExecutionEnvironment.getExecutionEnvironment

    val filePath = getClass.getClassLoader.getResource("taobao/UserBehavior-20171201.csv").getPath
    val textInputFormat = new TextInputFormat(new org.apache.flink.core.fs.Path(filePath))

    val readOnceStream = env.readFile(
      textInputFormat,
      filePath,
      FileProcessingMode.PROCESS_ONCE,
      0)

    readOnceStream.print()
    env.execute("Read File From Path")
  }
}
