package net.golikov.boba.traceengine

import cats.effect.{ Async, Resource }
import cats.implicits.{ catsSyntaxTuple2Parallel, toTraverseOps }
import ciris.{ env, _ }
import io.estatico.newtype.Coercible
import io.estatico.newtype.macros.newtype
import io.estatico.newtype.ops._
import net.golikov.boba.traceengine.TraceEngineConfig.{ HttpPort, KafkaBootstrapServers }

case class TraceEngineConfig(httpPort: HttpPort, kafkaBootstrapServers: KafkaBootstrapServers)

object TraceEngineConfig {
  @newtype case class HttpPort(value: Int)
  @newtype case class KafkaBootstrapServers(value: String)

  implicit def coercibleDecoder[A: Coercible[B, *], B: ConfigDecoder[String, *]]: ConfigDecoder[String, A] =
    ConfigDecoder[String, B].map(_.coerce[A])

  implicit def listDecoder[A: ConfigDecoder[String, *]]: ConfigDecoder[String, List[A]] =
    ConfigDecoder.lift(_.split(",").map(_.trim).toList.traverse(A.decode(None, _)))

  implicit class ConfigOps[F[_], A](cv: ConfigValue[F, A]) {
    // Same as `default` but it allows you to use the underlying type of the newtype
    def withDefault[T](value: T)(implicit ev: Coercible[T, A]): ConfigValue[F, A] =
      cv.default(value.coerce[A])
  }

  private def value[F[_]]: ConfigValue[F, TraceEngineConfig] =
    (
      env("TRACE_ENGINE_HTTP_PORT").as[HttpPort].withDefault(8083),
      env("KAFKA_BOOTSTRAP_SERVERS").as[KafkaBootstrapServers].withDefault("kafka:9092")
    ).parMapN(TraceEngineConfig.apply)

  def configR[F[_]: Async]: Resource[F, TraceEngineConfig] = value.resource
}
