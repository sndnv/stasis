package stasis.test.specs.unit.core.api

import play.api.libs.json.Json

import stasis.core.api.Formats._
import stasis.core.networking.grpc.GrpcEndpointAddress
import stasis.core.networking.http.HttpEndpointAddress
import stasis.core.persistence.crates.CrateStore
import stasis.core.routing.Node
import stasis.test.specs.unit.UnitSpec

class FormatsSpec extends UnitSpec {
  "Formats" should "convert crate store descriptors to/from JSON" in {
    val descriptors = Map(
      "memory" -> (
        CrateStore.Descriptor.ForStreamingMemoryBackend(maxSize = 42, maxChunkSize = 999, name = "backend-format-test"),
        """{"backend_type":"memory","max_size":42,"max_chunk_size":999,"name":"backend-format-test"}"""
      ),
      "container" -> (
        CrateStore.Descriptor.ForContainerBackend(path = "/some/path", maxChunkSize = 1, maxChunks = 2),
        """{"backend_type":"container","path":"/some/path","max_chunk_size":1,"max_chunks":2}"""
      ),
      "file" -> (
        CrateStore.Descriptor.ForFileBackend(parentDirectory = "/some/path"),
        """{"backend_type":"file","parent_directory":"/some/path"}"""
      )
    )

    descriptors.foreach { case (_, (descriptor, json)) =>
      crateStoreDescriptorWrites.writes(descriptor).toString should be(json)
      crateStoreDescriptorReads.reads(Json.parse(json)).asOpt match {
        case Some(createdDescriptor) => createdDescriptor should be(descriptor)
        case None                    => fail("Expected result but none was returned")
      }
    }
  }

  they should "convert nodes to/from JSON" in {
    val nodeId = Node.generateId()
    val storeDescriptor: CrateStore.Descriptor = CrateStore.Descriptor.ForStreamingMemoryBackend(
      maxSize = 42,
      maxChunkSize = 999,
      name = "node-format-test"
    )
    val httpAddress = HttpEndpointAddress(uri = "http://some-address:1234")
    val grpcAddress = GrpcEndpointAddress(host = "some-host", port = 1234, tlsEnabled = false)

    val nodes: Map[String, (Node, String)] = Map(
      "local" -> (
        Node.Local(id = nodeId, storeDescriptor = storeDescriptor),
        s"""{"node_type":"local","id":"$nodeId","store_descriptor":${Json.toJson(storeDescriptor).toString}}"""
      ),
      "remote-http" -> (
        Node.Remote.Http(id = nodeId, address = httpAddress, storageAllowed = true),
        s"""{"node_type":"remote-http","id":"$nodeId","address":{"uri":"${httpAddress.uri}"},"storage_allowed":true}"""
      ),
      "remote-grpc" -> (
        Node.Remote.Grpc(id = nodeId, address = grpcAddress, storageAllowed = false),
        s"""
           |{
           |"node_type":"remote-grpc",
           |"id":"$nodeId",
           |"address":{"host":"${grpcAddress.host}","port":${grpcAddress.port},"tls_enabled":${grpcAddress.tlsEnabled}},
           |"storage_allowed":false
           |}""".stripMargin.replaceAll("\n", "").trim
      )
    )

    nodes.foreach { case (_, (node, json)) =>
      nodeWrites.writes(node).toString should be(json)
      nodeReads.reads(Json.parse(json)).asOpt match {
        case Some(createdNode) => createdNode should be(node)
        case None              => fail("Expected result but none was returned")
      }
    }
  }
}
