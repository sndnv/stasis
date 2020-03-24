package stasis.test.specs.unit.client.analysis

import java.nio.file.attribute.PosixFileAttributes
import java.nio.file.{Files, LinkOption, Paths}
import java.time.Instant
import java.time.temporal.ChronoUnit

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import stasis.client.analysis.{Checksum, Metadata}
import stasis.client.model.FileMetadata
import stasis.core.packaging.Crate
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.ResourceHelpers

class MetadataSpec extends AsyncUnitSpec with ResourceHelpers {
  private implicit val system: ActorSystem = ActorSystem(name = "MetadataSpec")
  private implicit val mat: ActorMaterializer = ActorMaterializer()

  "A Metadata implementation" should "extract metadata from a file" in {
    val sourceFileResourcesPath = "analysis/metadata-source-file"
    val sourceFile = s"/$sourceFileResourcesPath".asTestResource

    val expectedCrateId = java.util.UUID.fromString("329efbeb-80a3-42b8-b1dc-79bc0fea7bca")
    val expectedChecksum = BigInt(42)

    Metadata
      .extractFileMetadata(
        file = sourceFile,
        withChecksum = expectedChecksum,
        withCrate = expectedCrateId
      )
      .map { actualFileMetadata =>
        actualFileMetadata.path.endsWith(sourceFileResourcesPath) should be(true)
        actualFileMetadata.size should be(26)
        actualFileMetadata.link should be(None)
        actualFileMetadata.isHidden should be(false)
        actualFileMetadata.updated should be > Instant.MIN
        actualFileMetadata.owner should not be empty
        actualFileMetadata.group should not be empty
        actualFileMetadata.permissions should not be empty
        actualFileMetadata.checksum should be(expectedChecksum)
        actualFileMetadata.crate should be(expectedCrateId)
      }
  }

  it should "apply metadata to a file" in {
    val targetFile = Files.createTempFile("metadata-target-file", "")
    targetFile.toFile.deleteOnExit()

    val attributes = Files.readAttributes(targetFile, classOf[PosixFileAttributes], LinkOption.NOFOLLOW_LINKS)

    val metadata = FileMetadata(
      path = targetFile,
      size = 1,
      link = None,
      isHidden = false,
      created = Instant.parse("2020-01-01T00:00:00Z"),
      updated = Instant.parse("2020-01-03T00:00:00Z"),
      owner = attributes.owner().getName,
      group = attributes.group().getName,
      permissions = "rwxrwxrwx",
      checksum = BigInt(1),
      crate = Crate.generateId()
    )

    for {
      metadataBeforeApplication <- Metadata
        .extractFileMetadata(
          file = targetFile,
          withChecksum = BigInt(1),
          withCrate = Crate.generateId()
        )
      _ <- Metadata.applyFileMetadata(metadata)
      metadataAfterApplication <- Metadata
        .extractFileMetadata(
          file = targetFile,
          withChecksum = BigInt(1),
          withCrate = Crate.generateId()
        )
    } yield {
      metadataBeforeApplication.permissions should not be metadata.permissions
      metadataBeforeApplication.updated should not be metadata.updated

      metadataAfterApplication.owner should be(metadata.owner)
      metadataAfterApplication.group should be(metadata.group)
      metadataAfterApplication.permissions should be(metadata.permissions)
      metadataAfterApplication.updated should be(metadata.updated)
    }
  }

  it should "collect metadata for source files with same content" in {
    val sourceFileResourcesPath = "analysis/metadata-source-file"
    val sourceFile = s"/$sourceFileResourcesPath".asTestResource

    val existingFileMetadata = FileMetadata(
      path = sourceFile,
      size = 1,
      link = None,
      isHidden = false,
      created = Instant.MIN.truncatedTo(ChronoUnit.SECONDS),
      updated = Instant.MAX.truncatedTo(ChronoUnit.SECONDS),
      owner = "root",
      group = "root",
      permissions = "rwxrwxrwx",
      checksum = BigInt("338496524657487844672953225842489206917"),
      crate = Crate.generateId()
    )

    Metadata
      .collectSource(
        checksum = Checksum.MD5,
        file = sourceFile,
        existingMetadata = Some(existingFileMetadata)
      )
      .map { actualSourceFile =>
        actualSourceFile.existingMetadata should not be empty

        actualSourceFile.currentMetadata.path.endsWith(sourceFileResourcesPath) should be(true)
        actualSourceFile.currentMetadata.size should be(26)
        actualSourceFile.currentMetadata.link should be(None)
        actualSourceFile.currentMetadata.isHidden should be(false)
        actualSourceFile.currentMetadata.updated should be > Instant.MIN
        actualSourceFile.currentMetadata.owner should not be empty
        actualSourceFile.currentMetadata.group should not be empty
        actualSourceFile.currentMetadata.permissions should not be empty
        actualSourceFile.currentMetadata.checksum should be(existingFileMetadata.checksum)
        actualSourceFile.currentMetadata.crate should be(existingFileMetadata.crate)
      }
  }

