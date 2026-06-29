package stasis.client_android.lib.ops.recovery

import stasis.client_android.lib.collection.rules.SourceUri

sealed interface RecoverySourceKind {
    data object Filesystem : RecoverySourceKind

    data class Library(val scheme: String) : RecoverySourceKind

    companion object {
        fun forEntity(entity: String): RecoverySourceKind =
            SourceUri.scheme(entity)?.let { Library(it) } ?: Filesystem
    }
}
