package stasis.identity.persistence.tokens

import scala.concurrent.Future

import org.apache.pekko.Done

import stasis.identity.model.clients.Client
import stasis.identity.model.owners.ResourceOwner
import stasis.identity.model.tokens.RefreshToken
import stasis.identity.model.tokens.StoredRefreshToken
import io.github.sndnv.layers.persistence.Store

trait RefreshTokenStore extends Store { store =>
  def put(client: Client.Id, token: RefreshToken, owner: ResourceOwner, scope: Option[String]): Future[Done]
  def delete(token: RefreshToken): Future[Boolean]
  def get(token: RefreshToken): Future[Option[StoredRefreshToken]]
  def all: Future[Seq[StoredRefreshToken]]
}
