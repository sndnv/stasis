package stasis.test.client_android.lib.ops.integration.mocks

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import okio.Buffer
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import okio.Source
import stasis.client_android.lib.collection.BackupCollector
import stasis.client_android.lib.collection.rules.Rule
import stasis.client_android.lib.collection.rules.SourceUri
import stasis.client_android.lib.model.DatasetMetadata
import stasis.client_android.lib.model.EntityMetadata
import stasis.client_android.lib.model.EntityRef
import stasis.client_android.lib.model.SourceEntity
import stasis.client_android.lib.ops.OperationId
import stasis.client_android.lib.ops.backup.BackupEntityKind
import stasis.client_android.lib.ops.backup.Providers
import stasis.client_android.lib.ops.backup.stages.EntityDiscovery
import java.math.BigInteger
import java.time.Instant

class MockBackupLibraryKind(private val library: MockLibrary) : BackupEntityKind.Library {
    override val scheme: String = library.scheme

    override suspend fun collector(
        operation: OperationId,
        collector: EntityDiscovery.Collector,
        latestMetadata: DatasetMetadata?,
        providers: Providers
    ): BackupCollector {
        val rules = when (collector) {
            is EntityDiscovery.Collector.WithRules -> collector.rules
            else -> emptyList()
        }

        val libraryRules = rules.filter { SourceUri.scheme(it.source) == scheme }
        val includes = libraryRules.filter { it.operation == Rule.Operation.Include }.map { it.source }
        val excludes = libraryRules.filter { it.operation == Rule.Operation.Exclude }.map { it.source }

        val sourceEntities = library.entries
            .filterKeys { key -> includes.any { key.startsWith(it) } && excludes.none { key.startsWith(it) } }
            .map { (key, content) ->
                SourceEntity(
                    ref = EntityRef.default(key),
                    existingMetadata = null,
                    currentMetadata = libraryMetadata(key, content)
                )
            }

        return object : BackupCollector {
            override fun collect(): Flow<SourceEntity> = sourceEntities.asFlow()
        }
    }

    override fun read(entity: SourceEntity, ref: EntityRef.Library): Source =
        Buffer().write(library.entries.getValue(ref.key))

    private fun libraryMetadata(key: String, content: ByteString): EntityMetadata.Library =
        EntityMetadata.Library(
            path = key,
            created = Instant.now(),
            updated = Instant.now(),
            size = content.size.toLong(),
            checksum = BigInteger.valueOf(content.size.toLong()),
            crates = emptyMap(),
            compression = "deflate",
            attributes = "attributes:$key".encodeUtf8()
        )
}
