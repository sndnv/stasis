package stasis.identity.api.manage

import akka.actor.typed.scaladsl.LoggerOps
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.stream.Materializer
import org.slf4j.{Logger, LoggerFactory}
import stasis.core.api.directives.EntityDiscardingDirectives
import stasis.identity.model.codes.{AuthorizationCode, AuthorizationCodeStore}
import stasis.identity.model.owners.ResourceOwner

class Codes(store: AuthorizationCodeStore)(implicit override val mat: Materializer) extends EntityDiscardingDirectives {
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
  import stasis.identity.api.Formats._

  private val log: Logger = LoggerFactory.getLogger(this.getClass.getName)

  def routes(user: ResourceOwner.Id): Route =
    concat(
      pathEndOrSingleSlash {
        get {
          onSuccess(store.codes) { codes =>
            log.debug("User [{}] successfully retrieved [{}] authorization codes", user, codes.size)
            discardEntity & complete(codes.values)
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
  def apply(store: AuthorizationCodeStore)(implicit mat: Materializer): Codes =
    new Codes(
      store = store
    )
}
