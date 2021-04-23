package stasis.test.client_android.lib.collection

import io.kotest.assertions.fail
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import stasis.client_android.lib.analysis.Checksum
import stasis.client_android.lib.collection.RecoveryMetadataCollector
import stasis.client_android.lib.model.EntityMetadata
import stasis.client_android.lib.model.TargetEntity
import stasis.test.client_android.lib.Fixtures
import stasis.test.client_android.lib.ResourceHelpers.asTestResource

class RecoveryMetadataCollectorSpec : WordSpec({
    "A RecoveryMetadataCollector" should {
        "collect file metadata (recovery)" {
            val file2 = "/collection/file-2".asTestResource()
            val file3 = "/collection/file-3".asTestResource()

            val collector = RecoveryMetadataCollector.Default(
                checksum = Checksum.Companion.MD5
            )

            val file2Metadata = Fixtures.Metadata.FileTwoMetadata.copy(path = file2)
            val file3Metadata = Fixtures.Metadata.FileThreeMetadata.copy(path = file3)

            val targetFile2 = collector.collect(
                entity = file2,
                destination = TargetEntity.Destination.Default,
                existingMetadata = file2Metadata
            )

            val targetFile3 = collector.collect(
                entity = file3,
                destination = TargetEntity.Destination.Default,
                existingMetadata = file3Metadata
            )

            targetFile2.path shouldBe (file2)
            targetFile2.existingMetadata shouldBe (file2Metadata)
            when (val metadata = targetFile2.currentMetadata) {
                is EntityMetadata.File -> metadata.size shouldBe (2)
                is EntityMetadata.Directory -> fail("Expected file but received directory metadata")
                null -> fail("Expected metadata but not received")
            }

            targetFile3.path shouldBe (file3)
            targetFile3.existingMetadata shouldBe (file3Metadata)
            when (val metadata = targetFile3.currentMetadata) {
                is EntityMetadata.File -> metadata.size shouldBe (3)
                is EntityMetadata.Directory -> fail("Expected file but received directory metadata")
                null -> fail("Expected metadata but not received")
            }
        }
    }
})
