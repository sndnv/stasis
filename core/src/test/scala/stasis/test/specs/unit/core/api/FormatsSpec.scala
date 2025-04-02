package stasis.test.specs.unit.core.api

import java.time.Instant

import play.api.libs.json.Json

import stasis.core.api.Formats._
import stasis.core.discovery.ServiceApiEndpoint
import stasis.core.discovery.ServiceDiscoveryResult
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

  they should "convert service API endpoints to/from JSON" in {
    val endpoints: Seq[(ServiceApiEndpoint, String)] = Seq(
      ServiceApiEndpoint.Api(
        uri = "test-uri"
      ) -> """{"uri":"test-uri","endpoint_type":"api"}""",
      ServiceApiEndpoint.Core(
        address = HttpEndpointAddress(uri = "test-uri")
      ) -> """{"address":{"address_type":"http","address":{"uri":"test-uri"}},"endpoint_type":"core"}""",
      ServiceApiEndpoint.Discovery(
        uri = "test-uri"
      ) -> """{"uri":"test-uri","endpoint_type":"discovery"}"""
    )

    endpoints.foreach { case (endpoint, json) =>
      serviceApiEndpointFormat.writes(endpoint).toString should be(json)
      serviceApiEndpointFormat.reads(Json.parse(json)).asOpt match {
        case Some(createdEndpoint) => createdEndpoint should be(endpoint)
        case None                  => fail("Expected result but none was returned")
      }
    }
  }

  they should "convert service discovery results to/from JSON" in {
    val results: Seq[(ServiceDiscoveryResult, String)] = Seq(
      ServiceDiscoveryResult.KeepExisting -> """{"result":"keep-existing"}""",
      ServiceDiscoveryResult.SwitchTo(
        endpoints = ServiceDiscoveryResult.Endpoints(
          api = ServiceApiEndpoint.Api(uri = "test-uri"),
          core = ServiceApiEndpoint.Core(address = HttpEndpointAddress(uri = "test-uri")),
          discovery = ServiceApiEndpoint.Discovery(uri = "test-uri")
        ),
        recreateExisting = true
      ) ->
        """{
          |"endpoints":{
          |"api":{"uri":"test-uri"},
          |"core":{"address":{"address_type":"http","address":{"uri":"test-uri"}}},
          |"discovery":{"uri":"test-uri"}
          |},
          |"recreate_existing":true,
          |"result":"switch-to"
          |}""".stripMargin.replaceAll("\n", "").trim
    )

    results.foreach { case (result, json) =>
      serviceDiscoveryResultFormat.writes(result).toString should be(json)
      serviceDiscoveryResultFormat.reads(Json.parse(json)).asOpt match {
        case Some(createdResult) => createdResult should be(result)
        case None                => fail("Expected result but none was returned")
      }
    }
  }
}
