package stasis.server.security.authenticators

import org.apache.pekko.http.scaladsl.model.headers.HttpCredentials
import stasis.server.security.CurrentUser

import scala.concurrent.Future

trait UserAuthenticator {
  def authenticate(credentials: HttpCredentials): Future[CurrentUser]
}
