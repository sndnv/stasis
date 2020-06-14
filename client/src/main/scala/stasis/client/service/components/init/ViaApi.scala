package stasis.client.service.components.init

import akka.Done
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
import play.api.libs.json.{Format, JsObject, Json}
import stasis.client.service.components.exceptions.ServiceStartupFailure

import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success}

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
              case Failure(e)                        => complete(InitState.Failed(cause = "unknown", message = e.getMessage): InitState)
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
