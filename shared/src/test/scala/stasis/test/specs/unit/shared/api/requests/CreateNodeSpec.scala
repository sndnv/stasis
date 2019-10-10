package stasis.test.specs.unit.shared.api.requests

import stasis.core.networking.grpc.GrpcEndpointAddress
import stasis.core.networking.http.HttpEndpointAddress
import stasis.core.persistence.crates.CrateStore
import stasis.core.routing.Node
import stasis.shared.api.requests.CreateNode.{CreateLocalNode, CreateRemoteGrpcNode, CreateRemoteHttpNode}
import stasis.test.specs.unit.UnitSpec

class CreateNodeSpec extends UnitSpec {
  it should "convert requests to local nodes" in {
    val expectedNode = Node.Local(
      id = Node.generateId(),
      storeDescriptor = CrateStore.Descriptor.ForFileBackend(parentDirectory = "/tmp")
    )

    val request = CreateLocalNode(
      storeDescriptor = expectedNode.storeDescriptor
    )

    val actualNode = request.toNode
    actualNode should be(expectedNode.copy(id = actualNode.id))
  }

  it should "convert requests to remote HTTP nodes" in {
    val expectedNode = Node.Remote.Http(
      id = Node.generateId(),
      address = HttpEndpointAddress(uri = "http://example.com")
    )

    val request = CreateRemoteHttpNode(
      address = expectedNode.address
    )

    val actualNode = request.toNode

    actualNode should be(expectedNode.copy(id = actualNode.id))
  }

  it should "convert requests to remote gRPC nodes" in {
    val expectedNode = Node.Remote.Grpc(
      id = Node.generateId(),
      address = GrpcEndpointAddress(host = "example.com", port = 443, tlsEnabled = true)
    )

    val request = CreateRemoteGrpcNode(
      address = expectedNode.address
    )

    val actualNode = request.toNode

    actualNode should be(expectedNode.copy(id = actualNode.id))
  }
}
