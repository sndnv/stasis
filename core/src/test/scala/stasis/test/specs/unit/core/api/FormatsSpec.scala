package stasis.test.specs.unit.core.api

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import akka.http.scaladsl.model.Uri
import akka.util.Timeout
import play.api.libs.json.Json
import stasis.core.api.Formats._
import stasis.core.networking.grpc.GrpcEndpointAddress
import stasis.core.networking.http.HttpEndpointAddress
import stasis.core.persistence.crates.CrateStore
import stasis.core.routing.Node
import stasis.test.specs.unit.UnitSpec

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class FormatsSpec extends UnitSpec {
  "Formats" should "convert durations to/from JSON" in {
    val duration = 42.seconds
    val json = "42"

    finiteDurationFormat.writes(duration).toString should be(json)
    finiteDurationFormat.reads(Json.parse(json)).asOpt should be(Some(duration))
  }

  they should "convert URIs to/from JSON" in {
    val rawUri = "http://localhost:1234?a=b&c=d&e=1"
    val uri = Uri(rawUri)
    val json = s"""\"$rawUri\""""

    uriFormat.writes(uri).toString should be(json)
    uriFormat.reads(Json.parse(json)).asOpt should be(Some(uri))
  }

  they should "convert crate store descriptors to/from JSON" in {
    val descriptors = Map(
      "memory" -> (
        CrateStore.Descriptor.ForStreamingMemoryBackend(maxSize = 42, name = "backend-format-test"),
        """{"backend-type":"memory","max-size":42,"name":"backend-format-test"}"""
      ),
      "container" -> (
        CrateStore.Descriptor.ForContainerBackend(path = "/some/path", maxChunkSize = 1, maxChunks = 2),
        """{"backend-type":"container","path":"/some/path","max-chunk-size":1,"max-chunks":2}"""
      ),
      "file" -> (
        CrateStore.Descriptor.ForFileBackend(parentDirectory = "/some/path"),
        """{"backend-type":"file","parent-directory":"/some/path"}"""
      )
    )

    descriptors.foreach {
      case (_, (descriptor, json)) =>
        crateStoreDescriptorWrites.writes(descriptor).toString should be(json)
        crateStoreDescriptorReads.reads(Json.parse(json)).asOpt match {
          case Some(createdDescriptor) => createdDescriptor should be(descriptor)
          case None                    => fail("Expected result but none was returned")
        }
    }
  }

  they should "convert nodes to/from JSON" in {
    val nodeId = Node.generateId()
    val storeDescriptor = CrateStore.Descriptor.ForStreamingMemoryBackend(maxSize = 42, name = "node-format-test")
    val httpAddress = HttpEndpointAddress(uri = "http://some-address:1234")
    val grpcAddress = GrpcEndpointAddress(host = "some-host", port = 1234, tlsEnabled = false)

    val nodes = Map(
      "local" -> (
        Node.Local(id = nodeId, storeDescriptor = storeDescriptor),
        s"""{"node-type":"local","id":"$nodeId","storeDescriptor":${Json.toJson(storeDescriptor).toString}}"""
      ),
      "remote-http" -> (
        Node.Remote.Http(id = nodeId, address = httpAddress),
        s"""{"node-type":"remote-http","id":"$nodeId","address":{"uri":"${httpAddress.uri}"}}"""
      ),
      "remote-grpc" -> (
        Node.Remote.Grpc(id = nodeId, address = grpcAddress),
        s"""
           |{
           |"node-type":"remote-grpc",
           |"id":"$nodeId",
           |"address":{"host":"${grpcAddress.host}","port":${grpcAddress.port},"tlsEnabled":${grpcAddress.tlsEnabled}}
           |}""".stripMargin.replaceAll("\n", "").trim
      )
    )

    nodes.foreach {
      case (_, (node, json)) =>
        nodeWrites.writes(node).toString should be(json)
        nodeReads.reads(Json.parse(json)).asOpt match {
          case Some(createdNode) => createdNode should be(node)
          case None              => fail("Expected result but none was returned")
        }
    }
  }

  private implicit val typedSystem: ActorSystem[SpawnProtocol] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol.behavior): Behavior[SpawnProtocol],
    "FormatsSpec"
  )

  private implicit val ec: ExecutionContext = typedSystem.executionContext
  private implicit val timeout: Timeout = 3.seconds
}
