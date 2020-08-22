package stasis.server.api

import akka.actor.typed.{ActorSystem, SpawnProtocol}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{ExceptionHandler, RejectionHandler, Route}
import akka.stream.scaladsl.Sink
import akka.stream.{Materializer, SystemMaterializer}
import akka.util.ByteString
import org.slf4j.LoggerFactory
import stasis.core.security.tls.EndpointContext
import stasis.server.api.routes.{DeviceBootstrap, RoutesContext}
import stasis.server.security.ResourceProvider
import stasis.server.security.authenticators.{BootstrapCodeAuthenticator, UserAuthenticator}

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.{Failure, Success}

class BootstrapEndpoint(
  resourceProvider: ResourceProvider,
  userAuthenticator: UserAuthenticator,
  bootstrapCodeAuthenticator: BootstrapCodeAuthenticator,
  deviceBootstrapContext: DeviceBootstrap.BootstrapContext
)(implicit val system: ActorSystem[SpawnProtocol.Command]) {
  private implicit val ec: ExecutionContextExecutor = system.executionContext
  private implicit val mat: Materializer = SystemMaterializer(system).materializer

  private val log = LoggerFactory.getLogger(this.getClass.getName)

  private implicit val context: RoutesContext = RoutesContext(resourceProvider, ec, mat, log)

  private val deviceBootstrap = DeviceBootstrap(deviceBootstrapContext)

  private val sanitizingExceptionHandler: ExceptionHandler = handlers.Sanitizing.create(log)
  private val rejectionHandler: RejectionHandler = handlers.Rejection.create(log)

  val endpointRoutes: Route =
    (extractMethod & extractUri & extractClientIP & extractRequest) { (method, uri, remoteAddress, request) =>
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
                authenticated(bootstrapCodeAuthenticator.authenticate(credentials)) {
                  case (code, user) =>
                    deviceBootstrap.execute(code)(currentUser = user)
                }
              }
            )
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

  private def authenticated[T](handler: => Future[T])(route: T => Route): Route =
    (extractMethod & extractUri & extractClientIP & extractRequest) { (method, uri, remoteAddress, request) =>
      onComplete(handler) {
        case Success(credentials) =>
          route(credentials)

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
    }
}

object BootstrapEndpoint {
  def apply(
    resourceProvider: ResourceProvider,
    userAuthenticator: UserAuthenticator,
    bootstrapCodeAuthenticator: BootstrapCodeAuthenticator,
    deviceBootstrapContext: DeviceBootstrap.BootstrapContext
  )(implicit system: ActorSystem[SpawnProtocol.Command]): BootstrapEndpoint =
    new BootstrapEndpoint(
      resourceProvider = resourceProvider,
      userAuthenticator = userAuthenticator,
      bootstrapCodeAuthenticator = bootstrapCodeAuthenticator,
      deviceBootstrapContext = deviceBootstrapContext
    )
}
