package stasis.identity.api.manage

import akka.actor.ActorSystem
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.stream.Materializer
import stasis.identity.api.directives.BaseApiDirective
import stasis.identity.api.manage.requests.CreateApi
import stasis.identity.model.apis.ApiStore
import stasis.identity.model.owners.ResourceOwner

import scala.concurrent.ExecutionContext

class Apis(store: ApiStore)(implicit system: ActorSystem, override val mat: Materializer) extends BaseApiDirective {
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
  import stasis.identity.api.Formats._

  private implicit val ec: ExecutionContext = system.dispatcher
  protected def log: LoggingAdapter = Logging(system, this.getClass.getName)

  def routes(user: ResourceOwner.Id): Route =
    concat(
      pathEndOrSingleSlash {
        concat(
          get {
            onSuccess(store.apis) { apis =>
              log.info("User [{}] successfully retrieved [{}] APIs", user, apis.size)
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
                    log.info("User [{}] successfully created API [{}]", user, request.id)
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
                log.info("User [{}] successfully retrieved API [{}]", user, apiId)
                discardEntity & complete(api)
              },
              delete {
                onSuccess(store.delete(apiId)) { _ =>
                  log.info("User [{}] successfully deleted API [{}]", user, apiId)
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