package stasis.identity.api.oauth.directives

import akka.event.LoggingAdapter
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Directive, Directive1}
import stasis.identity.api.directives.BaseApiDirective
import stasis.identity.model.clients.Client
import stasis.identity.model.owners.ResourceOwner
import stasis.identity.model.realms.Realm
import stasis.identity.model.tokens.generators.RefreshTokenGenerator
import stasis.identity.model.tokens.{RefreshToken, RefreshTokenStore}

import scala.util.{Failure, Success}

trait RefreshTokenGeneration extends BaseApiDirective {

  protected def log: LoggingAdapter

  protected def refreshTokenGenerator: RefreshTokenGenerator
  protected def refreshTokenStore: RefreshTokenStore

  def generateRefreshToken(
    realm: Realm,
    client: Client.Id,
    owner: ResourceOwner,
    scope: Option[String]
  ): Directive1[Option[RefreshToken]] =
    Directive { inner =>
      if (realm.refreshTokensAllowed) {
        val token = refreshTokenGenerator.generate()

        onComplete(refreshTokenStore.put(client, token, owner, scope)) {
          case Success(_) =>
            inner(Tuple1(Some(token)))

          case Failure(e) =>
            log.error(
              e,
              "Failed to store refresh token for client [{}]: [{}]",
              client,
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
