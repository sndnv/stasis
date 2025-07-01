package stasis.identity.api.manage

import org.apache.pekko.actor.typed.scaladsl.LoggerOps
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server._
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import stasis.identity.model.codes.AuthorizationCode
import stasis.identity.model.owners.ResourceOwner
import stasis.identity.persistence.codes.AuthorizationCodeStore
import io.github.sndnv.layers.api.directives.EntityDiscardingDirectives

class Codes(store: AuthorizationCodeStore) extends EntityDiscardingDirectives {
  import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

  import stasis.identity.api.Formats._

  private val log: Logger = LoggerFactory.getLogger(this.getClass.getName)

  def routes(user: ResourceOwner.Id): Route =
    concat(
      pathEndOrSingleSlash {
        get {
          onSuccess(store.all) { codes =>
            log.debug("User [{}] successfully retrieved [{}] authorization codes", user, codes.size)
            discardEntity & complete(codes)
          }
        }
      },
      path(Segment) { code =>
        concat(
          get {
            onSuccess(store.get(AuthorizationCode(code))) {
              case Some(storedCode) =>
                log.debugN(
                  "User [{}] successfully retrieved authorization code for client [{}] and owner [{}]",
                  user,
                  storedCode.client,
                  storedCode.owner
                )
                discardEntity & complete(storedCode)

              case None =>
                log.warnN(
                  "User [{}] requested authorization code [{}] but it was not found",
                  user,
                  code
                )
                discardEntity & complete(StatusCodes.NotFound)
            }
          },
          delete {
            onSuccess(store.delete(AuthorizationCode(code))) { deleted =>
              if (deleted) {
                log.debugN("User [{}] successfully deleted authorization code [{}]", user, code)
                discardEntity & complete(StatusCodes.OK)
              } else {
                log.warnN("User [{}] failed to delete authorization code [{}]", user, code)
                discardEntity & complete(StatusCodes.NotFound)
              }
            }
          }
        )
      }
    )
}

object Codes {
  def apply(store: AuthorizationCodeStore): Codes = new Codes(store = store)
}
