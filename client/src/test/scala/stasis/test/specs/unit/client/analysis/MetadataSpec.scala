package stasis.test.specs.unit.client.analysis

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Paths
import java.nio.file.attribute.PosixFileAttributes
import java.time.Instant
import java.time.temporal.ChronoUnit

import scala.concurrent.Future
import scala.util.control.NonFatal

import org.apache.pekko.actor.ActorSystem

import stasis.client.analysis.Checksum
import stasis.client.analysis.Metadata
import stasis.client.model.EntityMetadata
import stasis.client.model.TargetEntity
import stasis.core.packaging.Crate
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.Fixtures
import stasis.test.specs.unit.client.ResourceHelpers
import stasis.test.specs.unit.client.mocks.MockCompression

class MetadataSpec extends AsyncUnitSpec with ResourceHelpers {
  "A Metadata implementation" should "extract base metadata from a file" in {
    val sourceFileResourcesPath = "analysis/metadata-source-file"
    val sourceFile = s"/$sourceFileResourcesPath".asTestResource

    Metadata
      .extractBaseEntityMetadata(
        entity = sourceFile
      )
      .map { actualFileMetadata =>
        actualFileMetadata.path.endsWith(sourceFileResourcesPath) should be(true)
        actualFileMetadata.isDirectory should be(false)
        actualFileMetadata.link should be(None)
        actualFileMetadata.isHidden should be(false)
        actualFileMetadata.updated should be > Instant.MIN
        actualFileMetadata.owner should not be empty
        actualFileMetadata.group should not be empty
        actualFileMetadata.permissions should not be empty
      }
  }

  it should "extract base metadata from a directory" in {
    val sourceDirectoryResourcesPath = "analysis/"
    val sourceDirectory = s"/$sourceDirectoryResourcesPath".asTestResource

    Metadata
      .extractBaseEntityMetadata(
        entity = sourceDirectory
      )
      .map { actualFileMetadata =>
        actualFileMetadata.path.endsWith(sourceDirectoryResourcesPath) should be(true)
        actualFileMetadata.isDirectory should be(true)
        actualFileMetadata.link should be(None)
        actualFileMetadata.isHidden should be(false)
        actualFileMetadata.updated should be > Instant.MIN
        actualFileMetadata.owner should not be empty
        actualFileMetadata.group should not be empty
        actualFileMetadata.permissions should not be empty
      }
  }

  it should "collect file crate ID (source file / existing metadata / matching checksum)" in {
    Metadata
      .collectCratesForSourceFile(
        existingMetadata = Some(Fixtures.Metadata.FileOneMetadata),
        currentChecksum = Fixtures.Metadata.FileOneMetadata.checksum
      )
      .map { crates =>
        crates should be(Fixtures.Metadata.FileOneMetadata.crates)
      }
  }

  it should "collect file crate ID (source file / existing metadata / mismatching checksum)" in {
    Metadata
      .collectCratesForSourceFile(
        existingMetadata = Some(Fixtures.Metadata.FileOneMetadata),
        currentChecksum = BigInt(42)
      )
      .map { crates =>
        crates should not be Fixtures.Metadata.FileOneMetadata.crates
      }
  }

  it should "collect file crate ID (source file / missing metadata)" in {
    Metadata
      .collectCratesForSourceFile(
        existingMetadata = None,
        currentChecksum = Fixtures.Metadata.FileOneMetadata.checksum
      )
      .map { crates =>
        crates should not be Fixtures.Metadata.FileOneMetadata.crates
      }
  }

  it should "fail to collect file crate ID with directory metadata (source file)" in {
    Metadata
      .collectCratesForSourceFile(
        existingMetadata = Some(Fixtures.Metadata.DirectoryOneMetadata),
        currentChecksum = Fixtures.Metadata.FileOneMetadata.checksum
      )
      .map { result =>
        fail(s"Unexpected result received: [$result]")
      }
      .recover { case NonFatal(e: IllegalArgumentException) =>
        e.getMessage should be(
          s"Expected metadata for file but directory metadata for [${Fixtures.Metadata.DirectoryOneMetadata.path}] provided"
        )
      }
  }

