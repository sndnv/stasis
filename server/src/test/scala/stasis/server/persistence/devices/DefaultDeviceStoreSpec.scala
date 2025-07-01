package stasis.server.persistence.devices

import java.nio.charset.StandardCharsets

import scala.concurrent.Future
import scala.concurrent.duration._

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors

import stasis.core.persistence.backends.slick.LegacyKeyValueStore
import io.github.sndnv.layers.testing.UnitSpec
import io.github.sndnv.layers.testing.persistence.TestSlickDatabase
import io.github.sndnv.layers.telemetry.mocks.MockTelemetryContext
import stasis.shared.model.devices.Device
import stasis.test.specs.unit.shared.model.Generators

class DefaultDeviceStoreSpec extends UnitSpec with TestSlickDatabase {
  "A DefaultDeviceStore" should "add, retrieve and delete devices" in withRetry {
    withStore { (profile, database) =>
      val store = new DefaultDeviceStore(name = "TEST_DEVICES", profile = profile, database = database)

      val expectedDevice = Generators.generateDevice.copy(
        limits = Some(
          Device.Limits(
            maxCrates = 1,
            maxStorage = 2,
            maxStoragePerCrate = 3,
            maxRetention = 4.seconds,
            minRetention = 5.seconds
          )
        )
      )

      for {
        _ <- store.init()
        _ <- store.put(expectedDevice)
        actualDevice <- store.get(expectedDevice.id)
        someDevices <- store.list()
        _ <- store.delete(expectedDevice.id)
        missingDevice <- store.get(expectedDevice.id)
        noDevices <- store.list()
        _ <- store.drop()
      } yield {
        actualDevice should be(Some(expectedDevice))
        someDevices should be(Seq(expectedDevice))
        missingDevice should be(None)
        noDevices should be(Seq.empty)
      }
    }
  }

  it should "provide migrations" in withRetry {
    withClue("from key-value store to version 1") {
      withStore { (profile, database) =>
        import play.api.libs.json._

        import stasis.shared.api.Formats._

        val name = "TEST_DEVICES"
        val legacy = LegacyKeyValueStore(name = name, profile = profile, database = database)
        val current = new DefaultDeviceStore(name = name, profile = profile, database = database)

        val devices = Seq(
          Generators.generateDevice,
          Generators.generateDevice,
          Generators.generateDevice
        )

        val jsonDevices = devices.map { device =>
          device.id ->
            Json
              .obj(
                "id" -> device.id,
                "name" -> device.name,
                "node" -> device.node,
                "owner" -> device.owner,
                "active" -> device.active,
                "limits" -> device.limits
              )
              .toString()
              .getBytes(StandardCharsets.UTF_8)
        }

        for {
          _ <- legacy.create()
          _ <- Future.sequence(jsonDevices.map(e => legacy.insert(e._1.toString, e._2)))
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
          currentResultAfter.map(_.id).sorted should be(devices.map(_.id).sorted)
        }
      }
    }
  }

  private implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

  private implicit val system: ActorSystem[Nothing] = ActorSystem(
    guardianBehavior = Behaviors.ignore,
    "DefaultDeviceStoreSpec"
  )
}
