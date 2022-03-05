package stasis.identity.api.oauth.directives

import scala.util.{Failure, Success}

import akka.actor.typed.scaladsl.LoggerOps
import akka.http.scaladsl.model.{StatusCodes, Uri}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Directive, Directive1}
import org.slf4j.Logger
import stasis.core.api.directives.EntityDiscardingDirectives
import stasis.identity.model.ChallengeMethod
import stasis.identity.model.clients.Client
import stasis.identity.model.codes.generators.AuthorizationCodeGenerator
import stasis.identity.model.codes.{AuthorizationCode, AuthorizationCodeStore, StoredAuthorizationCode}
import stasis.identity.model.errors.AuthorizationError
import stasis.identity.model.owners.ResourceOwner

trait AuthorizationCodeGeneration extends EntityDiscardingDirectives {

  protected def log: Logger

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

      storeCode(
        client = client,
        redirectUri = redirectUri,
        state = state,
        owner = owner,
        storedCode = storedCode
      ) { code =>
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

      storeCode(
        client = client,
        redirectUri = redirectUri,
        state = state,
        owner = owner,
        storedCode = storedCode
      ) { code =>
        inner(Tuple1(code))
      }
    }

  private def storeCode(
    client: Client.Id,
    redirectUri: Uri,
    state: String,
    owner: ResourceOwner,
    storedCode: StoredAuthorizationCode
  ): Directive1[AuthorizationCode] =
    Directive { inner =>
      onComplete(authorizationCodeStore.put(storedCode)) {
        case Success(_) =>
          inner(Tuple1(storedCode.code))

        case Failure(e) =>
          log.errorN(
            "Failed to store authorization code for client [{}] and owner [{}]: [{}]",
            client,
            owner.username,
            e.getMessage,
            e
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
