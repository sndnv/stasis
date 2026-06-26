package stasis.test.specs.unit.client.model

import org.apache.pekko.util.ByteString

import stasis.client.model.EntityRef
import stasis.client.model.SourceEntity
import stasis.test.specs.unit.UnitSpec
import stasis.test.specs.unit.client.Fixtures
import stasis.test.specs.unit.client.ResourceHelpers.StringPath

class SourceEntitySpec extends UnitSpec {
  "A SourceEntity" should "fail if different entity types provided for current and existing metadata" in {
    an[IllegalArgumentException] should be thrownBy SourceEntity(
      ref = Fixtures.Metadata.FileOneMetadata.path.asRef,
      existingMetadata = Some(Fixtures.Metadata.DirectoryOneMetadata),
      currentMetadata = Fixtures.Metadata.FileOneMetadata
    )

    an[IllegalArgumentException] should be thrownBy SourceEntity(
      ref = Fixtures.Metadata.FileOneMetadata.path.asRef,
      existingMetadata = Some(Fixtures.Metadata.FileOneMetadata),
      currentMetadata = Fixtures.Metadata.DirectoryOneMetadata
    )
  }

  it should "determine if its metadata has changed" in {
    sourceFileWithoutExistingMetadata.hasChanged should be(true)
    sourceFileWithExistingMetadata.hasChanged should be(false)
    sourceFileWithUpdatedExistingGroup.hasChanged should be(true)
    sourceDirectoryWithoutExistingMetadata.hasChanged should be(true)
    sourceDirectoryWithExistingMetadata.hasChanged should be(false)
    sourceDirectoryWithUpdatedExistingGroup.hasChanged should be(true)
    sourceLibraryWithoutExistingMetadata.hasChanged should be(true)
    sourceLibraryWithExistingMetadata.hasChanged should be(false)
    sourceLibraryWithUpdatedExistingAttributes.hasChanged should be(true)
  }

  it should "determine if its content has changed" in {
    sourceFileWithoutExistingMetadata.hasContentChanged should be(true)
    sourceFileWithExistingMetadata.hasContentChanged should be(false)
    sourceFileWithUpdatedExistingSize.hasContentChanged should be(true)
    sourceFileWithUpdatedExistingChecksum.hasContentChanged should be(true)
    sourceDirectoryWithoutExistingMetadata.hasContentChanged should be(false)
    sourceDirectoryWithExistingMetadata.hasContentChanged should be(false)
    sourceDirectoryWithUpdatedExistingGroup.hasContentChanged should be(false)
    sourceLibraryWithoutExistingMetadata.hasContentChanged should be(true)
    sourceLibraryWithExistingMetadata.hasContentChanged should be(false)
    sourceLibraryWithUpdatedExistingChecksum.hasContentChanged should be(true)
  }

  private val fileEntity = SourceEntity(
    ref = Fixtures.Metadata.FileOneMetadata.path.asRef,
    existingMetadata = None,
    currentMetadata = Fixtures.Metadata.FileOneMetadata
  )

  private val sourceFileWithoutExistingMetadata =
    fileEntity

  private val sourceFileWithExistingMetadata =
    sourceFileWithoutExistingMetadata.copy(existingMetadata = Some(Fixtures.Metadata.FileOneMetadata))

  private val sourceFileWithUpdatedExistingGroup =
    sourceFileWithExistingMetadata.copy(existingMetadata = Some(Fixtures.Metadata.FileOneMetadata.copy(group = "none")))

  private val sourceFileWithUpdatedExistingSize =
    sourceFileWithExistingMetadata.copy(existingMetadata = Some(Fixtures.Metadata.FileOneMetadata.copy(size = 0)))

  private val sourceFileWithUpdatedExistingChecksum =
    sourceFileWithExistingMetadata.copy(existingMetadata = Some(Fixtures.Metadata.FileOneMetadata.copy(checksum = 0)))

  private val directoryEntity = SourceEntity(
    ref = Fixtures.Metadata.DirectoryOneMetadata.path.asRef,
    existingMetadata = None,
    currentMetadata = Fixtures.Metadata.DirectoryOneMetadata
  )

  private val sourceDirectoryWithoutExistingMetadata =
    directoryEntity

  private val sourceDirectoryWithExistingMetadata =
    sourceDirectoryWithoutExistingMetadata.copy(existingMetadata = Some(Fixtures.Metadata.DirectoryOneMetadata))

  private val sourceDirectoryWithUpdatedExistingGroup =
    sourceDirectoryWithExistingMetadata.copy(existingMetadata = Some(Fixtures.Metadata.DirectoryOneMetadata.copy(group = "none")))

  private val libraryEntity = SourceEntity(
    ref = EntityRef.default(Fixtures.Metadata.LibraryOneMetadata.path),
    existingMetadata = None,
    currentMetadata = Fixtures.Metadata.LibraryOneMetadata
  )

  private val sourceLibraryWithoutExistingMetadata =
    libraryEntity

  private val sourceLibraryWithExistingMetadata =
    libraryEntity.copy(existingMetadata = Some(Fixtures.Metadata.LibraryOneMetadata))

  private val sourceLibraryWithUpdatedExistingChecksum =
    sourceLibraryWithExistingMetadata.copy(existingMetadata = Some(Fixtures.Metadata.LibraryOneMetadata.copy(checksum = 0)))

  private val sourceLibraryWithUpdatedExistingAttributes =
    sourceLibraryWithExistingMetadata.copy(existingMetadata =
      Some(Fixtures.Metadata.LibraryOneMetadata.copy(attributes = ByteString("favorite=false")))
    )

}
