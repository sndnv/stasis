package stasis.test.specs.unit.identity.model.clients

import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import stasis.core.persistence.backends.memory.MemoryBackend
import stasis.identity.model.clients.{Client, ClientStore}
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.core.telemetry.MockTelemetryContext
import stasis.test.specs.unit.identity.model.Generators

class ClientStoreSpec extends AsyncUnitSpec {
  "A ClientStore" should "add, retrieve and delete clients" in {
    val store = createStore()

    val expectedClient = Generators.generateClient

    for {
      _ <- store.put(expectedClient)
      actualClient <- store.get(expectedClient.id)
      someClients <- store.clients
      _ <- store.delete(expectedClient.id)
      missingClient <- store.get(expectedClient.id)
      noClients <- store.clients
    } yield {
      actualClient should be(Some(expectedClient))
      someClients should be(Map(expectedClient.id -> expectedClient))
      missingClient should be(None)
      noClients should be(Map.empty)
    }
  }

  it should "provide a read-only view" in {
    val store = createStore()
    val storeView = store.view

    val expectedClient = Generators.generateClient

    for {
      _ <- store.put(expectedClient)
      actualClient <- storeView.get(expectedClient.id)
      someClients <- storeView.clients
      _ <- store.delete(expectedClient.id)
      missingClient <- storeView.get(expectedClient.id)
      noClients <- storeView.clients
    } yield {
      actualClient should be(Some(expectedClient))
      someClients should be(Map(expectedClient.id -> expectedClient))
      missingClient should be(None)
      noClients should be(Map.empty)
      a[ClassCastException] should be thrownBy { val _ = storeView.asInstanceOf[ClientStore] }
    }
  }

  private implicit val system: ActorSystem[SpawnProtocol.Command] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol()): Behavior[SpawnProtocol.Command],
    "ClientStoreSpec"
  )

  private implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

  private def createStore() =
    ClientStore(
      MemoryBackend[Client.Id, Client](name = s"client-store-${java.util.UUID.randomUUID()}")
    )
}
