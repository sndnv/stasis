package stasis.server.api.handlers

import akka.actor.typed.scaladsl.LoggerOps
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.ExceptionHandler
import org.slf4j.Logger
import stasis.core.streaming.Operators.ExtendedSource
import stasis.server.security.exceptions.AuthorizationFailure

import scala.util.control.NonFatal

object Sanitizing {
  def create(log: Logger): ExceptionHandler =
    ExceptionHandler {
      case e: AuthorizationFailure =>
        extractRequestEntity { entity =>
          extractActorSystem { implicit system =>
            log.errorN("User authorization failed: [{} - {}]", e.getClass.getSimpleName, e.getMessage)
            onSuccess(entity.dataBytes.cancelled()) { _ =>
              complete(StatusCodes.Forbidden)
            }
          }
        }

      case NonFatal(e) =>
        extractRequestEntity { entity =>
          extractActorSystem { implicit system =>
            val failureReference = java.util.UUID.randomUUID()

            log.error(
              "Unhandled exception encountered: [{}]; failure reference is [{}]",
              e.getMessage,
              failureReference,
              e
            )

            onSuccess(entity.dataBytes.cancelled()) { _ =>
              complete(
                StatusCodes.InternalServerError,
                HttpEntity(
                  ContentTypes.`text/plain(UTF-8)`,
                  s"Failed to process request; failure reference is [${failureReference.toString}]"
                )
              )
            }
          }
        }
    }
}