  it should "collect file crate ID (target file)" in {
    Metadata
      .collectCratesForTargetFile(
        existingMetadata = Fixtures.Metadata.FileOneMetadata
      )
      .map { crates =>
        crates should be(Fixtures.Metadata.FileOneMetadata.crates)
      }
  }

  it should "fail to collect file crate ID with directory metadata (target file)" in {
    Metadata
      .collectCratesForTargetFile(
        existingMetadata = Fixtures.Metadata.DirectoryOneMetadata
      )
      .map { result =>
        fail(s"Unexpected result received: [$result]")
      }
      .recover { case NonFatal(e: IllegalArgumentException) =>
        e.getMessage should be(
          s"Expected metadata for file but directory metadata for [${Fixtures.Metadata.DirectoryOneMetadata.path}] provided"
        )
      }
  }

  it should "collect file compression (target file)" in {
    Metadata
      .collectCompressionForTargetFile(
        existingMetadata = Fixtures.Metadata.FileTwoMetadata
      )
      .map { compression =>
        compression should be(Fixtures.Metadata.FileTwoMetadata.compression)
      }
  }

  it should "fail to collect file compression with directory metadata (target file)" in {
    Metadata
      .collectCompressionForTargetFile(
        existingMetadata = Fixtures.Metadata.DirectoryOneMetadata
      )
      .map { result =>
        fail(s"Unexpected result received: [$result]")
      }
      .recover { case NonFatal(e: IllegalArgumentException) =>
        e.getMessage should be(
          s"Expected metadata for file but directory metadata for [${Fixtures.Metadata.DirectoryOneMetadata.path}] provided"
        )
      }
  }

  it should "extract metadata from a file" in {
    val sourceFileResourcesPath = "analysis/metadata-source-file"
    val sourceFile = s"/$sourceFileResourcesPath".asTestResource

    val expectedCratePart = Paths.get(s"${sourceFile}_0")
    val expectedCrateId = java.util.UUID.fromString("329efbeb-80a3-42b8-b1dc-79bc0fea7bca")
    val expectedChecksum = BigInt("338496524657487844672953225842489206917")

    for {
      baseMetadata <- Metadata.extractBaseEntityMetadata(
        entity = sourceFile
      )
      actualFileMetadata <- Metadata.collectEntityMetadata(
        currentMetadata = baseMetadata,
        checksum = Checksum.MD5,
        collectCrates = _ => Future.successful(Map(expectedCratePart -> expectedCrateId)),
        collectCompression = () => Future.successful("test")
      )
    } yield {
      actualFileMetadata match {
        case metadata: EntityMetadata.File =>
          metadata.path.endsWith(sourceFileResourcesPath) should be(true)
          metadata.link should be(None)
          metadata.isHidden should be(false)
          metadata.updated should be > Instant.MIN
          metadata.owner should not be empty
          metadata.group should not be empty
          metadata.permissions should not be empty

          metadata.size should be(26)
          metadata.checksum should be(expectedChecksum)
          metadata.crates should be(Map(expectedCratePart -> expectedCrateId))
          metadata.compression should be("test")

        case _: EntityMetadata.Directory =>
          fail("Expected file but received directory metadata")
      }
    }
  }

