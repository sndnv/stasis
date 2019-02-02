package stasis.test.specs.unit.core.networking.mocks

import stasis.core.networking.EndpointCredentials
import stasis.core.networking.grpc.{GrpcCredentials, GrpcEndpointAddress}
import stasis.core.routing.Node

class MockGrpcEndpointCredentials(
  private val credentials: Map[GrpcEndpointAddress, (Node.Id, String)]
) extends EndpointCredentials[GrpcEndpointAddress, GrpcCredentials] {
  def this(address: GrpcEndpointAddress, expectedNode: Node.Id, expectedSecret: String) =
    this(Map(address -> (expectedNode, expectedSecret)))

  override def provide(address: GrpcEndpointAddress): Option[GrpcCredentials] =
    credentials.get(address).map {
      case (expectedNode, expectedSecret) =>
        GrpcCredentials.Psk(node = expectedNode.toString, secret = expectedSecret)
    }
}
