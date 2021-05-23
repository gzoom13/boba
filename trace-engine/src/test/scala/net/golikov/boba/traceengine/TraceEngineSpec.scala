package net.golikov.boba.traceengine

import net.golikov.boba.domain._
import net.golikov.boba.traceengine.HttpTraceEngineService._
import net.golikov.boba.traceengine.subscription.{ AwaitedCheckpoint, CheckpointSubscriptionStack, CheckpointSubscriptions, WaitingStage }
import org.scalatest.Inside.inside
import org.scalatest.flatspec._
import org.scalatest.matchers._

import java.util.UUID

class TraceEngineSpec extends AnyFlatSpec with should.Matchers {

  it should "map values" in {
    val template = MapContext(c => c.copy(map = c.map ++ Map("test1" -> "v2")))
    val traceId  = UUID.randomUUID
    val res      = collectTrace(traceId, TraceContext(Map("test1" -> "v1", "test2" -> "v1")), template).run
    res shouldEqual (List(), List(NewContext(traceId, TraceContext(Map("test1" -> "v2", "test2" -> "v1")))))
  }

  it should "map after next" in {
    val template =
      Fork(MapContext(c => c.copy(map = c.map ++ Map("test1" -> "v2"))), _ => Seq(MapContext(c => c.copy(map = c.map ++ Map("test2" -> "v2")))))
    val traceId  = UUID.randomUUID
    val res      = collectTrace(traceId, TraceContext(Map("test1" -> "v1", "test2" -> "v1")), template).run
    res shouldEqual (List(), List(NewContext(traceId, TraceContext(Map("test1" -> "v2", "test2" -> "v2")))))
  }

  it should "return empty subscriptions" in {
    val sql      = "some sql query"
    val template = Fork(MapContext(c => c.copy(map = c.map ++ Map("test1" -> "v2"))), _ => Seq(Checkpoint(SqlQueryTemplate(sql))))
    val traceId  = UUID.randomUUID
    val res      = collectTrace(traceId, TraceContext(Map("test1" -> "v1", "test2" -> "v1")), template).run
    inside(res) {
      case (
            CheckpointSubscriptions(
              _,
              AwaitedCheckpoint(actualTraceId, TraceContext(actualMap), Checkpoint(SqlQueryTemplate(actualSql))),
              CheckpointSubscriptionStack(stack) :: Nil,
              _
            ) :: Nil,
            List()
          ) =>
        stack shouldBe empty
        actualTraceId shouldEqual traceId
        actualSql shouldEqual sql
        actualMap shouldEqual Map("test1" -> "v2", "test2" -> "v1")
    }
  }

  it should "subscribe to head" in {
    val sql        = "some sql query"
    val template   = Fork(Checkpoint(SqlQueryTemplate(sql)), _ => Seq())
    val traceId    = UUID.randomUUID
    val initialMap = Map("test1" -> "v1", "test2" -> "v1")
    val res        = collectTrace(traceId, TraceContext(initialMap), template).run
    inside(res) {
      case (
            CheckpointSubscriptions(
              _,
              AwaitedCheckpoint(actualTraceId, TraceContext(actualMap), Checkpoint(SqlQueryTemplate(actualSql))),
              stacks,
              _
            ) :: Nil,
            List()
          ) =>
        actualTraceId shouldEqual traceId
        actualSql shouldEqual sql
        actualMap shouldEqual initialMap

        inside(stacks) { case CheckpointSubscriptionStack(WaitingStage(actualTraceIdFork, waitingFork) :: Nil) :: Nil =>
          actualTraceIdFork shouldEqual traceId
          waitingFork shouldEqual template
        }
    }
  }

  it should "not overflow stack" in {
    val sql        = "some sql query"
    val template   = (0 to 100_000).foldLeft(Fork(MapContext(identity), _ => Seq()))((fork, _) => Fork(fork, _ => Seq()))
    val traceId    = UUID.randomUUID
    val initialMap = Map("test1" -> "v1", "test2" -> "v1")
    val res        = collectTrace(traceId, TraceContext(initialMap), template).run
  }

}
