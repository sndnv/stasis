package stasis.test.specs.unit.security

import akka.http.scaladsl.model.headers.{BasicHttpCredentials, HttpCredentials}
import stasis.routing.Node
import stasis.security.NodeAuthenticator

class MockNodeAuthenticator(expectedUser: String, expectedPassword: String) extends NodeAuthenticator[HttpCredentials] {
  override def authenticate(credentials: HttpCredentials): Option[Node] =
    credentials match {
      case BasicHttpCredentials(`expectedUser`, `expectedPassword`) =>
        Some(Node(id = Node.generateId()))

      case _ =>
        None
    }
}
