package stasis.client_android.sources

import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flow
import okio.Buffer
import okio.ByteString.Companion.toByteString
import okio.Source
import okio.buffer
import stasis.client_android.lib.analysis.Checksum
import stasis.client_android.lib.collection.BackupCollector
import stasis.client_android.lib.collection.RecoveryCollector
import stasis.client_android.lib.collection.rules.Rule
import stasis.client_android.lib.collection.rules.SourceUri
import stasis.client_android.lib.collection.rules.exceptions.RuleParsingFailure
import stasis.client_android.lib.model.DatasetMetadata
import stasis.client_android.lib.model.EntityMetadata
import stasis.client_android.lib.model.EntityRef
import stasis.client_android.lib.model.FilesystemMetadata
import stasis.client_android.lib.model.SourceEntity
import stasis.client_android.lib.model.TargetEntity
import stasis.client_android.lib.ops.OperationId
import stasis.client_android.lib.ops.backup.BackupEntityKind
import stasis.client_android.lib.ops.backup.Providers as BackupProviders
import stasis.client_android.lib.ops.backup.stages.EntityDiscovery
import stasis.client_android.lib.ops.recovery.Providers as RecoveryProviders
import stasis.client_android.lib.ops.recovery.RecoveryEntityKind
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.PathMatcher
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

