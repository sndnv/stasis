package stasis.test.specs.unit.server.service

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import com.typesafe.config.Config
import stasis.server.service.Persistence
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.server.model.Generators

class PersistenceSpec extends AsyncUnitSpec {
  "Persistence" should "setup service data stores based on config" in {
    val persistence = new Persistence(
      persistenceConfig = config.getConfig("persistence")
    )

    val expectedDefinition = Generators.generateDefinition
    val expectedEntry = Generators.generateEntry
    val expectedDevice = Generators.generateDevice
    val expectedSchedule = Generators.generateSchedule
    val expectedUser = Generators.generateUser

    for {
      _ <- persistence.init()
      _ <- persistence.datasetDefinitions.manage().create(expectedDefinition)
      actualDefinition <- persistence.datasetDefinitions.view().get(expectedDefinition.id)
      _ <- persistence.datasetEntries.manage().create(expectedEntry)
      actualEntry <- persistence.datasetEntries.view().get(expectedEntry.id)
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
      actualDevice should be(Some(expectedDevice))
      actualSchedule should be(Some(expectedSchedule))
      actualUser should be(Some(expectedUser))
    }
  }

  private implicit val system: ActorSystem[SpawnProtocol] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol.behavior): Behavior[SpawnProtocol],
    "PersistenceSpec"
  )

  private val config: Config = system.settings.config.getConfig("stasis.test.server")
}
