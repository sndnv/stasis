package stasis.client_android.lib.model

import java.nio.file.Path

data class TargetEntity(
    val path: Path,
    val destination: Destination,
    val existingMetadata: EntityMetadata,
    val currentMetadata: EntityMetadata?
) {
    init {
        currentMetadata?.let { current ->
            require(current.javaClass == existingMetadata.javaClass) {
                "Mismatched current metadata for [${current.path}] and existing metadata for [${existingMetadata.path}]"
            }
        }
    }

    val hasChanged: Boolean by lazy {
        when (currentMetadata) {
            null -> true
            else -> existingMetadata.hasChanged(comparedTo = currentMetadata)
        }
    }

    val hasContentChanged: Boolean by lazy {
        when (val existing = existingMetadata) {
            is EntityMetadata.File -> {
                when (val current = currentMetadata) {
                    is EntityMetadata.File -> existing.size != current.size || existing.checksum != current.checksum
                    else -> true
                }
            }

            is EntityMetadata.Directory -> false
        }
    }

    val originalPath: Path = path.fileSystem.getPath(existingMetadata.path)

    val destinationPath: Path = when (destination) {
        is Destination.Default -> originalPath
        is Destination.Directory -> if (destination.keepDefaultStructure) {
            destination.path.resolve(originalPath.fileSystem.getPath("/").relativize(originalPath))
        } else {
            destination.path.resolve(originalPath.fileName)
        }
    }

    sealed class Destination {
        object Default : Destination()
        data class Directory(val path: Path, val keepDefaultStructure: Boolean) : Destination()
    }
}
