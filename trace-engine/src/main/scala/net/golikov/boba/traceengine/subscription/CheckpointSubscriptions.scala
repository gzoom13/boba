package net.golikov.boba.traceengine.subscription

import fs2.kafka.{ ProducerRecord, ProducerRecords }
import net.golikov.boba.domain.{ Checkpoint, Next, SqlQuery, TraceContext }

import java.util.UUID

case class CheckpointSubscriptions(
  requestId: UUID,
  source: AwaitedCheckpoint,
  queue: List[WaitingStage],
  records: ProducerRecords[Unit, UUID, (SqlQuery, TraceContext)]
) {
  def add(traceId: UUID, context: TraceContext, template: Next): CheckpointSubscriptions =
    copy(queue = WaitingStage(traceId, context, template) +: queue)
}

object CheckpointSubscriptions {
  def forCheckpoint(traceId: UUID, context: TraceContext, awaitingTemplate: Checkpoint): CheckpointSubscriptions = {
    val requestId = UUID.randomUUID()
    CheckpointSubscriptions(
      requestId,
      AwaitedCheckpoint(traceId, context, awaitingTemplate),
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

case class AwaitedCheckpoint(traceId: UUID, context: TraceContext, checkpoint: Checkpoint)
case class WaitingStage(traceId: UUID, context: TraceContext, waiting: Next)
