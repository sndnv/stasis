package stasis.test.client_android.lib.ops.recovery.stages

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import stasis.client_android.lib.analysis.Checksum
import stasis.client_android.lib.analysis.Metadata
import stasis.client_android.lib.api.clients.Clients
import stasis.client_android.lib.model.EntityMetadata
import stasis.client_android.lib.model.TargetEntity
import stasis.client_android.lib.ops.Operation
import stasis.client_android.lib.ops.recovery.Providers
import stasis.client_android.lib.ops.recovery.stages.MetadataApplication
import stasis.test.client_android.lib.mocks.MockCompression
import stasis.test.client_android.lib.mocks.MockEncryption
import stasis.test.client_android.lib.mocks.MockFileStaging
import stasis.test.client_android.lib.mocks.MockRecoveryTracker
import stasis.test.client_android.lib.mocks.MockServerApiEndpointClient
import stasis.test.client_android.lib.mocks.MockServerCoreEndpointClient
import java.math.BigInteger
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Paths
import java.nio.file.attribute.PosixFileAttributes
import java.time.Instant
import java.util.UUID

class MetadataApplicationSpec : WordSpec({
    "A Recovery MetadataApplication stage" should {
        "apply metadata to files" {
            val targetFile = Files.createTempFile("metadata-target-file", "")
            targetFile.toFile().deleteOnExit()

            val attributes = Files.readAttributes(targetFile, PosixFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)

            val metadata = EntityMetadata.File(
                path = targetFile,
                size = 1,
                link = null,
                isHidden = false,
                created = Instant.parse("2020-01-01T00:00:00Z"),
                updated = Instant.parse("2020-01-03T00:00:00Z"),
                owner = attributes.owner().name,
                group = attributes.group().name,
                permissions = "rwxrwxrwx",
                checksum = BigInteger("1"),
                crates = mapOf(Paths.get("${targetFile}_0") to UUID.randomUUID()),
                compression = "none"
            )

            val mockTracker = MockRecoveryTracker()

            val stage = object : MetadataApplication {
                override val providers: Providers = Providers(
                    checksum = Checksum.Companion.MD5,
                    staging = MockFileStaging(),
                    compression = MockCompression(),
                    decryptor = MockEncryption(),
                    clients = Clients(api = MockServerApiEndpointClient(), core = MockServerCoreEndpointClient()),
                    track = mockTracker
                )
            }

            val target = TargetEntity(
                path = metadata.path,
                destination = TargetEntity.Destination.Default,
                existingMetadata = metadata,
                currentMetadata = null
            )

            val metadataBeforeApplication = Metadata.extractBaseEntityMetadata(entity = targetFile)

            stage.metadataApplication(
                operation = Operation.generateId(),
                flow = listOf(target).asFlow()
            ).collect()

            val metadataAfterApplication = Metadata.extractBaseEntityMetadata(entity = targetFile)

            metadataBeforeApplication.permissions shouldNotBe metadata.permissions
            metadataBeforeApplication.updated shouldNotBe metadata.updated

            metadataAfterApplication.owner shouldBe (metadata.owner)
            metadataAfterApplication.group shouldBe (metadata.group)
            metadataAfterApplication.permissions shouldBe (metadata.permissions)
            metadataAfterApplication.updated shouldBe (metadata.updated)

            mockTracker.statistics[MockRecoveryTracker.Statistic.EntityExamined] shouldBe (0)
            mockTracker.statistics[MockRecoveryTracker.Statistic.EntityCollected] shouldBe (0)
            mockTracker.statistics[MockRecoveryTracker.Statistic.EntityProcessed] shouldBe (0)
            mockTracker.statistics[MockRecoveryTracker.Statistic.MetadataApplied] shouldBe (1)
            mockTracker.statistics[MockRecoveryTracker.Statistic.FailureEncountered] shouldBe (0)
            mockTracker.statistics[MockRecoveryTracker.Statistic.Completed] shouldBe (0)
        }
    }
})
