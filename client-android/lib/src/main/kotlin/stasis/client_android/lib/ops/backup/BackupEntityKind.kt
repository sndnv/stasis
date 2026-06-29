package stasis.client_android.lib.ops.backup

import okio.Source
import okio.source
import stasis.client_android.lib.collection.BackupCollector
import stasis.client_android.lib.collection.BackupMetadataCollector
import stasis.client_android.lib.collection.FilesystemBackupCollector
import stasis.client_android.lib.collection.rules.SourceUri
import stasis.client_android.lib.collection.rules.Specification
import stasis.client_android.lib.model.DatasetMetadata
import stasis.client_android.lib.model.EntityRef
import stasis.client_android.lib.model.SourceEntity
import stasis.client_android.lib.ops.OperationId
import stasis.client_android.lib.ops.backup.stages.EntityDiscovery
import java.nio.file.Files
import java.nio.file.Path

sealed interface BackupEntityKind {
    suspend fun collector(
        operation: OperationId,
        collector: EntityDiscovery.Collector,
        latestMetadata: DatasetMetadata?,
        providers: Providers
    ): BackupCollector

    interface Filesystem : BackupEntityKind {
        fun read(entity: SourceEntity, ref: EntityRef.Filesystem): Source
    }

    interface Library : BackupEntityKind {
        val scheme: String

        fun read(entity: SourceEntity, ref: EntityRef.Library): Source
    }

    companion object {
        fun read(kinds: List<BackupEntityKind>, entity: SourceEntity): Source =
            when (val ref = entity.ref) {
                is EntityRef.Filesystem ->
                    kinds.filterIsInstance<Filesystem>().firstOrNull()?.read(entity, ref)
                        ?: throw IllegalArgumentException("No filesystem backup kind was registered")

                is EntityRef.Library ->
                    kinds.filterIsInstance<Library>().firstOrNull { it.scheme == ref.scheme }?.read(entity, ref)
                        ?: throw IllegalArgumentException("No backup kind was registered for scheme [${ref.scheme}]")
            }

        val Filesystem: Filesystem = object : Filesystem {
            override suspend fun collector(
                operation: OperationId,
                collector: EntityDiscovery.Collector,
                latestMetadata: DatasetMetadata?,
                providers: Providers
            ): BackupCollector =
                FilesystemBackupCollector(
                    entities = resolveEntities(operation, collector, providers),
                    latestMetadata = latestMetadata,
                    metadataCollector = BackupMetadataCollector.Filesystem(
                        checksum = providers.checksum,
                        compression = providers.compression
                    ),
                    clients = providers.clients
                )

            override fun read(entity: SourceEntity, ref: EntityRef.Filesystem): Source =
                ref.path.source()
        }

        private fun resolveEntities(
            operation: OperationId,
            collector: EntityDiscovery.Collector,
            providers: Providers
        ): List<Path> =
            when (collector) {
                is EntityDiscovery.Collector.WithRules -> {
                    val spec = Specification.tracked(
                        operation = operation,
                        rules = collector.rules.filter { SourceUri.scheme(it.source) == null },
                        tracker = providers.track
                    )

                    spec.includedParents.forEach {
                        providers.track.entityDiscovered(operation, EntityRef.Filesystem(it))
                    }
                    providers.track.specificationProcessed(operation, unmatched = spec.unmatched)
                    spec.included
                }

                is EntityDiscovery.Collector.WithEntities -> {
                    val existing = collector.entities.filter { Files.exists(it) }
                    existing.forEach { providers.track.entityDiscovered(operation, EntityRef.Filesystem(it)) }
                    existing
                }

                is EntityDiscovery.Collector.WithState ->
                    collector.state.remainingEntities()
                        .filterIsInstance<EntityRef.Filesystem>()
                        .map { it.path }
            }
    }
}
