package stasis.server.api.routes

import org.apache.pekko.actor.typed.scaladsl.LoggerOps
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.Route

import stasis.server.model.staging.ServerStagingStore
import stasis.server.security.CurrentUser
import stasis.shared.api.responses.DeletedPendingDestaging

class Staging()(implicit ctx: RoutesContext) extends ApiRoutes {
  import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

  import stasis.shared.api.Formats._

  def routes(implicit currentUser: CurrentUser): Route =
    concat(
      pathEndOrSingleSlash {
        get {
          resource[ServerStagingStore.View.Service] { view =>
            view.list().map { pendingDestagingOps =>
              log.debugN(
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
                  log.debugN("User [{}] successfully deleted destaging operation for crate [{}]", currentUser, crateId)
                } else {
                  log.warnN("User [{}] failed to delete destaging operation for crate [{}]", currentUser, crateId)
                }

                discardEntity & complete(DeletedPendingDestaging(existing = deleted))
              }
            }
          }
        )
      }
    )
}

object Staging {
  def apply()(implicit ctx: RoutesContext): Staging = new Staging()
}
