package stasis.client.api.http

import akka.actor.typed.{ActorSystem, SpawnProtocol}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.http.scaladsl.{ConnectionContext, Http}
import akka.stream.{Materializer, SystemMaterializer}
import akka.stream.scaladsl.Sink
import akka.util.ByteString
import stasis.client.api.clients.exceptions.ServerApiFailure
import stasis.client.api.http.routes._
import stasis.client.security.FrontendAuthenticator

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.control.NonFatal
import scala.util.{Failure, Success}

class HttpApiEndpoint(
  authenticator: FrontendAuthenticator
)(implicit system: ActorSystem[SpawnProtocol.Command], context: Context) {

  private implicit val untypedSystem: akka.actor.ActorSystem = system.classicSystem
  private implicit val mat: Materializer = SystemMaterializer(system).materializer
  private implicit val ec: ExecutionContextExecutor = system.executionContext

  private val definitions = DatasetDefinitions()
  private val entries = DatasetEntries()
  private val metadata = DatasetMetadata()
  private val user = User()
  private val device = Device()
  private val service = Service()
  private val schedules = Schedules()
  private val operations = Operations()

  private implicit def exceptionHandler: ExceptionHandler =
    ExceptionHandler {
      case e: ServerApiFailure =>
        extractRequestEntity { entity =>
          val _ = entity.dataBytes.runWith(Sink.cancelled[ByteString])

          context.log.error(e.message, e)

          complete(
            e.status,
            HttpEntity(ContentTypes.`text/plain(UTF-8)`, e.message)
          )
        }

      case NonFatal(e) =>
        extractRequestEntity { entity =>
          val _ = entity.dataBytes.runWith(Sink.cancelled[ByteString])
          val message = s"Unhandled exception encountered: [${e.getMessage}]"

          context.log.error(message, e)

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
                pathPrefix("service") { service.routes() },
                pathPrefix("schedules") { schedules.routes() },
                pathPrefix("operations") { operations.routes() }
              )

            case Failure(e) =>
              val _ = request.entity.dataBytes.runWith(Sink.cancelled[ByteString])

              context.log.warn(
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

          context.log.warn(
            "Rejecting [{}] request for [{}] with no credentials from [{}]",
            method.value,
            uri,
            remoteAddress
          )

          complete(StatusCodes.Unauthorized)
      }
    }

  def start(interface: String, port: Int, context: Option[ConnectionContext]): Future[Http.ServerBinding] = {
    val http = Http()

    http.bindAndHandle(
      handler = endpointRoutes,
      interface = interface,
      port = port,
      connectionContext = context.getOrElse(http.defaultServerHttpContext)
    )
  }
}

object HttpApiEndpoint {
  def apply(
    authenticator: FrontendAuthenticator
  )(implicit system: ActorSystem[SpawnProtocol.Command], context: Context): HttpApiEndpoint =
    new HttpApiEndpoint(
      authenticator = authenticator
    )
}
