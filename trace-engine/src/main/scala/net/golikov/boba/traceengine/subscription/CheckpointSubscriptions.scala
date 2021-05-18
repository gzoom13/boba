package net.golikov.boba.traceengine.subscription

import fs2.kafka.ProducerRecord
import net.golikov.boba.domain.{ Checkpoint, Fork, SqlQuery, SqlQueryTemplate, TraceContext }

import java.util.UUID

case class CheckpointSubscriptions(
  requestId: UUID,
  source: AwaitedCheckpoint,
  stacks: Seq[CheckpointSubscriptionStack],
  record: ProducerRecord[UUID, SqlQuery]
) {
  def addToAllStacks(traceId: UUID, template: Fork): CheckpointSubscriptions =
    copy(stacks = stacks.map(_.add(traceId, template)))
}

object CheckpointSubscriptions {
  def forCheckpoint(traceId: UUID, context: TraceContext, awaitingTemplate: Checkpoint): CheckpointSubscriptions = {
    val requestId = UUID.randomUUID()
    CheckpointSubscriptions(
      requestId,
      AwaitedCheckpoint(traceId, context, awaitingTemplate),
      Seq(CheckpointSubscriptionStack(List())),
      ProducerRecord(
        "sql-query-actions",
        requestId,
        SqlQuery(awaitingTemplate match { case Checkpoint(sqlTemplate: SqlQueryTemplate) => sqlTemplate }, context)
      )
    )
  }
}
