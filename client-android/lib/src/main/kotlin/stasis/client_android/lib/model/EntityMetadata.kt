package stasis.client_android.lib.model

import okio.ByteString
import okio.ByteString.Companion.toByteString
import stasis.client_android.lib.utils.Try
import stasis.client_android.lib.utils.Try.Failure
import java.math.BigInteger
import java.time.Instant
import java.util.UUID

sealed class EntityMetadata {
    abstract val path: String
    abstract val created: Instant
    abstract val updated: Instant

    abstract fun asFilesystem(): Filesystem

    fun hasChanged(comparedTo: EntityMetadata): Boolean = when {
        this is WithContent && comparedTo is WithContent -> this != comparedTo.withCompression(this.compression)
        else -> this != comparedTo
    }

    interface Filesystem {
        val link: String?
        val isHidden: Boolean
        val owner: String
        val group: String
        val permissions: String
    }

    interface WithContent {
        val size: Long
        val checksum: BigInteger
        val crates: Map<String, UUID>
        val compression: String

        fun withCrates(crates: Map<String, UUID>): WithContent
        fun withCompression(compression: String): WithContent
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
        override val size: Long,
        override val checksum: BigInteger,
        override val crates: Map<String, UUID>,
        override val compression: String
    ) : EntityMetadata(), Filesystem, WithContent {
        override fun asFilesystem(): Filesystem = this
        override fun withCrates(crates: Map<String, UUID>): WithContent = copy(crates = crates)
        override fun withCompression(compression: String): WithContent = copy(compression = compression)
    }

    data class Directory(
        override val path: String,
        override val link: String?,
        override val isHidden: Boolean,
        override val created: Instant,
        override val updated: Instant,
        override val owner: String,
        override val group: String,
        override val permissions: String
    ) : EntityMetadata(), Filesystem {
        override fun asFilesystem(): Filesystem = this
    }

    data class Library(
        override val path: String,
        override val created: Instant,
        override val updated: Instant,
        override val size: Long,
        override val checksum: BigInteger,
        override val crates: Map<String, UUID>,
        override val compression: String,
        val attributes: ByteString
    ) : EntityMetadata(), WithContent {
        override fun withCrates(crates: Map<String, UUID>): WithContent = copy(crates = crates)
        override fun withCompression(compression: String): WithContent = copy(compression = compression)

        override fun asFilesystem(): Filesystem =
            throw IllegalArgumentException("Requested filesystem metadata but library metadata for [$path] found")
    }

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

                is Library -> {
                    val metadata = stasis.client_android.lib.model.proto.LibraryMetadata(
                        key = path,
                        size = size,
                        created = created.epochSecond,
                        updated = updated.epochSecond,
                        checksum = checksum.toByteArray().toByteString(),
                        crates = crates.map { it.toProto() }.toMap(),
                        compression = compression,
                        attributes = attributes
                    )

                    stasis.client_android.lib.model.proto.EntityMetadata(library = metadata)
                }
            }

        fun stasis.client_android.lib.model.proto.EntityMetadata.toModel(): Try<EntityMetadata> =
            when {
                file_ is stasis.client_android.lib.model.proto.FileMetadata -> Try {
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

                directory is stasis.client_android.lib.model.proto.DirectoryMetadata -> Try {
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

                library is stasis.client_android.lib.model.proto.LibraryMetadata -> Try {
                    Library(
                        path = library.key,
                        size = library.size,
                        created = Instant.ofEpochSecond(library.created),
                        updated = Instant.ofEpochSecond(library.updated),
                        checksum = BigInteger(library.checksum.toByteArray()),
                        crates = library.crates.map { it.toModel() }.toMap(),
                        compression = library.compression,
                        attributes = library.attributes
                    )
                }

                else -> Failure(IllegalArgumentException("Expected entity in metadata but none was found"))
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
