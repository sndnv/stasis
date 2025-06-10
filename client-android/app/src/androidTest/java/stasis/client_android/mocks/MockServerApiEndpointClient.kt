package stasis.client_android.mocks

import okio.ByteString
import okio.ByteString.Companion.toByteString
import stasis.client_android.lib.api.clients.ServerApiEndpointClient
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
import stasis.client_android.lib.utils.Try
import stasis.client_android.lib.utils.Try.Success
import stasis.core.commands.proto.Command
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

open class MockServerApiEndpointClient(
    override val self: DeviceId
) : ServerApiEndpointClient {
    private val stats: Map<Statistic, AtomicInteger> = mapOf(
        Statistic.DatasetEntryCreated to AtomicInteger(0),
        Statistic.DatasetEntryDeleted to AtomicInteger(0),
        Statistic.DatasetEntryRetrieved to AtomicInteger(0),
        Statistic.DatasetEntryRetrievedLatest to AtomicInteger(0),
        Statistic.DatasetEntriesRetrieved to AtomicInteger(0),
        Statistic.DatasetDefinitionCreated to AtomicInteger(0),
        Statistic.DatasetDefinitionUpdated to AtomicInteger(0),
        Statistic.DatasetDefinitionDeleted to AtomicInteger(0),
        Statistic.DatasetDefinitionRetrieved to AtomicInteger(0),
        Statistic.DatasetDefinitionsRetrieved to AtomicInteger(0),
        Statistic.PublicSchedulesRetrieved to AtomicInteger(0),
        Statistic.PublicScheduleRetrieved to AtomicInteger(0),
        Statistic.DatasetMetadataWithEntryIdRetrieved to AtomicInteger(0),
        Statistic.DatasetMetadataWithEntryRetrieved to AtomicInteger(0),
        Statistic.UserRetrieved to AtomicInteger(0),
        Statistic.UserSaltReset to AtomicInteger(0),
        Statistic.UserPasswordUpdated to AtomicInteger(0),
        Statistic.DeviceRetrieved to AtomicInteger(0),
        Statistic.DeviceKeyPushed to AtomicInteger(0),
        Statistic.DeviceKeyPulled to AtomicInteger(0),
        Statistic.DeviceKeyExists to AtomicInteger(0),
        Statistic.Ping to AtomicInteger(0),
        Statistic.Commands to AtomicInteger(0),
        Statistic.AnalyticsEntriesSent to AtomicInteger(0),
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

    override suspend fun updateDatasetDefinition(
        definition: DatasetDefinitionId,
        request: UpdateDatasetDefinition
    ): Try<Unit> {
        stats[Statistic.DatasetDefinitionUpdated]?.getAndIncrement()
        return Success(Unit)
    }

    override suspend fun deleteDatasetDefinition(definition: DatasetDefinitionId): Try<Unit> {
        stats[Statistic.DatasetDefinitionDeleted]?.getAndIncrement()
        return Success(Unit)
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

    override suspend fun deleteDatasetEntry(entry: DatasetEntryId): Try<Unit> {
        stats[Statistic.DatasetEntryDeleted]?.getAndIncrement()
        return Success(Unit)
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


    override suspend fun resetUserSalt(): Try<UpdatedUserSalt> {
        stats[Statistic.UserSaltReset]?.getAndIncrement()
        return Success(UpdatedUserSalt("test-salt"))
    }

    override suspend fun resetUserPassword(request: ResetUserPassword): Try<Unit> {
        stats[Statistic.UserPasswordUpdated]?.getAndIncrement()
        return Success(Unit)
    }

    override suspend fun device(): Try<Device> {
        stats[Statistic.DeviceRetrieved]?.getAndIncrement()
        return Success(Generators.generateDevice())
    }

    override suspend fun pushDeviceKey(key: ByteString): Try<Unit> {
        stats[Statistic.DeviceKeyPushed]?.getAndIncrement()
        return Success(Unit)
    }

    override suspend fun pullDeviceKey(): Try<ByteString> {
        stats[Statistic.DeviceKeyPulled]?.getAndIncrement()
        return Success("test-key".toByteArray().toByteString())
    }

    override suspend fun deviceKeyExists(): Try<Boolean> {
        stats[Statistic.DeviceKeyExists]?.getAndIncrement()
        return Success(false)
    }

    override suspend fun ping(): Try<Ping> {
        stats[Statistic.Ping]?.getAndIncrement()
        return Success(Ping(id = UUID.randomUUID()))
    }

    override suspend fun commands(lastSequenceId: Long?): Try<List<Command>> {
        stats[Statistic.Commands]?.getAndIncrement()
        return Success(emptyList())
    }

    override suspend fun sendAnalyticsEntry(entry: AnalyticsEntry): Try<Unit> {
        stats[Statistic.AnalyticsEntriesSent]?.getAndIncrement()
        return Success(Unit)
    }

    val statistics: Map<Statistic, Int>
        get() = stats.mapValues { it.value.get() }

    sealed class Statistic {
        data object DatasetEntryCreated : Statistic()
        data object DatasetEntryDeleted : Statistic()
        data object DatasetEntryRetrieved : Statistic()
        data object DatasetEntryRetrievedLatest : Statistic()
        data object DatasetEntriesRetrieved : Statistic()
        data object DatasetDefinitionCreated : Statistic()
        data object DatasetDefinitionUpdated : Statistic()
        data object DatasetDefinitionDeleted : Statistic()
        data object DatasetDefinitionRetrieved : Statistic()
        data object DatasetDefinitionsRetrieved : Statistic()
        data object PublicSchedulesRetrieved : Statistic()
        data object PublicScheduleRetrieved : Statistic()
        data object DatasetMetadataWithEntryIdRetrieved : Statistic()
        data object DatasetMetadataWithEntryRetrieved : Statistic()
        data object UserRetrieved : Statistic()
        data object UserSaltReset : Statistic()
        data object UserPasswordUpdated : Statistic()
        data object DeviceRetrieved : Statistic()
        data object DeviceKeyPushed : Statistic()
        data object DeviceKeyPulled : Statistic()
        data object DeviceKeyExists : Statistic()
        data object Ping : Statistic()
        data object Commands : Statistic()
        data object AnalyticsEntriesSent: Statistic()
    }

    companion object {
        operator fun invoke(): MockServerApiEndpointClient =
            MockServerApiEndpointClient(self = UUID.randomUUID())
    }
}
