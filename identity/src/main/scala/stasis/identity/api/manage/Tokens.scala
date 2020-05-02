package stasis.identity.api.manage

import akka.actor.ActorSystem
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.stream.Materializer
import stasis.core.api.directives.EntityDiscardingDirectives
import stasis.identity.model.owners.ResourceOwner
import stasis.identity.model.tokens.{RefreshToken, RefreshTokenStore}

class Tokens(store: RefreshTokenStore)(implicit system: ActorSystem, override val mat: Materializer)
    extends EntityDiscardingDirectives {
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
  import stasis.identity.api.Formats._

  private val log: LoggingAdapter = Logging(system, this.getClass.getName)

  def routes(user: ResourceOwner.Id): Route =
    concat(
      pathEndOrSingleSlash {
        get {
          onSuccess(store.tokens) { tokens =>
            log.debug("User [{}] successfully retrieved [{}] refresh tokens", user, tokens.size)
            discardEntity & complete(tokens.values)
          }
        }
      },
      path(Segment) { token =>
        concat(
          get {
            onSuccess(store.get(RefreshToken(token))) {
              case Some(storedToken) =>
                log.debug(
                  "User [{}] successfully retrieved refresh token for client [{}] and owner [{}]",
                  user,
                  storedToken.client,
                  storedToken.owner.username
                )
                discardEntity & complete(storedToken)

              case None =>
                log.warning(
                  "User [{}] requested refresh token [{}] but it was not found",
                  user,
                  token
                )
                discardEntity & complete(StatusCodes.NotFound)
            }
          },
          delete {
            onSuccess(store.delete(RefreshToken(token))) { deleted =>
              if (deleted) {
                log.debug("User [{}] successfully deleted refresh token [{}]", user, token)
                discardEntity & complete(StatusCodes.OK)
              } else {
                log.warning("User [{}] failed to delete refresh token [{}]", user, token)
                discardEntity & complete(StatusCodes.NotFound)
              }
            }
          }
        )
      }
    )
}
