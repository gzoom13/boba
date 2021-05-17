package net.golikov.boba.traceengine

import io.circe.generic.semiauto.{ deriveDecoder, deriveEncoder }
import io.circe.{ Decoder, Encoder }

case class NewTraceRequest(transferId: Long)

object NewTraceRequest {
  implicit val newTraceRequestEncoder: Encoder[NewTraceRequest] = deriveEncoder
  implicit val newTraceRequestDecoder: Decoder[NewTraceRequest] = deriveDecoder
}
