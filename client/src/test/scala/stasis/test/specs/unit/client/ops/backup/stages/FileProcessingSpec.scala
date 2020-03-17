package stasis.test.specs.unit.client.ops.backup.stages

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Materializer}
import akka.stream.scaladsl.Source
import stasis.client.analysis.Checksum
import stasis.client.api.clients.Clients
import stasis.client.encryption.secrets.DeviceSecret
import stasis.client.model.{FileMetadata, SourceFile}
import stasis.client.ops.ParallelismConfig
import stasis.client.ops.backup.Providers
import stasis.client.ops.backup.stages.FileProcessing
import stasis.core.packaging.Crate
import stasis.core.routing.Node
import stasis.shared.model.datasets.DatasetDefinition
import stasis.shared.ops.Operation
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.{Fixtures, ResourceHelpers}
import stasis.test.specs.unit.client.mocks.{MockBackupTracker, _}

import scala.concurrent.ExecutionContext
import scala.util.control.NonFatal

class FileProcessingSpec extends AsyncUnitSpec with ResourceHelpers { spec =>
  "A Backup FileProcessing stage" should "process files with changed content and metadata" in {
    val sourceFile1Metadata = "/ops/source-file-1".asTestResource.extractMetadata(
      withChecksum = 1,
      withCrate = Crate.generateId()
    )

    val sourceFile2Metadata = "/ops/source-file-2".asTestResource.extractMetadata(
      withChecksum = 2,
      withCrate = Crate.generateId()
    )

    val sourceFile3Metadata = "/ops/source-file-3".asTestResource.extractMetadata(
      withChecksum = 3,
      withCrate = Crate.generateId()
    )

    val sourceFile1 = SourceFile(
      path = sourceFile1Metadata.path,
      existingMetadata = None,
      currentMetadata = sourceFile1Metadata
    )

    val sourceFile2 = SourceFile(
      path = sourceFile2Metadata.path,
      existingMetadata = Some(sourceFile2Metadata.copy(isHidden = true)),
      currentMetadata = sourceFile2Metadata
    )

    val sourceFile3 = SourceFile(
      path = sourceFile3Metadata.path,
      existingMetadata = Some(sourceFile3Metadata.copy(checksum = BigInt(9999))),
      currentMetadata = sourceFile3Metadata
    )

    val mockStaging = new MockFileStaging()
    val mockCompression = new MockCompression()
    val mockEncryption = new MockEncryption()
    val mockCoreClient = MockServerCoreEndpointClient()
    val mockTracker = new MockBackupTracker

    implicit val operationId: Operation.Id = Operation.generateId()

    val stage = new FileProcessing {
      override protected def targetDataset: DatasetDefinition = Fixtures.Datasets.Default
      override protected def deviceSecret: DeviceSecret = Fixtures.Secrets.Default
      override protected def providers: Providers = Providers(
        checksum = Checksum.MD5,
        staging = mockStaging,
        compressor = mockCompression,
        encryptor = mockEncryption,
        decryptor = mockEncryption,
        clients = Clients(
          api = MockServerApiEndpointClient(),
          core = mockCoreClient
        ),
        track = mockTracker
      )
      override protected def parallelism: ParallelismConfig = ParallelismConfig(value = 1)
      override implicit protected def mat: Materializer = spec.mat
      override implicit protected def ec: ExecutionContext = spec.system.dispatcher
    }

    Source(List(sourceFile1, sourceFile2, sourceFile3))
      .via(stage.fileProcessing)
      .runFold(Seq.empty[Either[FileMetadata, FileMetadata]])(_ :+ _)
      .map { stageOutput =>
        stageOutput should be(
          Seq(
            Left(sourceFile1.currentMetadata), // content changed
            Right(sourceFile2.currentMetadata), // metadata changed
            Left(sourceFile3.currentMetadata) // content changed
          )
        )

        mockStaging.statistics(MockFileStaging.Statistic.TemporaryCreated) should be(2)
        mockStaging.statistics(MockFileStaging.Statistic.TemporaryDiscarded) should be(2)
        mockStaging.statistics(MockFileStaging.Statistic.Destaged) should be(0)

        mockCompression.statistics(MockCompression.Statistic.Compressed) should be(2)
        mockCompression.statistics(MockCompression.Statistic.Decompressed) should be(0)

        mockEncryption.statistics(MockEncryption.Statistic.FileEncrypted) should be(2)
        mockEncryption.statistics(MockEncryption.Statistic.FileDecrypted) should be(0)
        mockEncryption.statistics(MockEncryption.Statistic.MetadataEncrypted) should be(0)
        mockEncryption.statistics(MockEncryption.Statistic.MetadataDecrypted) should be(0)

        mockCoreClient.statistics(MockServerCoreEndpointClient.Statistic.CratePulled) should be(0)
        mockCoreClient.statistics(MockServerCoreEndpointClient.Statistic.CratePushed) should be(2)

        mockTracker.statistics(MockBackupTracker.Statistic.FileCollected) should be(0)
        mockTracker.statistics(MockBackupTracker.Statistic.FileProcessed) should be(3)
        mockTracker.statistics(MockBackupTracker.Statistic.MetadataCollected) should be(0)
        mockTracker.statistics(MockBackupTracker.Statistic.MetadataPushed) should be(0)
        mockTracker.statistics(MockBackupTracker.Statistic.FailureEncountered) should be(0)
        mockTracker.statistics(MockBackupTracker.Statistic.Completed) should be(0)
      }
  }

