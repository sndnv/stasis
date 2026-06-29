package stasis.test.client_android.lib.model

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import okio.ByteString.Companion.encodeUtf8
import stasis.client_android.lib.model.EntityRef
import stasis.client_android.lib.model.TargetEntity
import stasis.test.client_android.lib.Fixtures
import stasis.test.client_android.lib.ResourceHelpers.asRef
import stasis.test.client_android.lib.ResourceHelpers.asPath
import java.math.BigInteger
import java.nio.file.Files
import java.nio.file.Paths

class TargetEntitySpec : WordSpec({
    "A TargetEntity" should {
        val targetFile = TargetEntity(
            ref = Fixtures.Metadata.FileOneMetadata.path.asRef(),
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
            ref = Fixtures.Metadata.DirectoryOneMetadata.path.asRef(),
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

        val targetLibrary = TargetEntity(
            ref = Fixtures.Metadata.LibraryOneMetadata.path.asRef(),
            destination = TargetEntity.Destination.Default,
            existingMetadata = Fixtures.Metadata.LibraryOneMetadata,
            currentMetadata = null
        )

        val targetLibraryWithoutCurrentMetadata =
            targetLibrary

        val targetLibraryWithCurrentMetadata =
            targetLibrary.copy(currentMetadata = Fixtures.Metadata.LibraryOneMetadata)

        val targetLibraryWithUpdatedCurrentChecksum =
            targetLibrary.copy(
                currentMetadata = Fixtures.Metadata.LibraryOneMetadata.copy(checksum = BigInteger("0"))
            )

        val targetLibraryWithUpdatedCurrentAttributes =
            targetLibrary.copy(
                currentMetadata = Fixtures.Metadata.LibraryOneMetadata.copy(attributes = "favorite=false".encodeUtf8())
            )

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
            targetLibraryWithoutCurrentMetadata.hasChanged shouldBe (true)
            targetLibraryWithCurrentMetadata.hasChanged shouldBe (false)
            targetLibraryWithUpdatedCurrentAttributes.hasChanged shouldBe (true)
        }

        "determine if its content has changed" {
            targetFileWithoutCurrentMetadata.hasContentChanged shouldBe (true)
            targetFileWithCurrentMetadata.hasContentChanged shouldBe (false)
            targetFileWithUpdatedCurrentSize.hasContentChanged shouldBe (true)
            targetFileWithUpdatedCurrentChecksum.hasContentChanged shouldBe (true)
            targetDirectoryWithoutCurrentMetadata.hasContentChanged shouldBe (false)
            targetDirectoryWithCurrentMetadata.hasContentChanged shouldBe (false)
            targetDirectoryWithUpdatedCurrentGroup.hasContentChanged shouldBe (false)
            targetLibraryWithoutCurrentMetadata.hasContentChanged shouldBe (true)
            targetLibraryWithCurrentMetadata.hasContentChanged shouldBe (false)
            targetLibraryWithUpdatedCurrentChecksum.hasContentChanged shouldBe (true)
        }

        "provide its original file path" {
            targetFile.originalRef.key shouldBe (targetFile.existingMetadata.path)
        }

        "provide its destination file path" {
            val testDestinationPath = Paths.get("/tmp/destination")
            val originalPath = Fixtures.Metadata.FileOneMetadata.path.asPath()

            val expectedPathWithDefaultStructure = Paths.get("$testDestinationPath/$originalPath")
            val expectedPathWithoutDefaultStructure = Paths.get("$testDestinationPath/${originalPath.fileName}")

            targetFile.destinationRef shouldBe (targetFile.originalRef)

            targetFile
                .copy(
                    destination = TargetEntity.Destination.Directory(
                        path = testDestinationPath,
                        keepDefaultStructure = true,
                        preserveExisting = false
                    )
                )
                .destinationRef shouldBe (EntityRef.Filesystem(expectedPathWithDefaultStructure))

            targetFile
                .copy(
                    destination = TargetEntity.Destination.Directory(
                        path = testDestinationPath,
                        keepDefaultStructure = false,
                        preserveExisting = false
                    )
                )
                .destinationRef shouldBe (EntityRef.Filesystem(expectedPathWithoutDefaultStructure))
        }

        "provide a filesystem destination for a library ref" {
            val testDestinationPath = Paths.get("/tmp/destination")

            val libraryFile = targetFile.copy(ref = EntityRef.Library(scheme = "photos", path = "/album/img.heic"))

            libraryFile
                .copy(
                    destination = TargetEntity.Destination.Directory(
                        path = testDestinationPath,
                        keepDefaultStructure = false,
                        preserveExisting = false
                    )
                )
                .destinationRef shouldBe (EntityRef.Filesystem(Paths.get("/tmp/destination/img.heic")))
        }

        "provide a non-colliding destination file path when preserving existing files" {
            val directory = Files.createTempDirectory("target-entity-preserve")
            directory.toFile().deleteOnExit()

            val fileName = Fixtures.Metadata.FileOneMetadata.path.asPath().fileName.toString()

            val entity = targetFile.copy(
                destination = TargetEntity.Destination.Directory(
                    path = directory,
                    keepDefaultStructure = false,
                    preserveExisting = true
                )
            )

            entity.destinationRef shouldBe (EntityRef.Filesystem(directory.resolve(fileName)))

            Files.createFile(directory.resolve(fileName))
            entity.copy().destinationRef shouldBe (EntityRef.Filesystem(directory.resolve("$fileName-1")))

            Files.createFile(directory.resolve("$fileName-1"))
            entity.copy().destinationRef shouldBe (EntityRef.Filesystem(directory.resolve("$fileName-2")))
        }
    }
})
