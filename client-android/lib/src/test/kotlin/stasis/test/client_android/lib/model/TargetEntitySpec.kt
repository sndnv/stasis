package stasis.test.client_android.lib.model

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import stasis.client_android.lib.model.TargetEntity
import stasis.test.client_android.lib.Fixtures
import stasis.test.client_android.lib.ResourceHelpers.asPath
import java.math.BigInteger
import java.nio.file.Paths

class TargetEntitySpec : WordSpec({
    "A TargetEntity" should {
        val targetFile = TargetEntity(
            path = Fixtures.Metadata.FileOneMetadata.path.asPath(),
            destination = TargetEntity.Destination.Default,
            existingMetadata = Fixtures.Metadata.FileOneMetadata,
            currentMetadata = null
        )

        val targetFileWithoutCurrentMetadata =
            targetFile

        val targetFileWithCurrentMetadata =
            targetFile.copy(currentMetadata = Fixtures.Metadata.FileOneMetadata)

        val targetFileWithUpdatedCurrentGroup =
            targetFile.copy(currentMetadata = Fixtures.Metadata.FileOneMetadata.copy(group = "none"))

        val targetFileWithUpdatedCurrentSize =
            targetFile.copy(currentMetadata = Fixtures.Metadata.FileOneMetadata.copy(size = 0))

        val targetFileWithUpdatedCurrentChecksum =
            targetFile.copy(currentMetadata = Fixtures.Metadata.FileOneMetadata.copy(checksum = BigInteger("0")))

        val targetDirectory = TargetEntity(
            path = Fixtures.Metadata.DirectoryOneMetadata.path.asPath(),
            destination = TargetEntity.Destination.Default,
            existingMetadata = Fixtures.Metadata.DirectoryOneMetadata,
            currentMetadata = null
        )

        val targetDirectoryWithoutCurrentMetadata =
            targetDirectory

        val targetDirectoryWithCurrentMetadata =
            targetDirectory.copy(currentMetadata = Fixtures.Metadata.DirectoryOneMetadata)

        val targetDirectoryWithUpdatedCurrentGroup =
            targetDirectory.copy(currentMetadata = Fixtures.Metadata.DirectoryOneMetadata.copy(group = "none"))

        "fail if different entity types provided for current and existing metadata" {
            shouldThrow<IllegalArgumentException> {
                targetFile.copy(
                    existingMetadata = Fixtures.Metadata.DirectoryOneMetadata,
                    currentMetadata = Fixtures.Metadata.FileOneMetadata
                )
            }

            shouldThrow<IllegalArgumentException> {
                targetFile.copy(
                    existingMetadata = Fixtures.Metadata.FileOneMetadata,
                    currentMetadata = Fixtures.Metadata.DirectoryOneMetadata
                )
            }
        }

        "determine if its metadata has changed" {
            targetFileWithoutCurrentMetadata.hasChanged shouldBe (true)
            targetFileWithCurrentMetadata.hasChanged shouldBe (false)
            targetFileWithUpdatedCurrentGroup.hasChanged shouldBe (true)
            targetDirectoryWithoutCurrentMetadata.hasChanged shouldBe (true)
            targetDirectoryWithCurrentMetadata.hasChanged shouldBe (false)
            targetDirectoryWithUpdatedCurrentGroup.hasChanged shouldBe (true)
        }

        "determine if its content has changed" {
            targetFileWithoutCurrentMetadata.hasContentChanged shouldBe (true)
            targetFileWithCurrentMetadata.hasContentChanged shouldBe (false)
            targetFileWithUpdatedCurrentSize.hasContentChanged shouldBe (true)
            targetFileWithUpdatedCurrentChecksum.hasContentChanged shouldBe (true)
            targetDirectoryWithoutCurrentMetadata.hasContentChanged shouldBe (false)
            targetDirectoryWithCurrentMetadata.hasContentChanged shouldBe (false)
            targetDirectoryWithUpdatedCurrentGroup.hasContentChanged shouldBe (false)
        }

        "provide its original file path" {
            targetFile.originalPath.toString() shouldBe (targetFile.existingMetadata.path)
        }

        "provide its destination file path" {
            val testDestinationPath = Paths.get("/tmp/destination")

            val expectedOriginalPath = targetFile.originalPath
            val expectedPathWithDefaultStructure = Paths.get("$testDestinationPath/${targetFile.path}")
            val expectedPathWithoutDefaultStructure = Paths.get("$testDestinationPath/${targetFile.path.fileName}")

            targetFile.destinationPath shouldBe (expectedOriginalPath)

            targetFile
                .copy(
                    destination = TargetEntity.Destination.Directory(path = testDestinationPath, keepDefaultStructure = true)
                )
                .destinationPath shouldBe (expectedPathWithDefaultStructure)

            targetFile
                .copy(
                    destination = TargetEntity.Destination.Directory(path = testDestinationPath, keepDefaultStructure = false)
                )
                .destinationPath shouldBe (expectedPathWithoutDefaultStructure)
        }
    }
})
