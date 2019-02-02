package stasis.test.specs.unit.security.mocks

import stasis.networking.exceptions.CredentialsFailure
import stasis.networking.grpc.GrpcCredentials
import stasis.routing.Node
import stasis.security.NodeAuthenticator

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
