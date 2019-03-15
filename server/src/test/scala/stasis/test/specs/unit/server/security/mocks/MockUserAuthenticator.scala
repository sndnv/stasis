package stasis.test.specs.unit.server.security.mocks

import scala.concurrent.Future

import akka.http.scaladsl.model.headers.{BasicHttpCredentials, HttpCredentials}
import stasis.core.networking.exceptions.CredentialsFailure
import stasis.server.model.users.User
import stasis.server.security.UserAuthenticator

class MockUserAuthenticator(expectedUser: String, expectedPassword: String) extends UserAuthenticator {
  override def authenticate(credentials: HttpCredentials): Future[User.Id] =
    credentials match {
      case BasicHttpCredentials(`expectedUser`, `expectedPassword`) =>
        Future.successful(User.generateId())

      case _ =>
        Future.failed(CredentialsFailure("Invalid credentials supplied"))
    }
}
