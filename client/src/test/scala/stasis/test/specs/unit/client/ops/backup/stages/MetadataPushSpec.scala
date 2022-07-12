package stasis.test.specs.unit.client.ops.backup.stages

import akka.actor.ActorSystem
import akka.stream.{Materializer, SystemMaterializer}
import akka.stream.scaladsl.{Sink, Source}
import stasis.client.analysis.Checksum
import stasis.client.api.clients.Clients
import stasis.client.encryption.secrets.DeviceSecret
import stasis.client.model.{DatasetMetadata, FilesystemMetadata}
import stasis.client.ops.backup.Providers
import stasis.client.ops.backup.stages.MetadataPush
import stasis.shared.model.datasets.DatasetDefinition
import stasis.shared.ops.Operation
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.Fixtures
import stasis.test.specs.unit.client.mocks.{MockBackupTracker, _}

class MetadataPushSpec extends AsyncUnitSpec { spec =>
  "A Backup MetadataPush stage" should "push dataset metadata" in {
    val mockEncryption = new MockEncryption()
    val mockApiClient = MockServerApiEndpointClient()
    val mockCoreClient = MockServerCoreEndpointClient()
    val mockTracker = new MockBackupTracker

    val stage = new MetadataPush {
      override protected def targetDataset: DatasetDefinition = Fixtures.Datasets.Default
      override protected def deviceSecret: DeviceSecret = Fixtures.Secrets.Default
      override protected def providers: Providers =
        Providers(
          checksum = Checksum.MD5,
          staging = new MockFileStaging(),
          compression = MockCompression(),
          encryptor = mockEncryption,
          decryptor = mockEncryption,
          clients = Clients(
            api = mockApiClient,
            core = mockCoreClient
          ),
          track = mockTracker,
          telemetry = MockClientTelemetryContext()
        )

      override implicit protected def mat: Materializer = SystemMaterializer(system).materializer
    }

    implicit val operationId: Operation.Id = Operation.generateId()

    Source
      .single(
        DatasetMetadata(
          contentChanged = Map(
            Fixtures.Metadata.FileOneMetadata.path -> Fixtures.Metadata.FileOneMetadata
          ),
          metadataChanged = Map(
            Fixtures.Metadata.FileTwoMetadata.path -> Fixtures.Metadata.FileTwoMetadata,
            Fixtures.Metadata.FileThreeMetadata.path -> Fixtures.Metadata.FileThreeMetadata
          ),
          filesystem = FilesystemMetadata(
            entities = Map(
              Fixtures.Metadata.FileOneMetadata.path -> FilesystemMetadata.EntityState.New,
              Fixtures.Metadata.FileTwoMetadata.path -> FilesystemMetadata.EntityState.Updated,
              Fixtures.Metadata.FileThreeMetadata.path -> FilesystemMetadata.EntityState.Updated
            )
          )
        )
      )
      .via(stage.metadataPush)
      .runWith(Sink.ignore)
      .map { _ =>
        mockEncryption.statistics(MockEncryption.Statistic.FileEncrypted) should be(0)
        mockEncryption.statistics(MockEncryption.Statistic.FileDecrypted) should be(0)
        mockEncryption.statistics(MockEncryption.Statistic.MetadataEncrypted) should be(1)
        mockEncryption.statistics(MockEncryption.Statistic.MetadataDecrypted) should be(0)

        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntryCreated) should be(1)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetDefinitionCreated) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.PublicSchedulesRetrieved) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.PublicScheduleRetrieved) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.UserRetrieved) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DeviceRetrieved) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.Ping) should be(0)

        mockCoreClient.statistics(MockServerCoreEndpointClient.Statistic.CratePulled) should be(0)
        mockCoreClient.statistics(MockServerCoreEndpointClient.Statistic.CratePushed) should be(1)

        mockTracker.statistics(MockBackupTracker.Statistic.EntityDiscovered) should be(0)
        mockTracker.statistics(MockBackupTracker.Statistic.SpecificationProcessed) should be(0)
        mockTracker.statistics(MockBackupTracker.Statistic.EntityExamined) should be(0)
        mockTracker.statistics(MockBackupTracker.Statistic.EntityCollected) should be(0)
        mockTracker.statistics(MockBackupTracker.Statistic.EntityProcessingStarted) should be(0)
        mockTracker.statistics(MockBackupTracker.Statistic.EntityPartProcessed) should be(0)
        mockTracker.statistics(MockBackupTracker.Statistic.EntityProcessed) should be(0)
        mockTracker.statistics(MockBackupTracker.Statistic.MetadataCollected) should be(0)
        mockTracker.statistics(MockBackupTracker.Statistic.MetadataPushed) should be(1)
        mockTracker.statistics(MockBackupTracker.Statistic.FailureEncountered) should be(0)
        mockTracker.statistics(MockBackupTracker.Statistic.Completed) should be(0)
      }
  }

  private implicit val system: ActorSystem = ActorSystem(name = "MetadataPushSpec")
}
