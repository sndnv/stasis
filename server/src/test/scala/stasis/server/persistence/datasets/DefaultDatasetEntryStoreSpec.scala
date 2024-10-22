package stasis.server.persistence.datasets

import java.nio.charset.StandardCharsets
import java.time.Instant

import scala.concurrent.Future
import scala.concurrent.duration._

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors

import stasis.core.packaging.Crate
import stasis.layers.UnitSpec
import stasis.layers.persistence.SlickTestDatabase
import stasis.layers.telemetry.MockTelemetryContext
import stasis.core.persistence.backends.slick.LegacyKeyValueStore
import stasis.shared.model.datasets.DatasetDefinition
import stasis.shared.model.datasets.DatasetEntry
import stasis.shared.model.devices.Device

class DefaultDatasetEntryStoreSpec extends UnitSpec with SlickTestDatabase {
  "A DefaultDatasetEntryStore" should "add, retrieve and delete dataset entries" in withRetry {
    withStore { (profile, database) =>
      val store = new DefaultDatasetEntryStore(name = "TEST_ENTRIES", profile = profile, database = database)

      for {
        _ <- store.init()
        _ <- store.create(entry)
        actualEntry <- store.get(entry.id)
        someEntries <- store.list(entry.definition)
        _ <- store.delete(entry.id)
        missingEntry <- store.get(entry.id)
        noEntries <- store.list(entry.definition)
        _ <- store.drop()
      } yield {
        actualEntry should be(Some(entry))
        someEntries should be(Seq(entry))
        missingEntry should be(None)
        noEntries should be(Seq.empty)
      }
    }
  }

  it should "retrieve latest entries" in {
    withStore { (profile, database) =>
      val store = new DefaultDatasetEntryStore(name = "TEST_ENTRIES", profile = profile, database = database)

      val entries = Seq(earliestEntry, otherEntry, latestEntry)

      for {
        _ <- store.init()
        _ <- Future.sequence(entries.map(store.create))
        allEntries <- store.list(entry.definition)
        latestEntryForDefinition <- store.latest(
          definition = entry.definition,
          devices = Seq.empty,
          until = None
        )
        latestEntryUntilTimestamp <- store.latest(
          definition = entry.definition,
          devices = Seq.empty,
          until = Some(earliestEntry.created.plusSeconds((entryCreationDifference / 2).toSeconds))
        )
        latestEntryForDevice <- store.latest(
          definition = entry.definition,
          devices = Seq(Device.generateId()),
          until = None
        )
        _ <- store.drop()
      } yield {
        allEntries.map(_.id).sorted should be(entries.map(_.id).sorted)

        latestEntryForDefinition.map(_.id) should be(Some(latestEntry.id))
        latestEntryUntilTimestamp.map(_.id) should be(Some(earliestEntry.id))
        latestEntryForDevice should be(empty)
      }
    }
  }

  it should "provide migrations" in withRetry {
    withClue("from key-value store to version 1") {
      withStore { (profile, database) =>
        import play.api.libs.json._

        val name = "TEST_ENTRIES"
        val legacy = LegacyKeyValueStore(name = name, profile = profile, database = database)
        val current = new DefaultDatasetEntryStore(name = name, profile = profile, database = database)

        val entries = Seq(earliestEntry, otherEntry, latestEntry)

        val jsonEntries = entries.map { entry =>
          entry.id ->
            Json
              .obj(
                "id" -> entry.id,
                "definition" -> entry.definition,
                "device" -> entry.device,
                "data" -> entry.data,
                "metadata" -> entry.metadata,
                "created" -> entry.created
              )
              .toString()
              .getBytes(StandardCharsets.UTF_8)
        }

        for {
          _ <- legacy.create()
          _ <- Future.sequence(jsonEntries.map(e => legacy.insert(e._1.toString, e._2)))
          migration = current.migrations.find(_.version == 1) match {
            case Some(migration) => migration
            case None            => fail("Expected migration with version == 1 but none was found")
          }
          currentResultBefore <- current.list(entry.definition).failed
          neededBefore <- migration.needed.run()
          _ <- migration.action.run()
          neededAfter <- migration.needed.run()
          currentResultAfter <- current.list(entry.definition)
          _ <- current.drop()
        } yield {
          currentResultBefore.getMessage should include("""Column "ID" not found""")
          neededBefore should be(true)

          neededAfter should be(false)
          currentResultAfter.map(_.id).sorted should be(entries.map(_.id).sorted)
        }
      }
    }
  }

  private implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

  private implicit val system: ActorSystem[Nothing] = ActorSystem(
    guardianBehavior = Behaviors.ignore,
    "DefaultDatasetEntryStoreSpec"
  )

  private val entry = DatasetEntry(
    id = DatasetEntry.generateId(),
    definition = DatasetDefinition.generateId(),
    device = Device.generateId(),
    data = Set(Crate.generateId(), Crate.generateId(), Crate.generateId(), Crate.generateId(), Crate.generateId()),
    metadata = Crate.generateId(),
    created = Instant.now()
  )

  private val entryCreationDifference = 30.seconds

  private val earliestEntry = entry

  private val otherEntry = entry.copy(
    id = DatasetEntry.generateId(),
    created = earliestEntry.created.plusSeconds(entryCreationDifference.toSeconds)
  )

  private val latestEntry = entry.copy(
    id = DatasetEntry.generateId(),
    created = earliestEntry.created.plusSeconds((entryCreationDifference * 2).toSeconds)
  )
}
