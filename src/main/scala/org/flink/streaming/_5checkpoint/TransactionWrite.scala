package org.flink.streaming._5checkpoint


import java.io.BufferedWriter
import java.nio.file.{Files, Path, Paths}
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import org.apache.flink.streaming.api.scala._
import org.apache.flink.api.common.typeutils.base.{StringSerializer, VoidSerializer}
import org.apache.flink.configuration.{Configuration, RestOptions}
import org.apache.flink.streaming.api.functions.sink.{SinkFunction, TwoPhaseCommitSinkFunction}

object TransactionWrite {

  def main(args: Array[String]): Unit = {
    val conf = new Configuration
    // http://localhost:8082 Flink Web UI
    conf.setInteger(RestOptions.PORT, 8082)
    // 1
    val env = StreamExecutionEnvironment.createLocalEnvironment(1, conf)
    // Checkpoint
    env.getCheckpointConfig.setCheckpointInterval(5 * 1000)
    val countStream = env.addSource(new CheckpointedSource.CheckpointedSource)
    val result = countStream.map(new CheckpointedSource.FailingMapper(20))

    val preCommitPath = "C:/flinkprojects/flink-streaming-project/tmp/flink-sink-precommit"
    val commitedPath = "C:/flinkprojects/flink-streaming-project/tmp/flink-sink-commited"
    if (!Files.exists(Paths.get(preCommitPath))) Files.createDirectory(Paths.get(preCommitPath))
    if (!Files.exists(Paths.get(commitedPath))) Files.createDirectory(Paths.get(commitedPath))
    // Exactly-Once - Sink，，
    result.addSink(new TwoPhaseFileSink(preCommitPath, commitedPath))
    // Exactly-Once，
    result.print()
    env.execute("Two File Sink")
  }


  class TwoPhaseFileSink(var preCommitPath: String, var commitedPath: String) extends TwoPhaseCommitSinkFunction[(String, Int), String, Void](StringSerializer.INSTANCE, VoidSerializer.INSTANCE) {
    var transactionWriter: BufferedWriter = _

    override def invoke(transaction: String, in: (String, Int), context: SinkFunction.Context): Unit = {
      transactionWriter.write(in._1 + " " + in._2 + "\n")
    }

    override def beginTransaction: String = {
      val time = LocalDateTime.now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
      val subTaskIdx = getRuntimeContext.getIndexOfThisSubtask
      val fileName = time + "-" + subTaskIdx
      val preCommitFilePath = Paths.get(preCommitPath + "/" + fileName)
      Files.createFile(preCommitFilePath)
      transactionWriter = Files.newBufferedWriter(preCommitFilePath)
      System.out.println("Transaction File: " + preCommitFilePath)
      fileName
    }

    override def preCommit(transaction: String): Unit = {
      transactionWriter.flush()
      transactionWriter.close()
    }

    override def commit(transaction: String): Unit = {
      val preCommitFilePath = Paths.get(preCommitPath + "/" + transaction)
      if (Files.exists(preCommitFilePath)) {
        val commitedFilePath = Paths.get(commitedPath + "/" + transaction)
        try
          Files.move(preCommitFilePath, commitedFilePath)
        catch {
          case e: Exception =>
            System.out.println(e)
        }
      }
    }

    override def abort(transaction: String): Unit = {
      val preCommitFilePath = Paths.get(preCommitPath + "/" + transaction)
      if (Files.exists(preCommitFilePath)) try
        Files.delete(preCommitFilePath)
      catch {
        case e: Exception =>
          System.out.println(e)
      }
    }
  }
}
