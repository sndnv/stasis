package stasis.identity.api.manage

import akka.actor.ActorSystem
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.stream.Materializer
import stasis.identity.api.manage.directives.RealmValidation
import stasis.identity.api.manage.requests.{CreateOwner, UpdateOwner, UpdateOwnerCredentials}
import stasis.identity.model.owners.{ResourceOwner, ResourceOwnerStore}
import stasis.identity.model.realms.Realm
import stasis.identity.model.secrets.Secret

import scala.concurrent.ExecutionContext

class Owners(
  store: ResourceOwnerStore,
  ownerSecretConfig: Secret.ResourceOwnerConfig
)(implicit system: ActorSystem, materializer: Materializer)
    extends RealmValidation[ResourceOwner] {
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
  import stasis.identity.api.Formats._

  override implicit protected def mat: Materializer = materializer
  private implicit val ec: ExecutionContext = system.dispatcher
  protected def log: LoggingAdapter = Logging(system, this.getClass.getName)

  override implicit protected def extractor: RealmValidation.Extractor[ResourceOwner] = _.realm

  private implicit val secretConfig: Secret.ResourceOwnerConfig = ownerSecretConfig

  def routes(user: ResourceOwner.Id, realm: Realm.Id): Route =
    concat(
      pathEndOrSingleSlash {
        concat(
          get {
            filterRealm(realm, store.owners) { owners =>
              log.info(
                "Realm [{}]: User [{}] successfully retrieved [{}] resource owners",
                realm,
                user,
                owners.size
              )
              complete(owners.values)
            }
          },
          post {
            entity(as[CreateOwner]) { request =>
              val owner = request.toResourceOwner(realm)
              onSuccess(store.put(owner)) { _ =>
                log.info(
                  "Realm [{}]: User [{}] successfully created resource owner [{}]",
                  realm,
                  user,
                  owner.username
                )
                complete(StatusCodes.OK)
              }
            }
          }
        )
      },
      pathPrefix(Segment) { ownerUsername =>
        validateRealm(realm, store.get(ownerUsername)) {
          owner =>
            concat(
              path("credentials") {
                put {
                  entity(as[UpdateOwnerCredentials]) { request =>
                    val (secret, salt) = request.toSecret()

                    onSuccess(store.put(owner.copy(password = secret, salt = salt))) { _ =>
                      log.info(
                        "Realm [{}]: User [{}] successfully updated credentials for resource owner [{}]",
                        realm,
                        user,
                        ownerUsername
                      )
                      complete(StatusCodes.OK)
                    }
                  }
                }
              },
              pathEndOrSingleSlash {
                concat(
                  get {
                    log.info(
                      "Realm [{}]: User [{}] successfully retrieved resource owner [{}]",
                      realm,
                      user,
                      ownerUsername
                    )
                    complete(owner)
                  },
                  put {
                    entity(as[UpdateOwner]) { request =>
                      onSuccess(
                        store.put(
                          owner.copy(
                            allowedScopes = request.allowedScopes,
                            active = request.active
                          )
                        )
                      ) { _ =>
                        log.info(
                          "Realm [{}]: User [{}] successfully updated resource owner [{}]",
                          realm,
                          user,
                          ownerUsername
                        )
                        complete(StatusCodes.OK)

                      }
                    }
                  },
                  delete {
                    onSuccess(store.delete(ownerUsername)) { _ =>
                      log.info(
                        "Realm [{}]: User [{}] successfully deleted resource owner [{}]",
                        realm,
                        user,
                        ownerUsername
                      )
                      complete(StatusCodes.OK)
                    }
                  }
                )
              }
            )
        }
      }
    )
}
