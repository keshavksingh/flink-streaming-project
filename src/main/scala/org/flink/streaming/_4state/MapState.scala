package org.flink.streaming._4state

import org.flink.streaming.utils.taobao.{UserBehavior, UserBehaviorSource}
import org.apache.flink.api.common.eventtime.{SerializableTimestampAssigner, WatermarkStrategy}
import org.apache.flink.api.common.functions.RichFlatMapFunction
import org.apache.flink.api.common.state.{MapState, MapStateDescriptor}
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.scala._
import org.apache.flink.util.Collector

object MapState {

  def main(args: Array[String]): Unit = {

    val env: StreamExecutionEnvironment = StreamExecutionEnvironment.getExecutionEnvironment
    env.setParallelism(8)

    val sourceStream: DataStream[UserBehavior] = env
      .addSource(new UserBehaviorSource("taobao/UserBehavior-20171201.csv"))
      .assignTimestampsAndWatermarks(
        WatermarkStrategy
          .forMonotonousTimestamps()
          .withTimestampAssigner(new SerializableTimestampAssigner[UserBehavior] {
            override def extractTimestamp(t: UserBehavior, l: Long): Long = t.timestamp * 1000
          })
      )

    //KeyedStream
    val keyedStream =  sourceStream.keyBy(user => user.userId)

    // KeyedStream flatMap()
    val behaviorCountStream: DataStream[(Long, String, Int)] = keyedStream.flatMap(new MapStateFunction)

    behaviorCountStream.print()
    /*Sample Results
    8> (719485,pv,1)
    8> (734984,pv,1)
    8> (792120,pv,1)
    8> (961164,pv,1)
    8> (969311,pv,1)
    8> (978954,pv,1)
     */

    env.execute("taobao map state example")
  }

  class MapStateFunction extends RichFlatMapFunction[UserBehavior, (Long, String, Int)] {

    //MapState
    private var behaviorMapState: MapState[String, Int] = _

    override def open(parameters: Configuration): Unit = {
      // StateDescriptor
      val behaviorMapStateDescriptor = new MapStateDescriptor[String, Int]("behaviorMap", classOf[String], classOf[Int])
      //StateDescriptor
      behaviorMapState = getRuntimeContext.getMapState(behaviorMapStateDescriptor)
    }

    override def flatMap(input: UserBehavior, collector: Collector[(Long, String, Int)]): Unit = {
      var behaviorCnt = 1
      // behavior pv、cart、fav、buy
      // behavior
      if (behaviorMapState.contains(input.behavior)) {
        behaviorCnt = behaviorMapState.get(input.behavior) + 1
      }
      behaviorMapState.put(input.behavior, behaviorCnt)
      collector.collect((input.userId, input.behavior, behaviorCnt))
    }
  }
}
