package stasis.server.api.routes

import akka.actor.typed.scaladsl.LoggerOps
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import stasis.server.model.users.UserStore
import stasis.server.security.CurrentUser
import stasis.server.security.users.UserCredentialsManager
import stasis.shared.api.requests._
import stasis.shared.api.responses.{CreatedUser, DeletedUser, UpdatedUserSalt}
import stasis.shared.model.users.User
import stasis.shared.secrets.{DerivedPasswords, SecretsConfig}

import scala.concurrent.Future

class Users(
  credentialsManager: UserCredentialsManager,
  secretsConfig: SecretsConfig
)(implicit ctx: RoutesContext)
    extends ApiRoutes {
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
  import stasis.shared.api.Formats._

  def routes(implicit currentUser: CurrentUser): Route =
    concat(
      pathEndOrSingleSlash {
        concat(
          get {
            resource[UserStore.View.Privileged] { view =>
              view.list().map { users =>
                log.debugN("User [{}] successfully retrieved [{}] users", currentUser, users.size)
                discardEntity & complete(users.values)
              }
            }
          },
          post {
            entity(as[CreateUser]) { createRequest =>
              resource[UserStore.Manage.Privileged] { manage =>
                val user = createRequest.toUser(withSalt = manage.generateSalt())

                val authenticationPassword = if (secretsConfig.derivation.authentication.enabled) {
                  DerivedPasswords.encode(
                    hashedPassword = DerivedPasswords.deriveHashedAuthenticationPassword(
                      password = createRequest.rawPassword.toCharArray,
                      saltPrefix = secretsConfig.derivation.authentication.saltPrefix,
                      salt = user.salt,
                      iterations = secretsConfig.derivation.authentication.iterations,
                      derivedKeySize = secretsConfig.derivation.authentication.secretSize
                    )
                  )
                } else {
                  createRequest.rawPassword
                }

                credentialsManager
                  .createResourceOwner(
                    user = user,
                    username = createRequest.username,
                    rawPassword = authenticationPassword
                  )
                  .map {
                    case UserCredentialsManager.Result.Success =>
                      onSuccess(manage.create(user)) { _ =>
                        log.debugN("User [{}] successfully created user [{}]", currentUser, user.id)
                        complete(CreatedUser(user.id))
                      }

                    case UserCredentialsManager.Result.NotFound(message) =>
                      log.errorN(
                        "User [{}] failed to create user [{}] via [{}]: [{}]",
                        currentUser,
                        user.id,
                        credentialsManager.id,
                        message
                      )
                      complete(StatusCodes.NotFound)

                    case UserCredentialsManager.Result.Conflict(message) =>
                      log.errorN(
                        "User [{}] failed to create user [{}] via [{}]: [{}]",
                        currentUser,
                        user.id,
                        credentialsManager.id,
                        message
                      )
                      complete(StatusCodes.Conflict)
                  }
              }
            }
          }
        )
      },
      pathPrefix(JavaUUID) { userId =>
        concat(
          pathEndOrSingleSlash {
            concat(
              get {
                resource[UserStore.View.Privileged] { view =>
                  view.get(userId).map {
                    case Some(user) =>
                      log.debugN("User [{}] successfully retrieved user [{}]", currentUser, userId)
                      discardEntity & complete(user)

                    case None =>
                      log.warnN("User [{}] failed to retrieve user [{}]", currentUser, userId)
                      discardEntity & complete(StatusCodes.NotFound)
                  }
                }
              },
              delete {
                resource[UserStore.Manage.Privileged] { manage =>
                  credentialsManager
                    .deactivateResourceOwner(user = userId)
                    .map {
                      case UserCredentialsManager.Result.Success =>
                        onSuccess(manage.delete(userId)) { deleted =>
                          if (deleted) {
                            log.debugN("User [{}] successfully deleted user [{}]", currentUser, userId)
                          } else {
                            log.warnN("User [{}] failed to delete user [{}]", currentUser, userId)
                          }

                          discardEntity & complete(DeletedUser(existing = deleted))
                        }

                      case UserCredentialsManager.Result.NotFound(message) =>
                        log.errorN(
                          "User [{}] failed to disable user [{}] via [{}]: [{}]",
                          currentUser,
                          userId,
                          credentialsManager.id,
                          message
                        )
                        complete(StatusCodes.NotFound)

                      case UserCredentialsManager.Result.Conflict(message) =>
                        log.errorN(
                          "User [{}] failed to disable user [{}] via [{}]: [{}]",
                          currentUser,
                          userId,
                          credentialsManager.id,
                          message
                        )
                        complete(StatusCodes.Conflict)
                    }
                }
              }
            )
          },
          path("limits") {
            put {
              entity(as[UpdateUserLimits]) { updateRequest =>
                update(updateRequest, userId)
              }
            }
          },
          path("permissions") {
            put {
              entity(as[UpdateUserPermissions]) { updateRequest =>
                update(updateRequest, userId)
              }
            }
          },
          path("state") {
            put {
              entity(as[UpdateUserState]) { updateRequest =>
                val updated = if (updateRequest.active) {
                  credentialsManager.activateResourceOwner(user = userId)
                } else {
                  credentialsManager.deactivateResourceOwner(user = userId)
                }

                val result = updated.map {
                  case UserCredentialsManager.Result.Success =>
                    update(
                      updateRequest = updateRequest,
                      userId = userId
                    )

                  case UserCredentialsManager.Result.NotFound(message) =>
                    log.errorN(
                      "User [{}] failed to update state for user [{}] via [{}]: [{}]",
                      currentUser,
                      userId,
                      credentialsManager.id,
                      message
                    )
                    complete(StatusCodes.NotFound)

                  case UserCredentialsManager.Result.Conflict(message) =>
                    log.errorN(
                      "User [{}] failed to update state for user [{}] via [{}]: [{}]",
                      currentUser,
                      userId,
                      credentialsManager.id,
                      message
                    )
                    complete(StatusCodes.Conflict)
                }

                onSuccess(result)(identity)
              }
            }
          },
          path("password") {
            put {
              entity(as[UpdateUserPassword]) { updateRequest =>
                resource[UserStore.Manage.Privileged] { manage =>
                  val salt = manage.generateSalt()

                  val authenticationPassword = if (secretsConfig.derivation.authentication.enabled) {
                    DerivedPasswords.encode(
                      hashedPassword = DerivedPasswords.deriveHashedAuthenticationPassword(
                        password = updateRequest.rawPassword.toCharArray,
                        saltPrefix = secretsConfig.derivation.authentication.saltPrefix,
                        salt = salt,
                        iterations = secretsConfig.derivation.authentication.iterations,
                        derivedKeySize = secretsConfig.derivation.authentication.secretSize
                      )
                    )
                  } else {
                    updateRequest.rawPassword
                  }

                  credentialsManager
                    .setResourceOwnerPassword(
                      user = userId,
                      rawPassword = authenticationPassword
                    )
                    .map {
                      case UserCredentialsManager.Result.Success =>
                        log.debugN(
                          "User [{}] successfully updated password for user [{}] via [{}]",
                          currentUser,
                          userId,
                          credentialsManager.id
                        )
                        update(
                          updateRequest = UpdateUserSalt(salt),
                          userId = userId
                        )

                      case UserCredentialsManager.Result.NotFound(message) =>
                        log.errorN(
                          "User [{}] failed to update password for user [{}] via [{}]: [{}]",
                          currentUser,
                          userId,
                          credentialsManager.id,
                          message
                        )
                        complete(StatusCodes.NotFound)

                      case UserCredentialsManager.Result.Conflict(message) =>
                        log.errorN(
                          "User [{}] failed to update password for user [{}] via [{}]: [{}]",
                          currentUser,
                          userId,
                          credentialsManager.id,
                          message
                        )
                        complete(StatusCodes.Conflict)
                    }
                }
              }
            }
          }
        )
      },
      pathPrefix("self") {
        concat(
          pathEndOrSingleSlash {
            get {
              resource[UserStore.View.Self] { view =>
                view.get(currentUser).map {
                  case Some(user) =>
                    log.debugN("User [{}] successfully retrieved own data", currentUser)
                    discardEntity & complete(user)

                  case None =>
                    log.warnN("User [{}] failed to retrieve own data [{}]", currentUser)
                    discardEntity & complete(StatusCodes.NotFound)
                }
              }
            }
          },
          path("deactivate") {
            put {
              resource[UserStore.Manage.Self] { manage =>
                credentialsManager.deactivateResourceOwner(currentUser.id).map {
                  case UserCredentialsManager.Result.Success =>
                    onSuccess(manage.deactivate(currentUser)) { _ =>
                      log.debugN("User [{}] successfully deactivated own account", currentUser)
                      complete(StatusCodes.OK)
                    }

                  case UserCredentialsManager.Result.NotFound(message) =>
                    log.errorN(
                      "User [{}] failed to disable own account via [{}]: [{}]",
                      currentUser,
                      credentialsManager.id,
                      message
                    )
                    complete(StatusCodes.NotFound)

                  case UserCredentialsManager.Result.Conflict(message) =>
                    log.errorN(
                      "User [{}] failed to disable own account via [{}]: [{}]",
                      currentUser,
                      credentialsManager.id,
                      message
                    )
                    complete(StatusCodes.Conflict)
                }
              }
            }
          },
          path("salt") {
            put {
              resources[UserStore.Manage.Self, UserStore.View.Self] { case (manage, view) =>
                view.get(currentUser).flatMap {
                  case Some(_) =>
                    manage.resetSalt(currentUser).map { salt =>
                      log.debugN("User [{}] successfully updated own password salt", currentUser)
                      discardEntity & complete(UpdatedUserSalt(salt))
                    }

                  case None =>
                    log.warnN("User [{}] failed to update own password salt; user not found", currentUser)
                    Future.successful(discardEntity & complete(StatusCodes.NotFound))
                }
              }
            }
          },
          path("password") {
            put {
              entity(as[UpdateUserPassword]) { updateRequest =>
                resources[UserStore.Manage.Self, UserStore.View.Self] { case (_, view) =>
                  view.get(currentUser).flatMap {
                    case Some(_) =>
                      // when users reset their own password, hashing happens on the client side
                      credentialsManager.setResourceOwnerPassword(currentUser.id, updateRequest.rawPassword).map {
                        case UserCredentialsManager.Result.Success =>
                          log.debugN("User [{}] successfully updated own password", currentUser)
                          complete(StatusCodes.OK)

                        case UserCredentialsManager.Result.NotFound(message) =>
                          log.errorN(
                            "User [{}] failed to update own password via [{}]: [{}]",
                            currentUser,
                            credentialsManager.id,
                            message
                          )
                          complete(StatusCodes.NotFound)

                        case UserCredentialsManager.Result.Conflict(message) =>
                          log.errorN(
                            "User [{}] failed to update own password via [{}]: [{}]",
                            currentUser,
                            credentialsManager.id,
                            message
                          )
                          complete(StatusCodes.Conflict)
                      }

                    case None =>
                      log.warnN("User [{}] failed to update own password; user not found", currentUser)
                      Future.successful(complete(StatusCodes.NotFound))
                  }
                }
              }
            }
          }
        )
      }
    )

  private def update(
    updateRequest: UpdateUser,
    userId: User.Id
  )(implicit ctx: RoutesContext, currentUser: CurrentUser): Route =
    resources[UserStore.View.Privileged, UserStore.Manage.Privileged] { (view, manage) =>
      view.get(userId).flatMap {
        case Some(user) =>
          manage.update(updateRequest.toUpdatedUser(user)).map { _ =>
            log.debugN("User [{}] successfully updated user [{}]", currentUser, userId)
            complete(StatusCodes.OK)
          }

        case None =>
          log.warnN("User [{}] failed to update missing user [{}]", currentUser, userId)
          Future.successful(complete(StatusCodes.BadRequest))
      }
    }
}

object Users {
  def apply(
    credentialsManager: UserCredentialsManager,
    secretsConfig: SecretsConfig
  )(implicit ctx: RoutesContext): Users =
    new Users(credentialsManager, secretsConfig)
}
