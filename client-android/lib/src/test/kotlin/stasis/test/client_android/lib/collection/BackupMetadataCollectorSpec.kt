package stasis.test.client_android.lib.collection

import io.kotest.assertions.fail
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import stasis.client_android.lib.analysis.Checksum
import stasis.client_android.lib.collection.BackupMetadataCollector
import stasis.client_android.lib.model.EntityMetadata
import stasis.test.client_android.lib.Fixtures
import stasis.test.client_android.lib.ResourceHelpers.asTestResource
import stasis.test.client_android.lib.mocks.MockCompression

class BackupMetadataCollectorSpec : WordSpec({
    "A BackupMetadataCollector" should {
        "collect file metadata (backup)" {
            val file1 = "/collection/file-1".asTestResource()
            val file2 = "/collection/file-2".asTestResource()
            val file3 = "/collection/file-3".asTestResource()

            val file2Metadata = Fixtures.Metadata.FileTwoMetadata.copy(path = file2.toString())
            val file3Metadata = Fixtures.Metadata.FileThreeMetadata.copy(path = file3.toString())

            val collector = BackupMetadataCollector.Default(
                checksum = Checksum.Companion.MD5,
                compression = MockCompression()
            )

            val sourceFile1 = collector.collect(entity = file1, existingMetadata = null)
            val sourceFile2 = collector.collect(entity = file2, existingMetadata = file2Metadata)
            val sourceFile3 = collector.collect(entity = file3, existingMetadata = file3Metadata)

            sourceFile1.path shouldBe (file1)
            sourceFile1.existingMetadata shouldBe (null)
            when (val metadata = sourceFile1.currentMetadata) {
                is EntityMetadata.File -> metadata.size shouldBe (1)
                is EntityMetadata.Directory -> fail("Expected file but received directory metadata")
            }

            sourceFile2.path shouldBe (file2)
            sourceFile2.existingMetadata shouldBe (file2Metadata)
            when (val metadata = sourceFile2.currentMetadata) {
                is EntityMetadata.File -> metadata.size shouldBe (2)
                is EntityMetadata.Directory -> fail("Expected file but received directory metadata")
            }

            sourceFile3.path shouldBe (file3)
            sourceFile3.existingMetadata shouldBe (file3Metadata)
            when (val metadata = sourceFile3.currentMetadata) {
                is EntityMetadata.File -> metadata.size shouldBe (3)
                is EntityMetadata.Directory -> fail("Expected file but received directory metadata")
            }
        }
    }
})
