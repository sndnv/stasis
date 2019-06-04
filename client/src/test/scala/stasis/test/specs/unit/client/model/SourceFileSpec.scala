package stasis.test.specs.unit.client.model

import stasis.client.model.SourceFile
import stasis.test.specs.unit.UnitSpec
import stasis.test.specs.unit.client.Fixtures

class SourceFileSpec extends UnitSpec {
  private val sourceFileWithoutExistingMetadata =
    SourceFile(
      path = Fixtures.Metadata.FileOneMetadata.path,
      existingMetadata = None,
      currentMetadata = Fixtures.Metadata.FileOneMetadata
    )

  private val sourceFileWithExistingMetadata =
    sourceFileWithoutExistingMetadata.copy(existingMetadata = Some(Fixtures.Metadata.FileOneMetadata))

  private val sourceFileWithUpdatedExistingGroup =
    sourceFileWithExistingMetadata.copy(existingMetadata = Some(Fixtures.Metadata.FileOneMetadata.copy(group = "none")))

  private val sourceFileWithUpdatedExistingSize =
    sourceFileWithExistingMetadata.copy(existingMetadata = Some(Fixtures.Metadata.FileOneMetadata.copy(size = 0)))

  private val sourceFileWithUpdatedExistingChecksum =
    sourceFileWithExistingMetadata.copy(existingMetadata = Some(Fixtures.Metadata.FileOneMetadata.copy(checksum = 0)))

  "A SourceFile" should "determine if its metadata has changed" in {
    sourceFileWithoutExistingMetadata.hasChanged should be(true)
    sourceFileWithExistingMetadata.hasChanged should be(false)
    sourceFileWithUpdatedExistingGroup.hasChanged should be(true)
  }

  it should "determine if its content has changed" in {
    sourceFileWithoutExistingMetadata.hasContentChanged should be(true)
    sourceFileWithExistingMetadata.hasContentChanged should be(false)
    sourceFileWithUpdatedExistingSize.hasContentChanged should be(true)
    sourceFileWithUpdatedExistingChecksum.hasContentChanged should be(true)
  }
}
