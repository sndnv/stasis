package stasis.test.specs.unit.identity.model.apis

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import stasis.core.persistence.backends.memory.MemoryBackend
import stasis.identity.model.apis.{Api, ApiStore}
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.core.telemetry.MockTelemetryContext
import stasis.test.specs.unit.identity.model.Generators

class ApiStoreSpec extends AsyncUnitSpec {
  "An ApiStore" should "add, retrieve and delete APIs" in {
    val store = createStore()

    val expectedApi = Generators.generateApi

    for {
      _ <- store.put(expectedApi)
      actualApi <- store.get(expectedApi.id)
      someApis <- store.apis
      containsApiAfterPut <- store.contains(expectedApi.id)
      _ <- store.delete(expectedApi.id)
      missingApi <- store.get(expectedApi.id)
      containsApiAfterDelete <- store.contains(expectedApi.id)
      noApis <- store.apis
    } yield {
      actualApi should be(Some(expectedApi))
      someApis should be(Map(expectedApi.id -> expectedApi))
      containsApiAfterPut should be(true)
      missingApi should be(None)
      noApis should be(Map.empty)
      containsApiAfterDelete should be(false)
    }
  }

  it should "provide a read-only view" in {
    val store = createStore()
    val storeView = store.view

    val expectedApi = Generators.generateApi

    for {
      _ <- store.put(expectedApi)
      actualApi <- storeView.get(expectedApi.id)
      someApis <- storeView.apis
      containsApiAfterPut <- storeView.contains(expectedApi.id)
      _ <- store.delete(expectedApi.id)
      missingApi <- storeView.get(expectedApi.id)
      containsApiAfterDelete <- storeView.contains(expectedApi.id)
      noApis <- storeView.apis
    } yield {
      actualApi should be(Some(expectedApi))
      someApis should be(Map(expectedApi.id -> expectedApi))
      containsApiAfterPut should be(true)
      missingApi should be(None)
      noApis should be(Map.empty)
      containsApiAfterDelete should be(false)
      a[ClassCastException] should be thrownBy { val _ = storeView.asInstanceOf[ApiStore] }
    }
  }

  private implicit val system: ActorSystem[SpawnProtocol.Command] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol()): Behavior[SpawnProtocol.Command],
    "ApiStoreSpec"
  )

  private implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

  private def createStore() =
    ApiStore(
      MemoryBackend[Api.Id, Api](name = s"api-store-${java.util.UUID.randomUUID()}")
    )
}
