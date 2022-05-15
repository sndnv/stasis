package stasis.test.specs.unit.client.ops.backup.stages

import akka.actor.ActorSystem
import akka.stream.scaladsl.Sink
import akka.stream.{Materializer, SystemMaterializer}
import stasis.client.analysis.Checksum
import stasis.client.api.clients.Clients
import stasis.client.collection.BackupCollector
import stasis.client.collection.rules.Rule
import stasis.client.model.{DatasetMetadata, FilesystemMetadata}
import stasis.client.ops.ParallelismConfig
import stasis.client.ops.backup.Providers
import stasis.client.ops.backup.stages.EntityDiscovery
import stasis.shared.ops.Operation
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.ResourceHelpers
import stasis.test.specs.unit.client.mocks._

import java.nio.file.Paths
import scala.concurrent.ExecutionContext

class EntityDiscoverySpec extends AsyncUnitSpec with ResourceHelpers {
  "A Backup EntityDiscovery stage" should "discover files (based on rules)" in {
    val checksum = Checksum.SHA256

    val sourceDirectory1Metadata = "/ops".asTestResource.extractDirectoryMetadata()
    val sourceFile1Metadata = "/ops/source-file-1".asTestResource.extractFileMetadata(checksum)
    val sourceFile2Metadata = "/ops/source-file-2".asTestResource.extractFileMetadata(checksum)
    val sourceFile3Metadata = "/ops/source-file-3".asTestResource.extractFileMetadata(checksum)

    val sourceDirectory2Metadata = "/ops/nested".asTestResource.extractDirectoryMetadata()

    val metadata = DatasetMetadata(
      contentChanged = Map(
        sourceFile1Metadata.path -> sourceFile1Metadata.copy(isHidden = true),
        sourceFile2Metadata.path -> sourceFile2Metadata,
        sourceFile3Metadata.path -> sourceFile3Metadata.copy(checksum = BigInt(0))
      ),
      metadataChanged = Map(
        sourceDirectory1Metadata.path -> sourceDirectory1Metadata
      ),
      filesystem = FilesystemMetadata(
        entities = Map(
          sourceDirectory1Metadata.path -> FilesystemMetadata.EntityState.New,
          sourceFile1Metadata.path -> FilesystemMetadata.EntityState.New,
          sourceFile2Metadata.path -> FilesystemMetadata.EntityState.New,
          sourceFile3Metadata.path -> FilesystemMetadata.EntityState.New
        )
      )
    )

    val mockTracker = new MockBackupTracker

    val stage = new EntityDiscovery {
      override protected def collector: EntityDiscovery.Collector = EntityDiscovery.Collector.WithRules(
        rules = Seq(
          Rule(line = s"+ ${sourceDirectory1Metadata.path.toAbsolutePath} source-file-*", lineNumber = 0).get,
          Rule(line = s"+ ${sourceDirectory2Metadata.path.toAbsolutePath} source-file-*", lineNumber = 0).get
        )
      )
      override protected def latestMetadata: Option[DatasetMetadata] = Some(metadata)
      override protected def providers: Providers =
        Providers(
          checksum = checksum,
          staging = new MockFileStaging(),
          compressor = new MockCompression(),
          encryptor = new MockEncryption(),
          decryptor = new MockEncryption(),
          clients = Clients(
            api = MockServerApiEndpointClient(),
            core = MockServerCoreEndpointClient()
          ),
          track = mockTracker
        )
      override protected def parallelism: ParallelismConfig = ParallelismConfig(value = 1)
      override protected implicit def mat: Materializer = SystemMaterializer(system).materializer
      override protected implicit def ec: ExecutionContext = system.dispatcher
    }

    implicit val operationId: Operation.Id = Operation.generateId()

    val collector = stage.entityDiscovery
      .runWith(Sink.head)
      .await

    collector should be(a[BackupCollector.Default])

    val entities = collector.collect().runWith(Sink.seq).await

    entities.length should be(7) // 2 directories + 5 files

    mockTracker.statistics(MockBackupTracker.Statistic.EntityDiscovered) should be(7) // 2 directories + 5 files
    mockTracker.statistics(MockBackupTracker.Statistic.SpecificationProcessed) should be(1)
    mockTracker.statistics(MockBackupTracker.Statistic.EntityExamined) should be(0)
    mockTracker.statistics(MockBackupTracker.Statistic.EntityCollected) should be(0)
    mockTracker.statistics(MockBackupTracker.Statistic.EntityProcessed) should be(0)
    mockTracker.statistics(MockBackupTracker.Statistic.MetadataCollected) should be(0)
    mockTracker.statistics(MockBackupTracker.Statistic.MetadataPushed) should be(0)
    mockTracker.statistics(MockBackupTracker.Statistic.FailureEncountered) should be(0)
    mockTracker.statistics(MockBackupTracker.Statistic.Completed) should be(0)
  }

  it should "discover files (based on entities)" in {

    val sourceFile1Metadata = "/ops/source-file-1".asTestResource
    val sourceFile2Metadata = "/ops/source-file-2".asTestResource

    val mockTracker = new MockBackupTracker

    val stage = new EntityDiscovery {
      override protected def collector: EntityDiscovery.Collector = EntityDiscovery.Collector.WithEntities(
        entities = Seq(
          sourceFile1Metadata,
          sourceFile2Metadata,
          Paths.get("/ops/invalid-file")
        )
      )
      override protected def latestMetadata: Option[DatasetMetadata] = Some(DatasetMetadata.empty)
      override protected def providers: Providers =
        Providers(
          checksum = Checksum.SHA256,
          staging = new MockFileStaging(),
          compressor = new MockCompression(),
          encryptor = new MockEncryption(),
          decryptor = new MockEncryption(),
          clients = Clients(
            api = MockServerApiEndpointClient(),
            core = MockServerCoreEndpointClient()
          ),
          track = mockTracker
        )
      override protected def parallelism: ParallelismConfig = ParallelismConfig(value = 1)
      override protected implicit def mat: Materializer = SystemMaterializer(system).materializer
      override protected implicit def ec: ExecutionContext = system.dispatcher
    }

    implicit val operationId: Operation.Id = Operation.generateId()

    val collector = stage.entityDiscovery
      .runWith(Sink.head)
      .await

    collector should be(a[BackupCollector.Default])

    val entities = collector.collect().runWith(Sink.seq).await

    entities.length should be(2) // 2 valid files

    mockTracker.statistics(MockBackupTracker.Statistic.EntityDiscovered) should be(2) // 2 valid files
    mockTracker.statistics(MockBackupTracker.Statistic.SpecificationProcessed) should be(0)
    mockTracker.statistics(MockBackupTracker.Statistic.EntityExamined) should be(0)
    mockTracker.statistics(MockBackupTracker.Statistic.EntityCollected) should be(0)
    mockTracker.statistics(MockBackupTracker.Statistic.EntityProcessed) should be(0)
    mockTracker.statistics(MockBackupTracker.Statistic.MetadataCollected) should be(0)
    mockTracker.statistics(MockBackupTracker.Statistic.MetadataPushed) should be(0)
    mockTracker.statistics(MockBackupTracker.Statistic.FailureEncountered) should be(0)
    mockTracker.statistics(MockBackupTracker.Statistic.Completed) should be(0)
  }

  private implicit val system: ActorSystem = ActorSystem(name = "EntityDiscoverySpec")
}
