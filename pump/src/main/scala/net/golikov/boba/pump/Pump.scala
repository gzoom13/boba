package net.golikov.boba.pump

import cats.effect.{ ExitCode, IO, IOApp, _ }
import cats.implicits._
import ciris.env
import doobie.h2.H2Transactor
import doobie.h2.H2Transactor.newH2Transactor
import doobie.util.ExecutionContexts.fixedThreadPool
import fs2.kafka._
import io.circe.parser.decode
import io.circe.syntax._
import io.circe.{ Decoder, Encoder }
import net.golikov.boba.domain.{ SqlQueryAction, TraceContext }

import java.nio.charset.StandardCharsets.UTF_8
import java.util.UUID

object Pump extends IOApp {

  implicit def injectSerializer[F[_]: Sync, A: Encoder: Decoder]: Serializer[F, A] =
    Serializer.lift[F, A](_.asJson.noSpaces.getBytes(UTF_8).pure[F])

  implicit def injectDeserializer[F[_]: Sync, A: Encoder: Decoder]: Deserializer[F, A] =
    Deserializer.lift(bytes => decode[A](new String(bytes, UTF_8)).liftTo[F])

  def routerTransactor[F[_]: Async]: Resource[F, H2Transactor[F]] =
    for {
      ec <- fixedThreadPool[F](2)
      xa <- newH2Transactor("jdbc:h2:tcp://router_mock_db:1521/router", "sa", "", ec)
    } yield xa

  override def run(args: List[String]): IO[ExitCode] = {

    def processRecord[F[_]: Sync](xa: H2Transactor[F], record: ConsumerRecord[UUID, (SqlQueryAction, TraceContext)]): F[Seq[TraceContext]] =
      (for {
        con <- xa.connect(xa.kernel)
        ps  <- Resource.fromAutoCloseable(Sync[F].blocking(con.prepareStatement(record.value._1.sql)))
        rs  <- Resource.fromAutoCloseable(Sync[F].blocking(ps.executeQuery()))
      } yield rs).use { rs =>
        for {
          metadata    <- Sync[F].blocking(rs.getMetaData)
          columnCount <- Sync[F].blocking(metadata.getColumnCount)
          contexts    <- (Vector[TraceContext](), rs)
                           .iterateWhileM[F] { case (contexts, rs) =>
                             (1 to columnCount)
                               .map(i => (Sync[F].blocking(metadata.getColumnName(i)), Sync[F].blocking(rs.getString(i))))
                               .map(_.tupled)
                               .foldLeft(Sync[F].pure(Map.empty[String, String]))((m, t) => (m, t).mapN(_ + _))
                               .map(TraceContext.apply)
                               .map(contexts :+ _)
                               .map((_, rs))
                           } { case (_, set) => set.next() }
                           .map(_._1)
        } yield contexts
      }

    (for {
      xa               <- routerTransactor[IO]
      bootstrapServers <- env("KAFKA_BOOTSTRAP_SERVERS").default("kafka:9092").resource[IO]
      consumerSettings  =
        ConsumerSettings[IO, UUID, (SqlQueryAction, TraceContext)]
          .withAutoOffsetReset(AutoOffsetReset.Earliest)
          .withBootstrapServers(bootstrapServers)
          .withGroupId("group")
      producerSettings  =
        ProducerSettings[IO, UUID, TraceContext]
          .withBootstrapServers(bootstrapServers)
    } yield (consumerSettings, producerSettings, xa)).use { case (consumerSettings, producerSettings, xa) =>
      def stream(xa: H2Transactor[IO]): fs2.Stream[IO, Unit] =
        KafkaConsumer
          .stream(consumerSettings)
          .evalTap(_.subscribeTo("sql-query-actions"))
          .flatMap(_.stream)
          .observe(_.printlns)
          .mapAsync(25) { committable =>
            processRecord(xa, committable.record).map(value =>
              ProducerRecords(value.map(ProducerRecord("sql-query-response", committable.record.key, _))))
          }
          .observe(_.printlns)
          .through(KafkaProducer.pipe(producerSettings))
          .map(_.passthrough)

      stream(xa).compile.drain.map(_ => ExitCode.Success)
    }

  }
}
