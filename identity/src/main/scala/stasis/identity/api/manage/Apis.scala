package stasis.identity.api.manage

import akka.actor.ActorSystem
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.stream.Materializer
import stasis.identity.api.manage.directives.RealmValidation
import stasis.identity.api.manage.requests.CreateApi
import stasis.identity.model.apis.{Api, ApiStore}
import stasis.identity.model.owners.ResourceOwner
import stasis.identity.model.realms.Realm

import scala.concurrent.ExecutionContext

class Apis(store: ApiStore)(implicit system: ActorSystem, override val mat: Materializer) extends RealmValidation[Api] {
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
  import stasis.identity.api.Formats._

  private implicit val ec: ExecutionContext = system.dispatcher
  protected def log: LoggingAdapter = Logging(system, this.getClass.getName)

  override implicit protected def extractor: RealmValidation.Extractor[Api] = _.realm

  def routes(user: ResourceOwner.Id, realm: Realm.Id): Route =
    concat(
      pathEndOrSingleSlash {
        concat(
          get {
            filterRealm(realm, store.apis) { apis =>
              log.info("Realm [{}]: User [{}] successfully retrieved [{}] APIs", realm, user, apis.size)
              discardEntity & complete(apis.values)
            }
          },
          post {
            entity(as[CreateApi]) { request =>
              onSuccess(store.put(request.toApi(realm))) { _ =>
                log.info("Realm [{}]: User [{}] successfully created API [{}]", realm, user, request.id)
                complete(StatusCodes.OK)
              }
            }
          }
        )
      },
      path(Segment) { apiId =>
        validateRealm(realm, store.get(realm, apiId)) { api =>
          concat(
            get {
              log.info("Realm [{}]: User [{}] successfully retrieved API [{}]", realm, user, apiId)
              discardEntity & complete(api)
            },
            delete {
              onSuccess(store.delete(realm, apiId)) { _ =>
                log.info("Realm [{}]: User [{}] successfully deleted API [{}]", realm, user, apiId)
                discardEntity & complete(StatusCodes.OK)
              }
            }
          )
        }
      }
    )
}
