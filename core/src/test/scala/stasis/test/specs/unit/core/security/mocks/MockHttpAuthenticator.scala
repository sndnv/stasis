package stasis.test.specs.unit.core.security.mocks

import scala.concurrent.Future

import org.apache.pekko.http.scaladsl.model.headers.BasicHttpCredentials
import org.apache.pekko.http.scaladsl.model.headers.HttpCredentials

import stasis.core.networking.exceptions.CredentialsFailure
import stasis.core.routing.Node
import stasis.core.security.NodeAuthenticator

class MockHttpAuthenticator(expectedUser: String, expectedPassword: String) extends NodeAuthenticator[HttpCredentials] {
  override def authenticate(credentials: HttpCredentials): Future[Node.Id] =
    credentials match {
      case BasicHttpCredentials(`expectedUser`, `expectedPassword`) =>
        Future.successful(Node.generateId())

      case _ =>
        Future.failed(CredentialsFailure("Invalid credentials supplied"))
    }
}
