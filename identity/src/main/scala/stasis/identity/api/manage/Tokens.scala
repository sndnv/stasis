package stasis.identity.api.manage

import akka.actor.ActorSystem
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import stasis.identity.model.owners.ResourceOwner
import stasis.identity.model.tokens.RefreshTokenStore

class Tokens(store: RefreshTokenStore)(implicit system: ActorSystem) {
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
  import stasis.identity.api.Formats._

  private val log: LoggingAdapter = Logging(system, this.getClass.getName)

  def routes(user: ResourceOwner.Id): Route =
    concat(
      pathEndOrSingleSlash {
        get {
          onSuccess(store.tokens) { tokens =>
            log.info("User [{}] successfully retrieved [{}] refresh tokens", user, tokens.size)
            complete(tokens.values)
          }
        }
      },
      path(JavaUUID) { clientId =>
        concat(
          get {
            onSuccess(store.get(clientId)) {
              case Some(token) =>
                log.info("User [{}] successfully retrieved refresh token for client [{}]", user, clientId)
                complete(token)

              case None =>
                log.warning(
                  "User [{}] requested a refresh token for client [{}] but none was found",
                  user,
                  clientId
                )
                complete(StatusCodes.NotFound)
            }
          },
          delete {
            onSuccess(store.delete(clientId)) { deleted =>
              if (deleted) {
                log.info("User [{}] successfully deleted refresh token for client [{}]", user, clientId)
                complete(StatusCodes.OK)
              } else {
                log.warning("User [{}] failed to delete refresh token for client [{}]", user, clientId)
                complete(StatusCodes.NotFound)
              }
            }
          }
        )
      }
    )
}
