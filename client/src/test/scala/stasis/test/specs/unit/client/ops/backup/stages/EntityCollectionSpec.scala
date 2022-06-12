package stasis.test.specs.unit.client.ops.backup.stages

import akka.actor.ActorSystem
import akka.stream.scaladsl.Source
import stasis.client.analysis.Checksum
import stasis.client.api.clients.Clients
import stasis.client.model.SourceEntity
import stasis.client.ops.backup.Providers
import stasis.client.ops.backup.stages.EntityCollection
import stasis.shared.model.datasets.DatasetDefinition
import stasis.shared.ops.Operation
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.Fixtures
import stasis.test.specs.unit.client.mocks._

class EntityCollectionSpec extends AsyncUnitSpec {
  "A Backup EntityCollection stage" should "collect and filter files" in {
    val sourceFile1 = SourceEntity(
      path = Fixtures.Metadata.FileOneMetadata.path,
      existingMetadata = None,
      currentMetadata = Fixtures.Metadata.FileOneMetadata
    )

    val sourceFile2 = SourceEntity(
      path = Fixtures.Metadata.FileTwoMetadata.path,
      existingMetadata = Some(Fixtures.Metadata.FileTwoMetadata),
      currentMetadata = Fixtures.Metadata.FileTwoMetadata
    )

    val sourceFile3 = SourceEntity(
      path = Fixtures.Metadata.FileThreeMetadata.path,
      existingMetadata = Some(Fixtures.Metadata.FileThreeMetadata.copy(isHidden = true)),
      currentMetadata = Fixtures.Metadata.FileThreeMetadata
    )

    val mockTracker = new MockBackupTracker
    val mockTelemetry = MockClientTelemetryContext()

    val stage = new EntityCollection {
      override protected def providers: Providers =
        Providers(
          checksum = Checksum.MD5,
          staging = new MockFileStaging(),
          compression = MockCompression(),
          encryptor = new MockEncryption(),
          decryptor = new MockEncryption(),
          clients = Clients(
            api = MockServerApiEndpointClient(),
            core = MockServerCoreEndpointClient()
          ),
          track = mockTracker,
          telemetry = mockTelemetry
        )

      override protected def targetDataset: DatasetDefinition = Fixtures.Datasets.Default
    }

    implicit val operationId: Operation.Id = Operation.generateId()

    Source
      .single(new MockBackupCollector(List(sourceFile1, sourceFile2, sourceFile3)))
      .via(stage.entityCollection)
      .runFold(Seq.empty[SourceEntity])(_ :+ _)
      .map { collectedFiles =>
        collectedFiles should be(Seq(sourceFile1, sourceFile3))

        mockTracker.statistics(MockBackupTracker.Statistic.EntityDiscovered) should be(0)
        mockTracker.statistics(MockBackupTracker.Statistic.SpecificationProcessed) should be(0)
        mockTracker.statistics(MockBackupTracker.Statistic.EntityExamined) should be(3)
        mockTracker.statistics(MockBackupTracker.Statistic.EntityCollected) should be(2)
        mockTracker.statistics(MockBackupTracker.Statistic.EntityProcessed) should be(0)
        mockTracker.statistics(MockBackupTracker.Statistic.MetadataCollected) should be(0)
        mockTracker.statistics(MockBackupTracker.Statistic.MetadataPushed) should be(0)
        mockTracker.statistics(MockBackupTracker.Statistic.FailureEncountered) should be(0)
        mockTracker.statistics(MockBackupTracker.Statistic.Completed) should be(0)

        mockTelemetry.ops.backup.entityExamined should be(3)
        mockTelemetry.ops.backup.entityCollected should be(2)
        mockTelemetry.ops.backup.entityChunkProcessed should be(0)
        mockTelemetry.ops.backup.entityProcessed should be(0)
      }
  }

  private implicit val system: ActorSystem = ActorSystem(name = "EntityCollectionSpec")
}
