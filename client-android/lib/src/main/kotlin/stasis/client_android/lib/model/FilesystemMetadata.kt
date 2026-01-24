package stasis.client_android.lib.model

import stasis.client_android.lib.model.FilesystemMetadata.EntityState.Companion.toModel
import stasis.client_android.lib.model.FilesystemMetadata.EntityState.Companion.toProto
import stasis.client_android.lib.model.server.datasets.DatasetEntryId
import stasis.client_android.lib.utils.Try
import stasis.client_android.lib.utils.Try.Companion.flatMap
import stasis.client_android.lib.utils.Try.Companion.map
import stasis.client_android.lib.utils.Try.Failure
import stasis.client_android.lib.utils.Try.Success
import java.util.UUID

data class FilesystemMetadata(
    val entities: Map<String, EntityState>
) {
    fun updated(changes: Iterable<String>, latestEntry: DatasetEntryId): FilesystemMetadata {
        val newAndUpdated = changes.map { entity ->
            if (entities.contains(entity)) {
                entity to EntityState.Updated
            } else {
                entity to EntityState.New
            }
        }

        val existing = entities.mapValues {
            when (it.value) {
                is EntityState.New -> EntityState.Existing(entry = latestEntry)
                is EntityState.Updated -> EntityState.Existing(entry = latestEntry)
                is EntityState.Existing -> it.value
            }
        }

        return FilesystemMetadata(entities = existing + newAndUpdated)
    }

    sealed class EntityState {
        object New : EntityState()
        data class Existing(val entry: DatasetEntryId) : EntityState()
        object Updated : EntityState()

        companion object {
            fun EntityState.toProto(): stasis.client_android.lib.model.proto.EntityState =
                when (this) {
                    is New -> stasis.client_android.lib.model.proto.EntityState(
                        present_new = stasis.client_android.lib.model.proto.EntityState.PresentNew()
                    )
                    is Existing -> stasis.client_android.lib.model.proto.EntityState(
                        present_existing = stasis.client_android.lib.model.proto.EntityState.PresentExisting(
                            entry = stasis.client_android.lib.model.proto.Uuid(
                                mostSignificantBits = entry.mostSignificantBits,
                                leastSignificantBits = entry.leastSignificantBits
                            )
                        )
                    )
                    is Updated -> stasis.client_android.lib.model.proto.EntityState(
                        present_updated = stasis.client_android.lib.model.proto.EntityState.PresentUpdated()
                    )
                }

            fun stasis.client_android.lib.model.proto.EntityState.toModel(): Try<EntityState> =
                when (present_new) {
                    is stasis.client_android.lib.model.proto.EntityState.PresentNew -> Success(New)
                    else -> when (present_existing) {
                        is stasis.client_android.lib.model.proto.EntityState.PresentExisting -> {
                            when (present_existing.entry) {
                                null -> Failure(IllegalArgumentException("No entry ID found for existing file"))
                                else -> Success(
                                    Existing(
                                        UUID(
                                            present_existing.entry.mostSignificantBits,
                                            present_existing.entry.leastSignificantBits
                                        )
                                    )
                                )
                            }
                        }
                        else -> when (present_updated) {
                            is stasis.client_android.lib.model.proto.EntityState.PresentUpdated -> Success(Updated)
                            else -> Failure(IllegalArgumentException("Unexpected empty file state encountered"))
                        }
                    }
                }
        }
    }

    companion object {
        fun empty(): FilesystemMetadata = FilesystemMetadata(entities = emptyMap())

        operator fun invoke(changes: Iterable<String>): FilesystemMetadata = FilesystemMetadata(
            entities = changes.associateWith { EntityState.New }
        )

        fun FilesystemMetadata.toProto(): stasis.client_android.lib.model.proto.FilesystemMetadata =
            stasis.client_android.lib.model.proto.FilesystemMetadata(
                entities = entities.map { entity ->
                    entity.key to entity.value.toProto()
                }.toMap()
            )

        fun stasis.client_android.lib.model.proto.FilesystemMetadata?.toModel(): Try<FilesystemMetadata> =
            when (this) {
                null -> Failure(IllegalArgumentException("No filesystem metadata provided"))
                else -> foldTryMap(
                    entities.map { entity ->
                        entity.key to entity.value.toModel()
                    }
                ).map { entities ->
                    FilesystemMetadata(entities)
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
