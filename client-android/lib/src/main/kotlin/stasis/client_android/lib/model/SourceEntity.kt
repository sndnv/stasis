package stasis.client_android.lib.model

data class SourceEntity(
    val ref: EntityRef,
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
        val current = currentMetadata
        val existing = existingMetadata
        when {
            existing is EntityMetadata.WithContent && current is EntityMetadata.WithContent ->
                existing.size != current.size || existing.checksum != current.checksum

            existing == null && current is EntityMetadata.WithContent -> true

            else -> false
        }
    }
}
