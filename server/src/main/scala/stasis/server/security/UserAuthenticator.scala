package stasis.server.security

import akka.http.scaladsl.model.headers.HttpCredentials
import stasis.server.model.users.User

import scala.concurrent.Future

trait UserAuthenticator {
  def authenticate(credentials: HttpCredentials): Future[User.Id]
}
