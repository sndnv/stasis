package stasis.test.specs.unit.shared.api.requests

import java.time.Instant

import stasis.core.networking.grpc.GrpcEndpointAddress
import stasis.core.networking.http.HttpEndpointAddress
import stasis.core.persistence.crates.CrateStore
import stasis.core.routing.Node
import stasis.shared.api.requests.CreateNode.CreateLocalNode
import stasis.shared.api.requests.CreateNode.CreateRemoteGrpcNode
import stasis.shared.api.requests.CreateNode.CreateRemoteHttpNode
import stasis.test.specs.unit.UnitSpec

class CreateNodeSpec extends UnitSpec {
  it should "convert requests to local nodes" in {
    val expectedNode = Node.Local(
      id = Node.generateId(),
      storeDescriptor = CrateStore.Descriptor.ForFileBackend(parentDirectory = "/tmp"),
      created = Instant.now(),
      updated = Instant.now()
    )

    val request = CreateLocalNode(
      storeDescriptor = expectedNode.storeDescriptor
    )

    val actualNode = request.toNode
    actualNode should be(expectedNode.copy(id = actualNode.id, created = actualNode.created, updated = actualNode.updated))
  }

  it should "convert requests to remote HTTP nodes" in {
    val expectedNode = Node.Remote.Http(
      id = Node.generateId(),
      address = HttpEndpointAddress(uri = "http://example.com"),
      storageAllowed = true,
      created = Instant.now(),
      updated = Instant.now()
    )

    val request = CreateRemoteHttpNode(
      address = expectedNode.address,
      storageAllowed = true
    )

    val actualNode = request.toNode

    actualNode should be(expectedNode.copy(id = actualNode.id, created = actualNode.created, updated = actualNode.updated))
  }

  it should "convert requests to remote gRPC nodes" in {
    val expectedNode = Node.Remote.Grpc(
      id = Node.generateId(),
      address = GrpcEndpointAddress(host = "example.com", port = 443, tlsEnabled = true),
      storageAllowed = false,
      created = Instant.now(),
      updated = Instant.now()
    )

    val request = CreateRemoteGrpcNode(
      address = expectedNode.address,
      storageAllowed = false
    )

    val actualNode = request.toNode

    actualNode should be(expectedNode.copy(id = actualNode.id, created = actualNode.created, updated = actualNode.updated))
  }
}
