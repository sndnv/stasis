package stasis.test.specs.unit.core.security.mocks

import stasis.core.networking.exceptions.CredentialsFailure
import stasis.core.networking.grpc.GrpcCredentials
import stasis.core.routing.Node
import stasis.core.security.NodeAuthenticator

import scala.concurrent.Future

class MockGrpcAuthenticator(expectedNode: Node.Id, expectedSecret: String) extends NodeAuthenticator[GrpcCredentials] {
  override def authenticate(credentials: GrpcCredentials): Future[Node.Id] =
    credentials match {
      case GrpcCredentials.Psk(node, `expectedSecret`) if node == expectedNode.toString =>
        Future.successful(expectedNode)

      case _ =>
        Future.failed(CredentialsFailure("Invalid credentials supplied"))
    }
}
