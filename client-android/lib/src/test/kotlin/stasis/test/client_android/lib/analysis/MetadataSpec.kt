package stasis.test.client_android.lib.analysis

import io.kotest.assertions.fail
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import stasis.client_android.lib.analysis.Checksum
import stasis.client_android.lib.analysis.Metadata
import stasis.client_android.lib.model.EntityMetadata
import stasis.client_android.lib.model.TargetEntity
import stasis.test.client_android.lib.Fixtures
import stasis.test.client_android.lib.ResourceHelpers.asTestResource
import stasis.test.client_android.lib.mocks.MockCompression
import java.math.BigInteger
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Paths
import java.nio.file.attribute.PosixFileAttributes
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class MetadataSpec : WordSpec({
    "A Metadata implementation" should {
        "extract base metadata from a file" {
            val sourceFileResourcesPath = "analysis/metadata-source-file"
            val sourceFile = "/$sourceFileResourcesPath".asTestResource()

            val actualMetadata = Metadata.extractBaseEntityMetadata(entity = sourceFile)
            actualMetadata.path.endsWith(sourceFileResourcesPath) shouldBe (true)
            actualMetadata.isDirectory shouldBe (false)
            actualMetadata.link shouldBe (null)
            actualMetadata.isHidden shouldBe (false)
            actualMetadata.updated shouldBeGreaterThan (Instant.MIN)
            actualMetadata.owner.isNotEmpty() shouldBe (true)
            actualMetadata.group.isNotEmpty() shouldBe (true)
            actualMetadata.permissions.isNotEmpty() shouldBe (true)
        }

        "extract base metadata from a directory" {
            val sourceDirectoryResourcesPath = "analysis/"
            val sourceDirectory = "/$sourceDirectoryResourcesPath".asTestResource()

            val actualMetadata = Metadata.extractBaseEntityMetadata(entity = sourceDirectory)
            actualMetadata.path.endsWith(sourceDirectoryResourcesPath) shouldBe (true)
            actualMetadata.isDirectory shouldBe (true)
            actualMetadata.link shouldBe (null)
            actualMetadata.isHidden shouldBe (false)
            actualMetadata.updated shouldBeGreaterThan (Instant.MIN)
            actualMetadata.owner.isNotEmpty() shouldBe (true)
            actualMetadata.group.isNotEmpty() shouldBe (true)
            actualMetadata.permissions.isNotEmpty() shouldBe (true)
        }

        "collect file crate ID (source file / existing metadata / matching checksum)" {
            val crates = Metadata.collectCratesForSourceFile(
                existingMetadata = Fixtures.Metadata.FileOneMetadata,
                currentChecksum = Fixtures.Metadata.FileOneMetadata.checksum
            )
            crates shouldBe (Fixtures.Metadata.FileOneMetadata.crates)
        }

        "collect file crate ID (source file / existing metadata / mismatching checksum)" {
            val crates = Metadata.collectCratesForSourceFile(
                existingMetadata = Fixtures.Metadata.FileOneMetadata,
                currentChecksum = BigInteger("42")
            )
            crates shouldNotBe (Fixtures.Metadata.FileOneMetadata.crates)
        }

        "collect file crate ID (source file / missing metadata)" {
            val crates = Metadata.collectCratesForSourceFile(
                existingMetadata = null,
                currentChecksum = Fixtures.Metadata.FileOneMetadata.checksum
            )
            crates shouldNotBe (Fixtures.Metadata.FileOneMetadata.crates)
        }

        "fail to collect file crate ID with directory metadata (source file)" {
            val e = shouldThrow<IllegalArgumentException> {
                Metadata.collectCratesForSourceFile(
                    existingMetadata = Fixtures.Metadata.DirectoryOneMetadata,
                    currentChecksum = Fixtures.Metadata.FileOneMetadata.checksum
                )
            }

            e.message shouldBe (
                    "Expected metadata for file but directory metadata for " +
                            "[${Fixtures.Metadata.DirectoryOneMetadata.path}] provided"
                    )
        }

        "collect file crate ID (target file)" {
            val crates = Metadata.collectCratesForTargetFile(
                existingMetadata = Fixtures.Metadata.FileOneMetadata
            )
            crates shouldBe (Fixtures.Metadata.FileOneMetadata.crates)
        }

        "fail to collect file crate ID with directory metadata (target file)" {
            val e = shouldThrow<IllegalArgumentException> {
                Metadata.collectCratesForTargetFile(
                    existingMetadata = Fixtures.Metadata.DirectoryOneMetadata
                )
            }

            e.message shouldBe (
                    "Expected metadata for file but directory metadata for " +
                            "[${Fixtures.Metadata.DirectoryOneMetadata.path}] provided"
                    )
        }

        "collect file compression (target file)" {
            val compression = Metadata.collectCompressionForTargetFile(
                existingMetadata = Fixtures.Metadata.FileTwoMetadata
            )

            compression shouldBe (Fixtures.Metadata.FileTwoMetadata.compression)
        }

        "fail to collect file compression with directory metadata (target file)" {
            val e = shouldThrow<IllegalArgumentException> {
                Metadata.collectCompressionForTargetFile(
                    existingMetadata = Fixtures.Metadata.DirectoryOneMetadata
                )
            }

            e.message shouldBe (
                    "Expected metadata for file but directory metadata for " +
                            "[${Fixtures.Metadata.DirectoryOneMetadata.path}] provided"
                    )
        }

        "extract metadata from a file" {
            val sourceFileResourcesPath = "analysis/metadata-source-file"
            val sourceFile = "/$sourceFileResourcesPath".asTestResource()

            val expectedCratePart = Paths.get("${sourceFile}_0")
            val expectedCrateId = java.util.UUID.fromString("329efbeb-80a3-42b8-b1dc-79bc0fea7bca")
            val expectedChecksum = BigInteger("338496524657487844672953225842489206917")

            val baseMetadata = Metadata.extractBaseEntityMetadata(
                entity = sourceFile
            )

            val actualFileMetadata = Metadata.collectEntityMetadata(
                currentMetadata = baseMetadata,
                checksum = Checksum.Companion.MD5,
                collectCrates = { mapOf(expectedCratePart to expectedCrateId) },
                collectCompression = { "test" }
            )

            when (actualFileMetadata) {
                is EntityMetadata.File -> {
                    actualFileMetadata.path.endsWith(sourceFileResourcesPath) shouldBe (true)
                    actualFileMetadata.link shouldBe (null)
                    actualFileMetadata.isHidden shouldBe (false)
                    actualFileMetadata.updated shouldBeGreaterThan (Instant.MIN)
                    actualFileMetadata.owner.isNotEmpty() shouldBe (true)
                    actualFileMetadata.group.isNotEmpty() shouldBe (true)
                    actualFileMetadata.permissions.isNotEmpty() shouldBe (true)

                    actualFileMetadata.size shouldBe (26)
                    actualFileMetadata.checksum shouldBe (expectedChecksum)
                    actualFileMetadata.crates shouldBe (mapOf(expectedCratePart to expectedCrateId))
                    actualFileMetadata.compression shouldBe ("test")
                }

                is EntityMetadata.Directory -> {
                    fail("Expected file but received directory metadata")
                }
            }
        }

        "extract metadata from a directory" {
            val sourceDirectoryResourcesPath = "analysis/"
            val sourceDirectory = "/$sourceDirectoryResourcesPath".asTestResource()

            val baseMetadata = Metadata.extractBaseEntityMetadata(
                entity = sourceDirectory
            )
            val actualDirectoryMetadata = Metadata.collectEntityMetadata(
                currentMetadata = baseMetadata,
                checksum = Checksum.Companion.MD5,
                collectCrates = { emptyMap() },
                collectCompression = { "test" }
            )
            when (actualDirectoryMetadata) {
                is EntityMetadata.Directory -> {
                    actualDirectoryMetadata.path.endsWith(sourceDirectoryResourcesPath) shouldBe (true)
                    actualDirectoryMetadata.link shouldBe (null)
                    actualDirectoryMetadata.isHidden shouldBe (false)
                    actualDirectoryMetadata.updated shouldBeGreaterThan (Instant.MIN)
                    actualDirectoryMetadata.owner.isNotEmpty() shouldBe (true)
                    actualDirectoryMetadata.group.isNotEmpty() shouldBe (true)
                    actualDirectoryMetadata.permissions.isNotEmpty() shouldBe (true)
                }

                is EntityMetadata.File -> {
                    fail("Expected directory but received file metadata")
                }
            }
        }

        "apply metadata to a file" {
            val targetFile = Files.createTempFile("metadata-target-file", "")
            targetFile.toFile().deleteOnExit()

            val attributes =
                Files.readAttributes(targetFile, PosixFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)

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

            val metadataBeforeApplication = Metadata.extractBaseEntityMetadata(entity = targetFile)
            Metadata.applyEntityMetadataTo(metadata = metadata, entity = metadata.path)

            val metadataAfterApplication = Metadata.extractBaseEntityMetadata(entity = targetFile)

            metadataBeforeApplication.permissions shouldNotBe (metadata.permissions)
            metadataBeforeApplication.updated shouldNotBe (metadata.updated)

            metadataAfterApplication.owner shouldBe (metadata.owner)
            metadataAfterApplication.group shouldBe (metadata.group)
            metadataAfterApplication.permissions shouldBe (metadata.permissions)
            metadataAfterApplication.updated shouldBe (metadata.updated)
        }

        "apply metadata to a directory" {
            val targetDirectory = Files.createTempDirectory("metadata-target-directory")
            targetDirectory.toFile().deleteOnExit()

            val attributes =
                Files.readAttributes(targetDirectory, PosixFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)

            val metadata = EntityMetadata.Directory(
                path = targetDirectory,
                link = null,
                isHidden = false,
                created = Instant.parse("2020-01-01T00:00:00Z"),
                updated = Instant.parse("2020-01-03T00:00:00Z"),
                owner = attributes.owner().name,
                group = attributes.group().name,
                permissions = "rwxrwxrwx"
            )

            val metadataBeforeApplication = Metadata.extractBaseEntityMetadata(entity = targetDirectory)
            Metadata.applyEntityMetadataTo(metadata = metadata, entity = metadata.path)

            val metadataAfterApplication = Metadata.extractBaseEntityMetadata(entity = targetDirectory)

            metadataBeforeApplication.permissions shouldNotBe (metadata.permissions)
            metadataBeforeApplication.updated shouldNotBe (metadata.updated)

            metadataAfterApplication.owner shouldBe (metadata.owner)
            metadataAfterApplication.group shouldBe (metadata.group)
            metadataAfterApplication.permissions shouldBe (metadata.permissions)
            metadataAfterApplication.updated shouldBe (metadata.updated)
        }

        "collect metadata for source files with same content" {
            val sourceFileResourcesPath = "analysis/metadata-source-file"
            val sourceFile = "/$sourceFileResourcesPath".asTestResource()

            val existingFileMetadata = EntityMetadata.File(
                path = sourceFile,
                size = 1,
                link = null,
                isHidden = false,
                created = Instant.MIN.truncatedTo(ChronoUnit.SECONDS),
                updated = Instant.MAX.truncatedTo(ChronoUnit.SECONDS),
                owner = "root",
                group = "root",
                permissions = "rwxrwxrwx",
                checksum = BigInteger("338496524657487844672953225842489206917"),
                crates = mapOf(Paths.get("${sourceFile}_0") to UUID.randomUUID()),
                compression = "none"
            )

            val actualSourceFile = Metadata.collectSource(
                checksum = Checksum.Companion.MD5,
                compression = MockCompression(),
                entity = sourceFile,
                existingMetadata = existingFileMetadata
            )
            actualSourceFile.existingMetadata shouldNotBe (null)

            when (val metadata = actualSourceFile.currentMetadata) {
                is EntityMetadata.File -> {
                    metadata.path.endsWith(sourceFileResourcesPath) shouldBe (true)
                    metadata.size shouldBe (26)
                    metadata.link shouldBe (null)
                    metadata.isHidden shouldBe (false)
                    metadata.updated shouldBeGreaterThan (Instant.MIN)
                    metadata.owner.isNotEmpty() shouldBe (true)
                    metadata.group.isNotEmpty() shouldBe (true)
                    metadata.permissions.isNotEmpty() shouldBe (true)
                    metadata.checksum shouldBe (existingFileMetadata.checksum)
                    metadata.crates shouldBe (existingFileMetadata.crates)
                }

                is EntityMetadata.Directory -> {
                    fail("Expected file but received directory metadata")
                }
            }
        }

        "collect metadata for source files with updated content" {
            val sourceFileResourcesPath = "analysis/metadata-source-file"
            val sourceFile = "/$sourceFileResourcesPath".asTestResource()

            val existingFileMetadata = EntityMetadata.File(
                path = sourceFile,
                size = 1,
                link = null,
                isHidden = false,
                created = Instant.MIN.truncatedTo(ChronoUnit.SECONDS),
                updated = Instant.MAX.truncatedTo(ChronoUnit.SECONDS),
                owner = "root",
                group = "root",
                permissions = "rwxrwxrwx",
                checksum = BigInteger("1"),
                crates = mapOf(Paths.get("${sourceFile}_0") to UUID.randomUUID()),
                compression = "none"
            )

            val expectedChecksum = BigInteger("338496524657487844672953225842489206917")

            val actualSourceFile = Metadata.collectSource(
                checksum = Checksum.Companion.MD5,
                compression = MockCompression(),
                entity = sourceFile,
                existingMetadata = existingFileMetadata
            )
            actualSourceFile.existingMetadata shouldNotBe (null)

            when (val metadata = actualSourceFile.currentMetadata) {
                is EntityMetadata.File -> {
                    metadata.path.endsWith(sourceFileResourcesPath) shouldBe (true)
                    metadata.size shouldBe (26)
                    metadata.link shouldBe (null)
                    metadata.isHidden shouldBe (false)
                    metadata.updated shouldBeGreaterThan (Instant.MIN)
                    metadata.owner.isNotEmpty() shouldBe (true)
                    metadata.group.isNotEmpty() shouldBe (true)
                    metadata.permissions.isNotEmpty() shouldBe (true)
                    metadata.checksum shouldBe (expectedChecksum)
                    metadata.crates shouldNotBe (existingFileMetadata.crates)
                }

                is EntityMetadata.Directory -> {
                    fail("Expected file but received directory metadata")
                }
            }
        }

        "collect metadata for existing target files" {
            val targetFileResourcesPath = "analysis/metadata-source-file"
            val targetFile = "/$targetFileResourcesPath".asTestResource()

            val existingFileMetadata = EntityMetadata.File(
                path = targetFile,
                size = 1,
                link = null,
                isHidden = false,
                created = Instant.MIN.truncatedTo(ChronoUnit.SECONDS),
                updated = Instant.MAX.truncatedTo(ChronoUnit.SECONDS),
                owner = "root",
                group = "root",
                permissions = "rwxrwxrwx",
                checksum = BigInteger("1"),
                crates = mapOf(Paths.get("${targetFile}_0") to UUID.randomUUID()),
                compression = "none"
            )

            val expectedChecksum = BigInteger("338496524657487844672953225842489206917")

            val actualTargetFile = Metadata.collectTarget(
                checksum = Checksum.Companion.MD5,
                entity = targetFile,
                destination = TargetEntity.Destination.Default,
                existingMetadata = existingFileMetadata
            )
            actualTargetFile.existingMetadata shouldBe (existingFileMetadata)

            when (val currentMetadata = actualTargetFile.currentMetadata) {
                is EntityMetadata.File -> {
                    currentMetadata.path.endsWith(targetFileResourcesPath) shouldBe (true)
                    currentMetadata.size shouldBe (26)
                    currentMetadata.link shouldBe (null)
                    currentMetadata.isHidden shouldBe (false)
                    currentMetadata.updated shouldBeGreaterThan (Instant.MIN)
                    currentMetadata.owner.isNotEmpty() shouldBe (true)
                    currentMetadata.group.isNotEmpty() shouldBe (true)
                    currentMetadata.permissions.isNotEmpty() shouldBe (true)
                    currentMetadata.checksum shouldBe (expectedChecksum)
                    currentMetadata.crates shouldBe (existingFileMetadata.crates)
                }

                is EntityMetadata.Directory -> {
                    fail("Expected file but received directory metadata")
                }

                null -> {
                    fail("Expected current target file metadata but none was found")
                }
            }
        }

        "collect metadata for missing target files" {
            val targetFileResourcesPath = "analysis/metadata-missing-file"
            val targetFile = Paths.get("/tmp/$targetFileResourcesPath")

            val existingFileMetadata = EntityMetadata.File(
                path = targetFile,
                size = 1,
                link = null,
                isHidden = false,
                created = Instant.MIN.truncatedTo(ChronoUnit.SECONDS),
                updated = Instant.MAX.truncatedTo(ChronoUnit.SECONDS),
                owner = "root",
                group = "root",
                permissions = "rwxrwxrwx",
                checksum = BigInteger("1"),
                crates = mapOf(Paths.get("${targetFile}_0") to UUID.randomUUID()),
                compression = "none"
            )

            val actualTargetFile = Metadata.collectTarget(
                checksum = Checksum.Companion.MD5,
                entity = targetFile,
                destination = TargetEntity.Destination.Default,
                existingMetadata = existingFileMetadata
            )

            actualTargetFile.existingMetadata shouldBe (existingFileMetadata)
            actualTargetFile.currentMetadata shouldBe (null)
        }
    }
})
