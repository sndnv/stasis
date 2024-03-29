package stasis.identity.api.oauth.directives

import org.apache.pekko.http.scaladsl.server.{Directive, Directive1}
import stasis.core.api.directives.EntityDiscardingDirectives
import stasis.identity.model.apis.Api
import stasis.identity.model.clients.Client
import stasis.identity.model.owners.ResourceOwner
import stasis.identity.model.tokens.AccessTokenWithExpiration
import stasis.identity.model.tokens.generators.AccessTokenGenerator

trait AccessTokenGeneration extends EntityDiscardingDirectives {

  protected def accessTokenGenerator: AccessTokenGenerator

  def generateAccessToken(client: Client, audience: Seq[Client]): Directive1[AccessTokenWithExpiration] =
    Directive { inner =>
      inner(Tuple1(accessTokenGenerator.generate(client, audience)))
    }

  def generateAccessToken(owner: ResourceOwner, audience: Seq[Api]): Directive1[AccessTokenWithExpiration] =
    Directive { inner =>
      inner(Tuple1(accessTokenGenerator.generate(owner, audience)))
    }
}
