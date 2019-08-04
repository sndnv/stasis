package stasis.test.specs.unit.identity.model.apis

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import stasis.core.persistence.backends.memory.MemoryBackend
import stasis.identity.model.apis.{Api, ApiStore}
import stasis.identity.model.realms.Realm
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.identity.model.Generators

class ApiStoreSpec extends AsyncUnitSpec {
  "An ApiStore" should "add, retrieve and delete APIs" in {
    val store = createStore()

    val expectedApi = Generators.generateApi

    for {
      _ <- store.put(expectedApi)
      actualApi <- store.get(expectedApi.realm, expectedApi.id)
      someApis <- store.apis
      containsApiAfterPut <- store.contains(expectedApi.realm, expectedApi.id)
      _ <- store.delete(expectedApi.realm, expectedApi.id)
      missingApi <- store.get(expectedApi.realm, expectedApi.id)
      containsApiAfterDelete <- store.contains(expectedApi.realm, expectedApi.id)
      noApis <- store.apis
    } yield {
      actualApi should be(Some(expectedApi))
      someApis should be(Map(expectedApi.realm -> expectedApi.id -> expectedApi))
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
      actualApi <- storeView.get(expectedApi.realm, expectedApi.id)
      someApis <- storeView.apis
      containsApiAfterPut <- storeView.contains(expectedApi.realm, expectedApi.id)
      _ <- store.delete(expectedApi.realm, expectedApi.id)
      missingApi <- storeView.get(expectedApi.realm, expectedApi.id)
      containsApiAfterDelete <- storeView.contains(expectedApi.realm, expectedApi.id)
      noApis <- storeView.apis
    } yield {
      actualApi should be(Some(expectedApi))
      someApis should be(Map(expectedApi.realm -> expectedApi.id -> expectedApi))
      containsApiAfterPut should be(true)
      missingApi should be(None)
      noApis should be(Map.empty)
      containsApiAfterDelete should be(false)
      a[ClassCastException] should be thrownBy { val _ = storeView.asInstanceOf[ApiStore] }
    }
  }

  private implicit val system: ActorSystem[SpawnProtocol] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol.behavior): Behavior[SpawnProtocol],
    "ApiStoreSpec"
  )

  private def createStore() = ApiStore(
    MemoryBackend[(Realm.Id, Api.Id), Api](name = s"api-store-${java.util.UUID.randomUUID()}")
  )
}
