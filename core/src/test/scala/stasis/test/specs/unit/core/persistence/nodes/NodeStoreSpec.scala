package stasis.test.specs.unit.core.persistence.nodes

import java.time.Instant

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors

import stasis.core.networking.http.HttpEndpointAddress
import stasis.core.persistence.nodes.NodeStore
import stasis.core.routing.Node
import stasis.test.specs.unit.AsyncUnitSpec

class NodeStoreSpec extends AsyncUnitSpec {
  "A NodeStore" should "add, retrieve and delete nodes" in {
    val store = MockNodeStore()

    val expectedNode = Node.Remote.Http(
      id = Node.generateId(),
      address = HttpEndpointAddress("localhost"),
      storageAllowed = true,
      created = Instant.now(),
      updated = Instant.now()
    )

    for {
      _ <- store.put(expectedNode)
      actualNode <- store.get(expectedNode.id)
      actualNodeExists <- store.contains(expectedNode.id)
      someNodes <- store.nodes
      _ <- store.delete(expectedNode.id)
      missingNode <- store.get(expectedNode.id)
      missingNodeExists <- store.contains(expectedNode.id)
      noNodes <- store.nodes
    } yield {
      actualNode should be(Some(expectedNode))
      actualNodeExists should be(true)
      someNodes should be(Map(expectedNode.id -> expectedNode))
      missingNode should be(None)
      missingNodeExists should be(false)
      noNodes should be(Map.empty)
    }
  }

  it should "provide a read-only view" in {
    val store = MockNodeStore()
    val storeView = store.view

    val expectedNode = Node.Remote.Http(
      id = Node.generateId(),
      address = HttpEndpointAddress("localhost"),
      storageAllowed = true,
      created = Instant.now(),
      updated = Instant.now()
    )

    for {
      _ <- store.put(expectedNode)
      actualNode <- storeView.get(expectedNode.id)
      actualNodeExists <- storeView.contains(expectedNode.id)
      someNodes <- storeView.nodes
      _ <- store.delete(expectedNode.id)
      missingNode <- storeView.get(expectedNode.id)
      missingNodeExists <- storeView.contains(expectedNode.id)
      noNodes <- storeView.nodes
    } yield {
      actualNode should be(Some(expectedNode))
      actualNodeExists should be(true)
      someNodes should be(Map(expectedNode.id -> expectedNode))
      missingNode should be(None)
      missingNodeExists should be(false)
      noNodes should be(Map.empty)
      a[ClassCastException] should be thrownBy { val _ = storeView.asInstanceOf[NodeStore] }
    }
  }

  private implicit val system: ActorSystem[Nothing] = ActorSystem(
    guardianBehavior = Behaviors.ignore,
    name = "NodeStoreSpec"
  )
}
