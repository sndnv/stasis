package stasis.identity.api.oauth.directives

import akka.event.LoggingAdapter
import akka.http.scaladsl.model.{StatusCodes, Uri}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Directive, Directive1}
import stasis.identity.api.directives.BaseApiDirective
import stasis.identity.model.ChallengeMethod
import stasis.identity.model.clients.Client
import stasis.identity.model.codes.generators.AuthorizationCodeGenerator
import stasis.identity.model.codes.{AuthorizationCode, AuthorizationCodeStore, StoredAuthorizationCode}
import stasis.identity.model.errors.AuthorizationError
import stasis.identity.model.owners.ResourceOwner

import scala.util.{Failure, Success}

trait AuthorizationCodeGeneration extends BaseApiDirective {

  protected def log: LoggingAdapter

  protected def authorizationCodeGenerator: AuthorizationCodeGenerator
  protected def authorizationCodeStore: AuthorizationCodeStore

  def generateAuthorizationCode(
    client: Client.Id,
    redirectUri: Uri,
    state: String,
    owner: ResourceOwner,
    scope: Option[String]
  ): Directive1[AuthorizationCode] =
    Directive { inner =>
      val code = authorizationCodeGenerator.generate()
      val storedCode = StoredAuthorizationCode(code, client, owner, scope)

      storeCode(client, redirectUri, state, owner, scope, storedCode) { code =>
        inner(Tuple1(code))
      }
    }

  def generateAuthorizationCode(
    client: Client.Id,
    redirectUri: Uri,
    state: String,
    owner: ResourceOwner,
    scope: Option[String],
    challenge: String,
    challengeMethod: Option[ChallengeMethod]
  ): Directive1[AuthorizationCode] =
    Directive { inner =>
      val code = authorizationCodeGenerator.generate()
      val codeChallenge = StoredAuthorizationCode.Challenge(challenge, challengeMethod)
      val storedCode = StoredAuthorizationCode(code, client, owner, scope, Some(codeChallenge))

      storeCode(client, redirectUri, state, owner, scope, storedCode) { code =>
        inner(Tuple1(code))
      }
    }

  private def storeCode(
    client: Client.Id,
    redirectUri: Uri,
    state: String,
    owner: ResourceOwner,
    scope: Option[String],
    storedCode: StoredAuthorizationCode
  ): Directive1[AuthorizationCode] =
    Directive { inner =>
      onComplete(authorizationCodeStore.put(storedCode)) {
        case Success(_) =>
          inner(Tuple1(storedCode.code))

        case Failure(e) =>
          log.error(
            e,
            "Failed to store authorization code for client [{}] and owner [{}]: [{}]",
            client,
            owner.username,
            e.getMessage
          )

          discardEntity {
            redirect(
              redirectUri.withQuery(AuthorizationError.ServerError(state).asQuery),
              StatusCodes.Found
            )
          }
      }
    }
}
