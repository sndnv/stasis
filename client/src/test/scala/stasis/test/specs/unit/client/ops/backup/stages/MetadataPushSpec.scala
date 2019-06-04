package stasis.test.specs.unit.client.ops.backup.stages

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.{ActorMaterializer, Materializer}
import stasis.client.encryption.secrets.DeviceSecret
import stasis.client.model.DatasetMetadata
import stasis.client.ops.ParallelismConfig
import stasis.client.ops.backup.stages.MetadataPush
import stasis.client.ops.backup.{Clients, Providers}
import stasis.core.routing.Node
import stasis.shared.model.datasets.DatasetDefinition
import stasis.shared.model.devices.Device
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.Fixtures
import stasis.test.specs.unit.client.mocks._

class MetadataPushSpec extends AsyncUnitSpec { spec =>
  private implicit val system: ActorSystem = ActorSystem(name = "MetadataPushSpec")
  private implicit val mat: ActorMaterializer = ActorMaterializer()

  "A Backup MetadataPush stage" should "push dataset metadata" in {
    val mockEncryption = new MockEncryption()
    val mockApiClient = new MockServerApiEndpointClient(self = Device.generateId())
    val mockCoreClient = new MockServerCoreEndpointClient(self = Node.generateId(), crates = Map.empty)

    val stage = new MetadataPush {
      override protected def targetDataset: DatasetDefinition = Fixtures.Datasets.Default
      override protected def deviceSecret: DeviceSecret = Fixtures.Secrets.Default
      override protected def providers: Providers = Providers(
        collector = new MockBackupCollector(List.empty),
        staging = new MockFileStaging(),
        compressor = new MockCompression(),
        encryptor = mockEncryption
      )
      override protected def clients: Clients = Clients(
        api = mockApiClient,
        core = mockCoreClient
      )
      override protected def parallelism: ParallelismConfig = ParallelismConfig(value = 1)
      override implicit protected def mat: Materializer = spec.mat
    }

    Source
      .single(
        DatasetMetadata(
          contentChanged = Seq(Fixtures.Metadata.FileOneMetadata),
          metadataChanged = Seq(Fixtures.Metadata.FileTwoMetadata, Fixtures.Metadata.FileThreeMetadata)
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

        mockCoreClient.statistics(MockServerCoreEndpointClient.Statistic.CratePulled) should be(0)
        mockCoreClient.statistics(MockServerCoreEndpointClient.Statistic.CratePushed) should be(1)
      }
  }
}
