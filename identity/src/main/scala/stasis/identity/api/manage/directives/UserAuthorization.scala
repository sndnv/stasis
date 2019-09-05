package stasis.identity.api.manage.directives

import akka.event.LoggingAdapter
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.{Directive, Directive0}
import stasis.core.api.directives.EntityDiscardingDirectives
import stasis.identity.model.owners.ResourceOwner

trait UserAuthorization extends EntityDiscardingDirectives {

  protected def log: LoggingAdapter

  def authorize(user: ResourceOwner, scope: String): Directive0 =
    Directive { inner =>
      if (user.allowedScopes.contains(scope)) {
        inner(())
      } else {
        log.warning("User [{}] not allowed to access scope [{}]", user.username, scope)
        discardEntity & complete(StatusCodes.Forbidden)
      }
    }
}
