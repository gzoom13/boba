package net.golikov.boba.traceengine

import cats.implicits._
import net.golikov.boba.domain._
import net.golikov.boba.traceengine.HttpTraceEngineService._
import org.scalatest.Inside.inside
import org.scalatest._
import org.scalatest.flatspec._
import org.scalatest.matchers._

import java.util.UUID

class TraceEngineSpec extends AnyFlatSpec with should.Matchers {

  it should "maps values" in {
    val template = MapContext(c => c.copy(map = c.map ++ Map("test1" -> "v2")))
    val traceId  = UUID.randomUUID
    val res      = collectTrace(traceId, TraceContext(Map("test1" -> "v1", "test2" -> "v1")), template)
    res shouldEqual Right(Some(NewContext(traceId, TraceContext(Map("test1" -> "v2", "test2" -> "v1")))))
  }

  it should "maps after next" in {
    val template =
      Next(MapContext(c => c.copy(map = c.map ++ Map("test1" -> "v2"))), _ => MapContext(c => c.copy(map = c.map ++ Map("test2" -> "v2"))).some)
    val traceId  = UUID.randomUUID
    val res      = collectTrace(traceId, TraceContext(Map("test1" -> "v1", "test2" -> "v1")), template)
    res shouldEqual Right(Some(NewContext(traceId, TraceContext(Map("test1" -> "v2", "test2" -> "v2")))))
  }

  it should "returns subscribes" in {
    val sql      = "some sql query"
    val template = Next(MapContext(c => c.copy(map = c.map ++ Map("test1" -> "v2"))), _ => Checkpoint(SqlQuery(sql)).some)
    val traceId  = UUID.randomUUID
    val res      = collectTrace(traceId, TraceContext(Map("test1" -> "v1", "test2" -> "v1")), template)
    inside(res) { case Left(Subscriptions(_, (actualTraceId, TraceContext(actualMap), Checkpoint(SqlQuery(actualSql))), queue, _)) =>
      queue shouldBe empty
      actualTraceId shouldEqual traceId
      actualSql shouldEqual sql
      actualMap shouldEqual Map("test1" -> "v2", "test2" -> "v1")
    }
  }
}
