package stasis.server.api.routes

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import stasis.server.api.requests._
import stasis.server.api.responses.{CreatedUser, DeletedUser}
import stasis.server.model.users.{User, UserStore}

import scala.concurrent.Future

object Users extends ApiRoutes {
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
  import stasis.server.api.Formats._

  private def update(
    updateRequest: UpdateUser,
    userId: User.Id
  )(implicit ctx: RoutesContext): Route =
    resources[UserStore.View.Privileged, UserStore.Manage.Privileged] { (view, manage) =>
      view.get(userId).flatMap {
        case Some(user) =>
          manage.update(updateRequest.toUpdatedUser(user)).map { _ =>
            log.info("User [{}] successfully updated user [{}]", ctx.user, userId)
            complete(StatusCodes.OK)
          }

        case None =>
          log.warning("User [{}] failed to update missing user [{}]", ctx.user, userId)
          Future.successful(complete(StatusCodes.BadRequest))
      }
    }

  def apply()(implicit ctx: RoutesContext): Route =
    concat(
      pathEndOrSingleSlash {
        concat(
          get {
            resource[UserStore.View.Privileged] { view =>
              view.list().map { users =>
                log.info("User [{}] successfully retrieved [{}] users", ctx.user, users.size)
                complete(users.values)
              }
            }
          },
          post {
            entity(as[CreateUser]) { createRequest =>
              resource[UserStore.Manage.Privileged] { manage =>
                val user = createRequest.toUser
                manage.create(user).map { _ =>
                  log.info("User [{}] successfully created user [{}]", ctx.user, user.id)
                  complete(CreatedUser(user.id))
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
                      log.info("User [{}] successfully retrieved user [{}]", ctx.user, userId)
                      complete(user)

                    case None =>
                      log.warning("User [{}] failed to retrieve user [{}]", ctx.user, userId)
                      complete(StatusCodes.NotFound)
                  }
                }
              },
              delete {
                resource[UserStore.Manage.Privileged] { manage =>
                  manage.delete(userId).map { deleted =>
                    if (deleted) {
                      log.info("User [{}] successfully deleted user [{}]", ctx.user, userId)
                    } else {
                      log.warning("User [{}] failed to delete user [{}]", ctx.user, userId)
                    }

                    complete(DeletedUser(existing = deleted))
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
                update(updateRequest, userId)
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
                view.get(ctx.user).map {
                  case Some(user) =>
                    log.info("User [{}] successfully retrieved own data", ctx.user)
                    complete(user)

                  case None =>
                    log.warning("User [{}] failed to retrieve own data [{}]", ctx.user)
                    complete(StatusCodes.NotFound)
                }
              }
            }
          },
          path("deactivate") {
            put {
              resource[UserStore.Manage.Self] { manage =>
                manage.deactivate(ctx.user).map { _ =>
                  log.info("User [{}] successfully deactivated own account", ctx.user)
                  complete(StatusCodes.OK)
                }
              }
            }
          }
        )
      }
    )
}
