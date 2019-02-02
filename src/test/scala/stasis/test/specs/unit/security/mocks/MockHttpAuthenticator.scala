package stasis.test.specs.unit.security.mocks

import akka.http.scaladsl.model.headers.{BasicHttpCredentials, HttpCredentials}
import stasis.networking.exceptions.CredentialsFailure
import stasis.routing.Node
import stasis.security.NodeAuthenticator

import scala.concurrent.Future

class MockHttpAuthenticator(expectedUser: String, expectedPassword: String) extends NodeAuthenticator[HttpCredentials] {
  override def authenticate(credentials: HttpCredentials): Future[Node.Id] =
    credentials match {
      case BasicHttpCredentials(`expectedUser`, `expectedPassword`) =>
        Future.successful(Node.generateId())

      case _ =>
        Future.failed(CredentialsFailure("Invalid credentials supplied"))
    }
}
