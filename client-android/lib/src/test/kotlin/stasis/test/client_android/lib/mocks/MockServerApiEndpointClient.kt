package stasis.test.client_android.lib.mocks

import okio.ByteString
import okio.ByteString.Companion.toByteString
import stasis.client_android.lib.api.clients.ServerApiEndpointClient
import stasis.client_android.lib.model.DatasetMetadata
import stasis.client_android.lib.model.server.api.requests.CreateDatasetDefinition
import stasis.client_android.lib.model.server.api.requests.CreateDatasetEntry
import stasis.client_android.lib.model.server.api.requests.ResetUserPassword
import stasis.client_android.lib.model.server.api.requests.UpdateDatasetDefinition
import stasis.client_android.lib.model.server.api.responses.CommandAsJson.Companion.asProtobuf
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
import stasis.core.commands.proto.CommandParameters
import stasis.core.commands.proto.LogoutUser
import stasis.test.client_android.lib.model.Generators
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
        val commands = listOf(
            Command(
                sequenceId = 1,
                source = "user",
                target = null,
                parameters = CommandParameters(),
                created = Instant.now().toEpochMilli()
            ),
            Command(
                sequenceId = 2,
                source = "service",
                target = self.asProtobuf(),
                parameters = CommandParameters(logoutUser = LogoutUser(reason = "test")),
                created = Instant.now().toEpochMilli()
            ),
            Command(
                sequenceId = 3,
                source = "user",
                target = null,
                parameters = CommandParameters(),
                created = Instant.now().toEpochMilli()
            ),
        )

        return Success(commands.filter { it.sequenceId > (lastSequenceId ?: 0) })
    }

    override suspend fun sendAnalyticsEntry(entry: AnalyticsEntry): Try<Unit> {
        stats[Statistic.AnalyticsEntriesSent]?.getAndIncrement()
        return Success(Unit)
    }

    val statistics: Map<Statistic, Int>
        get() = stats.mapValues { it.value.get() }

    sealed class Statistic {
        object DatasetEntryCreated : Statistic()
        object DatasetEntryDeleted : Statistic()
        object DatasetEntryRetrieved : Statistic()
        object DatasetEntryRetrievedLatest : Statistic()
        object DatasetEntriesRetrieved : Statistic()
        object DatasetDefinitionCreated : Statistic()
        object DatasetDefinitionUpdated : Statistic()
        object DatasetDefinitionDeleted : Statistic()
        object DatasetDefinitionRetrieved : Statistic()
        object DatasetDefinitionsRetrieved : Statistic()
        object PublicSchedulesRetrieved : Statistic()
        object PublicScheduleRetrieved : Statistic()
        object DatasetMetadataWithEntryIdRetrieved : Statistic()
        object DatasetMetadataWithEntryRetrieved : Statistic()
        object UserRetrieved : Statistic()
        object UserSaltReset : Statistic()
        object UserPasswordUpdated : Statistic()
        object DeviceRetrieved : Statistic()
        object DeviceKeyPushed : Statistic()
        object DeviceKeyPulled : Statistic()
        object DeviceKeyExists : Statistic()
        object Ping : Statistic()
        object Commands : Statistic()
        object AnalyticsEntriesSent : Statistic()
    }

    companion object {
        operator fun invoke(): MockServerApiEndpointClient =
            MockServerApiEndpointClient(self = UUID.randomUUID())
    }
}
