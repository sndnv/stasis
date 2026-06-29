package stasis.test.client_android.sources

import com.google.gson.Gson
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okio.Buffer
import okio.ByteString.Companion.toByteString
import okio.buffer
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import stasis.client_android.lib.analysis.Checksum
import stasis.client_android.lib.collection.rules.Rule
import stasis.client_android.lib.compression.Compression
import stasis.client_android.lib.compression.Compressor
import stasis.client_android.lib.model.DatasetMetadata
import stasis.client_android.lib.model.EntityMetadata
import stasis.client_android.lib.model.EntityRef
import stasis.client_android.lib.model.FilesystemMetadata
import stasis.client_android.lib.model.TargetEntity
import stasis.client_android.lib.ops.backup.Providers as BackupProviders
import stasis.client_android.lib.ops.backup.stages.EntityDiscovery
import stasis.client_android.lib.ops.recovery.Providers as RecoveryProviders
import stasis.client_android.lib.tracking.BackupTracker
import stasis.client_android.sources.LibraryEntityKind
import stasis.client_android.sources.LibraryPermissionMissing
import java.math.BigInteger
import java.nio.file.Path
import java.time.Instant
import java.util.UUID

class LibraryEntityKindSpec {
    private val gson = Gson()

    private val recordA = FakeRecord(key = "6df54e0c-ced7-4c69-ad03-ff3ad9fea7e7", label = "test a", payload = "test")
    private val recordB = FakeRecord(key = "28822a4b-c017-4b38-b78f-1debf386fa99", label = "test b", payload = "test a")

    @Test
    fun collectAllMatchingRecords() = runBlocking {
        val kind = kindFor(listOf(recordA, recordB), read = true, write = true)
        val track = mockk<BackupTracker>(relaxed = true)
        val operation = UUID.randomUUID()

        val entities = kind.collector(
            operation = operation,
            collector = EntityDiscovery.Collector.WithRules(listOf(includeRule("*"))),
            latestMetadata = null,
            providers = backupProviders(track)
        ).collect().toList()

        assertThat(
            entities.map { it.ref.key }.toSet(),
            equalTo(setOf("fake:/${recordA.key}", "fake:/${recordB.key}"))
        )
        verify(exactly = 2) { track.entityDiscovered(operation, any()) }
    }

    @Test
    fun applyIncludeAndExcludeGlobs() = runBlocking {
        val kind = kindFor(listOf(recordA, recordB), read = true, write = true)

        val entities = kind.collector(
            operation = UUID.randomUUID(),
            collector = EntityDiscovery.Collector.WithRules(listOf(includeRule("*"), excludeRule("test b"))),
            latestMetadata = null,
            providers = backupProviders(mockk(relaxed = true))
        ).collect().toList()

        assertThat(entities.map { it.ref.key }, equalTo(listOf("fake:/${recordA.key}")))
    }

    @Test
    fun serializeAndReadBackTheSameContent() = runBlocking {
        val kind = kindFor(listOf(recordA), read = true, write = true)

        val entity = kind.collector(
            operation = UUID.randomUUID(),
            collector = EntityDiscovery.Collector.WithRules(listOf(includeRule("*"))),
            latestMetadata = null,
            providers = backupProviders(mockk(relaxed = true))
        ).collect().toList().single()

        val metadata = entity.currentMetadata as EntityMetadata.Library
        assertThat(metadata.path, equalTo("fake:/${recordA.key}"))
        assertThat(metadata.compression, equalTo("none"))

        val attributes = gson.fromJson(metadata.attributes.utf8(), Map::class.java)
        assertThat(attributes["name"], equalTo("test a"))

        val bytes = kind.read(entity, entity.ref as EntityRef.Library).buffer().use { it.readByteArray() }
        assertThat(metadata.size, equalTo(bytes.size.toLong()))
        assertThat(gson.fromJson(bytes.decodeToString(), FakeRecord::class.java), equalTo(recordA))
    }

    @Test
    fun reuseCratesForUnchangedRecords() {
        runBlocking {
            val crates = mapOf("fake:/${recordA.key}__part=0" to UUID.randomUUID())
            val kind = kindFor(listOf(recordA), read = true, write = true)

            val entity = kind.collector(
                operation = UUID.randomUUID(),
                collector = EntityDiscovery.Collector.WithRules(listOf(includeRule("*"))),
                latestMetadata = latestMetadataFor(recordA, crates),
                providers = backupProviders(mockk(relaxed = true))
            ).collect().toList().single()

            val metadata = entity.currentMetadata as EntityMetadata.Library
            assertThat(metadata.crates, equalTo(crates))
            assertThat(entity.hasContentChanged, equalTo(false))

            assertThrows(IllegalStateException::class.java) {
                kind.read(entity, entity.ref as EntityRef.Library)
            }
        }
    }

    @Test
    fun reuploadChangedRecords() = runBlocking {
        val changed = recordA.copy(payload = "changed payload")
        val crates = mapOf("fake:/${recordA.key}__part=0" to UUID.randomUUID())
        val kind = kindFor(listOf(changed), read = true, write = true)

        val entity = kind.collector(
            operation = UUID.randomUUID(),
            collector = EntityDiscovery.Collector.WithRules(listOf(includeRule("*"))),
            latestMetadata = latestMetadataFor(recordA, crates),
            providers = backupProviders(mockk(relaxed = true))
        ).collect().toList().single()

        val metadata = entity.currentMetadata as EntityMetadata.Library
        assertThat(metadata.crates.isEmpty(), equalTo(true))
        assertThat(entity.hasContentChanged, equalTo(true))

        val bytes = kind.read(entity, entity.ref as EntityRef.Library).buffer().use { it.readByteArray() }
        assertThat(gson.fromJson(bytes.decodeToString(), FakeRecord::class.java), equalTo(changed))
    }

