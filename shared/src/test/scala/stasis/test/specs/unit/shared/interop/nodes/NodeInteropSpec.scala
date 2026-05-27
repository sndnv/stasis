package stasis.test.specs.unit.shared.interop.nodes

import java.util.UUID

import stasis.core.networking.grpc.GrpcEndpointAddress
import stasis.core.networking.http.HttpEndpointAddress
import stasis.core.persistence.crates.CrateStore
import stasis.shared.api.Formats._
import stasis.shared.api.requests.CreateNode
import stasis.shared.api.requests.UpdateNode
import stasis.shared.api.responses.CreatedNode
import stasis.shared.api.responses.DeletedNode
import stasis.test.specs.unit.shared.interop.InteropSpec

class NodeInteropSpec extends InteropSpec {
  "Node interop" should "decode and re-encode CreateNode.local" in {
    assert[CreateNode](
      domain = "nodes",
      resource = "CreateNode.local",
      matches = CreateNode.CreateLocalNode(
        storeDescriptor = CrateStore.Descriptor.ForFileBackend(parentDirectory = "/var/stasis/crates")
      )
    )
  }

  it should "decode and re-encode CreateNode.remote-http" in {
    assert[CreateNode](
      domain = "nodes",
      resource = "CreateNode.remote-http",
      matches = CreateNode.CreateRemoteHttpNode(
        address = HttpEndpointAddress("https://core.example.test"),
        storageAllowed = true
      )
    )
  }

  it should "decode and re-encode CreateNode.remote-grpc" in {
    assert[CreateNode](
      domain = "nodes",
      resource = "CreateNode.remote-grpc",
      matches = CreateNode.CreateRemoteGrpcNode(
        address = GrpcEndpointAddress(host = "core.example.test", port = 9999, tlsEnabled = true),
        storageAllowed = false
      )
    )
  }

  it should "decode and re-encode UpdateNode.local" in {
    assert[UpdateNode](
      domain = "nodes",
      resource = "UpdateNode.local",
      matches = UpdateNode.UpdateLocalNode(
        storeDescriptor = CrateStore.Descriptor.ForStreamingMemoryBackend(
          maxSize = 1073741824L,
          maxChunkSize = 1048576,
          name = "memory-backend"
        )
      )
    )
  }

  it should "decode and re-encode UpdateNode.remote-http" in {
    assert[UpdateNode](
      domain = "nodes",
      resource = "UpdateNode.remote-http",
      matches = UpdateNode.UpdateRemoteHttpNode(
        address = HttpEndpointAddress("https://core-updated.example.test"),
        storageAllowed = false
      )
    )
  }

  it should "decode and re-encode UpdateNode.remote-grpc" in {
    assert[UpdateNode](
      domain = "nodes",
      resource = "UpdateNode.remote-grpc",
      matches = UpdateNode.UpdateRemoteGrpcNode(
        address = GrpcEndpointAddress(host = "core-updated.example.test", port = 8888, tlsEnabled = false),
        storageAllowed = true
      )
    )
  }

  it should "decode and re-encode CreatedNode" in {
    assert(
      domain = "nodes",
      resource = "CreatedNode",
      matches = CreatedNode(node = UUID.fromString("fc60b46a-902a-4324-9fa5-d6c791f6e2c9"))
    )
  }

  it should "decode and re-encode DeletedNode" in {
    assert(
      domain = "nodes",
      resource = "DeletedNode",
      matches = DeletedNode(existing = true)
    )
  }
}
