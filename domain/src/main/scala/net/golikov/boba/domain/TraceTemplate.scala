package net.golikov.boba.domain

import io.circe.{ Decoder, Encoder }
import io.circe.generic.semiauto.{ deriveDecoder, deriveEncoder }

import java.time.OffsetDateTime

sealed trait TraceTemplate

case class Next(head: TraceTemplate, next: TraceContext => Option[TraceTemplate]) extends TraceTemplate
case class Checkpoint(queryAction: QueryTemplate)                                 extends TraceTemplate
case class MapContext(f: TraceContext => TraceContext)                            extends TraceTemplate

sealed trait Action

sealed trait QueryTemplate extends Action

case class SqlQueryTemplate(sql: String) extends QueryTemplate

object SqlQueryTemplate {
  implicit val sqlQueryActionEncoder: Encoder[SqlQueryTemplate] = deriveEncoder
  implicit val sqlQueryActionDecoder: Decoder[SqlQueryTemplate] = deriveDecoder
}

case class SqlQuery(template: SqlQueryTemplate, context: TraceContext)

object SqlQuery {
  implicit val sqlQueryActionEncoder: Encoder[SqlQuery] = deriveEncoder
  implicit val sqlQueryActionDecoder: Decoder[SqlQuery] = deriveDecoder
}

case class TraceContext(map: Map[String, String])

object TraceContext {
  implicit val traceEncoder: Encoder[TraceContext] = deriveEncoder
  implicit val traceDecoder: Decoder[TraceContext] = deriveDecoder
}
