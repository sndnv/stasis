package stasis.server.api.routes

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import stasis.server.model.users.UserStore
import stasis.server.security.CurrentUser
import stasis.shared.api.requests._
import stasis.shared.api.responses.{CreatedUser, DeletedUser}
import stasis.shared.model.users.User

import scala.concurrent.Future

class Users()(implicit ctx: RoutesContext) extends ApiRoutes {
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
  import stasis.shared.api.Formats._

  override implicit protected def mat: Materializer = ctx.mat

  def routes(implicit currentUser: CurrentUser): Route =
    concat(
      pathEndOrSingleSlash {
        concat(
          get {
            resource[UserStore.View.Privileged] { view =>
              view.list().map { users =>
                log.info("User [{}] successfully retrieved [{}] users", currentUser, users.size)
                discardEntity & complete(users.values)
              }
            }
          },
          post {
            entity(as[CreateUser]) { createRequest =>
              resource[UserStore.Manage.Privileged] { manage =>
                val user = createRequest.toUser
                manage.create(user).map { _ =>
                  log.info("User [{}] successfully created user [{}]", currentUser, user.id)
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
                      log.info("User [{}] successfully retrieved user [{}]", currentUser, userId)
                      discardEntity & complete(user)

                    case None =>
                      log.warning("User [{}] failed to retrieve user [{}]", currentUser, userId)
                      discardEntity & complete(StatusCodes.NotFound)
                  }
                }
              },
              delete {
                resource[UserStore.Manage.Privileged] { manage =>
                  manage.delete(userId).map { deleted =>
                    if (deleted) {
                      log.info("User [{}] successfully deleted user [{}]", currentUser, userId)
                    } else {
                      log.warning("User [{}] failed to delete user [{}]", currentUser, userId)
                    }

                    discardEntity & complete(DeletedUser(existing = deleted))
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
                view.get(currentUser).map {
                  case Some(user) =>
                    log.info("User [{}] successfully retrieved own data", currentUser)
                    discardEntity & complete(user)

                  case None =>
                    log.warning("User [{}] failed to retrieve own data [{}]", currentUser)
                    discardEntity & complete(StatusCodes.NotFound)
                }
              }
            }
          },
          path("deactivate") {
            put {
              resource[UserStore.Manage.Self] { manage =>
                manage.deactivate(currentUser).map { _ =>
                  log.info("User [{}] successfully deactivated own account", currentUser)
                  complete(StatusCodes.OK)
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
            log.info("User [{}] successfully updated user [{}]", currentUser, userId)
            complete(StatusCodes.OK)
          }

        case None =>
          log.warning("User [{}] failed to update missing user [{}]", currentUser, userId)
          Future.successful(complete(StatusCodes.BadRequest))
      }
    }
}
