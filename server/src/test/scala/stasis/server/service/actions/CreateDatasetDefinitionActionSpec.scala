package stasis.server.service.actions

import java.time.Instant

import scala.concurrent.duration._

import io.github.sndnv.layers.events.Event
import io.github.sndnv.layers.testing.UnitSpec
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors

import stasis.server.events.Events
import stasis.server.persistence.datasets.MockDatasetDefinitionStore
import stasis.shared.model.datasets.DatasetDefinition
import stasis.shared.model.devices.Device

class CreateDatasetDefinitionActionSpec extends UnitSpec {
  "A CreateDatasetDefinitionAction" should "load its config" in {
    val expected = CreateDatasetDefinitionAction.Config(
      info = "test-definition",
      redundantCopies = 2,
      existingVersions = DatasetDefinition.Retention(
        policy = DatasetDefinition.Retention.Policy.AtMost(versions = 5),
        duration = 7.days
      ),
      removedVersions = DatasetDefinition.Retention(
        policy = DatasetDefinition.Retention.Policy.LatestOnly,
        duration = 0.seconds
      )
    )

    val actual = CreateDatasetDefinitionAction.Config(config = definitionsConfig.getConfig("CreateDatasetDefinitionAction"))

    actual should be(expected)
  }

  it should "provide its trigger name" in {
    val store = MockDatasetDefinitionStore()

    val config = CreateDatasetDefinitionAction.Config(config = definitionsConfig.getConfig("CreateDatasetDefinitionAction"))

    val action = new CreateDatasetDefinitionAction(
      datasetDefinitions = store,
      config = config
    )

    action.trigger should be(Events.Devices.DeviceCreated.eventName)
  }

  it should "run when triggered" in {
    val store = MockDatasetDefinitionStore()

    val config = CreateDatasetDefinitionAction.Config(config = definitionsConfig.getConfig("CreateDatasetDefinitionAction"))

    val action = new CreateDatasetDefinitionAction(
      datasetDefinitions = store,
      config = config
    )

    val device = Device.generateId()

    val event = Event(
      name = "test-event",
      attributes = Map("device" -> device),
      timestamp = Instant.now
    )

    for {
      before <- store.view().list()
      createdEvent <- action.run(event)
      after <- store.view().list()
    } yield {
      before should be(empty)

      createdEvent.map(_.apply()) match {
        case Some(created) =>
          created.name should be("dataset_definition_created")
          created.attributes should be(
            Map[String, Event.AttributeValue](
              "device" -> device,
              "privileged" -> true
            )
          )

        case None =>
          fail("Expected an event but none was founf")
      }

      after.toList match {
        case definition :: Nil =>
          definition.info should be(config.info)
          definition.device should be(device)
          definition.redundantCopies should be(config.redundantCopies)
          definition.existingVersions should be(config.existingVersions)
          definition.removedVersions should be(config.removedVersions)

        case other =>
          fail(s"Unexpected result received: [$other]")
      }
    }
  }

  it should "fail if device ID not provided" in {
    val store = MockDatasetDefinitionStore()

    val action = new CreateDatasetDefinitionAction(
      datasetDefinitions = store,
      config = CreateDatasetDefinitionAction.Config(config = definitionsConfig.getConfig("CreateDatasetDefinitionAction"))
    )

    for {
      before <- store.view().list()
      e <- action.run(event = Event("test-event")).failed
      after <- store.view().list()
    } yield {
      before should be(empty)
      e.getMessage should be("Missing required attribute [device]")
      after should be(empty)
    }
  }

  private implicit val typedSystem: ActorSystem[Nothing] = ActorSystem(
    guardianBehavior = Behaviors.ignore,
    name = "CreateDatasetDefinitionActionSpec"
  )

  private val definitionsConfig = typedSystem.settings.config.getConfig(
    "stasis.test.server.service.components.actions.definitions"
  )
}
