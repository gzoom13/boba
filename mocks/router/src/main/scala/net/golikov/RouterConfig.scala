package net.golikov

import cats.effect.{Async, Resource}
import cats.implicits.toTraverseOps
import ciris._
import io.estatico.newtype.Coercible
import io.estatico.newtype.macros.newtype
import io.estatico.newtype.ops._
import net.golikov.RouterConfig.HttpPort

case class RouterConfig(httpPort: HttpPort)

object RouterConfig {
  @newtype case class HttpPort(value: Int)

  implicit def coercibleDecoder[A: Coercible[B, *], B: ConfigDecoder[String, *]]
      : ConfigDecoder[String, A] =
    ConfigDecoder[String, B].map(_.coerce[A])

  implicit def listDecoder[A: ConfigDecoder[String, *]]
      : ConfigDecoder[String, List[A]] =
    ConfigDecoder.lift(
      _.split(",").map(_.trim).toList.traverse(A.decode(None, _))
    )

  implicit class ConfigOps[F[_], A](cv: ConfigValue[F, A]) {
    // Same as `default` but it allows you to use the underlying type of the newtype
    def withDefault[T](value: T)(implicit
        ev: Coercible[T, A]
    ): ConfigValue[F, A] =
      cv.default(value.coerce[A])
  }

  private def value[F[_]]: ConfigValue[F, RouterConfig] =
    env("ROUTER_HTTP_PORT")
      .as[HttpPort]
      .withDefault(8081)
      .map(RouterConfig(_))

  def configR[F[_]: Async]: Resource[F, RouterConfig] = value.resource
}
