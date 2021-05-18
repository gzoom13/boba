package net.golikov.boba.traceengine

import cats.effect._
import cats.effect.std.Console
import cats.implicits._
import fs2.kafka.KafkaProducer
import io.circe.parser.parse
import net.golikov.boba.domain._
import net.golikov.boba.traceengine.HttpTraceEngineService._
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class HttpTraceEngineService[F[+_]: Async: Console](
  producer: KafkaProducer.Metrics[F, UUID, (SqlQuery, TraceContext)],
  subscriptions: ConcurrentHashMap[UUID, Subscriptions],
  traceStorage: ConcurrentHashMap[UUID, Seq[TraceContext]]
) extends Http4sDsl[F] {

  def addContext(newContext: NewContext): F[Seq[TraceContext]] =
    Sync[F].pure(
      traceStorage.compute(newContext.traceId, (_, contexts) => if (contexts == null) Seq(newContext.context) else contexts :+ newContext.context)
    ) <* Console[F].println(traceStorage)

  import org.http4s.circe.CirceEntityCodec._

  import scala.jdk.CollectionConverters._

  def routes: HttpRoutes[F] = HttpRoutes.of[F] {
    case GET -> Root / "traces"        => Ok(traceStorage.asScala.map { case (uuid, value) => (uuid.toString, value) })
    case req @ POST -> Root / "traces" =>
      (for {
        traceRequest  <- req.as[NewTraceRequest]
        traceId        = UUID.randomUUID()
        initialContext = TraceContext(routerDb)
        _             <- addContext(NewContext(traceId, initialContext))
        value          = collectTrace(traceId, initialContext, preconfiguredTemplate(traceRequest.transferId))
        _             <- save(value)
      } yield ()) *> Created()
  }

  def save(value: Either[Subscriptions, Option[NewContext]]): F[Either[Subscriptions, Seq[TraceContext]]] =
    value match {
      case Left(subs)           =>
        Sync[F]
          .delay(subscriptions.put(subs.requestId, subs))
          .flatTap(_ => Sync[F].delay(subscriptions.get(subs.requestId)).map(_.records).flatMap(producer.produce(_).flatten))
          .map(_.asLeft)
      case Right(Some(context)) => addContext(context).map(_.asRight)
      case Right(None)          => Sync[F].pure(Seq().asRight)
    }
}

object HttpTraceEngineService {

  val routerDb = Map(
    "jdbcUrl"  -> "jdbc:h2:tcp://router_mock_db:1521/router",
    "user"     -> "sa",
    "password" -> ""
  )

  val converterDb = Map(
    "jdbcUrl"  -> "jdbc:h2:tcp://converter_mock_db:1521/router",
    "user"     -> "sa",
    "password" -> ""
  )

  def preconfiguredTemplate(transferId: Long): TraceTemplate =
    Next(
      Next(
        Checkpoint(
          SqlQuery("select id as \"transactionId\", UTF8TOSTRING(content) as \"originalContent\" from transfer where id = " + transferId.toString)
        ),
        _ =>
          MapContext(c =>
            c.map
              .get("originalContent")
              .toRight(new IllegalArgumentException("Field \"originalContent\" not found"))
              .flatMap(parse)
              .flatMap(_.hcursor.downField("id").as[Long])
              .fold(
                error => c.copy(map = c.map + ("error" -> error.getMessage)),
                transactionId => c.copy(map = c.map + ("transactionId" -> transactionId.toString))
              )
          ).some
      ),
      _.map
        .get("transactionId")
        .map(tranId =>
          SqlQuery(
            "select id as \"convertedTransactionId\", " +
              "UTF8TOSTRING(content) as \"convertedContent\" " +
              "from converted_transaction where original_transaction_id = " + tranId
          )
        )
        .map(Checkpoint(_))
        .map(checkpoint => Next(MapContext(c => TraceContext(c.map ++ converterDb)), _ => Some(checkpoint)))
    )

  def collectTrace(traceId: UUID, context: TraceContext, template: TraceTemplate): Either[Subscriptions, Option[NewContext]] = {
    def collectT(context: TraceContext, template: TraceTemplate): Either[Subscriptions, Option[NewContext]] =
      template match {
        case currTemplate @ Next(head, next) =>
          collectT(context, head) match {
            case Left(subs)     => subs.add(traceId, context, currTemplate).asLeft
            case Right(results) =>
              results
                .map(_.context)
                .map(headResult => next(headResult).map(template => collectT(headResult, template)).flatSequence)
                .flatSequence
          }
        case MapContext(f)                   => Some(NewContext(traceId, f(context))).asRight
        case checkpoint: Checkpoint          => Subscriptions(traceId, context, checkpoint).asLeft
      }

    collectT(context, template)
  }

}
