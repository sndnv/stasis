package stasis.core.discovery.http

import org.apache.pekko.actor.typed.scaladsl.LoggerOps
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server._
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import stasis.core.discovery.ServiceDiscoveryRequest
import stasis.core.discovery.providers.server.ServiceDiscoveryProvider
import stasis.layers.api.directives.EntityDiscardingDirectives

class HttpServiceDiscoveryEndpoint(
  provider: ServiceDiscoveryProvider
) extends EntityDiscardingDirectives {
  import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

  import stasis.core.api.Formats.serviceDiscoveryRequestFormat
  import stasis.core.api.Formats.serviceDiscoveryResultFormat

  private val log: Logger = LoggerFactory.getLogger(this.getClass.getName)

  val routes: Route = concat(
    path("provide") {
      post {
        entity(as[ServiceDiscoveryRequest]) { request =>
          onSuccess(provider.provide(request)) { result =>
            log.debugN(
              "Successfully retrieved service discovery information [{}] for request [{}]",
              result.asString,
              request.id
            )
            discardEntity & complete(result)
          }
        }
      }
    }
  )
}
