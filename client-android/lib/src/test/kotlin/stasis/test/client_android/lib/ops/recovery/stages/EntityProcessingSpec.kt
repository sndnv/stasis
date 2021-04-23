package stasis.test.client_android.lib.ops.recovery.stages

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.toList
import okio.ByteString.Companion.toByteString
import stasis.client_android.lib.analysis.Checksum
import stasis.client_android.lib.api.clients.Clients
import stasis.client_android.lib.encryption.secrets.DeviceSecret
import stasis.client_android.lib.model.TargetEntity
import stasis.client_android.lib.ops.Operation
import stasis.client_android.lib.ops.recovery.Providers
import stasis.client_android.lib.ops.recovery.stages.EntityProcessing
import stasis.test.client_android.lib.Fixtures
import stasis.test.client_android.lib.ResourceHelpers.asTestResource
import stasis.test.client_android.lib.ResourceHelpers.clear
import stasis.test.client_android.lib.ResourceHelpers.extractDirectoryMetadata
import stasis.test.client_android.lib.ResourceHelpers.extractFileMetadata
import stasis.test.client_android.lib.ResourceHelpers.withRootAt
import stasis.test.client_android.lib.mocks.MockCompression
import stasis.test.client_android.lib.mocks.MockEncryption
import stasis.test.client_android.lib.mocks.MockFileStaging
import stasis.test.client_android.lib.mocks.MockRecoveryTracker
import stasis.test.client_android.lib.mocks.MockServerApiEndpointClient
import stasis.test.client_android.lib.mocks.MockServerCoreEndpointClient
import java.math.BigInteger
import java.nio.file.Paths
import java.util.UUID

