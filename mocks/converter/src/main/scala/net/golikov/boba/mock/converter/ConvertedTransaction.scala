package net.golikov.boba.mock.converter

import io.circe.generic.semiauto.{ deriveDecoder, deriveEncoder }
import io.circe.{ Decoder, Encoder }

case class ConvertedTransaction(
  id: Option[Long],
  originalTransactionId: Long,
  content: Array[Byte]
)

object ConvertedTransaction {
  implicit val encoder: Encoder[ConvertedTransaction] = deriveEncoder
  implicit val decoder: Decoder[ConvertedTransaction] = deriveDecoder
}
