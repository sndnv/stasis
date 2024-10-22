package stasis.server.api.routes

import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.Route
import stasis.server.persistence.reservations.ServerReservationStore
import stasis.server.security.CurrentUser

class Reservations()(implicit ctx: RoutesContext) extends ApiRoutes {
  import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

  import stasis.core.api.Formats._

  def routes(implicit currentUser: CurrentUser): Route =
    pathEndOrSingleSlash {
      get {
        resource[ServerReservationStore.View.Service] { view =>
          view.list().map { reservations =>
            log.debug("User [{}] successfully retrieved [{}] reservations", currentUser, reservations.size)
            discardEntity & complete(reservations)
          }
        }
      }
    }

}

object Reservations {
  def apply()(implicit ctx: RoutesContext): Reservations = new Reservations()
}
