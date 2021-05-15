package net.golikov.boba.traceengine

import cats.effect.{ ExitCode, IO, IOApp, Resource, _ }
import cats.implicits._
import fs2.kafka.{ ProducerRecords, _ }
import net.golikov.boba.domain.SqlQueryAction
import net.golikov.boba.traceengine.TraceEngineConfig.configR
import io.circe._
import io.circe.parser.decode
import io.circe.syntax._
import net.golikov.boba.domain.TraceContext

import java.util.UUID

object TraceEngine extends IOApp {

  import java.nio.charset.StandardCharsets.UTF_8

  implicit def injectSerializer[F[_]: Sync, A: Encoder: Decoder]: Serializer[F, A] =
    Serializer.lift[F, A](_.asJson.noSpaces.getBytes(UTF_8).pure[F])

  implicit def injectDeserializer[F[_]: Sync, A: Encoder: Decoder]: Deserializer[F, A] =
    Deserializer.lift(bytes => decode[A](new String(bytes, UTF_8)).liftTo[F])

  def run(args: List[String]): IO[ExitCode] =
    (for {
      config          <- configR[IO]
      producerSettings =
        ProducerSettings[IO, UUID, (SqlQueryAction, TraceContext)]
          .withBootstrapServers(config.kafkaBootstrapServers.value)
      producer        <- KafkaProducer.resource(producerSettings)
    } yield (config, producer)).use { case (config, producer) =>
      val requestId = UUID.randomUUID()
      val value     = ProducerRecord("sql-query-actions", requestId, (SqlQueryAction("select 5551"), TraceContext(Map.empty)))
      val value1    = ProducerRecords.one(value)
      for {
        _ <- producer.produce(value1).flatten
      } yield ExitCode.Success
    }

}
