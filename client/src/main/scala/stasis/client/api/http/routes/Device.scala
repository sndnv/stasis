package stasis.client.api.http.routes

import org.apache.pekko.actor.typed.scaladsl.LoggerOps
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.Route
import stasis.client.api.http.Context

class Device()(implicit context: Context) extends ApiRoutes {
  import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._
  import stasis.client.api.http.Formats.serverStateFormat
  import stasis.shared.api.Formats.deviceFormat

  def routes(): Route =
    concat(
      pathEndOrSingleSlash {
        get {
          onSuccess(context.api.device()) { device =>
            log.debugN("API successfully retrieved device [{}] for user [{}]", device.id, device.owner)
            consumeEntity & complete(device)
          }
        }
      },
      path("connections") {
        get {
          onSuccess(context.trackers.server.state) { state =>
            log.debugN("API successfully retrieved connection state for [{}] servers", state.size)
            consumeEntity & complete(state)
          }
        }
      }
    )
}

object Device {
  def apply()(implicit context: Context): Device =
    new Device()
}