    @Test
    fun reportInvalidPatterns() = runBlocking {
        val kind = kindFor(listOf(recordA), read = true, write = true)
        val track = mockk<BackupTracker>(relaxed = true)
        val operation = UUID.randomUUID()

        kind.collector(
            operation = operation,
            collector = EntityDiscovery.Collector.WithRules(listOf(includeRule("[invalid"))),
            latestMetadata = null,
            providers = backupProviders(track)
        ).collect().toList()

        verify(atLeast = 1) { track.failureEncountered(operation, any<Throwable>()) }
    }

    @Test
    fun skipBackupWhenReadPermissionMissing() = runBlocking {
        val kind = kindFor(listOf(recordA), read = false, write = true)
        val track = mockk<BackupTracker>(relaxed = true)
        val operation = UUID.randomUUID()

        val entities = kind.collector(
            operation = operation,
            collector = EntityDiscovery.Collector.WithRules(listOf(includeRule("*"))),
            latestMetadata = null,
            providers = backupProviders(track)
        ).collect().toList()

        assertThat(entities.isEmpty(), equalTo(true))
        verify(exactly = 1) { track.failureEncountered(operation, any<Throwable>()) }
    }

    @Test
    fun previewDecodedRecord() {
        val kind = kindFor(listOf(recordA), read = true, write = true)

        val preview = kind.preview(gson.toJson(recordA).encodeToByteArray())

        val fields = preview.sections.flatMap { it.fields }.associate { it.label to it.value }
        assertThat(fields["Label"], equalTo(recordA.label))
        assertThat(fields["Payload"], equalTo(recordA.payload))
    }

    @Test
    fun exportDecodedRecord() {
        val kind = kindFor(listOf(recordA), read = true, write = true)

        val exported = kind.export(gson.toJson(recordA).encodeToByteArray())

        assertThat(exported.extension, equalTo("txt"))
        assertThat(exported.bytes.decodeToString(), equalTo("${recordA.label}:${recordA.payload}"))
    }

    @Test
    fun restoreDecodedRecordOnWrite() = runBlocking {
        val source = FakeLibrarySource(records = emptyList(), readPermission = true, writePermission = true)
        val kind = LibraryEntityKind(source, gson)

        kind.write(
            entity = mockk<TargetEntity>(),
            ref = EntityRef.Library(scheme = "fake", path = "/${recordA.key}"),
            content = Buffer().write(gson.toJson(recordA).encodeToByteArray()),
            providers = mockk<RecoveryProviders>()
        )

        assertThat(source.restored, equalTo(listOf(recordA)))
    }

    @Test
    fun failWriteWhenWritePermissionMissing() {
        val kind = kindFor(emptyList(), read = true, write = false)

        assertThrows(LibraryPermissionMissing::class.java) {
            runBlocking {
                kind.write(
                    entity = mockk<TargetEntity>(),
                    ref = EntityRef.Library(scheme = "fake", path = "/${recordA.key}"),
                    content = Buffer().write(gson.toJson(recordA).encodeToByteArray()),
                    providers = mockk<RecoveryProviders>()
                )
            }
        }
    }

    private fun kindFor(records: List<FakeRecord>, read: Boolean, write: Boolean): LibraryEntityKind<FakeRecord> =
        LibraryEntityKind(FakeLibrarySource(records = records, readPermission = read, writePermission = write), gson)

    private fun latestMetadataFor(record: FakeRecord, crates: Map<String, UUID>): DatasetMetadata {
        val key = "fake:/${record.key}"
        val content = gson.toJson(record).encodeToByteArray()

        val metadata = EntityMetadata.Library(
            path = key,
            created = Instant.EPOCH.plusSeconds(1000),
            updated = Instant.EPOCH.plusSeconds(2000),
            size = content.size.toLong(),
            checksum = BigInteger.valueOf(content.size.toLong()),
            crates = crates,
            compression = "none",
            attributes = gson.toJson(mapOf("name" to record.label)).encodeToByteArray().toByteString()
        )

        return DatasetMetadata(
            contentChanged = mapOf(key to metadata),
            metadataChanged = emptyMap(),
            filesystem = FilesystemMetadata(changes = listOf(key))
        )
    }

    private fun includeRule(pattern: String): Rule =
        Rule(id = 0, operation = Rule.Operation.Include, source = "fake:/", pattern = pattern, definition = null)

    private fun excludeRule(pattern: String): Rule =
        Rule(id = 0, operation = Rule.Operation.Exclude, source = "fake:/", pattern = pattern, definition = null)

    private fun backupProviders(track: BackupTracker): BackupProviders =
        BackupProviders(
            checksum = TestChecksum,
            staging = mockk(),
            compression = compressionFor("none"),
            encryptor = mockk(),
            decryptor = mockk(),
            clients = mockk(),
            track = track,
            analytics = mockk(),
            kinds = emptyList()
        )

    private fun compressionFor(name: String): Compression {
        val compressor = mockk<Compressor>()
        every { compressor.name() } returns name

        val compression = mockk<Compression>()
        every { compression.defaultCompression() } returns compressor

        return compression
    }

    private object TestChecksum : Checksum {
        override suspend fun calculate(file: Path): BigInteger = throw NotImplementedError()
        override fun calculate(bytes: ByteArray): BigInteger = BigInteger.valueOf(bytes.size.toLong())
    }
}
