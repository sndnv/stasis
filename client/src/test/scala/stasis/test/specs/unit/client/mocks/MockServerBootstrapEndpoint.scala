package stasis.test.specs.unit.client.mocks

import java.util.concurrent.atomic.AtomicInteger

import org.apache.pekko.actor.typed.scaladsl.LoggerOps
import org.apache.pekko.actor.typed.{ActorSystem, SpawnProtocol}
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.model.headers.OAuth2BearerToken
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.Route
import org.slf4j.{Logger, LoggerFactory}
import stasis.core.security.tls.EndpointContext
import stasis.shared.model.devices.DeviceBootstrapParameters

import scala.concurrent.Future

class MockServerBootstrapEndpoint(
  expectedCode: String,
  providedParams: DeviceBootstrapParameters
)(implicit system: ActorSystem[SpawnProtocol.Command]) {
  import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._
  import stasis.shared.api.Formats._

  private val bootstrapExecuted: AtomicInteger = new AtomicInteger(0)

  private val log: Logger = LoggerFactory.getLogger(this.getClass.getName)

  private val routes: Route =
    (extractMethod & extractUri & extractRequest) { (method, uri, request) =>
      extractCredentials {
        case Some(OAuth2BearerToken(`expectedCode`)) =>
          pathPrefix("devices" / "execute") {
            put {
              log.infoN("Successfully executed bootstrap for device [{}]", providedParams.serverApi.device)
              val _ = bootstrapExecuted.incrementAndGet()
              complete(providedParams)
            }
          }

        case _ =>
          val _ = request.discardEntityBytes()

          log.warnN(
            "Rejecting [{}] request for [{}] with no/invalid credentials",
            method.value,
            uri
          )

          complete(StatusCodes.Unauthorized)
      }
    }

  def bootstrapExecutedCount(): Int =
    bootstrapExecuted.get()

  def start(
    port: Int,
    context: Option[EndpointContext] = None
  ): Future[Http.ServerBinding] = {
    val server = {
      val builder = Http().newServerAt(interface = "localhost", port = port)

      context match {
        case Some(httpsContext) => builder.enableHttps(httpsContext.connection)
        case None               => builder
      }
    }

    server.bindFlow(handlerFlow = routes)
  }
}
