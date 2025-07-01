package stasis.test.specs.unit.core.persistence.nodes

import java.nio.charset.StandardCharsets

import scala.concurrent.Future

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors

import stasis.core.persistence.backends.slick.LegacyKeyValueStore
import stasis.core.persistence.nodes.DefaultNodeStore
import stasis.core.routing.Node
import io.github.sndnv.layers.testing.UnitSpec
import io.github.sndnv.layers.testing.persistence.TestSlickDatabase
import io.github.sndnv.layers.persistence.memory.MemoryStore
import stasis.test.specs.unit.core.persistence.Generators
import stasis.test.specs.unit.core.telemetry.MockTelemetryContext

class DefaultNodeStoreSpec extends UnitSpec with TestSlickDatabase {
  "A DefaultNodeStore" should "add, retrieve and delete nodes" in withRetry {
    withStore { (profile, database) =>
      implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

      val cache = MemoryStore[Node.Id, Node](name = "test-nodes-cache")
      val store = new DefaultNodeStore(name = "TEST_NODES", profile = profile, database = database, cache = cache)
      val expectedLocalNode = Generators.generateLocalNode
      val expectedRemoteHttpNode = Generators.generateRemoteHttpNode
      val expectedRemoteGrpcNode = Generators.generateRemoteGrpcNode

      for {
        _ <- store.init()
        _ <- store.put(expectedLocalNode)
        actualNode <- store.get(expectedLocalNode.id)
        someNodes <- store.nodes
        _ <- store.delete(expectedLocalNode.id)
        missingNode <- store.get(expectedLocalNode.id)
        noNodes <- store.nodes
        _ <- store.put(expectedRemoteHttpNode)
        _ <- store.put(expectedRemoteGrpcNode)
        remoteNodes <- store.nodes
        _ <- store.drop()
      } yield {
        actualNode should be(Some(expectedLocalNode))
        someNodes should be(Map(expectedLocalNode.id -> expectedLocalNode))
        missingNode should be(None)
        noNodes should be(Map.empty)
        remoteNodes.values.toSeq.sortBy(_.id) should be(Seq(expectedRemoteGrpcNode, expectedRemoteHttpNode).sortBy(_.id))
      }
    }
  }

  it should "provide migrations" in withRetry {
    withClue("from key-value store to version 1") {
      withStore { (profile, database) =>
        import play.api.libs.json._

        import stasis.core.api.Formats.crateStoreDescriptorWrites
        import stasis.core.api.Formats.grpcEndpointAddressFormat
        import stasis.core.api.Formats.httpEndpointAddressFormat

        implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

        val name = "TEST_NODES"
        val cache = MemoryStore[Node.Id, Node](name = "test-nodes-cache")
        val legacy = LegacyKeyValueStore(name = name, profile = profile, database = database)
        val current = new DefaultNodeStore(name = name, profile = profile, database = database, cache = cache)

        val nodes = Seq(
          Generators.generateLocalNode,
          Generators.generateRemoteGrpcNode,
          Generators.generateRemoteHttpNode
        )

        val jsonNodes = nodes.map { node =>
          val obj = node match {
            case node: Node.Local =>
              Json.obj(
                "node_type" -> Json.toJson("local"),
                "id" -> Json.toJson(node.id),
                "store_descriptor" -> Json.toJson(node.storeDescriptor)
              )

            case node: Node.Remote.Http =>
              Json.obj(
                "node_type" -> Json.toJson("remote-http"),
                "id" -> Json.toJson(node.id),
                "address" -> Json.toJson(node.address),
                "storage_allowed" -> Json.toJson(node.storageAllowed)
              )

            case node: Node.Remote.Grpc =>
              Json.obj(
                "node_type" -> Json.toJson("remote-grpc"),
                "id" -> Json.toJson(node.id),
                "address" -> Json.toJson(node.address),
                "storage_allowed" -> Json.toJson(node.storageAllowed)
              )
          }

          node.id -> obj.toString().getBytes(StandardCharsets.UTF_8)
        }

        for {
          _ <- legacy.create()
          _ <- Future.sequence(jsonNodes.map(e => legacy.insert(e._1.toString, e._2)))
          migration = current.migrations.find(_.version == 1) match {
            case Some(migration) => migration
            case None            => fail("Expected migration with version == 1 but none was found")
          }
          currentResultBefore <- current.delete(Node.generateId()).failed
          neededBefore <- migration.needed.run()
          _ <- migration.action.run()
          neededAfter <- migration.needed.run()
          currentResultAfter <- current.delete(Node.generateId())
          _ <- current.drop()
        } yield {
          currentResultBefore.getMessage should include("""Column "TEST_NODES.ID" not found""")
          neededBefore should be(true)

          neededAfter should be(false)
          currentResultAfter should be(false)
        }
      }
    }
  }

  it should "support caching" in withRetry {
    withStore { (profile, database) =>
      def createStore()(implicit telemetry: MockTelemetryContext): DefaultNodeStore = new DefaultNodeStore(
        name = "TEST_NODES",
        profile = profile,
        database = database,
        cache = MemoryStore[Node.Id, Node](name = "test-nodes-cache")
      )

      val store = createStore()(MockTelemetryContext())
      val expectedNode = Generators.generateLocalNode

      for {
        _ <- store.init()
        _ <- store.put(expectedNode)
        actualNode <- store.get(expectedNode.id)
        someNodes <- store.nodes
        recreatedTelemetry = MockTelemetryContext()
        recreatedStore = createStore()(recreatedTelemetry)
        nodesBeforeReplication <- recreatedStore.nodes // cache is empty
        _ <- recreatedStore.prepare() // replication from store to cache
        nodesAfterReplication <- recreatedStore.nodes // cache is populated
        _ <- store.drop()
      } yield {
        actualNode should be(Some(expectedNode))
        someNodes should be(Map(expectedNode.id -> expectedNode))

        nodesBeforeReplication should be(Map.empty)
        nodesAfterReplication should be(Map(expectedNode.id -> expectedNode))

        recreatedTelemetry.layers.persistence.keyValue.put should be(0)
        recreatedTelemetry.layers.persistence.keyValue.get should be(0)
        recreatedTelemetry.layers.persistence.keyValue.delete should be(0)
        recreatedTelemetry.layers.persistence.keyValue.contains should be(0)
        recreatedTelemetry.layers.persistence.keyValue.list should be(4) // 2x 2 calls (one call for the store, one for the cache)
      }
    }
  }

  private implicit val system: ActorSystem[Nothing] = ActorSystem(
    guardianBehavior = Behaviors.ignore,
    name = "DefaultNodeStoreSpec"
  )
}
