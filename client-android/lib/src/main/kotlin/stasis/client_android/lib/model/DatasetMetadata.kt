package stasis.client_android.lib.model

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Buffer
import okio.ByteString
import okio.Source
import okio.buffer
import stasis.client_android.lib.api.clients.ServerApiEndpointClient
import stasis.client_android.lib.compression.Compressor
import stasis.client_android.lib.compression.Gzip
import stasis.client_android.lib.encryption.Decoder
import stasis.client_android.lib.encryption.Encoder
import stasis.client_android.lib.encryption.secrets.DeviceMetadataSecret
import stasis.client_android.lib.model.EntityMetadata.Companion.toModel
import stasis.client_android.lib.model.EntityMetadata.Companion.toProto
import stasis.client_android.lib.model.FilesystemMetadata.Companion.toModel
import stasis.client_android.lib.model.FilesystemMetadata.Companion.toProto
import stasis.client_android.lib.model.core.CrateId
import stasis.client_android.lib.utils.Try
import stasis.client_android.lib.utils.Try.Companion.flatMap
import stasis.client_android.lib.utils.Try.Companion.map
import java.nio.file.Path
import java.nio.file.Paths

data class DatasetMetadata(
    val contentChanged: Map<Path, EntityMetadata>,
    val metadataChanged: Map<Path, EntityMetadata>,
    val filesystem: FilesystemMetadata
) {
    val contentChangedBytes: Long by lazy {
        contentChanged.values.sumOf {
            when (it) {
                is EntityMetadata.File -> it.size
                else -> 0L
            }
        }
    }

    suspend fun collect(
        entity: Path,
        api: ServerApiEndpointClient
    ): EntityMetadata? =
        filesystem.entities[entity]?.let { state ->
            when (state) {
                is FilesystemMetadata.EntityState.New, FilesystemMetadata.EntityState.Updated -> {
                    when (val metadata = contentChanged[entity] ?: metadataChanged[entity]) {
                        null -> throw IllegalArgumentException(
                            "Metadata for entity [${entity.toAbsolutePath()}] not found"
                        )
                        else -> metadata
                    }
                }

                is FilesystemMetadata.EntityState.Existing -> {
                    val entryMetadata = api.datasetMetadata(state.entry).get()
                    when (val metadata = entryMetadata.contentChanged[entity]
                        ?: entryMetadata.metadataChanged[entity]) {
                        null -> throw IllegalArgumentException(
                            "Expected metadata for entity [${entity.toAbsolutePath()}] " +
                                    "but none was found in metadata for entry [${state.entry}]"
                        )
                        else -> metadata
                    }
                }
            }
        }

    suspend fun require(
        entity: Path,
        api: ServerApiEndpointClient
    ): EntityMetadata =
        when (val metadata = collect(entity, api)) {
            null -> throw IllegalArgumentException("Required metadata for entity [${entity.toAbsolutePath()}] not found")
            else -> metadata
        }

    companion object {
        private val DefaultCompressor: Compressor = Gzip

        fun empty(): DatasetMetadata = DatasetMetadata(
            contentChanged = emptyMap(),
            metadataChanged = emptyMap(),
            filesystem = FilesystemMetadata.empty()
        )

        fun DatasetMetadata.toByteString(): ByteString {
            val data = stasis.client_android.lib.model.proto.DatasetMetadata(
                contentChanged = contentChanged.map { entity ->
                    entity.key.toAbsolutePath().toString() to entity.value.toProto()
                }.toMap(),
                metadataChanged = metadataChanged.map { entity ->
                    entity.key.toAbsolutePath().toString() to entity.value.toProto()
                }.toMap(),
                filesystem = filesystem.toProto()
            )

            return DefaultCompressor
                .compress(Buffer().write(data.encodeByteString()))
                .buffer()
                .use { it.readByteString() }
        }

        fun ByteString.toDatasetMetadata(): Try<DatasetMetadata> =
            Try {
                stasis.client_android.lib.model.proto.DatasetMetadata.ADAPTER.decode(
                    DefaultCompressor
                        .decompress(Buffer().write(this))
                        .buffer()
                        .use { it.readByteString() }
                )
            }.flatMap { data ->
                foldTryMap(
                    data.contentChanged.map { entity ->
                        Paths.get(entity.key) to entity.value.toModel()
                    }
                ).flatMap { contentChanged ->
                    foldTryMap(
                        data.metadataChanged.map { entity ->
                            Paths.get(entity.key) to entity.value.toModel()
                        }
                    ).flatMap { metadataChanged ->
                        data.filesystem.toModel().map { filesystem ->
                            DatasetMetadata(
                                contentChanged = contentChanged,
                                metadataChanged = metadataChanged,
                                filesystem = filesystem
                            )
                        }
                    }
                }
            }

        suspend fun encrypt(
            metadataSecret: DeviceMetadataSecret,
            metadata: DatasetMetadata,
            encoder: Encoder
        ): ByteString = withContext(Dispatchers.IO) {
            encoder
                .encrypt(Buffer().write(metadata.toByteString()), metadataSecret)
                .buffer()
                .use { it.readByteString() }
        }

        suspend fun decrypt(
            metadataCrate: CrateId,
            metadataSecret: DeviceMetadataSecret,
            metadata: Source?,
            decoder: Decoder
        ): DatasetMetadata = when (metadata) {
            null -> throw IllegalArgumentException("Cannot decrypt metadata crate [${metadataCrate}]; no data provided")
            else -> withContext(Dispatchers.IO) {
                decoder.decrypt(metadata, metadataSecret)
                    .buffer()
                    .use { it.readByteString().toDatasetMetadata().get() }
            }
        }

        private fun <K, V> foldTryMap(source: List<Pair<K, Try<V>>>): Try<Map<K, V>> =
            source.fold(Try { emptyMap() }) { tryCollected, (key, tryCurrent) ->
                tryCollected.flatMap { collected ->
                    tryCurrent.map { current -> collected + (key to current) }
                }
            }
    }
}
