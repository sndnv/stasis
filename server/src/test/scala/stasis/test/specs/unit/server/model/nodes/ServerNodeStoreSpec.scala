package stasis.test.specs.unit.server.model.nodes

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import stasis.core.persistence.crates.CrateStore
import stasis.core.routing.Node
import stasis.server.model.nodes.ServerNodeStore
import stasis.server.security.CurrentUser
import stasis.shared.model.devices.Device
import stasis.shared.model.users.User
import stasis.shared.security.Permission
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.core.persistence.Generators
import stasis.test.specs.unit.core.persistence.mocks.MockNodeStore

import scala.util.control.NonFatal

class ServerNodeStoreSpec extends AsyncUnitSpec {
  "A ServerNodeStore" should "provide a view resource (service)" in {
    val store = ServerNodeStore(new MockNodeStore())

    store.view().requiredPermission should be(Permission.View.Service)
  }

  it should "return existing nodes via view resource (service)" in {
    val underlying = new MockNodeStore()
    val store = ServerNodeStore(underlying)

    val node = Generators.generateLocalNode
    underlying.put(node).await

    store.view().get(node.id).map { result =>
      result should be(Some(node))
    }
  }

  it should "return a list of nodes via view resource (service)" in {
    val underlying = new MockNodeStore()
    val store = ServerNodeStore(underlying)

    val node = Generators.generateLocalNode
    underlying.put(node).await

    store.view().list().map { result =>
      result should be(Map(node.id -> node))
    }
  }

  it should "provide management resource (service)" in {
    val store = ServerNodeStore(new MockNodeStore())

    store.manage().requiredPermission should be(Permission.Manage.Service)
  }

  it should "allow creating nodes via management resource (service)" in {
    val underlying = new MockNodeStore()
    val store = ServerNodeStore(underlying)

    val node = Generators.generateLocalNode
    for {
      noNodes <- underlying.nodes
      _ <- store.manage().create(node)
      someNodes <- underlying.nodes
    } yield {
      noNodes should be(Map.empty)
      someNodes should be(Map(node.id -> node))
    }
  }

  it should "allow updating nodes via management resource (service)" in {
    val underlying = new MockNodeStore()
    val store = ServerNodeStore(underlying)

    val initialNode = Generators.generateLocalNode
    underlying.put(initialNode).await

    val updatedNode = initialNode.copy(storeDescriptor = CrateStore.Descriptor.ForFileBackend(parentDirectory = "/tmp"))

    for {
      initialNodes <- underlying.nodes
      _ <- store.manage().update(updatedNode)
      updatedNodes <- underlying.nodes
    } yield {
      initialNodes should be(Map(initialNode.id -> initialNode))
      updatedNodes should be(Map(initialNode.id -> updatedNode))
    }
  }

  it should "allow deleting nodes via management resource (service)" in {
    val underlying = new MockNodeStore()
    val store = ServerNodeStore(underlying)

    val node = Generators.generateLocalNode
    underlying.put(node).await

    for {
      someNodes <- underlying.nodes
      deleted <- store.manage().delete(node.id)
      noNodes <- underlying.nodes
    } yield {
      someNodes should be(Map(node.id -> node))
      deleted should be(true)
      noNodes should be(Map.empty)
    }
  }

  it should "provide management resource (self)" in {
    val store = ServerNodeStore(new MockNodeStore())

    store.manageSelf().requiredPermission should be(Permission.Manage.Self)
  }

  it should "allow creating nodes via management resource (self)" in {
    val underlying = new MockNodeStore()
    val store = ServerNodeStore(underlying)

    val deviceNode = Generators.generateLocalNode

    val ownDevice = Device(
      id = Device.generateId(),
      node = deviceNode.id,
      owner = self.id,
      active = true,
      limits = None
    )

    for {
      noNodes <- underlying.nodes
      _ <- store.manageSelf().create(self, ownDevice, deviceNode)
      someNodes <- underlying.nodes
    } yield {
      noNodes should be(Map.empty)
      someNodes should be(Map(deviceNode.id -> deviceNode))
    }
  }

  it should "fail to create nodes for another user's device via management resource (self)" in {
    val underlying = new MockNodeStore()
    val store = ServerNodeStore(underlying)

    val deviceNode = Generators.generateLocalNode

    val device = Device(
      id = Device.generateId(),
      node = deviceNode.id,
      owner = User.generateId(),
      active = true,
      limits = None
    )

    store
      .manageSelf()
      .create(self, device, deviceNode)
      .map { response =>
        fail(s"Received unexpected response from store: [$response]")
      }
      .recover { case NonFatal(e) =>
        e.getMessage should be(
          s"Expected to create node for own [${self.id.toString}] device but device for user [${device.owner.toString}] provided"
        )
      }
  }

  it should "fail to create nodes for mismatched devices via management resource (self)" in {
    val underlying = new MockNodeStore()
    val store = ServerNodeStore(underlying)

    val node = Generators.generateLocalNode

    val device = Device(
      id = Device.generateId(),
      node = Node.generateId(),
      owner = self.id,
      active = true,
      limits = None
    )

    store
      .manageSelf()
      .create(self, device, node)
      .map { response =>
        fail(s"Received unexpected response from store: [$response]")
      }
      .recover { case NonFatal(e) =>
        e.getMessage should be(
          s"Provided device [${device.id}] has a mismatched node [${device.node}]; expected [${node.id}]"
        )
      }
  }

  private implicit val system: ActorSystem[SpawnProtocol.Command] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol()): Behavior[SpawnProtocol.Command],
    "ServerNodeStoreSpec"
  )

  private val self = CurrentUser(User.generateId())
}
