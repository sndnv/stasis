package stasis.identity.api

import akka.actor.typed.{ActorSystem, SpawnProtocol}
import akka.actor.typed.scaladsl.LoggerOps
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import ch.megard.akka.http.cors.scaladsl.CorsDirectives._
import org.jose4j.jwk.JsonWebKey
import org.slf4j.{Logger, LoggerFactory}
import stasis.core.api.MessageResponse
import stasis.core.api.directives._
import stasis.core.security.tls.EndpointContext
import stasis.core.telemetry.TelemetryContext
import stasis.identity.api.manage.setup.{Config => ManageConfig, Providers => ManageProviders}
import stasis.identity.api.oauth.setup.{Config => OAuthConfig, Providers => OAuthProviders}

import scala.concurrent.Future
import scala.util.control.NonFatal

class IdentityEndpoint(
  keys: Seq[JsonWebKey],
  oauthConfig: OAuthConfig,
  oauthProviders: OAuthProviders,
  manageConfig: ManageConfig,
  manageProviders: ManageProviders
)(implicit system: ActorSystem[SpawnProtocol.Command], override val telemetry: TelemetryContext)
    extends LoggingDirectives
    with EntityDiscardingDirectives {
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
  import stasis.core.api.Formats.messageResponseFormat

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
          failureReference,
          e
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
  )(implicit system: ActorSystem[SpawnProtocol.Command], telemetry: TelemetryContext): IdentityEndpoint =
    new IdentityEndpoint(
      keys = keys,
      oauthConfig = oauthConfig,
      oauthProviders = oauthProviders,
      manageConfig = manageConfig,
      manageProviders = manageProviders
    )
}
