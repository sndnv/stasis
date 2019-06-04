package stasis.test.specs.unit.client.ops.recovery.stages

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import stasis.client.model.SourceFile
import stasis.client.ops.recovery.Providers
import stasis.client.ops.recovery.stages.FileCollection
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.Fixtures
import stasis.test.specs.unit.client.mocks.{MockCompression, MockEncryption, MockFileStaging, MockRecoveryCollector}

class FileCollectionSpec extends AsyncUnitSpec {
  private implicit val system: ActorSystem = ActorSystem(name = "FileCollectionSpec")
  private implicit val mat: ActorMaterializer = ActorMaterializer()

  "A Recovery FileCollection stage" should "collect and filter files" in {
    val sourceFile1 = SourceFile(
      path = Fixtures.Metadata.FileOneMetadata.path,
      existingMetadata = None,
      currentMetadata = Fixtures.Metadata.FileOneMetadata
    )

    val sourceFile2 = SourceFile(
      path = Fixtures.Metadata.FileTwoMetadata.path,
      existingMetadata = Some(Fixtures.Metadata.FileTwoMetadata),
      currentMetadata = Fixtures.Metadata.FileTwoMetadata
    )

    val sourceFile3 = SourceFile(
      path = Fixtures.Metadata.FileThreeMetadata.path,
      existingMetadata = Some(Fixtures.Metadata.FileThreeMetadata.copy(isHidden = true)),
      currentMetadata = Fixtures.Metadata.FileThreeMetadata
    )

    val stage = new FileCollection {
      override protected def providers: Providers = Providers(
        collector = new MockRecoveryCollector(List(sourceFile1, sourceFile2, sourceFile3)),
        staging = new MockFileStaging(),
        decompressor = new MockCompression(),
        decryptor = new MockEncryption()
      )
    }

    stage.fileCollection
      .runFold(Seq.empty[SourceFile])(_ :+ _)
      .map { collectedFiles =>
        collectedFiles should be(Seq(sourceFile1, sourceFile3))
      }
  }
}
