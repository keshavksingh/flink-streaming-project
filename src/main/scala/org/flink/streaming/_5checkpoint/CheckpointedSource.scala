package org.flink.streaming._5checkpoint

import org.apache.flink.streaming.api.scala._
import org.apache.flink.api.common.functions.MapFunction
import org.apache.flink.api.common.state.{ListState, ListStateDescriptor}
import org.apache.flink.configuration.{Configuration, RestOptions}
import org.apache.flink.runtime.state.{FunctionInitializationContext, FunctionSnapshotContext}
import org.apache.flink.streaming.api.checkpoint.CheckpointedFunction
import org.apache.flink.streaming.api.functions.source.{RichSourceFunction, SourceFunction}

object CheckpointedSource {

  def main(args: Array[String]): Unit = {
    val conf = new Configuration
    // http://localhost:8082 Flink Web UI
    conf.setInteger(RestOptions.PORT, 8082)
    // 1
    val env = StreamExecutionEnvironment.createLocalEnvironment(1, conf)
    // Checkpoint
    env.getCheckpointConfig.setCheckpointInterval(2 * 1000)
    val countStream = env.addSource(new CheckpointedSource.CheckpointedSource)
    val result = countStream.map(new CheckpointedSource.FailingMapper(20))
    result.print()
    env.execute("Checkpointed Source")
  }

  class CheckpointedSource extends RichSourceFunction[(String, Int)] with CheckpointedFunction {
    private var offset = 0
    private var isRunning = true
    private var offsetState: ListState[Int] = _

    override def run(ctx: SourceFunction.SourceContext[(String, Int)]): Unit = {
      while ( {
        isRunning
      }) {
        Thread.sleep(100)
        ctx.getCheckpointLock.synchronized {
          ctx.collect(new (String, Int)("" + offset, 1))
          offset += 1
        }

        if (offset == 1000)
          isRunning = false
      }
    }

    override def cancel(): Unit = {
      isRunning = false
    }

    override def snapshotState(snapshotContext: FunctionSnapshotContext): Unit = {
      offsetState.clear()
      // offset
      offsetState.add(offset)
    }

    override def initializeState(initializationContext: FunctionInitializationContext): Unit = { // offsetState
      val desc = new ListStateDescriptor[Int]("offset", classOf[Int])
      offsetState = initializationContext.getOperatorStateStore.getListState(desc)
      val iter = offsetState.get
      if (iter == null || !iter.iterator.hasNext) {
        offset = 0
      }
      else { // offset
        offset = iter.iterator.next
      }
    }
  }

  class FailingMapper(var failInterval: Int) extends MapFunction[(String, Int), (String, Int)] {
    private var count = 0

    override def map(in: (String, Int)): (String, Int) = {
      count += 1
      if (count > failInterval) throw new RuntimeException("Job Fail! Show How Flink Checkpoint And Recovery")
      in
    }
  }

}
