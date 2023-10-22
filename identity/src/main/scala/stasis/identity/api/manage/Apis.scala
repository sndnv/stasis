package stasis.identity.api.manage

import org.apache.pekko.actor.typed.scaladsl.LoggerOps
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server._
import org.slf4j.{Logger, LoggerFactory}
import stasis.core.api.directives.EntityDiscardingDirectives
import stasis.identity.api.manage.requests.CreateApi
import stasis.identity.model.apis.ApiStore
import stasis.identity.model.owners.ResourceOwner

class Apis(store: ApiStore) extends EntityDiscardingDirectives {
  import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._
  import stasis.identity.api.Formats._

  private val log: Logger = LoggerFactory.getLogger(this.getClass.getName)

  def routes(user: ResourceOwner.Id): Route =
    concat(
      pathEndOrSingleSlash {
        concat(
          get {
            onSuccess(store.apis) { apis =>
              log.debugN("User [{}] successfully retrieved [{}] APIs", user, apis.size)
              discardEntity & complete(apis.values)
            }
          },
          post {
            entity(as[CreateApi]) { request =>
              onSuccess(store.contains(request.id)) {
                case true =>
                  log.warnN("User [{}] tried to create API [{}] but it already exists", user, request.id)
                  complete(StatusCodes.Conflict)

                case false =>
                  onSuccess(store.put(request.toApi)) { _ =>
                    log.debugN("User [{}] successfully created API [{}]", user, request.id)
                    complete(StatusCodes.OK)
                  }
              }
            }
          }
        )
      },
      path(Segment) { apiId =>
        onSuccess(store.get(apiId)) {
          case Some(api) =>
            concat(
              get {
                log.debugN("User [{}] successfully retrieved API [{}]", user, apiId)
                discardEntity & complete(api)
              },
              delete {
                onSuccess(store.delete(apiId)) { _ =>
                  log.debugN("User [{}] successfully deleted API [{}]", user, apiId)
                  discardEntity & complete(StatusCodes.OK)
                }
              }
            )

          case None =>
            log.warnN("User [{}] made request for API [{}] but it was not found", user, apiId)
            discardEntity & complete(StatusCodes.NotFound)
        }
      }
    )
}

object Apis {
  def apply(store: ApiStore): Apis = new Apis(store = store)
}
