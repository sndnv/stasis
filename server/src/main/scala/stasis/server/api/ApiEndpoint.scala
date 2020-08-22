package stasis.server.api

import akka.actor.typed.{ActorSystem, SpawnProtocol}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.stream.scaladsl.Sink
import akka.stream.{Materializer, SystemMaterializer}
import akka.util.ByteString
import org.slf4j.LoggerFactory
import stasis.core.security.tls.EndpointContext
import stasis.server.api.routes._
import stasis.server.security.ResourceProvider
import stasis.server.security.authenticators.UserAuthenticator

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.{Failure, Success}

class ApiEndpoint(
  resourceProvider: ResourceProvider,
  authenticator: UserAuthenticator
)(implicit val system: ActorSystem[SpawnProtocol.Command]) {
  private implicit val ec: ExecutionContextExecutor = system.executionContext
  private implicit val mat: Materializer = SystemMaterializer(system).materializer

  private val log = LoggerFactory.getLogger(this.getClass.getName)

  private implicit val context: RoutesContext = RoutesContext(resourceProvider, ec, mat, log)

  private val definitions = DatasetDefinitions()
  private val entries = DatasetEntries()
  private val users = Users()
  private val devices = Devices()
  private val schedules = Schedules()
  private val nodes = Nodes()
  private val reservations = Reservations()
  private val staging = Staging()
  private val service = Service()

  private val sanitizingExceptionHandler: ExceptionHandler = handlers.Sanitizing.create(log)
  private val rejectionHandler: RejectionHandler = handlers.Rejection.create(log)

  val endpointRoutes: Route =
    (extractMethod & extractUri & extractClientIP & extractRequest) { (method, uri, remoteAddress, request) =>
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
              val _ = request.entity.dataBytes.runWith(Sink.cancelled[ByteString])

              log.warn(
                "Rejecting [{}] request for [{}] with invalid credentials from [{}]: [{}]",
                method.value,
                uri,
                remoteAddress,
                e
              )

              complete(StatusCodes.Unauthorized)
          }

        case None =>
          val _ = request.entity.dataBytes.runWith(Sink.cancelled[ByteString])

          log.warn(
            "Rejecting [{}] request for [{}] with no credentials from [{}]",
            method.value,
            uri,
            remoteAddress
          )

          complete(StatusCodes.Unauthorized)
      }
    }

  def start(interface: String, port: Int, context: Option[EndpointContext]): Future[Http.ServerBinding] = {
    import EndpointContext._

    Http()
      .newServerAt(interface = interface, port = port)
      .withContext(context = context)
      .bindFlow(
        handlerFlow = (handleExceptions(sanitizingExceptionHandler) & handleRejections(rejectionHandler)) {
          endpointRoutes
        }
      )
  }
}

object ApiEndpoint {
  def apply(
    resourceProvider: ResourceProvider,
    authenticator: UserAuthenticator
  )(implicit system: ActorSystem[SpawnProtocol.Command]): ApiEndpoint =
    new ApiEndpoint(
      resourceProvider = resourceProvider,
      authenticator = authenticator
    )
}
