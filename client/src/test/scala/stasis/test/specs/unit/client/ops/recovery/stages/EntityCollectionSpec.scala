package stasis.test.specs.unit.client.ops.recovery.stages

import akka.actor.ActorSystem
import stasis.client.analysis.Checksum
import stasis.client.api.clients.Clients
import stasis.client.collection.RecoveryCollector
import stasis.client.model.TargetEntity
import stasis.client.ops.recovery.Providers
import stasis.client.ops.recovery.stages.EntityCollection
import stasis.shared.ops.Operation
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.Fixtures
import stasis.test.specs.unit.client.mocks._

class EntityCollectionSpec extends AsyncUnitSpec {
  "A Recovery EntityCollection stage" should "collect and filter files" in {
    val targetFile1 = TargetEntity(
      path = Fixtures.Metadata.FileOneMetadata.path,
      destination = TargetEntity.Destination.Default,
      existingMetadata = Fixtures.Metadata.FileOneMetadata,
      currentMetadata = None
    )

    val targetFile2 = TargetEntity(
      path = Fixtures.Metadata.FileTwoMetadata.path,
      destination = TargetEntity.Destination.Default,
      existingMetadata = Fixtures.Metadata.FileTwoMetadata,
      currentMetadata = Some(Fixtures.Metadata.FileTwoMetadata)
    )

    val targetFile3 = TargetEntity(
      path = Fixtures.Metadata.FileThreeMetadata.path,
      destination = TargetEntity.Destination.Default,
      existingMetadata = Fixtures.Metadata.FileThreeMetadata,
      currentMetadata = Some(Fixtures.Metadata.FileThreeMetadata.copy(isHidden = true))
    )

    val mockTracker = new MockRecoveryTracker
    val mockTelemetry = MockClientTelemetryContext()

    val stage = new EntityCollection {
      override protected def collector: RecoveryCollector =
        new MockRecoveryCollector(List(targetFile1, targetFile2, targetFile3))

      override protected def providers: Providers =
        Providers(
          checksum = Checksum.MD5,
          staging = new MockFileStaging(),
          compression = MockCompression(),
          decryptor = new MockEncryption(),
          clients = Clients(api = MockServerApiEndpointClient(), core = MockServerCoreEndpointClient()),
          track = mockTracker,
          telemetry = mockTelemetry
        )
    }

    implicit val operationId: Operation.Id = Operation.generateId()

    stage.entityCollection
      .runFold(Seq.empty[TargetEntity])(_ :+ _)
      .map { collectedFiles =>
        collectedFiles should be(Seq(targetFile1, targetFile3))

        mockTracker.statistics(MockRecoveryTracker.Statistic.EntityExamined) should be(3)
        mockTracker.statistics(MockRecoveryTracker.Statistic.EntityCollected) should be(2)
        mockTracker.statistics(MockRecoveryTracker.Statistic.EntityProcessed) should be(0)
        mockTracker.statistics(MockRecoveryTracker.Statistic.MetadataApplied) should be(0)
        mockTracker.statistics(MockRecoveryTracker.Statistic.FailureEncountered) should be(0)
        mockTracker.statistics(MockRecoveryTracker.Statistic.Completed) should be(0)

        mockTelemetry.ops.recovery.entityExamined should be(3)
        mockTelemetry.ops.recovery.entityCollected should be(2)
        mockTelemetry.ops.recovery.entityChunkProcessed should be(0)
        mockTelemetry.ops.recovery.entityProcessed should be(0)
        mockTelemetry.ops.recovery.metadataApplied should be(0)
      }
  }

  private implicit val system: ActorSystem = ActorSystem(name = "EntityCollectionSpec")
}
