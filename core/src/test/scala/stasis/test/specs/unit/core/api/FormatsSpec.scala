package stasis.test.specs.unit.core.api

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import akka.http.scaladsl.model.Uri
import akka.util.Timeout
import play.api.libs.json.Json
import stasis.core.api.Formats._
import stasis.core.networking.http.HttpEndpointAddress
import stasis.core.persistence.backends.file.{ContainerBackend, FileBackend}
import stasis.core.persistence.backends.memory.StreamingMemoryBackend
import stasis.core.persistence.crates.CrateStore
import stasis.core.persistence.reservations.ReservationStore
import stasis.core.routing.Node
import stasis.test.specs.unit.UnitSpec
import stasis.test.specs.unit.core.persistence.mocks.MockReservationStore

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

  they should "convert streaming backends to/from JSON" in {
    val backends = Map(
      "memory" -> (
        StreamingMemoryBackend(maxSize = 42, name = "backend-format-test"),
        """{"backend-type":"memory","max-size":42,"name":"backend-format-test"}"""
      ),
      "container" -> (
        new ContainerBackend(path = "/some/path", maxChunkSize = 1, maxChunks = 2),
        """{"backend-type":"container","path":"/some/path","max-chunk-size":1,"max-chunks":2}"""
      ),
      "file" -> (
        new FileBackend(parentDirectory = "/some/path"),
        """{"backend-type":"file","parent-directory":"/some/path"}"""
      )
    )

    backends.foreach {
      case (_, (backend, json)) =>
        streamingBackendWrites.writes(backend).toString should be(json)
        streamingBackendReads.reads(Json.parse(json)).asOpt match {
          case Some(createdBackend) => createdBackend.getClass should be(backend.getClass)
          case None                 => fail("Expected result but none was returned")
        }
    }
  }

  they should "convert nodes to/from JSON" in {
    val nodeId = Node.generateId()
    val nodeBackend = StreamingMemoryBackend(maxSize = 42, name = "node-format-test")
    val nodeAddress = HttpEndpointAddress("http://some-address:1234")

    implicit val reservationStore: ReservationStore = new MockReservationStore()
    implicit val reservationExpiration: FiniteDuration = 3.seconds

    val crateStore = CrateStore(
      streamingBackend = nodeBackend,
      reservationStore = new MockReservationStore(),
      storeId = nodeId
    )(typedSystem.toUntyped)

    val nodes = Map(
      "local" -> (
        Node.Local(id = nodeId, crateStore = crateStore),
        s"""{"node-type":"local","id":"$nodeId","backend":${Json.toJson(nodeBackend).toString}}"""
      ),
      "remote-http" -> (
        Node.Remote.Http(id = nodeId, address = nodeAddress),
        s"""{"node-type":"remote-http","id":"$nodeId","address":{"uri":"${nodeAddress.uri}"}}"""
      )
    )

    nodes.foreach {
      case (_, (node, json)) =>
        nodeWrites.writes(node).toString should be(json)
        nodeReads.reads(Json.parse(json)).asOpt match {
          case Some(createdNode) => createdNode.getClass should be(node.getClass)
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
