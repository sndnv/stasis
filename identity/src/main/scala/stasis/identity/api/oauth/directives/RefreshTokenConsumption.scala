package stasis.identity.api.oauth.directives

import akka.event.LoggingAdapter
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Directive, Directive1}
import stasis.core.api.directives.EntityDiscardingDirectives
import stasis.identity.api.Formats._
import stasis.identity.model.clients.Client
import stasis.identity.model.errors.TokenError
import stasis.identity.model.owners.ResourceOwner
import stasis.identity.model.tokens.{RefreshToken, RefreshTokenStore, StoredRefreshToken}

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

trait RefreshTokenConsumption extends EntityDiscardingDirectives {
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._

  protected implicit def ec: ExecutionContext
  protected def log: LoggingAdapter

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
            log.warning(
              "Client [{}] provided less restrictive scope [{}] than originally requested for refresh token: [{}]",
              client,
              providedScope,
              storedScope
            )

            discardEntity & complete(StatusCodes.BadRequest, TokenError.InvalidScope)
          }

        case Success(Some(StoredRefreshToken(storedToken, storedClient, owner, _, _))) =>
          log.warning(
            "Refresh token [{}] stored for client [{}] and owner [{}] did not have expected client [{}]",
            storedToken,
            client,
            owner.username,
            storedClient
          )

          discardEntity & complete(StatusCodes.BadRequest, TokenError.InvalidGrant)

        case Success(None) =>
          log.warning(
            "No refresh token found for client [{}]",
            client
          )

          discardEntity & complete(StatusCodes.BadRequest, TokenError.InvalidGrant)

        case Failure(e) =>
          log.error(
            e,
            "Failed to consume refresh token for client [{}]: [{}]",
            client,
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
