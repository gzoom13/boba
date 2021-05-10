package net.golikov

import cats.effect.Async
import cats.free.Trampoline
import cats.implicits._
import doobie.h2.H2Transactor
import doobie.implicits.{toDoobieStreamOps, _}
import doobie.quill.DoobieContext
import io.circe._
import io.circe.parser._
import io.getquill.{SnakeCase, idiom => _}
import org.http4s.circe.CirceEntityCodec.circeEntityEncoder
import org.http4s.dsl.Http4sDsl
import org.http4s.{HttpRoutes, ApiVersion => _}

import scala.Array.emptyByteArray

class HttpConverterService[F[_]](xa: H2Transactor[F])(implicit F: Async[F]) extends Http4sDsl[F] {

  val ctx = new DoobieContext.H2[SnakeCase](SnakeCase)
  import ctx._

  def transactions: fs2.Stream[F, ConvertedTransaction] =
    ctx.stream(quote(query[ConvertedTransaction])).transact(xa)

  def insert(transfer: ConvertedTransaction): doobie.ConnectionIO[Long] =
    ctx
      .run(quote {
        query[ConvertedTransaction].insert(lift(transfer)).returningGenerated(_.id)
      })
      .map(_.get)

  def updateContent(id: Long, content: Array[Byte]): doobie.ConnectionIO[Long] =
    ctx.run(quote {
      query[ConvertedTransaction].filter(_.id.contains(lift(id))).update(_.content -> lift(content))
    })

  def routes: HttpRoutes[F] =
    HttpRoutes.of[F] {
      // Below is required since circe-fs2 does not work with CE3 yet
      case GET -> Root / "transactions"        => Ok(transactions.compile.toList)
      case req @ POST -> Root / "transactions" =>
        (for {
          content      <- req.body.through(fs2.text.utf8Decode).compile.string
          origTran     <- F.fromEither(parse(content))
          origTranId   <- F.fromEither(origTran.hcursor.downField("id").as[Long])
          newTranId    <- insert(ConvertedTransaction(None, origTranId, emptyByteArray)).transact(xa)
          convertedTran = convert(origTran, newTranId)
          _            <- updateContent(newTranId, convertedTran.noSpaces.getBytes).transact(xa)
        } yield ()) *> Created()
    }

  private def convert(orig: Json, newTransactionId: Long): Json = {
    val changeId = orig.mapObject(j => j.deepMerge(JsonObject("id" -> Json.fromLong(newTransactionId))))
    transformKeys(changeId, _ + "_converted").run
  }

  private def transformObjectKeys(obj: JsonObject, f: String => String): JsonObject =
    JsonObject.fromIterable(obj.toList.map { case (k, v) => f(k) -> v })

  private def transformKeys(json: Json, f: String => String): Trampoline[Json]      =
    json.arrayOrObject(
      Trampoline.done(json),
      _.traverse(j => Trampoline.defer(transformKeys(j, f))).map(Json.fromValues),
      transformObjectKeys(_, f).traverse(obj => Trampoline.defer(transformKeys(obj, f))).map(Json.fromJsonObject)
    )
}