class LibraryEntityKind<R : Any>(
    private val source: LibrarySource<R>,
    private val gson: Gson
) : BackupEntityKind.Library, RecoveryEntityKind.Library {
    override val scheme: String = source.scheme

    private val pendingContent: ConcurrentHashMap<String, ByteArray> = ConcurrentHashMap()

    override suspend fun collector(
        operation: OperationId,
        collector: EntityDiscovery.Collector,
        latestMetadata: DatasetMetadata?,
        providers: BackupProviders
    ): BackupCollector {
        if (!source.hasReadPermission()) {
            providers.track.failureEncountered(operation, LibraryPermissionMissing(scheme))
            return object : BackupCollector {
                override fun collect(): Flow<SourceEntity> = emptyList<SourceEntity>().asFlow()
            }
        }

        reportInvalidPatterns(operation, collector, providers)

        val records = selectRecords(collector)
        val compression = providers.compression.defaultCompression().name()

        return object : BackupCollector {
            override fun collect(): Flow<SourceEntity> = flow {
                pendingContent.clear()

                records.forEach { record ->
                    val ref = EntityRef.Library(scheme = scheme, path = "/${source.id(record)}")
                    val content = serialize(record)
                    val existing = latestMetadata?.collect(ref.key, providers.clients) as? EntityMetadata.Library

                    val entity =
                        sourceEntity(ref, content, source.attributes(record), existing, providers.checksum, compression)

                    if (entity.hasContentChanged) pendingContent[ref.key] = content

                    providers.track.entityDiscovered(operation, ref)

                    emit(entity)
                }
            }
        }
    }

    override fun read(entity: SourceEntity, ref: EntityRef.Library): Source {
        val content = pendingContent.remove(ref.key)
            ?: throw IllegalStateException("Library entity [${ref.key}] is no longer available")

        return Buffer().write(content)
    }

    override fun collector(
        targetMetadata: DatasetMetadata,
        keep: (String, FilesystemMetadata.EntityState) -> Boolean,
        destination: TargetEntity.Destination,
        providers: RecoveryProviders
    ): RecoveryCollector =
        object : RecoveryCollector {
            override fun collect(filesystem: FileSystem): Flow<TargetEntity> = flow {
                val entities = targetMetadata.filesystem.collect { entity, state ->
                    if (SourceUri.scheme(entity) == scheme && keep(entity, state)) entity else null
                }

                entities.forEach { entity ->
                    emit(
                        TargetEntity(
                            ref = EntityRef.default(entity),
                            destination = destination,
                            existingMetadata = targetMetadata.require(entity = entity, clients = providers.clients),
                            currentMetadata = null
                        )
                    )
                }
            }
        }

    override fun prepare(entity: TargetEntity, ref: EntityRef.Library, providers: RecoveryProviders) = Unit

    override suspend fun write(
        entity: TargetEntity,
        ref: EntityRef.Library,
        content: Source,
        providers: RecoveryProviders
    ) {
        if (!source.hasWritePermission()) {
            throw LibraryPermissionMissing(scheme)
        }

        val bytes = content.buffer().use { it.readByteArray() }
        val record = gson.fromJson(bytes.decodeToString(), source.recordClass)
            ?: throw IllegalArgumentException("Malformed content for library entity [${ref.key}]")

        source.restore(record)
    }

    override suspend fun applyMetadata(entity: TargetEntity, ref: EntityRef.Library, providers: RecoveryProviders) = Unit

    fun preview(bytes: ByteArray): EntityPreview {
        val record = gson.fromJson(bytes.decodeToString(), source.recordClass)
            ?: throw IllegalArgumentException("Malformed content for library entity [$scheme]")

        return source.describe(record)
    }

    fun export(bytes: ByteArray): ExportedContent {
        val record = gson.fromJson(bytes.decodeToString(), source.recordClass)
            ?: throw IllegalArgumentException("Malformed content for library entity [$scheme]")

        return source.export(record)
    }

    private fun selectRecords(collector: EntityDiscovery.Collector): List<R> =
        when (collector) {
            is EntityDiscovery.Collector.WithRules -> {
                val schemeRules = collector.rules.filter { SourceUri.scheme(it.source) == scheme }
                val includes = compile(schemeRules.filter { it.operation == Rule.Operation.Include }.map { it.pattern })
                val excludes = compile(schemeRules.filter { it.operation == Rule.Operation.Exclude }.map { it.pattern })

                source.list().filter { record ->
                    val name = source.displayName(record)
                    includes.any { it.matches(name) } && excludes.none { it.matches(name) }
                }
            }

            is EntityDiscovery.Collector.WithState -> {
                val remaining = collector.state.remainingEntities()
                    .filterIsInstance<EntityRef.Library>()
                    .filter { it.scheme == scheme }
                    .map { it.path.trimStart('/') }
                    .toSet()

                source.list().filter { source.id(it) in remaining }
            }

            is EntityDiscovery.Collector.WithEntities -> emptyList()
        }

    private fun sourceEntity(
        ref: EntityRef.Library,
        content: ByteArray,
        attributes: Map<String, String>,
        existing: EntityMetadata.Library?,
        checksum: Checksum,
        compression: String
    ): SourceEntity {
        val currentChecksum = checksum.calculate(content)
        val size = content.size.toLong()
        val reusable = existing?.takeIf { it.checksum == currentChecksum }
        val now = Instant.now()

        return SourceEntity(
            ref = ref,
            existingMetadata = existing,
            currentMetadata = EntityMetadata.Library(
                path = ref.key,
                created = existing?.created ?: now,
                updated = reusable?.updated ?: now,
                size = size,
                checksum = currentChecksum,
                crates = reusable?.crates ?: emptyMap(),
                compression = compression,
                attributes = gson.toJson(attributes).encodeToByteArray().toByteString()
            )
        )
    }

    private fun reportInvalidPatterns(
        operation: OperationId,
        collector: EntityDiscovery.Collector,
        providers: BackupProviders
    ) {
        if (collector !is EntityDiscovery.Collector.WithRules) return

        collector.rules
            .filter { SourceUri.scheme(it.source) == scheme }
            .map { it.pattern }
            .filter { compile(listOf(it)).isEmpty() }
            .forEach { pattern ->
                providers.track.failureEncountered(
                    operation,
                    RuleParsingFailure("Invalid pattern [$pattern] for source [$scheme:/]")
                )
            }
    }

    private fun serialize(record: R): ByteArray = gson.toJson(record).encodeToByteArray()

    private fun compile(patterns: List<String>): List<NameMatcher> =
        patterns.mapNotNull { pattern ->
            try {
                NameMatcher(DefaultFilesystem.getPathMatcher("glob:$pattern"))
            } catch (e: IllegalArgumentException) {
                null
            }
        }

    private class NameMatcher(private val matcher: PathMatcher) {
        fun matches(name: String): Boolean =
            try {
                matcher.matches(DefaultFilesystem.getPath(name))
            } catch (e: IllegalArgumentException) {
                false
            }
    }

    companion object {
        private val DefaultFilesystem: FileSystem = FileSystems.getDefault()
    }
}