  it should "collect metadata for source files with updated content" in {
    val sourceFileResourcesPath = "analysis/metadata-source-file"
    val sourceFile = s"/$sourceFileResourcesPath".asTestResource

    val existingFileMetadata = FileMetadata(
      path = sourceFile,
      size = 1,
      link = None,
      isHidden = false,
      created = Instant.MIN.truncatedTo(ChronoUnit.SECONDS),
      updated = Instant.MAX.truncatedTo(ChronoUnit.SECONDS),
      owner = "root",
      group = "root",
      permissions = "rwxrwxrwx",
      checksum = BigInt(1),
      crate = Crate.generateId()
    )

    val expectedChecksum = BigInt("338496524657487844672953225842489206917")

    Metadata
      .collectSource(
        checksum = Checksum.MD5,
        file = sourceFile,
        existingMetadata = Some(existingFileMetadata)
      )
      .map { actualSourceFile =>
        actualSourceFile.existingMetadata should not be empty

        actualSourceFile.currentMetadata.path.endsWith(sourceFileResourcesPath) should be(true)
        actualSourceFile.currentMetadata.size should be(26)
        actualSourceFile.currentMetadata.link should be(None)
        actualSourceFile.currentMetadata.isHidden should be(false)
        actualSourceFile.currentMetadata.updated should be > Instant.MIN
        actualSourceFile.currentMetadata.owner should not be empty
        actualSourceFile.currentMetadata.group should not be empty
        actualSourceFile.currentMetadata.permissions should not be empty
        actualSourceFile.currentMetadata.checksum should be(expectedChecksum)
        actualSourceFile.currentMetadata.crate should not be existingFileMetadata.crate
      }
  }

  it should "collect metadata for existing target files" in {
    val targetFileResourcesPath = "analysis/metadata-source-file"
    val targetFile = s"/$targetFileResourcesPath".asTestResource

    val existingFileMetadata = FileMetadata(
      path = targetFile,
      size = 1,
      link = None,
      isHidden = false,
      created = Instant.MIN.truncatedTo(ChronoUnit.SECONDS),
      updated = Instant.MAX.truncatedTo(ChronoUnit.SECONDS),
      owner = "root",
      group = "root",
      permissions = "rwxrwxrwx",
      checksum = BigInt(1),
      crate = Crate.generateId()
    )

    val expectedChecksum = BigInt("338496524657487844672953225842489206917")

    Metadata
      .collectTarget(
        checksum = Checksum.MD5,
        file = targetFile,
        existingMetadata = existingFileMetadata
      )
      .map { actualTargetFile =>
        actualTargetFile.existingMetadata should be(existingFileMetadata)

        actualTargetFile.currentMetadata match {
          case Some(currentMetadata) =>
            currentMetadata.path.endsWith(targetFileResourcesPath) should be(true)
            currentMetadata.size should be(26)
            currentMetadata.link should be(None)
            currentMetadata.isHidden should be(false)
            currentMetadata.updated should be > Instant.MIN
            currentMetadata.owner should not be empty
            currentMetadata.group should not be empty
            currentMetadata.permissions should not be empty
            currentMetadata.checksum should be(expectedChecksum)
            currentMetadata.crate should be(existingFileMetadata.crate)

          case None =>
            fail("Expected current target file metadata but none was found")
        }
      }
  }

  it should "collect metadata for missing target files" in {
    val targetFileResourcesPath = "analysis/metadata-missing-file"
    val targetFile = Paths.get(s"/tmp/$targetFileResourcesPath")

    val existingFileMetadata = FileMetadata(
      path = targetFile,
      size = 1,
      link = None,
      isHidden = false,
      created = Instant.MIN.truncatedTo(ChronoUnit.SECONDS),
      updated = Instant.MAX.truncatedTo(ChronoUnit.SECONDS),
      owner = "root",
      group = "root",
      permissions = "rwxrwxrwx",
      checksum = BigInt(1),
      crate = Crate.generateId()
    )

    Metadata
      .collectTarget(
        checksum = Checksum.MD5,
        file = targetFile,
        existingMetadata = existingFileMetadata
      )
      .map { actualTargetFile =>
        actualTargetFile.existingMetadata should be(existingFileMetadata)
        actualTargetFile.currentMetadata should be(None)
      }
  }
}
