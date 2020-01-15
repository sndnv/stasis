package stasis.test.specs.unit.client.ops.recovery.stages

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import stasis.client.analysis.Checksum
import stasis.client.api.clients.Clients
import stasis.client.collection.RecoveryCollector
import stasis.client.model.SourceFile
import stasis.client.ops.recovery.stages.FileCollection
import stasis.client.ops.recovery.Providers
import stasis.shared.ops.Operation
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.Fixtures
import stasis.test.specs.unit.client.mocks._

class FileCollectionSpec extends AsyncUnitSpec {
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

    val mockTracker = new MockRecoveryTracker

    val stage = new FileCollection {
      override protected def collector: RecoveryCollector =
        new MockRecoveryCollector(List(sourceFile1, sourceFile2, sourceFile3))

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
      .runFold(Seq.empty[SourceFile])(_ :+ _)
      .map { collectedFiles =>
        collectedFiles should be(Seq(sourceFile1, sourceFile3))

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
