package stasis.server.api.handlers

import org.apache.pekko.actor.typed.scaladsl.LoggerOps
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.RejectionHandler
import org.apache.pekko.http.scaladsl.server.ValidationRejection
import org.slf4j.Logger
import io.github.sndnv.layers.api.MessageResponse
import io.github.sndnv.layers.streaming.Operators.ExtendedSource

object Rejection {
  def create(log: Logger): RejectionHandler =
    RejectionHandler
      .newBuilder()
      .handle { case ValidationRejection(rejectionMessage, _) =>
        import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

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
