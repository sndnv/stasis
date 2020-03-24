package stasis.test.specs.unit.client.model

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

  private val targetFileWithoutCurrentMetadata =
    TargetFile(
      path = Fixtures.Metadata.FileOneMetadata.path,
      existingMetadata = Fixtures.Metadata.FileOneMetadata,
      currentMetadata = None
    )

  private val targetFileWithCurrentMetadata =
    targetFileWithoutCurrentMetadata.copy(currentMetadata = Some(Fixtures.Metadata.FileOneMetadata))

  private val targetFileWithUpdatedCurrentGroup =
    targetFileWithCurrentMetadata.copy(currentMetadata = Some(Fixtures.Metadata.FileOneMetadata.copy(group = "none")))

  private val targetFileWithUpdatedCurrentSize =
    targetFileWithCurrentMetadata.copy(currentMetadata = Some(Fixtures.Metadata.FileOneMetadata.copy(size = 0)))

  private val targetFileWithUpdatedCurrentChecksum =
    targetFileWithCurrentMetadata.copy(currentMetadata = Some(Fixtures.Metadata.FileOneMetadata.copy(checksum = 0)))
}
