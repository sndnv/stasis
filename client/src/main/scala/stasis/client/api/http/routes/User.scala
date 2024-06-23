package stasis.client.api.http.routes

import org.apache.pekko.Done
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.Route
import stasis.client.api.http.Context
import stasis.client.encryption.secrets.UserPassword
import stasis.shared.api.requests.ResetUserPassword
import stasis.shared.api.requests.UpdateUserPasswordOwn
import stasis.shared.api.requests.UpdateUserSaltOwn
import stasis.shared.secrets.SecretsConfig

class User()(implicit context: Context) extends ApiRoutes {
  import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._
  import stasis.shared.api.Formats._

  def routes(): Route =
    concat(
      pathEndOrSingleSlash {
        get {
          onSuccess(context.api.user()) { user =>
            log.debug("API successfully retrieved user [{}]", user.id)
            consumeEntity & complete(user)
          }
        }
      },
      path("password") {
        put {
          entity(as[UpdateUserPasswordOwn]) { updateRequest =>
            extractExecutionContext { implicit ec =>
              implicit val secrets: SecretsConfig = context.secretsConfig

              if (context.handlers.verifyUserPassword(updateRequest.currentPassword.toCharArray)) {
                val result = for {
                  user <- context.api.user()
                  newUserPassword = UserPassword(
                    user = user.id,
                    salt = user.salt,
                    password = updateRequest.newPassword.toCharArray
                  )
                  _ <- context.api.resetUserPassword(
                    ResetUserPassword(rawPassword = newUserPassword.toAuthenticationPassword.extract())
                  )
                  _ <- context.handlers.updateUserCredentials(updateRequest.newPassword.toCharArray, user.salt)
                } yield {
                  log.debug("API successfully updated password for user [{}]", user.id)
                  Done
                }

                onSuccess(result) { _ =>
                  complete(StatusCodes.OK)
                }
              } else {
                log.warn("API failed to update password; invalid current password provided")
                complete(StatusCodes.Conflict)
              }
            }
          }
        }
      },
      path("salt") {
        put {
          entity(as[UpdateUserSaltOwn]) { updateRequest =>
            extractExecutionContext { implicit ec =>
              if (context.handlers.verifyUserPassword(updateRequest.currentPassword.toCharArray)) {
                val result = for {
                  user <- context.api.user()
                  _ <- context.handlers.updateUserCredentials(updateRequest.currentPassword.toCharArray, updateRequest.newSalt)
                } yield {
                  log.debug("API successfully updated salt for user [{}]", user.id)
                  Done
                }

                onSuccess(result) { _ =>
                  complete(StatusCodes.OK)
                }
              } else {
                log.warn("API failed to update salt; invalid current password provided")
                complete(StatusCodes.Conflict)
              }
            }
          }
        }
      }
    )
}

object User {
  def apply()(implicit context: Context): User =
    new User()
}
