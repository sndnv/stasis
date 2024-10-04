package stasis.identity.api.oauth.directives

import scala.util.Failure
import scala.util.Success

import org.apache.pekko.actor.typed.scaladsl.LoggerOps
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directive
import org.apache.pekko.http.scaladsl.server.Directive1
import org.apache.pekko.http.scaladsl.server.Directives._
import org.slf4j.Logger

import stasis.identity.model.clients.Client
import stasis.identity.model.owners.ResourceOwner
import stasis.identity.model.tokens.RefreshToken
import stasis.identity.model.tokens.generators.RefreshTokenGenerator
import stasis.identity.persistence.tokens.RefreshTokenStore
import stasis.layers.api.directives.EntityDiscardingDirectives

trait RefreshTokenGeneration extends EntityDiscardingDirectives {

  protected def log: Logger

  protected def refreshTokenGenerator: RefreshTokenGenerator
  protected def refreshTokenStore: RefreshTokenStore

  protected def refreshTokensAllowed: Boolean

  def generateRefreshToken(
    client: Client.Id,
    owner: ResourceOwner,
    scope: Option[String]
  ): Directive1[Option[RefreshToken]] =
    Directive { inner =>
      if (refreshTokensAllowed) {
        val token = refreshTokenGenerator.generate()

        onComplete(refreshTokenStore.put(client, token, owner, scope)) {
          case Success(_) =>
            inner(Tuple1(Some(token)))

          case Failure(e) =>
            log.errorN(
              "Failed to store refresh token for client [{}]: [{} - {}]",
              client,
              e.getClass.getSimpleName,
              e.getMessage
            )

            discardEntity {
              complete(
                StatusCodes.InternalServerError
              )
            }
        }
      } else {
        inner(Tuple1(None))
      }
    }
}
