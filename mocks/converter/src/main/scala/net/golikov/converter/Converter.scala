package net.golikov.converter

import cats.effect.{ Async, _ }
import doobie.h2.H2Transactor
import doobie.h2.H2Transactor.newH2Transactor
import doobie.implicits.{ toSqlInterpolator, _ }
import doobie.util.ExecutionContexts._
import io.getquill.{ idiom => _ }
import ConverterConfig.configR
import org.http4s.implicits._
import org.http4s.server.blaze._
import org.http4s.server.middleware.RequestLogger
import org.http4s.{ ApiVersion => _ }

object Converter extends IOApp {

  def transactorR[F[_]: Async]: Resource[F, H2Transactor[F]] =
    for {
      ec <- fixedThreadPool[F](32)
      xa <- newH2Transactor("jdbc:h2:mem:converter;DB_CLOSE_DELAY=-1", "sa", "", ec)
    } yield xa

  def run(args: List[String]): IO[ExitCode] =
    (for {
      config     <- configR[IO]
      transactor <- transactorR[IO]
    } yield (transactor, config)).use { case (xa, config) =>
      for {
        ec            <- IO.executionContext
        _             <- sql"""CREATE TABLE CONVERTED_TRANSACTION(
                      id IDENTITY PRIMARY KEY,
                      original_transaction_id BIGINT,
                      content BINARY
                    )""".update.run.transact(xa)
        httpApp        = new HttpConverterService(xa).routes.orNotFound
        httpAppLogging = RequestLogger.httpApp(logHeaders = true, logBody = true)(httpApp)
        _             <- BlazeServerBuilder[IO](ec)
                           .bindHttp(config.httpPort.value, "0.0.0.0")
                           .withHttpApp(httpAppLogging)
                           .serve
                           .compile
                           .drain
      } yield ExitCode.Success
    }

}