  it should "extract metadata from a directory" in {
    val sourceDirectoryResourcesPath = "analysis/"
    val sourceDirectory = s"/$sourceDirectoryResourcesPath".asTestResource

    for {
      baseMetadata <- Metadata.extractBaseEntityMetadata(
        entity = sourceDirectory
      )
      actualDirectoryMetadata <- Metadata.collectEntityMetadata(
        currentMetadata = baseMetadata,
        checksum = Checksum.MD5,
        collectCrates = _ => Future.failed(new IllegalStateException("Not available")),
        collectCompression = () => Future.failed(new IllegalStateException("Not available"))
      )
    } yield {
      actualDirectoryMetadata match {
        case metadata: EntityMetadata.Directory =>
          metadata.path.endsWith(sourceDirectoryResourcesPath) should be(true)
          metadata.link should be(None)
          metadata.isHidden should be(false)
          metadata.updated should be > Instant.MIN
          metadata.owner should not be empty
          metadata.group should not be empty
          metadata.permissions should not be empty

        case _: EntityMetadata.File =>
          fail("Expected directory but received file metadata")
      }
    }
  }

  it should "apply metadata to a file" in {
    val targetFile = Files.createTempFile("metadata-target-file", "")
    targetFile.toFile.deleteOnExit()

    val attributes = Files.readAttributes(targetFile, classOf[PosixFileAttributes], LinkOption.NOFOLLOW_LINKS)

    val metadata = EntityMetadata.File(
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
      crates = Map(Paths.get(s"${targetFile}_0") -> Crate.generateId()),
      compression = "none"
    )

    for {
      metadataBeforeApplication <- Metadata.extractBaseEntityMetadata(entity = targetFile)
      _ <- Metadata.applyEntityMetadataTo(metadata = metadata, entity = metadata.path)
      metadataAfterApplication <- Metadata.extractBaseEntityMetadata(entity = targetFile)
    } yield {
      metadataBeforeApplication.permissions should not be metadata.permissions
      metadataBeforeApplication.updated should not be metadata.updated

      metadataAfterApplication.owner should be(metadata.owner)
      metadataAfterApplication.group should be(metadata.group)
      metadataAfterApplication.permissions should be(metadata.permissions)
      metadataAfterApplication.updated should be(metadata.updated)
    }
  }

