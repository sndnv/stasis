package stasis.identity.api.oauth.directives

import java.time.Instant

import scala.util.Failure
import scala.util.Success

import org.apache.pekko.actor.typed.scaladsl.LoggerOps
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.model.Uri
import org.apache.pekko.http.scaladsl.server.Directive
import org.apache.pekko.http.scaladsl.server.Directive1
import org.apache.pekko.http.scaladsl.server.Directives._
import org.slf4j.Logger

import stasis.identity.model.ChallengeMethod
import stasis.identity.model.clients.Client
import stasis.identity.model.codes.AuthorizationCode
import stasis.identity.model.codes.StoredAuthorizationCode
import stasis.identity.model.codes.generators.AuthorizationCodeGenerator
import stasis.identity.model.errors.AuthorizationError
import stasis.identity.model.owners.ResourceOwner
import stasis.identity.persistence.codes.AuthorizationCodeStore
import stasis.layers.api.directives.EntityDiscardingDirectives

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
      val storedCode = StoredAuthorizationCode(code, client, owner, scope, Some(codeChallenge), created = Instant.now())

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
            "Failed to store authorization code for client [{}] and owner [{}]: [{} - {}]",
            client,
            owner.username,
            e.getClass.getSimpleName,
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
