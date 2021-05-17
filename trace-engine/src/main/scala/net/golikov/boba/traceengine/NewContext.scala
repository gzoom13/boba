package net.golikov.boba.traceengine

import net.golikov.boba.domain.TraceContext

import java.util.UUID

case class NewContext(traceId: UUID, context: TraceContext)
