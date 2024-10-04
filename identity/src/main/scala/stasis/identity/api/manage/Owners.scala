package stasis.identity.api.manage

import java.time.Instant

import org.apache.pekko.actor.typed.scaladsl.LoggerOps
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server._
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import stasis.identity.api.manage.requests.CreateOwner
import stasis.identity.api.manage.requests.UpdateOwner
import stasis.identity.api.manage.requests.UpdateOwnerCredentials
import stasis.identity.model.owners.ResourceOwner
import stasis.identity.model.secrets.Secret
import stasis.identity.persistence.owners.ResourceOwnerStore
import stasis.layers.api.directives.EntityDiscardingDirectives

class Owners(
  store: ResourceOwnerStore,
  ownerSecretConfig: Secret.ResourceOwnerConfig
) extends EntityDiscardingDirectives {
  import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

  import stasis.identity.api.Formats._

  private val log: Logger = LoggerFactory.getLogger(this.getClass.getName)

  private implicit val secretConfig: Secret.ResourceOwnerConfig = ownerSecretConfig

  def routes(user: ResourceOwner.Id): Route =
    concat(
      pathEndOrSingleSlash {
        concat(
          get {
            onSuccess(store.all) { owners =>
              log.debugN("User [{}] successfully retrieved [{}] resource owners", user, owners.size)
              discardEntity & complete(owners)
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
      pathPrefix("by-subject") {
        pathPrefix(Segment) { ownerSubject =>
          onSuccess(store.all) { owners =>
            owners.filter(_.subject.contains(ownerSubject)).toList match {
              case owner :: Nil =>
                concat(
                  pathEndOrSingleSlash {
                    get {
                      log.debugN(
                        "User [{}] successfully retrieved resource owner [{}] with subject [{}]",
                        user,
                        owner.username,
                        ownerSubject
                      )
                      discardEntity & complete(owner)
                    }
                  },
                  path("activate") {
                    put {
                      onSuccess(store.put(owner.copy(active = true, updated = Instant.now()))) { _ =>
                        log.debugN(
                          "User [{}] successfully activated resource owner [{}] with subject [{}]",
                          user,
                          owner.username,
                          ownerSubject
                        )
                        discardEntity & complete(StatusCodes.OK)
                      }
                    }
                  },
                  path("deactivate") {
                    put {
                      onSuccess(store.put(owner.copy(active = false, updated = Instant.now()))) { _ =>
                        log.debugN(
                          "User [{}] successfully deactivated resource owner [{}] with subject [{}]",
                          user,
                          owner.username,
                          ownerSubject
                        )
                        discardEntity & complete(StatusCodes.OK)
                      }
                    }
                  },
                  path("credentials") {
                    put {
                      entity(as[UpdateOwnerCredentials]) { request =>
                        val (secret, salt) = request.toSecret()

                        onSuccess(store.put(owner.copy(password = secret, salt = salt, updated = Instant.now()))) { _ =>
                          log.debugN(
                            "User [{}] successfully updated credentials for resource owner [{}] with subject [{}]",
                            user,
                            owner.username,
                            ownerSubject
                          )
                          complete(StatusCodes.OK)
                        }
                      }
                    }
                  }
                )

              case Nil =>
                log.debugN(
                  "User [{}] tried to access resource owner by subject [{}] but no results were found",
                  user,
                  ownerSubject
                )

                discardEntity & complete(StatusCodes.NotFound)

              case other =>
                log.debugN(
                  "User [{}] tried to access resource owner by subject [{}] but too many results were found: [{}]",
                  user,
                  ownerSubject,
                  other.length
                )

                discardEntity & complete(StatusCodes.Conflict)
            }
          }
        }
      },
      pathPrefix(Segment) { ownerUsername =>
        onSuccess(store.get(ownerUsername)) {
          case Some(owner) =>
            concat(
              path("credentials") {
                put {
                  entity(as[UpdateOwnerCredentials]) { request =>
                    val (secret, salt) = request.toSecret()

                    onSuccess(store.put(owner.copy(password = secret, salt = salt, updated = Instant.now()))) { _ =>
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
                            active = request.active,
                            updated = Instant.now()
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
  ): Owners =
    new Owners(
      store = store,
      ownerSecretConfig = ownerSecretConfig
    )
}
