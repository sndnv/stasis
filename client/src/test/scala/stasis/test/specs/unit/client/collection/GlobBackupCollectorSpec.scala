package stasis.test.specs.unit.client.collection

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import stasis.client.analysis.{Checksum, Metadata}
import stasis.client.collection.GlobBackupCollector
import stasis.client.collection.GlobBackupCollector.GlobRule
import stasis.client.model.{DatasetMetadata, SourceFile}
import stasis.client.ops.ParallelismConfig
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.ResourceHelpers

class GlobBackupCollectorSpec extends AsyncUnitSpec with ResourceHelpers {
  private implicit val system: ActorSystem = ActorSystem(name = "GlobBackupCollectorSpec")
  private implicit val mat: ActorMaterializer = ActorMaterializer()

  private implicit val parallelismConfig: ParallelismConfig = ParallelismConfig(value = 1)

  "A GlobBackupCollector" should "collect backup files based on glob rules" in {
    val targetDirectory = "/collection".asTestResource

    val collector = new GlobBackupCollector(
      rules = Seq(
        GlobRule(directory = targetDirectory.toString, pattern = "file-2"),
        GlobRule(directory = targetDirectory.toString, pattern = "other-*")
      ),
      metadata = new Metadata.Default(
        checksum = Checksum.MD5,
        lastDatasetMetadata = DatasetMetadata.empty
      )
    )

    collector
      .collect()
      .runFold(Seq.empty[SourceFile])(_ :+ _)
      .map(_.sortBy(_.path.toAbsolutePath.toString))
      .map {
        case sourceFile2 :: otherSourceFile1 :: otherSourceFile2 :: Nil =>
          sourceFile2.path should be("/collection/file-2".asTestResource)
          sourceFile2.existingMetadata should be(None)
          sourceFile2.currentMetadata.size should be(2)

          otherSourceFile1.path should be("/collection/other-file-1".asTestResource)
          otherSourceFile1.existingMetadata should be(None)
          otherSourceFile1.currentMetadata.size should be(1)

          otherSourceFile2.path should be("/collection/other-file-2".asTestResource)
          otherSourceFile2.existingMetadata should be(None)
          otherSourceFile2.currentMetadata.size should be(2)

        case sourceFiles =>
          fail(s"Unexpected number of entries received: [${sourceFiles.size}]")
      }
  }
}
