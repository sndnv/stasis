package stasis.client_android.lib.model

import java.nio.file.Path

data class SourceEntity(
    val path: Path,
    val existingMetadata: EntityMetadata?,
    val currentMetadata: EntityMetadata
) {
    init {
        existingMetadata?.let { existing ->
            require(existing.javaClass == currentMetadata.javaClass) {
                "Mismatched current metadata for [${currentMetadata.path}] and existing metadata for [${existing.path}]"
            }
        }
    }

    val hasChanged: Boolean by lazy {
        when (existingMetadata) {
            null -> true
            else -> existingMetadata.hasChanged(comparedTo = currentMetadata)
        }
    }

    val hasContentChanged: Boolean by lazy {
        when (val current = currentMetadata) {
            is EntityMetadata.File -> {
                when (val existing = existingMetadata) {
                    is EntityMetadata.File -> existing.size != current.size || existing.checksum != current.checksum
                    else -> true
                }
            }

            is EntityMetadata.Directory -> false
        }
    }
}
