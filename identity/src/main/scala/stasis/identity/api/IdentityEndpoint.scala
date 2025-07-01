package stasis.identity.api

import scala.concurrent.Future
import scala.util.control.NonFatal

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.LoggerOps
import org.apache.pekko.http.cors.scaladsl.CorsDirectives._
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server._
import org.jose4j.jwk.JsonWebKey
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import stasis.identity.api.manage.setup.{Config => ManageConfig}
import stasis.identity.api.manage.setup.{Providers => ManageProviders}
import stasis.identity.api.oauth.setup.{Config => OAuthConfig}
import stasis.identity.api.oauth.setup.{Providers => OAuthProviders}
import io.github.sndnv.layers.api.MessageResponse
import io.github.sndnv.layers.api.directives.EntityDiscardingDirectives
import io.github.sndnv.layers.api.directives.LoggingDirectives
import io.github.sndnv.layers.security.tls.EndpointContext
import io.github.sndnv.layers.telemetry.TelemetryContext

class IdentityEndpoint(
  keys: Seq[JsonWebKey],
  oauthConfig: OAuthConfig,
  oauthProviders: OAuthProviders,
  manageConfig: ManageConfig,
  manageProviders: ManageProviders
)(implicit system: ActorSystem[Nothing], override val telemetry: TelemetryContext)
    extends LoggingDirectives
    with EntityDiscardingDirectives {
  import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

  override protected val log: Logger = LoggerFactory.getLogger(this.getClass.getName)

  private val oauth = OAuth(oauthConfig, oauthProviders)
  private val jwks = Jwks(keys)
  private val manage = Manage(manageProviders, manageConfig)

  private val sanitizingExceptionHandler: ExceptionHandler =
    ExceptionHandler { case NonFatal(e) =>
      extractRequest { request =>
        val failureReference = java.util.UUID.randomUUID()

        log.errorN(
          "Unhandled exception encountered during [{}] request for [{}]: [{} - {}]; failure reference is [{}]",
          request.method.value,
          request.uri.path.toString,
          e.getClass.getSimpleName,
          e.getMessage,
          failureReference
        )

        discardEntity & complete(
          StatusCodes.InternalServerError,
          MessageResponse(s"Failed to process request; failure reference is [${failureReference.toString}]")
        )
      }
    }

  private val rejectionHandler: RejectionHandler =
    RejectionHandler
      .newBuilder()
      .handle { case MissingQueryParamRejection(parameterName) =>
        extractRequest { request =>
          val message = s"Parameter [$parameterName] is missing, invalid or malformed"

          log.warnN(
            "[{}] request for [{}] rejected: [{}]",
            request.method.value,
            request.uri.path.toString,
            message
          )

          discardEntity & complete(
            StatusCodes.BadRequest,
            MessageResponse(message)
          )
        }
      }
      .handle { case ValidationRejection(rejectionMessage, _) =>
        extractRequest { request =>
          val message = s"Provided data is invalid or malformed: [$rejectionMessage]"

          log.warnN(
            "[{}] request for [{}] rejected: [{}]",
            request.method.value,
            request.uri.path.toString,
            message
          )

          discardEntity & complete(
            StatusCodes.BadRequest,
            MessageResponse(message)
          )
        }
      }
      .result()
      .seal

  val routes: Route =
    concat(
      pathPrefix("oauth") { oauth.routes },
      pathPrefix("jwks") { jwks.routes },
      pathPrefix("manage") { manage.routes },
      pathPrefix("service") { path("health") { complete(StatusCodes.OK) } }
    )

  def start(interface: String, port: Int, context: Option[EndpointContext]): Future[Http.ServerBinding] = {
    import EndpointContext._

    Http()
      .newServerAt(interface = interface, port = port)
      .withContext(context = context)
      .bindFlow(
        handlerFlow = withLoggedRequestAndResponse {
          handleRejections(corsRejectionHandler) {
            cors() {
              (handleExceptions(sanitizingExceptionHandler) & handleRejections(rejectionHandler)) {
                routes
              }
            }
          }
        }
      )
  }
}

object IdentityEndpoint {
  def apply(
    keys: Seq[JsonWebKey],
    oauthConfig: OAuthConfig,
    oauthProviders: OAuthProviders,
    manageConfig: ManageConfig,
    manageProviders: ManageProviders
  )(implicit system: ActorSystem[Nothing], telemetry: TelemetryContext): IdentityEndpoint =
    new IdentityEndpoint(
      keys = keys,
      oauthConfig = oauthConfig,
      oauthProviders = oauthProviders,
      manageConfig = manageConfig,
      manageProviders = manageProviders
    )
}
