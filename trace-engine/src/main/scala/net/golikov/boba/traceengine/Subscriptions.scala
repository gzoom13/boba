package net.golikov.boba.traceengine

import fs2.kafka.{ ProducerRecord, ProducerRecords }
import net.golikov.boba.domain.{ Checkpoint, Next, SqlQuery, TraceContext, TraceTemplate }

import java.util.UUID

case class Subscriptions(
  requestId: UUID,
  head: (UUID, TraceContext, Checkpoint),
  queue: List[(UUID, TraceContext, Next)],
  records: ProducerRecords[Unit, UUID, (SqlQuery, TraceContext)]
) {
  def add(traceId: UUID, context: TraceContext, template: Next): Subscriptions =
    copy(queue = (traceId, context, template) +: queue)

}

object Subscriptions {
  def apply(traceId: UUID, context: TraceContext, awaitingTemplate: Checkpoint): Subscriptions = {
    val requestId = UUID.randomUUID()
    new Subscriptions(
      requestId,
      (traceId, context, awaitingTemplate),
      List(),
      ProducerRecords.one(
        ProducerRecord(
          "sql-query-actions",
          requestId,
          (awaitingTemplate match { case Checkpoint(queryAction) => queryAction match { case q: SqlQuery => q } }, context)
        )
      )
    )
  }
}
