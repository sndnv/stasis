package stasis.client.api.clients

import java.time.Instant

import scala.concurrent.Future

import org.apache.pekko.Done
import org.apache.pekko.util.ByteString
import stasis.client.model.DatasetMetadata
import stasis.shared.api.requests.ResetUserPassword
import stasis.shared.api.requests.CreateDatasetDefinition
import stasis.shared.api.requests.CreateDatasetEntry
import stasis.shared.api.responses.UpdatedUserSalt
import stasis.shared.api.responses.CreatedDatasetDefinition
import stasis.shared.api.responses.CreatedDatasetEntry
import stasis.shared.api.responses.Ping
import stasis.shared.model.datasets.DatasetDefinition
import stasis.shared.model.datasets.DatasetEntry
import stasis.shared.model.devices.Device
import stasis.shared.model.schedules.Schedule
import stasis.shared.model.users.User

trait ServerApiEndpointClient {
  def self: Device.Id
  def server: String

  def datasetDefinitions(): Future[Seq[DatasetDefinition]]
  def datasetDefinition(definition: DatasetDefinition.Id): Future[DatasetDefinition]
  def createDatasetDefinition(request: CreateDatasetDefinition): Future[CreatedDatasetDefinition]

  def datasetEntries(definition: DatasetDefinition.Id): Future[Seq[DatasetEntry]]
  def datasetEntry(entry: DatasetEntry.Id): Future[DatasetEntry]
  def latestEntry(definition: DatasetDefinition.Id, until: Option[Instant]): Future[Option[DatasetEntry]]
  def createDatasetEntry(request: CreateDatasetEntry): Future[CreatedDatasetEntry]

  def publicSchedules(): Future[Seq[Schedule]]
  def publicSchedule(schedule: Schedule.Id): Future[Schedule]

  def datasetMetadata(entry: DatasetEntry.Id): Future[DatasetMetadata]
  def datasetMetadata(entry: DatasetEntry): Future[DatasetMetadata]

  def user(): Future[User]
  def resetUserSalt(): Future[UpdatedUserSalt]
  def resetUserPassword(request: ResetUserPassword): Future[Done]

  def device(): Future[Device]
  def pushDeviceKey(key: ByteString): Future[Done]
  def pullDeviceKey(): Future[Option[ByteString]]

  def ping(): Future[Ping]
}
