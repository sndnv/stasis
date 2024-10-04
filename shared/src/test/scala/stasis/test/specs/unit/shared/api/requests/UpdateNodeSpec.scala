package stasis.test.specs.unit.shared.api.requests

import stasis.core.networking.grpc.GrpcEndpointAddress
import stasis.core.networking.http.HttpEndpointAddress
import stasis.core.persistence.crates.CrateStore
import stasis.core.routing.Node
import stasis.shared.api.requests.UpdateNode.UpdateLocalNode
import stasis.shared.api.requests.UpdateNode.UpdateRemoteGrpcNode
import stasis.shared.api.requests.UpdateNode.UpdateRemoteHttpNode
import stasis.test.specs.unit.UnitSpec

class UpdateNodeSpec extends UnitSpec {
  it should "convert requests to updated local nodes" in {
    val initialNode = Node.Local(
      id = Node.generateId(),
      storeDescriptor = CrateStore.Descriptor.ForFileBackend(parentDirectory = "/tmp")
    )

    val request = UpdateLocalNode(
      storeDescriptor = CrateStore.Descriptor.ForContainerBackend(path = "/tmp", maxChunkSize = 1, maxChunks = 1)
    )

    val updatedNode = request.toUpdatedNode(initialNode)

    updatedNode should be(initialNode.copy(storeDescriptor = request.storeDescriptor))
  }

  it should "convert requests to updated remote HTTP nodes" in {
    val initialNode = Node.Remote.Http(
      id = Node.generateId(),
      address = HttpEndpointAddress(uri = "http://example.com"),
      storageAllowed = true
    )

    val request = UpdateRemoteHttpNode(
      address = HttpEndpointAddress(uri = "http://test.example.com"),
      storageAllowed = true
    )

    val updatedNode = request.toUpdatedNode(initialNode)

    updatedNode should be(initialNode.copy(address = request.address))
  }

  it should "convert requests to updated remote gRPC nodes" in {
    val initialNode = Node.Remote.Grpc(
      id = Node.generateId(),
      address = GrpcEndpointAddress(host = "example.com", port = 443, tlsEnabled = true),
      storageAllowed = false
    )

    val request = UpdateRemoteGrpcNode(
      address = GrpcEndpointAddress(host = "test.example.com", port = 9999, tlsEnabled = false),
      storageAllowed = false
    )

    val updatedNode = request.toUpdatedNode(initialNode)

    updatedNode should be(initialNode.copy(address = request.address))
  }

  it should "fail to convert requests when node types do not match" in {
    val initialNode = Node.Remote.Http(
      id = Node.generateId(),
      address = HttpEndpointAddress(uri = "http://example.com"),
      storageAllowed = true
    )

    val request = UpdateLocalNode(
      storeDescriptor = CrateStore.Descriptor.ForContainerBackend(path = "/tmp", maxChunkSize = 1, maxChunks = 1)
    )

    an[IllegalArgumentException] should be thrownBy request.toUpdatedNode(initialNode)
  }
}
