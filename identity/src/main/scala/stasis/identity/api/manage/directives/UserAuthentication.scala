package stasis.identity.api.manage.directives

import akka.event.LoggingAdapter
import akka.http.scaladsl.model.headers.{HttpChallenges, OAuth2BearerToken}
import akka.http.scaladsl.model.{headers, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Directive, Directive1}
import stasis.core.api.directives.EntityDiscardingDirectives
import stasis.identity.authentication.manage.ResourceOwnerAuthenticator
import stasis.identity.model.owners.ResourceOwner

import scala.util.{Failure, Success}

trait UserAuthentication extends EntityDiscardingDirectives {

  protected def log: LoggingAdapter

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
                log.warning(
                  "Rejecting [{}] request for [{}] with invalid credentials from [{}]: [{}]",
                  Array(
                    method.value,
                    uri,
                    remoteAddress,
                    e
                  )
                )

                discardEntity & complete(StatusCodes.Unauthorized)
            }

          case Some(unsupportedCredentials) =>
            log.warning(
              "Rejecting [{}] request for [{}] with unsupported credentials [{}] from [{}]",
              Array(
                method.value,
                uri,
                unsupportedCredentials.scheme(),
                remoteAddress
              )
            )

            discardEntity & complete(StatusCodes.Unauthorized)

          case None =>
            log.warning(
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
