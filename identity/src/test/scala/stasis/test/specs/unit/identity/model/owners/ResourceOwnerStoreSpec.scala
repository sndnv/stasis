package stasis.test.specs.unit.identity.model.owners

import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import stasis.core.persistence.backends.memory.MemoryBackend
import stasis.identity.model.owners.{ResourceOwner, ResourceOwnerStore}
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.core.telemetry.MockTelemetryContext
import stasis.test.specs.unit.identity.model.Generators

class ResourceOwnerStoreSpec extends AsyncUnitSpec {
  "A ResourceOwnerStore" should "add, retrieve and delete resource owners" in {
    val store = createStore()

    val expectedResourceOwner = Generators.generateResourceOwner

    for {
      _ <- store.put(expectedResourceOwner)
      actualResourceOwner <- store.get(expectedResourceOwner.username)
      someResourceOwners <- store.owners
      containsOwnerAfterPut <- store.contains(expectedResourceOwner.username)
      _ <- store.delete(expectedResourceOwner.username)
      missingResourceOwner <- store.get(expectedResourceOwner.username)
      containsOwnerAfterDelete <- store.contains(expectedResourceOwner.username)
      noResourceOwners <- store.owners
    } yield {
      actualResourceOwner should be(Some(expectedResourceOwner))
      someResourceOwners should be(Map(expectedResourceOwner.username -> expectedResourceOwner))
      containsOwnerAfterPut should be(true)
      missingResourceOwner should be(None)
      noResourceOwners should be(Map.empty)
      containsOwnerAfterDelete should be(false)
    }
  }

  it should "provide a read-only view" in {
    val store = createStore()
    val storeView = store.view

    val expectedResourceOwner = Generators.generateResourceOwner

    for {
      _ <- store.put(expectedResourceOwner)
      actualResourceOwner <- storeView.get(expectedResourceOwner.username)
      someResourceOwners <- storeView.owners
      containsOwnerAfterPut <- storeView.contains(expectedResourceOwner.username)
      _ <- store.delete(expectedResourceOwner.username)
      missingResourceOwner <- storeView.get(expectedResourceOwner.username)
      containsOwnerAfterDelete <- storeView.contains(expectedResourceOwner.username)
      noResourceOwners <- storeView.owners
    } yield {
      actualResourceOwner should be(Some(expectedResourceOwner))
      someResourceOwners should be(Map(expectedResourceOwner.username -> expectedResourceOwner))
      containsOwnerAfterPut should be(true)
      missingResourceOwner should be(None)
      noResourceOwners should be(Map.empty)
      containsOwnerAfterDelete should be(false)
      a[ClassCastException] should be thrownBy { val _ = storeView.asInstanceOf[ResourceOwnerStore] }
    }
  }

  private implicit val system: ActorSystem[SpawnProtocol.Command] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol()): Behavior[SpawnProtocol.Command],
    "ResourceOwnerStoreSpec"
  )

  private implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

  private def createStore() =
    ResourceOwnerStore(
      MemoryBackend[ResourceOwner.Id, ResourceOwner](name = s"owner-store-${java.util.UUID.randomUUID()}")
    )
}
