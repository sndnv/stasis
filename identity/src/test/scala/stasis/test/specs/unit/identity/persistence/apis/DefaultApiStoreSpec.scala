package stasis.test.specs.unit.identity.persistence.apis

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors

import stasis.identity.persistence.apis.ApiStore
import stasis.identity.persistence.apis.DefaultApiStore
import stasis.layers.UnitSpec
import stasis.layers.persistence.SlickTestDatabase
import stasis.layers.telemetry.MockTelemetryContext
import stasis.test.specs.unit.identity.model.Generators

class DefaultApiStoreSpec extends UnitSpec with SlickTestDatabase {
  "A DefaultApiStore" should "add, retrieve and delete APIs" in withRetry {
    withStore { (profile, database) =>
      val store = new DefaultApiStore(name = "TEST_APIS", profile = profile, database = database)
      val expectedApi = Generators.generateApi

      for {
        _ <- store.init()
        _ <- store.put(expectedApi)
        actualApi <- store.get(expectedApi.id)
        someApis <- store.all
        containsApiAfterPut <- store.contains(expectedApi.id)
        _ <- store.delete(expectedApi.id)
        missingApi <- store.get(expectedApi.id)
        containsApiAfterDelete <- store.contains(expectedApi.id)
        noApis <- store.all
        _ <- store.drop()
      } yield {
        actualApi should be(Some(expectedApi))
        someApis should be(Seq(expectedApi))
        containsApiAfterPut should be(true)
        missingApi should be(None)
        noApis should be(Seq.empty)
        containsApiAfterDelete should be(false)
      }
    }
  }

  it should "provide a read-only view" in withRetry {
    withStore { (profile, database) =>
      val store = new DefaultApiStore(name = "TEST_APIS", profile = profile, database = database)
      val storeView = store.view

      val expectedApi = Generators.generateApi

      for {
        _ <- store.init()
        _ <- store.put(expectedApi)
        actualApi <- storeView.get(expectedApi.id)
        someApis <- storeView.all
        containsApiAfterPut <- storeView.contains(expectedApi.id)
        _ <- store.delete(expectedApi.id)
        missingApi <- storeView.get(expectedApi.id)
        containsApiAfterDelete <- storeView.contains(expectedApi.id)
        noApis <- storeView.all
        _ <- store.drop()
      } yield {
        actualApi should be(Some(expectedApi))
        someApis should be(Seq(expectedApi))
        containsApiAfterPut should be(true)
        missingApi should be(None)
        noApis should be(Seq.empty)
        containsApiAfterDelete should be(false)
        a[ClassCastException] should be thrownBy {
          val _ = storeView.asInstanceOf[ApiStore]
        }
      }
    }
  }

  private implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

  private implicit val system: ActorSystem[Nothing] = ActorSystem(
    guardianBehavior = Behaviors.ignore,
    "ApiStoreSpec"
  )
}