  it should "apply metadata to a directory" in {
    val targetDirectory = Files.createTempDirectory("metadata-target-directory")
    targetDirectory.toFile.deleteOnExit()

    val attributes = Files.readAttributes(targetDirectory, classOf[PosixFileAttributes], LinkOption.NOFOLLOW_LINKS)

    val metadata = EntityMetadata.Directory(
      path = targetDirectory,
      link = None,
      isHidden = false,
      created = Instant.parse("2020-01-01T00:00:00Z"),
      updated = Instant.parse("2020-01-03T00:00:00Z"),
      owner = attributes.owner().getName,
      group = attributes.group().getName,
      permissions = "rwxrwxrwx"
    )

    for {
      metadataBeforeApplication <- Metadata.extractBaseEntityMetadata(entity = targetDirectory)
      _ <- Metadata.applyEntityMetadataTo(metadata = metadata, entity = metadata.path)
      metadataAfterApplication <- Metadata.extractBaseEntityMetadata(entity = targetDirectory)
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

    val existingFileMetadata = EntityMetadata.File(
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
      crates = Map(Paths.get(s"${sourceFile}_0") -> Crate.generateId()),
      compression = "none"
    )

    Metadata
      .collectSource(
        checksum = Checksum.MD5,
        entity = sourceFile,
        existingMetadata = Some(existingFileMetadata),
        compression = MockCompression()
      )
      .map { actualSourceFile =>
        actualSourceFile.existingMetadata should not be empty

        actualSourceFile.currentMetadata match {
          case metadata: EntityMetadata.File =>
            metadata.path.endsWith(sourceFileResourcesPath) should be(true)
            metadata.size should be(26)
            metadata.link should be(None)
            metadata.isHidden should be(false)
            metadata.updated should be > Instant.MIN
            metadata.owner should not be empty
            metadata.group should not be empty
            metadata.permissions should not be empty
            metadata.checksum should be(existingFileMetadata.checksum)
            metadata.crates should be(existingFileMetadata.crates)
            metadata.compression should be("mock")

          case _: EntityMetadata.Directory =>
            fail("Expected file but received directory metadata")
        }
      }
  }

  it should "collect metadata for source files with updated content" in {
    val sourceFileResourcesPath = "analysis/metadata-source-file"
    val sourceFile = s"/$sourceFileResourcesPath".asTestResource

    val existingFileMetadata = EntityMetadata.File(
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
      crates = Map(Paths.get(s"${sourceFile}_0") -> Crate.generateId()),
      compression = "none"
    )

    val expectedChecksum = BigInt("338496524657487844672953225842489206917")

    Metadata
      .collectSource(
        checksum = Checksum.MD5,
        entity = sourceFile,
        existingMetadata = Some(existingFileMetadata),
        compression = MockCompression()
      )
      .map { actualSourceFile =>
        actualSourceFile.existingMetadata should not be empty

        actualSourceFile.currentMetadata match {
          case metadata: EntityMetadata.File =>
            metadata.path.endsWith(sourceFileResourcesPath) should be(true)
            metadata.size should be(26)
            metadata.link should be(None)
            metadata.isHidden should be(false)
            metadata.updated should be > Instant.MIN
            metadata.owner should not be empty
            metadata.group should not be empty
            metadata.permissions should not be empty
            metadata.checksum should be(expectedChecksum)
            metadata.crates should not be existingFileMetadata.crates
            metadata.compression should be("mock")

          case _: EntityMetadata.Directory =>
            fail("Expected file but received directory metadata")
        }
      }
  }

  it should "collect metadata for existing target files" in {
    val targetFileResourcesPath = "analysis/metadata-source-file"
    val targetFile = s"/$targetFileResourcesPath".asTestResource

    val existingFileMetadata = EntityMetadata.File(
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
      crates = Map(Paths.get(s"${targetFile}_0") -> Crate.generateId()),
      compression = "none"
    )

    val expectedChecksum = BigInt("338496524657487844672953225842489206917")

    Metadata
      .collectTarget(
        checksum = Checksum.MD5,
        entity = targetFile,
        destination = TargetEntity.Destination.Default,
        existingMetadata = existingFileMetadata
      )
      .map { actualTargetFile =>
        actualTargetFile.existingMetadata should be(existingFileMetadata)

        actualTargetFile.currentMetadata match {
          case Some(currentMetadata: EntityMetadata.File) =>
            currentMetadata.path.endsWith(targetFileResourcesPath) should be(true)
            currentMetadata.size should be(26)
            currentMetadata.link should be(None)
            currentMetadata.isHidden should be(false)
            currentMetadata.updated should be > Instant.MIN
            currentMetadata.owner should not be empty
            currentMetadata.group should not be empty
            currentMetadata.permissions should not be empty
            currentMetadata.checksum should be(expectedChecksum)
            currentMetadata.crates should be(existingFileMetadata.crates)

          case Some(_: EntityMetadata.Directory) =>
            fail("Expected file but received directory metadata")

          case None =>
            fail("Expected current target file metadata but none was found")
        }
      }
  }

  it should "collect metadata for missing target files" in {
    val targetFileResourcesPath = "analysis/metadata-missing-file"
    val targetFile = Paths.get(s"/tmp/$targetFileResourcesPath")

    val existingFileMetadata = EntityMetadata.File(
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
      crates = Map(Paths.get(s"${targetFile}_0") -> Crate.generateId()),
      compression = "none"
    )

    Metadata
      .collectTarget(
        checksum = Checksum.MD5,
        entity = targetFile,
        destination = TargetEntity.Destination.Default,
        existingMetadata = existingFileMetadata
      )
      .map { actualTargetFile =>
        actualTargetFile.existingMetadata should be(existingFileMetadata)
        actualTargetFile.currentMetadata should be(None)
      }
  }

  private implicit val system: ActorSystem = ActorSystem(name = "MetadataSpec")
}
