package stasis.server.security.authenticators

import scala.concurrent.Future

import org.apache.pekko.http.scaladsl.model.headers.HttpCredentials

import stasis.server.security.CurrentUser

trait UserAuthenticator {
  def authenticate(credentials: HttpCredentials): Future[CurrentUser]
}
