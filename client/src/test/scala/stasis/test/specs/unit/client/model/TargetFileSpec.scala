package stasis.test.specs.unit.client.model

import java.nio.file.Paths

import stasis.client.model.TargetFile
import stasis.test.specs.unit.UnitSpec
import stasis.test.specs.unit.client.Fixtures

class TargetFileSpec extends UnitSpec {
  "A TargetFile" should "determine if its metadata has changed" in {
    targetFileWithoutCurrentMetadata.hasChanged should be(true)
    targetFileWithCurrentMetadata.hasChanged should be(false)
    targetFileWithUpdatedCurrentGroup.hasChanged should be(true)
  }

  it should "determine if its content has changed" in {
    targetFileWithoutCurrentMetadata.hasContentChanged should be(true)
    targetFileWithCurrentMetadata.hasContentChanged should be(false)
    targetFileWithUpdatedCurrentSize.hasContentChanged should be(true)
    targetFileWithUpdatedCurrentChecksum.hasContentChanged should be(true)
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
        destination = TargetFile.Destination.Directory(path = testDestinationPath, keepDefaultStructure = true)
      )
      .destinationPath should be(expectedPathWithDefaultStructure)

    targetFile
      .copy(
        destination = TargetFile.Destination.Directory(path = testDestinationPath, keepDefaultStructure = false)
      )
      .destinationPath should be(expectedPathWithoutDefaultStructure)
  }

  private val targetFile = TargetFile(
    path = Fixtures.Metadata.FileOneMetadata.path,
    destination = TargetFile.Destination.Default,
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
}
