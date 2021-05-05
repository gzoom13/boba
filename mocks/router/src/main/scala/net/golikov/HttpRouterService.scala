package net.golikov

import cats.effect.Async
import cats.implicits._
import doobie.h2.H2Transactor
import doobie.implicits.{toDoobieStreamOps, _}
import doobie.quill.DoobieContext
import io.getquill.{SnakeCase, idiom => _}
import net.golikov.Transfer._
import org.http4s.circe.CirceEntityCodec.circeEntityEncoder
import org.http4s.dsl.Http4sDsl
import org.http4s.{HttpRoutes, ApiVersion => _}

class HttpRouterService[F[_]](xa: H2Transactor[F])(implicit F: Async[F])
    extends Http4sDsl[F] {

  val ctx = new DoobieContext.H2[SnakeCase](SnakeCase)
  import ctx._

  def all: fs2.Stream[F, Transfer] =
    ctx.stream(quote(query[Transfer])).transact(xa)

  def insert(transfer: Transfer): doobie.ConnectionIO[Option[BigDecimal]] =
    ctx.run(quote {
      query[Transfer].insert(lift(transfer)).returningGenerated(_.id)
    })

  def routes: HttpRoutes[F] = HttpRoutes.of[F] {
    case GET -> Root / "hello" / name => Ok(s"Hello $name")
    // Below is required since circe-fs2 does not work with CE3 yet
    case GET -> Root / "transfers" => Ok(all.compile.toList)
    case req @ POST -> Root / "transfers" =>
      (for {
        file <- req.body.compile.to(Array)
        _    <- insert(Transfer(None, file)).transact(xa)
      } yield ()) *> Ok()
  }
}

object HttpRouterService {
  def apply[F[_]: Async](xa: H2Transactor[F]): HttpRouterService[F] =
    new HttpRouterService[F](xa)
}
