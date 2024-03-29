package stasis.server.api.handlers

import org.apache.pekko.actor.typed.scaladsl.LoggerOps
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.ExceptionHandler
import org.slf4j.Logger
import stasis.core.api.MessageResponse
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
        import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._
        import stasis.core.api.Formats.messageResponseFormat

        extractRequestEntity { entity =>
          extractActorSystem { implicit system =>
            val failureReference = java.util.UUID.randomUUID()

            log.error(
              "Unhandled exception encountered: [{} - {}]; failure reference is [{}]",
              e.getClass.getSimpleName,
              e.getMessage,
              failureReference,
              e
            )

            onSuccess(entity.dataBytes.cancelled()) { _ =>
              complete(
                StatusCodes.InternalServerError,
                MessageResponse(s"Failed to process request; failure reference is [${failureReference.toString}]")
              )
            }
          }
        }
    }
}
