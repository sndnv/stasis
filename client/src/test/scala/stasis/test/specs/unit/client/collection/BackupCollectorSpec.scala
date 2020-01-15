package stasis.test.specs.unit.client.collection

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import stasis.client.analysis.Checksum
import stasis.client.collection.{BackupCollector, BackupMetadataCollector}
import stasis.client.model.{DatasetMetadata, SourceFile}
import stasis.client.ops.ParallelismConfig
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.ResourceHelpers

class BackupCollectorSpec extends AsyncUnitSpec with ResourceHelpers {
  private implicit val system: ActorSystem = ActorSystem(name = "BackupCollectorSpec")
  private implicit val mat: ActorMaterializer = ActorMaterializer()

  private implicit val parallelismConfig: ParallelismConfig = ParallelismConfig(value = 1)

  "A default BackupCollector" should "collect backup files based on a files list" in {
    val file1 = "/collection/file-1".asTestResource
    val file2 = "/collection/file-2".asTestResource

    val collector = new BackupCollector.Default(
      files = List(file1, file2),
      metadataCollector = new BackupMetadataCollector.Default(
        checksum = Checksum.MD5,
        latestMetadata = Some(DatasetMetadata.empty)
      )
    )

    collector
      .collect()
      .runFold(Seq.empty[SourceFile])(_ :+ _)
      .map(_.sortBy(_.path.toAbsolutePath.toString))
      .map {
        case sourceFile1 :: sourceFile2 :: Nil =>
          sourceFile1.path should be(file1)
          sourceFile1.existingMetadata should be(None)
          sourceFile1.currentMetadata.size should be(1)

          sourceFile2.path should be(file2)
          sourceFile2.existingMetadata should be(None)
          sourceFile2.currentMetadata.size should be(2)

        case sourceFiles =>
          fail(s"Unexpected number of entries received: [${sourceFiles.size}]")
      }
  }
}
