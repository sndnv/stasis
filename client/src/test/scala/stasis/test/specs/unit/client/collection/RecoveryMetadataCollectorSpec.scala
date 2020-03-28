package stasis.test.specs.unit.client.collection

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import stasis.client.analysis.Checksum
import stasis.client.collection.RecoveryMetadataCollector
import stasis.client.model.TargetFile
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
      targetFile2 <- collector.collect(
        file = file2,
        destination = TargetFile.Destination.Default,
        existingMetadata = file2Metadata
      )
      targetFile3 <- collector.collect(
        file = file3,
        destination = TargetFile.Destination.Default,
        existingMetadata = file3Metadata
      )
    } yield {
      targetFile2.path should be(file2)
      targetFile2.existingMetadata should be(file2Metadata)
      targetFile2.currentMetadata.map(_.size) should be(Some(2))

      targetFile3.path should be(file3)
      targetFile3.existingMetadata should be(file3Metadata)
      targetFile3.currentMetadata.map(_.size) should be(Some(3))
    }
  }

  private implicit val system: ActorSystem = ActorSystem(name = "RecoveryMetadataCollectorSpec")
  private implicit val mat: ActorMaterializer = ActorMaterializer()
}
