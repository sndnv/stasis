package stasis.server.api

import akka.actor.ActorSystem
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.{ConnectionContext, Http}
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{ExceptionHandler, Route}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import akka.util.ByteString
import stasis.server.api.routes.{DatasetDefinitions, DatasetEntries, Devices, RoutesContext, Schedules, Users}
import stasis.server.security.exceptions.AuthorizationFailure
import stasis.server.security.{ResourceProvider, UserAuthenticator}

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.control.NonFatal
import scala.util.{Failure, Success}

class ApiEndpoint(
  resourceProvider: ResourceProvider,
  authenticator: UserAuthenticator
)(implicit val system: ActorSystem) {

  private implicit val mat: ActorMaterializer = ActorMaterializer()
  private implicit val ec: ExecutionContextExecutor = system.dispatcher

  private val log: LoggingAdapter = Logging(system, this.getClass.getName)

  private implicit val context: RoutesContext = RoutesContext(resourceProvider, ec, mat, log)

  private val definitions = new DatasetDefinitions()
  private val entries = new DatasetEntries()
  private val users = new Users()
  private val devices = new Devices()
  private val schedules = new Schedules()

  private implicit def sanitizingExceptionHandler: ExceptionHandler =
    ExceptionHandler {
      case e: AuthorizationFailure =>
        extractRequestEntity { entity =>
          val _ = entity.dataBytes.runWith(Sink.cancelled[ByteString])
          log.error(e, "User authorization failed: [{}]", e.getMessage)
          complete(StatusCodes.Forbidden)
        }

      case NonFatal(e) =>
        extractRequestEntity { entity =>
          val _ = entity.dataBytes.runWith(Sink.cancelled[ByteString])
          val failureReference = java.util.UUID.randomUUID()

          log.error(
            e,
            "Unhandled exception encountered: [{}]; failure reference is [{}]",
            e.getMessage,
            failureReference
          )

          complete(
            HttpResponse(
              status = StatusCodes.InternalServerError,
              entity = s"Failed to process request; failure reference is [$failureReference]"
            )
          )
        }
    }

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
                pathPrefix("schedules") { schedules.routes(currentUser = user) }
              )

            case Failure(e) =>
              val _ = request.entity.dataBytes.runWith(Sink.cancelled[ByteString])

              log.warning(
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

          log.warning(
            "Rejecting [{}] request for [{}] with no credentials from [{}]",
            method.value,
            uri,
            remoteAddress
          )

          complete(StatusCodes.Unauthorized)
      }
    }

  def start(interface: String, port: Int, context: ConnectionContext): Future[Http.ServerBinding] =
    Http().bindAndHandle(
      handler = endpointRoutes,
      interface = interface,
      port = port,
      connectionContext = context
    )
}
