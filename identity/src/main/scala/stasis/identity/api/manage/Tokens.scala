package stasis.identity.api.manage

import akka.actor.ActorSystem
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.stream.Materializer
import stasis.identity.api.directives.BaseApiDirective
import stasis.identity.model.owners.ResourceOwner
import stasis.identity.model.tokens.RefreshTokenStore

class Tokens(store: RefreshTokenStore)(implicit system: ActorSystem, override val mat: Materializer)
    extends BaseApiDirective {
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
  import stasis.identity.api.Formats._

  private val log: LoggingAdapter = Logging(system, this.getClass.getName)

  def routes(user: ResourceOwner.Id): Route =
    concat(
      pathEndOrSingleSlash {
        get {
          onSuccess(store.tokens) { tokens =>
            log.info("User [{}] successfully retrieved [{}] refresh tokens", user, tokens.size)
            discardEntity & complete(tokens)
          }
        }
      },
      path(JavaUUID) { clientId =>
        concat(
          get {
            onSuccess(store.get(clientId)) {
              case Some(token) =>
                log.info("User [{}] successfully retrieved refresh token for client [{}]", user, clientId)
                discardEntity & complete(token)

              case None =>
                log.warning(
                  "User [{}] requested a refresh token for client [{}] but none was found",
                  user,
                  clientId
                )
                discardEntity & complete(StatusCodes.NotFound)
            }
          },
          delete {
            onSuccess(store.delete(clientId)) { deleted =>
              if (deleted) {
                log.info("User [{}] successfully deleted refresh token for client [{}]", user, clientId)
                discardEntity & complete(StatusCodes.OK)
              } else {
                log.warning("User [{}] failed to delete refresh token for client [{}]", user, clientId)
                discardEntity & complete(StatusCodes.NotFound)
              }
            }
          }
        )
      }
    )
}
