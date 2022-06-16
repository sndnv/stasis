package stasis.test.client_android.lib.ops.backup.stages

import io.kotest.assertions.fail
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList
import okio.Source
import stasis.client_android.lib.analysis.Checksum
import stasis.client_android.lib.api.clients.Clients
import stasis.client_android.lib.encryption.secrets.DeviceSecret
import stasis.client_android.lib.model.EntityMetadata
import stasis.client_android.lib.model.SourceEntity
import stasis.client_android.lib.model.core.Manifest
import stasis.client_android.lib.model.server.datasets.DatasetDefinition
import stasis.client_android.lib.ops.Operation
import stasis.client_android.lib.ops.backup.Providers
import stasis.client_android.lib.ops.backup.stages.EntityProcessing
import stasis.test.client_android.lib.Fixtures
import stasis.test.client_android.lib.ResourceHelpers.asTestResource
import stasis.test.client_android.lib.ResourceHelpers.extractFileMetadata
import stasis.test.client_android.lib.mocks.MockBackupTracker
import stasis.test.client_android.lib.mocks.MockCompression
import stasis.test.client_android.lib.mocks.MockEncryption
import stasis.test.client_android.lib.mocks.MockFileStaging
import stasis.test.client_android.lib.mocks.MockServerApiEndpointClient
import stasis.test.client_android.lib.mocks.MockServerCoreEndpointClient
import java.math.BigInteger
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

