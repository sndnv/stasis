package stasis.server.api.handlers

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
        extractRequestEntity { entity =>
          extractActorSystem { implicit system =>
            val _ = entity.dataBytes.runWith(Sink.cancelled[ByteString])

            val message = s"Provided data is invalid or malformed: [$rejectionMessage]"
            log.warn(message)

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
