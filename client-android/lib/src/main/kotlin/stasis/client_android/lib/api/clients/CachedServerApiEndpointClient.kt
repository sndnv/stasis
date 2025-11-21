package stasis.client_android.lib.api.clients

import okio.ByteString
import stasis.client_android.lib.api.clients.caching.CacheRefreshHandler
import stasis.client_android.lib.api.clients.caching.DatasetEntriesForDefinition
import stasis.client_android.lib.api.clients.exceptions.ResourceMissingFailure
import stasis.client_android.lib.model.DatasetMetadata
import stasis.client_android.lib.model.server.api.requests.CreateDatasetDefinition
import stasis.client_android.lib.model.server.api.requests.CreateDatasetEntry
import stasis.client_android.lib.model.server.api.requests.ResetUserPassword
import stasis.client_android.lib.model.server.api.requests.UpdateDatasetDefinition
import stasis.client_android.lib.model.server.api.responses.CreatedDatasetDefinition
import stasis.client_android.lib.model.server.api.responses.CreatedDatasetEntry
import stasis.client_android.lib.model.server.api.responses.Ping
import stasis.client_android.lib.model.server.api.responses.UpdatedUserSalt
import stasis.client_android.lib.model.server.datasets.DatasetDefinition
import stasis.client_android.lib.model.server.datasets.DatasetDefinitionId
import stasis.client_android.lib.model.server.datasets.DatasetEntry
import stasis.client_android.lib.model.server.datasets.DatasetEntryId
import stasis.client_android.lib.model.server.devices.Device
import stasis.client_android.lib.model.server.devices.DeviceId
import stasis.client_android.lib.model.server.schedules.Schedule
import stasis.client_android.lib.model.server.schedules.ScheduleId
import stasis.client_android.lib.model.server.users.User
import stasis.client_android.lib.telemetry.analytics.AnalyticsEntry
import stasis.client_android.lib.utils.Cache
import stasis.client_android.lib.utils.Try
import stasis.client_android.lib.utils.Try.Companion.map
import stasis.core.commands.proto.Command
import java.time.Instant