class EntityProcessingSpec : WordSpec({
    "A Backup EntityProcessing stage" should {
        "process files with changed content and metadata" {
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

            val sourceFile1 = SourceEntity(
                path = sourceFile1Metadata.path,
                existingMetadata = null,
                currentMetadata = sourceFile1Metadata
            )

            val sourceFile2 = SourceEntity(
                path = sourceFile2Metadata.path,
                existingMetadata = sourceFile2Metadata.copy(isHidden = true),
                currentMetadata = sourceFile2Metadata
            )

            val sourceFile3 = SourceEntity(
                path = sourceFile3Metadata.path,
                existingMetadata = sourceFile3Metadata.copy(checksum = BigInteger("9999")),
                currentMetadata = sourceFile3Metadata
            )

            val mockStaging = MockFileStaging()
            val mockCompression = MockCompression()
            val mockEncryption = MockEncryption()
            val mockCoreClient = MockServerCoreEndpointClient()
            val mockTracker = MockBackupTracker()

            val stage = object : EntityProcessing {
                override val targetDataset: DatasetDefinition = Fixtures.Datasets.Default

                override val deviceSecret: DeviceSecret = Fixtures.Secrets.Default

                override val providers: Providers = Providers(
                    checksum = Checksum.Companion.MD5,
                    staging = mockStaging,
                    compression = mockCompression,
                    encryptor = mockEncryption,
                    decryptor = mockEncryption,
                    clients = Clients(
                        api = MockServerApiEndpointClient(),
                        core = mockCoreClient
                    ),
                    track = mockTracker
                )

                override val maxPartSize: Long = 16384
            }

            val stageOutput = stage.entityProcessing(
                operation = Operation.generateId(),
                flow = listOf(sourceFile1, sourceFile2, sourceFile3).asFlow()
            ).toList()

            val actualSourceFile1Metadata = when (val metadata = stageOutput[0].left) {
                is EntityMetadata.File -> metadata
                else -> fail("Unexpected metadata provided")
            }

            val actualSourceFile2Metadata = when (val metadata = stageOutput[1].right) {
                is EntityMetadata.File -> metadata
                else -> fail("Unexpected metadata provided")
            }

            val actualSourceFile3Metadata = when (val metadata = stageOutput[2].left) {
                is EntityMetadata.File -> metadata
                else -> fail("Unexpected metadata provided")
            }

            actualSourceFile1Metadata shouldBe (sourceFile1Metadata.copy(crates = actualSourceFile1Metadata.crates))
            actualSourceFile2Metadata shouldBe (sourceFile2Metadata.copy(crates = actualSourceFile2Metadata.crates))
            actualSourceFile3Metadata shouldBe (sourceFile3Metadata.copy(crates = actualSourceFile3Metadata.crates))

            actualSourceFile1Metadata.crates.size shouldBe (1)
            actualSourceFile2Metadata.crates.size shouldBe (1)
            actualSourceFile3Metadata.crates.size shouldBe (1)

            mockStaging.statistics[MockFileStaging.Statistic.TemporaryCreated] shouldBe (2)
            mockStaging.statistics[MockFileStaging.Statistic.TemporaryDiscarded] shouldBe (2)
            mockStaging.statistics[MockFileStaging.Statistic.Destaged] shouldBe (0)

            mockCompression.statistics[MockCompression.Statistic.Compressed] shouldBe (2)
            mockCompression.statistics[MockCompression.Statistic.Decompressed] shouldBe (0)

            mockEncryption.statistics[MockEncryption.Statistic.FileEncrypted] shouldBe (2)
            mockEncryption.statistics[MockEncryption.Statistic.FileDecrypted] shouldBe (0)
            mockEncryption.statistics[MockEncryption.Statistic.MetadataEncrypted] shouldBe (0)
            mockEncryption.statistics[MockEncryption.Statistic.MetadataDecrypted] shouldBe (0)

            mockCoreClient.statistics[MockServerCoreEndpointClient.Statistic.CratePulled] shouldBe (0)
            mockCoreClient.statistics[MockServerCoreEndpointClient.Statistic.CratePushed] shouldBe (2)

            mockTracker.statistics[MockBackupTracker.Statistic.EntityDiscovered] shouldBe (0)
            mockTracker.statistics[MockBackupTracker.Statistic.SpecificationProcessed] shouldBe (0)
            mockTracker.statistics[MockBackupTracker.Statistic.EntityExamined] shouldBe (0)
            mockTracker.statistics[MockBackupTracker.Statistic.EntityCollected] shouldBe (0)
            mockTracker.statistics[MockBackupTracker.Statistic.EntityProcessed] shouldBe (3)
            mockTracker.statistics[MockBackupTracker.Statistic.MetadataCollected] shouldBe (0)
            mockTracker.statistics[MockBackupTracker.Statistic.MetadataPushed] shouldBe (0)
            mockTracker.statistics[MockBackupTracker.Statistic.FailureEncountered] shouldBe (0)
            mockTracker.statistics[MockBackupTracker.Statistic.Completed] shouldBe (0)
        }

        "process files with changed content size above maximum part size" {
            val largeSourceFileMetadata = "/ops/large-source-file-1".asTestResource().extractFileMetadata(
                withChecksum = BigInteger("1"),
                withCrate = UUID.randomUUID()
            )

            val largeSourceFile = SourceEntity(
                path = largeSourceFileMetadata.path,
                existingMetadata = null,
                currentMetadata = largeSourceFileMetadata
            )

            val mockStaging = MockFileStaging()
            val mockCompression = object : MockCompression() {
                override fun compress(source: Source): Source = source
            }
            val mockEncryption = MockEncryption()
            val mockCoreClient = MockServerCoreEndpointClient()
            val mockTracker = MockBackupTracker()

            val stage = object : EntityProcessing {
                override val targetDataset: DatasetDefinition = Fixtures.Datasets.Default

                override val deviceSecret: DeviceSecret = Fixtures.Secrets.Default

                override val providers: Providers = Providers(
                    checksum = Checksum.Companion.MD5,
                    staging = mockStaging,
                    compression = mockCompression,
                    encryptor = mockEncryption,
                    decryptor = mockEncryption,
                    clients = Clients(
                        api = MockServerApiEndpointClient(),
                        core = mockCoreClient
                    ),
                    track = mockTracker
                )

                override val maxPartSize: Long = 10
            }

            val expectedParts = 4 // 3 x 10 chars + 1 x 6 chars (36 total)

            val stageOutput = stage
                .entityProcessing(operation = Operation.generateId(), flow = listOf(largeSourceFile).asFlow())
                .toList()

            stageOutput.size shouldBe (1)

            val actualSourceFileMetadata = when (val metadata = stageOutput[0].left) {
                is EntityMetadata.File -> metadata
                else -> fail("Unexpected metadata provided")
            }

            actualSourceFileMetadata shouldBe (largeSourceFileMetadata.copy(crates = actualSourceFileMetadata.crates))
            actualSourceFileMetadata.crates.size shouldBe (expectedParts)

            mockStaging.statistics[MockFileStaging.Statistic.TemporaryCreated] shouldBe (expectedParts)
            mockStaging.statistics[MockFileStaging.Statistic.TemporaryDiscarded] shouldBe (expectedParts)
            mockStaging.statistics[MockFileStaging.Statistic.Destaged] shouldBe (0)

            mockCompression.statistics[MockCompression.Statistic.Compressed] shouldBe (0) // compression is skipped
            mockCompression.statistics[MockCompression.Statistic.Decompressed] shouldBe (0)

            mockEncryption.statistics[MockEncryption.Statistic.FileEncrypted] shouldBe (expectedParts)
            mockEncryption.statistics[MockEncryption.Statistic.FileDecrypted] shouldBe (0)
            mockEncryption.statistics[MockEncryption.Statistic.MetadataEncrypted] shouldBe (0)
            mockEncryption.statistics[MockEncryption.Statistic.MetadataDecrypted] shouldBe (0)

            mockCoreClient.statistics[MockServerCoreEndpointClient.Statistic.CratePulled] shouldBe (0)
            mockCoreClient.statistics[MockServerCoreEndpointClient.Statistic.CratePushed] shouldBe (expectedParts)

            mockTracker.statistics[MockBackupTracker.Statistic.EntityDiscovered] shouldBe (0)
            mockTracker.statistics[MockBackupTracker.Statistic.SpecificationProcessed] shouldBe (0)
            mockTracker.statistics[MockBackupTracker.Statistic.EntityExamined] shouldBe (0)
            mockTracker.statistics[MockBackupTracker.Statistic.EntityCollected] shouldBe (0)
            mockTracker.statistics[MockBackupTracker.Statistic.EntityProcessed] shouldBe (1)
            mockTracker.statistics[MockBackupTracker.Statistic.MetadataCollected] shouldBe (0)
            mockTracker.statistics[MockBackupTracker.Statistic.MetadataPushed] shouldBe (0)
            mockTracker.statistics[MockBackupTracker.Statistic.FailureEncountered] shouldBe (0)
            mockTracker.statistics[MockBackupTracker.Statistic.Completed] shouldBe (0)
        }

        "handle core push failures" {
            val sourceFile1Metadata = "/ops/source-file-1".asTestResource().extractFileMetadata(
                withChecksum = BigInteger("1"),
                withCrate = UUID.randomUUID()
            )

            val sourceFile1 = SourceEntity(
                path = sourceFile1Metadata.path,
                existingMetadata = null,
                currentMetadata = sourceFile1Metadata
            )

            val mockStaging = MockFileStaging()
            val mockCompression = MockCompression()
            val mockEncryption = MockEncryption()
            val mockCoreClient = MockServerCoreEndpointClient(
                self = UUID.randomUUID(),
                crates = emptyMap(),
                pushDisabled = true
            )
            val mockTracker = MockBackupTracker()

            val stage = object : EntityProcessing {
                override val targetDataset: DatasetDefinition = Fixtures.Datasets.Default

                override val deviceSecret: DeviceSecret = Fixtures.Secrets.Default

                override val providers: Providers = Providers(
                    checksum = Checksum.Companion.MD5,
                    staging = mockStaging,
                    compression = mockCompression,
                    encryptor = mockEncryption,
                    decryptor = mockEncryption,
                    clients = Clients(
                        api = MockServerApiEndpointClient(),
                        core = mockCoreClient
                    ),
                    track = mockTracker
                )

                override val maxPartSize: Long = 16384
            }

            val e = shouldThrow<RuntimeException> {
                stage
                    .entityProcessing(operation = Operation.generateId(), flow = listOf(sourceFile1).asFlow())
                    .toList()
            }

            e.message shouldBe ("[pushDisabled] is set to [true]")

            mockStaging.statistics[MockFileStaging.Statistic.TemporaryCreated] shouldBe (1)
            mockStaging.statistics[MockFileStaging.Statistic.TemporaryDiscarded] shouldBe (1)
            mockStaging.statistics[MockFileStaging.Statistic.Destaged] shouldBe (0)

            mockCompression.statistics[MockCompression.Statistic.Compressed] shouldBe (1)
            mockCompression.statistics[MockCompression.Statistic.Decompressed] shouldBe (0)

            mockEncryption.statistics[MockEncryption.Statistic.FileEncrypted] shouldBe (1)
            mockEncryption.statistics[MockEncryption.Statistic.FileDecrypted] shouldBe (0)
            mockEncryption.statistics[MockEncryption.Statistic.MetadataEncrypted] shouldBe (0)
            mockEncryption.statistics[MockEncryption.Statistic.MetadataDecrypted] shouldBe (0)

            mockCoreClient.statistics[MockServerCoreEndpointClient.Statistic.CratePulled] shouldBe (0)
            mockCoreClient.statistics[MockServerCoreEndpointClient.Statistic.CratePushed] shouldBe (0)

            mockTracker.statistics[MockBackupTracker.Statistic.EntityDiscovered] shouldBe (0)
            mockTracker.statistics[MockBackupTracker.Statistic.SpecificationProcessed] shouldBe (0)
            mockTracker.statistics[MockBackupTracker.Statistic.EntityExamined] shouldBe (0)
            mockTracker.statistics[MockBackupTracker.Statistic.EntityCollected] shouldBe (0)
            mockTracker.statistics[MockBackupTracker.Statistic.EntityProcessed] shouldBe (0)
            mockTracker.statistics[MockBackupTracker.Statistic.MetadataCollected] shouldBe (0)
            mockTracker.statistics[MockBackupTracker.Statistic.MetadataPushed] shouldBe (0)
            mockTracker.statistics[MockBackupTracker.Statistic.FailureEncountered] shouldBe (0)
            mockTracker.statistics[MockBackupTracker.Statistic.Completed] shouldBe (0)
        }

        "fail if unexpected target entity metadata is provided" {
            val entity = SourceEntity(
                path = Fixtures.Metadata.DirectoryOneMetadata.path,
                existingMetadata = null,
                currentMetadata = Fixtures.Metadata.DirectoryOneMetadata
            )

            val e = shouldThrow<IllegalArgumentException> {
                EntityProcessing.expectFileMetadata(entity = entity)
            }

            e.message shouldBe ("Expected metadata for file but directory metadata for [${entity.path}] provided")
        }

        "fail processing a file if processing for a part fails" {
            val largeSourceFileMetadata = "/ops/large-source-file-1".asTestResource().extractFileMetadata(
                withChecksum = BigInteger("1"),
                withCrate = UUID.randomUUID()
            )

            val largeSourceFile = SourceEntity(
                path = largeSourceFileMetadata.path,
                existingMetadata = null,
                currentMetadata = largeSourceFileMetadata
            )

            val maxPartsBeforeFailure = 3
            val remainingParts = AtomicInteger(maxPartsBeforeFailure)

            val mockStaging = MockFileStaging()
            val mockCompression = MockCompression()
            val mockEncryption = MockEncryption()
            val mockCoreClient = object : MockServerCoreEndpointClient(
                self = UUID.randomUUID(),
                crates = emptyMap()
            ) {
                override suspend fun push(manifest: Manifest, content: Source) {
                    if (remainingParts.get() > 0) {
                        remainingParts.decrementAndGet()
                    } else {
                        throw RuntimeException("Test failure")
                    }
                }
            }
            val mockTracker = MockBackupTracker()

            val stage = object : EntityProcessing {
                override val targetDataset: DatasetDefinition = Fixtures.Datasets.Default

                override val deviceSecret: DeviceSecret = Fixtures.Secrets.Default

                override val providers: Providers = Providers(
                    checksum = Checksum.Companion.MD5,
                    staging = mockStaging,
                    compression = mockCompression,
                    encryptor = mockEncryption,
                    decryptor = mockEncryption,
                    clients = Clients(
                        api = MockServerApiEndpointClient(),
                        core = mockCoreClient
                    ),
                    track = mockTracker
                )

                override val maxPartSize: Long = 10
            }

            val e = shouldThrow<RuntimeException> {
                stage
                    .entityProcessing(operation = Operation.generateId(), flow = listOf(largeSourceFile).asFlow())
                    .toList()
            }

            e.message shouldBe ("Test failure")

            val expectedParts = 4

            mockStaging.statistics[MockFileStaging.Statistic.TemporaryCreated] shouldBe (expectedParts)
            mockStaging.statistics[MockFileStaging.Statistic.TemporaryDiscarded] shouldBe (expectedParts)
            mockStaging.statistics[MockFileStaging.Statistic.Destaged] shouldBe (0)

            mockEncryption.statistics[MockEncryption.Statistic.FileEncrypted] shouldBe (expectedParts)
            mockEncryption.statistics[MockEncryption.Statistic.FileDecrypted] shouldBe (0)
            mockEncryption.statistics[MockEncryption.Statistic.MetadataEncrypted] shouldBe (0)
            mockEncryption.statistics[MockEncryption.Statistic.MetadataDecrypted] shouldBe (0)

            mockCoreClient.statistics[MockServerCoreEndpointClient.Statistic.CratePulled] shouldBe (0)
            mockCoreClient.statistics[MockServerCoreEndpointClient.Statistic.CratePushed] shouldBe (0)

            mockTracker.statistics[MockBackupTracker.Statistic.EntityDiscovered] shouldBe (0)
            mockTracker.statistics[MockBackupTracker.Statistic.SpecificationProcessed] shouldBe (0)
            mockTracker.statistics[MockBackupTracker.Statistic.EntityExamined] shouldBe (0)
            mockTracker.statistics[MockBackupTracker.Statistic.EntityCollected] shouldBe (0)
            mockTracker.statistics[MockBackupTracker.Statistic.EntityProcessed] shouldBe (0)
            mockTracker.statistics[MockBackupTracker.Statistic.MetadataCollected] shouldBe (0)
            mockTracker.statistics[MockBackupTracker.Statistic.MetadataPushed] shouldBe (0)
            mockTracker.statistics[MockBackupTracker.Statistic.FailureEncountered] shouldBe (0)
            mockTracker.statistics[MockBackupTracker.Statistic.Completed] shouldBe (0)
        }
    }
})
