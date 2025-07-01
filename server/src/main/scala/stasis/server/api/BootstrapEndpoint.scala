package stasis.server.api

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.http.cors.scaladsl.CorsDirectives._
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.ExceptionHandler
import org.apache.pekko.http.scaladsl.server.RejectionHandler
import org.apache.pekko.http.scaladsl.server.Route
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import io.github.sndnv.layers.api.directives.EntityDiscardingDirectives
import io.github.sndnv.layers.api.directives.LoggingDirectives
import io.github.sndnv.layers.security.tls.EndpointContext
import io.github.sndnv.layers.telemetry.TelemetryContext
import stasis.server.api.routes.DeviceBootstrap
import stasis.server.api.routes.RoutesContext
import stasis.server.security.ResourceProvider
import stasis.server.security.authenticators.BootstrapCodeAuthenticator
import stasis.server.security.authenticators.UserAuthenticator

class BootstrapEndpoint(
  resourceProvider: ResourceProvider,
  userAuthenticator: UserAuthenticator,
  bootstrapCodeAuthenticator: BootstrapCodeAuthenticator,
  deviceBootstrapContext: DeviceBootstrap.BootstrapContext
)(implicit val system: ActorSystem[Nothing], override val telemetry: TelemetryContext)
    extends LoggingDirectives
    with EntityDiscardingDirectives {
  private implicit val ec: ExecutionContextExecutor = system.executionContext

  override protected val log: Logger = LoggerFactory.getLogger(this.getClass.getName)

  private implicit val context: RoutesContext = RoutesContext(resourceProvider, ec, log)

  private val deviceBootstrap = DeviceBootstrap(deviceBootstrapContext)

  private val sanitizingExceptionHandler: ExceptionHandler = handlers.Sanitizing.create(log)
  private val rejectionHandler: RejectionHandler = handlers.Rejection.create(log)

  val endpointRoutes: Route =
    (extractMethod & extractUri & extractClientIP) { (method, uri, remoteAddress) =>
      extractCredentials {
        case Some(credentials) =>
          pathPrefix("devices") {
            concat(
              pathPrefix("codes") {
                authenticated(userAuthenticator.authenticate(credentials)) { user =>
                  deviceBootstrap.codes(currentUser = user)
                }
              },
              pathPrefix("execute") {
                authenticated(bootstrapCodeAuthenticator.authenticate(credentials)) { case (code, user) =>
                  deviceBootstrap.execute(code)(currentUser = user)
                }
              }
            )
          }

        case None =>
          log.warn(
            "Rejecting [{}] request for [{}] with no credentials from [{}]",
            method.value,
            uri,
            remoteAddress
          )

          discardEntity & complete(StatusCodes.Unauthorized)
      }
    }

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
                pathPrefix("v1") { endpointRoutes }
              }
            }
          }
        }
      )
  }

  private def authenticated[T](handler: => Future[T])(route: T => Route): Route =
    (extractMethod & extractUri & extractClientIP) { (method, uri, remoteAddress) =>
      onComplete(handler) {
        case Success(credentials) =>
          route(credentials)

        case Failure(e) =>
          log.warn(
            "Rejecting [{}] request for [{}] with invalid credentials from [{}]: [{} - {}]",
            method.value,
            uri,
            remoteAddress,
            e.getClass.getSimpleName,
            e.getMessage
          )

          discardEntity & complete(StatusCodes.Unauthorized)
      }
    }
}

object BootstrapEndpoint {
  def apply(
    resourceProvider: ResourceProvider,
    userAuthenticator: UserAuthenticator,
    bootstrapCodeAuthenticator: BootstrapCodeAuthenticator,
    deviceBootstrapContext: DeviceBootstrap.BootstrapContext
  )(implicit system: ActorSystem[Nothing], telemetry: TelemetryContext): BootstrapEndpoint =
    new BootstrapEndpoint(
      resourceProvider = resourceProvider,
      userAuthenticator = userAuthenticator,
      bootstrapCodeAuthenticator = bootstrapCodeAuthenticator,
      deviceBootstrapContext = deviceBootstrapContext
    )
}
