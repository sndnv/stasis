package stasis.server.security.mocks

import scala.concurrent.Future

import org.apache.pekko.http.scaladsl.model.headers.BasicHttpCredentials
import org.apache.pekko.http.scaladsl.model.headers.HttpCredentials

import stasis.core.networking.exceptions.CredentialsFailure
import stasis.server.security.CurrentUser
import stasis.server.security.authenticators.UserAuthenticator
import stasis.shared.model.users.User

class MockUserAuthenticator(expectedUser: String, expectedPassword: String) extends UserAuthenticator {
  override def authenticate(credentials: HttpCredentials): Future[CurrentUser] =
    credentials match {
      case BasicHttpCredentials(`expectedUser`, `expectedPassword`) =>
        Future.successful(CurrentUser(User.generateId()))

      case _ =>
        Future.failed(CredentialsFailure("Invalid credentials supplied"))
    }
}
