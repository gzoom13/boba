package net.golikov

import io.circe.{ Decoder, Encoder }
import io.circe.generic.semiauto._

case class Transfer(id: Option[Long], content: Array[Byte])

object Transfer {
  implicit val encoder: Encoder[Transfer] = deriveEncoder
  implicit val decoder: Decoder[Transfer] = deriveDecoder
}
