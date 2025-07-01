package stasis.identity.api.manage

import org.apache.pekko.actor.typed.scaladsl.LoggerOps
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server._
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import stasis.identity.model.owners.ResourceOwner
import stasis.identity.model.tokens.RefreshToken
import stasis.identity.persistence.tokens.RefreshTokenStore
import io.github.sndnv.layers.api.directives.EntityDiscardingDirectives

class Tokens(store: RefreshTokenStore) extends EntityDiscardingDirectives {
  import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

  import stasis.identity.api.Formats._

  private val log: Logger = LoggerFactory.getLogger(this.getClass.getName)

  def routes(user: ResourceOwner.Id): Route =
    concat(
      pathEndOrSingleSlash {
        get {
          onSuccess(store.all) { tokens =>
            log.debugN("User [{}] successfully retrieved [{}] refresh tokens", user, tokens.size)
            discardEntity & complete(tokens)
          }
        }
      },
      path(Segment) { token =>
        concat(
          get {
            onSuccess(store.get(RefreshToken(token))) {
              case Some(storedToken) =>
                log.debugN(
                  "User [{}] successfully retrieved refresh token for client [{}] and owner [{}]",
                  user,
                  storedToken.client,
                  storedToken.owner
                )
                discardEntity & complete(storedToken)

              case None =>
                log.warnN(
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
                log.debugN("User [{}] successfully deleted refresh token [{}]", user, token)
                discardEntity & complete(StatusCodes.OK)
              } else {
                log.warnN("User [{}] failed to delete refresh token [{}]", user, token)
                discardEntity & complete(StatusCodes.NotFound)
              }
            }
          }
        )
      }
    )
}

object Tokens {
  def apply(store: RefreshTokenStore): Tokens = new Tokens(store = store)
}
