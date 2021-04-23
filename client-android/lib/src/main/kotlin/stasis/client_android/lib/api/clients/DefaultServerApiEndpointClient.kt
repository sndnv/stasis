package stasis.client_android.lib.api.clients

import stasis.client_android.lib.api.clients.internal.ClientExtensions
import stasis.client_android.lib.encryption.Decoder
import stasis.client_android.lib.encryption.secrets.DeviceSecret
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
import stasis.client_android.lib.security.HttpCredentials
import java.time.Instant

class DefaultServerApiEndpointClient(
    serverApiUrl: String,
    override val credentials: suspend () -> HttpCredentials,
    private val decryption: DecryptionContext,
    override val self: DeviceId
) : ServerApiEndpointClient, ClientExtensions() {
    override val server: String = serverApiUrl.trimEnd { it == '/' }

    override suspend fun datasetDefinitions(): List<DatasetDefinition> {
        val definitions = jsonListRequest<DatasetDefinition> { builder ->
            builder
                .url("$server/datasets/definitions/own")
                .get()
        }

        return definitions.filter { it.device == self }
    }

    override suspend fun datasetDefinition(definition: DatasetDefinitionId): DatasetDefinition {
        val actualDefinition = jsonRequest<DatasetDefinition> { builder ->
            builder
                .url("$server/datasets/definitions/own/${definition}")
                .get()
        }

        return if (actualDefinition.device == self) {
            actualDefinition
        } else {
            throw IllegalArgumentException("Cannot retrieve dataset definition for a different device")
        }
    }

    override suspend fun createDatasetDefinition(request: CreateDatasetDefinition): CreatedDatasetDefinition {
        if (request.device == self) {
            return jsonRequest { builder ->
                builder
                    .url("$server/datasets/definitions/own")
                    .post(request.toBody())
            }
        } else {
            throw IllegalArgumentException("Cannot create dataset definition for a different device: [${request.device}]")
        }
    }

    override suspend fun datasetEntries(definition: DatasetDefinitionId): List<DatasetEntry> {
        return jsonListRequest { builder ->
            builder
                .url("$server/datasets/entries/own/for-definition/$definition")
                .get()
        }
    }

    override suspend fun datasetEntry(entry: DatasetEntryId): DatasetEntry {
        return jsonRequest { builder ->
            builder
                .url("$server/datasets/entries/own/$entry")
                .get()
        }
    }

    override suspend fun latestEntry(definition: DatasetDefinitionId, until: Instant?): DatasetEntry? {
        val baseUrl = "$server/datasets/entries/own/for-definition/$definition/latest"

        val response = request { builder ->
            builder
                .url(
                    when (until) {
                        null -> baseUrl
                        else -> "$baseUrl?until=$until"
                    }
                )
        }

        return if (response.code == StatusNotFound) {
            null
        } else {
            response.toRequiredModel()
        }
    }

    override suspend fun createDatasetEntry(request: CreateDatasetEntry): CreatedDatasetEntry {
        return jsonRequest { builder ->
            builder
                .url("$server/datasets/entries/own/for-definition/${request.definition}")
                .post(request.toBody())
        }
    }

    override suspend fun publicSchedules(): List<Schedule> {
        return jsonListRequest { builder ->
            builder
                .url("$server/schedules/public")
                .get()
        }
    }

    override suspend fun publicSchedule(schedule: ScheduleId): Schedule {
        return jsonRequest { builder ->
            builder
                .url("$server/schedules/public/$schedule")
                .get()
        }
    }

    override suspend fun datasetMetadata(entry: DatasetEntryId): DatasetMetadata {
        return datasetMetadata(entry = datasetEntry(entry))
    }

    override suspend fun datasetMetadata(entry: DatasetEntry): DatasetMetadata {
        val encryptedEntryMetadata = decryption.core.pull(crate = entry.metadata)

        return DatasetMetadata.decrypt(
            metadataCrate = entry.metadata,
            metadataSecret = decryption.deviceSecret.toMetadataSecret(metadataCrate = entry.metadata),
            metadata = encryptedEntryMetadata,
            decoder = decryption.decoder
        )
    }

    override suspend fun user(): User {
        return jsonRequest { builder ->
            builder
                .url("$server/users/self")
                .get()
        }
    }

    override suspend fun device(): Device {
        return jsonRequest { builder ->
            builder
                .url("$server/devices/own/$self")
                .get()
        }
    }

    override suspend fun ping(): Ping {
        return jsonRequest { builder ->
            builder
                .url("$server/service/ping")
                .get()
        }
    }

    data class DecryptionContext(
        val core: ServerCoreEndpointClient,
        val deviceSecret: DeviceSecret,
        val decoder: Decoder
    )

    companion object {
        const val StatusNotFound: Int = 404
    }
}
