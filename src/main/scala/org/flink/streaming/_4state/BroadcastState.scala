package org.flink.streaming._4state

import org.flink.streaming.utils.taobao.{BehaviorPattern, UserBehavior, UserBehaviorSource}
import org.apache.flink.api.common.eventtime.{SerializableTimestampAssigner, WatermarkStrategy}
import org.apache.flink.api.common.state.{BroadcastState, MapStateDescriptor, ValueState, ValueStateDescriptor}
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.datastream.BroadcastStream
import org.apache.flink.streaming.api.functions.co.KeyedBroadcastProcessFunction
import org.apache.flink.streaming.api.scala._
import org.apache.flink.util.Collector

object BroadcastState {

  def main(args: Array[String]): Unit = {

    val env: StreamExecutionEnvironment = StreamExecutionEnvironment.getExecutionEnvironment
    env.setParallelism(8)

    val userBehaviorStream: DataStream[UserBehavior] = env
      .addSource(new UserBehaviorSource("taobao/UserBehavior-20171201.csv"))
      .assignTimestampsAndWatermarks(
        WatermarkStrategy
          .forMonotonousTimestamps()
          .withTimestampAssigner(new SerializableTimestampAssigner[UserBehavior] {
            override def extractTimestamp(t: UserBehavior, l: Long): Long = t.timestamp * 1000
          })
      )

    // BehaviorPattern
    val patternStream: DataStream[BehaviorPattern] = env.fromElements(BehaviorPattern("pv", "buy"))

    // Broadcast State Key->Value,MapStateDescriptor
    val broadcastStateDescriptor =
      new MapStateDescriptor[Void, BehaviorPattern]("behaviorPattern", classOf[Void], classOf[BehaviorPattern])
    val broadcastStream: BroadcastStream[BehaviorPattern] = patternStream
      .broadcast(broadcastStateDescriptor)

    // KeyedStream
    val keyedStream =  userBehaviorStream.keyBy(user => user.userId)
    // KeyedStream,connect process
    val matchedStream = keyedStream
      .connect(broadcastStream)
      .process(new BroadcastPatternFunction)

    matchedStream.print()
    /*Sample Results
    3> (39613,BehaviorPattern(pv,buy))
    3> (836353,BehaviorPattern(pv,buy))
    8> (697264,BehaviorPattern(pv,buy))
    6> (401785,BehaviorPattern(pv,buy))
     */

    env.execute("Broadcast Taobao Example")
  }

  class BroadcastPatternFunction
    extends KeyedBroadcastProcessFunction[Long, UserBehavior, BehaviorPattern, (Long, BehaviorPattern)] {

    private var lastBehaviorState: ValueState[String] = _
    // Broadcast State Descriptor
    private var bcPatternDesc: MapStateDescriptor[Void, BehaviorPattern] = _

    override def open(parameters: Configuration): Unit = {

      lastBehaviorState = getRuntimeContext.getState(
        new ValueStateDescriptor[String]("lastBehaviorState", classOf[String])
      )

      bcPatternDesc = new MapStateDescriptor[Void, BehaviorPattern]("behaviorPattern", classOf[Void], classOf[BehaviorPattern])

    }


    override def processBroadcastElement(pattern: BehaviorPattern,
                                         context: KeyedBroadcastProcessFunction[Long, UserBehavior, BehaviorPattern, (Long, BehaviorPattern)]#Context,
                                         collector: Collector[(Long, BehaviorPattern)]): Unit = {

      val bcPatternState: BroadcastState[Void, BehaviorPattern] = context.getBroadcastState(bcPatternDesc)
      bcPatternState.put(null, pattern)
    }

    override def processElement(userBehavior: UserBehavior,
                                context: KeyedBroadcastProcessFunction[Long, UserBehavior, BehaviorPattern, (Long, BehaviorPattern)]#ReadOnlyContext,
                                collector: Collector[(Long, BehaviorPattern)]): Unit = {

      val pattern: BehaviorPattern = context.getBroadcastState(bcPatternDesc).get(null)
      val lastBehavior: String = lastBehaviorState.value()
      if (pattern != null && lastBehavior != null) {
        if (pattern.firstBehavior.equals(lastBehavior) &&
          pattern.secondBehavior.equals(userBehavior.behavior))
          collector.collect((userBehavior.userId, pattern))
      }
      lastBehaviorState.update(userBehavior.behavior)
    }
  }
}

