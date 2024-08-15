package stasis.client.api.http

import scala.concurrent.Future
import scala.util.control.NonFatal
import scala.util.Failure
import scala.util.Success

import org.apache.pekko.actor.typed.scaladsl.LoggerOps
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.SpawnProtocol
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server._
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import stasis.client.api.Context
import stasis.client.api.clients.exceptions.ServerApiFailure
import stasis.client.api.http.routes._
import stasis.client.security.FrontendAuthenticator
import stasis.core.api.MessageResponse
import stasis.core.api.directives.EntityDiscardingDirectives
import stasis.core.api.directives.LoggingDirectives
import stasis.core.security.tls.EndpointContext
import stasis.core.telemetry.TelemetryContext

class HttpApiEndpoint(
  authenticator: FrontendAuthenticator
)(implicit system: ActorSystem[SpawnProtocol.Command], override val telemetry: TelemetryContext, context: Context)
    extends EntityDiscardingDirectives
    with LoggingDirectives {
  import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._
  import stasis.core.api.Formats.messageResponseFormat

  override protected val log: Logger = LoggerFactory.getLogger(this.getClass.getName)

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
        context.log.error(e.message)

        discardEntity & complete(
          e.status,
          MessageResponse(e.message)
        )

      case NonFatal(e) =>
        extractRequest { request =>
          val failureReference = java.util.UUID.randomUUID()

          log.errorN(
            "Unhandled exception encountered during [{}] request for [{}]: [{} - {}]; failure reference is [{}]",
            request.method.value,
            request.uri.path.toString,
            e.getClass.getSimpleName,
            e.getMessage,
            failureReference,
            e
          )

          val message = s"Unhandled exception encountered: [${e.getClass.getSimpleName} - ${e.getMessage}]; " +
            s"failure reference is [${failureReference.toString}]"

          discardEntity & complete(
            StatusCodes.InternalServerError,
            MessageResponse(message)
          )
        }
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
        handlerFlow = withLoggedRequestAndResponse {
          handleExceptions(exceptionHandler) {
            endpointRoutes
          }
        }
      )
  }
}

object HttpApiEndpoint {
  def apply(
    authenticator: FrontendAuthenticator
  )(implicit system: ActorSystem[SpawnProtocol.Command], telemetry: TelemetryContext, context: Context): HttpApiEndpoint =
    new HttpApiEndpoint(
      authenticator = authenticator
    )
}
