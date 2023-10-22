package stasis.client.api.http.routes

import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.Route
import stasis.client.api.http.Context
import stasis.shared.api.responses.Ping

class Service()(implicit context: Context) extends ApiRoutes {
  import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._
  import stasis.shared.api.Formats._

  def routes(): Route =
    concat(
      path("ping") {
        get {
          val response = Ping()
          log.debug("Received ping request; responding with [{}]", response.id)
          consumeEntity & complete(response)
        }
      },
      path("stop") {
        put {
          log.info("Received client termination request; stopping...")
          context.terminateService()
          consumeEntity & complete(StatusCodes.NoContent)
        }
      }
    )
}

object Service {
  def apply()(implicit context: Context): Service =
    new Service()
}
