package stasis.server.api.handlers

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Directives.{complete, extractRequestEntity}
import akka.http.scaladsl.server.{RejectionHandler, ValidationRejection}
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import akka.util.ByteString
import org.slf4j.Logger

object Rejection {
  def create(log: Logger)(implicit mat: Materializer): RejectionHandler =
    RejectionHandler
      .newBuilder()
      .handle {
        case ValidationRejection(_, _) =>
          extractRequestEntity { entity =>
            val _ = entity.dataBytes.runWith(Sink.cancelled[ByteString])

            val message = "Provided data is invalid or malformed"
            log.warn(message)

            complete(
              StatusCodes.BadRequest,
              HttpEntity(ContentTypes.`text/plain(UTF-8)`, message)
            )
          }
      }
      .result()
      .seal
}
