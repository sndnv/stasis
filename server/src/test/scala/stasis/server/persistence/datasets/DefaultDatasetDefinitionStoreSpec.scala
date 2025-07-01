package stasis.server.persistence.datasets

import java.nio.charset.StandardCharsets

import scala.concurrent.Future

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors

import io.github.sndnv.layers.testing.UnitSpec
import io.github.sndnv.layers.testing.persistence.TestSlickDatabase
import io.github.sndnv.layers.telemetry.mocks.MockTelemetryContext
import stasis.core.persistence.backends.slick.LegacyKeyValueStore
import stasis.test.specs.unit.shared.model.Generators

class DefaultDatasetDefinitionStoreSpec extends UnitSpec with TestSlickDatabase {
  "A DefaultDatasetDefinitionStore" should "add, retrieve and delete dataset definitions" in withRetry {
    withStore { (profile, database) =>
      val store = new DefaultDatasetDefinitionStore(name = "TEST_DEFINITIONS", profile = profile, database = database)
      val expectedDefinition = Generators.generateDefinition

      for {
        _ <- store.init()
        _ <- store.put(expectedDefinition)
        actualDefinition <- store.get(expectedDefinition.id)
        someDefinitions <- store.list()
        _ <- store.delete(expectedDefinition.id)
        missingDefinition <- store.get(expectedDefinition.id)
        noDefinitions <- store.list()
        _ <- store.drop()
      } yield {
        actualDefinition should be(Some(expectedDefinition))
        someDefinitions should be(Seq(expectedDefinition))
        missingDefinition should be(None)
        noDefinitions should be(Seq.empty)
      }
    }
  }

  it should "provide migrations" in withRetry {
    withClue("from key-value store to version 1") {
      withStore { (profile, database) =>
        import play.api.libs.json._

        import stasis.shared.api.Formats._

        val name = "TEST_DEFINITIONS"
        val legacy = LegacyKeyValueStore(name = name, profile = profile, database = database)
        val current = new DefaultDatasetDefinitionStore(name = name, profile = profile, database = database)

        val definitions = Seq(
          Generators.generateDefinition,
          Generators.generateDefinition,
          Generators.generateDefinition
        )

        val jsonDefinitions = definitions.map { definition =>
          definition.id ->
            Json
              .obj(
                "id" -> definition.id,
                "info" -> definition.info,
                "device" -> definition.device,
                "redundant_copies" -> definition.redundantCopies,
                "existing_versions" -> definition.existingVersions,
                "removed_versions" -> definition.removedVersions
              )
              .toString()
              .getBytes(StandardCharsets.UTF_8)
        }

        for {
          _ <- legacy.create()
          _ <- Future.sequence(jsonDefinitions.map(e => legacy.insert(e._1.toString, e._2)))
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
          currentResultAfter.map(_.id).sorted should be(definitions.map(_.id).sorted)
        }
      }
    }
  }

  private implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

  private implicit val system: ActorSystem[Nothing] = ActorSystem(
    guardianBehavior = Behaviors.ignore,
    name = "DefaultDatasetDefinitionStoreSpec"
  )
}
