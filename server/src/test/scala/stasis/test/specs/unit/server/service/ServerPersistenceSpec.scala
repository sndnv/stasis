package stasis.test.specs.unit.server.service

import java.util.concurrent.ThreadLocalRandom

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import com.typesafe.config.Config
import stasis.server.service.ServerPersistence
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.shared.model.Generators

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
    val expectedSchedule = Generators.generateSchedule
    val expectedUser = Generators.generateUser

    for {
      _ <- persistence.init()
      _ <- persistence.datasetDefinitions.manage().create(expectedDefinition)
      actualDefinition <- persistence.datasetDefinitions.view().get(expectedDefinition.id)
      _ <- persistence.datasetEntries.manage().create(expectedEntry)
      actualEntry <- persistence.datasetEntries.view().get(expectedEntry.id)
      _ <- persistence.deviceBootstrapCodes.manage().put(expectedInitCode)
      actualInitCode <- persistence.deviceBootstrapCodes.view().get(expectedInitCode.value)
      _ <- persistence.devices.manage().create(expectedDevice)
      actualDevice <- persistence.devices.view().get(expectedDevice.id)
      _ <- persistence.schedules.manage().create(expectedSchedule)
      actualSchedule <- persistence.schedules.view().get(expectedSchedule.id)
      _ <- persistence.users.manage().create(expectedUser)
      actualUser <- persistence.users.view().get(expectedUser.id)
      _ <- persistence.drop()
    } yield {
      actualDefinition should be(Some(expectedDefinition))
      actualEntry should be(Some(expectedEntry))
      actualInitCode should be(Some(expectedInitCode))
      actualDevice should be(Some(expectedDevice))
      actualSchedule should be(Some(expectedSchedule))
      actualUser should be(Some(expectedUser))
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
    val schedules = 3 // manage + view + view-public
    val users = 4 // manage + manage-self + view + view-self

    persistence.resources.size should be(
      datasetDefinitions + datasetEntries + deviceInitCodes + devices + schedules + users
    )
  }

  private implicit val system: ActorSystem[SpawnProtocol.Command] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol()): Behavior[SpawnProtocol.Command],
    "ServerPersistenceSpec"
  )

  private val config: Config = system.settings.config.getConfig("stasis.test.server")
}
