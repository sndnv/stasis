package stasis.test.specs.unit.core.persistence.nodes

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import akka.util.Timeout
import stasis.core.persistence.nodes.NodeStoreSerdes
import stasis.core.routing.Node
import stasis.test.specs.unit.UnitSpec
import stasis.test.specs.unit.core.persistence.Generators

import scala.concurrent.duration._

class NodeStoreSerdesSpec extends UnitSpec {
  "NodeStoreSerdes" should "serialize and deserialize keys" in {
    val node = Node.generateId()

    val serialized = NodeStoreSerdes.serializeKey(node)
    val deserialized = NodeStoreSerdes.deserializeKey(serialized)

    deserialized should be(node)
  }

  they should "serialize and deserialize values" in {
    val localNode = Generators.generateLocalNode
    val serializedLocalNode = NodeStoreSerdes.serializeValue(localNode)
    val deserializedLocalNode = NodeStoreSerdes.deserializeValue(serializedLocalNode)

    deserializedLocalNode match {
      case Node.Local(id, storeDescriptor) =>
        id should be(localNode.id)
        storeDescriptor should be(localNode.storeDescriptor)

      case other =>
        fail(s"Unexpected node encountered: [$other]")
    }

    val remoteHttpNode = Generators.generateRemoteHttpNode
    val serializedRemoteHttpNode = NodeStoreSerdes.serializeValue(remoteHttpNode)
    val deserializedRemoteHttpNode = NodeStoreSerdes.deserializeValue(serializedRemoteHttpNode)
    deserializedRemoteHttpNode should be(remoteHttpNode)
  }

  private implicit val system: ActorSystem[SpawnProtocol.Command] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol()): Behavior[SpawnProtocol.Command],
    "NodeStoreSerdesSpec"
  )

  private implicit val timeout: Timeout = 3.seconds
}
