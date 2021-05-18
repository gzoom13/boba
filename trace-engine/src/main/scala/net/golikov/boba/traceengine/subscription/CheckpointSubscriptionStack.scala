package net.golikov.boba.traceengine.subscription

import net.golikov.boba.domain.{ Checkpoint, Fork, TraceContext }

import java.util.UUID

case class CheckpointSubscriptionStack(stack: List[WaitingStage]) {
  def add(traceId: UUID, template: Fork): CheckpointSubscriptionStack =
    copy(stack = WaitingStage(traceId, template) +: stack)
}

case class AwaitedCheckpoint(traceId: UUID, context: TraceContext, checkpoint: Checkpoint)
case class WaitingStage(traceId: UUID, waiting: Fork)