@Suppress("TooManyFunctions")
class CachedServerApiEndpointClient(
    private val underlying: ServerApiEndpointClient,
    private val datasetDefinitionsCache: Cache<DatasetDefinitionId, DatasetDefinition>,
    private val datasetEntriesCache: Cache<DatasetEntryId, DatasetEntry>,
    private val datasetEntriesForDefinitionCache: Cache<DatasetDefinitionId, DatasetEntriesForDefinition>,
    private val datasetMetadataCache: Cache<DatasetEntryId, DatasetMetadata>,
    val refreshHandler: CacheRefreshHandler
) : ServerApiEndpointClient {
    override val self: DeviceId
        get() = underlying.self

    override val server: String
        get() = underlying.server

    override suspend fun datasetDefinitions(): Try<List<DatasetDefinition>> = Try {
        datasetDefinitionsCache.all().ifEmpty {
            refreshHandler.refreshNow(target = CacheRefreshHandler.RefreshTarget.AllDatasetDefinitions)
            datasetDefinitionsCache.all()
        }.values.toList().sortedBy { it.info }
    }

    override suspend fun datasetDefinition(definition: DatasetDefinitionId): Try<DatasetDefinition> = Try {
        when (val cached = datasetDefinitionsCache.get(key = definition)) {
            null -> {
                refreshHandler.refreshNow(
                    target = CacheRefreshHandler.RefreshTarget.IndividualDatasetDefinition(
                        definition = definition
                    )
                )
                datasetDefinitionsCache.get(key = definition)
            }

            else -> cached
        } ?: throw ResourceMissingFailure()
    }

    override suspend fun createDatasetDefinition(request: CreateDatasetDefinition): Try<CreatedDatasetDefinition> {
        val result = underlying.createDatasetDefinition(request)

        if (result is Try.Success) {
            refreshHandler.refreshNow(
                target = CacheRefreshHandler.RefreshTarget.IndividualDatasetDefinition(result.value.definition)
            )
        }

        return result
    }

    override suspend fun updateDatasetDefinition(
        definition: DatasetDefinitionId,
        request: UpdateDatasetDefinition
    ): Try<Unit> {
        val result = underlying.updateDatasetDefinition(definition, request)

        if (result is Try.Success) {
            refreshHandler.refreshNow(
                target = CacheRefreshHandler.RefreshTarget.IndividualDatasetDefinition(definition)
            )
        }

        return result
    }

    override suspend fun deleteDatasetDefinition(definition: DatasetDefinitionId): Try<Unit> {
        val result = underlying.deleteDatasetDefinition(definition)
        datasetDefinitionsCache.remove(key = definition)
        datasetEntriesForDefinitionCache.remove(key = definition)
        return result
    }

    override suspend fun datasetEntries(definition: DatasetDefinitionId): Try<List<DatasetEntry>> = Try {
        when (val cached = datasetEntriesForDefinitionCache.get(key = definition)) {
            null -> {
                refreshHandler.refreshNow(target = CacheRefreshHandler.RefreshTarget.AllDatasetEntries(definition = definition))
                datasetEntriesForDefinitionCache.get(key = definition)
            }

            else -> cached
        }?.let { cached ->
            cached.entries.keys.mapNotNull { datasetEntriesCache.get(key = it) }
        } ?: emptyList()
    }

    override suspend fun datasetEntry(entry: DatasetEntryId): Try<DatasetEntry> = Try {
        when (val cached = datasetEntriesCache.get(key = entry)) {
            null -> {
                refreshHandler.refreshNow(target = CacheRefreshHandler.RefreshTarget.IndividualDatasetEntry(entry = entry))
                datasetEntriesCache.get(key = entry)
            }

            else -> cached
        } ?: throw ResourceMissingFailure()
    }

    override suspend fun latestEntry(definition: DatasetDefinitionId, until: Instant?): Try<DatasetEntry?> =
        when (until) {
            null -> datasetEntries(definition = definition).map { entries ->
                entries.maxByOrNull { it.created }
            }

            // result depends on value of `until`; do not cache
            else -> underlying.latestEntry(definition, until)
        }

    override suspend fun createDatasetEntry(request: CreateDatasetEntry): Try<CreatedDatasetEntry> {
        val result = underlying.createDatasetEntry(request)

        if (result is Try.Success) {
            refreshHandler.refreshNow(
                target = CacheRefreshHandler.RefreshTarget.IndividualDatasetEntry(result.value.entry)
            )
        }

        return result
    }

    override suspend fun deleteDatasetEntry(entry: DatasetEntryId): Try<Unit> {
        val result = underlying.deleteDatasetEntry(entry)

        if (result is Try.Success) {
            datasetEntriesCache.get(key = entry)?.let { cached ->
                datasetEntriesCache.remove(key = entry)
                refreshHandler.refreshNow(
                    target = CacheRefreshHandler.RefreshTarget.AllDatasetEntries(definition = cached.definition)
                )
            }
        }

        return result
    }

    override suspend fun publicSchedules(): Try<List<Schedule>> =
        underlying.publicSchedules()

    override suspend fun publicSchedule(schedule: ScheduleId): Try<Schedule> =
        underlying.publicSchedule(schedule)

    override suspend fun datasetMetadata(entry: DatasetEntryId): Try<DatasetMetadata> = Try {
        datasetMetadataCache.getOrLoad(
            key = entry,
            load = {
                underlying.datasetMetadata(entry = entry).toOption()
            }
        ) ?: throw ResourceMissingFailure()
    }

    override suspend fun datasetMetadata(entry: DatasetEntry): Try<DatasetMetadata> = Try {
        datasetMetadataCache.getOrLoad(
            key = entry.id,
            load = {
                underlying.datasetMetadata(entry = entry).toOption()
            }
        ) ?: throw ResourceMissingFailure()
    }

    override suspend fun user(): Try<User> =
        underlying.user()

    override suspend fun resetUserSalt(): Try<UpdatedUserSalt> =
        underlying.resetUserSalt()

    override suspend fun resetUserPassword(request: ResetUserPassword): Try<Unit> =
        underlying.resetUserPassword(request)

    override suspend fun device(): Try<Device> =
        underlying.device()

    override suspend fun pushDeviceKey(key: ByteString): Try<Unit> =
        underlying.pushDeviceKey(key)

    override suspend fun pullDeviceKey(): Try<ByteString> =
        underlying.pullDeviceKey()

    override suspend fun deviceKeyExists(): Try<Boolean> =
        underlying.deviceKeyExists()

    override suspend fun ping(): Try<Ping> =
        underlying.ping()

    override suspend fun commands(lastSequenceId: Long?): Try<List<Command>> =
        underlying.commands(lastSequenceId)

    override suspend fun sendAnalyticsEntry(entry: AnalyticsEntry): Try<Unit> =
        underlying.sendAnalyticsEntry(entry)
}
