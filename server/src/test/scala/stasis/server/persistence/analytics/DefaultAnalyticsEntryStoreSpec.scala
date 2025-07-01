package stasis.server.persistence.analytics

import java.time.Instant

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors

import io.github.sndnv.layers.testing.UnitSpec
import io.github.sndnv.layers.testing.persistence.TestSlickDatabase
import io.github.sndnv.layers.telemetry.ApplicationInformation
import io.github.sndnv.layers.telemetry.mocks.MockTelemetryContext
import io.github.sndnv.layers.telemetry.analytics.AnalyticsEntry
import stasis.shared.model.analytics.StoredAnalyticsEntry

class DefaultAnalyticsEntryStoreSpec extends UnitSpec with TestSlickDatabase {
  "A DefaultAnalyticsEntryStore" should "add, retrieve and delete analytics entries" in withRetry {
    withStore { (profile, database) =>
      val store = new DefaultAnalyticsEntryStore(name = "TEST_ANALYTICS_ENTRIES", profile = profile, database = database)
      val expectedEntry = StoredAnalyticsEntry(
        id = StoredAnalyticsEntry.generateId(),
        runtime = AnalyticsEntry.RuntimeInformation(app = ApplicationInformation.none),
        events = Seq.empty,
        failures = Seq.empty,
        created = Instant.now(),
        updated = Instant.now(),
        received = Instant.now()
      )

      for {
        _ <- store.init()
        _ <- store.put(expectedEntry)
        actualEntry <- store.get(expectedEntry.id)
        someEntries <- store.list()
        _ <- store.delete(expectedEntry.id)
        missingEntry <- store.get(expectedEntry.id)
        noEntries <- store.list()
        _ <- store.drop()
      } yield {
        actualEntry should be(Some(expectedEntry))
        someEntries should be(Seq(expectedEntry))
        missingEntry should be(None)
        noEntries should be(Seq.empty)
      }
    }
  }

  it should "provide migrations" in withRetry {
    withStore { (profile, database) =>
      val store = new DefaultAnalyticsEntryStore(name = "TEST_ANALYTICS_ENTRIES", profile = profile, database = database)

      for {
        resultBefore <- store.list().failed
        migration = store.migrations.find(_.version == 1) match {
          case Some(migration) => migration
          case None            => fail("Expected migration with version == 1 but none was found")
        }
        neededBefore <- migration.needed.run()
        _ <- migration.action.run()
        neededAfter <- migration.needed.run()
        resultAfter <- store.list()
        _ <- store.drop()
      } yield {
        resultBefore.getMessage should include("""Table "TEST_ANALYTICS_ENTRIES" not found""")
        neededBefore should be(true)

        neededAfter should be(false)
        resultAfter should be(Seq.empty)
      }
    }
  }

  private implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

  private implicit val system: ActorSystem[Nothing] = ActorSystem(
    guardianBehavior = Behaviors.ignore,
    name = "DefaultAnalyticsEntryStoreSpec"
  )
}
