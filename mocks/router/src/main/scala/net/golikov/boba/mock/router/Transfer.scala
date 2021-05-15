package net.golikov.boba.mock.router

import io.circe.generic.semiauto._
import io.circe.{ Decoder, Encoder }

case class Transfer(id: Option[Long], content: Array[Byte])

object Transfer {
  implicit val encoder: Encoder[Transfer] = deriveEncoder
  implicit val decoder: Decoder[Transfer] = deriveDecoder
}
