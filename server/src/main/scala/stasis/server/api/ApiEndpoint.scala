package stasis.server.api

import akka.actor.typed.scaladsl.LoggerOps
import akka.actor.typed.{ActorSystem, SpawnProtocol}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.http.scaladsl.{ConnectionContext, Http}
import akka.stream.scaladsl.Sink
import akka.stream.{Materializer, SystemMaterializer}
import akka.util.ByteString
import org.slf4j.LoggerFactory
import stasis.server.api.routes._
import stasis.server.security.exceptions.AuthorizationFailure
import stasis.server.security.{ResourceProvider, UserAuthenticator}

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.control.NonFatal
import scala.util.{Failure, Success}

class ApiEndpoint(
  resourceProvider: ResourceProvider,
  authenticator: UserAuthenticator
)(implicit val system: ActorSystem[SpawnProtocol.Command]) {
  private implicit val ec: ExecutionContextExecutor = system.executionContext
  private implicit val mat: Materializer = SystemMaterializer(system).materializer

  private val log = LoggerFactory.getLogger(this.getClass.getName)

  private implicit val context: RoutesContext = RoutesContext(resourceProvider, ec, mat, log)

  private val definitions = new DatasetDefinitions()
  private val entries = new DatasetEntries()
  private val users = new Users()
  private val devices = new Devices()
  private val schedules = new Schedules()
  private val nodes = new Nodes()
  private val reservations = new Reservations()
  private val staging = new Staging()
  private val service = new Service()

  private implicit def sanitizingExceptionHandler: ExceptionHandler =
    ExceptionHandler {
      case e: AuthorizationFailure =>
        extractRequestEntity { entity =>
          val _ = entity.dataBytes.runWith(Sink.cancelled[ByteString])
          log.errorN("User authorization failed: [{}]", e.getMessage, e)
          complete(StatusCodes.Forbidden)
        }

      case NonFatal(e) =>
        extractRequestEntity { entity =>
          val _ = entity.dataBytes.runWith(Sink.cancelled[ByteString])
          val failureReference = java.util.UUID.randomUUID()

          log.error(
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

  private implicit def rejectionHandler: RejectionHandler =
    RejectionHandler
      .newBuilder()
      .handle {
        case ValidationRejection(_, _) =>
          extractRequestEntity { entity =>
            val _ = entity.dataBytes.runWith(Sink.cancelled[ByteString])

            val message = "Provided data is invalid or malformed"
            log.warn(message)

            complete(
              StatusCodes.BadRequest,
              HttpEntity(ContentTypes.`text/plain(UTF-8)`, message)
            )
          }
      }
      .result()
      .seal

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

  def start(interface: String, port: Int, context: ConnectionContext): Future[Http.ServerBinding] = {
    implicit val untyped: akka.actor.ActorSystem = system.classicSystem

    Http().bindAndHandle(
      handler = endpointRoutes,
      interface = interface,
      port = port,
      connectionContext = context
    )
  }
}
