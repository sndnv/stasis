package stasis.server.api.routes

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import stasis.server.model.staging.ServerStagingStore
import stasis.server.security.CurrentUser
import stasis.shared.api.responses.DeletedPendingDestaging

class Staging()(implicit ctx: RoutesContext) extends ApiRoutes {
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
  import stasis.shared.api.Formats._

  override implicit protected def mat: Materializer = ctx.mat

  def routes(implicit currentUser: CurrentUser): Route =
    concat(
      pathEndOrSingleSlash {
        get {
          resource[ServerStagingStore.View.Service] { view =>
            view.list().map { pendingDestagingOps =>
              log.debug(
                "User [{}] successfully retrieved [{}] pending destaging operations",
                currentUser,
                pendingDestagingOps.size
              )
              discardEntity & complete(pendingDestagingOps.values)
            }
          }
        }
      },
      path(JavaUUID) { crateId =>
        concat(
          delete {
            resource[ServerStagingStore.Manage.Service] { manage =>
              manage.drop(crateId).map { deleted =>
                if (deleted) {
                  log.debug("User [{}] successfully deleted destaging operation for crate [{}]", currentUser, crateId)
                } else {
                  log.warning("User [{}] failed to delete destaging operation for crate [{}]", currentUser, crateId)
                }

                discardEntity & complete(DeletedPendingDestaging(existing = deleted))
              }
            }
          }
        )
      }
    )
}
