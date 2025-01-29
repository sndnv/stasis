package stasis.test.specs.unit.client.mocks

import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

import scala.concurrent.Future

import org.apache.pekko.Done
import org.apache.pekko.util.ByteString

import stasis.client.api.clients.ServerApiEndpointClient
import stasis.client.model.DatasetMetadata
import stasis.core.commands.proto.Command
import stasis.core.commands.proto.CommandParameters
import stasis.core.commands.proto.CommandSource
import stasis.core.commands.proto.LogoutUser
import stasis.shared.api.requests.CreateDatasetDefinition
import stasis.shared.api.requests.CreateDatasetEntry
import stasis.shared.api.requests.ResetUserPassword
import stasis.shared.api.requests.UpdateDatasetDefinition
import stasis.shared.api.responses.CreatedDatasetDefinition
import stasis.shared.api.responses.CreatedDatasetEntry
import stasis.shared.api.responses.Ping
import stasis.shared.api.responses.UpdatedUserSalt
import stasis.shared.model.datasets.DatasetDefinition
import stasis.shared.model.datasets.DatasetEntry
import stasis.shared.model.devices.Device
import stasis.shared.model.schedules.Schedule
import stasis.shared.model.users.User
import stasis.test.specs.unit.client.mocks.MockServerApiEndpointClient.Statistic
import stasis.test.specs.unit.shared.model.Generators

