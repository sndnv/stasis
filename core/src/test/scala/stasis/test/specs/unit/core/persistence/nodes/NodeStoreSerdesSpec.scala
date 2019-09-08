package stasis.test.specs.unit.core.persistence.nodes

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import akka.util.Timeout
import stasis.core.persistence.nodes.NodeStoreSerdes
import stasis.core.persistence.reservations.ReservationStore
import stasis.core.routing.Node
import stasis.test.specs.unit.UnitSpec
import stasis.test.specs.unit.core.persistence.Generators
import stasis.test.specs.unit.core.persistence.mocks.MockReservationStore

import scala.concurrent.duration._

class NodeStoreSerdesSpec extends UnitSpec {
  "NodeStoreSerdes" should "serialize and deserialize keys" in {
    val serdes = new NodeStoreSerdes(reservationStore)

    val node = Node.generateId()

    val serialized = serdes.serializeKey(node)
    val deserialized = serdes.deserializeKey(serialized)

    deserialized should be(node)
  }

  they should "serialize and deserialize values" in {
    val serdes = new NodeStoreSerdes(reservationStore)

    val localNode = Generators.generateLocalNode
    val serializedLocalNode = serdes.serializeValue(localNode)
    val deserializedLocalNode = serdes.deserializeValue(serializedLocalNode)

    deserializedLocalNode match {
      case Node.Local(id, crateStore) =>
        id should be(localNode.id)
        crateStore.getClass should be(localNode.crateStore.getClass)

      case other =>
        fail(s"Unexpected node encountered: [$other]")
    }

    val remoteHttpNode = Generators.generateRemoteHttpNode
    val serializedRemoteHttpNode = serdes.serializeValue(remoteHttpNode)
    val deserializedRemoteHttpNode = serdes.deserializeValue(serializedRemoteHttpNode)
    deserializedRemoteHttpNode should be(remoteHttpNode)
  }

  private implicit val system: ActorSystem[SpawnProtocol] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol.behavior): Behavior[SpawnProtocol],
    "NodeStoreSerdesSpec"
  )

  private implicit val reservationStore: ReservationStore = new MockReservationStore()

  private implicit val timeout: Timeout = 3.seconds
}
