package stasis.server.api.routes

import akka.event.LoggingAdapter
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import stasis.server.api.requests._
import stasis.server.api.responses.{CreatedUser, DeletedUser}
import stasis.server.model.users.{User, UserStore}
import stasis.server.security.ResourceProvider

import scala.concurrent.ExecutionContext

object Users {
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
  import stasis.server.api.Formats._

  private def update(
    resourceProvider: ResourceProvider,
    currentUser: User.Id,
    updateRequest: UpdateUser,
    userId: User.Id
  )(implicit ec: ExecutionContext, log: LoggingAdapter): Route =
    onSuccess(
      resourceProvider.provide[UserStore.View.Privileged](currentUser).flatMap(_.get(userId))
    ) {
      case Some(user) =>
        onSuccess(
          resourceProvider
            .provide[UserStore.Manage.Privileged](currentUser)
            .flatMap(_.update(updateRequest.toUpdatedUser(user)))
        ) { _ =>
          log.info("User [{}] successfully updated user [{}]", currentUser, userId)
          complete(StatusCodes.OK)
        }

      case None =>
        log.warning("User [{}] failed to update missing user [{}]", currentUser, userId)
        complete(StatusCodes.BadRequest)
    }

  def apply(
    resourceProvider: ResourceProvider,
    currentUser: User.Id
  )(implicit ec: ExecutionContext, log: LoggingAdapter): Route =
    concat(
      pathEndOrSingleSlash {
        concat(
          get {
            onSuccess(
              resourceProvider.provide[UserStore.View.Privileged](currentUser).flatMap(_.list())
            ) { users =>
              log.info("User [{}] successfully retrieved [{}] users", currentUser, users.size)
              complete(users.values)
            }
          },
          post {
            entity(as[CreateUser]) { createRequest =>
              val user = createRequest.toUser
              onSuccess(
                resourceProvider.provide[UserStore.Manage.Privileged](currentUser).flatMap(_.create(user))
              ) { _ =>
                log.info("User [{}] successfully created user [{}]", currentUser, user.id)
                complete(CreatedUser(user.id))
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
                onSuccess(
                  resourceProvider.provide[UserStore.View.Privileged](currentUser).flatMap(_.get(userId))
                ) {
                  case Some(user) =>
                    log.info("User [{}] successfully retrieved user [{}]", currentUser, userId)
                    complete(user)

                  case None =>
                    log.warning("User [{}] failed to retrieve user [{}]", currentUser, userId)
                    complete(StatusCodes.NotFound)
                }
              },
              delete {
                onSuccess(
                  resourceProvider.provide[UserStore.Manage.Privileged](currentUser).flatMap(_.delete(userId))
                ) { deleted =>
                  if (deleted) {
                    log.info("User [{}] successfully deleted user [{}]", currentUser, userId)
                  } else {
                    log.warning("User [{}] failed to delete user [{}]", currentUser, userId)
                  }

                  complete(DeletedUser(existing = deleted))
                }
              }
            )
          },
          path("limits") {
            put {
              entity(as[UpdateUserLimits]) { updateRequest =>
                update(resourceProvider, currentUser, updateRequest, userId)
              }
            }
          },
          path("permissions") {
            put {
              entity(as[UpdateUserPermissions]) { updateRequest =>
                update(resourceProvider, currentUser, updateRequest, userId)
              }
            }
          },
          path("state") {
            put {
              entity(as[UpdateUserState]) { updateRequest =>
                update(resourceProvider, currentUser, updateRequest, userId)
              }
            }
          }
        )
      },
      pathPrefix("self") {
        concat(
          pathEndOrSingleSlash {
            get {
              onSuccess(
                resourceProvider.provide[UserStore.View.Self](currentUser).flatMap(_.get(currentUser))
              ) {
                case Some(user) =>
                  log.info("User [{}] successfully retrieved own data", currentUser)
                  complete(user)

                case None =>
                  log.warning("User [{}] failed to retrieve own data [{}]", currentUser)
                  complete(StatusCodes.NotFound)
              }
            }
          },
          path("deactivate") {
            put {
              onSuccess(
                resourceProvider.provide[UserStore.Manage.Self](currentUser).flatMap(_.deactivate(currentUser))
              ) { _ =>
                log.info("User [{}] successfully deactivated own account", currentUser)
                complete(StatusCodes.OK)
              }
            }
          }
        )
      }
    )
}
