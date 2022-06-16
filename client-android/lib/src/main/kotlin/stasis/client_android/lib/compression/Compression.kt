package stasis.client_android.lib.compression

import stasis.client_android.lib.model.EntityMetadata
import stasis.client_android.lib.model.SourceEntity
import stasis.client_android.lib.model.TargetEntity
import java.nio.file.Path
import java.util.Locale

interface Compression {
    fun defaultCompression(): Compressor

    fun disabledExtensions(): Set<String>

    fun algorithmFor(entity: Path): String =
        if (compressionAllowedFor(entity)) {
            defaultCompression().name()
        } else {
            Identity.name()
        }

    fun encoderFor(entity: SourceEntity): Encoder =
        compressionFor(entity.currentMetadata)

    fun decoderFor(entity: TargetEntity): Decoder =
        compressionFor(entity.existingMetadata)

    private fun compressionAllowedFor(entity: Path): Boolean =
        !disabledExtensions().any { extension -> entity.toString().endsWith(".$extension") }

    private fun compressionFor(entity: EntityMetadata): Compressor =
        when (entity) {
            is EntityMetadata.File -> fromString(entity.compression)
            is EntityMetadata.Directory -> throw IllegalArgumentException(
                "Expected metadata for file but directory metadata for [${entity.path}] provided"
            )
        }

    companion object {
        operator fun invoke(
            withDefaultCompression: String,
            withDisabledExtensions: String
        ): Compression = invoke(
            withDefaultCompression = fromString(compression = withDefaultCompression),
            withDisabledExtensions = withDisabledExtensions
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .toSet()
        )

        operator fun invoke(
            withDefaultCompression: Compressor,
            withDisabledExtensions: Set<String>
        ): Compression = object : Compression {
            override fun defaultCompression(): Compressor = withDefaultCompression
            override fun disabledExtensions(): Set<String> = withDisabledExtensions
        }

        fun fromString(compression: String): Compressor =
            when (compression.lowercase(Locale.getDefault())) {
                Deflate.name() -> Deflate
                Gzip.name() -> Gzip
                Identity.name() -> Identity
                else -> throw IllegalArgumentException("Unsupported compression provided: [$compression]")
            }
    }
}
