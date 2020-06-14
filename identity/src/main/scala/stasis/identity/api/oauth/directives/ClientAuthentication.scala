package stasis.identity.api.oauth.directives

import akka.event.LoggingAdapter
import akka.http.scaladsl.model.headers.{BasicHttpCredentials, HttpChallenges}
import akka.http.scaladsl.model.{headers, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Directive, Directive1}
import stasis.core.api.directives.EntityDiscardingDirectives
import stasis.identity.api.Formats._
import stasis.identity.authentication.oauth.ClientAuthenticator
import stasis.identity.model.clients.Client
import stasis.identity.model.errors.TokenError

import scala.util.{Failure, Success}

trait ClientAuthentication extends EntityDiscardingDirectives {
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._

  protected def log: LoggingAdapter

  protected def clientAuthenticator: ClientAuthenticator

  protected def realm: String

  def authenticateClient(): Directive1[Client] =
    Directive { inner =>
      extractClientIP { remoteAddress =>
        extractCredentials {
          case Some(clientCredentials: BasicHttpCredentials) =>
            onComplete(clientAuthenticator.authenticate(clientCredentials)) {
              case Success(client) =>
                inner(Tuple1(client))

              case Failure(e) =>
                log.warning(
                  "Authentication failed for client [{}]: [{}]",
                  clientCredentials.username,
                  e.getMessage
                )

                discardEntity {
                  complete(
                    StatusCodes.Unauthorized,
                    List(headers.`WWW-Authenticate`(HttpChallenges.basic(realm))),
                    TokenError.InvalidClient: TokenError
                  )
                }
            }

          case Some(unsupportedCredentials) =>
            log.warning(
              "Client with address [{}] provided unsupported credentials: [{}]",
              remoteAddress,
              unsupportedCredentials.scheme()
            )

            discardEntity {
              complete(
                StatusCodes.Unauthorized,
                List(headers.`WWW-Authenticate`(HttpChallenges.basic(realm))),
                TokenError.InvalidClient: TokenError
              )
            }

          case None =>
            log.warning(
              "Client with address [{}] provided no credentials",
              remoteAddress
            )

            discardEntity {
              complete(
                StatusCodes.Unauthorized,
                List(headers.`WWW-Authenticate`(HttpChallenges.basic(realm))),
                TokenError.InvalidClient: TokenError
              )
            }
        }
      }
    }
}