class EntityProcessingSpec : WordSpec({
    "A Recovery EntityProcessing stage" should {
        "process files and directories with changed content and metadata" {
            val targetFile2Metadata = "/ops/source-file-2".asTestResource().extractFileMetadata(
                withChecksum = BigInteger("2"),
                withCrate = UUID.randomUUID()
            )

            val targetFile3Metadata = "/ops/source-file-3".asTestResource().extractFileMetadata(
                withChecksum = BigInteger("3"),
                withCrate = UUID.randomUUID()
            )

            val targetFile4Path = "/ops/nested/source-file-4".asTestResource()
            val targetFile4Metadata = targetFile4Path
                .extractFileMetadata(
                    withChecksum = BigInteger("3"),
                    withCrate = UUID.randomUUID()
                )
                .copy(
                    crates = mapOf(
                        Paths.get("${targetFile4Path}_0") to UUID.randomUUID(),
                        Paths.get("${targetFile4Path}_1") to UUID.randomUUID(),
                        Paths.get("${targetFile4Path}_2") to UUID.randomUUID(),
                        Paths.get("${targetFile4Path}_3") to UUID.randomUUID()
                    )
                )

            val targetDirectoryMetadata = "/ops/nested".asTestResource().extractDirectoryMetadata().withRootAt("/ops")

            val ignoredDirectoryMetadata = Fixtures.Metadata.DirectoryOneMetadata

            val targetDirectoryDestination = "/ops/processing".asTestResource()
            targetDirectoryDestination.clear()

            val targetFile2 = TargetEntity(
                path = targetFile2Metadata.path,
                destination = TargetEntity.Destination.Default,
                existingMetadata = targetFile2Metadata,
                currentMetadata = targetFile2Metadata.copy(isHidden = true)
            )

            val targetFile3 = TargetEntity(
                path = targetFile3Metadata.path,
                destination = TargetEntity.Destination.Directory(path = targetDirectoryDestination, keepDefaultStructure = false),
                existingMetadata = targetFile3Metadata,
                currentMetadata = targetFile3Metadata.copy(checksum = BigInteger("9999"))
            )

            val targetFile4 = TargetEntity(
                path = targetFile4Metadata.path,
                destination = TargetEntity.Destination.Default,
                existingMetadata = targetFile4Metadata,
                currentMetadata = targetFile4Metadata.copy(checksum = BigInteger("9999"))
            )

            val targetDirectory = TargetEntity(
                path = targetDirectoryMetadata.path,
                destination = TargetEntity.Destination.Directory(path = targetDirectoryDestination, keepDefaultStructure = true),
                existingMetadata = targetDirectoryMetadata,
                currentMetadata = targetDirectoryMetadata
            )

            val ignoredDirectory = TargetEntity(
                path = ignoredDirectoryMetadata.path,
                destination = TargetEntity.Destination.Directory(path = targetDirectoryDestination, keepDefaultStructure = false),
                existingMetadata = ignoredDirectoryMetadata,
                currentMetadata = ignoredDirectoryMetadata
            )

            val mockStaging = MockFileStaging()
            val mockCompression = MockCompression()
            val mockEncryption = MockEncryption()
            val mockApiClient = MockServerApiEndpointClient()
            val mockCoreClient = MockServerCoreEndpointClient(
                self = UUID.randomUUID(),
                crates = (targetFile2Metadata.crates.map { (_, id) ->
                    id to ("source-file-2".toByteArray().toByteString())
                } + targetFile3Metadata.crates.map { (_, id) ->
                    id to ("source-file-3".toByteArray().toByteString())
                } + targetFile4Metadata.crates.map { (_, id) ->
                    id to ("source-file-4".toByteArray().toByteString())
                }).toMap()
            )
            val mockTracker = MockRecoveryTracker()

            val stage = object : EntityProcessing {
                override val deviceSecret: DeviceSecret = Fixtures.Secrets.Default

                override val providers: Providers = Providers(
                    checksum = Checksum.Companion.MD5,
                    staging = mockStaging,
                    decompressor = mockCompression,
                    decryptor = mockEncryption,
                    clients = Clients(api = mockApiClient, core = mockCoreClient),
                    track = mockTracker
                )
            }

            val stageOutput = stage.entityProcessing(
                operation = Operation.generateId(),
                flow = listOf(targetFile2, targetFile3, targetFile4, targetDirectory, ignoredDirectory).asFlow()
            ).toList()

            stageOutput shouldBe (
                    listOf(
                        targetFile2,
                        targetFile3,
                        targetFile4,
                        targetDirectory
                    )
                    )

            val metadataChanged = 2 // file2 + directory
            val contentChanged = 2 // file3 + file4
            val totalChanged = metadataChanged + contentChanged

            val contentCrates = 5 // 1 crate for file3 + 4 crates for file4

            mockStaging.statistics[MockFileStaging.Statistic.TemporaryCreated] shouldBe (contentChanged)
            mockStaging.statistics[MockFileStaging.Statistic.TemporaryDiscarded] shouldBe (0)
            mockStaging.statistics[MockFileStaging.Statistic.Destaged] shouldBe (contentChanged)

            mockCompression.statistics[MockCompression.Statistic.Compressed] shouldBe (0)
            mockCompression.statistics[MockCompression.Statistic.Decompressed] shouldBe (contentChanged)

            mockEncryption.statistics[MockEncryption.Statistic.FileEncrypted] shouldBe (0)
            mockEncryption.statistics[MockEncryption.Statistic.FileDecrypted] shouldBe (contentCrates)
            mockEncryption.statistics[MockEncryption.Statistic.MetadataEncrypted] shouldBe (0)
            mockEncryption.statistics[MockEncryption.Statistic.MetadataDecrypted] shouldBe (0)

            mockCoreClient.statistics[MockServerCoreEndpointClient.Statistic.CratePulled] shouldBe (contentCrates)
            mockCoreClient.statistics[MockServerCoreEndpointClient.Statistic.CratePushed] shouldBe (0)

            mockTracker.statistics[MockRecoveryTracker.Statistic.EntityExamined] shouldBe (0)
            mockTracker.statistics[MockRecoveryTracker.Statistic.EntityCollected] shouldBe (0)
            mockTracker.statistics[MockRecoveryTracker.Statistic.EntityProcessed] shouldBe (totalChanged)
            mockTracker.statistics[MockRecoveryTracker.Statistic.MetadataApplied] shouldBe (0)
            mockTracker.statistics[MockRecoveryTracker.Statistic.FailureEncountered] shouldBe (0)
            mockTracker.statistics[MockRecoveryTracker.Statistic.Completed] shouldBe (0)
        }

        "fail if crates could not be pulled" {
            val targetFile1Metadata = "/ops/source-file-2".asTestResource().extractFileMetadata(
                withChecksum = BigInteger("2"),
                withCrate = UUID.randomUUID()
            )

            val targetFile1 = TargetEntity(
                path = targetFile1Metadata.path,
                destination = TargetEntity.Destination.Default,
                existingMetadata = targetFile1Metadata,
                currentMetadata = targetFile1Metadata.copy(checksum = BigInteger("9999"))
            )

            val mockStaging = MockFileStaging()
            val mockCompression = MockCompression()
            val mockEncryption = MockEncryption()
            val mockApiClient = MockServerApiEndpointClient()
            val mockCoreClient = MockServerCoreEndpointClient()
            val mockTracker = MockRecoveryTracker()

            val stage = object : EntityProcessing {
                override val deviceSecret: DeviceSecret = Fixtures.Secrets.Default

                override val providers: Providers = Providers(
                    checksum = Checksum.Companion.MD5,
                    staging = mockStaging,
                    decompressor = mockCompression,
                    decryptor = mockEncryption,
                    clients = Clients(api = mockApiClient, core = mockCoreClient),
                    track = mockTracker
                )
            }

            val e = shouldThrow<RuntimeException> {
                stage.entityProcessing(
                    operation = Operation.generateId(),
                    flow = listOf(targetFile1).asFlow()
                ).collect()
            }

            e.message shouldStartWith ("Failed to pull crate")

            mockStaging.statistics[MockFileStaging.Statistic.TemporaryCreated] shouldBe (0)
            mockStaging.statistics[MockFileStaging.Statistic.TemporaryDiscarded] shouldBe (0)
            mockStaging.statistics[MockFileStaging.Statistic.Destaged] shouldBe (0)

            mockCompression.statistics[MockCompression.Statistic.Compressed] shouldBe (0)
            mockCompression.statistics[MockCompression.Statistic.Decompressed] shouldBe (0)

            mockEncryption.statistics[MockEncryption.Statistic.FileEncrypted] shouldBe (0)
            mockEncryption.statistics[MockEncryption.Statistic.FileDecrypted] shouldBe (0)
            mockEncryption.statistics[MockEncryption.Statistic.MetadataEncrypted] shouldBe (0)
            mockEncryption.statistics[MockEncryption.Statistic.MetadataDecrypted] shouldBe (0)

            mockCoreClient.statistics[MockServerCoreEndpointClient.Statistic.CratePulled] shouldBe (0)
            mockCoreClient.statistics[MockServerCoreEndpointClient.Statistic.CratePushed] shouldBe (0)

            mockTracker.statistics[MockRecoveryTracker.Statistic.EntityExamined] shouldBe (0)
            mockTracker.statistics[MockRecoveryTracker.Statistic.EntityCollected] shouldBe (0)
            mockTracker.statistics[MockRecoveryTracker.Statistic.EntityProcessed] shouldBe (0)
            mockTracker.statistics[MockRecoveryTracker.Statistic.MetadataApplied] shouldBe (0)
            mockTracker.statistics[MockRecoveryTracker.Statistic.FailureEncountered] shouldBe (0)
            mockTracker.statistics[MockRecoveryTracker.Statistic.Completed] shouldBe (0)
        }

        "fail if unexpected target entity metadata is provided" {
            val entity = TargetEntity(
                path = Fixtures.Metadata.DirectoryOneMetadata.path,
                destination = TargetEntity.Destination.Default,
                existingMetadata = Fixtures.Metadata.DirectoryOneMetadata,
                currentMetadata = null
            )

            val e = shouldThrow<IllegalArgumentException> {
                EntityProcessing.expectFileMetadata(entity = entity)
            }

            e.message shouldBe ("Expected metadata for file but directory metadata for [${entity.path}] provided")
        }
    }
})
