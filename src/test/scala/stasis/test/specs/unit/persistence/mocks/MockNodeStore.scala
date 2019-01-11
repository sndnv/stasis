package stasis.test.specs.unit.persistence.mocks

import akka.Done
import akka.actor.typed.{ActorSystem, SpawnProtocol}
import akka.util.Timeout
import stasis.persistence.backends.memory.MemoryBackend
import stasis.persistence.nodes.NodeStore
import stasis.routing.Node

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class MockNodeStore(
  replacementNodes: Map[Node.Id, Option[Node]] = Map.empty
)(implicit system: ActorSystem[SpawnProtocol])
    extends NodeStore {
  private type StoreKey = Node.Id
  private type StoreValue = Node

  private implicit val timeout: Timeout = 3.seconds
  private implicit val ec: ExecutionContext = system.executionContext

  private val store =
    MemoryBackend.typed[StoreKey, StoreValue](name = s"mock-node-store-${java.util.UUID.randomUUID()}")

  override def put(node: Node): Future[Done] = store.put(node.id, node)

  override def delete(node: Node.Id): Future[Boolean] = store.delete(node)

  override def get(node: Node.Id): Future[Option[Node]] =
    replacementNodes.get(node) match {
      case Some(replacement) =>
        Future.successful(replacement)

      case None =>
        store.get(node)
    }

  override def nodes: Future[Map[Node.Id, Node]] =
    storeData.map { result =>
      (result.mapValues(value => Some(value)) ++ replacementNodes).flatMap {
        case (k, optV) =>
          optV.map(v => k -> v)
      }
    }

  private def storeData: Future[Map[StoreKey, StoreValue]] = store.entries
}
