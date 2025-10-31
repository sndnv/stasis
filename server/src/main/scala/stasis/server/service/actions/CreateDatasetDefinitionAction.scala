package stasis.server.service.actions

import java.time.Instant

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success

import io.github.sndnv.layers.events.Event
import io.github.sndnv.layers.service.actions.Action

import stasis.server.events.Events
import stasis.server.persistence.datasets.DatasetDefinitionStore
import stasis.server.service.CorePersistence
import stasis.server.service.ServerPersistence
import stasis.shared.model.datasets.DatasetDefinition

class CreateDatasetDefinitionAction(
  datasetDefinitions: DatasetDefinitionStore,
  config: CreateDatasetDefinitionAction.Config
) extends Action.WithEvent {
  override val trigger: String = Events.Devices.DeviceCreated.eventName

  override def run(event: Event): Future[Option[Event.Lazy]] =
    event.requireAttribute[Event.UuidAttributeValue](
      attributeKey = Events.Devices.Attributes.Device
    ) match {
      case Success(Event.UuidAttributeValue(device)) =>
        val now = Instant.now()

        datasetDefinitions
          .manage()
          .put(
            definition = DatasetDefinition(
              id = DatasetDefinition.generateId(),
              info = config.info,
              device = device,
              redundantCopies = config.redundantCopies,
              existingVersions = config.existingVersions,
              removedVersions = config.removedVersions,
              created = now,
              updated = now
            )
          )
          .map { _ =>
            Some(() =>
              Events.DatasetDefinitions.DatasetDefinitionCreated.createWithAttributes(
                Events.DatasetDefinitions.Attributes.Device.withValue(value = device),
                Events.DatasetDefinitions.Attributes.Privileged.withValue(value = true)
              )
            )
          }(ExecutionContext.parasitic)

      case Failure(e) =>
        Future.failed(e)
    }
}

object CreateDatasetDefinitionAction {
  object Factory extends ActionFactory[CreateDatasetDefinitionAction] {
    override val actionName: String = classOf[CreateDatasetDefinitionAction].getSimpleName

    override def create(
      core: CorePersistence,
      server: ServerPersistence,
      config: com.typesafe.config.Config
    ): CreateDatasetDefinitionAction =
      new CreateDatasetDefinitionAction(
        datasetDefinitions = server.datasetDefinitions,
        config = Config(config.getConfig("parameters"))
      )
  }

  final case class Config(
    info: String,
    redundantCopies: Int,
    existingVersions: DatasetDefinition.Retention,
    removedVersions: DatasetDefinition.Retention
  )

  object Config {
    def apply(config: com.typesafe.config.Config): Config =
      Config(
        info = config.getString("info"),
        redundantCopies = config.getInt("redundant-copies"),
        existingVersions = DatasetDefinition.Retention(config.getConfig("existing-versions")),
        removedVersions = DatasetDefinition.Retention(config.getConfig("removed-versions"))
      )
  }
}
