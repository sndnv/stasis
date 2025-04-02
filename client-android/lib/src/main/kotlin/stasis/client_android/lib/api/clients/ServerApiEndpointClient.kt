package stasis.client_android.lib.api.clients

import okio.ByteString
import stasis.client_android.lib.discovery.ServiceApiClient
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
import stasis.core.commands.proto.Command
import java.time.Instant

@Suppress("TooManyFunctions")
interface ServerApiEndpointClient : ServiceApiClient {
    val self: DeviceId
    val server: String

    suspend fun datasetDefinitions(): Try<List<DatasetDefinition>>
    suspend fun datasetDefinition(definition: DatasetDefinitionId): Try<DatasetDefinition>
    suspend fun createDatasetDefinition(request: CreateDatasetDefinition): Try<CreatedDatasetDefinition>
    suspend fun updateDatasetDefinition(definition: DatasetDefinitionId, request: UpdateDatasetDefinition): Try<Unit>
    suspend fun deleteDatasetDefinition(definition: DatasetDefinitionId): Try<Unit>

    suspend fun datasetEntries(definition: DatasetDefinitionId): Try<List<DatasetEntry>>
    suspend fun datasetEntry(entry: DatasetEntryId): Try<DatasetEntry>
    suspend fun latestEntry(definition: DatasetDefinitionId, until: Instant?): Try<DatasetEntry?>
    suspend fun createDatasetEntry(request: CreateDatasetEntry): Try<CreatedDatasetEntry>
    suspend fun deleteDatasetEntry(entry: DatasetEntryId): Try<Unit>

    suspend fun publicSchedules(): Try<List<Schedule>>
    suspend fun publicSchedule(schedule: ScheduleId): Try<Schedule>

    suspend fun datasetMetadata(entry: DatasetEntryId): Try<DatasetMetadata>
    suspend fun datasetMetadata(entry: DatasetEntry): Try<DatasetMetadata>

    suspend fun user(): Try<User>
    suspend fun resetUserSalt(): Try<UpdatedUserSalt>
    suspend fun resetUserPassword(request: ResetUserPassword): Try<Unit>

    suspend fun device(): Try<Device>
    suspend fun pushDeviceKey(key: ByteString): Try<Unit>
    suspend fun pullDeviceKey(): Try<ByteString>
    suspend fun deviceKeyExists(): Try<Boolean>

    suspend fun ping(): Try<Ping>
    suspend fun commands(lastSequenceId: Long?): Try<List<Command>>
}
