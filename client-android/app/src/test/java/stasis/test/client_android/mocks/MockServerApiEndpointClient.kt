package stasis.test.client_android.mocks

import okio.ByteString
import stasis.client_android.lib.api.clients.ServerApiEndpointClient
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
import stasis.client_android.lib.utils.Try
import stasis.client_android.lib.utils.Try.Failure
import stasis.client_android.lib.utils.Try.Success
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

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
        Statistic.Ping to AtomicInteger(0)
    )

    private val deviceSecretRef: AtomicReference<ByteString?> = AtomicReference(null)

    override val server: String = "mock-api-server"

    override suspend fun datasetDefinitions(): Try<List<DatasetDefinition>> {
        stats[Statistic.DatasetDefinitionsRetrieved]?.getAndIncrement()
        return Success(emptyList())
    }

    override suspend fun datasetDefinition(definition: DatasetDefinitionId): Try<DatasetDefinition> {
        stats[Statistic.DatasetDefinitionRetrieved]?.getAndIncrement()
        return Failure(RuntimeException("Test failure"))
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
        return Success(emptyList())
    }

    override suspend fun datasetEntry(entry: DatasetEntryId): Try<DatasetEntry> {
        stats[Statistic.DatasetEntryRetrieved]?.getAndIncrement()
        return Failure(RuntimeException("Test failure"))
    }

    override suspend fun latestEntry(
        definition: DatasetDefinitionId,
        until: Instant?
    ): Try<DatasetEntry?> {
        stats[Statistic.DatasetEntryRetrievedLatest]?.getAndIncrement()
        return Failure(RuntimeException("Test failure"))
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
        return Success(emptyList())
    }

    override suspend fun publicSchedule(schedule: ScheduleId): Try<Schedule> {
        stats[Statistic.PublicScheduleRetrieved]?.getAndIncrement()
        return Failure(RuntimeException("Test failure"))
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
        return Failure(RuntimeException("Test failure"))
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
        return Failure(RuntimeException("Test failure"))
    }

    override suspend fun pushDeviceKey(key: ByteString): Try<Unit> {
        stats[Statistic.DeviceKeyPushed]?.getAndIncrement()
        deviceSecretRef.set(key)
        return Success(Unit)
    }

    override suspend fun pullDeviceKey(): Try<ByteString> {
        stats[Statistic.DeviceKeyPulled]?.getAndIncrement()
        return when (val secret = deviceSecretRef.get()) {
            null -> Failure(ResourceMissingFailure())
            else -> Success(secret)
        }
    }

    override suspend fun deviceKeyExists(): Try<Boolean> {
        stats[Statistic.DeviceKeyExists]?.getAndIncrement()
        return Success(false)
    }

    override suspend fun ping(): Try<Ping> {
        stats[Statistic.Ping]?.getAndIncrement()
        return Success(Ping(id = UUID.randomUUID()))
    }

    val statistics: Map<Statistic, Int>
        get() = stats.mapValues { it.value.get() }

    val deviceSecret: ByteString?
        get() = deviceSecretRef.get()

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
    }

    companion object {
        operator fun invoke(): MockServerApiEndpointClient =
            MockServerApiEndpointClient(self = UUID.randomUUID())
    }
}
