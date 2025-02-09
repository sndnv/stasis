package stasis.test.specs.unit.core.api

import java.time.Instant

import play.api.libs.json.Json

import stasis.core.api.Formats._
import stasis.core.networking.grpc.GrpcEndpointAddress
import stasis.core.networking.http.HttpEndpointAddress
import stasis.core.persistence.crates.CrateStore
import stasis.core.routing.Node
import stasis.test.specs.unit.UnitSpec

class FormatsSpec extends UnitSpec {
  "Formats" should "convert endpoint addresses to/from JSON" in {
    val httpAddress = HttpEndpointAddress(uri = "http://some-address:1234")
    val grpcAddress = GrpcEndpointAddress(host = "some-host", port = 1234, tlsEnabled = false)

    val addresses = Map(
      "http" -> (
        httpAddress,
        s"""{"address_type":"http","address":{"uri":"${httpAddress.uri}"}}"""
      ),
      "grpc" -> (
        grpcAddress,
        s"""{"address_type":"grpc","address":{"host":"${grpcAddress.host}","port":${grpcAddress.port},"tls_enabled":${grpcAddress.tlsEnabled}}}"""
      )
    )

    addresses.foreach { case (_, (address, json)) =>
      addressFormat.writes(address).toString should be(json)
      addressFormat.reads(Json.parse(json)).asOpt match {
        case Some(createdAddress) => createdAddress should be(address)
        case None                 => fail("Expected result but none was returned")
      }
    }
  }

  they should "convert crate store descriptors to/from JSON" in {
    val descriptors = Map(
      "memory" -> (
        CrateStore.Descriptor.ForStreamingMemoryBackend(maxSize = 4200, maxChunkSize = 999, name = "backend-format-test"),
        """{"backend_type":"memory","max_size":4200,"max_chunk_size":999,"name":"backend-format-test"}"""
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
      maxSize = 4200,
      maxChunkSize = 999,
      name = "node-format-test"
    )
    val httpAddress = HttpEndpointAddress(uri = "http://some-address:1234")
    val grpcAddress = GrpcEndpointAddress(host = "some-host", port = 1234, tlsEnabled = false)
    val now = Instant.now()

    val nodes: Map[String, (Node, String)] = Map(
      "local" -> (
        Node.Local(id = nodeId, storeDescriptor = storeDescriptor, created = now, updated = now),
        s"""
           |{
           |"node_type":"local",
           |"id":"$nodeId",
           |"store_descriptor":${Json.toJson(storeDescriptor).toString},
           |"created":"${now.toString}",
           |"updated":"${now.toString}"
           |}""".stripMargin.replaceAll("\n", "").trim
      ),
      "remote-http" -> (
        Node.Remote.Http(id = nodeId, address = httpAddress, storageAllowed = true, created = now, updated = now),
        s"""
           |{
           |"node_type":"remote-http",
           |"id":"$nodeId",
           |"address":{"uri":"${httpAddress.uri}"},
           |"storage_allowed":true,
           |"created":"${now.toString}",
           |"updated":"${now.toString}"
           |}""".stripMargin.replaceAll("\n", "").trim
      ),
      "remote-grpc" -> (
        Node.Remote.Grpc(id = nodeId, address = grpcAddress, storageAllowed = false, created = now, updated = now),
        s"""
           |{
           |"node_type":"remote-grpc",
           |"id":"$nodeId",
           |"address":{"host":"${grpcAddress.host}","port":${grpcAddress.port},"tls_enabled":${grpcAddress.tlsEnabled}},
           |"storage_allowed":false,
           |"created":"${now.toString}",
           |"updated":"${now.toString}"
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
