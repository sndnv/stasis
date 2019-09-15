package stasis.test.specs.unit.core.networking.mocks

import stasis.core.networking.grpc.{GrpcCredentials, GrpcEndpointAddress}
import stasis.core.routing.Node
import stasis.core.security.NodeCredentialsProvider

import scala.concurrent.Future

class MockGrpcNodeCredentialsProvider(
  private val credentials: Map[GrpcEndpointAddress, (Node.Id, String)]
) extends NodeCredentialsProvider[GrpcEndpointAddress, GrpcCredentials] {
  def this(address: GrpcEndpointAddress, expectedNode: Node.Id, expectedSecret: String) =
    this(Map(address -> (expectedNode, expectedSecret)))

  override def provide(address: GrpcEndpointAddress): Future[GrpcCredentials] =
    credentials.get(address) match {
      case Some((expectedNode, expectedSecret)) =>
        Future.successful(GrpcCredentials.Psk(node = expectedNode.toString, secret = expectedSecret))

      case None =>
        Future.failed(new RuntimeException(s"No credentials found for [$address]"))
    }
}