  it should "handle core push failures" in {
    val sourceFile1Metadata = "/ops/source-file-1".asTestResource.extractMetadata(
      withChecksum = 1,
      withCrate = Crate.generateId()
    )

    val sourceFile1 = SourceFile(
      path = sourceFile1Metadata.path,
      existingMetadata = None,
      currentMetadata = sourceFile1Metadata
    )

    val mockStaging = new MockFileStaging()
    val mockCompression = new MockCompression()
    val mockEncryption = new MockEncryption()
    val mockCoreClient = new MockServerCoreEndpointClient(
      self = Node.generateId(),
      crates = Map.empty,
      pushDisabled = true
    )
    val mockTracker = new MockBackupTracker

    implicit val operationId: Operation.Id = Operation.generateId()

    val stage = new FileProcessing {
      override protected def targetDataset: DatasetDefinition = Fixtures.Datasets.Default
      override protected def deviceSecret: DeviceSecret = Fixtures.Secrets.Default
      override protected def providers: Providers = Providers(
        checksum = Checksum.MD5,
        staging = mockStaging,
        compressor = mockCompression,
        encryptor = mockEncryption,
        decryptor = mockEncryption,
        clients = Clients(
          api = MockServerApiEndpointClient(),
          core = mockCoreClient
        ),
        track = mockTracker
      )
      override protected def parallelism: ParallelismConfig = ParallelismConfig(value = 1)
      override implicit protected def mat: Materializer = spec.mat
      override implicit protected def ec: ExecutionContext = spec.system.dispatcher
    }

    Source(List(sourceFile1))
      .via(stage.fileProcessing)
      .runFold(Seq.empty[Either[FileMetadata, FileMetadata]])(_ :+ _)
      .map { stageOutput =>
        fail(s"Unexpected result received: [$stageOutput]")
      }
      .recover {
        case NonFatal(e) =>
          e shouldBe a[RuntimeException]
          e.getMessage should be("[pushDisabled] is set to [true]")

          mockStaging.statistics(MockFileStaging.Statistic.TemporaryCreated) should be(1)
          mockStaging.statistics(MockFileStaging.Statistic.TemporaryDiscarded) should be(1)
          mockStaging.statistics(MockFileStaging.Statistic.Destaged) should be(0)

          mockCompression.statistics(MockCompression.Statistic.Compressed) should be(1)
          mockCompression.statistics(MockCompression.Statistic.Decompressed) should be(0)

          mockEncryption.statistics(MockEncryption.Statistic.FileEncrypted) should be(1)
          mockEncryption.statistics(MockEncryption.Statistic.FileDecrypted) should be(0)
          mockEncryption.statistics(MockEncryption.Statistic.MetadataEncrypted) should be(0)
          mockEncryption.statistics(MockEncryption.Statistic.MetadataDecrypted) should be(0)

          mockCoreClient.statistics(MockServerCoreEndpointClient.Statistic.CratePulled) should be(0)
          mockCoreClient.statistics(MockServerCoreEndpointClient.Statistic.CratePushed) should be(0)

          mockTracker.statistics(MockBackupTracker.Statistic.FileCollected) should be(0)
          mockTracker.statistics(MockBackupTracker.Statistic.FileProcessed) should be(0)
          mockTracker.statistics(MockBackupTracker.Statistic.MetadataCollected) should be(0)
          mockTracker.statistics(MockBackupTracker.Statistic.MetadataPushed) should be(0)
          mockTracker.statistics(MockBackupTracker.Statistic.FailureEncountered) should be(0)
          mockTracker.statistics(MockBackupTracker.Statistic.Completed) should be(0)
      }
  }

  private implicit val system: ActorSystem = ActorSystem(name = "FileProcessingSpec")
  private implicit val mat: ActorMaterializer = ActorMaterializer()
}