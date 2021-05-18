package net.golikov.boba.traceengine

import cats.effect.{ ExitCode, IO, IOApp, _ }
import cats.implicits._
import fs2.kafka._
import io.circe._
import io.circe.parser.decode
import io.circe.syntax._
import net.golikov.boba.domain.{ SqlQuery, TraceContext }
import net.golikov.boba.traceengine.HttpTraceEngineService._
import net.golikov.boba.traceengine.TraceEngineConfig.configR
import net.golikov.boba.traceengine.subscription.{ AwaitedCheckpoint, CheckpointSubscriptions, WaitingStage }
import org.http4s.implicits.http4sKleisliResponseSyntaxOptionT
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.Logger

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object TraceEngine extends IOApp {

  import java.nio.charset.StandardCharsets.UTF_8

  implicit def circeSerializer[F[_]: Sync, A: Encoder]: Serializer[F, A] =
    Serializer.lift[F, A](_.asJson.noSpaces.getBytes(UTF_8).pure[F])

  implicit def circeDeserializer[F[_]: Sync, A: Decoder]: Deserializer[F, A] =
    Deserializer.lift(bytes => decode[A](new String(bytes, UTF_8)).liftTo[F])

  def run(args: List[String]): IO[ExitCode] =
    (for {
      config          <- configR[IO]
      producerSettings =
        ProducerSettings[IO, UUID, SqlQuery]
          .withBootstrapServers(config.kafkaBootstrapServers.value)
      consumerSettings =
        ConsumerSettings[IO, UUID, TraceContext]
          .withAutoOffsetReset(AutoOffsetReset.Earliest)
          .withBootstrapServers(config.kafkaBootstrapServers.value)
          .withGroupId("engine")
      producer        <- KafkaProducer.resource(producerSettings)
      subscriptions   <- Resource.pure(new ConcurrentHashMap[UUID, CheckpointSubscriptions]())
      traceStorage    <- Resource.pure(new ConcurrentHashMap[UUID, Seq[TraceContext]]())
    } yield (config, producer, consumerSettings, subscriptions, traceStorage)).use {
      case (config, producer, consumerSettings, subscriptions, traceStorage) =>
        for {
          ec           <- IO.executionContext
          service       = new HttpTraceEngineService[IO](producer, subscriptions, traceStorage)
          httpApp       = service.routes.orNotFound
          httpAppLogged = Logger.httpApp(logHeaders = true, logBody = true)(httpApp)
          _            <- fs2
                            .Stream(
                              KafkaConsumer
                                .stream(consumerSettings)
                                .evalTap(_.subscribeTo("sql-query-response"))
                                .flatMap(_.stream)
                                .observe(_.printlns)
                                .mapAsync(25)(committable =>
                                  IO.delay(Option(subscriptions.get(committable.record.key))).flatMap {
                                    case Some(CheckpointSubscriptions(_, checkpoint @ AwaitedCheckpoint(traceId, TraceContext(oldMap), _), stacks, _)) =>
                                      for {
                                        received  <- IO.pure(committable.record.value)
                                        newContext = NewContext(traceId, TraceContext(oldMap ++ received.map))
                                        _         <- service.addContext(newContext)
                                        _         <- IO.println(s"Processing head $checkpoint and stacks $stacks")
                                        results    =
                                          stacks
                                            .map(_.stack)
                                            .map(stack =>
                                              stack
                                                .foldRight(emptyResults.map(_ => List(newContext))) { case (WaitingStage(traceId, fork), headResults) =>
                                                  combine(collectFork(traceId, fork, headResults), headResults)
                                                }
                                            )
                                        _         <- results.map(service.save).sequence
                                      } yield ()
                                    case None                                                                                                          =>
                                      IO.unit
                                  }
                                ),
                              BlazeServerBuilder[IO](ec)
                                .bindHttp(config.httpPort.value, "0.0.0.0")
                                .withHttpApp(httpAppLogged)
                                .serve
                            )
                            .parJoinUnbounded
                            .compile
                            .drain
        } yield ExitCode.Success
    }

}
