package stasis.server.service.bootstrap

import java.util.UUID

import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors

import stasis.layers.UnitSpec
import stasis.server.persistence.devices.MockDeviceStore
import stasis.shared.model.devices.Device
import stasis.test.specs.unit.shared.model.Generators

class DeviceBootstrapEntityProviderSpec extends UnitSpec {
  "An DeviceBootstrapEntityProvider" should "provide its name and default entities" in {
    val provider = new DeviceBootstrapEntityProvider(MockDeviceStore())

    provider.name should be("devices")

    provider.default should be(empty)
  }

  it should "support loading entities from config" in {
    val provider = new DeviceBootstrapEntityProvider(MockDeviceStore())

    val expectedDeviceId = UUID.fromString("9b47ab81-c472-40e6-834e-6ede83f8893b")
    val expectedUserId = UUID.fromString("749d8c0e-6105-4022-ae0e-39bd77752c5d")

    bootstrapConfig.getConfigList("devices").asScala.map(provider.load).toList match {
      case device1 :: device2 :: Nil =>
        device1.owner should be(expectedUserId)
        device1.active should be(true)
        device1.limits should be(None)

        device2.id should be(expectedDeviceId)
        device2.owner should be(expectedUserId)
        device2.active should be(true)
        device2.limits should be(
          Some(
            Device.Limits(
              maxCrates = 100000,
              maxStorage = 536870912000L,
              maxStoragePerCrate = 1073741824L,
              maxRetention = 90.days,
              minRetention = 3.days
            )
          )
        )

      case other =>
        fail(s"Unexpected result received: [$other]")
    }
  }

  it should "support validating entities" in {
    val provider = new DeviceBootstrapEntityProvider(MockDeviceStore())(system.executionContext)

    val validDevices = Seq(
      Generators.generateDevice,
      Generators.generateDevice,
      Generators.generateDevice
    )

    val sharedId1 = Device.generateId()
    val sharedId2 = Device.generateId()

    val invalidDevices = Seq(
      Generators.generateDevice.copy(id = sharedId1),
      Generators.generateDevice.copy(id = sharedId1),
      Generators.generateDevice.copy(id = sharedId2),
      Generators.generateDevice.copy(id = sharedId2)
    )

    noException should be thrownBy provider.validate(validDevices).await

    val e = provider.validate(invalidDevices).failed.await

    e.getMessage should (be(s"Duplicate values [$sharedId1,$sharedId2] found for field [id] in [Device]") or be(
      s"Duplicate values [$sharedId2,$sharedId1] found for field [id] in [Device]"
    ))
  }

  it should "support creating entities" in {
    val store = MockDeviceStore()
    val provider = new DeviceBootstrapEntityProvider(store)

    for {
      existingBefore <- store.view().list()
      _ <- provider.create(Generators.generateDevice)
      existingAfter <- store.view().list()
    } yield {
      existingBefore should be(empty)
      existingAfter should not be empty
    }
  }

  it should "support rendering entities" in {
    val provider = new DeviceBootstrapEntityProvider(MockDeviceStore())

    val deviceWithLimits = Generators.generateDevice.copy(limits =
      Some(
        Device.Limits(
          maxCrates = 1,
          maxStorage = 2,
          maxStoragePerCrate = 3,
          maxRetention = 4.millis,
          minRetention = 5.millis
        )
      )
    )

    provider.render(deviceWithLimits, withPrefix = "") should be(
      s"""
         |  device:
         |    id:                      ${deviceWithLimits.id}
         |    name:                    ${deviceWithLimits.name}
         |    node:                    ${deviceWithLimits.node}
         |    owner:                   ${deviceWithLimits.owner}
         |    active:                  ${deviceWithLimits.active}
         |    limits:
         |      max-crates:            1
         |      max-storage:           2
         |      max-storage-per-crate: 3
         |      max-retention:         4 milliseconds
         |      min-retention:         5 milliseconds
         |    created:                 ${deviceWithLimits.created.toString}
         |    updated:                 ${deviceWithLimits.updated.toString}""".stripMargin
    )

    val deviceWithoutLimits = Generators.generateDevice.copy(limits = None)

    provider.render(deviceWithoutLimits, withPrefix = "") should be(
      s"""
         |  device:
         |    id:                      ${deviceWithoutLimits.id}
         |    name:                    ${deviceWithoutLimits.name}
         |    node:                    ${deviceWithoutLimits.node}
         |    owner:                   ${deviceWithoutLimits.owner}
         |    active:                  ${deviceWithoutLimits.active}
         |    limits:
         |      max-crates:            -
         |      max-storage:           -
         |      max-storage-per-crate: -
         |      max-retention:         -
         |      min-retention:         -
         |    created:                 ${deviceWithoutLimits.created.toString}
         |    updated:                 ${deviceWithoutLimits.updated.toString}""".stripMargin
    )
  }

  it should "support extracting entity IDs" in {
    val provider = new DeviceBootstrapEntityProvider(MockDeviceStore())

    val device = Generators.generateDevice

    provider.extractId(device) should be(device.id.toString)
  }

  private implicit val system: ActorSystem[Nothing] = ActorSystem(
    guardianBehavior = Behaviors.ignore,
    name = "DeviceBootstrapEntityProviderSpec"
  )

  private val bootstrapConfig = ConfigFactory.load("bootstrap-unit.conf").getConfig("bootstrap")
}
