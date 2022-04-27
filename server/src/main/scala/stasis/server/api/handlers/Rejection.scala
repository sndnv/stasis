package stasis.server.api.handlers

import akka.actor.typed.scaladsl.LoggerOps
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{RejectionHandler, ValidationRejection}
import org.slf4j.Logger
import stasis.core.streaming.Operators.ExtendedSource

object Rejection {
  def create(log: Logger): RejectionHandler =
    RejectionHandler
      .newBuilder()
      .handle { case ValidationRejection(rejectionMessage, _) =>
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
                HttpEntity(ContentTypes.`text/plain(UTF-8)`, message)
              )
            }
          }
        }
      }
      .result()
      .seal
}
