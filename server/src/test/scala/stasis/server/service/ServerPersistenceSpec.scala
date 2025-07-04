package stasis.server.service

import java.util.concurrent.ThreadLocalRandom

import com.typesafe.config.Config
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors

import io.github.sndnv.layers.persistence.migration.MigrationResult
import io.github.sndnv.layers.telemetry.TelemetryContext
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.core.telemetry.MockTelemetryContext
import stasis.test.specs.unit.shared.model.Generators
import stasis.test.specs.unit.core.persistence.{Generators => CoreGenerators}

class ServerPersistenceSpec extends AsyncUnitSpec {
  "ServerPersistence" should "setup service data stores based on config" in {
    val persistence = new ServerPersistence(
      persistenceConfig = config.getConfig("persistence")
    )

    implicit val rnd: ThreadLocalRandom = ThreadLocalRandom.current()

    val expectedDefinition = Generators.generateDefinition
    val expectedEntry = Generators.generateEntry
    val expectedInitCode = Generators.generateDeviceBootstrapCode
    val expectedDevice = Generators.generateDevice
    val expectedDeviceKey = Generators.generateDeviceKey.copy(device = expectedDevice.id)
    val expectedCommand = CoreGenerators.generateCommand.copy(target = Some(expectedDevice.id))
    val expectedSchedule = Generators.generateSchedule
    val expectedUser = Generators.generateUser

    for {
      _ <- persistence.init()
      _ <- persistence.datasetDefinitions.manage().put(expectedDefinition)
      actualDefinition <- persistence.datasetDefinitions.view().get(expectedDefinition.id)
      _ <- persistence.datasetEntries.manage().create(expectedEntry)
      actualEntry <- persistence.datasetEntries.view().get(expectedEntry.id)
      _ <- persistence.deviceBootstrapCodes.manage().put(expectedInitCode)
      actualInitCode <- persistence.deviceBootstrapCodes.view().get(expectedInitCode.value)
      _ <- persistence.devices.manage().put(expectedDevice)
      actualDevice <- persistence.devices.view().get(expectedDevice.id)
      _ <- persistence.deviceKeys.manageSelf().put(Seq(expectedDevice.id), expectedDeviceKey)
      actualDeviceKey <- persistence.deviceKeys.viewSelf().get(Seq(expectedDevice.id), expectedDevice.id)
      _ <- persistence.deviceCommands.manage().put(expectedCommand)
      actualCommands <- persistence.deviceCommands.viewSelf().list(Seq(expectedDevice.id), expectedDevice.id)
      _ <- persistence.schedules.manage().put(expectedSchedule)
      actualSchedule <- persistence.schedules.view().get(expectedSchedule.id)
      _ <- persistence.users.manage().put(expectedUser)
      actualUser <- persistence.users.view().get(expectedUser.id)
      _ <- persistence.drop()
    } yield {
      actualDefinition should be(Some(expectedDefinition))
      actualEntry should be(Some(expectedEntry))
      actualInitCode should be(Some(expectedInitCode))
      actualDevice should be(Some(expectedDevice))
      actualDeviceKey should be(Some(expectedDeviceKey))
      actualCommands should be(Seq(expectedCommand.copy(sequenceId = 1)))
      actualSchedule should be(Some(expectedSchedule))
      actualUser should be(Some(expectedUser))
    }
  }

  it should "support running data store migrations" in {
    val persistence = new ServerPersistence(
      persistenceConfig = config.getConfig("persistence")
    )

    for {
      result <- persistence.migrate()
    } yield {
      result should be(MigrationResult(found = 8, executed = 2))
    }
  }

  it should "provide service data stores as resources" in {
    val persistence = new ServerPersistence(
      persistenceConfig = config.getConfig("persistence")
    )

    val datasetDefinitions = 4 // manage + manage-self + view + view-self
    val datasetEntries = 4 // manage + manage-self + view + view-self
    val deviceInitCodes = 4 // manage + manage-self + view + view-self
    val devices = 4 // manage + manage-self + view + view-self
    val deviceKeys = 4 // manage + manage-self + view + view-self
    val deviceCommands = 4 // manage + manage-self + view + view-self
    val schedules = 3 // manage + view + view-public
    val users = 4 // manage + manage-self + view + view-self
    val analytics = 3 // manage + manage-self + view

    persistence.resources.size should be(
      datasetDefinitions + datasetEntries
        + deviceInitCodes + devices + deviceKeys + deviceCommands
        + schedules + users + analytics
    )
  }

  private implicit val system: ActorSystem[Nothing] = ActorSystem(
    guardianBehavior = Behaviors.ignore,
    name = "ServerPersistenceSpec"
  )

  private implicit val telemetry: TelemetryContext = MockTelemetryContext()

  private val config: Config = system.settings.config.getConfig("stasis.test.server")
}
