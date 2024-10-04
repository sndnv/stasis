package stasis.client.service.components.init

import scala.concurrent.Future
import scala.concurrent.Promise
import scala.util.Failure
import scala.util.Success

import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._
import org.apache.pekko.Done
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.Route
import play.api.libs.json.Format
import play.api.libs.json.JsObject
import play.api.libs.json.Json

import stasis.client.service.components.exceptions.ServiceStartupFailure

object ViaApi {
  sealed trait InitState
  object InitState {
    case object Pending extends InitState
    case object Completed extends InitState
    final case class Failed(cause: String, message: String) extends InitState
  }

  implicit val initStateFormat: Format[InitState] = Format(
    fjs = _.validate[JsObject].flatMap { state =>
      (state \ "startup").validate[String].map {
        case "pending"    => InitState.Pending
        case "successful" => InitState.Completed
        case "failed"     => InitState.Failed(cause = (state \ "cause").as[String], message = (state \ "message").as[String])
      }
    },
    tjs = {
      case InitState.Pending                => Json.obj("startup" -> "pending")
      case InitState.Completed              => Json.obj("startup" -> "successful")
      case InitState.Failed(cause, message) => Json.obj("startup" -> "failed", "cause" -> cause, "message" -> message)
    }
  )

  def routes(
    credentials: Promise[(String, Array[Char])],
    startup: Future[Done]
  ): Route =
    path("init") {
      concat(
        get {
          if (credentials.isCompleted) {
            onComplete(startup) {
              case Success(_)                        => complete(InitState.Completed: InitState)
              case Failure(e: ServiceStartupFailure) => complete(InitState.Failed(e.cause, e.message): InitState)
              case Failure(e) => complete(InitState.Failed(cause = "unknown", message = e.getMessage): InitState)
            }
          } else {
            complete(InitState.Pending: InitState)
          }
        },
        post {
          formFields(
            "username".as[String],
            "password".as[Array[Char]]
          ) {
            case (username, password) if username.isEmpty || password.isEmpty => complete(StatusCodes.BadRequest)
            case provided if credentials.trySuccess(provided)                 => complete(StatusCodes.Accepted)
            case _                                                            => complete(StatusCodes.Conflict)
          }
        }
      )
    }
}
