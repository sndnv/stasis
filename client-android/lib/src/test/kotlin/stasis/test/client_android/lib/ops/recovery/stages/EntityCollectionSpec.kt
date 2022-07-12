package stasis.test.client_android.lib.ops.recovery.stages

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.toList
import stasis.client_android.lib.analysis.Checksum
import stasis.client_android.lib.api.clients.Clients
import stasis.client_android.lib.collection.RecoveryCollector
import stasis.client_android.lib.model.TargetEntity
import stasis.client_android.lib.ops.Operation
import stasis.client_android.lib.ops.recovery.Providers
import stasis.client_android.lib.ops.recovery.stages.EntityCollection
import stasis.test.client_android.lib.Fixtures
import stasis.test.client_android.lib.mocks.MockCompression
import stasis.test.client_android.lib.mocks.MockEncryption
import stasis.test.client_android.lib.mocks.MockFileStaging
import stasis.test.client_android.lib.mocks.MockRecoveryCollector
import stasis.test.client_android.lib.mocks.MockRecoveryTracker
import stasis.test.client_android.lib.mocks.MockServerApiEndpointClient
import stasis.test.client_android.lib.mocks.MockServerCoreEndpointClient

class EntityCollectionSpec : WordSpec({
    "A Recovery EntityCollection stage" should {
        "collect and filter files" {
            val targetFile1 = TargetEntity(
                path = Fixtures.Metadata.FileOneMetadata.path,
                destination = TargetEntity.Destination.Default,
                existingMetadata = Fixtures.Metadata.FileOneMetadata,
                currentMetadata = null
            )

            val targetFile2 = TargetEntity(
                path = Fixtures.Metadata.FileTwoMetadata.path,
                destination = TargetEntity.Destination.Default,
                existingMetadata = Fixtures.Metadata.FileTwoMetadata,
                currentMetadata = Fixtures.Metadata.FileTwoMetadata
            )

            val targetFile3 = TargetEntity(
                path = Fixtures.Metadata.FileThreeMetadata.path,
                destination = TargetEntity.Destination.Default,
                existingMetadata = Fixtures.Metadata.FileThreeMetadata,
                currentMetadata = Fixtures.Metadata.FileThreeMetadata.copy(isHidden = true)
            )

            val mockTracker = MockRecoveryTracker()

            val stage = object : EntityCollection {
                override val collector: RecoveryCollector =
                    MockRecoveryCollector(files = listOf(targetFile1, targetFile2, targetFile3))

                override val providers: Providers = Providers(
                    checksum = Checksum.Companion.MD5,
                    staging = MockFileStaging(),
                    compression = MockCompression(),
                    decryptor = MockEncryption(),
                    clients = Clients(
                        api = MockServerApiEndpointClient(),
                        core = MockServerCoreEndpointClient()
                    ),
                    track = mockTracker
                )
            }

            val collectedFiles = stage.entityCollection(
                operation = Operation.generateId()
            ).toList()

            collectedFiles shouldBe (listOf(targetFile1, targetFile3))

            mockTracker.statistics[MockRecoveryTracker.Statistic.EntityExamined] shouldBe (3)
            mockTracker.statistics[MockRecoveryTracker.Statistic.EntityCollected] shouldBe (2)
            mockTracker.statistics[MockRecoveryTracker.Statistic.EntityProcessingStarted] shouldBe (0)
            mockTracker.statistics[MockRecoveryTracker.Statistic.EntityPartProcessed] shouldBe (0)
            mockTracker.statistics[MockRecoveryTracker.Statistic.EntityProcessed] shouldBe (0)
            mockTracker.statistics[MockRecoveryTracker.Statistic.MetadataApplied] shouldBe (0)
            mockTracker.statistics[MockRecoveryTracker.Statistic.FailureEncountered] shouldBe (0)
            mockTracker.statistics[MockRecoveryTracker.Statistic.Completed] shouldBe (0)
        }
    }
})
