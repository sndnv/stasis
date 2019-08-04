package stasis.test.specs.unit.identity.model.realms

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import stasis.core.persistence.backends.memory.MemoryBackend
import stasis.identity.model.realms.{Realm, RealmStore}
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.identity.model.Generators

class RealmStoreSpec extends AsyncUnitSpec {
  "A RealmStore" should "add, retrieve and delete realms" in {
    val store = createStore()

    val expectedRealm = Generators.generateRealm

    for {
      _ <- store.put(expectedRealm)
      actualRealm <- store.get(expectedRealm.id)
      someRealms <- store.realms
      containsRealmAfterPut <- store.contains(expectedRealm.id)
      _ <- store.delete(expectedRealm.id)
      missingRealm <- store.get(expectedRealm.id)
      containsRealmAfterDelete <- store.contains(expectedRealm.id)
      noRealms <- store.realms
    } yield {
      actualRealm should be(Some(expectedRealm))
      someRealms should be(Map(expectedRealm.id -> expectedRealm))
      containsRealmAfterPut should be(true)
      missingRealm should be(None)
      noRealms should be(Map.empty)
      containsRealmAfterDelete should be(false)
    }
  }

  it should "provide a read-only view" in {
    val store = createStore()
    val storeView = store.view

    val expectedRealm = Generators.generateRealm

    for {
      _ <- store.put(expectedRealm)
      actualRealm <- storeView.get(expectedRealm.id)
      someRealms <- storeView.realms
      containsRealmAfterPut <- storeView.contains(expectedRealm.id)
      _ <- store.delete(expectedRealm.id)
      missingRealm <- storeView.get(expectedRealm.id)
      containsRealmAfterDelete <- storeView.contains(expectedRealm.id)
      noRealms <- storeView.realms
    } yield {
      actualRealm should be(Some(expectedRealm))
      someRealms should be(Map(expectedRealm.id -> expectedRealm))
      containsRealmAfterPut should be(true)
      missingRealm should be(None)
      noRealms should be(Map.empty)
      containsRealmAfterDelete should be(false)
      a[ClassCastException] should be thrownBy { val _ = storeView.asInstanceOf[RealmStore] }
    }
  }

  private implicit val system: ActorSystem[SpawnProtocol] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol.behavior): Behavior[SpawnProtocol],
    "RealmStoreSpec"
  )

  private def createStore() = RealmStore(
    MemoryBackend[Realm.Id, Realm](name = s"realm-store-${java.util.UUID.randomUUID()}")
  )
}
