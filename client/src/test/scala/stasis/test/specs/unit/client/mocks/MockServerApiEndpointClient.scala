package stasis.test.specs.unit.client.mocks

import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

import stasis.client.api.clients.ServerApiEndpointClient
import stasis.client.model.DatasetMetadata
import stasis.shared.api.requests.{CreateDatasetDefinition, CreateDatasetEntry}
import stasis.shared.api.responses.{CreatedDatasetDefinition, CreatedDatasetEntry, Ping}
import stasis.shared.model.datasets.{DatasetDefinition, DatasetEntry}
import stasis.shared.model.devices.Device
import stasis.shared.model.schedules.Schedule
import stasis.shared.model.users.User
import stasis.test.specs.unit.client.mocks.MockServerApiEndpointClient.Statistic
import stasis.test.specs.unit.shared.model.Generators

import scala.concurrent.Future

class MockServerApiEndpointClient(
  override val self: Device.Id
) extends ServerApiEndpointClient {
  private val stats: Map[Statistic, AtomicInteger] = Map(
    Statistic.DatasetEntryCreated -> new AtomicInteger(0),
    Statistic.DatasetEntryRetrieved -> new AtomicInteger(0),
    Statistic.DatasetEntryRetrievedLatest -> new AtomicInteger(0),
    Statistic.DatasetEntriesRetrieved -> new AtomicInteger(0),
    Statistic.DatasetDefinitionCreated -> new AtomicInteger(0),
    Statistic.DatasetDefinitionRetrieved -> new AtomicInteger(0),
    Statistic.DatasetDefinitionsRetrieved -> new AtomicInteger(0),
    Statistic.PublicSchedulesRetrieved -> new AtomicInteger(0),
    Statistic.PublicScheduleRetrieved -> new AtomicInteger(0),
    Statistic.DatasetMetadataWithEntryIdRetrieved -> new AtomicInteger(0),
    Statistic.DatasetMetadataWithEntryRetrieved -> new AtomicInteger(0),
    Statistic.UserRetrieved -> new AtomicInteger(0),
    Statistic.DeviceRetrieved -> new AtomicInteger(0),
    Statistic.Ping -> new AtomicInteger(0)
  )

  override val server: String = "mock-api-server"

  override def createDatasetDefinition(request: CreateDatasetDefinition): Future[CreatedDatasetDefinition] = {
    stats(Statistic.DatasetDefinitionCreated).getAndIncrement()
    Future.successful(CreatedDatasetDefinition(DatasetDefinition.generateId()))
  }

  override def createDatasetEntry(request: CreateDatasetEntry): Future[CreatedDatasetEntry] = {
    stats(Statistic.DatasetEntryCreated).getAndIncrement()
    Future.successful(CreatedDatasetEntry(DatasetEntry.generateId()))
  }

  override def datasetDefinitions(): Future[Seq[DatasetDefinition]] = {
    stats(Statistic.DatasetDefinitionsRetrieved).getAndIncrement()
    Future.successful(
      Seq(
        Generators.generateDefinition,
        Generators.generateDefinition
      )
    )
  }

  override def datasetEntries(definition: DatasetDefinition.Id): Future[Seq[DatasetEntry]] = {
    stats(Statistic.DatasetEntriesRetrieved).getAndIncrement()
    Future.successful(
      Seq(
        Generators.generateEntry,
        Generators.generateEntry,
        Generators.generateEntry
      )
    )
  }

  override def datasetDefinition(definition: DatasetDefinition.Id): Future[DatasetDefinition] = {
    stats(Statistic.DatasetDefinitionRetrieved).getAndIncrement()
    Future.successful(
      Generators.generateDefinition
    )
  }

  override def datasetEntry(entry: DatasetEntry.Id): Future[DatasetEntry] = {
    stats(Statistic.DatasetEntryRetrieved).getAndIncrement()
    Future.successful(Generators.generateEntry)
  }

  override def latestEntry(definition: DatasetDefinition.Id, until: Option[Instant]): Future[Option[DatasetEntry]] = {
    stats(Statistic.DatasetEntryRetrievedLatest).getAndIncrement()
    Future.successful(Some(Generators.generateEntry))
  }

  override def publicSchedules(): Future[Seq[Schedule]] = {
    stats(Statistic.PublicSchedulesRetrieved).getAndIncrement()
    Future.successful(
      Seq(
        Generators.generateSchedule.copy(isPublic = true),
        Generators.generateSchedule.copy(isPublic = true),
        Generators.generateSchedule.copy(isPublic = true)
      )
    )
  }

  override def publicSchedule(schedule: Schedule.Id): Future[Schedule] = {
    stats(Statistic.PublicScheduleRetrieved).getAndIncrement()
    Future.successful(Generators.generateSchedule.copy(id = schedule, isPublic = true))
  }

  override def datasetMetadata(entry: DatasetEntry.Id): Future[DatasetMetadata] = {
    stats(Statistic.DatasetMetadataWithEntryIdRetrieved).getAndIncrement()
    Future.successful(DatasetMetadata.empty)
  }

  override def datasetMetadata(entry: DatasetEntry): Future[DatasetMetadata] = {
    stats(Statistic.DatasetMetadataWithEntryRetrieved).getAndIncrement()
    Future.successful(DatasetMetadata.empty)
  }

  override def user(): Future[User] = {
    stats(Statistic.UserRetrieved).getAndIncrement()
    Future.successful(
      Generators.generateUser
    )
  }

  override def device(): Future[Device] = {
    stats(Statistic.DeviceRetrieved).getAndIncrement()
    Future.successful(
      Generators.generateDevice
    )
  }

  override def ping(): Future[Ping] = {
    stats(Statistic.Ping).getAndIncrement()
    Future.successful(Ping())
  }

  def statistics: Map[Statistic, Int] = stats.view.mapValues(_.get()).toMap
}

object MockServerApiEndpointClient {
  def apply(): MockServerApiEndpointClient = new MockServerApiEndpointClient(self = Device.generateId())

  sealed trait Statistic
  object Statistic {
    case object DatasetEntryCreated extends Statistic
    case object DatasetEntryRetrieved extends Statistic
    case object DatasetEntryRetrievedLatest extends Statistic
    case object DatasetEntriesRetrieved extends Statistic
    case object DatasetDefinitionCreated extends Statistic
    case object DatasetDefinitionRetrieved extends Statistic
    case object DatasetDefinitionsRetrieved extends Statistic
    case object PublicSchedulesRetrieved extends Statistic
    case object PublicScheduleRetrieved extends Statistic
    case object DatasetMetadataWithEntryIdRetrieved extends Statistic
    case object DatasetMetadataWithEntryRetrieved extends Statistic
    case object UserRetrieved extends Statistic
    case object DeviceRetrieved extends Statistic
    case object Ping extends Statistic
  }
}
