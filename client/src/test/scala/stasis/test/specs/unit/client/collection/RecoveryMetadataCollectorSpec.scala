package stasis.test.specs.unit.client.collection

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import stasis.client.analysis.Checksum
import stasis.client.collection.RecoveryMetadataCollector
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.{Fixtures, ResourceHelpers}

class RecoveryMetadataCollectorSpec extends AsyncUnitSpec with ResourceHelpers {
  "A RecoveryMetadataCollector" should "collect file metadata (recovery)" in {
    val file2 = "/collection/file-2".asTestResource
    val file3 = "/collection/file-3".asTestResource

    val collector = new RecoveryMetadataCollector.Default(
      checksum = Checksum.MD5
    )

    val file2Metadata = Fixtures.Metadata.FileTwoMetadata.copy(path = file2)
    val file3Metadata = Fixtures.Metadata.FileThreeMetadata.copy(path = file3)

    for {
      sourceFile2 <- collector.collect(file = file2, existingMetadata = file2Metadata)
      sourceFile3 <- collector.collect(file = file3, existingMetadata = file3Metadata)
    } yield {
      sourceFile2.path should be(file2)
      sourceFile2.existingMetadata should be(Some(file2Metadata))
      sourceFile2.currentMetadata.size should be(2)

      sourceFile3.path should be(file3)
      sourceFile3.existingMetadata should be(Some(file3Metadata))
      sourceFile3.currentMetadata.size should be(3)
    }
  }

  private implicit val system: ActorSystem = ActorSystem(name = "RecoveryMetadataCollectorSpec")
  private implicit val mat: ActorMaterializer = ActorMaterializer()
}
