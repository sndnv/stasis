package stasis.test.specs.unit.client.collection

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import stasis.client.analysis.Checksum
import stasis.client.collection.BackupMetadataCollector
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
      sourceFile1 <- collector.collect(file = file1, existingMetadata = None)
      sourceFile2 <- collector.collect(file = file2, existingMetadata = Some(file2Metadata))
      sourceFile3 <- collector.collect(file = file3, existingMetadata = Some(file3Metadata))
    } yield {
      sourceFile1.path should be(file1)
      sourceFile1.existingMetadata should be(None)
      sourceFile1.currentMetadata.size should be(1)

      sourceFile2.path should be(file2)
      sourceFile2.existingMetadata should be(Some(file2Metadata))
      sourceFile2.currentMetadata.size should be(2)

      sourceFile3.path should be(file3)
      sourceFile3.existingMetadata should be(Some(file3Metadata))
      sourceFile3.currentMetadata.size should be(3)
    }
  }

  private implicit val system: ActorSystem = ActorSystem(name = "BackupMetadataCollectorSpec")
  private implicit val mat: ActorMaterializer = ActorMaterializer()
}
