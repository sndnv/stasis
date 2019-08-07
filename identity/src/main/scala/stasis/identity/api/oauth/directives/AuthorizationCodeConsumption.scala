package stasis.identity.api.oauth.directives

import java.nio.charset.{Charset, StandardCharsets}
import java.security.MessageDigest
import java.util.Base64

import akka.event.LoggingAdapter
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{Directive, Route}
import akka.http.scaladsl.server.Directives._
import stasis.identity.api.Formats._
import stasis.identity.api.directives.BaseApiDirective
import stasis.identity.model.ChallengeMethod
import stasis.identity.model.clients.Client
import stasis.identity.model.codes.{AuthorizationCode, AuthorizationCodeStore, StoredAuthorizationCode}
import stasis.identity.model.errors.TokenError
import stasis.identity.model.owners.ResourceOwner

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

trait AuthorizationCodeConsumption extends BaseApiDirective {
  import AuthorizationCodeConsumption._
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._

  protected implicit def ec: ExecutionContext
  protected def log: LoggingAdapter

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
          log.warning(
            "Authorization code for client [{}] and owner [{}] has challenge but none was expected",
            client,
            owner.username
          )

          discardEntity {
            complete(
              StatusCodes.BadRequest,
              TokenError.InvalidGrant
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
              log.warning(
                "Plain code verifier transformation method used for client [{}] and owner [{}]",
                client,
                owner.username
              )

              verifier == challenge.value
          }

          if (verifierMatchesChallenge) {
            inner(Tuple2(owner, scope))
          } else {
            log.warning(
              "Authorization code for client [{}] and owner [{}] did not have matching challenge and verifier",
              client,
              owner.username
            )

            discardEntity {
              complete(
                StatusCodes.BadRequest,
                TokenError.InvalidGrant
              )
            }
          }

        case Success(Some(StoredAuthorizationCode(`providedCode`, `client`, owner, _, None))) =>
          log.warning(
            "Authorization code for client [{}] and owner [{}] has no challenge but one was expected",
            client,
            owner.username
          )

          discardEntity {
            complete(
              StatusCodes.BadRequest,
              TokenError.InvalidGrant
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
            log.warning(
              "Authorization code [{}] stored for client [{}] and owner [{}] did not have expected client [{}]",
              storedCode.value,
              client,
              owner.username,
              storedClient
            )

            discardEntity {
              complete(
                StatusCodes.BadRequest,
                TokenError.InvalidGrant
              )
            }

          case Success(None) =>
            log.warning(
              "Authorization code [{}] was not found",
              providedCode.value
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
}

object AuthorizationCodeConsumption {
  object ChallengeVerification {
    final val DigestAlgorithm: String = "SHA-256"
    final val Charset: Charset = StandardCharsets.US_ASCII
  }
}
