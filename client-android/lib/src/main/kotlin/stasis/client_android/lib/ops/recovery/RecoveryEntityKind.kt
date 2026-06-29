package stasis.client_android.lib.ops.recovery

import okio.Source
import stasis.client_android.lib.analysis.Metadata
import stasis.client_android.lib.collection.FilesystemRecoveryCollector
import stasis.client_android.lib.collection.RecoveryCollector
import stasis.client_android.lib.collection.RecoveryMetadataCollector
import stasis.client_android.lib.collection.rules.SourceUri
import stasis.client_android.lib.model.DatasetMetadata
import stasis.client_android.lib.model.EntityMetadata
import stasis.client_android.lib.model.EntityRef
import stasis.client_android.lib.model.FilesystemMetadata
import stasis.client_android.lib.model.TargetEntity
import stasis.client_android.lib.ops.recovery.stages.internal.DestagedByteStringSource.destage
import java.nio.file.Files
import java.nio.file.attribute.FileAttribute
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions

sealed interface RecoveryEntityKind {
    fun collector(
        targetMetadata: DatasetMetadata,
        keep: (String, FilesystemMetadata.EntityState) -> Boolean,
        destination: TargetEntity.Destination,
        providers: Providers
    ): RecoveryCollector

    interface Filesystem : RecoveryEntityKind {
        fun prepare(entity: TargetEntity, ref: EntityRef.Filesystem, providers: Providers)

        suspend fun write(entity: TargetEntity, ref: EntityRef.Filesystem, content: Source, providers: Providers)

        suspend fun applyMetadata(entity: TargetEntity, ref: EntityRef.Filesystem, providers: Providers)
    }

    interface Library : RecoveryEntityKind {
        val scheme: String

        fun prepare(entity: TargetEntity, ref: EntityRef.Library, providers: Providers)

        suspend fun write(entity: TargetEntity, ref: EntityRef.Library, content: Source, providers: Providers)

        suspend fun applyMetadata(entity: TargetEntity, ref: EntityRef.Library, providers: Providers)
    }

    companion object {
        fun prepare(kinds: List<RecoveryEntityKind>, entity: TargetEntity, providers: Providers) {
            when (val ref = entity.destinationRef) {
                is EntityRef.Filesystem ->
                    kinds.filterIsInstance<Filesystem>().firstOrNull()?.prepare(entity, ref, providers)

                is EntityRef.Library ->
                    kinds.filterIsInstance<Library>().firstOrNull { it.scheme == ref.scheme }
                        ?.prepare(entity, ref, providers)
            }
        }

        suspend fun write(
            kinds: List<RecoveryEntityKind>,
            entity: TargetEntity,
            content: Source,
            providers: Providers
        ) {
            when (val ref = entity.destinationRef) {
                is EntityRef.Filesystem ->
                    (kinds.filterIsInstance<Filesystem>().firstOrNull()
                        ?: throw IllegalArgumentException("No filesystem recovery kind was registered"))
                        .write(entity, ref, content, providers)

                is EntityRef.Library ->
                    (kinds.filterIsInstance<Library>().firstOrNull { it.scheme == ref.scheme }
                        ?: throw IllegalArgumentException("No recovery kind was registered for scheme [${ref.scheme}]"))
                        .write(entity, ref, content, providers)
            }
        }

        suspend fun applyMetadata(kinds: List<RecoveryEntityKind>, entity: TargetEntity, providers: Providers) {
            when (val ref = entity.destinationRef) {
                is EntityRef.Filesystem ->
                    (kinds.filterIsInstance<Filesystem>().firstOrNull()
                        ?: throw IllegalArgumentException("No filesystem recovery kind was registered"))
                        .applyMetadata(entity, ref, providers)

                is EntityRef.Library ->
                    (kinds.filterIsInstance<Library>().firstOrNull { it.scheme == ref.scheme }
                        ?: throw IllegalArgumentException("No recovery kind was registered for scheme [${ref.scheme}]"))
                        .applyMetadata(entity, ref, providers)
            }
        }

        val Filesystem: Filesystem = object : Filesystem {
            private val targetDirectoryAttributes: FileAttribute<Set<PosixFilePermission>> =
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"))

            override fun collector(
                targetMetadata: DatasetMetadata,
                keep: (String, FilesystemMetadata.EntityState) -> Boolean,
                destination: TargetEntity.Destination,
                providers: Providers
            ): RecoveryCollector =
                FilesystemRecoveryCollector(
                    targetMetadata = targetMetadata,
                    keep = { entity, state -> keep(entity, state) && SourceUri.scheme(entity) == null },
                    destination = destination,
                    metadataCollector = RecoveryMetadataCollector.Filesystem(checksum = providers.checksum),
                    clients = providers.clients
                )

            override fun prepare(entity: TargetEntity, ref: EntityRef.Filesystem, providers: Providers) {
                val directory = when (entity.existingMetadata) {
                    is EntityMetadata.Directory -> ref.path
                    is EntityMetadata.File -> ref.path.parent
                    is EntityMetadata.Library -> ref.path.parent
                }

                Files.createDirectories(directory, targetDirectoryAttributes)
            }

            override suspend fun write(
                entity: TargetEntity,
                ref: EntityRef.Filesystem,
                content: Source,
                providers: Providers
            ) {
                content.destage(to = ref.path, providers = providers)
            }

            override suspend fun applyMetadata(entity: TargetEntity, ref: EntityRef.Filesystem, providers: Providers) {
                Metadata.applyEntityMetadataTo(metadata = entity.existingMetadata, entity = ref.path)
            }
        }
    }
}
