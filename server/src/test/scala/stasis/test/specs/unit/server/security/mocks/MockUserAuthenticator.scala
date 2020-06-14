package stasis.test.specs.unit.server.security.mocks

import akka.http.scaladsl.model.headers.{BasicHttpCredentials, HttpCredentials}
import stasis.core.networking.exceptions.CredentialsFailure
import stasis.server.security.{CurrentUser, UserAuthenticator}
import stasis.shared.model.users.User

import scala.concurrent.Future

class MockUserAuthenticator(expectedUser: String, expectedPassword: String) extends UserAuthenticator {
  override def authenticate(credentials: HttpCredentials): Future[CurrentUser] =
    credentials match {
      case BasicHttpCredentials(`expectedUser`, `expectedPassword`) =>
        Future.successful(CurrentUser(User.generateId()))

      case _ =>
        Future.failed(CredentialsFailure("Invalid credentials supplied"))
    }
}
