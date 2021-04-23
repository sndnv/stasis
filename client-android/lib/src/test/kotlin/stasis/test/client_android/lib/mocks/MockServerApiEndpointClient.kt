package stasis.test.client_android.lib.mocks

import stasis.client_android.lib.api.clients.ServerApiEndpointClient
import stasis.client_android.lib.model.DatasetMetadata
import stasis.client_android.lib.model.server.api.requests.CreateDatasetDefinition
import stasis.client_android.lib.model.server.api.requests.CreateDatasetEntry
import stasis.client_android.lib.model.server.api.responses.CreatedDatasetDefinition
import stasis.client_android.lib.model.server.api.responses.CreatedDatasetEntry
import stasis.client_android.lib.model.server.api.responses.Ping
import stasis.client_android.lib.model.server.datasets.DatasetDefinition
import stasis.client_android.lib.model.server.datasets.DatasetDefinitionId
import stasis.client_android.lib.model.server.datasets.DatasetEntry
import stasis.client_android.lib.model.server.datasets.DatasetEntryId
import stasis.client_android.lib.model.server.devices.Device
import stasis.client_android.lib.model.server.devices.DeviceId
import stasis.client_android.lib.model.server.schedules.Schedule
import stasis.client_android.lib.model.server.schedules.ScheduleId
import stasis.client_android.lib.model.server.users.User
import stasis.test.client_android.lib.model.Generators
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

open class MockServerApiEndpointClient(
    override val self: DeviceId
) : ServerApiEndpointClient {
    private val stats: Map<Statistic, AtomicInteger> = mapOf(
        Statistic.DatasetEntryCreated to AtomicInteger(0),
        Statistic.DatasetEntryRetrieved to AtomicInteger(0),
        Statistic.DatasetEntryRetrievedLatest to AtomicInteger(0),
        Statistic.DatasetEntriesRetrieved to AtomicInteger(0),
        Statistic.DatasetDefinitionCreated to AtomicInteger(0),
        Statistic.DatasetDefinitionRetrieved to AtomicInteger(0),
        Statistic.DatasetDefinitionsRetrieved to AtomicInteger(0),
        Statistic.PublicSchedulesRetrieved to AtomicInteger(0),
        Statistic.PublicScheduleRetrieved to AtomicInteger(0),
        Statistic.DatasetMetadataWithEntryIdRetrieved to AtomicInteger(0),
        Statistic.DatasetMetadataWithEntryRetrieved to AtomicInteger(0),
        Statistic.UserRetrieved to AtomicInteger(0),
        Statistic.DeviceRetrieved to AtomicInteger(0),
        Statistic.Ping to AtomicInteger(0)
    )

    override val server: String = "mock-api-server"

    override suspend fun datasetDefinitions(): List<DatasetDefinition> {
        stats[Statistic.DatasetDefinitionsRetrieved]?.getAndIncrement()
        return listOf(
            Generators.generateDefinition(),
            Generators.generateDefinition()
        )
    }

    override suspend fun datasetDefinition(definition: DatasetDefinitionId): DatasetDefinition {
        stats[Statistic.DatasetDefinitionRetrieved]?.getAndIncrement()
        return Generators.generateDefinition()
    }

    override suspend fun createDatasetDefinition(request: CreateDatasetDefinition): CreatedDatasetDefinition {
        stats[Statistic.DatasetDefinitionCreated]?.getAndIncrement()
        return CreatedDatasetDefinition(definition = UUID.randomUUID())
    }

    override suspend fun datasetEntries(definition: DatasetDefinitionId): List<DatasetEntry> {
        stats[Statistic.DatasetEntriesRetrieved]?.getAndIncrement()
        return listOf(
            Generators.generateEntry(),
            Generators.generateEntry(),
            Generators.generateEntry()
        )
    }

    override suspend fun datasetEntry(entry: DatasetEntryId): DatasetEntry {
        stats[Statistic.DatasetEntryRetrieved]?.getAndIncrement()
        return Generators.generateEntry()
    }

    override suspend fun latestEntry(definition: DatasetDefinitionId, until: Instant?): DatasetEntry? {
        stats[Statistic.DatasetEntryRetrievedLatest]?.getAndIncrement()
        return Generators.generateEntry()
    }

    override suspend fun createDatasetEntry(request: CreateDatasetEntry): CreatedDatasetEntry {
        stats[Statistic.DatasetEntryCreated]?.getAndIncrement()
        return CreatedDatasetEntry(entry = UUID.randomUUID())
    }

    override suspend fun publicSchedules(): List<Schedule> {
        stats[Statistic.PublicSchedulesRetrieved]?.getAndIncrement()
        return listOf(
            Generators.generateSchedule().copy(isPublic = true),
            Generators.generateSchedule().copy(isPublic = true),
            Generators.generateSchedule().copy(isPublic = true)
        )
    }

    override suspend fun publicSchedule(schedule: ScheduleId): Schedule {
        stats[Statistic.PublicScheduleRetrieved]?.getAndIncrement()
        return Generators.generateSchedule().copy(id = schedule, isPublic = true)
    }

    override suspend fun datasetMetadata(entry: DatasetEntryId): DatasetMetadata {
        stats[Statistic.DatasetMetadataWithEntryIdRetrieved]?.getAndIncrement()
        return DatasetMetadata.empty()
    }

    override suspend fun datasetMetadata(entry: DatasetEntry): DatasetMetadata {
        stats[Statistic.DatasetMetadataWithEntryRetrieved]?.getAndIncrement()
        return DatasetMetadata.empty()
    }

    override suspend fun user(): User {
        stats[Statistic.UserRetrieved]?.getAndIncrement()
        return Generators.generateUser()
    }

    override suspend fun device(): Device {
        stats[Statistic.DeviceRetrieved]?.getAndIncrement()
        return Generators.generateDevice()
    }

    override suspend fun ping(): Ping {
        stats[Statistic.Ping]?.getAndIncrement()
        return Ping(id = UUID.randomUUID())
    }

    val statistics: Map<Statistic, Int>
        get() = stats.mapValues { it.value.get() }

    sealed class Statistic {
        object DatasetEntryCreated : Statistic()
        object DatasetEntryRetrieved : Statistic()
        object DatasetEntryRetrievedLatest : Statistic()
        object DatasetEntriesRetrieved : Statistic()
        object DatasetDefinitionCreated : Statistic()
        object DatasetDefinitionRetrieved : Statistic()
        object DatasetDefinitionsRetrieved : Statistic()
        object PublicSchedulesRetrieved : Statistic()
        object PublicScheduleRetrieved : Statistic()
        object DatasetMetadataWithEntryIdRetrieved : Statistic()
        object DatasetMetadataWithEntryRetrieved : Statistic()
        object UserRetrieved : Statistic()
        object DeviceRetrieved : Statistic()
        object Ping : Statistic()
    }

    companion object {
        operator fun invoke(): MockServerApiEndpointClient = MockServerApiEndpointClient(self = UUID.randomUUID())
    }
}
