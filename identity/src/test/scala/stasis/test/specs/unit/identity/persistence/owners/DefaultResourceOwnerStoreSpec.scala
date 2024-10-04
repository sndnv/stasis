package stasis.test.specs.unit.identity.persistence.owners

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors

import stasis.identity.persistence.owners.DefaultResourceOwnerStore
import stasis.identity.persistence.owners.ResourceOwnerStore
import stasis.layers.UnitSpec
import stasis.layers.persistence.SlickTestDatabase
import stasis.layers.telemetry.MockTelemetryContext
import stasis.test.specs.unit.identity.model.Generators

class DefaultResourceOwnerStoreSpec extends UnitSpec with SlickTestDatabase {
  "A DefaultResourceOwnerStore" should "add, retrieve and delete resource owners" in withRetry {
    withStore { (profile, database) =>
      val store = new DefaultResourceOwnerStore(name = "TEST_OWNERS", profile = profile, database = database)
      val expectedResourceOwner = Generators.generateResourceOwner

      for {
        _ <- store.init()
        _ <- store.put(expectedResourceOwner)
        actualResourceOwner <- store.get(expectedResourceOwner.username)
        someResourceOwners <- store.all
        containsOwnerAfterPut <- store.contains(expectedResourceOwner.username)
        _ <- store.delete(expectedResourceOwner.username)
        missingResourceOwner <- store.get(expectedResourceOwner.username)
        containsOwnerAfterDelete <- store.contains(expectedResourceOwner.username)
        noResourceOwners <- store.all
        _ <- store.drop()
      } yield {
        actualResourceOwner should be(Some(expectedResourceOwner))
        someResourceOwners should be(Seq(expectedResourceOwner))
        containsOwnerAfterPut should be(true)
        missingResourceOwner should be(None)
        noResourceOwners should be(Seq.empty)
        containsOwnerAfterDelete should be(false)
      }
    }
  }

  it should "provide a read-only view" in withRetry {
    withStore { (profile, database) =>
      val store = new DefaultResourceOwnerStore(name = "TEST_OWNERS", profile = profile, database = database)
      val storeView = store.view

      val expectedResourceOwner = Generators.generateResourceOwner

      for {
        _ <- store.init()
        _ <- store.put(expectedResourceOwner)
        actualResourceOwner <- storeView.get(expectedResourceOwner.username)
        someResourceOwners <- storeView.all
        containsOwnerAfterPut <- storeView.contains(expectedResourceOwner.username)
        _ <- store.delete(expectedResourceOwner.username)
        missingResourceOwner <- storeView.get(expectedResourceOwner.username)
        containsOwnerAfterDelete <- storeView.contains(expectedResourceOwner.username)
        noResourceOwners <- storeView.all
        _ <- store.drop()
      } yield {
        actualResourceOwner should be(Some(expectedResourceOwner))
        someResourceOwners should be(Seq(expectedResourceOwner))
        containsOwnerAfterPut should be(true)
        missingResourceOwner should be(None)
        noResourceOwners should be(Seq.empty)
        containsOwnerAfterDelete should be(false)
        a[ClassCastException] should be thrownBy {
          val _ = storeView.asInstanceOf[ResourceOwnerStore]
        }
      }
    }
  }

  private implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

  private implicit val system: ActorSystem[Nothing] = ActorSystem(
    guardianBehavior = Behaviors.ignore,
    "ResourceOwnerStoreSpec"
  )
}
