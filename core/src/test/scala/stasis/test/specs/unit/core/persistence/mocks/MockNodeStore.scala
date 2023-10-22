package stasis.test.specs.unit.core.persistence.mocks

import org.apache.pekko.Done
import org.apache.pekko.actor.typed.{ActorSystem, SpawnProtocol}
import org.apache.pekko.util.Timeout
import stasis.core.persistence.backends.memory.MemoryBackend
import stasis.core.persistence.nodes.NodeStore
import stasis.core.routing.Node
import stasis.core.telemetry.TelemetryContext

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class MockNodeStore(
  replacementNodes: Map[Node.Id, Option[Node]] = Map.empty
)(implicit system: ActorSystem[SpawnProtocol.Command], telemetry: TelemetryContext)
    extends NodeStore {
  private type StoreKey = Node.Id
  private type StoreValue = Node

  private implicit val timeout: Timeout = 3.seconds
  private implicit val ec: ExecutionContext = system.executionContext

  private val store = MemoryBackend[StoreKey, StoreValue](name = s"mock-node-store-${java.util.UUID.randomUUID()}")

  override def put(node: Node): Future[Done] = store.put(node.id, node)

  override def delete(node: Node.Id): Future[Boolean] = store.delete(node)

  override def get(node: Node.Id): Future[Option[Node]] =
    replacementNodes.get(node) match {
      case Some(replacement) => Future.successful(replacement)
      case None              => store.get(node)
    }

  override def contains(node: Node.Id): Future[Boolean] =
    if (replacementNodes.contains(node)) {
      Future.successful(true)
    } else {
      store.contains(node)
    }

  override def nodes: Future[Map[Node.Id, Node]] =
    storeData.map { result =>
      (result.view.mapValues(value => Some(value)) ++ replacementNodes).flatMap { case (k, optV) =>
        optV.map(v => k -> v)
      }.toMap
    }

  private def storeData: Future[Map[StoreKey, StoreValue]] = store.entries
}
