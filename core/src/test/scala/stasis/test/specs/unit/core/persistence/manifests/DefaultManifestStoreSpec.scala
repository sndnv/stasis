package stasis.test.specs.unit.core.persistence.manifests

import java.nio.charset.StandardCharsets

import scala.concurrent.Future

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors

import stasis.core.persistence.backends.slick.LegacyKeyValueStore
import stasis.core.persistence.manifests.DefaultManifestStore
import io.github.sndnv.layers.testing.UnitSpec
import io.github.sndnv.layers.testing.persistence.TestSlickDatabase
import stasis.test.specs.unit.core.persistence.Generators
import stasis.test.specs.unit.core.telemetry.MockTelemetryContext

class DefaultManifestStoreSpec extends UnitSpec with TestSlickDatabase {
  "A DefaultManifestStore" should "add, retrieve and delete manifests" in withRetry {
    withStore { (profile, database) =>
      val store = new DefaultManifestStore(name = "TEST_MANIFESTS", profile = profile, database = database)
      val expectedManifest = Generators.generateManifest

      for {
        _ <- store.init()
        _ <- store.put(expectedManifest)
        actualManifest <- store.get(expectedManifest.crate)
        someManifests <- store.list()
        _ <- store.delete(expectedManifest.crate)
        missingManifest <- store.get(expectedManifest.crate)
        noManifests <- store.list()
        _ <- store.drop()
      } yield {
        actualManifest should be(Some(expectedManifest))
        someManifests should be(Seq(expectedManifest))
        missingManifest should be(None)
        noManifests should be(Seq.empty)
      }
    }
  }

  it should "provide migrations" in withRetry {
    withClue("from key-value store to version 1") {
      withStore { (profile, database) =>
        import play.api.libs.json._

        val name = "TEST_MANIFESTS"
        val legacy = LegacyKeyValueStore(name = name, profile = profile, database = database)
        val current = new DefaultManifestStore(name = name, profile = profile, database = database)

        val manifests = Seq(
          Generators.generateManifest,
          Generators.generateManifest,
          Generators.generateManifest
        )

        val jsonManifests = manifests.map { manifest =>
          manifest.crate ->
            Json
              .obj(
                "crate" -> manifest.crate,
                "size" -> manifest.size,
                "copies" -> manifest.copies,
                "origin" -> manifest.origin,
                "source" -> manifest.source,
                "destinations" -> manifest.destinations
              )
              .toString()
              .getBytes(StandardCharsets.UTF_8)
        }

        for {
          _ <- legacy.create()
          _ <- Future.sequence(jsonManifests.map(e => legacy.insert(e._1.toString, e._2)))
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
          currentResultBefore.getMessage should include("""Column "CRATE" not found""")
          neededBefore should be(true)

          neededAfter should be(false)
          currentResultAfter.map(_.crate).sorted should be(manifests.map(_.crate).sorted)
        }
      }
    }
  }

  private implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

  private implicit val system: ActorSystem[Nothing] = ActorSystem(
    guardianBehavior = Behaviors.ignore,
    name = "DefaultManifestStoreSpec"
  )
}
