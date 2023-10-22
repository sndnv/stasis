package stasis.identity.api.oauth.directives

import scala.util.{Failure, Success}

import org.apache.pekko.actor.typed.scaladsl.LoggerOps
import org.apache.pekko.http.scaladsl.model.headers.{BasicHttpCredentials, HttpChallenges}
import org.apache.pekko.http.scaladsl.model.{headers, StatusCodes}
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.{Directive, Directive1}
import org.slf4j.Logger
import stasis.core.api.directives.EntityDiscardingDirectives
import stasis.identity.api.Formats._
import stasis.identity.authentication.oauth.ClientAuthenticator
import stasis.identity.model.clients.Client
import stasis.identity.model.errors.TokenError

trait ClientAuthentication extends EntityDiscardingDirectives {
  import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

  protected def log: Logger

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
                log.warnN(
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
            log.warnN(
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
            log.warnN(
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
