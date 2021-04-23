package stasis.client_android.lib.api.clients

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
import java.time.Instant

interface ServerApiEndpointClient {
    val self: DeviceId
    val server: String

    suspend fun datasetDefinitions(): List<DatasetDefinition>
    suspend fun datasetDefinition(definition: DatasetDefinitionId): DatasetDefinition
    suspend fun createDatasetDefinition(request: CreateDatasetDefinition): CreatedDatasetDefinition

    suspend fun datasetEntries(definition: DatasetDefinitionId): List<DatasetEntry>
    suspend fun datasetEntry(entry: DatasetEntryId): DatasetEntry
    suspend fun latestEntry(definition: DatasetDefinitionId, until: Instant?): DatasetEntry?
    suspend fun createDatasetEntry(request: CreateDatasetEntry): CreatedDatasetEntry

    suspend fun publicSchedules(): List<Schedule>
    suspend fun publicSchedule(schedule: ScheduleId): Schedule

    suspend fun datasetMetadata(entry: DatasetEntryId): DatasetMetadata
    suspend fun datasetMetadata(entry: DatasetEntry): DatasetMetadata

    suspend fun user(): User
    suspend fun device(): Device

    suspend fun ping(): Ping
}
