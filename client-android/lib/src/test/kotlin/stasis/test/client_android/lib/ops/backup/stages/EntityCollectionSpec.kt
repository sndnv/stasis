package stasis.test.client_android.lib.ops.backup.stages

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList
import stasis.client_android.lib.analysis.Checksum
import stasis.client_android.lib.api.clients.Clients
import stasis.client_android.lib.model.SourceEntity
import stasis.client_android.lib.model.server.datasets.DatasetDefinition
import stasis.client_android.lib.ops.Operation
import stasis.client_android.lib.ops.backup.Providers
import stasis.client_android.lib.ops.backup.stages.EntityCollection
import stasis.test.client_android.lib.Fixtures
import stasis.test.client_android.lib.mocks.MockBackupCollector
import stasis.test.client_android.lib.mocks.MockBackupTracker
import stasis.test.client_android.lib.mocks.MockCompression
import stasis.test.client_android.lib.mocks.MockEncryption
import stasis.test.client_android.lib.mocks.MockFileStaging
import stasis.test.client_android.lib.mocks.MockServerApiEndpointClient
import stasis.test.client_android.lib.mocks.MockServerCoreEndpointClient

class EntityCollectionSpec : WordSpec({
    "A Backup EntityCollection stage" should {
        "collect and filter files" {
            val sourceFile1 = SourceEntity(
                path = Fixtures.Metadata.FileOneMetadata.path,
                existingMetadata = null,
                currentMetadata = Fixtures.Metadata.FileOneMetadata
            )

            val sourceFile2 = SourceEntity(
                path = Fixtures.Metadata.FileTwoMetadata.path,
                existingMetadata = Fixtures.Metadata.FileTwoMetadata,
                currentMetadata = Fixtures.Metadata.FileTwoMetadata
            )

            val sourceFile3 = SourceEntity(
                path = Fixtures.Metadata.FileThreeMetadata.path,
                existingMetadata = Fixtures.Metadata.FileThreeMetadata.copy(isHidden = true),
                currentMetadata = Fixtures.Metadata.FileThreeMetadata
            )

            val mockTracker = MockBackupTracker()

            val stage = object : EntityCollection {
                override val targetDataset: DatasetDefinition =
                    Fixtures.Datasets.Default

                override val providers: Providers = Providers(
                    checksum = Checksum.Companion.MD5,
                    staging = MockFileStaging(),
                    compression = MockCompression(),
                    encryptor = MockEncryption(),
                    decryptor = MockEncryption(),
                    clients = Clients(
                        api = MockServerApiEndpointClient(),
                        core = MockServerCoreEndpointClient()
                    ),
                    track = mockTracker
                )
            }

            val collector = MockBackupCollector(files = listOf(sourceFile1, sourceFile2, sourceFile3))
            val collectedFiles = stage.entityCollection(Operation.generateId(), listOf(collector).asFlow()).toList()

            collectedFiles shouldBe (listOf(sourceFile1, sourceFile3))

            mockTracker.statistics[MockBackupTracker.Statistic.Started] shouldBe (0)
            mockTracker.statistics[MockBackupTracker.Statistic.EntityDiscovered] shouldBe (0)
            mockTracker.statistics[MockBackupTracker.Statistic.SpecificationProcessed] shouldBe (0)
            mockTracker.statistics[MockBackupTracker.Statistic.EntityExamined] shouldBe (3)
            mockTracker.statistics[MockBackupTracker.Statistic.EntitySkipped] shouldBe (1)
            mockTracker.statistics[MockBackupTracker.Statistic.EntityCollected] shouldBe (2)
            mockTracker.statistics[MockBackupTracker.Statistic.EntityProcessingStarted] shouldBe (0)
            mockTracker.statistics[MockBackupTracker.Statistic.EntityPartProcessed] shouldBe (0)
            mockTracker.statistics[MockBackupTracker.Statistic.EntityProcessed] shouldBe (0)
            mockTracker.statistics[MockBackupTracker.Statistic.MetadataCollected] shouldBe (0)
            mockTracker.statistics[MockBackupTracker.Statistic.MetadataPushed] shouldBe (0)
            mockTracker.statistics[MockBackupTracker.Statistic.FailureEncountered] shouldBe (0)
            mockTracker.statistics[MockBackupTracker.Statistic.Completed] shouldBe (0)
        }
    }
})
