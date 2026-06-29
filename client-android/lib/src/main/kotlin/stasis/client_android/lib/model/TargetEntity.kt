package stasis.client_android.lib.model

import java.nio.file.Files
import java.nio.file.Path

data class TargetEntity(
    val ref: EntityRef,
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
        val existing = existingMetadata
        val current = currentMetadata
        when {
            existing is EntityMetadata.WithContent && current is EntityMetadata.WithContent ->
                existing.size != current.size || existing.checksum != current.checksum

            existing is EntityMetadata.WithContent && current == null -> true

            else -> false
        }
    }

    val originalRef: EntityRef = ref.mapFilesystem { it.fileSystem.getPath(existingMetadata.path) }

    val destinationRef: EntityRef = when (destination) {
        is Destination.Default -> originalRef
        is Destination.Directory -> originalRef.flatMap { reference ->
            val original = when (reference) {
                is EntityRef.Filesystem -> reference.path
                is EntityRef.Library -> destination.path.fileSystem.getPath(reference.path)
            }

            val target = if (destination.keepDefaultStructure) {
                destination.path.resolve(original.fileSystem.getPath("/").relativize(original))
            } else {
                destination.path.resolve(original.fileName)
            }

            EntityRef.Filesystem(if (destination.preserveExisting) nonCollidingPath(target) else target)
        }
    }

    sealed class Destination {
        object Default : Destination()
        data class Directory(
            val path: Path,
            val keepDefaultStructure: Boolean,
            val preserveExisting: Boolean
        ) : Destination()
    }

    companion object {
        private fun nonCollidingPath(path: Path): Path {
            if (!Files.exists(path)) return path

            val fileName = path.fileName.toString()
            val separator = fileName.lastIndexOf('.')
            val base = if (separator > 0) fileName.substring(0, separator) else fileName
            val extension = if (separator > 0) fileName.substring(separator) else ""

            var index = 1
            var candidate = path.resolveSibling("$base-$index$extension")
            while (Files.exists(candidate)) {
                index += 1
                candidate = path.resolveSibling("$base-$index$extension")
            }

            return candidate
        }
    }
}
