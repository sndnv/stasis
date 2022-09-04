package stasis.server.api.routes

import akka.actor.typed.scaladsl.LoggerOps
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import stasis.server.security.CurrentUser
import stasis.shared.api.responses.Ping

class Service()(implicit ctx: RoutesContext) extends ApiRoutes {
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
  import stasis.shared.api.Formats._

  def routes(implicit currentUser: CurrentUser): Route =
    concat(
      path("ping") {
        get {
          val response = Ping()
          log.debugN("Received ping request from user [{}]; responding with [{}]", currentUser, response.id)
          discardEntity & complete(response)
        }
      },
      path("health") {
        complete(StatusCodes.OK)
      }
    )
}

object Service {
  def apply()(implicit ctx: RoutesContext): Service = new Service()
}
