package stasis.server.persistence.analytics

import java.time.Instant

import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors

import stasis.layers.telemetry.ApplicationInformation
import stasis.layers.telemetry.analytics.AnalyticsEntry
import stasis.shared.model.analytics.StoredAnalyticsEntry
import stasis.shared.security.Permission
import stasis.test.specs.unit.AsyncUnitSpec

class AnalyticsEntryStoreSpec extends AsyncUnitSpec {
  "An AnalyticsEntryStore" should "provide a view resource (service)" in {
    val store = MockAnalyticsEntryStore()
    store.view().requiredPermission should be(Permission.View.Service)
  }

  it should "return existing entries via view resource (service)" in {
    val store = MockAnalyticsEntryStore()

    store.manageSelf().create(mockEntry).await

    store.view().get(mockEntry.id).map(result => result should be(Some(mockEntry)))
  }

  it should "return a list of entries via view resource (service)" in {
    val store = MockAnalyticsEntryStore()

    store.manageSelf().create(mockEntry).await
    store.manageSelf().create(mockEntry.copy(id = StoredAnalyticsEntry.generateId())).await
    store.manageSelf().create(mockEntry.copy(id = StoredAnalyticsEntry.generateId())).await

    store.view().list().map { result =>
      result.size should be(3)
      result.find(_.id == mockEntry.id) should be(Some(mockEntry))
    }
  }

  it should "provide management resource (service)" in {
    val store = MockAnalyticsEntryStore()
    store.manage().requiredPermission should be(Permission.Manage.Service)
  }

  it should "allow deleting entries via management resource (service)" in {
    val store = MockAnalyticsEntryStore()

    for {
      createResult <- store.manageSelf().create(mockEntry)
      getResult <- store.view().get(mockEntry.id)
      deleteResult <- store.manage().delete(mockEntry.id)
      deletedGetResult <- store.view().get(mockEntry.id)
    } yield {
      createResult should be(Done)
      getResult should be(Some(mockEntry))
      deleteResult should be(true)
      deletedGetResult should be(None)
    }
  }

  it should "provide management resource (self)" in {
    val store = MockAnalyticsEntryStore()
    store.manageSelf().requiredPermission should be(Permission.Manage.Self)
  }

  it should "allow creating entries via management resource (self)" in {
    val store = MockAnalyticsEntryStore()

    for {
      createResult <- store.manageSelf().create(mockEntry)
      getResult <- store.view().get(mockEntry.id)
    } yield {
      createResult should be(Done)
      getResult should be(Some(mockEntry))
    }
  }

  private val mockEntry: StoredAnalyticsEntry = StoredAnalyticsEntry(
    id = StoredAnalyticsEntry.generateId(),
    runtime = AnalyticsEntry.RuntimeInformation(app = ApplicationInformation.none),
    events = Seq.empty,
    failures = Seq.empty,
    created = Instant.now(),
    updated = Instant.now(),
    received = Instant.now()
  )

  private implicit val typedSystem: ActorSystem[Nothing] = ActorSystem(
    guardianBehavior = Behaviors.ignore,
    name = "AnalyticsEntryStoreSpec"
  )
}
