package stasis.client_android.lib.model

import io.github.sndnv.fsi.Index
import io.github.sndnv.fsi.backends.TrieIndex
import stasis.client_android.lib.model.FilesystemMetadata.EntityState.Companion.toModel
import stasis.client_android.lib.model.FilesystemMetadata.EntityState.Companion.toProto
import stasis.client_android.lib.model.proto.Uuid
import stasis.client_android.lib.model.server.datasets.DatasetEntryId
import stasis.client_android.lib.utils.Try
import stasis.client_android.lib.utils.Try.Companion.flatMap
import stasis.client_android.lib.utils.Try.Companion.map
import stasis.client_android.lib.utils.Try.Failure
import stasis.client_android.lib.utils.Try.Success
import java.nio.file.FileSystems
import java.util.UUID
import java.util.regex.Pattern

sealed class FilesystemMetadata {
    abstract val underlying: Index<EntityState>

    fun get(entity: String): EntityState? =
        underlying.get(entity)

    fun <T> collect(f: (String, EntityState) -> T?): List<T> =
        underlying.collect { entity, state -> f(entity, state) }

    fun search(regex: Pattern): Map<String, EntityState> =
        underlying.search(regex)

    fun updated(changes: Iterable<String>, latestEntry: DatasetEntryId): FilesystemMetadata = apply {
        underlying.replaceAll { _, state ->
            when (state) {
                is EntityState.New -> EntityState.Existing(entry = latestEntry)
                is EntityState.Updated -> EntityState.Existing(entry = latestEntry)
                is EntityState.Existing -> state
            }
        }.putAll(paths = changes) { _, existing ->
            if (existing != null) EntityState.Updated
            else EntityState.New
        }
    }

    data class AsTrie(override val underlying: TrieIndex<EntityState>) : FilesystemMetadata()

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
                            entry = Uuid(
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
        fun empty(): FilesystemMetadata = AsTrie(
            underlying = TrieIndex.mutable(separator = DefaultFilesystemSeparator)
        )

        operator fun invoke(entities: Map<String, EntityState>): FilesystemMetadata = AsTrie(
            underlying = TrieIndex.mutable<EntityState>(separator = DefaultFilesystemSeparator)
                .putAll(entities)
        )

        operator fun invoke(changes: Iterable<String>): FilesystemMetadata = AsTrie(
            underlying = TrieIndex.mutable<EntityState>(separator = DefaultFilesystemSeparator)
                .putAll(paths = changes) { _, _ -> EntityState.New }
        )

        fun FilesystemMetadata.toProto(): stasis.client_android.lib.model.proto.FilesystemMetadata {
            val result = mutableMapOf<String, stasis.client_android.lib.model.proto.EntityState>()

            this.underlying.forEach { path, state ->
                result[path] = state.toProto()
            }

            return stasis.client_android.lib.model.proto.FilesystemMetadata(
                entities = result
            )
        }

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

        val DefaultFilesystemSeparator: String = FileSystems.getDefault().separator
    }
}
