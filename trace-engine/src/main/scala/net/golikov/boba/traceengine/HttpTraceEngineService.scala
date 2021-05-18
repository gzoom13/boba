package net.golikov.boba.traceengine

import cats.effect._
import cats.effect.std.Console
import cats.implicits._
import fs2.kafka.{ KafkaProducer, ProducerRecords }
import io.circe.parser.parse
import net.golikov.boba.domain._
import net.golikov.boba.traceengine.HttpTraceEngineService._
import net.golikov.boba.traceengine.subscription.CheckpointSubscriptions
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class HttpTraceEngineService[F[+_]: Async: Console](
  producer: KafkaProducer.Metrics[F, UUID, SqlQuery],
  subscriptions: ConcurrentHashMap[UUID, CheckpointSubscriptions],
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

  def save(value: (Seq[CheckpointSubscriptions], Seq[NewContext])): F[Unit] =
    value match {
      case (allSubs, contexts) =>
        for {
          _ <- contexts.map(context => addContext(context)).sequence
          _ <- allSubs.map(subs => Sync[F].delay(subscriptions.put(subs.requestId, subs))).sequence
          _ <- producer.produce(ProducerRecords(allSubs.map(_.record))).flatten
        } yield ()
    }
}

object HttpTraceEngineService {

  val routerDb = Map(
    "jdbcUrl"  -> "jdbc:h2:tcp://router_mock_db:1521/router",
    "user"     -> "sa",
    "password" -> ""
  )

  val converterDb = Map(
    "jdbcUrl"  -> "jdbc:h2:tcp://converter_mock_db:1521/converter",
    "user"     -> "sa",
    "password" -> ""
  )

  def preconfiguredTemplate(transferId: Long): TraceTemplate =
    Fork(
      Fork(
        Checkpoint(
          SqlQueryTemplate(
            "select id as \"transferId\", UTF8TOSTRING(content) as \"originalContent\" from transfer where id = " + transferId.toString
          )
        ),
        _ =>
          Seq(
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
            )
          )
      ),
      _.map
        .get("transactionId")
        .map(tranId =>
          SqlQueryTemplate(
            "select id as \"convertedTransactionId\", " +
              "UTF8TOSTRING(content) as \"convertedContent\" " +
              "from converted_transaction where original_transaction_id = " + tranId
          )
        )
        .map(Checkpoint(_))
        .map(checkpoint => Fork(MapContext(c => TraceContext(c.map ++ converterDb)), _ => Seq(checkpoint)))
        .toList
    )

  type Results = (List[CheckpointSubscriptions], List[NewContext])
  val emptyResults: Results = (List(), List())

  def combine(r1: Results, r2: Results): Results = (r1._1 ++ r2._1, r1._2 ++ r2._2)

  def collectTrace(traceId: UUID, context: TraceContext, template: TraceTemplate): Results = {
    def collectT(context: TraceContext, template: TraceTemplate): Results =
      template match {
        case currTemplate @ Fork(head, _) =>
          collectFork(traceId, currTemplate, collectT(context, head))
        case MapContext(f)                =>
          (List(), List(NewContext(traceId, f(context))))
        case checkpoint: Checkpoint       =>
          (List(CheckpointSubscriptions.forCheckpoint(traceId, context, checkpoint)), List())
      }

    collectT(context, template)
  }

  def collectFork(
    traceId: UUID,
    currTemplate: Fork,
    headResults: Results
  ): Results =
    headResults match {
      case (headSubs, headResults) =>
        headResults
          .map(_.context)
          .map(headResult =>
            currTemplate
              .next(headResult)
              .map(template => collectTrace(traceId, headResult, template))
              .foldLeft(emptyResults)(combine)
          )
          .foldLeft(emptyResults)(combine)
          .leftMap(_ ++ headSubs.map(_.addToAllStacks(traceId, currTemplate)))
    }
}
