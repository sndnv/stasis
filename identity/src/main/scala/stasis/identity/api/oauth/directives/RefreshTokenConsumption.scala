package stasis.identity.api.oauth.directives

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

import akka.actor.typed.scaladsl.LoggerOps
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Directive, Directive1}
import org.slf4j.Logger
import stasis.core.api.directives.EntityDiscardingDirectives
import stasis.identity.api.Formats._
import stasis.identity.model.clients.Client
import stasis.identity.model.errors.TokenError
import stasis.identity.model.owners.ResourceOwner
import stasis.identity.model.tokens.{RefreshToken, RefreshTokenStore, StoredRefreshToken}

trait RefreshTokenConsumption extends EntityDiscardingDirectives {
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._

  protected implicit def ec: ExecutionContext
  protected def log: Logger

  protected def refreshTokenStore: RefreshTokenStore

  def consumeRefreshToken(
    client: Client.Id,
    providedScope: Option[String],
    providedToken: RefreshToken
  ): Directive1[ResourceOwner] =
    Directive { inner =>
      onComplete(
        for {
          storedToken <- refreshTokenStore.get(providedToken)
          _ <- refreshTokenStore.delete(providedToken)
        } yield {
          storedToken
        }
      ) {
        case Success(Some(StoredRefreshToken(`providedToken`, `client`, owner, storedScope, _))) =>
          if (providedScopeAllowed(storedScope, providedScope)) {
            inner(Tuple1(owner))
          } else {
            log.warnN(
              "Client [{}] provided less restrictive scope [{}] than originally requested for refresh token: [{}]",
              client,
              providedScope,
              storedScope
            )

            discardEntity & complete(StatusCodes.BadRequest, TokenError.InvalidScope: TokenError)
          }

        case Success(Some(StoredRefreshToken(storedToken, storedClient, owner, _, _))) =>
          log.warnN(
            "Refresh token [{}] stored for client [{}] and owner [{}] did not have expected client [{}]",
            storedToken.value,
            client,
            owner.username,
            storedClient
          )

          discardEntity & complete(StatusCodes.BadRequest, TokenError.InvalidGrant: TokenError)

        case Success(None) =>
          log.warnN(
            "No refresh token found for client [{}]",
            client
          )

          discardEntity & complete(StatusCodes.BadRequest, TokenError.InvalidGrant: TokenError)

        case Failure(e) =>
          log.errorN(
            "Failed to consume refresh token for client [{}]: [{} - {}]",
            client,
            e.getClass.getSimpleName,
            e.getMessage
          )

          discardEntity & complete(StatusCodes.InternalServerError)
      }
    }

  def providedScopeAllowed(stored: Option[String], provided: Option[String]): Boolean = {
    val storedScopes = extractScopes(stored)
    val providedScopes = extractScopes(provided)
    providedScopes.nonEmpty && storedScopes.containsSlice(providedScopes)
  }

  private def extractScopes(scope: Option[String]): Array[String] =
    scope.map(_.split(" ")).getOrElse(Array.empty).distinct.sorted
}
