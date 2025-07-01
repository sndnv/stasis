package stasis.server.persistence.schedules

import java.nio.charset.StandardCharsets

import scala.concurrent.Future

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors

import io.github.sndnv.layers.testing.UnitSpec
import io.github.sndnv.layers.testing.persistence.TestSlickDatabase
import io.github.sndnv.layers.telemetry.mocks.MockTelemetryContext
import stasis.core.persistence.backends.slick.LegacyKeyValueStore
import stasis.test.specs.unit.shared.model.Generators

class DefaultScheduleStoreSpec extends UnitSpec with TestSlickDatabase {
  "A DefaultScheduleStore" should "add, retrieve and delete schedules" in withRetry {
    withStore { (profile, database) =>
      val store = new DefaultScheduleStore(name = "TEST_SCHEDULES", profile = profile, database = database)
      val expectedSchedule = Generators.generateSchedule

      for {
        _ <- store.init()
        _ <- store.put(expectedSchedule)
        actualSchedule <- store.get(expectedSchedule.id)
        someSchedules <- store.list()
        _ <- store.delete(expectedSchedule.id)
        missingSchedule <- store.get(expectedSchedule.id)
        noSchedules <- store.list()
        _ <- store.drop()
      } yield {
        actualSchedule should be(Some(expectedSchedule))
        someSchedules should be(Seq(expectedSchedule))
        missingSchedule should be(None)
        noSchedules should be(Seq.empty)
      }
    }
  }

  it should "provide migrations" in withRetry {
    withClue("from key-value store to version 1") {
      withStore { (profile, database) =>
        import play.api.libs.json._

        import io.github.sndnv.layers.api.Formats._

        val name = "TEST_SCHEDULES"
        val legacy = LegacyKeyValueStore(name = name, profile = profile, database = database)
        val current = new DefaultScheduleStore(name = name, profile = profile, database = database)

        val schedules = Seq(
          Generators.generateSchedule,
          Generators.generateSchedule,
          Generators.generateSchedule
        )

        val jsonSchedules = schedules.map { schedule =>
          schedule.id ->
            Json
              .obj(
                "id" -> schedule.id,
                "info" -> schedule.info,
                "is_public" -> schedule.isPublic,
                "start" -> schedule.start,
                "interval" -> schedule.interval
              )
              .toString()
              .getBytes(StandardCharsets.UTF_8)
        }

        for {
          _ <- legacy.create()
          _ <- Future.sequence(jsonSchedules.map(e => legacy.insert(e._1.toString, e._2)))
          migration = current.migrations.find(_.version == 1) match {
            case Some(migration) => migration
            case None            => fail("Expected migration with version == 1 but none was found")
          }
          currentResultBefore <- current.list().failed
          neededBefore <- migration.needed.run()
          _ <- migration.action.run()
          neededAfter <- migration.needed.run()
          currentResultAfter <- current.list()
          _ <- current.drop()
        } yield {
          currentResultBefore.getMessage should include("""Column "ID" not found""")
          neededBefore should be(true)

          neededAfter should be(false)
          currentResultAfter.map(_.id).sorted should be(schedules.map(_.id).sorted)
        }
      }
    }
  }

  private implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

  private implicit val system: ActorSystem[Nothing] = ActorSystem(
    guardianBehavior = Behaviors.ignore,
    "DefaultScheduleStoreSpec"
  )
}
