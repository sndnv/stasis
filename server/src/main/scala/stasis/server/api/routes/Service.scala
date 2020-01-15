package stasis.server.api.routes

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import stasis.server.security.CurrentUser
import stasis.shared.api.responses.Ping

class Service()(implicit ctx: RoutesContext) extends ApiRoutes {
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
  import stasis.shared.api.Formats._

  override implicit protected def mat: Materializer = ctx.mat

  def routes(implicit currentUser: CurrentUser): Route =
    path("ping") {
      get {
        val response = Ping()
        log.info("Received ping request from user [{}]; responding with [{}]", currentUser, response.id)
        discardEntity & complete(response)
      }
    }
}
