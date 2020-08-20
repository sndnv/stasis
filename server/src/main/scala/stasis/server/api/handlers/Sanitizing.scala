package stasis.server.api.handlers

import akka.actor.typed.scaladsl.LoggerOps
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Directives.{complete, extractRequestEntity}
import akka.http.scaladsl.server.ExceptionHandler
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import akka.util.ByteString
import org.slf4j.Logger
import stasis.server.security.exceptions.AuthorizationFailure

import scala.util.control.NonFatal

object Sanitizing {
  def create(log: Logger)(implicit mat: Materializer): ExceptionHandler =
    ExceptionHandler {
      case e: AuthorizationFailure =>
        extractRequestEntity { entity =>
          val _ = entity.dataBytes.runWith(Sink.cancelled[ByteString])
          log.errorN("User authorization failed: [{}]", e.getMessage, e)
          complete(StatusCodes.Forbidden)
        }

      case NonFatal(e) =>
        extractRequestEntity { entity =>
          val _ = entity.dataBytes.runWith(Sink.cancelled[ByteString])
          val failureReference = java.util.UUID.randomUUID()

          log.error(
            "Unhandled exception encountered: [{}]; failure reference is [{}]",
            e.getMessage,
            failureReference,
            e
          )

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
