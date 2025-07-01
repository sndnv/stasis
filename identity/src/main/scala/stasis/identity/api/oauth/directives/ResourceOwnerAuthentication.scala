package stasis.identity.api.oauth.directives

import scala.util.Failure
import scala.util.Success

import org.apache.pekko.actor.typed.scaladsl.LoggerOps
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.model.Uri
import org.apache.pekko.http.scaladsl.model.headers.BasicHttpCredentials
import org.apache.pekko.http.scaladsl.server.Directive
import org.apache.pekko.http.scaladsl.server.Directive1
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.StandardRoute
import org.slf4j.Logger

import stasis.identity.api.Formats._
import stasis.identity.authentication.oauth.ResourceOwnerAuthenticator
import stasis.identity.model.errors.AuthorizationError
import stasis.identity.model.owners.ResourceOwner
import io.github.sndnv.layers.api.directives.EntityDiscardingDirectives

trait ResourceOwnerAuthentication extends EntityDiscardingDirectives {
  import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

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
