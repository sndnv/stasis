package stasis.identity.api.manage.directives

import scala.util.{Failure, Success}

import org.apache.pekko.actor.typed.scaladsl.LoggerOps
import org.apache.pekko.http.scaladsl.model.headers.{HttpChallenges, OAuth2BearerToken}
import org.apache.pekko.http.scaladsl.model.{headers, StatusCodes}
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.{Directive, Directive1}
import org.slf4j.Logger
import stasis.core.api.directives.EntityDiscardingDirectives
import stasis.identity.authentication.manage.ResourceOwnerAuthenticator
import stasis.identity.model.owners.ResourceOwner

trait UserAuthentication extends EntityDiscardingDirectives {

  protected def log: Logger

  protected def authenticator: ResourceOwnerAuthenticator

  protected def realm: String

  def authenticate(): Directive1[ResourceOwner] =
    Directive { inner =>
      (extractMethod & extractUri & extractClientIP) { (method, uri, remoteAddress) =>
        extractCredentials {
          case Some(token: OAuth2BearerToken) =>
            onComplete(authenticator.authenticate(token)) {
              case Success(user) =>
                inner(Tuple1(user))

              case Failure(e) =>
                log.warnN(
                  "Rejecting [{}] request for [{}] with invalid credentials from [{}]: [{} - {}]",
                  method.value,
                  uri,
                  remoteAddress,
                  e.getClass.getSimpleName,
                  e.getMessage
                )

                discardEntity & complete(StatusCodes.Unauthorized)
            }

          case Some(unsupportedCredentials) =>
            log.warnN(
              "Rejecting [{}] request for [{}] with unsupported credentials [{}] from [{}]",
              method.value,
              uri,
              unsupportedCredentials.scheme(),
              remoteAddress
            )

            discardEntity & complete(StatusCodes.Unauthorized)

          case None =>
            log.warnN(
              "Rejecting [{}] request for [{}] with no credentials from [{}]",
              method.value,
              uri,
              remoteAddress
            )

            discardEntity {
              complete(
                status = StatusCodes.Unauthorized,
                headers = List(headers.`WWW-Authenticate`(HttpChallenges.oAuth2(realm))),
                v = "" // empty response
              )
            }
        }
      }
    }
}
