package stasis.client.api.http

import akka.actor.ActorSystem
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.http.scaladsl.{ConnectionContext, Http}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import akka.util.ByteString
import stasis.client.api.http.routes._
import stasis.client.security.FrontendAuthenticator

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.control.NonFatal
import scala.util.{Failure, Success}

class ApiEndpoint(
  authenticator: FrontendAuthenticator
)(implicit system: ActorSystem, providers: Context) {

  private implicit val mat: ActorMaterializer = ActorMaterializer()
  private implicit val ec: ExecutionContextExecutor = system.dispatcher

  private val log: LoggingAdapter = Logging(system, this.getClass.getName)

  private val definitions = new DatasetDefinitions()
  private val entries = new DatasetEntries()
  private val metadata = new DatasetMetadata()
  private val user = new User()
  private val device = new Device()
  private val schedules = new Schedules()
  private val operations = new Operations()

  private implicit def exceptionHandler: ExceptionHandler =
    ExceptionHandler {
      case NonFatal(e) =>
        extractRequestEntity { entity =>
          val _ = entity.dataBytes.runWith(Sink.cancelled[ByteString])
          val failureReference = java.util.UUID.randomUUID()

          val message = s"Unhandled exception encountered: [${e.getMessage}]; failure reference is [$failureReference]"

          log.error(e, message)

          complete(
            StatusCodes.InternalServerError,
            HttpEntity(ContentTypes.`text/plain(UTF-8)`, message)
          )
        }
    }

  val endpointRoutes: Route =
    (extractMethod & extractUri & extractClientIP & extractRequest) { (method, uri, remoteAddress, request) =>
      extractCredentials {
        case Some(credentials) =>
          onComplete(authenticator.authenticate(credentials)) {
            case Success(_) =>
              concat(
                pathPrefix("datasets") {
                  concat(
                    pathPrefix("definitions") { definitions.routes() },
                    pathPrefix("entries") { entries.routes() },
                    pathPrefix("metadata") { metadata.routes() }
                  )
                },
                pathPrefix("user") { user.routes() },
                pathPrefix("device") { device.routes() },
                pathPrefix("schedules") { schedules.routes() },
                pathPrefix("operations") { operations.routes() }
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
