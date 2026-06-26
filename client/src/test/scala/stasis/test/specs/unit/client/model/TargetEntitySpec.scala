package stasis.test.specs.unit.client.model

import java.nio.file.Paths

import org.apache.pekko.util.ByteString

import stasis.client.model.EntityRef
import stasis.client.model.TargetEntity
import stasis.test.specs.unit.UnitSpec
import stasis.test.specs.unit.client.Fixtures
import stasis.test.specs.unit.client.ResourceHelpers.StringPath

class TargetEntitySpec extends UnitSpec {
  "A TargetEntity" should "fail if different entity types provided for current and existing metadata" in {
    an[IllegalArgumentException] should be thrownBy targetFile.copy(
      existingMetadata = Fixtures.Metadata.DirectoryOneMetadata,
      currentMetadata = Some(Fixtures.Metadata.FileOneMetadata)
    )

    an[IllegalArgumentException] should be thrownBy targetFile.copy(
      existingMetadata = Fixtures.Metadata.FileOneMetadata,
      currentMetadata = Some(Fixtures.Metadata.DirectoryOneMetadata)
    )
  }

  it should "determine if its metadata has changed" in {
    targetFileWithoutCurrentMetadata.hasChanged should be(true)
    targetFileWithCurrentMetadata.hasChanged should be(false)
    targetFileWithUpdatedCurrentGroup.hasChanged should be(true)
    targetDirectoryWithoutCurrentMetadata.hasChanged should be(true)
    targetDirectoryWithCurrentMetadata.hasChanged should be(false)
    targetDirectoryWithUpdatedCurrentGroup.hasChanged should be(true)
    targetLibraryWithoutCurrentMetadata.hasChanged should be(true)
    targetLibraryWithCurrentMetadata.hasChanged should be(false)
    targetLibraryWithUpdatedCurrentAttributes.hasChanged should be(true)
  }

  it should "determine if its content has changed" in {
    targetFileWithoutCurrentMetadata.hasContentChanged should be(true)
    targetFileWithCurrentMetadata.hasContentChanged should be(false)
    targetFileWithUpdatedCurrentSize.hasContentChanged should be(true)
    targetFileWithUpdatedCurrentChecksum.hasContentChanged should be(true)
    targetDirectoryWithoutCurrentMetadata.hasContentChanged should be(false)
    targetDirectoryWithCurrentMetadata.hasContentChanged should be(false)
    targetDirectoryWithUpdatedCurrentGroup.hasContentChanged should be(false)
    targetLibraryWithoutCurrentMetadata.hasContentChanged should be(true)
    targetLibraryWithCurrentMetadata.hasContentChanged should be(false)
    targetLibraryWithUpdatedCurrentChecksum.hasContentChanged should be(true)
  }

  it should "provide its original file path" in {
    targetFile.originalRef.key should be(targetFile.existingMetadata.path)
  }

  it should "provide its destination file path" in {
    val testDestinationPath = Paths.get("/tmp/destination")
    val originalPath = Fixtures.Metadata.FileOneMetadata.path.asPath

    val expectedPathWithDefaultStructure = Paths.get(s"$testDestinationPath/$originalPath")
    val expectedPathWithoutDefaultStructure = Paths.get(s"$testDestinationPath/${originalPath.getFileName}")

    targetFile.destinationRef should be(targetFile.originalRef)

    targetFile
      .copy(
        destination = TargetEntity.Destination.Directory(path = testDestinationPath, keepDefaultStructure = true)
      )
      .destinationRef should be(EntityRef.Filesystem(expectedPathWithDefaultStructure))

    targetFile
      .copy(
        destination = TargetEntity.Destination.Directory(path = testDestinationPath, keepDefaultStructure = false)
      )
      .destinationRef should be(EntityRef.Filesystem(expectedPathWithoutDefaultStructure))
  }

  it should "provide a filesystem destination for a library ref" in {
    val testDestinationPath = Paths.get("/tmp/destination")

    val libraryFile = targetFile.copy(ref = EntityRef.Library(scheme = "photos", path = "/album/img.heic"))

    libraryFile
      .copy(
        destination = TargetEntity.Destination.Directory(path = testDestinationPath, keepDefaultStructure = false)
      )
      .destinationRef should be(EntityRef.Filesystem(Paths.get("/tmp/destination/img.heic")))
  }

  private val targetFile = TargetEntity(
    ref = Fixtures.Metadata.FileOneMetadata.path.asRef,
    destination = TargetEntity.Destination.Default,
    existingMetadata = Fixtures.Metadata.FileOneMetadata,
    currentMetadata = None
  )

  private val targetFileWithoutCurrentMetadata =
    targetFile

  private val targetFileWithCurrentMetadata =
    targetFile.copy(currentMetadata = Some(Fixtures.Metadata.FileOneMetadata))

  private val targetFileWithUpdatedCurrentGroup =
    targetFile.copy(currentMetadata = Some(Fixtures.Metadata.FileOneMetadata.copy(group = "none")))

  private val targetFileWithUpdatedCurrentSize =
    targetFile.copy(currentMetadata = Some(Fixtures.Metadata.FileOneMetadata.copy(size = 0)))

  private val targetFileWithUpdatedCurrentChecksum =
    targetFile.copy(currentMetadata = Some(Fixtures.Metadata.FileOneMetadata.copy(checksum = 0)))

  private val targetDirectory = TargetEntity(
    ref = Fixtures.Metadata.DirectoryOneMetadata.path.asRef,
    destination = TargetEntity.Destination.Default,
    existingMetadata = Fixtures.Metadata.DirectoryOneMetadata,
    currentMetadata = None
  )

  private val targetDirectoryWithoutCurrentMetadata =
    targetDirectory

  private val targetDirectoryWithCurrentMetadata =
    targetDirectory.copy(currentMetadata = Some(Fixtures.Metadata.DirectoryOneMetadata))

  private val targetDirectoryWithUpdatedCurrentGroup =
    targetDirectory.copy(currentMetadata = Some(Fixtures.Metadata.DirectoryOneMetadata.copy(group = "none")))

  private val targetLibrary = TargetEntity(
    ref = EntityRef.default(Fixtures.Metadata.LibraryOneMetadata.path),
    destination = TargetEntity.Destination.Default,
    existingMetadata = Fixtures.Metadata.LibraryOneMetadata,
    currentMetadata = None
  )

  private val targetLibraryWithoutCurrentMetadata =
    targetLibrary

  private val targetLibraryWithCurrentMetadata =
    targetLibrary.copy(currentMetadata = Some(Fixtures.Metadata.LibraryOneMetadata))

  private val targetLibraryWithUpdatedCurrentChecksum =
    targetLibrary.copy(currentMetadata = Some(Fixtures.Metadata.LibraryOneMetadata.copy(checksum = 0)))

  private val targetLibraryWithUpdatedCurrentAttributes =
    targetLibrary.copy(currentMetadata =
      Some(Fixtures.Metadata.LibraryOneMetadata.copy(attributes = ByteString("favorite=false")))
    )
}
