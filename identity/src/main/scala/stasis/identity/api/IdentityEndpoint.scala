package stasis.identity.api

import akka.actor.ActorSystem
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.stream.ActorMaterializer
import org.jose4j.jwk.JsonWebKey
import stasis.identity.api.manage.setup.{Config => ManageConfig, Providers => ManageProviders}
import stasis.identity.api.oauth.setup.{Providers => OAuthProviders}

import scala.concurrent.Future
import scala.util.control.NonFatal

class IdentityEndpoint(
  keys: Seq[JsonWebKey],
  oauthProviders: OAuthProviders,
  manageProviders: ManageProviders,
  manageConfig: ManageConfig
)(implicit system: ActorSystem) {

  private val log: LoggingAdapter = Logging(system, this.getClass.getName)
  private implicit val mat: ActorMaterializer = ActorMaterializer()

  private val oauth = new OAuth(oauthProviders)
  private val jwks = new Jwks(keys)
  private val manage = new Manage(manageProviders, manageConfig)

  private implicit def sanitizingExceptionHandler: ExceptionHandler =
    ExceptionHandler {
      case NonFatal(e) =>
        extractRequestEntity { entity =>
          val _ = entity.discardBytes()
          val failureReference = java.util.UUID.randomUUID()

          log.error(
            e,
            "Unhandled exception encountered: [{}]; failure reference is [{}]",
            e.getMessage,
            failureReference
          )

          complete(
            StatusCodes.InternalServerError,
            s"Failed to process request; failure reference is [$failureReference]"
          )
        }
    }

  private implicit def rejectionHandler: RejectionHandler =
    RejectionHandler
      .newBuilder()
      .handle {
        case MissingQueryParamRejection(parameterName) =>
          extractRequestEntity { entity =>
            val _ = entity.discardBytes()

            val message = s"Parameter [$parameterName] is missing, invalid or malformed"
            log.warning(message)
            complete(StatusCodes.BadRequest, message)
          }
      }
      .result()
      .seal

  val routes: Route =
    concat(
      pathPrefix("oauth") { oauth.routes },
      pathPrefix("jwks") { jwks.routes },
      pathPrefix("manage") { manage.routes }
    )

  def start(interface: String, port: Int): Future[Http.ServerBinding] =
    Http().bindAndHandle(routes, interface, port)
}
