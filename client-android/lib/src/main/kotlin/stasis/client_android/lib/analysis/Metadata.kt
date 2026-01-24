package stasis.client_android.lib.analysis

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import stasis.client_android.lib.compression.Compression
import stasis.client_android.lib.model.EntityMetadata
import stasis.client_android.lib.model.SourceEntity
import stasis.client_android.lib.model.TargetEntity
import stasis.client_android.lib.model.core.CrateId
import java.math.BigInteger
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFileAttributes
import java.nio.file.attribute.PosixFilePermissions
import java.time.Instant
import java.time.temporal.ChronoUnit

object Metadata {
    suspend fun collectSource(
        checksum: Checksum,
        compression: Compression,
        entity: Path,
        existingMetadata: EntityMetadata?
    ): SourceEntity {
        val baseMetadata = extractBaseEntityMetadata(entity)

        val entityMetadata = collectEntityMetadata(
            currentMetadata = baseMetadata,
            checksum = checksum,
            collectCrates = { currentChecksum -> collectCratesForSourceFile(existingMetadata, currentChecksum) },
            collectCompression = { compression.algorithmFor(entity) }
        )

        return SourceEntity(
            path = entity,
            existingMetadata = existingMetadata,
            currentMetadata = entityMetadata
        )
    }

    suspend fun collectTarget(
        checksum: Checksum,
        entity: Path,
        destination: TargetEntity.Destination,
        existingMetadata: EntityMetadata
    ): TargetEntity {
        val targetEntity = TargetEntity(
            path = entity,
            destination = destination,
            existingMetadata = existingMetadata,
            currentMetadata = null
        )

        val destinationPath = targetEntity.destinationPath

        return if (Files.exists(destinationPath)) {
            val baseMetadata = extractBaseEntityMetadata(destinationPath)

            val entityMetadata = collectEntityMetadata(
                currentMetadata = baseMetadata,
                checksum = checksum,
                collectCrates = { collectCratesForTargetFile(existingMetadata) },
                collectCompression = { collectCompressionForTargetFile(existingMetadata) }
            )

            targetEntity.copy(currentMetadata = entityMetadata)
        } else {
            targetEntity
        }
    }

    suspend fun collectEntityMetadata(
        currentMetadata: BaseEntityMetadata,
        checksum: Checksum,
        collectCrates: (BigInteger) -> Map<String, CrateId>,
        collectCompression: () -> String
    ): EntityMetadata = if (currentMetadata.isDirectory) {
        EntityMetadata.Directory(
            path = currentMetadata.path.toAbsolutePath().toString(),
            link = currentMetadata.link?.toAbsolutePath()?.toString(),
            isHidden = currentMetadata.isHidden,
            created = currentMetadata.created,
            updated = currentMetadata.updated,
            owner = currentMetadata.owner,
            group = currentMetadata.group,
            permissions = currentMetadata.permissions
        )
    } else {
        val currentChecksum = checksum.calculate(currentMetadata.path)
        val crates = collectCrates(currentChecksum)

        EntityMetadata.File(
            path = currentMetadata.path.toAbsolutePath().toString(),
            size = currentMetadata.attributes.size(),
            link = currentMetadata.link?.toAbsolutePath()?.toString(),
            isHidden = currentMetadata.isHidden,
            created = currentMetadata.created,
            updated = currentMetadata.updated,
            owner = currentMetadata.owner,
            group = currentMetadata.group,
            permissions = currentMetadata.permissions,
            checksum = currentChecksum,
            crates = crates,
            compression = collectCompression()
        )
    }

    fun collectCratesForSourceFile(
        existingMetadata: EntityMetadata?,
        currentChecksum: BigInteger
    ): Map<String, CrateId> = when (existingMetadata) {
        is EntityMetadata.File -> if (existingMetadata.checksum == currentChecksum) {
            existingMetadata.crates
        } else {
            emptyMap()
        }
        is EntityMetadata.Directory -> throw IllegalArgumentException(
            "Expected metadata for file but directory metadata for [${existingMetadata.path}] provided"
        )
        else -> emptyMap()
    }

    fun collectCratesForTargetFile(
        existingMetadata: EntityMetadata
    ): Map<String, CrateId> = when (existingMetadata) {
        is EntityMetadata.File -> existingMetadata.crates
        is EntityMetadata.Directory -> throw IllegalArgumentException(
            "Expected metadata for file but directory metadata for [${existingMetadata.path}] provided"
        )
    }

    fun collectCompressionForTargetFile(
        existingMetadata: EntityMetadata
    ): String = when (existingMetadata) {
        is EntityMetadata.File -> existingMetadata.compression

        is EntityMetadata.Directory -> throw IllegalArgumentException(
            "Expected metadata for file but directory metadata for [${existingMetadata.path}] provided"
        )
    }

    suspend fun extractBaseEntityMetadata(
        entity: Path
    ): BaseEntityMetadata = withContext(Dispatchers.IO) {
        val attributes = Files.readAttributes(entity, PosixFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)

        val isDirectory = attributes.isDirectory
        val link = if (Files.isSymbolicLink(entity)) Files.readSymbolicLink(entity) else null
        val isHidden = Files.isHidden(entity)
        val created = attributes.creationTime().toInstant().truncatedTo(ChronoUnit.SECONDS)
        val updated = attributes.lastModifiedTime().toInstant().truncatedTo(ChronoUnit.SECONDS)
        val owner = attributes.owner().name
        val group = attributes.group().name
        val permissions = PosixFilePermissions.toString(attributes.permissions())

        BaseEntityMetadata(
            path = entity,
            isDirectory = isDirectory,
            link = link,
            isHidden = isHidden,
            created = created,
            updated = updated,
            owner = owner,
            group = group,
            permissions = permissions,
            attributes = attributes
        )
    }

    suspend fun applyEntityMetadataTo(
        metadata: EntityMetadata,
        entity: Path
    ): Unit = withContext(Dispatchers.IO) {
        val attributes =
            Files.getFileAttributeView(entity, PosixFileAttributeView::class.java, LinkOption.NOFOLLOW_LINKS)

        attributes.setPermissions(PosixFilePermissions.fromString(metadata.permissions))

        val lookupService = entity.fileSystem.userPrincipalLookupService

        val owner = lookupService.lookupPrincipalByName(metadata.owner)
        val group = lookupService.lookupPrincipalByGroupName(metadata.group)

        attributes.owner = owner
        attributes.setGroup(group)

        attributes.setTimes(
            /* lastModifiedTime */ FileTime.from(metadata.updated),
            /* lastAccessTime */ FileTime.from(Instant.now()),
            /* createTime */ FileTime.from(metadata.created)
        )
    }

    data class BaseEntityMetadata(
        val path: Path,
        val isDirectory: Boolean,
        val link: Path?,
        val isHidden: Boolean,
        val created: Instant,
        val updated: Instant,
        val owner: String,
        val group: String,
        val permissions: String,
        val attributes: PosixFileAttributes
    )
}
