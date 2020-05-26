package stasis.client.api.clients

import java.time.Instant

import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.{ActorSystem, SpawnProtocol}
import akka.http.caching.LfuCache
import akka.http.caching.scaladsl.{Cache, CachingSettings}
import stasis.client.model.DatasetMetadata
import stasis.shared.api.requests.{CreateDatasetDefinition, CreateDatasetEntry}
import stasis.shared.api.responses.{CreatedDatasetDefinition, CreatedDatasetEntry, Ping}
import stasis.shared.model.datasets.{DatasetDefinition, DatasetEntry}
import stasis.shared.model.devices.Device
import stasis.shared.model.schedules.Schedule
import stasis.shared.model.users.User

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class CachedServerApiEndpointClient(
  config: CachedServerApiEndpointClient.Config,
  underlying: ServerApiEndpointClient
)(implicit system: ActorSystem[SpawnProtocol])
    extends ServerApiEndpointClient {

  private implicit val ec: ExecutionContext = system.executionContext

  private val defaultCacheSettings: CachingSettings = CachingSettings(system.toUntyped)

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

  override def device(): Future[Device] =
    underlying.device()

  override def ping(): Future[Ping] =
    underlying.ping()

  override def publicSchedules(): Future[Seq[Schedule]] =
    underlying.publicSchedules()

  override def publicSchedule(schedule: Schedule.Id): Future[Schedule] =
    underlying.publicSchedule(schedule)

  override def createDatasetDefinition(request: CreateDatasetDefinition): Future[CreatedDatasetDefinition] =
    underlying.createDatasetDefinition(request)

  override def createDatasetEntry(request: CreateDatasetEntry): Future[CreatedDatasetEntry] =
    underlying.createDatasetEntry(request)

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
}
