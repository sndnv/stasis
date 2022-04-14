package stasis.identity.api

import akka.actor.ActorSystem
import akka.actor.typed.scaladsl.LoggerOps
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.stream.scaladsl.Sink
import akka.util.ByteString
import ch.megard.akka.http.cors.scaladsl.CorsDirectives._
import org.jose4j.jwk.JsonWebKey
import org.slf4j.{Logger, LoggerFactory}
import stasis.core.api.directives.LoggingDirectives
import stasis.core.security.tls.EndpointContext
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
)(implicit system: ActorSystem)
    extends LoggingDirectives {

  override protected val log: Logger = LoggerFactory.getLogger(this.getClass.getName)

  private val oauth = OAuth(oauthConfig, oauthProviders)
  private val jwks = Jwks(keys)
  private val manage = Manage(manageProviders, manageConfig)

  private val sanitizingExceptionHandler: ExceptionHandler =
    ExceptionHandler { case NonFatal(e) =>
      extractRequestEntity { entity =>
        val _ = entity.dataBytes.runWith(Sink.cancelled[ByteString])
        val failureReference = java.util.UUID.randomUUID()

        log.errorN(
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

  private val rejectionHandler: RejectionHandler =
    RejectionHandler
      .newBuilder()
      .handle { case MissingQueryParamRejection(parameterName) =>
        extractRequestEntity { entity =>
          val _ = entity.dataBytes.runWith(Sink.cancelled[ByteString])

          val message = s"Parameter [$parameterName] is missing, invalid or malformed"
          log.warnN(message)

          complete(
            StatusCodes.BadRequest,
            HttpEntity(ContentTypes.`text/plain(UTF-8)`, message)
          )
        }
      }
      .handle { case ValidationRejection(rejectionMessage, _) =>
        extractRequestEntity { entity =>
          val _ = entity.dataBytes.runWith(Sink.cancelled[ByteString])

          val message = s"Provided data is invalid or malformed: [$rejectionMessage]"
          log.warnN(message)

          complete(
            StatusCodes.BadRequest,
            HttpEntity(ContentTypes.`text/plain(UTF-8)`, message)
          )
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
  )(implicit system: ActorSystem): IdentityEndpoint =
    new IdentityEndpoint(
      keys = keys,
      oauthConfig = oauthConfig,
      oauthProviders = oauthProviders,
      manageConfig = manageConfig,
      manageProviders = manageProviders
    )
}
