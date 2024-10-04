package stasis.identity.authentication.manage

import scala.concurrent.Future

import org.apache.pekko.http.scaladsl.model.headers.OAuth2BearerToken

import stasis.identity.model.owners.ResourceOwner

trait ResourceOwnerAuthenticator {
  def authenticate(credentials: OAuth2BearerToken): Future[ResourceOwner]
}
