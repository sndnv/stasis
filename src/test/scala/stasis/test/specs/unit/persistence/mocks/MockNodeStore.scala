package stasis.test.specs.unit.persistence.mocks

import akka.Done
import akka.actor.Scheduler
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorSystem, SpawnProtocol}
import akka.util.Timeout
import stasis.persistence.NodeStore
import stasis.routing.Node
import stasis.test.specs.unit.networking.mocks.MockEndpointAddress

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class MockNodeStore(addressFailureNodes: Seq[Node] = Seq.empty)(implicit system: ActorSystem[SpawnProtocol])
    extends NodeStore[MockEndpointAddress] {
  private type StoreKey = Node
  private type StoreValue = MockEndpointAddress

  private implicit val timeout: Timeout = 3.seconds
  private implicit val scheduler: Scheduler = system.scheduler
  private implicit val ec: ExecutionContext = system.executionContext

  private val storeRef =
    system ? SpawnProtocol.Spawn(
      MapStoreActor.store(Map.empty[StoreKey, StoreValue]),
      s"mock-node-store-${java.util.UUID.randomUUID()}"
    )

  override def put(node: Node, address: MockEndpointAddress): Future[Done] =
    storeRef.flatMap(_ ? (ref => MapStoreActor.Put(node, address, ref)))

  override def list: Future[Seq[Node]] =
    storeData.map(_.keys.toSeq)

  override def addressOf(node: Node): Future[Option[MockEndpointAddress]] =
    if (!addressFailureNodes.contains(node)) {
      storeData.map(_.get(node))
    } else {
      Future.successful(None)
    }

  private def storeData: Future[Map[StoreKey, StoreValue]] =
    storeRef.flatMap(_ ? (ref => MapStoreActor.GetAll(ref)))
}
