package net.golikov

import cats.effect.{Async, _}
import doobie.h2.H2Transactor
import doobie.h2.H2Transactor.newH2Transactor
import doobie.implicits.{toSqlInterpolator, _}
import doobie.util.ExecutionContexts._
import io.getquill.{idiom => _}
import net.golikov.RouterConfig.configR
import org.http4s.implicits._
import org.http4s.server.blaze._
import org.http4s.{ApiVersion => _}

object Router extends IOApp {

  def transactorR[F[_]: Async]: Resource[F, H2Transactor[F]] =
    for {
      ec <- fixedThreadPool[F](32)
      xa <- newH2Transactor("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1", "sa", "", ec)
    } yield xa

  def run(args: List[String]): IO[ExitCode] =
    (for {
      config     <- configR[IO]
      transactor <- transactorR[IO]
    } yield (transactor, config)).use { case (xa, config) =>
      for {
        ec <- IO.executionContext
        _ <-
          sql"CREATE TABLE TRANSFER(id IDENTITY PRIMARY KEY, content BINARY)".update.run
            .transact(xa)
        _ <- BlazeServerBuilder[IO](ec)
          .bindHttp(config.httpPort.value, "localhost")
          .withHttpApp(HttpRouterService(xa).routes.orNotFound)
          .serve
          .compile
          .drain
      } yield ExitCode.Success
    }

}
