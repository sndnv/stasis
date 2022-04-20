package stasis.server.api.handlers

import akka.actor.typed.scaladsl.LoggerOps
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{RejectionHandler, ValidationRejection}
import akka.stream.scaladsl.Sink
import akka.util.ByteString
import org.slf4j.Logger

object Rejection {
  def create(log: Logger): RejectionHandler =
    RejectionHandler
      .newBuilder()
      .handle { case ValidationRejection(rejectionMessage, _) =>
        extractRequest { request =>
          extractActorSystem { implicit system =>
            val _ = request.entity.dataBytes.runWith(Sink.cancelled[ByteString])

            val message = s"Provided data is invalid or malformed: [$rejectionMessage]"

            log.warnN(
              "[{}] request for [{}] rejected: [{}]",
              request.method.value,
              request.uri.path.toString,
              message
            )

            complete(
              StatusCodes.BadRequest,
              HttpEntity(ContentTypes.`text/plain(UTF-8)`, message)
            )
          }
        }
      }
      .result()
      .seal
}
