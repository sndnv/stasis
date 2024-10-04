package stasis.test.specs.unit.identity.persistence.clients

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors

import stasis.identity.persistence.clients.ClientStore
import stasis.identity.persistence.clients.DefaultClientStore
import stasis.layers.UnitSpec
import stasis.layers.persistence.SlickTestDatabase
import stasis.layers.telemetry.MockTelemetryContext
import stasis.test.specs.unit.identity.model.Generators

class DefaultClientStoreSpec extends UnitSpec with SlickTestDatabase {
  "A DefaultClientStore" should "add, retrieve and delete clients" in withRetry {
    withStore { (profile, database) =>
      val store = new DefaultClientStore(name = "TEST_CLIENTS", profile = profile, database = database)

      val expectedClient = Generators.generateClient

      for {
        _ <- store.init()
        _ <- store.put(expectedClient)
        actualClient <- store.get(expectedClient.id)
        someClients <- store.all
        _ <- store.delete(expectedClient.id)
        missingClient <- store.get(expectedClient.id)
        noClients <- store.all
        _ <- store.drop()
      } yield {
        actualClient should be(Some(expectedClient))
        someClients should be(Seq(expectedClient))
        missingClient should be(None)
        noClients should be(Seq.empty)
      }
    }
  }

  it should "provide a read-only view" in withRetry {
    withStore { (profile, database) =>
      val store = new DefaultClientStore(name = "TEST_CLIENTS", profile = profile, database = database)
      val storeView = store.view

      val expectedClient = Generators.generateClient

      for {
        _ <- store.init()
        _ <- store.put(expectedClient)
        actualClient <- storeView.get(expectedClient.id)
        someClients <- storeView.all
        _ <- store.delete(expectedClient.id)
        missingClient <- storeView.get(expectedClient.id)
        noClients <- storeView.all
        _ <- store.drop()
      } yield {
        actualClient should be(Some(expectedClient))
        someClients should be(Seq(expectedClient))
        missingClient should be(None)
        noClients should be(Seq.empty)
        a[ClassCastException] should be thrownBy {
          val _ = storeView.asInstanceOf[ClientStore]
        }
      }
    }
  }

  private implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

  private implicit val system: ActorSystem[Nothing] = ActorSystem(
    guardianBehavior = Behaviors.ignore,
    name = "DefaultClientStoreSpec"
  )
}
