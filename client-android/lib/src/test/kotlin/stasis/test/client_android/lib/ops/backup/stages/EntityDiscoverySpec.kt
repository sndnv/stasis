package stasis.test.client_android.lib.ops.backup.stages

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.toList
import stasis.client_android.lib.analysis.Checksum
import stasis.client_android.lib.api.clients.Clients
import stasis.client_android.lib.collection.rules.Rule
import stasis.client_android.lib.model.DatasetMetadata
import stasis.client_android.lib.model.FilesystemMetadata
import stasis.client_android.lib.ops.Operation
import stasis.client_android.lib.ops.backup.Providers
import stasis.client_android.lib.ops.backup.stages.EntityDiscovery
import stasis.test.client_android.lib.ResourceHelpers.asTestResource
import stasis.test.client_android.lib.ResourceHelpers.extractDirectoryMetadata
import stasis.test.client_android.lib.ResourceHelpers.extractFileMetadata
import stasis.test.client_android.lib.mocks.MockBackupTracker
import stasis.test.client_android.lib.mocks.MockCompression
import stasis.test.client_android.lib.mocks.MockEncryption
import stasis.test.client_android.lib.mocks.MockFileStaging
import stasis.test.client_android.lib.mocks.MockServerApiEndpointClient
import stasis.test.client_android.lib.mocks.MockServerCoreEndpointClient
import java.math.BigInteger
import java.nio.file.Paths
import java.util.UUID

class EntityDiscoverySpec : WordSpec({
    "A Backup EntityDiscovery stage" should {
        "discover files (based on rules)" {
            val checksum = Checksum.Companion.SHA256

            val sourceDirectory1Metadata = "/ops".asTestResource().extractDirectoryMetadata()

            val sourceFile1Metadata = "/ops/source-file-1".asTestResource().extractFileMetadata(
                withChecksum = BigInteger("1"),
                withCrate = UUID.randomUUID()
            )

            val sourceFile2Metadata = "/ops/source-file-2".asTestResource().extractFileMetadata(
                withChecksum = BigInteger("2"),
                withCrate = UUID.randomUUID()
            )

            val sourceFile3Metadata = "/ops/source-file-3".asTestResource().extractFileMetadata(
                withChecksum = BigInteger("3"),
                withCrate = UUID.randomUUID()
            )

            val sourceDirectory2Metadata = "/ops/nested".asTestResource().extractDirectoryMetadata()

            val mockTracker = MockBackupTracker()

            val stage = object : EntityDiscovery {
                override val collector: EntityDiscovery.Collector = EntityDiscovery.Collector.WithRules(
                    rules = listOf(
                        Rule(
                            id = 0,
                            operation = Rule.Operation.Include,
                            directory = sourceDirectory1Metadata.path.toAbsolutePath().toString(),
                            pattern = "source-file-*"
                        ),
                        Rule(
                            id = 1,
                            operation = Rule.Operation.Include,
                            directory = sourceDirectory2Metadata.path.toAbsolutePath().toString(),
                            pattern = "source-file-*"
                        )
                    )
                )

                override val latestMetadata: DatasetMetadata = DatasetMetadata(
                    contentChanged = mapOf(
                        sourceFile1Metadata.path to sourceFile1Metadata.copy(isHidden = true),
                        sourceFile2Metadata.path to sourceFile2Metadata,
                        sourceFile3Metadata.path to sourceFile3Metadata.copy(checksum = BigInteger("0"))
                    ),
                    metadataChanged = mapOf(
                        sourceDirectory1Metadata.path to sourceDirectory1Metadata
                    ),
                    filesystem = FilesystemMetadata(
                        entities = mapOf(
                            sourceDirectory1Metadata.path to FilesystemMetadata.EntityState.New,
                            sourceFile1Metadata.path to FilesystemMetadata.EntityState.New,
                            sourceFile2Metadata.path to FilesystemMetadata.EntityState.New,
                            sourceFile3Metadata.path to FilesystemMetadata.EntityState.New
                        )
                    )
                )

                override val providers: Providers = Providers(
                    checksum = checksum,
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

            val collector = stage.entityDiscovery(
                operation = Operation.generateId()
            ).toList().first()

            val entities = collector.collect().toList()

            entities.size shouldBe (7) // 2 directories + 5 files

            mockTracker.statistics[MockBackupTracker.Statistic.EntityDiscovered] shouldBe (7) // 2 directories + 5 files
            mockTracker.statistics[MockBackupTracker.Statistic.SpecificationProcessed] shouldBe (1)
            mockTracker.statistics[MockBackupTracker.Statistic.EntityExamined] shouldBe (0)
            mockTracker.statistics[MockBackupTracker.Statistic.EntityCollected] shouldBe (0)
            mockTracker.statistics[MockBackupTracker.Statistic.EntityProcessingStarted] shouldBe (0)
            mockTracker.statistics[MockBackupTracker.Statistic.EntityPartProcessed] shouldBe (0)
            mockTracker.statistics[MockBackupTracker.Statistic.EntityProcessed] shouldBe (0)
            mockTracker.statistics[MockBackupTracker.Statistic.MetadataCollected] shouldBe (0)
            mockTracker.statistics[MockBackupTracker.Statistic.MetadataPushed] shouldBe (0)
            mockTracker.statistics[MockBackupTracker.Statistic.FailureEncountered] shouldBe (0)
            mockTracker.statistics[MockBackupTracker.Statistic.Completed] shouldBe (0)
        }

        "discover files (based on entities)" {
            val sourceFile1 = "/ops/source-file-1".asTestResource()

            val sourceFile2 = "/ops/source-file-2".asTestResource()

            val mockTracker = MockBackupTracker()

            val stage = object : EntityDiscovery {
                override val collector: EntityDiscovery.Collector = EntityDiscovery.Collector.WithEntities(
                    entities = listOf(
                        sourceFile1,
                        sourceFile2,
                        Paths.get("/ops/invalid-file")
                    )
                )

                override val latestMetadata: DatasetMetadata = DatasetMetadata.empty()

                override val providers: Providers = Providers(
                    checksum = Checksum.Companion.SHA256,
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

            val collector = stage.entityDiscovery(
                operation = Operation.generateId()
            ).toList().first()

            val entities = collector.collect().toList()

            entities.size shouldBe (2) // 2 valid files

            mockTracker.statistics[MockBackupTracker.Statistic.EntityDiscovered] shouldBe (2) // 2 valid files
            mockTracker.statistics[MockBackupTracker.Statistic.SpecificationProcessed] shouldBe (0)
            mockTracker.statistics[MockBackupTracker.Statistic.EntityExamined] shouldBe (0)
            mockTracker.statistics[MockBackupTracker.Statistic.EntityCollected] shouldBe (0)
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
