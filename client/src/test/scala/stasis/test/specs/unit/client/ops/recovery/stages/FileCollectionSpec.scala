package stasis.test.specs.unit.client.ops.recovery.stages

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import stasis.client.analysis.Checksum
import stasis.client.api.clients.Clients
import stasis.client.collection.RecoveryCollector
import stasis.client.model.TargetFile
import stasis.client.ops.recovery.stages.FileCollection
import stasis.client.ops.recovery.Providers
import stasis.shared.ops.Operation
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.Fixtures
import stasis.test.specs.unit.client.mocks._

class FileCollectionSpec extends AsyncUnitSpec {
  "A Recovery FileCollection stage" should "collect and filter files" in {
    val targetFile1 = TargetFile(
      path = Fixtures.Metadata.FileOneMetadata.path,
      existingMetadata = Fixtures.Metadata.FileOneMetadata,
      currentMetadata = None
    )

    val targetFile2 = TargetFile(
      path = Fixtures.Metadata.FileTwoMetadata.path,
      existingMetadata = Fixtures.Metadata.FileTwoMetadata,
      currentMetadata = Some(Fixtures.Metadata.FileTwoMetadata)
    )

    val targetFile3 = TargetFile(
      path = Fixtures.Metadata.FileThreeMetadata.path,
      existingMetadata = Fixtures.Metadata.FileThreeMetadata,
      currentMetadata = Some(Fixtures.Metadata.FileThreeMetadata.copy(isHidden = true))
    )

    val mockTracker = new MockRecoveryTracker

    val stage = new FileCollection {
      override protected def collector: RecoveryCollector =
        new MockRecoveryCollector(List(targetFile1, targetFile2, targetFile3))

      override protected def providers: Providers = Providers(
        checksum = Checksum.MD5,
        staging = new MockFileStaging(),
        decompressor = new MockCompression(),
        decryptor = new MockEncryption(),
        clients = Clients(api = MockServerApiEndpointClient(), core = MockServerCoreEndpointClient()),
        track = mockTracker
      )
    }

    implicit val operationId: Operation.Id = Operation.generateId()

    stage.fileCollection
      .runFold(Seq.empty[TargetFile])(_ :+ _)
      .map { collectedFiles =>
        collectedFiles should be(Seq(targetFile1, targetFile3))

        mockTracker.statistics(MockRecoveryTracker.Statistic.FileExamined) should be(3)
        mockTracker.statistics(MockRecoveryTracker.Statistic.FileCollected) should be(2)
        mockTracker.statistics(MockRecoveryTracker.Statistic.FileProcessed) should be(0)
        mockTracker.statistics(MockRecoveryTracker.Statistic.MetadataApplied) should be(0)
        mockTracker.statistics(MockRecoveryTracker.Statistic.FailureEncountered) should be(0)
        mockTracker.statistics(MockRecoveryTracker.Statistic.Completed) should be(0)
      }
  }

  private implicit val system: ActorSystem = ActorSystem(name = "FileCollectionSpec")
  private implicit val mat: ActorMaterializer = ActorMaterializer()
}
