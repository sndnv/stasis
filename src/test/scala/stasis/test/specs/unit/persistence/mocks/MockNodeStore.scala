package stasis.test.specs.unit.persistence.mocks

import akka.Done
import akka.actor.Scheduler
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorSystem, SpawnProtocol}
import akka.util.Timeout
import stasis.persistence.NodeStore
import stasis.routing.Node
import stasis.routing.Node.Id

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class MockNodeStore(replacementNodes: Map[Node.Id, Option[Node]] = Map.empty)(
  implicit system: ActorSystem[SpawnProtocol])
    extends NodeStore {
  private type StoreKey = Node.Id
  private type StoreValue = Node

  private implicit val timeout: Timeout = 3.seconds
  private implicit val scheduler: Scheduler = system.scheduler
  private implicit val ec: ExecutionContext = system.executionContext

  private val storeRef =
    system ? SpawnProtocol.Spawn(
      MapStoreActor.store(Map.empty[StoreKey, StoreValue]),
      s"mock-node-store-${java.util.UUID.randomUUID()}"
    )

  override def put(node: Node): Future[Done] =
    storeRef.flatMap(_ ? (ref => MapStoreActor.Put(node.id, node, ref)))

  override def get(node: Id): Future[Option[Node]] =
    replacementNodes.get(node) match {
      case Some(replacement) =>
        Future.successful(replacement)

      case None =>
        storeRef.flatMap(_ ? (ref => MapStoreActor.Get(node, ref)))
    }

  override def nodes: Future[Seq[Node]] =
    storeData.map { result =>
      (result.mapValues(value => Some(value)) ++ replacementNodes).values.flatten.toSeq
    }

  private def storeData: Future[Map[StoreKey, StoreValue]] =
    storeRef.flatMap(_ ? (ref => MapStoreActor.GetAll(ref)))
}
