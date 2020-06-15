package stasis.identity.api.manage

import akka.actor.ActorSystem
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.stream.Materializer
import stasis.core.api.directives.EntityDiscardingDirectives
import stasis.identity.api.manage.requests.CreateApi
import stasis.identity.model.apis.ApiStore
import stasis.identity.model.owners.ResourceOwner

import scala.concurrent.ExecutionContext

class Apis(store: ApiStore)(implicit system: ActorSystem, override val mat: Materializer) extends EntityDiscardingDirectives {
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
  import stasis.identity.api.Formats._

  private implicit val ec: ExecutionContext = system.dispatcher
  private val log: LoggingAdapter = Logging(system, this.getClass.getName)

  def routes(user: ResourceOwner.Id): Route =
    concat(
      pathEndOrSingleSlash {
        concat(
          get {
            onSuccess(store.apis) { apis =>
              log.debug("User [{}] successfully retrieved [{}] APIs", user, apis.size)
              discardEntity & complete(apis.values)
            }
          },
          post {
            entity(as[CreateApi]) { request =>
              onSuccess(store.contains(request.id)) {
                case true =>
                  log.warning("User [{}] tried to create API [{}] but it already exists", user, request.id)
                  complete(StatusCodes.Conflict)

                case false =>
                  onSuccess(store.put(request.toApi)) { _ =>
                    log.debug("User [{}] successfully created API [{}]", user, request.id)
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
                log.debug("User [{}] successfully retrieved API [{}]", user, apiId)
                discardEntity & complete(api)
              },
              delete {
                onSuccess(store.delete(apiId)) { _ =>
                  log.debug("User [{}] successfully deleted API [{}]", user, apiId)
                  discardEntity & complete(StatusCodes.OK)
                }
              }
            )

          case None =>
            log.warning("User [{}] made request for API [{}] but it was not found", user, apiId)
            discardEntity & complete(StatusCodes.NotFound)
        }
      }
    )
}

object Apis {
  def apply(store: ApiStore)(implicit system: ActorSystem, mat: Materializer): Apis =
    new Apis(
      store = store
    )
}
