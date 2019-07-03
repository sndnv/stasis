package stasis.identity.api.oauth.directives

import akka.event.LoggingAdapter
import akka.http.scaladsl.model.{StatusCodes, Uri}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Directive, Directive1}
import stasis.identity.api.directives.BaseApiDirective
import stasis.identity.model.clients.Client
import stasis.identity.model.codes.generators.AuthorizationCodeGenerator
import stasis.identity.model.codes.{AuthorizationCode, AuthorizationCodeStore}
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

      onComplete(authorizationCodeStore.put(client, code, owner, scope)) {
        case Success(_) =>
          inner(Tuple1(code))

        case Failure(e) =>
          log.error(
            e,
            "Failed to store authorization code for client [{}]: [{}]",
            client,
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
