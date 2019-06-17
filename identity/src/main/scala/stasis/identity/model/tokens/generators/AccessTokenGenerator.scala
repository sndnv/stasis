package stasis.identity.model.tokens.generators

import stasis.identity.model.apis.Api
import stasis.identity.model.clients.Client
import stasis.identity.model.owners.ResourceOwner
import stasis.identity.model.tokens.AccessToken

trait AccessTokenGenerator {
  def generate(client: Client, audience: Seq[Client]): AccessToken
  def generate(owner: ResourceOwner, audience: Seq[Api]): AccessToken
}
