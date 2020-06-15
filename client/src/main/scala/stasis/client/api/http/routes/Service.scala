package stasis.client.api.http.routes

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import stasis.client.api.http.Context
import stasis.shared.api.responses.Ping

class Service()(implicit override val mat: Materializer, context: Context) extends ApiRoutes {
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
  import stasis.shared.api.Formats._

  def routes(): Route =
    concat(
      path("ping") {
        get {
          val response = Ping()
          log.debug("Received ping request; responding with [{}]", response.id)
          discardEntity & complete(response)
        }
      },
      path("stop") {
        put {
          log.info("Received client termination request; stopping...")
          context.terminateService()
          discardEntity & complete(StatusCodes.Accepted)
        }
      }
    )
}

object Service {
  def apply()(implicit mat: Materializer, context: Context): Service =
    new Service()
}
