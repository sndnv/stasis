package stasis.client_android.lib.model

import okio.ByteString.Companion.toByteString
import stasis.client_android.lib.utils.Try
import stasis.client_android.lib.utils.Try.Failure
import java.math.BigInteger
import java.time.Instant
import java.util.UUID

sealed class EntityMetadata {
    abstract val path: String
    abstract val link: String?
    abstract val isHidden: Boolean
    abstract val created: Instant
    abstract val updated: Instant
    abstract val owner: String
    abstract val group: String
    abstract val permissions: String

    fun hasChanged(comparedTo: EntityMetadata): Boolean = when {
        this is File && comparedTo is File -> this != comparedTo.copy(compression = this.compression)
        else -> this != comparedTo
    }

    data class File(
        override val path: String,
        override val link: String?,
        override val isHidden: Boolean,
        override val created: Instant,
        override val updated: Instant,
        override val owner: String,
        override val group: String,
        override val permissions: String,
        val size: Long,
        val checksum: BigInteger,
        val crates: Map<String, UUID>,
        val compression: String
    ) : EntityMetadata()

    data class Directory(
        override val path: String,
        override val link: String?,
        override val isHidden: Boolean,
        override val created: Instant,
        override val updated: Instant,
        override val owner: String,
        override val group: String,
        override val permissions: String
    ) : EntityMetadata()

    companion object {
        fun EntityMetadata.toProto(): stasis.client_android.lib.model.proto.EntityMetadata =
            when (this) {
                is File -> {
                    val metadata = stasis.client_android.lib.model.proto.FileMetadata(
                        path = path,
                        size = size,
                        link = link ?: "",
                        isHidden = isHidden,
                        created = created.epochSecond,
                        updated = updated.epochSecond,
                        owner = owner,
                        group = group,
                        permissions = permissions,
                        checksum = checksum.toByteArray().toByteString(),
                        crates = crates.map { it.toProto() }.toMap(),
                        compression = compression
                    )

                    stasis.client_android.lib.model.proto.EntityMetadata(file_ = metadata)
                }

                is Directory -> {
                    val metadata = stasis.client_android.lib.model.proto.DirectoryMetadata(
                        path = path,
                        link = link ?: "",
                        isHidden = isHidden,
                        created = created.epochSecond,
                        updated = updated.epochSecond,
                        owner = owner,
                        group = group,
                        permissions = permissions
                    )

                    stasis.client_android.lib.model.proto.EntityMetadata(directory = metadata)
                }
            }

        fun stasis.client_android.lib.model.proto.EntityMetadata.toModel(): Try<EntityMetadata> =
            when (file_) {
                is stasis.client_android.lib.model.proto.FileMetadata -> Try {
                    File(
                        path = file_.path,
                        size = file_.size,
                        link = file_.link.ifEmpty { null },
                        isHidden = file_.isHidden,
                        created = Instant.ofEpochSecond(file_.created),
                        updated = Instant.ofEpochSecond(file_.updated),
                        owner = file_.owner,
                        group = file_.group,
                        permissions = file_.permissions,
                        checksum = BigInteger(file_.checksum.toByteArray()),
                        crates = file_.crates.map { it.toModel() }.toMap(),
                        compression = file_.compression
                    )
                }

                else -> when (directory) {
                    is stasis.client_android.lib.model.proto.DirectoryMetadata -> Try {
                        Directory(
                            path = directory.path,
                            link = directory.link.ifEmpty { null },
                            isHidden = directory.isHidden,
                            created = Instant.ofEpochSecond(directory.created),
                            updated = Instant.ofEpochSecond(directory.updated),
                            owner = directory.owner,
                            group = directory.group,
                            permissions = directory.permissions
                        )
                    }

                    else -> Failure(IllegalArgumentException("Expected entity in metadata but none was found"))
                }
            }

        private fun Map.Entry<String, UUID>.toProto(): Pair<String, stasis.client_android.lib.model.proto.Uuid> =
            key to stasis.client_android.lib.model.proto.Uuid(
                mostSignificantBits = value.mostSignificantBits,
                leastSignificantBits = value.leastSignificantBits
            )

        private fun Map.Entry<String, stasis.client_android.lib.model.proto.Uuid>.toModel(): Pair<String, UUID> =
            key to UUID(value.mostSignificantBits, value.leastSignificantBits)
    }
}
