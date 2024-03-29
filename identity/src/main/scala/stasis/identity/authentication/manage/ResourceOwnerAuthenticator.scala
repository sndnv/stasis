package stasis.identity.authentication.manage

import org.apache.pekko.http.scaladsl.model.headers.OAuth2BearerToken
import stasis.identity.model.owners.ResourceOwner

import scala.concurrent.Future

trait ResourceOwnerAuthenticator {
  def authenticate(credentials: OAuth2BearerToken): Future[ResourceOwner]
}
