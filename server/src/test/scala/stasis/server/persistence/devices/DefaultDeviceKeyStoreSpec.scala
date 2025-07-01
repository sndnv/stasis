package stasis.server.persistence.devices

import java.nio.charset.StandardCharsets

import scala.concurrent.Future

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.util.ByteString

import stasis.core.persistence.backends.slick.LegacyKeyValueStore
import io.github.sndnv.layers.testing.UnitSpec
import io.github.sndnv.layers.testing.persistence.TestSlickDatabase
import io.github.sndnv.layers.telemetry.mocks.MockTelemetryContext
import stasis.test.specs.unit.shared.model.Generators

class DefaultDeviceKeyStoreSpec extends UnitSpec with TestSlickDatabase {
  "A DefaultDeviceKeyStore" should "add, retrieve and delete device keys" in withRetry {
    withStore { (profile, database) =>
      val store = new DefaultDeviceKeyStore(name = "TEST_DEVICE_KEYS", profile = profile, database = database)
      val expectedDeviceKey = Generators.generateDeviceKey

      for {
        _ <- store.init()
        _ <- store.put(expectedDeviceKey)
        actualKey <- store.get(expectedDeviceKey.device)
        keyExistsBefore <- store.exists(expectedDeviceKey.device)
        someKeys <- store.list()
        _ <- store.delete(expectedDeviceKey.device)
        missingKey <- store.get(expectedDeviceKey.device)
        keyExistsAfter <- store.exists(expectedDeviceKey.device)
        noKeys <- store.list()
        _ <- store.drop()
      } yield {
        actualKey should be(Some(expectedDeviceKey))
        keyExistsBefore should be(true)
        someKeys should be(Seq(expectedDeviceKey.copy(value = ByteString.empty)))
        missingKey should be(None)
        keyExistsAfter should be(false)
        noKeys should be(Seq.empty)
      }
    }
  }

  it should "provide migrations" in withRetry {
    withClue("from key-value store to version 1") {
      withStore { (profile, database) =>
        import play.api.libs.json._

        import io.github.sndnv.layers.api.Formats._

        val name = "TEST_DEVICE_KEYS"
        val legacy = LegacyKeyValueStore(name = name, profile = profile, database = database)
        val current = new DefaultDeviceKeyStore(name = name, profile = profile, database = database)

        val keys = Seq(
          Generators.generateDeviceKey,
          Generators.generateDeviceKey,
          Generators.generateDeviceKey
        )

        val jsonDeviceKeys = keys.map { key =>
          key.device ->
            Json
              .obj(
                "value" -> key.value,
                "owner" -> key.owner,
                "device" -> key.device
              )
              .toString()
              .getBytes(StandardCharsets.UTF_8)
        }

        for {
          _ <- legacy.create()
          _ <- Future.sequence(jsonDeviceKeys.map(e => legacy.insert(e._1.toString, e._2)))
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
          currentResultBefore.getMessage should include("""Column "OWNER" not found""")
          neededBefore should be(true)

          neededAfter should be(false)
          currentResultAfter.map(_.device).sorted should be(keys.map(_.device).sorted)
        }
      }
    }
  }

  private implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

  private implicit val system: ActorSystem[Nothing] = ActorSystem(
    guardianBehavior = Behaviors.ignore,
    name = "DefaultDeviceKeyStoreSpec"
  )
}
