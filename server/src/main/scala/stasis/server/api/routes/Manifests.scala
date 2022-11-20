package stasis.server.api.routes

import akka.actor.typed.scaladsl.LoggerOps
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import stasis.server.model.manifests.ServerManifestStore
import stasis.server.security.CurrentUser
import stasis.shared.api.responses.DeletedManifest

class Manifests()(implicit ctx: RoutesContext) extends ApiRoutes {
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
  import stasis.core.api.Formats._
  import stasis.shared.api.Formats._

  def routes(implicit currentUser: CurrentUser): Route =
    pathPrefix(JavaUUID) { crateId =>
      pathEndOrSingleSlash {
        concat(
          get {
            resource[ServerManifestStore.View.Service] { view =>
              view.get(crateId).map {
                case Some(manifest) =>
                  log.debugN("User [{}] successfully retrieved manifest for crate [{}]", currentUser, crateId)
                  discardEntity & complete(manifest)

                case None =>
                  log.warnN("User [{}] failed to retrieve manifest for crate [{}]", currentUser, crateId)
                  discardEntity & complete(StatusCodes.NotFound)
              }
            }
          },
          delete {
            resource[ServerManifestStore.Manage.Service] { manage =>
              manage.delete(crateId).map { deleted =>
                if (deleted) {
                  log.debugN("User [{}] successfully deleted manifest for crate [{}]", currentUser, crateId)
                } else {
                  log.warnN("User [{}] failed to delete manifest for crate [{}]", currentUser, crateId)
                }

                discardEntity & complete(DeletedManifest(existing = deleted))
              }
            }
          }
        )
      }
    }
}

object Manifests {
  def apply()(implicit ctx: RoutesContext): Manifests = new Manifests()
}
