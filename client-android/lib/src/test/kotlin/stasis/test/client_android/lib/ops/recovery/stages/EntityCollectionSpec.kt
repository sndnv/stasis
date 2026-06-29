package stasis.test.client_android.lib.ops.recovery.stages

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.toList
import stasis.client_android.lib.analysis.Checksum
import stasis.client_android.lib.api.clients.Clients
import stasis.client_android.lib.model.DatasetMetadata
import stasis.client_android.lib.model.FilesystemMetadata
import stasis.client_android.lib.model.TargetEntity
import stasis.client_android.lib.ops.Operation
import stasis.client_android.lib.ops.recovery.Providers
import stasis.client_android.lib.ops.recovery.stages.EntityCollection
import stasis.client_android.lib.telemetry.analytics.AnalyticsCollector
import stasis.test.client_android.lib.Fixtures
import stasis.test.client_android.lib.ResourceHelpers.asRef
import stasis.test.client_android.lib.mocks.MockCompression
import stasis.test.client_android.lib.mocks.MockEncryption
import stasis.test.client_android.lib.mocks.MockFileStaging
import stasis.test.client_android.lib.mocks.MockRecoveryCollector
import stasis.test.client_android.lib.mocks.MockRecoveryEntityKind
import stasis.test.client_android.lib.mocks.MockRecoveryTracker
import stasis.test.client_android.lib.mocks.MockServerApiEndpointClient
import stasis.test.client_android.lib.mocks.MockServerCoreEndpointClient
import java.nio.file.FileSystems

class EntityCollectionSpec : WordSpec({
    "A Recovery EntityCollection stage" should {
        "collect and filter files" {
            val targetFile1 = TargetEntity(
                ref = Fixtures.Metadata.FileOneMetadata.path.asRef(),
                destination = TargetEntity.Destination.Default,
                existingMetadata = Fixtures.Metadata.FileOneMetadata,
                currentMetadata = null
            )

            val targetFile2 = TargetEntity(
                ref = Fixtures.Metadata.FileTwoMetadata.path.asRef(),
                destination = TargetEntity.Destination.Default,
                existingMetadata = Fixtures.Metadata.FileTwoMetadata,
                currentMetadata = Fixtures.Metadata.FileTwoMetadata
            )

            val targetFile3 = TargetEntity(
                ref = Fixtures.Metadata.FileThreeMetadata.path.asRef(),
                destination = TargetEntity.Destination.Default,
                existingMetadata = Fixtures.Metadata.FileThreeMetadata,
                currentMetadata = Fixtures.Metadata.FileThreeMetadata.copy(isHidden = true)
            )

            val mockTracker = MockRecoveryTracker()

            val stage = object : EntityCollection {
                override val targetMetadata: DatasetMetadata = DatasetMetadata.empty()
                override val keep: (String, FilesystemMetadata.EntityState) -> Boolean = { _, _ -> true }
                override val destination: TargetEntity.Destination = TargetEntity.Destination.Default

                override val providers: Providers = Providers(
                    checksum = Checksum.Companion.MD5,
                    staging = MockFileStaging(),
                    compression = MockCompression(),
                    decryptor = MockEncryption(),
                    clients = Clients(
                        api = MockServerApiEndpointClient(),
                        core = MockServerCoreEndpointClient()
                    ),
                    track = mockTracker,
                    analytics = AnalyticsCollector.NoOp,
                    kinds = listOf(
                        MockRecoveryEntityKind(
                            MockRecoveryCollector(files = listOf(targetFile1, targetFile2, targetFile3))
                        )
                    )
                )
            }

            val collectedFiles = stage.entityCollection(
                operation = Operation.generateId(),
                filesystem = FileSystems.getDefault()
            ).toList()

            collectedFiles shouldBe (listOf(targetFile1, targetFile3))

            mockTracker.statistics[MockRecoveryTracker.Statistic.Started] shouldBe (0)
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
