package stasis.identity.api.manage.directives

import org.apache.pekko.actor.typed.scaladsl.LoggerOps
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directive
import org.apache.pekko.http.scaladsl.server.Directive0
import org.apache.pekko.http.scaladsl.server.Directives.complete
import org.slf4j.Logger

import stasis.identity.model.owners.ResourceOwner
import io.github.sndnv.layers.api.directives.EntityDiscardingDirectives

trait UserAuthorization extends EntityDiscardingDirectives {

  protected def log: Logger

  def authorize(user: ResourceOwner, scope: String): Directive0 =
    Directive { inner =>
      if (user.allowedScopes.contains(scope)) {
        inner(())
      } else {
        log.warnN("User [{}] not allowed to access scope [{}]", user.username, scope)
        discardEntity & complete(StatusCodes.Forbidden)
      }
    }
}
