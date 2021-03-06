package net.golikov.boba.mock.router

import cats.effect.{ Async, _ }
import doobie.h2.H2Transactor
import doobie.h2.H2Transactor.newH2Transactor
import doobie.implicits.{ toSqlInterpolator, _ }
import doobie.util.ExecutionContexts._
import io.getquill.{ idiom => _ }
import RouterConfig.configR
import org.http4s.client.JavaNetClientBuilder
import org.http4s.implicits._
import org.http4s.server.blaze._
import org.http4s.{ ApiVersion => _ }
import org.http4s.client.middleware.{ Logger, RequestLogger, ResponseLogger }

object Router extends IOApp {

  def transactorR[F[_]: Async]: Resource[F, H2Transactor[F]] =
    for {
      ec <- fixedThreadPool[F](32)
      xa <- newH2Transactor("jdbc:h2:tcp://router_mock_db:1521/router", "sa", "", ec)
    } yield xa

  def run(args: List[String]): IO[ExitCode] =
    (for {
      config     <- configR[IO]
      transactor <- transactorR[IO]
    } yield (transactor, config)).use { case (xa, config) =>
      for {
        ec              <- IO.executionContext
        _               <- sql"CREATE TABLE TRANSFER(id IDENTITY PRIMARY KEY, content BINARY)".update.run.transact(xa)
        httpClient       = JavaNetClientBuilder[IO].create
        httpClientLogged = Logger.apply(logHeaders = true, logBody = true)(httpClient)
        _               <- BlazeServerBuilder[IO](ec)
                             .bindHttp(config.httpPort.value, "0.0.0.0")
                             .withHttpApp(new HttpRouterService(xa, config, httpClientLogged).routes.orNotFound)
                             .serve
                             .compile
                             .drain
      } yield ExitCode.Success
    }

}
