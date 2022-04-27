package stasis.client.api.http

import akka.actor.typed.{ActorSystem, SpawnProtocol}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import stasis.client.api.clients.exceptions.ServerApiFailure
import stasis.client.api.http.routes._
import stasis.client.security.FrontendAuthenticator
import stasis.core.api.directives.EntityDiscardingDirectives
import stasis.core.security.tls.EndpointContext

import scala.concurrent.Future
import scala.util.control.NonFatal
import scala.util.{Failure, Success}

class HttpApiEndpoint(
  authenticator: FrontendAuthenticator
)(implicit system: ActorSystem[SpawnProtocol.Command], context: Context)
    extends EntityDiscardingDirectives {

  private val definitions = DatasetDefinitions()
  private val entries = DatasetEntries()
  private val metadata = DatasetMetadata()
  private val user = User()
  private val device = Device()
  private val service = Service()
  private val schedules = Schedules()
  private val operations = Operations()

  private val exceptionHandler: ExceptionHandler =
    ExceptionHandler {
      case e: ServerApiFailure =>
        context.log.error(e.message, e)

        discardEntity & complete(
          e.status,
          HttpEntity(ContentTypes.`text/plain(UTF-8)`, e.message)
        )

      case NonFatal(e) =>
        val message = s"Unhandled exception encountered: [${e.getMessage}]"

        context.log.error(message, e)

        discardEntity & complete(
          StatusCodes.InternalServerError,
          HttpEntity(ContentTypes.`text/plain(UTF-8)`, message)
        )
    }

  val endpointRoutes: Route =
    (extractMethod & extractUri & extractClientIP) { (method, uri, remoteAddress) =>
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
              context.log.warn(
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
          context.log.warn(
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
        handlerFlow = handleExceptions(exceptionHandler) {
          endpointRoutes
        }
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
