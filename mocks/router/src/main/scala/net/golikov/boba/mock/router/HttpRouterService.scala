package net.golikov.boba.mock.router

import cats.effect.Async
import cats.implicits._
import doobie.h2.H2Transactor
import doobie.implicits.{ toDoobieStreamOps, _ }
import doobie.quill.DoobieContext
import io.getquill.{ SnakeCase, idiom => _ }
import org.http4s.Uri.Authority
import org.http4s.Uri.Scheme.http
import org.http4s.circe.CirceEntityCodec.circeEntityEncoder
import org.http4s.client.{ Client, UnexpectedStatus }
import org.http4s.dsl.Http4sDsl
import org.http4s.{ EntityEncoder, HttpRoutes, Request, Uri, ApiVersion => _ }

class HttpRouterService[F[_]](xa: H2Transactor[F], config: RouterConfig, httpClient: Client[F])(implicit F: Async[F]) extends Http4sDsl[F] {

  private val converterUri =
    Uri(scheme = http.some, authority = Authority(host = Uri.RegName("converter_mock"), port = config.converterPort.value.some).some) / "transactions"

  private val ctx = new DoobieContext.H2[SnakeCase](SnakeCase)
  import ctx._

  def all: fs2.Stream[F, Transfer] =
    ctx.stream(quote(query[Transfer])).transact(xa)

  def insert(transfer: Transfer): doobie.ConnectionIO[Transfer] =
    ctx
      .run(quote {
        query[Transfer].insert(lift(transfer)).returningGenerated(_.id)
      })
      .map(id => transfer.copy(id = id))

  def routes: HttpRoutes[F] = HttpRoutes.of[F] {
    // Below is required since circe-fs2 does not work with CE3 yet
    case GET -> Root / "transfers"        => Ok(all.compile.toList)
    case req @ POST -> Root / "transfers" =>
      (for {
        file        <- req.body.compile.to(Array)
        _           <- insert(Transfer(None, file)).transact(xa)
        converterReq = Request[F](method = POST, uri = converterUri).withEntity(new String(file))(EntityEncoder.stringEncoder)
        status      <- httpClient.status(converterReq)
        _           <- if (status.isSuccess) F.unit
                       else F.raiseError(UnexpectedStatus(status, converterReq.method, converterReq.uri))
      } yield ()) *> Created()
  }
}
