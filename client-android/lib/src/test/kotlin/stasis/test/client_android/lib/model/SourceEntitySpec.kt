package stasis.test.client_android.lib.model

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import okio.ByteString.Companion.encodeUtf8
import stasis.client_android.lib.model.SourceEntity
import stasis.test.client_android.lib.Fixtures
import stasis.test.client_android.lib.ResourceHelpers.asRef
import java.math.BigInteger

class SourceEntitySpec : WordSpec({
    "A SourceEntity" should {
        val fileEntity = SourceEntity(
            ref = Fixtures.Metadata.FileOneMetadata.path.asRef(),
            existingMetadata = null,
            currentMetadata = Fixtures.Metadata.FileOneMetadata
        )

        val sourceFileWithoutExistingMetadata =
            fileEntity

        val sourceFileWithExistingMetadata =
            sourceFileWithoutExistingMetadata.copy(existingMetadata = Fixtures.Metadata.FileOneMetadata)

        val sourceFileWithUpdatedExistingGroup =
            sourceFileWithExistingMetadata.copy(existingMetadata = Fixtures.Metadata.FileOneMetadata.copy(group = "none"))

        val sourceFileWithUpdatedExistingSize =
            sourceFileWithExistingMetadata.copy(existingMetadata = Fixtures.Metadata.FileOneMetadata.copy(size = 0))

        val sourceFileWithUpdatedExistingChecksum =
            sourceFileWithExistingMetadata.copy(
                existingMetadata = Fixtures.Metadata.FileOneMetadata.copy(checksum = BigInteger("0"))
            )

        val directoryEntity = SourceEntity(
            ref = Fixtures.Metadata.DirectoryOneMetadata.path.asRef(),
            existingMetadata = null,
            currentMetadata = Fixtures.Metadata.DirectoryOneMetadata
        )

        val sourceDirectoryWithoutExistingMetadata =
            directoryEntity

        val sourceDirectoryWithExistingMetadata =
            sourceDirectoryWithoutExistingMetadata.copy(existingMetadata = Fixtures.Metadata.DirectoryOneMetadata)

        val sourceDirectoryWithUpdatedExistingGroup =
            sourceDirectoryWithExistingMetadata.copy(
                existingMetadata = Fixtures.Metadata.DirectoryOneMetadata.copy(group = "none")
            )

        val libraryEntity = SourceEntity(
            ref = Fixtures.Metadata.LibraryOneMetadata.path.asRef(),
            existingMetadata = null,
            currentMetadata = Fixtures.Metadata.LibraryOneMetadata
        )

        val sourceLibraryWithoutExistingMetadata =
            libraryEntity

        val sourceLibraryWithExistingMetadata =
            libraryEntity.copy(existingMetadata = Fixtures.Metadata.LibraryOneMetadata)

        val sourceLibraryWithUpdatedExistingChecksum =
            sourceLibraryWithExistingMetadata.copy(
                existingMetadata = Fixtures.Metadata.LibraryOneMetadata.copy(checksum = BigInteger("0"))
            )

        val sourceLibraryWithUpdatedExistingAttributes =
            sourceLibraryWithExistingMetadata.copy(
                existingMetadata = Fixtures.Metadata.LibraryOneMetadata.copy(attributes = "favorite=false".encodeUtf8())
            )

        "fail if different entity types provided for current and existing metadata" {
            shouldThrow<IllegalArgumentException> {
                SourceEntity(
                    ref = Fixtures.Metadata.FileOneMetadata.path.asRef(),
                    existingMetadata = Fixtures.Metadata.DirectoryOneMetadata,
                    currentMetadata = Fixtures.Metadata.FileOneMetadata
                )
            }

            shouldThrow<IllegalArgumentException> {
                SourceEntity(
                    ref = Fixtures.Metadata.FileOneMetadata.path.asRef(),
                    existingMetadata = Fixtures.Metadata.FileOneMetadata,
                    currentMetadata = Fixtures.Metadata.DirectoryOneMetadata
                )
            }
        }

        "determine if its metadata has changed" {
            sourceFileWithoutExistingMetadata.hasChanged shouldBe (true)
            sourceFileWithExistingMetadata.hasChanged shouldBe (false)
            sourceFileWithUpdatedExistingGroup.hasChanged shouldBe (true)
            sourceDirectoryWithoutExistingMetadata.hasChanged shouldBe (true)
            sourceDirectoryWithExistingMetadata.hasChanged shouldBe (false)
            sourceDirectoryWithUpdatedExistingGroup.hasChanged shouldBe (true)
            sourceLibraryWithoutExistingMetadata.hasChanged shouldBe (true)
            sourceLibraryWithExistingMetadata.hasChanged shouldBe (false)
            sourceLibraryWithUpdatedExistingAttributes.hasChanged shouldBe (true)
        }

        "determine if its content has changed" {
            sourceFileWithoutExistingMetadata.hasContentChanged shouldBe (true)
            sourceFileWithExistingMetadata.hasContentChanged shouldBe (false)
            sourceFileWithUpdatedExistingSize.hasContentChanged shouldBe (true)
            sourceFileWithUpdatedExistingChecksum.hasContentChanged shouldBe (true)
            sourceDirectoryWithoutExistingMetadata.hasContentChanged shouldBe (false)
            sourceDirectoryWithExistingMetadata.hasContentChanged shouldBe (false)
            sourceDirectoryWithUpdatedExistingGroup.hasContentChanged shouldBe (false)
            sourceLibraryWithoutExistingMetadata.hasContentChanged shouldBe (true)
            sourceLibraryWithExistingMetadata.hasContentChanged shouldBe (false)
            sourceLibraryWithUpdatedExistingChecksum.hasContentChanged shouldBe (true)
        }
    }
})
