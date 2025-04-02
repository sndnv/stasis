package stasis.test.specs.unit.core.discovery.mocks

import scala.concurrent.Future

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.LoggerOps
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.model.headers.HttpCredentials
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.Route
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import stasis.core.discovery.ServiceDiscoveryRequest
import stasis.core.discovery.ServiceDiscoveryResult
import stasis.layers.security.tls.EndpointContext

class MockDiscoveryApiEndpoint(
  expectedCredentials: HttpCredentials,
  result: ServiceDiscoveryResult = ServiceDiscoveryResult.KeepExisting
)(implicit system: ActorSystem[Nothing]) {
  import stasis.core.api.Formats._

  private val log: Logger = LoggerFactory.getLogger(this.getClass.getName)

  private val routes: Route =
    (extractMethod & extractUri & extractRequest) { (method, uri, request) =>
      extractCredentials {
        case Some(`expectedCredentials`) =>
          path("discovery" / "provide") {
            post {
              import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

              entity(as[ServiceDiscoveryRequest]) { request =>
                log.info(
                  "Responding to service discovery request [{}] with [{}]",
                  request.id,
                  result.asString
                )

                complete(result)
              }
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

  def start(port: Int, context: Option[EndpointContext] = None): Future[Http.ServerBinding] = {
    val server = {
      val builder = Http().newServerAt(interface = "localhost", port = port)

      context match {
        case Some(httpsContext) => builder.enableHttps(httpsContext.connection)
        case None               => builder
      }
    }

    server.bindFlow(handlerFlow = pathPrefix("v1") { routes })
  }
}
