package stasis.server.api.handlers

import akka.actor.typed.scaladsl.LoggerOps
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{RejectionHandler, ValidationRejection}
import org.slf4j.Logger
import stasis.core.api.MessageResponse
import stasis.core.streaming.Operators.ExtendedSource

object Rejection {
  def create(log: Logger): RejectionHandler =
    RejectionHandler
      .newBuilder()
      .handle { case ValidationRejection(rejectionMessage, _) =>
        import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
        import stasis.core.api.Formats.messageResponseFormat

        extractRequest { request =>
          extractActorSystem { implicit system =>
            val message = s"Provided data is invalid or malformed: [$rejectionMessage]"

            log.warnN(
              "[{}] request for [{}] rejected: [{}]",
              request.method.value,
              request.uri.path.toString,
              message
            )

            onSuccess(request.entity.dataBytes.cancelled()) { _ =>
              complete(
                StatusCodes.BadRequest,
                MessageResponse(message)
              )
            }
          }
        }
      }
      .result()
      .seal
}
