package stasis.server.api.handlers

import scala.util.control.NonFatal

import org.apache.pekko.actor.typed.scaladsl.LoggerOps
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.ExceptionHandler
import org.slf4j.Logger
import stasis.layers.api.MessageResponse
import stasis.layers.streaming.Operators.ExtendedSource
import stasis.server.security.exceptions.AuthorizationFailure

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

        extractRequestEntity { entity =>
          extractActorSystem { implicit system =>
            val failureReference = java.util.UUID.randomUUID()

            log.error(
              "Unhandled exception encountered: [{} - {}]; failure reference is [{}]",
              e.getClass.getSimpleName,
              e.getMessage,
              failureReference
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
