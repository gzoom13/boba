package net.golikov.boba.domain

import io.circe.{ Decoder, Encoder }
import io.circe.generic.semiauto.{ deriveDecoder, deriveEncoder }

import java.time.OffsetDateTime

sealed trait Trace

case class CompositeTrace(parent: Trace, children: Seq[Trace])                extends Trace
case class ConditionalTrace(trace: Trace, predicate: TraceContext => Boolean) extends Trace

sealed trait Action extends Trace

sealed trait QueryAction extends Action

case class SqlQueryAction(sql: String) extends QueryAction

object SqlQueryAction {
  implicit val eventEncoder: Encoder[SqlQueryAction] = deriveEncoder
  implicit val eventDecoder: Decoder[SqlQueryAction] = deriveDecoder
}

case class Span(operationName: String, startTimestamp: OffsetDateTime, finishTimestamp: OffsetDateTime, spanContext: TraceContext)

case class TraceContext(map: Map[String, String])

object TraceContext {
  implicit val eventEncoder: Encoder[TraceContext] = deriveEncoder
  implicit val eventDecoder: Decoder[TraceContext] = deriveDecoder
}
