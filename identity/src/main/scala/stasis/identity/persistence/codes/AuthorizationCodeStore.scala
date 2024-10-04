package stasis.identity.persistence.codes

import scala.concurrent.Future

import org.apache.pekko.Done

import stasis.identity.model.codes.AuthorizationCode
import stasis.identity.model.codes.StoredAuthorizationCode
import stasis.layers.persistence.Store

trait AuthorizationCodeStore extends Store {
  def put(storedCode: StoredAuthorizationCode): Future[Done]
  def delete(code: AuthorizationCode): Future[Boolean]
  def get(code: AuthorizationCode): Future[Option[StoredAuthorizationCode]]
  def all: Future[Seq[StoredAuthorizationCode]]
}
