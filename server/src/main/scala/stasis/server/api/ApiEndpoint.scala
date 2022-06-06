package stasis.server.api

import akka.actor.typed.{ActorSystem, SpawnProtocol}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import ch.megard.akka.http.cors.scaladsl.CorsDirectives._
import org.slf4j.{Logger, LoggerFactory}
import stasis.core.api.directives.{EntityDiscardingDirectives, LoggingDirectives}
import stasis.core.security.tls.EndpointContext
import stasis.core.telemetry.TelemetryContext
import stasis.server.api.routes._
import stasis.server.security.ResourceProvider
import stasis.server.security.authenticators.UserAuthenticator
import stasis.server.security.users.UserCredentialsManager
import stasis.shared.secrets.SecretsConfig

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.{Failure, Success}

class ApiEndpoint(
  resourceProvider: ResourceProvider,
  authenticator: UserAuthenticator,
  userCredentialsManager: UserCredentialsManager,
  secretsConfig: SecretsConfig
)(implicit val system: ActorSystem[SpawnProtocol.Command], override val telemetry: TelemetryContext)
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
  private val reservations = Reservations()
  private val staging = Staging()
  private val service = Service()

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
                pathPrefix("reservations") { reservations.routes(currentUser = user) },
                pathPrefix("staging") { staging.routes(currentUser = user) },
                pathPrefix("service") { service.routes(currentUser = user) }
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
                endpointRoutes
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
    secretsConfig: SecretsConfig
  )(implicit system: ActorSystem[SpawnProtocol.Command], telemetry: TelemetryContext): ApiEndpoint =
    new ApiEndpoint(
      resourceProvider = resourceProvider,
      authenticator = authenticator,
      userCredentialsManager = userCredentialsManager,
      secretsConfig = secretsConfig
    )
}
