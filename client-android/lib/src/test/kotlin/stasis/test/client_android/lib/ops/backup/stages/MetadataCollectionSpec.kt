package stasis.test.client_android.lib.ops.backup.stages

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList
import stasis.client_android.lib.analysis.Checksum
import stasis.client_android.lib.api.clients.Clients
import stasis.client_android.lib.model.DatasetMetadata
import stasis.client_android.lib.model.EntityMetadata
import stasis.client_android.lib.model.FilesystemMetadata
import stasis.client_android.lib.model.server.datasets.DatasetEntry
import stasis.client_android.lib.ops.Operation
import stasis.client_android.lib.ops.backup.Providers
import stasis.client_android.lib.ops.backup.stages.MetadataCollection
import stasis.client_android.lib.utils.Either
import stasis.client_android.lib.utils.Either.Left
import stasis.client_android.lib.utils.Either.Right
import stasis.test.client_android.lib.Fixtures
import stasis.test.client_android.lib.mocks.MockBackupTracker
import stasis.test.client_android.lib.mocks.MockCompression
import stasis.test.client_android.lib.mocks.MockEncryption
import stasis.test.client_android.lib.mocks.MockFileStaging
import stasis.test.client_android.lib.mocks.MockServerApiEndpointClient
import stasis.test.client_android.lib.mocks.MockServerCoreEndpointClient

class MetadataCollectionSpec : WordSpec({
    "A Backup MetadataCollection stage" should {
        "collect dataset metadata (with previous metadata)" {
            val mockTracker = MockBackupTracker()

            val stage = object : MetadataCollection {
                override val latestEntry: DatasetEntry = Fixtures.Entries.Default

                override val latestMetadata: DatasetMetadata = DatasetMetadata(
                    contentChanged = mapOf(
                        Fixtures.Metadata.FileOneMetadata.path to Fixtures.Metadata.FileOneMetadata
                    ),
                    metadataChanged = emptyMap(),
                    filesystem = FilesystemMetadata(
                        changes = listOf(
                            Fixtures.Metadata.FileOneMetadata.path
                        )
                    )
                )

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

            val stageInput = listOf<Either<EntityMetadata, EntityMetadata>>(
                Right(Fixtures.Metadata.FileOneMetadata), // metadata changed
                Left(Fixtures.Metadata.FileTwoMetadata), // content changed
                Right(Fixtures.Metadata.FileThreeMetadata) // metadata changed
            )

            val stageOutput = stage.metadataCollection(operation = Operation.generateId(), flow = stageInput.asFlow()).toList()

            stageOutput.size shouldBe (1)

            stageOutput[0] shouldBe (
                    DatasetMetadata(
                        contentChanged = mapOf(
                            Fixtures.Metadata.FileTwoMetadata.path to Fixtures.Metadata.FileTwoMetadata
                        ),
                        metadataChanged = mapOf(
                            Fixtures.Metadata.FileOneMetadata.path to Fixtures.Metadata.FileOneMetadata,
                            Fixtures.Metadata.FileThreeMetadata.path to Fixtures.Metadata.FileThreeMetadata
                        ),
                        filesystem = FilesystemMetadata(
                            entities = mapOf(
                                Fixtures.Metadata.FileOneMetadata.path to FilesystemMetadata.EntityState.Updated,
                                Fixtures.Metadata.FileTwoMetadata.path to FilesystemMetadata.EntityState.New,
                                Fixtures.Metadata.FileThreeMetadata.path to FilesystemMetadata.EntityState.New
                            )
                        )
                    )
                    )

            mockTracker.statistics[MockBackupTracker.Statistic.EntityDiscovered] shouldBe (0)
            mockTracker.statistics[MockBackupTracker.Statistic.SpecificationProcessed] shouldBe (0)
            mockTracker.statistics[MockBackupTracker.Statistic.EntityExamined] shouldBe (0)
            mockTracker.statistics[MockBackupTracker.Statistic.EntityCollected] shouldBe (0)
            mockTracker.statistics[MockBackupTracker.Statistic.EntityProcessed] shouldBe (0)
            mockTracker.statistics[MockBackupTracker.Statistic.MetadataCollected] shouldBe (1)
            mockTracker.statistics[MockBackupTracker.Statistic.MetadataPushed] shouldBe (0)
            mockTracker.statistics[MockBackupTracker.Statistic.FailureEncountered] shouldBe (0)
            mockTracker.statistics[MockBackupTracker.Statistic.Completed] shouldBe (0)
        }

        "collect dataset metadata (without previous metadata)" {
            val mockTracker = MockBackupTracker()

            val stage = object : MetadataCollection {
                override val latestEntry: DatasetEntry? = null

                override val latestMetadata: DatasetMetadata? = null

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

            val stageInput = listOf<Either<EntityMetadata, EntityMetadata>>(
                Right(Fixtures.Metadata.FileOneMetadata), // metadata changed
                Left(Fixtures.Metadata.FileTwoMetadata), // content changed
                Right(Fixtures.Metadata.FileThreeMetadata) // metadata changed
            )

            val stageOutput = stage.metadataCollection(operation = Operation.generateId(), flow = stageInput.asFlow()).toList()

            stageOutput.size shouldBe (1)

            stageOutput[0] shouldBe (
                    DatasetMetadata(
                        contentChanged = mapOf(
                            Fixtures.Metadata.FileTwoMetadata.path to Fixtures.Metadata.FileTwoMetadata
                        ),
                        metadataChanged = mapOf(
                            Fixtures.Metadata.FileOneMetadata.path to Fixtures.Metadata.FileOneMetadata,
                            Fixtures.Metadata.FileThreeMetadata.path to Fixtures.Metadata.FileThreeMetadata
                        ),
                        filesystem = FilesystemMetadata(
                            changes = listOf(
                                Fixtures.Metadata.FileOneMetadata.path,
                                Fixtures.Metadata.FileTwoMetadata.path,
                                Fixtures.Metadata.FileThreeMetadata.path
                            )
                        )
                    )
                    )

            mockTracker.statistics[MockBackupTracker.Statistic.EntityDiscovered] shouldBe (0)
            mockTracker.statistics[MockBackupTracker.Statistic.SpecificationProcessed] shouldBe (0)
            mockTracker.statistics[MockBackupTracker.Statistic.EntityExamined] shouldBe (0)
            mockTracker.statistics[MockBackupTracker.Statistic.EntityCollected] shouldBe (0)
            mockTracker.statistics[MockBackupTracker.Statistic.EntityProcessed] shouldBe (0)
            mockTracker.statistics[MockBackupTracker.Statistic.MetadataCollected] shouldBe (1)
            mockTracker.statistics[MockBackupTracker.Statistic.MetadataPushed] shouldBe (0)
            mockTracker.statistics[MockBackupTracker.Statistic.FailureEncountered] shouldBe (0)
            mockTracker.statistics[MockBackupTracker.Statistic.Completed] shouldBe (0)
        }
    }
})
