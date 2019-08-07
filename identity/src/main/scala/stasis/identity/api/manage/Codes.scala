package stasis.identity.api.manage

import akka.actor.ActorSystem
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.stream.Materializer
import stasis.identity.api.directives.BaseApiDirective
import stasis.identity.model.codes.{AuthorizationCode, AuthorizationCodeStore}
import stasis.identity.model.owners.ResourceOwner

class Codes(store: AuthorizationCodeStore)(implicit system: ActorSystem, override val mat: Materializer)
    extends BaseApiDirective {
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
  import stasis.identity.api.Formats._

  private val log: LoggingAdapter = Logging(system, this.getClass.getName)

  def routes(user: ResourceOwner.Id): Route =
    concat(
      pathEndOrSingleSlash {
        get {
          onSuccess(store.codes) { codes =>
            log.info("User [{}] successfully retrieved [{}] authorization codes", user, codes.size)
            discardEntity & complete(codes)
          }
        }
      },
      path(Segment) { code =>
        concat(
          get {
            onSuccess(store.get(AuthorizationCode(code))) {
              case Some(storedCode) =>
                log.info(
                  "User [{}] successfully retrieved authorization code for client [{}] and owner [{}]",
                  user,
                  storedCode.client,
                  storedCode.owner
                )
                discardEntity & complete(storedCode)

              case None =>
                log.warning(
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
                log.info("User [{}] successfully deleted authorization code [{}]", user, code)
                discardEntity & complete(StatusCodes.OK)
              } else {
                log.warning("User [{}] failed to delete authorization code [{}]", user, code)
                discardEntity & complete(StatusCodes.NotFound)
              }
            }
          }
        )
      }
    )
}
