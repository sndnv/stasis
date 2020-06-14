package stasis.test.specs.unit.client.collection

import akka.actor.ActorSystem
import stasis.client.analysis.Checksum
import stasis.client.collection.BackupMetadataCollector
import stasis.client.model.EntityMetadata
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.{Fixtures, ResourceHelpers}

class BackupMetadataCollectorSpec extends AsyncUnitSpec with ResourceHelpers {
  "A BackupMetadataCollector" should "collect file metadata (backup)" in {
    val file1 = "/collection/file-1".asTestResource
    val file2 = "/collection/file-2".asTestResource
    val file3 = "/collection/file-3".asTestResource

    val file2Metadata = Fixtures.Metadata.FileTwoMetadata.copy(path = file2)
    val file3Metadata = Fixtures.Metadata.FileThreeMetadata.copy(path = file3)

    val collector = new BackupMetadataCollector.Default(checksum = Checksum.MD5)

    for {
      sourceFile1 <- collector.collect(entity = file1, existingMetadata = None)
      sourceFile2 <- collector.collect(entity = file2, existingMetadata = Some(file2Metadata))
      sourceFile3 <- collector.collect(entity = file3, existingMetadata = Some(file3Metadata))
    } yield {
      sourceFile1.path should be(file1)
      sourceFile1.existingMetadata should be(None)
      sourceFile1.currentMetadata match {
        case metadata: EntityMetadata.File => metadata.size should be(1)
        case _: EntityMetadata.Directory   => fail("Expected file but received directory metadata")
      }

      sourceFile2.path should be(file2)
      sourceFile2.existingMetadata should be(Some(file2Metadata))
      sourceFile2.currentMetadata match {
        case metadata: EntityMetadata.File => metadata.size should be(2)
        case _: EntityMetadata.Directory   => fail("Expected file but received directory metadata")
      }

      sourceFile3.path should be(file3)
      sourceFile3.existingMetadata should be(Some(file3Metadata))
      sourceFile3.currentMetadata match {
        case metadata: EntityMetadata.File => metadata.size should be(3)
        case _: EntityMetadata.Directory   => fail("Expected file but received directory metadata")
      }
    }
  }

  private implicit val system: ActorSystem = ActorSystem(name = "BackupMetadataCollectorSpec")
}
