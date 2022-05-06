package stasis.identity.api.oauth.directives

import java.nio.charset.{Charset, StandardCharsets}
import java.security.MessageDigest
import java.util.Base64

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

import akka.actor.typed.scaladsl.LoggerOps
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Directive, Route}
import org.slf4j.Logger
import stasis.core.api.directives.EntityDiscardingDirectives
import stasis.identity.api.Formats._
import stasis.identity.model.ChallengeMethod
import stasis.identity.model.clients.Client
import stasis.identity.model.codes.{AuthorizationCode, AuthorizationCodeStore, StoredAuthorizationCode}
import stasis.identity.model.errors.TokenError
import stasis.identity.model.owners.ResourceOwner

trait AuthorizationCodeConsumption extends EntityDiscardingDirectives {
  import AuthorizationCodeConsumption._
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._

  protected implicit def ec: ExecutionContext
  protected def log: Logger

  protected def authorizationCodeStore: AuthorizationCodeStore

  def consumeAuthorizationCode(
    client: Client.Id,
    providedCode: AuthorizationCode
  ): Directive[(ResourceOwner, Option[String])] =
    consumeCode(
      client,
      providedCode,
      handler = inner => {
        case Success(Some(StoredAuthorizationCode(`providedCode`, `client`, owner, scope, None))) =>
          inner(Tuple2(owner, scope))

        case Success(Some(StoredAuthorizationCode(`providedCode`, `client`, owner, _, Some(_)))) =>
          log.warnN(
            "Authorization code for client [{}] and owner [{}] has challenge but none was expected",
            client,
            owner.username
          )

          discardEntity {
            complete(
              StatusCodes.BadRequest,
              TokenError.InvalidGrant: TokenError
            )
          }
      }
    )

  def consumeAuthorizationCode(
    client: Client.Id,
    providedCode: AuthorizationCode,
    verifier: String
  ): Directive[(ResourceOwner, Option[String])] =
    consumeCode(
      client,
      providedCode,
      handler = inner => {
        case Success(Some(StoredAuthorizationCode(`providedCode`, `client`, owner, scope, Some(challenge)))) =>
          val verifierMatchesChallenge = challenge.method match {
            case Some(ChallengeMethod.S256) =>
              val hashedVerifier =
                MessageDigest
                  .getInstance(ChallengeVerification.DigestAlgorithm)
                  .digest(verifier.getBytes(ChallengeVerification.Charset))

              val encodedVerifier =
                Base64.getUrlEncoder
                  .withoutPadding()
                  .encodeToString(hashedVerifier)

              encodedVerifier == challenge.value

            case Some(ChallengeMethod.Plain) | None =>
              log.warnN(
                "Plain code verifier transformation method used for client [{}] and owner [{}]",
                client,
                owner.username
              )

              verifier == challenge.value
          }

          if (verifierMatchesChallenge) {
            inner(Tuple2(owner, scope))
          } else {
            log.warnN(
              "Authorization code for client [{}] and owner [{}] did not have matching challenge and verifier",
              client,
              owner.username
            )

            discardEntity {
              complete(
                StatusCodes.BadRequest,
                TokenError.InvalidGrant: TokenError
              )
            }
          }

        case Success(Some(StoredAuthorizationCode(`providedCode`, `client`, owner, _, None))) =>
          log.warnN(
            "Authorization code for client [{}] and owner [{}] has no challenge but one was expected",
            client,
            owner.username
          )

          discardEntity {
            complete(
              StatusCodes.BadRequest,
              TokenError.InvalidGrant: TokenError
            )
          }
      }
    )

  private def consumeCode(
    client: Client.Id,
    providedCode: AuthorizationCode,
    handler: (
      ((ResourceOwner, Option[String])) => Route
    ) => PartialFunction[Try[Option[StoredAuthorizationCode]], Route]
  ): Directive[(ResourceOwner, Option[String])] =
    Directive { inner =>
      onComplete(
        for {
          storedCode <- authorizationCodeStore.get(providedCode)
          _ <- authorizationCodeStore.delete(providedCode)
        } yield {
          storedCode
        }
      ) {
        handler(inner).orElse {
          case Success(Some(StoredAuthorizationCode(storedCode, storedClient, owner, _, _))) =>
            log.warnN(
              "Authorization code [{}] stored for client [{}] and owner [{}] did not have expected client [{}]",
              storedCode.value,
              client,
              owner.username,
              storedClient
            )

            discardEntity {
              complete(
                StatusCodes.BadRequest,
                TokenError.InvalidGrant: TokenError
              )
            }

          case Success(None) =>
            log.warnN(
              "Authorization code [{}] was not found",
              providedCode.value
            )

            discardEntity {
              complete(
                StatusCodes.BadRequest,
                TokenError.InvalidGrant: TokenError
              )
            }

          case Failure(e) =>
            log.errorN(
              "Failed to consume authorization code for client [{}]: [{} - {}]",
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

      }
    }
}

object AuthorizationCodeConsumption {
  object ChallengeVerification {
    final val DigestAlgorithm: String = "SHA-256"
    final val Charset: Charset = StandardCharsets.US_ASCII
  }
}
