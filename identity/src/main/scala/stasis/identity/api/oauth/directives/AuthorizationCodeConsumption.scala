package stasis.identity.api.oauth.directives

import akka.event.LoggingAdapter
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directive
import akka.http.scaladsl.server.Directives._
import stasis.identity.api.Formats._
import stasis.identity.api.directives.BaseApiDirective
import stasis.identity.model.clients.Client
import stasis.identity.model.codes.{AuthorizationCode, AuthorizationCodeStore, StoredAuthorizationCode}
import stasis.identity.model.errors.TokenError
import stasis.identity.model.owners.ResourceOwner

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

trait AuthorizationCodeConsumption extends BaseApiDirective {
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._

  protected implicit def ec: ExecutionContext
  protected def log: LoggingAdapter

  protected def authorizationCodeStore: AuthorizationCodeStore

  def consumeAuthorizationCode(
    client: Client.Id,
    providedCode: AuthorizationCode
  ): Directive[(ResourceOwner, Option[String])] =
    Directive { inner =>
      onComplete(
        for {
          storedCode <- authorizationCodeStore.get(client)
          _ <- authorizationCodeStore.delete(client)
        } yield {
          storedCode
        }
      ) {
        case Success(Some(StoredAuthorizationCode(`providedCode`, owner, scope))) =>
          inner(Tuple2(owner, scope))

        case Success(Some(StoredAuthorizationCode(storedCode, owner, _))) =>
          log.warning(
            "Authorization code [{}] stored for client [{}] and owner [{}] did not match provided code",
            storedCode,
            client,
            owner.username
          )

          discardEntity {
            complete(
              StatusCodes.BadRequest,
              TokenError.InvalidGrant
            )
          }

        case Success(None) =>
          log.warning(
            "No authorization code found for client [{}]",
            client
          )

          discardEntity {
            complete(
              StatusCodes.BadRequest,
              TokenError.InvalidGrant
            )
          }

        case Failure(e) =>
          log.error(
            e,
            "Failed to consume authorization code for client [{}]: [{}]",
            client,
            e.getMessage
          )

          discardEntity {
            complete(
              StatusCodes.InternalServerError
            )
          }
      }
    }
}
