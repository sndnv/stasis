package stasis.identity.api.oauth.directives

import scala.util.{Failure, Success}

import akka.actor.typed.scaladsl.LoggerOps
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Directive, Directive1}
import org.slf4j.Logger
import stasis.core.api.directives.EntityDiscardingDirectives
import stasis.identity.model.clients.Client
import stasis.identity.model.owners.ResourceOwner
import stasis.identity.model.tokens.generators.RefreshTokenGenerator
import stasis.identity.model.tokens.{RefreshToken, RefreshTokenStore}

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
              "Failed to store refresh token for client [{}]: [{}]",
              client,
              e.getMessage,
              e
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
