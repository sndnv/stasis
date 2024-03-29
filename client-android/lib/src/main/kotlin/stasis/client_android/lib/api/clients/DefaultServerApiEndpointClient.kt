package stasis.client_android.lib.api.clients

import stasis.client_android.lib.api.clients.exceptions.ResourceMissingFailure
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
import stasis.client_android.lib.utils.Try
import stasis.client_android.lib.utils.Try.Companion.map
import stasis.client_android.lib.utils.Try.Companion.recoverWith
import stasis.client_android.lib.utils.Try.Failure
import stasis.client_android.lib.utils.Try.Success
import java.time.Instant

class DefaultServerApiEndpointClient(
    serverApiUrl: String,
    override val credentials: suspend () -> HttpCredentials,
    private val decryption: DecryptionContext,
    override val self: DeviceId
) : ServerApiEndpointClient, ClientExtensions() {
    override val server: String = serverApiUrl.trimEnd { it == '/' }

    override suspend fun datasetDefinitions(): Try<List<DatasetDefinition>> =
        jsonListRequest<DatasetDefinition> { builder ->
            builder
                .url("$server/datasets/definitions/own")
                .get()
        }.map { definitions -> definitions.filter { it.device == self } }


    override suspend fun datasetDefinition(definition: DatasetDefinitionId): Try<DatasetDefinition> =
        jsonRequest<DatasetDefinition> { builder ->
            builder
                .url("$server/datasets/definitions/own/${definition}")
                .get()
        }.map { actualDefinition ->
            if (actualDefinition.device == self) {
                actualDefinition
            } else {
                throw IllegalArgumentException("Cannot retrieve dataset definition for a different device")
            }
        }

    override suspend fun createDatasetDefinition(request: CreateDatasetDefinition): Try<CreatedDatasetDefinition> =
        if (request.device == self) {
            jsonRequest { builder ->
                builder
                    .url("$server/datasets/definitions/own")
                    .post(request.toBody())
            }
        } else {
            Failure(
                IllegalArgumentException(
                    "Cannot create dataset definition for a different device: [${request.device}]"
                )
            )
        }

    override suspend fun datasetEntries(definition: DatasetDefinitionId): Try<List<DatasetEntry>> =
        jsonListRequest { builder ->
            builder
                .url("$server/datasets/entries/own/for-definition/$definition")
                .get()
        }

    override suspend fun datasetEntry(entry: DatasetEntryId): Try<DatasetEntry> =
        jsonRequest { builder ->
            builder
                .url("$server/datasets/entries/own/$entry")
                .get()
        }

    override suspend fun latestEntry(
        definition: DatasetDefinitionId,
        until: Instant?
    ): Try<DatasetEntry?> {
        val baseUrl = "$server/datasets/entries/own/for-definition/$definition/latest"

        return jsonRequest<DatasetEntry?> { builder ->
            builder
                .url(
                    when (until) {
                        null -> baseUrl
                        else -> "$baseUrl?until=$until"
                    }
                )
        }.recoverWith {
            when (it) {
                is ResourceMissingFailure -> Success(null)
                else -> Failure(it)
            }
        }
    }

    override suspend fun createDatasetEntry(request: CreateDatasetEntry): Try<CreatedDatasetEntry> =
        jsonRequest { builder ->
            builder
                .url("$server/datasets/entries/own/for-definition/${request.definition}")
                .post(request.toBody())
        }


    override suspend fun publicSchedules(): Try<List<Schedule>> =
        jsonListRequest { builder ->
            builder
                .url("$server/schedules/public")
                .get()
        }


    override suspend fun publicSchedule(schedule: ScheduleId): Try<Schedule> =
        jsonRequest { builder ->
            builder
                .url("$server/schedules/public/$schedule")
                .get()
        }


    override suspend fun datasetMetadata(entry: DatasetEntryId): Try<DatasetMetadata> =
        when (val result = datasetEntry(entry)) {
            is Success -> datasetMetadata(entry = result.value)
            is Failure -> Failure(result.exception)
        }


    override suspend fun datasetMetadata(entry: DatasetEntry): Try<DatasetMetadata> = Try {
        val encryptedEntryMetadata = decryption.core.pull(crate = entry.metadata)

        DatasetMetadata.decrypt(
            metadataCrate = entry.metadata,
            metadataSecret = decryption.deviceSecret().toMetadataSecret(entry.metadata),
            metadata = encryptedEntryMetadata,
            decoder = decryption.decoder
        )
    }

    override suspend fun user(): Try<User> =
        jsonRequest { builder ->
            builder
                .url("$server/users/self")
                .get()
        }

    override suspend fun device(): Try<Device> =
        jsonRequest { builder ->
            builder
                .url("$server/devices/own/$self")
                .get()
        }

    override suspend fun ping(): Try<Ping> =
        jsonRequest { builder ->
            builder
                .url("$server/service/ping")
                .get()
        }

    data class DecryptionContext(
        val core: ServerCoreEndpointClient,
        val deviceSecret: () -> DeviceSecret,
        val decoder: Decoder
    )
}
