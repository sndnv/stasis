package stasis.test.specs.unit.client.model

import java.nio.file.Paths

import stasis.client.model.TargetEntity
import stasis.test.specs.unit.UnitSpec
import stasis.test.specs.unit.client.Fixtures

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
  }

  it should "determine if its content has changed" in {
    targetFileWithoutCurrentMetadata.hasContentChanged should be(true)
    targetFileWithCurrentMetadata.hasContentChanged should be(false)
    targetFileWithUpdatedCurrentSize.hasContentChanged should be(true)
    targetFileWithUpdatedCurrentChecksum.hasContentChanged should be(true)
    targetDirectoryWithoutCurrentMetadata.hasContentChanged should be(false)
    targetDirectoryWithCurrentMetadata.hasContentChanged should be(false)
    targetDirectoryWithUpdatedCurrentGroup.hasContentChanged should be(false)
  }

  it should "provide its original file path" in {
    targetFile.originalPath should be(targetFile.existingMetadata.path)
  }

  it should "provide its destination file path" in {
    val testDestinationPath = Paths.get("/tmp/destination")

    val expectedOriginalPath = targetFile.originalPath
    val expectedPathWithDefaultStructure = Paths.get(s"$testDestinationPath/${targetFile.path}")
    val expectedPathWithoutDefaultStructure = Paths.get(s"$testDestinationPath/${targetFile.path.getFileName}")

    targetFile.destinationPath should be(expectedOriginalPath)

    targetFile
      .copy(
        destination = TargetEntity.Destination.Directory(path = testDestinationPath, keepDefaultStructure = true)
      )
      .destinationPath should be(expectedPathWithDefaultStructure)

    targetFile
      .copy(
        destination = TargetEntity.Destination.Directory(path = testDestinationPath, keepDefaultStructure = false)
      )
      .destinationPath should be(expectedPathWithoutDefaultStructure)
  }

  private val targetFile = TargetEntity(
    path = Fixtures.Metadata.FileOneMetadata.path,
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
    path = Fixtures.Metadata.DirectoryOneMetadata.path,
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
}
