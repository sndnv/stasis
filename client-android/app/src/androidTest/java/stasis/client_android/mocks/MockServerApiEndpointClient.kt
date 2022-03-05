package stasis.client_android.mocks

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
import stasis.client_android.lib.utils.Try
import stasis.client_android.lib.utils.Try.Success
import java.time.Instant
import java.util.*
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

    override suspend fun datasetDefinitions(): Try<List<DatasetDefinition>> {
        stats[Statistic.DatasetDefinitionsRetrieved]?.getAndIncrement()
        return Success(
            listOf(
                Generators.generateDefinition(),
                Generators.generateDefinition()
            )
        )
    }

    override suspend fun datasetDefinition(definition: DatasetDefinitionId): Try<DatasetDefinition> {
        stats[Statistic.DatasetDefinitionRetrieved]?.getAndIncrement()
        return Success(Generators.generateDefinition())
    }

    override suspend fun createDatasetDefinition(request: CreateDatasetDefinition): Try<CreatedDatasetDefinition> {
        stats[Statistic.DatasetDefinitionCreated]?.getAndIncrement()
        return Success(CreatedDatasetDefinition(definition = UUID.randomUUID()))
    }

    override suspend fun datasetEntries(definition: DatasetDefinitionId): Try<List<DatasetEntry>> {
        stats[Statistic.DatasetEntriesRetrieved]?.getAndIncrement()
        return Success(
            listOf(
                Generators.generateEntry(),
                Generators.generateEntry(),
                Generators.generateEntry()
            )
        )
    }

    override suspend fun datasetEntry(entry: DatasetEntryId): Try<DatasetEntry> {
        stats[Statistic.DatasetEntryRetrieved]?.getAndIncrement()
        return Success(Generators.generateEntry())
    }

    override suspend fun latestEntry(
        definition: DatasetDefinitionId,
        until: Instant?
    ): Try<DatasetEntry?> {
        stats[Statistic.DatasetEntryRetrievedLatest]?.getAndIncrement()
        return Success(Generators.generateEntry())
    }

    override suspend fun createDatasetEntry(request: CreateDatasetEntry): Try<CreatedDatasetEntry> {
        stats[Statistic.DatasetEntryCreated]?.getAndIncrement()
        return Success(CreatedDatasetEntry(entry = UUID.randomUUID()))
    }

    override suspend fun publicSchedules(): Try<List<Schedule>> {
        stats[Statistic.PublicSchedulesRetrieved]?.getAndIncrement()
        return Success(
            listOf(
                Generators.generateSchedule().copy(isPublic = true),
                Generators.generateSchedule().copy(isPublic = true),
                Generators.generateSchedule().copy(isPublic = true)
            )
        )
    }

    override suspend fun publicSchedule(schedule: ScheduleId): Try<Schedule> {
        stats[Statistic.PublicScheduleRetrieved]?.getAndIncrement()
        return Success(Generators.generateSchedule().copy(id = schedule, isPublic = true))
    }

    override suspend fun datasetMetadata(entry: DatasetEntryId): Try<DatasetMetadata> {
        stats[Statistic.DatasetMetadataWithEntryIdRetrieved]?.getAndIncrement()
        return Success(DatasetMetadata.empty())
    }

    override suspend fun datasetMetadata(entry: DatasetEntry): Try<DatasetMetadata> {
        stats[Statistic.DatasetMetadataWithEntryRetrieved]?.getAndIncrement()
        return Success(DatasetMetadata.empty())
    }

    override suspend fun user(): Try<User> {
        stats[Statistic.UserRetrieved]?.getAndIncrement()
        return Success(Generators.generateUser())
    }

    override suspend fun device(): Try<Device> {
        stats[Statistic.DeviceRetrieved]?.getAndIncrement()
        return Success(Generators.generateDevice())
    }

    override suspend fun ping(): Try<Ping> {
        stats[Statistic.Ping]?.getAndIncrement()
        return Success(Ping(id = UUID.randomUUID()))
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
        operator fun invoke(): MockServerApiEndpointClient =
            MockServerApiEndpointClient(self = UUID.randomUUID())
    }
}
