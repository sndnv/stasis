package stasis.identity.api.oauth.directives

import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.model.{StatusCodes, Uri}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Directive, Directive1, StandardRoute}
import stasis.core.api.directives.EntityDiscardingDirectives
import stasis.identity.api.Formats._
import stasis.identity.authentication.oauth.ResourceOwnerAuthenticator
import stasis.identity.model.errors.AuthorizationError
import stasis.identity.model.owners.ResourceOwner
import scala.util.{Failure, Success}

import akka.actor.typed.scaladsl.LoggerOps
import org.slf4j.Logger

trait ResourceOwnerAuthentication extends EntityDiscardingDirectives {
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._

  protected def log: Logger

  protected def resourceOwnerAuthenticator: ResourceOwnerAuthenticator

  def authenticateResourceOwner(username: String, password: String): Directive1[ResourceOwner] =
    Directive { inner =>
      onComplete(resourceOwnerAuthenticator.authenticate(BasicHttpCredentials(username, password))) {
        case Success(owner) =>
          inner(Tuple1(owner))

        case Failure(e) =>
          log.warnN(
            "Authentication failed for resource owner [{}]: [{}]",
            username,
            e.getMessage
          )

          discardEntity & complete(StatusCodes.Unauthorized)
      }
    }

  def authenticateResourceOwner(redirectUri: Uri, state: String, noRedirect: Boolean): Directive1[ResourceOwner] =
    authenticateResourceOwner(
      unauthorizedResponse = if (noRedirect) {
        complete(
          StatusCodes.Unauthorized,
          AuthorizationError.AccessDenied(state): AuthorizationError
        )
      } else {
        redirect(
          redirectUri.withQuery(AuthorizationError.AccessDenied(state).asQuery),
          StatusCodes.Found
        )
      }
    )

  private def authenticateResourceOwner(unauthorizedResponse: => StandardRoute): Directive1[ResourceOwner] =
    Directive { inner =>
      extractClientIP { remoteAddress =>
        extractCredentials {
          case Some(resourceOwnerCredentials: BasicHttpCredentials) =>
            onComplete(resourceOwnerAuthenticator.authenticate(resourceOwnerCredentials)) {
              case Success(owner) =>
                inner(Tuple1(owner))

              case Failure(e) =>
                log.warnN(
                  "Authentication failed for resource owner [{}]: [{}]",
                  resourceOwnerCredentials.username,
                  e.getMessage
                )

                discardEntity & unauthorizedResponse
            }

          case Some(unsupportedCredentials) =>
            log.warnN(
              "Resource owner with address [{}] provided unsupported credentials: [{}]",
              remoteAddress,
              unsupportedCredentials.scheme()
            )
            discardEntity & unauthorizedResponse

          case None =>
            log.warnN(
              "Resource owner with address [{}] provided no credentials",
              remoteAddress
            )
            discardEntity & unauthorizedResponse
        }
      }
    }
}
