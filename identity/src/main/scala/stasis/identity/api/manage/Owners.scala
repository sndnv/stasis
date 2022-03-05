package stasis.identity.api.manage

import akka.actor.typed.scaladsl.LoggerOps
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.stream.Materializer
import org.slf4j.{Logger, LoggerFactory}
import stasis.core.api.directives.EntityDiscardingDirectives
import stasis.identity.api.manage.requests.{CreateOwner, UpdateOwner, UpdateOwnerCredentials}
import stasis.identity.model.owners.{ResourceOwner, ResourceOwnerStore}
import stasis.identity.model.secrets.Secret

class Owners(
  store: ResourceOwnerStore,
  ownerSecretConfig: Secret.ResourceOwnerConfig
)(implicit override val mat: Materializer)
    extends EntityDiscardingDirectives {
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
  import stasis.identity.api.Formats._

  private val log: Logger = LoggerFactory.getLogger(this.getClass.getName)

  private implicit val secretConfig: Secret.ResourceOwnerConfig = ownerSecretConfig

  def routes(user: ResourceOwner.Id): Route =
    concat(
      pathEndOrSingleSlash {
        concat(
          get {
            onSuccess(store.owners) { owners =>
              log.debugN("User [{}] successfully retrieved [{}] resource owners", user, owners.size)
              discardEntity & complete(owners.values)
            }
          },
          post {
            entity(as[CreateOwner]) { request =>
              onSuccess(store.contains(request.username)) {
                case true =>
                  log.warnN(
                    "User [{}] tried to create resource owner [{}] but it already exists",
                    user,
                    request.username
                  )
                  complete(StatusCodes.Conflict)

                case false =>
                  val owner = request.toResourceOwner
                  onSuccess(store.put(owner)) { _ =>
                    log.debugN("User [{}] successfully created resource owner [{}]", user, owner.username)
                    complete(StatusCodes.OK)
                  }
              }
            }
          }
        )
      },
      pathPrefix(Segment) { ownerUsername =>
        onSuccess(store.get(ownerUsername)) {
          case Some(owner) =>
            concat(
              path("credentials") {
                put {
                  entity(as[UpdateOwnerCredentials]) { request =>
                    val (secret, salt) = request.toSecret()

                    onSuccess(store.put(owner.copy(password = secret, salt = salt))) { _ =>
                      log.debugN(
                        "User [{}] successfully updated credentials for resource owner [{}]",
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
                    log.debugN(
                      "User [{}] successfully retrieved resource owner [{}]",
                      user,
                      ownerUsername
                    )
                    discardEntity & complete(owner)
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
                        log.debugN("User [{}] successfully updated resource owner [{}]", user, ownerUsername)
                        complete(StatusCodes.OK)

                      }
                    }
                  },
                  delete {
                    onSuccess(store.delete(ownerUsername)) { _ =>
                      log.debugN("User [{}] successfully deleted resource owner [{}]", user, ownerUsername)
                      discardEntity & complete(StatusCodes.OK)
                    }
                  }
                )
              }
            )

          case None =>
            log.warnN("User [{}] made request for resource owner [{}] but it was not found", user, ownerUsername)
            discardEntity & complete(StatusCodes.NotFound)
        }
      }
    )
}

object Owners {
  def apply(
    store: ResourceOwnerStore,
    ownerSecretConfig: Secret.ResourceOwnerConfig
  )(implicit mat: Materializer): Owners =
    new Owners(
      store = store,
      ownerSecretConfig = ownerSecretConfig
    )
}
