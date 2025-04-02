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
import org.apache.pekko.http.scaladsl.server._
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import stasis.core.discovery.http.HttpServiceDiscoveryEndpoint
import stasis.core.discovery.providers.server.ServiceDiscoveryProvider
import stasis.layers.api.directives.EntityDiscardingDirectives
import stasis.layers.api.directives.LoggingDirectives
import stasis.layers.security.tls.EndpointContext
import stasis.layers.telemetry.TelemetryContext
import stasis.server.api.routes._
import stasis.server.security.ResourceProvider
import stasis.server.security.authenticators.UserAuthenticator
import stasis.server.security.users.UserCredentialsManager
import stasis.shared.secrets.SecretsConfig

class ApiEndpoint(
  resourceProvider: ResourceProvider,
  authenticator: UserAuthenticator,
  userCredentialsManager: UserCredentialsManager,
  serviceDiscoveryProvider: ServiceDiscoveryProvider,
  secretsConfig: SecretsConfig
)(implicit val system: ActorSystem[Nothing], override val telemetry: TelemetryContext)
    extends LoggingDirectives
    with EntityDiscardingDirectives {
  private implicit val ec: ExecutionContextExecutor = system.executionContext

  override protected val log: Logger = LoggerFactory.getLogger(this.getClass.getName)

  private implicit val context: RoutesContext = RoutesContext(resourceProvider, ec, log)

  private val definitions = DatasetDefinitions()
  private val entries = DatasetEntries()
  private val users = Users(userCredentialsManager, secretsConfig)
  private val devices = Devices()
  private val schedules = Schedules()
  private val nodes = Nodes()
  private val manifests = Manifests()
  private val reservations = Reservations()
  private val staging = Staging()
  private val service = Service()
  private val discovery = new HttpServiceDiscoveryEndpoint(provider = serviceDiscoveryProvider)

  private val sanitizingExceptionHandler: ExceptionHandler = handlers.Sanitizing.create(log)
  private val rejectionHandler: RejectionHandler = handlers.Rejection.create(log)

  val endpointRoutes: Route =
    (extractMethod & extractUri & extractClientIP) { (method, uri, remoteAddress) =>
      extractCredentials {
        case Some(credentials) =>
          onComplete(authenticator.authenticate(credentials)) {
            case Success(user) =>
              concat(
                pathPrefix("datasets") {
                  concat(
                    pathPrefix("definitions") { definitions.routes(currentUser = user) },
                    pathPrefix("entries") { entries.routes(currentUser = user) }
                  )
                },
                pathPrefix("users") { users.routes(currentUser = user) },
                pathPrefix("devices") { devices.routes(currentUser = user) },
                pathPrefix("schedules") { schedules.routes(currentUser = user) },
                pathPrefix("nodes") { nodes.routes(currentUser = user) },
                pathPrefix("manifests") { manifests.routes(currentUser = user) },
                pathPrefix("reservations") { reservations.routes(currentUser = user) },
                pathPrefix("staging") { staging.routes(currentUser = user) },
                pathPrefix("service") { service.routes(currentUser = user) },
                pathPrefix("discovery") { discovery.routes }
              )

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
}

object ApiEndpoint {
  def apply(
    resourceProvider: ResourceProvider,
    authenticator: UserAuthenticator,
    userCredentialsManager: UserCredentialsManager,
    serviceDiscoveryProvider: ServiceDiscoveryProvider,
    secretsConfig: SecretsConfig
  )(implicit system: ActorSystem[Nothing], telemetry: TelemetryContext): ApiEndpoint =
    new ApiEndpoint(
      resourceProvider = resourceProvider,
      authenticator = authenticator,
      userCredentialsManager = userCredentialsManager,
      serviceDiscoveryProvider = serviceDiscoveryProvider,
      secretsConfig = secretsConfig
    )
}
