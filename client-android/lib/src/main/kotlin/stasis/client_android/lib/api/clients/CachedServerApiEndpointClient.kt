package stasis.client_android.lib.api.clients

import okio.ByteString
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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

@Suppress("TooManyFunctions")
class CachedServerApiEndpointClient(
    private val underlying: ServerApiEndpointClient,
    private val datasetDefinitionsCache: Cache<DatasetDefinitionId, DatasetDefinition>,
    private val datasetEntriesCache: Cache<DatasetEntryId, DatasetEntry>,
    private val datasetMetadataCache: Cache<DatasetEntryId, DatasetMetadata>
) : ServerApiEndpointClient {
    override val self: DeviceId
        get() = underlying.self

    override val server: String
        get() = underlying.server

    private val allDefinitionsCached = AtomicBoolean(false)
    private val latestEntries = ConcurrentHashMap<DatasetDefinitionId, DatasetEntryId>()

    override suspend fun datasetDefinitions(): Try<List<DatasetDefinition>> =
        loadDefinitions()

    override suspend fun datasetDefinition(definition: DatasetDefinitionId): Try<DatasetDefinition> = Try {
        datasetDefinitionsCache.getOrLoad(
            key = definition,
            load = { underlying.datasetDefinition(definition).toOption() }
        ) ?: throw ResourceMissingFailure()
    }

    override suspend fun createDatasetDefinition(request: CreateDatasetDefinition): Try<CreatedDatasetDefinition> {
        val result = underlying.createDatasetDefinition(request)
        datasetDefinitionsCache.clear()
        allDefinitionsCached.set(false)
        return result
    }

    override suspend fun updateDatasetDefinition(
        definition: DatasetDefinitionId,
        request: UpdateDatasetDefinition
    ): Try<Unit> {
        val result = underlying.updateDatasetDefinition(definition, request)
        datasetDefinitionsCache.clear()
        allDefinitionsCached.set(false)
        return result
    }

    override suspend fun deleteDatasetDefinition(definition: DatasetDefinitionId): Try<Unit> {
        val result = underlying.deleteDatasetDefinition(definition)
        datasetDefinitionsCache.remove(definition)
        return result
    }

    override suspend fun datasetEntries(definition: DatasetDefinitionId): Try<List<DatasetEntry>> =
        underlying.datasetEntries(definition)

    override suspend fun datasetEntry(entry: DatasetEntryId): Try<DatasetEntry> = Try {
        datasetEntriesCache.getOrLoad(
            key = entry,
            load = { underlying.datasetEntry(entry).toOption() }
        ) ?: throw ResourceMissingFailure()
    }

    override suspend fun latestEntry(definition: DatasetDefinitionId, until: Instant?): Try<DatasetEntry?> =
        when (until) {
            null -> when (val entry = latestEntries[definition]) {
                null -> underlying.latestEntry(definition, until).map {
                    it?.apply {
                        latestEntries[definition] = id
                        datasetEntriesCache.put(id, this)
                    }
                }

                else -> Try {
                    datasetEntriesCache.getOrLoad(
                        key = entry,
                        load = { underlying.datasetEntry(entry).toOption() }
                    )
                }
            }

            // result depends on value of `until`; do not cache
            else -> underlying.latestEntry(definition, until)
        }

    override suspend fun createDatasetEntry(request: CreateDatasetEntry): Try<CreatedDatasetEntry> {
        val result = underlying.createDatasetEntry(request)
        latestEntries.remove(request.definition)
        return result
    }

    override suspend fun deleteDatasetEntry(entry: DatasetEntryId): Try<Unit> {
        val result = underlying.deleteDatasetEntry(entry)

        latestEntries.toList().find { it.second == entry }?.first?.let { latestEntries.remove(it) }
        datasetEntriesCache.remove(entry)

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

    private suspend fun loadDefinitions(): Try<List<DatasetDefinition>> =
        if (allDefinitionsCached.get()) {
            Try { datasetDefinitionsCache.all().values.toList().sortedBy { it.info } }
        } else {
            underlying.datasetDefinitions()
                .map { latest ->
                    if (latest.isNotEmpty()) {
                        latest.forEach { definition -> datasetDefinitionsCache.put(definition.id, definition) }
                        allDefinitionsCached.set(true)
                    }
                    latest.sortedBy { it.info }
                }
        }
}
