package stasis.client.api.clients

import java.time.Instant

import scala.concurrent.Future
import scala.concurrent.duration._

import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.http.caching.LfuCache
import org.apache.pekko.http.caching.scaladsl.Cache
import org.apache.pekko.http.caching.scaladsl.CachingSettings
import org.apache.pekko.util.ByteString

import stasis.client.model.DatasetMetadata
import stasis.core.commands.proto.Command
import io.github.sndnv.layers.telemetry.analytics.AnalyticsEntry
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

class CachedServerApiEndpointClient(
  config: CachedServerApiEndpointClient.Config,
  underlying: ServerApiEndpointClient
)(implicit system: ActorSystem[Nothing])
    extends ServerApiEndpointClient {

  private val defaultCacheSettings: CachingSettings = CachingSettings(system.classicSystem)

  private val cacheSettings: CachingSettings = defaultCacheSettings.withLfuCacheSettings(
    defaultCacheSettings.lfuCacheSettings
      .withInitialCapacity(config.initialCapacity)
      .withMaxCapacity(config.maximumCapacity)
      .withTimeToLive(config.timeToLive)
      .withTimeToIdle(config.timeToIdle)
  )

  private val datasetDefinitionsCache: Cache[DatasetDefinition.Id, DatasetDefinition] = LfuCache(cacheSettings)
  private val datasetEntriesCache: Cache[DatasetEntry.Id, DatasetEntry] = LfuCache(cacheSettings)
  private val datasetMetadataCache: Cache[DatasetEntry.Id, DatasetMetadata] = LfuCache(cacheSettings)

  override def self: Device.Id =
    underlying.self

  override def server: String =
    underlying.server

  override def user(): Future[User] =
    underlying.user()

  override def resetUserSalt(): Future[UpdatedUserSalt] =
    underlying.resetUserSalt()

  override def resetUserPassword(request: ResetUserPassword): Future[Done] =
    underlying.resetUserPassword(request)

  override def device(): Future[Device] =
    underlying.device()

  override def pushDeviceKey(key: ByteString): Future[Done] =
    underlying.pushDeviceKey(key)

  override def pullDeviceKey(): Future[Option[ByteString]] =
    underlying.pullDeviceKey()

  override def deviceKeyExists(): Future[Boolean] =
    underlying.deviceKeyExists()

  override def ping(): Future[Ping] =
    underlying.ping()

  override def sendAnalyticsEntry(entry: AnalyticsEntry): Future[Done] =
    underlying.sendAnalyticsEntry(entry)

  override def commands(lastSequenceId: Option[Long]): Future[Seq[Command]] =
    underlying.commands(lastSequenceId)

  override def publicSchedules(): Future[Seq[Schedule]] =
    underlying.publicSchedules()

  override def publicSchedule(schedule: Schedule.Id): Future[Schedule] =
    underlying.publicSchedule(schedule)

  override def createDatasetDefinition(request: CreateDatasetDefinition): Future[CreatedDatasetDefinition] =
    underlying.createDatasetDefinition(request)

  override def updateDatasetDefinition(definition: DatasetDefinition.Id, request: UpdateDatasetDefinition): Future[Done] = {
    datasetDefinitionsCache.remove(definition)
    underlying.updateDatasetDefinition(definition, request)
  }

  override def deleteDatasetDefinition(definition: DatasetDefinition.Id): Future[Done] = {
    datasetDefinitionsCache.remove(definition)
    underlying.deleteDatasetDefinition(definition)
  }

  override def createDatasetEntry(request: CreateDatasetEntry): Future[CreatedDatasetEntry] =
    underlying.createDatasetEntry(request)

  override def deleteDatasetEntry(entry: DatasetEntry.Id): Future[Done] = {
    datasetEntriesCache.remove(entry)
    datasetMetadataCache.remove(entry)
    underlying.deleteDatasetEntry(entry)
  }

  override def datasetDefinitions(): Future[Seq[DatasetDefinition]] =
    underlying.datasetDefinitions()

  override def datasetEntries(definition: DatasetDefinition.Id): Future[Seq[DatasetEntry]] =
    underlying.datasetEntries(definition)

  override def latestEntry(definition: DatasetDefinition.Id, until: Option[Instant]): Future[Option[DatasetEntry]] =
    underlying.latestEntry(definition, until)

  override def datasetDefinition(definition: DatasetDefinition.Id): Future[DatasetDefinition] =
    datasetDefinitionsCache.getOrLoad(key = definition, loadValue = underlying.datasetDefinition)

  override def datasetEntry(entry: DatasetEntry.Id): Future[DatasetEntry] =
    datasetEntriesCache.getOrLoad(key = entry, loadValue = underlying.datasetEntry)

  override def datasetMetadata(entry: DatasetEntry.Id): Future[DatasetMetadata] =
    datasetMetadataCache.getOrLoad(key = entry, loadValue = underlying.datasetMetadata)

  override def datasetMetadata(entry: DatasetEntry): Future[DatasetMetadata] =
    datasetMetadataCache.getOrLoad(key = entry.id, loadValue = _ => underlying.datasetMetadata(entry))
}

object CachedServerApiEndpointClient {
  final case class Config(
    initialCapacity: Int,
    maximumCapacity: Int,
    timeToLive: FiniteDuration,
    timeToIdle: FiniteDuration
  )

  def apply(
    config: CachedServerApiEndpointClient.Config,
    underlying: ServerApiEndpointClient
  )(implicit system: ActorSystem[Nothing]): CachedServerApiEndpointClient =
    new CachedServerApiEndpointClient(
      config = config,
      underlying = underlying
    )
}
