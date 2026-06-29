package stasis.test.client_android.lib.ops.backup

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import okio.Buffer
import okio.Source
import okio.buffer
import stasis.client_android.lib.collection.BackupCollector
import stasis.client_android.lib.model.DatasetMetadata
import stasis.client_android.lib.model.EntityRef
import stasis.client_android.lib.model.SourceEntity
import stasis.client_android.lib.ops.OperationId
import stasis.client_android.lib.ops.backup.BackupEntityKind
import stasis.client_android.lib.ops.backup.Providers
import stasis.client_android.lib.ops.backup.stages.EntityDiscovery
import stasis.test.client_android.lib.Fixtures
import stasis.test.client_android.lib.ResourceHelpers.FileSystemSetup
import stasis.test.client_android.lib.ResourceHelpers.createMockFileSystem
import java.nio.file.Files

class BackupEntityKindSpec : WordSpec({
    val libraryKind = object : BackupEntityKind.Library {
        override val scheme: String = "photos"

        override suspend fun collector(
            operation: OperationId,
            collector: EntityDiscovery.Collector,
            latestMetadata: DatasetMetadata?,
            providers: Providers
        ): BackupCollector = throw NotImplementedError()

        override fun read(entity: SourceEntity, ref: EntityRef.Library): Source =
            Buffer().writeUtf8("library")
    }

    "A filesystem BackupEntityKind" should {
        "read entity content" {
            val (filesystem, _) = createMockFileSystem(setup = FileSystemSetup.Unix)

            val file = filesystem.getPath("/file")
            Files.write(file, "test".toByteArray())

            val entity = SourceEntity(
                ref = EntityRef.Filesystem(file),
                existingMetadata = null,
                currentMetadata = Fixtures.Metadata.FileOneMetadata
            )

            val filesystemKind = BackupEntityKind.Filesystem
            filesystemKind.read(entity = entity, ref = EntityRef.Filesystem(file))
                .buffer().readUtf8() shouldBe ("test")
        }
    }

    "A BackupEntityKind dispatcher" should {
        "route reads to the filesystem kind" {
            val (filesystem, _) = createMockFileSystem(setup = FileSystemSetup.Unix)

            val file = filesystem.getPath("/file")
            Files.write(file, "test".toByteArray())

            val entity = SourceEntity(
                ref = EntityRef.Filesystem(file),
                existingMetadata = null,
                currentMetadata = Fixtures.Metadata.FileOneMetadata
            )

            BackupEntityKind.read(kinds = listOf(BackupEntityKind.Filesystem), entity = entity)
                .buffer().readUtf8() shouldBe ("test")
        }

        "route reads to a matching library kind" {
            val entity = SourceEntity(
                ref = EntityRef.Library(scheme = "photos", path = "/album/img.heic"),
                existingMetadata = null,
                currentMetadata = Fixtures.Metadata.FileOneMetadata
            )

            BackupEntityKind.read(kinds = listOf(BackupEntityKind.Filesystem, libraryKind), entity = entity)
                .buffer().readUtf8() shouldBe ("library")
        }

        "fail when no kind is registered for a filesystem ref" {
            val (filesystem, _) = createMockFileSystem(setup = FileSystemSetup.Unix)

            val entity = SourceEntity(
                ref = EntityRef.Filesystem(filesystem.getPath("/file")),
                existingMetadata = null,
                currentMetadata = Fixtures.Metadata.FileOneMetadata
            )

            val e = shouldThrow<IllegalArgumentException> {
                BackupEntityKind.read(kinds = emptyList(), entity = entity)
            }

            e.message shouldBe ("No filesystem backup kind was registered")
        }

        "fail when no kind is registered for a library scheme" {
            val entity = SourceEntity(
                ref = EntityRef.Library(scheme = "unknown", path = "/x"),
                existingMetadata = null,
                currentMetadata = Fixtures.Metadata.FileOneMetadata
            )

            val e = shouldThrow<IllegalArgumentException> {
                BackupEntityKind.read(kinds = listOf(BackupEntityKind.Filesystem), entity = entity)
            }

            e.message shouldBe ("No backup kind was registered for scheme [unknown]")
        }
    }
})
