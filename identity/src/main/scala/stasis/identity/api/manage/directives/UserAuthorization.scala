package stasis.identity.api.manage.directives

import akka.event.LoggingAdapter
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.{Directive, Directive0}
import stasis.identity.api.directives.BaseApiDirective
import stasis.identity.model.owners.ResourceOwner
import stasis.identity.model.realms.Realm

trait UserAuthorization extends BaseApiDirective {

  protected def log: LoggingAdapter

  def authorize(user: ResourceOwner, targetRealm: Realm.Id, scope: String): Directive0 =
    Directive { inner =>
      user.realm match {
        case Realm.Master =>
          log.info("Realm [{}]: Allowed access for user [{}]", targetRealm, user.username)
          inner(())

        case `targetRealm` =>
          if (user.allowedScopes.contains(scope)) {
            inner(())
          } else {
            log.warning(
              "Realm [{}]: User [{}] not allowed to access scope [{}]",
              targetRealm,
              user.username,
              scope
            )

            discardEntity & complete(StatusCodes.Forbidden)
          }

        case _ =>
          log.warning(
            "Realm [{}]: User [{}] not allowed to access realm",
            targetRealm,
            user.username
          )

          discardEntity & complete(StatusCodes.Forbidden)
      }
    }
}
