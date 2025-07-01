package stasis.test.specs.unit.core.persistence.nodes

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._

import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.util.Timeout

import stasis.core.persistence.nodes.NodeStore
import stasis.core.routing.Node
import io.github.sndnv.layers.persistence.KeyValueStore
import io.github.sndnv.layers.persistence.memory.MemoryStore
import io.github.sndnv.layers.persistence.migration.Migration
import io.github.sndnv.layers.telemetry.mocks.MockTelemetryContext
import io.github.sndnv.layers.telemetry.TelemetryContext

class MockNodeStore(
  replacementNodes: Map[Node.Id, Option[Node]],
  underlying: KeyValueStore[Node.Id, Node]
)(implicit system: ActorSystem[Nothing])
    extends NodeStore {
  private implicit val ec: ExecutionContext = system.executionContext

  override val name: String = underlying.name()

  override val migrations: Seq[Migration] = Seq.empty

  override def init(): Future[Done] = underlying.init()

  override def drop(): Future[Done] = underlying.drop()

  override def put(node: Node): Future[Done] = underlying.put(node.id, node)

  override def delete(node: Node.Id): Future[Boolean] = underlying.delete(node)

  override def get(node: Node.Id): Future[Option[Node]] =
    replacementNodes.get(node) match {
      case Some(replacement) => Future.successful(replacement)
      case None              => underlying.get(node)
    }

  override def contains(node: Node.Id): Future[Boolean] =
    if (replacementNodes.contains(node)) {
      Future.successful(true)
    } else {
      underlying.contains(node)
    }

  override def nodes: Future[Map[Node.Id, Node]] =
    storeData.map { result =>
      (result.view.mapValues(value => Some(value)) ++ replacementNodes).flatMap { case (k, optV) =>
        optV.map(v => k -> v)
      }.toMap
    }

  private def storeData: Future[Map[Node.Id, Node]] = underlying.entries
}

object MockNodeStore {
  def apply(
    replacementNodes: Map[Node.Id, Option[Node]] = Map.empty
  )(implicit system: ActorSystem[Nothing]): MockNodeStore = {
    implicit val telemetry: TelemetryContext = MockTelemetryContext()

    implicit val timeout: Timeout = 3.seconds

    val underlying = MemoryStore[Node.Id, Node](name = s"mock-node-store-${java.util.UUID.randomUUID()}")

    new MockNodeStore(replacementNodes = replacementNodes, underlying = underlying)
  }
}
