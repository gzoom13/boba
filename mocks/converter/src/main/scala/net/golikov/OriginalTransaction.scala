package net.golikov

import io.circe.{ Decoder, Encoder }
import io.circe.generic.semiauto._

case class OriginalTransaction(id: Option[Long], content: Array[Byte])

object OriginalTransaction {
  implicit val encoder: Encoder[OriginalTransaction] = deriveEncoder
  implicit val decoder: Decoder[OriginalTransaction] = deriveDecoder
}
