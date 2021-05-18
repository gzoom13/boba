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
import net.golikov.boba.domain.{ SqlQuery, SqlQueryTemplate, TraceContext }

import java.nio.charset.StandardCharsets.UTF_8
import java.util.UUID

object Pump extends IOApp {

  implicit def circeSerializer[F[_]: Sync, A: Encoder]: Serializer[F, A] =
    Serializer.lift[F, A](_.asJson.noSpaces.getBytes(UTF_8).pure[F])

  implicit def circeDeserializer[F[_]: Sync, A: Decoder]: Deserializer[F, A] =
    Deserializer.lift(bytes => decode[A](new String(bytes, UTF_8)).liftTo[F])

  def routerTransactor[F[_]: Async](jdbcUrl: String, user: String, password: String): Resource[F, H2Transactor[F]] =
    for {
      ec <- fixedThreadPool[F](2)
      xa <- newH2Transactor(jdbcUrl, user, password, ec)
    } yield xa

  override def run(args: List[String]): IO[ExitCode] = {

    def processRecord[F[_]: Async](record: ConsumerRecord[UUID, SqlQuery]): F[Seq[TraceContext]] =
      (for {
        map    <- Resource.pure(record.value.context.map)
        params <- Resource.eval(
                    (map.get("jdbcUrl"), map.get("user"), map.get("password"))
                      .tupled
                      .toRight(new IllegalArgumentException(s"Cannot find router connection parameters in $map"))
                      .liftTo[F]
                  )
        xa     <- routerTransactor[F](params._1, params._2, params._3)
        con    <- xa.connect(xa.kernel)
        ps     <- Resource.fromAutoCloseable(Sync[F].blocking(con.prepareStatement(record.value.template.sql)))
        rs     <- Resource.fromAutoCloseable(Sync[F].blocking(ps.executeQuery()))
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
      bootstrapServers <- env("KAFKA_BOOTSTRAP_SERVERS").default("kafka:9092").resource[IO]
      consumerSettings  =
        ConsumerSettings[IO, UUID, SqlQuery]
          .withAutoOffsetReset(AutoOffsetReset.Earliest)
          .withBootstrapServers(bootstrapServers)
          .withGroupId("group")
      producerSettings  =
        ProducerSettings[IO, UUID, TraceContext]
          .withBootstrapServers(bootstrapServers)
    } yield (consumerSettings, producerSettings)).use { case (consumerSettings, producerSettings) =>
      def stream(): fs2.Stream[IO, Unit] =
        KafkaConsumer
          .stream(consumerSettings)
          .evalTap(_.subscribeTo("sql-query-actions"))
          .flatMap(_.stream)
          .observe(_.printlns)
          .mapAsync(25) { committable =>
            processRecord[IO](committable.record).map(value =>
              ProducerRecords(value.map(ProducerRecord("sql-query-response", committable.record.key, _)))
            )
          }
          .observe(_.printlns)
          .through(KafkaProducer.pipe(producerSettings))
          .map(_.passthrough)

      stream().compile.drain.map(_ => ExitCode.Success)
    }

  }
}
