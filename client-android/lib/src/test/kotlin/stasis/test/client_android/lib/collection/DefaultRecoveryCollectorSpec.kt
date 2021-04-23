package stasis.test.client_android.lib.collection

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.flow.fold
import stasis.client_android.lib.collection.DefaultRecoveryCollector
import stasis.client_android.lib.model.DatasetMetadata
import stasis.client_android.lib.model.EntityMetadata
import stasis.client_android.lib.model.FilesystemMetadata
import stasis.client_android.lib.model.TargetEntity
import stasis.test.client_android.lib.Fixtures
import stasis.test.client_android.lib.ResourceHelpers.asTestResource
import stasis.test.client_android.lib.mocks.MockRecoveryMetadataCollector
import stasis.test.client_android.lib.mocks.MockServerApiEndpointClient
import java.math.BigInteger
import java.nio.file.Paths
import java.time.Instant
import java.util.UUID

class DefaultRecoveryCollectorSpec : WordSpec({
    "A DefaultRecoveryCollector" should {
        "collect recovery files based on dataset metadata" {
            val file2 = "/collection/file-2".asTestResource()
            val file3 = "/collection/file-3".asTestResource()

            val file2Metadata = EntityMetadata.File(
                path = file2,
                size = 0,
                link = null,
                isHidden = false,
                created = Instant.now(),
                updated = Instant.now(),
                owner = "some-owner",
                group = "some-group",
                permissions = "rwxrwxrwx",
                checksum = BigInteger("1"),
                crates = mapOf(
                    Paths.get("${file2}_0") to UUID.randomUUID()
                )
            )

            val file3Metadata = EntityMetadata.File(
                path = file3,
                size = 0,
                link = null,
                isHidden = false,
                created = Instant.now(),
                updated = Instant.now(),
                owner = "some-owner",
                group = "some-group",
                permissions = "rwxrwxrwx",
                checksum = BigInteger("1"),
                crates = mapOf(
                    Paths.get("${file3}_0") to UUID.randomUUID()
                )
            )

            val collector = DefaultRecoveryCollector(
                targetMetadata = DatasetMetadata(
                    contentChanged = mapOf(
                        file2Metadata.path to file2Metadata
                    ),
                    metadataChanged = mapOf(
                        file3Metadata.path to file3Metadata
                    ),
                    filesystem = FilesystemMetadata(
                        entities = mapOf(
                            file2Metadata.path to FilesystemMetadata.EntityState.New,
                            file3Metadata.path to FilesystemMetadata.EntityState.Updated
                        )
                    )
                ),
                keep = { _, _ -> true },
                destination = TargetEntity.Destination.Default,
                metadataCollector = MockRecoveryMetadataCollector(
                    metadata = mapOf(
                        file2Metadata.path to file2Metadata,
                        file3Metadata.path to file3Metadata
                    )
                ),
                api = MockServerApiEndpointClient()
            )

            val targetFiles = collector
                .collect()
                .fold(emptyList<TargetEntity>()) { acc, value -> acc + value }
                .sortedBy { it.path.toAbsolutePath().toString() }

            targetFiles.size shouldBe (2)

            val targetFile2 = targetFiles[0]
            targetFile2.path shouldBe (file2Metadata.path)
            targetFile2.existingMetadata shouldBe (file2Metadata)
            targetFile2.currentMetadata shouldNotBe (null)

            val targetFile3 = targetFiles[1]
            targetFile3.path shouldBe (file3Metadata.path)
            targetFile3.existingMetadata shouldBe (file3Metadata)
            targetFile3.currentMetadata shouldNotBe (null)
        }

        "collect metadata for individual files" {
            val targetMetadata = DatasetMetadata(
                contentChanged = mapOf(
                    Fixtures.Metadata.FileOneMetadata.path to Fixtures.Metadata.FileOneMetadata
                ),
                metadataChanged = mapOf(
                    Fixtures.Metadata.FileTwoMetadata.path to Fixtures.Metadata.FileTwoMetadata,
                    Fixtures.Metadata.FileThreeMetadata.path to Fixtures.Metadata.FileThreeMetadata
                ),
                filesystem = FilesystemMetadata(
                    entities = mapOf(
                        Fixtures.Metadata.FileOneMetadata.path to FilesystemMetadata.EntityState.New,
                        Fixtures.Metadata.FileTwoMetadata.path to FilesystemMetadata.EntityState.New,
                        Fixtures.Metadata.FileThreeMetadata.path to FilesystemMetadata.EntityState.Updated
                    )
                )
            )

            val actualMetadata = DefaultRecoveryCollector.collectEntityMetadata(
                targetMetadata = targetMetadata,
                keep = { _, state -> state == FilesystemMetadata.EntityState.New },
                api = MockServerApiEndpointClient()
            )

            actualMetadata shouldBe (
                    listOf(
                        Fixtures.Metadata.FileOneMetadata,
                        Fixtures.Metadata.FileTwoMetadata
                    )
                    )
        }
    }
})
