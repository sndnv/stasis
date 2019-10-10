package stasis.server.api.routes

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import stasis.server.model.reservations.ServerReservationStore
import stasis.server.security.CurrentUser

class Reservations()(implicit ctx: RoutesContext) extends ApiRoutes {
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
  import stasis.core.api.Formats._

  override implicit protected def mat: Materializer = ctx.mat

  def routes(implicit currentUser: CurrentUser): Route =
    pathEndOrSingleSlash {
      get {
        resource[ServerReservationStore.View.Service] { view =>
          view.list().map { reservations =>
            log.info("User [{}] successfully retrieved [{}] reservations", currentUser, reservations.size)
            discardEntity & complete(reservations.values)
          }
        }
      }
    }

}
