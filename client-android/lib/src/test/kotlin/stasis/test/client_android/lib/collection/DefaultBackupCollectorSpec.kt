package stasis.test.client_android.lib.collection

import io.kotest.assertions.fail
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.fold
import kotlinx.coroutines.flow.toList
import stasis.client_android.lib.analysis.Checksum
import stasis.client_android.lib.api.clients.Clients
import stasis.client_android.lib.collection.BackupMetadataCollector
import stasis.client_android.lib.collection.DefaultBackupCollector
import stasis.client_android.lib.model.DatasetMetadata
import stasis.client_android.lib.model.EntityMetadata
import stasis.client_android.lib.model.FilesystemMetadata
import stasis.client_android.lib.model.SourceEntity
import stasis.test.client_android.lib.Fixtures
import stasis.test.client_android.lib.ResourceHelpers.asTestResource
import stasis.test.client_android.lib.mocks.MockCompression
import stasis.test.client_android.lib.mocks.MockServerApiEndpointClient
import stasis.test.client_android.lib.mocks.MockServerCoreEndpointClient

class DefaultBackupCollectorSpec : WordSpec({
    "A DefaultBackupCollector" should {
        "collect backup files based on a files list" {
            val mockApiClient = MockServerApiEndpointClient()

            val file1 = "/collection/file-1".asTestResource()
            val file2 = "/collection/file-2".asTestResource()

            val collector = DefaultBackupCollector(
                entities = listOf(file1, file2),
                latestMetadata = DatasetMetadata.empty(),
                metadataCollector = BackupMetadataCollector.Default(
                    checksum = Checksum.Companion.MD5,
                    compression = MockCompression()
                ),
                clients = Clients(api = mockApiClient, core = MockServerCoreEndpointClient())
            )

            val sourceFiles = collector
                .collect()
                .fold(emptyList<SourceEntity>()) { acc, value -> acc + value }
                .sortedBy { it.path.toAbsolutePath().toString() }

            sourceFiles.size shouldBe (2)
            val sourceFile1 = sourceFiles[0]
            val sourceFile2 = sourceFiles[1]

            sourceFile1.path shouldBe (file1)
            sourceFile1.existingMetadata shouldBe (null)
            when (val metadata = sourceFile1.currentMetadata) {
                is EntityMetadata.File -> metadata.size shouldBe (1)
                is EntityMetadata.Directory -> fail("Expected file but received directory metadata")
            }

            sourceFile2.path shouldBe (file2)
            sourceFile2.existingMetadata shouldBe (null)
            when (val metadata = sourceFile2.currentMetadata) {
                is EntityMetadata.File -> metadata.size shouldBe (2)
                is EntityMetadata.Directory -> fail("Expected file but received directory metadata")
            }
        }

        "collect metadata for individual files" {
            val mockApiClient = MockServerApiEndpointClient()

            val file1 = "/collection/file-1".asTestResource()
            val file1Metadata = Fixtures.Metadata.FileOneMetadata.copy(path = file1.toString())

            val file2 = "/collection/file-2".asTestResource()

            val collectedFiles = DefaultBackupCollector.collectEntityMetadata(
                entities = listOf(file1, file2),
                latestMetadata = DatasetMetadata(
                    contentChanged = mapOf(file1.toString() to file1Metadata),
                    metadataChanged = emptyMap(),
                    filesystem = FilesystemMetadata(entities = mapOf(file1.toString() to FilesystemMetadata.EntityState.New))
                ),
                clients = Clients(api = mockApiClient, core = MockServerCoreEndpointClient())
            ).toList()

            collectedFiles.size shouldBe (2)

            val collectedFile1 = collectedFiles[0].first
            val collectedFile1Metadata = collectedFiles[0].second

            collectedFile1 shouldBe (file1)
            collectedFile1Metadata shouldBe (file1Metadata)

            val collectedFile2 = collectedFiles[1].first
            val collectedFile2Metadata = collectedFiles[1].second

            collectedFile2 shouldBe (file2)
            collectedFile2Metadata shouldBe (null)
        }

        "collect no file metadata if dataset metadata is not available" {
            val mockApiClient = MockServerApiEndpointClient()

            val file1 = "/collection/file-1".asTestResource()
            val file2 = "/collection/file-2".asTestResource()

            val collectedFiles = DefaultBackupCollector
                .collectEntityMetadata(
                    entities = listOf(file1, file2),
                    latestMetadata = null,
                    clients = Clients(api = mockApiClient, core = MockServerCoreEndpointClient())
                ).toList()

            collectedFiles.size shouldBe (2)

            val collectedFile1 = collectedFiles[0].first
            val collectedFile1Metadata = collectedFiles[0].second

            collectedFile1 shouldBe (file1)
            collectedFile1Metadata shouldBe (null)

            val collectedFile2 = collectedFiles[1].first
            val collectedFile2Metadata = collectedFiles[1].second

            collectedFile2 shouldBe (file2)
            collectedFile2Metadata shouldBe (null)
        }
    }
})
