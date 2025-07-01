package stasis.identity.api.oauth.directives

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success

import org.apache.pekko.actor.typed.scaladsl.LoggerOps
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directive
import org.apache.pekko.http.scaladsl.server.Directive1
import org.apache.pekko.http.scaladsl.server.Directives._
import org.slf4j.Logger

import stasis.identity.api.Formats._
import stasis.identity.model.clients.Client
import stasis.identity.model.errors.TokenError
import stasis.identity.model.owners.ResourceOwner
import stasis.identity.model.tokens.RefreshToken
import stasis.identity.model.tokens.StoredRefreshToken
import stasis.identity.persistence.owners.ResourceOwnerStore
import stasis.identity.persistence.tokens.RefreshTokenStore
import io.github.sndnv.layers.api.directives.EntityDiscardingDirectives

trait RefreshTokenConsumption extends EntityDiscardingDirectives {
  import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

  protected implicit def ec: ExecutionContext
  protected def log: Logger

  protected def resourceOwnerStore: ResourceOwnerStore.View
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
          owner <- storedToken match {
            case Some(token) => resourceOwnerStore.get(token.owner)
            case None        => Future.successful(None)
          }
        } yield {
          for {
            token <- storedToken
            owner <- owner
          } yield {
            token -> owner
          }
        }
      ) {
        case Success(Some((StoredRefreshToken(`providedToken`, `client`, _, storedScope, _, _), owner))) =>
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

        case Success(Some((StoredRefreshToken(storedToken, storedClient, owner, _, _, _), _))) =>
          log.warnN(
            "Refresh token [{}] stored for client [{}] and owner [{}] did not have expected client [{}]",
            storedToken.value,
            client,
            owner,
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
