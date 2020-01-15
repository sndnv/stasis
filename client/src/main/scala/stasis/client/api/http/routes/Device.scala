package stasis.client.api.http.routes

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import stasis.client.api.http.Context

class Device()(implicit override val mat: Materializer, context: Context) extends ApiRoutes {
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
  import stasis.client.api.http.Formats.serverStateFormat
  import stasis.shared.api.Formats.deviceFormat

  def routes(): Route =
    concat(
      pathEndOrSingleSlash {
        get {
          onSuccess(context.api.device()) { device =>
            log.debug("API successfully retrieved device [{}] for user [{}]", device.id, device.owner)
            discardEntity & complete(device)
          }
        }
      },
      path("connections") {
        get {
          onSuccess(context.tracker.state) { state =>
            log.debug("API successfully retrieved connection state for [{}] servers", state.servers.size)
            discardEntity & complete(state.servers)
          }
        }
      }
    )
}
