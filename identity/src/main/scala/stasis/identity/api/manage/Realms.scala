package stasis.identity.api.manage

import akka.actor.ActorSystem
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.stream.Materializer
import stasis.identity.api.directives.BaseApiDirective
import stasis.identity.api.manage.requests.CreateRealm
import stasis.identity.model.owners.ResourceOwner
import stasis.identity.model.realms.RealmStore

class Realms(store: RealmStore)(implicit system: ActorSystem, override val mat: Materializer) extends BaseApiDirective {
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
  import stasis.identity.api.Formats._

  private val log: LoggingAdapter = Logging(system, this.getClass.getName)

  def routes(user: ResourceOwner.Id): Route =
    concat(
      pathEndOrSingleSlash {
        concat(
          get {
            onSuccess(store.realms) { realms =>
              log.info("User [{}] successfully retrieved [{}] realms", user, realms.size)
              discardEntity & complete(realms.values)
            }
          },
          post {
            entity(as[CreateRealm]) { request =>
              onSuccess(store.contains(request.id)) {
                case true =>
                  log.warning(
                    "User [{}] tried to create realm [{}] but it already exists",
                    user,
                    request.id
                  )
                  complete(StatusCodes.Conflict)

                case false =>
                  val realm = request.toRealm
                  onSuccess(store.put(realm)) { _ =>
                    log.info("User [{}] successfully created realm [{}]", user, realm.id)
                    complete(StatusCodes.OK)
                  }
              }
            }
          }
        )
      },
      path(Segment) { realmId =>
        concat(
          get {
            onSuccess(store.get(realmId)) {
              case Some(realm) =>
                log.info("User [{}] successfully retrieved realm [{}]", user, realmId)
                discardEntity & complete(realm)

              case None =>
                log.warning(
                  "User [{}] requested realm [{}] but none was found",
                  user,
                  realmId
                )
                discardEntity & complete(StatusCodes.NotFound)
            }
          },
          delete {
            onSuccess(store.delete(realmId)) { deleted =>
              if (deleted) {
                log.info("User [{}] successfully deleted realm [{}]", user, realmId)
                discardEntity & complete(StatusCodes.OK)
              } else {
                log.warning("User [{}] failed to delete realm [{}]", user, realmId)
                discardEntity & complete(StatusCodes.NotFound)
              }
            }
          }
        )
      }
    )
}