class MockServerApiEndpointClient(
  override val self: Device.Id
) extends ServerApiEndpointClient {
  private val stats: Map[Statistic, AtomicInteger] = Map(
    Statistic.DatasetEntryCreated -> new AtomicInteger(0),
    Statistic.DatasetEntryDeleted -> new AtomicInteger(0),
    Statistic.DatasetEntryRetrieved -> new AtomicInteger(0),
    Statistic.DatasetEntryRetrievedLatest -> new AtomicInteger(0),
    Statistic.DatasetEntriesRetrieved -> new AtomicInteger(0),
    Statistic.DatasetDefinitionCreated -> new AtomicInteger(0),
    Statistic.DatasetDefinitionUpdated -> new AtomicInteger(0),
    Statistic.DatasetDefinitionDeleted -> new AtomicInteger(0),
    Statistic.DatasetDefinitionRetrieved -> new AtomicInteger(0),
    Statistic.DatasetDefinitionsRetrieved -> new AtomicInteger(0),
    Statistic.PublicSchedulesRetrieved -> new AtomicInteger(0),
    Statistic.PublicScheduleRetrieved -> new AtomicInteger(0),
    Statistic.DatasetMetadataWithEntryIdRetrieved -> new AtomicInteger(0),
    Statistic.DatasetMetadataWithEntryRetrieved -> new AtomicInteger(0),
    Statistic.UserRetrieved -> new AtomicInteger(0),
    Statistic.UserSaltReset -> new AtomicInteger(0),
    Statistic.UserPasswordUpdated -> new AtomicInteger(0),
    Statistic.DeviceRetrieved -> new AtomicInteger(0),
    Statistic.DeviceKeyPushed -> new AtomicInteger(0),
    Statistic.DeviceKeyPulled -> new AtomicInteger(0),
    Statistic.DeviceKeyExists -> new AtomicInteger(0),
    Statistic.Ping -> new AtomicInteger(0),
    Statistic.Commands -> new AtomicInteger(0)
  )

  override val server: String = "mock-api-server"

  override def createDatasetDefinition(request: CreateDatasetDefinition): Future[CreatedDatasetDefinition] = {
    stats(Statistic.DatasetDefinitionCreated).getAndIncrement()
    Future.successful(CreatedDatasetDefinition(DatasetDefinition.generateId()))
  }

  override def updateDatasetDefinition(definition: DatasetDefinition.Id, request: UpdateDatasetDefinition): Future[Done] = {
    stats(Statistic.DatasetDefinitionUpdated).getAndIncrement()
    Future.successful(Done)
  }

  override def deleteDatasetDefinition(definition: DatasetDefinition.Id): Future[Done] = {
    stats(Statistic.DatasetDefinitionDeleted).getAndIncrement()
    Future.successful(Done)
  }

  override def createDatasetEntry(request: CreateDatasetEntry): Future[CreatedDatasetEntry] = {
    stats(Statistic.DatasetEntryCreated).getAndIncrement()
    Future.successful(CreatedDatasetEntry(DatasetEntry.generateId()))
  }

  override def deleteDatasetEntry(entry: DatasetEntry.Id): Future[Done] = {
    stats(Statistic.DatasetEntryDeleted).getAndIncrement()
    Future.successful(Done)
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

  override def resetUserSalt(): Future[UpdatedUserSalt] = {
    stats(Statistic.UserSaltReset).getAndIncrement()
    Future.successful(UpdatedUserSalt(salt = "test-salt"))
  }

  override def resetUserPassword(request: ResetUserPassword): Future[Done] = {
    stats(Statistic.UserPasswordUpdated).getAndIncrement()
    Future.successful(Done)
  }

  override def device(): Future[Device] = {
    stats(Statistic.DeviceRetrieved).getAndIncrement()
    Future.successful(
      Generators.generateDevice
    )
  }

  override def pushDeviceKey(key: ByteString): Future[Done] = {
    stats(Statistic.DeviceKeyPushed).getAndIncrement()
    Future.successful(Done)
  }

  override def pullDeviceKey(): Future[Option[ByteString]] = {
    stats(Statistic.DeviceKeyPulled).getAndIncrement()
    Future.successful(Some(ByteString("test-key")))
  }

  override def deviceKeyExists(): Future[Boolean] = {
    stats(Statistic.DeviceKeyExists).getAndIncrement()
    Future.successful(false)
  }

  override def ping(): Future[Ping] = {
    stats(Statistic.Ping).getAndIncrement()
    Future.successful(Ping())
  }

  override def commands(lastSequenceId: Option[Long]): Future[Seq[Command]] = {
    stats(Statistic.Commands).getAndIncrement()
    val commands = Seq(
      Command(
        sequenceId = 1,
        source = CommandSource.User,
        target = None,
        parameters = CommandParameters.Empty,
        created = Instant.now()
      ),
      Command(
        sequenceId = 2,
        source = CommandSource.Service,
        target = Some(self),
        parameters = LogoutUser(reason = Some("test")),
        created = Instant.now()
      ),
      Command(
        sequenceId = 3,
        source = CommandSource.User,
        target = None,
        parameters = CommandParameters.Empty,
        created = Instant.now()
      )
    )

    Future.successful(commands.filter(_.sequenceId > lastSequenceId.getOrElse(0L)))
  }

  def statistics: Map[Statistic, Int] = stats.view.mapValues(_.get()).toMap
}

object MockServerApiEndpointClient {
  def apply(): MockServerApiEndpointClient = new MockServerApiEndpointClient(self = Device.generateId())

  sealed trait Statistic
  object Statistic {
    case object DatasetEntryCreated extends Statistic
    case object DatasetEntryDeleted extends Statistic
    case object DatasetEntryRetrieved extends Statistic
    case object DatasetEntryRetrievedLatest extends Statistic
    case object DatasetEntriesRetrieved extends Statistic
    case object DatasetDefinitionCreated extends Statistic
    case object DatasetDefinitionUpdated extends Statistic
    case object DatasetDefinitionDeleted extends Statistic
    case object DatasetDefinitionRetrieved extends Statistic
    case object DatasetDefinitionsRetrieved extends Statistic
    case object PublicSchedulesRetrieved extends Statistic
    case object PublicScheduleRetrieved extends Statistic
    case object DatasetMetadataWithEntryIdRetrieved extends Statistic
    case object DatasetMetadataWithEntryRetrieved extends Statistic
    case object UserRetrieved extends Statistic
    case object UserSaltReset extends Statistic
    case object UserPasswordUpdated extends Statistic
    case object DeviceRetrieved extends Statistic
    case object DeviceKeyPushed extends Statistic
    case object DeviceKeyPulled extends Statistic
    case object DeviceKeyExists extends Statistic
    case object Ping extends Statistic
    case object Commands extends Statistic
  }
}
