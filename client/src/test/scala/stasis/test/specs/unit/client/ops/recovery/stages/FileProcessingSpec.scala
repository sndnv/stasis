package stasis.test.specs.unit.client.ops.recovery.stages

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Materializer}
import akka.stream.scaladsl.Source
import akka.util.ByteString
import stasis.client.analysis.Checksum
import stasis.client.api.clients.Clients
import stasis.client.encryption.secrets.DeviceSecret
import stasis.client.model.{FileMetadata, SourceFile}
import stasis.client.ops.ParallelismConfig
import stasis.client.ops.recovery.stages.FileProcessing
import stasis.client.ops.recovery.Providers
import stasis.core.packaging.Crate
import stasis.core.routing.Node
import stasis.core.routing.exceptions.PullFailure
import stasis.shared.ops.Operation
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.{Fixtures, ResourceHelpers}
import stasis.test.specs.unit.client.mocks._

import scala.concurrent.ExecutionContext
import scala.util.control.NonFatal

class FileProcessingSpec extends AsyncUnitSpec with ResourceHelpers { spec =>
  "A Recovery FileProcessing stage" should "process files with changed content and metadata" in {
    val sourceFile2Metadata = "/ops/source-file-2".asTestResource.extractMetadata(
      withChecksum = 2,
      withCrate = Crate.generateId()
    )

    val sourceFile3Metadata = "/ops/source-file-3".asTestResource.extractMetadata(
      withChecksum = 3,
      withCrate = Crate.generateId()
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
    val mockApiClient = MockServerApiEndpointClient()
    val mockCoreClient = new MockServerCoreEndpointClient(
      self = Node.generateId(),
      crates = Map(
        sourceFile2Metadata.crate -> ByteString("source-file-2"),
        sourceFile3Metadata.crate -> ByteString("source-file-3")
      )
    )
    val mockTracker = new MockRecoveryTracker

    val stage = new FileProcessing {
      override protected def deviceSecret: DeviceSecret = Fixtures.Secrets.Default
      override protected def providers: Providers = Providers(
        checksum = Checksum.MD5,
        staging = mockStaging,
        decompressor = mockCompression,
        decryptor = mockEncryption,
        clients = Clients(api = mockApiClient, core = mockCoreClient),
        track = mockTracker
      )
      override protected def parallelism: ParallelismConfig = ParallelismConfig(value = 1)
      override implicit protected def mat: Materializer = spec.mat
      override implicit protected def ec: ExecutionContext = spec.system.dispatcher
    }

    implicit val operationId: Operation.Id = Operation.generateId()

    Source(List(sourceFile2, sourceFile3))
      .via(stage.fileProcessing)
      .runFold(Seq.empty[FileMetadata])(_ :+ _)
      .map { stageOutput =>
        stageOutput should be(
          Seq(
            sourceFile2.existingMetadata.getOrElse(sourceFile2Metadata),
            sourceFile3.existingMetadata.getOrElse(sourceFile3Metadata)
          )
        )

        mockStaging.statistics(MockFileStaging.Statistic.TemporaryCreated) should be(1)
        mockStaging.statistics(MockFileStaging.Statistic.TemporaryDiscarded) should be(0)
        mockStaging.statistics(MockFileStaging.Statistic.Destaged) should be(1)

        mockCompression.statistics(MockCompression.Statistic.Compressed) should be(0)
        mockCompression.statistics(MockCompression.Statistic.Decompressed) should be(1)

        mockEncryption.statistics(MockEncryption.Statistic.FileEncrypted) should be(0)
        mockEncryption.statistics(MockEncryption.Statistic.FileDecrypted) should be(1)
        mockEncryption.statistics(MockEncryption.Statistic.MetadataEncrypted) should be(0)
        mockEncryption.statistics(MockEncryption.Statistic.MetadataDecrypted) should be(0)

        mockCoreClient.statistics(MockServerCoreEndpointClient.Statistic.CratePulled) should be(1)
        mockCoreClient.statistics(MockServerCoreEndpointClient.Statistic.CratePushed) should be(0)

        mockTracker.statistics(MockRecoveryTracker.Statistic.FileCollected) should be(0)
        mockTracker.statistics(MockRecoveryTracker.Statistic.FileProcessed) should be(2)
        mockTracker.statistics(MockRecoveryTracker.Statistic.MetadataApplied) should be(0)
        mockTracker.statistics(MockRecoveryTracker.Statistic.FailureEncountered) should be(0)
        mockTracker.statistics(MockRecoveryTracker.Statistic.Completed) should be(0)
      }
  }

  it should "fail if no existing metadata is found" in {
    val sourceFile1Metadata = "/ops/source-file-2".asTestResource.extractMetadata(
      withChecksum = 2,
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
    val mockApiClient = MockServerApiEndpointClient()
    val mockCoreClient = MockServerCoreEndpointClient()
    val mockTracker = new MockRecoveryTracker

    val stage = new FileProcessing {
      override protected def deviceSecret: DeviceSecret = Fixtures.Secrets.Default
      override protected def providers: Providers = Providers(
        checksum = Checksum.MD5,
        staging = mockStaging,
        decompressor = mockCompression,
        decryptor = mockEncryption,
        clients = Clients(api = mockApiClient, core = mockCoreClient),
        track = mockTracker
      )
      override protected def parallelism: ParallelismConfig = ParallelismConfig(value = 1)
      override implicit protected def mat: Materializer = spec.mat
      override implicit protected def ec: ExecutionContext = spec.system.dispatcher
    }

    implicit val operationId: Operation.Id = Operation.generateId()

    Source(List(sourceFile1))
      .via(stage.fileProcessing)
      .runFold(Seq.empty[FileMetadata])(_ :+ _)
      .map { stageOutput =>
        fail(s"Unexpected result received: [$stageOutput]")
      }
      .recover {
        case NonFatal(e) =>
          e shouldBe an[IllegalStateException]

          mockStaging.statistics(MockFileStaging.Statistic.TemporaryCreated) should be(0)
          mockStaging.statistics(MockFileStaging.Statistic.TemporaryDiscarded) should be(0)
          mockStaging.statistics(MockFileStaging.Statistic.Destaged) should be(0)

          mockCompression.statistics(MockCompression.Statistic.Compressed) should be(0)
          mockCompression.statistics(MockCompression.Statistic.Decompressed) should be(0)

          mockEncryption.statistics(MockEncryption.Statistic.FileEncrypted) should be(0)
          mockEncryption.statistics(MockEncryption.Statistic.FileDecrypted) should be(0)
          mockEncryption.statistics(MockEncryption.Statistic.MetadataEncrypted) should be(0)
          mockEncryption.statistics(MockEncryption.Statistic.MetadataDecrypted) should be(0)

          mockCoreClient.statistics(MockServerCoreEndpointClient.Statistic.CratePulled) should be(0)
          mockCoreClient.statistics(MockServerCoreEndpointClient.Statistic.CratePushed) should be(0)

          mockTracker.statistics(MockRecoveryTracker.Statistic.FileCollected) should be(0)
          mockTracker.statistics(MockRecoveryTracker.Statistic.FileProcessed) should be(0)
          mockTracker.statistics(MockRecoveryTracker.Statistic.MetadataApplied) should be(0)
          mockTracker.statistics(MockRecoveryTracker.Statistic.FailureEncountered) should be(0)
          mockTracker.statistics(MockRecoveryTracker.Statistic.Completed) should be(0)
      }
  }

  it should "fail if if crate could not be pulled" in {
    val sourceFile1Metadata = "/ops/source-file-2".asTestResource.extractMetadata(
      withChecksum = 2,
      withCrate = Crate.generateId()
    )

    val sourceFile1 = SourceFile(
      path = sourceFile1Metadata.path,
      existingMetadata = Some(sourceFile1Metadata.copy(checksum = BigInt(9999))),
      currentMetadata = sourceFile1Metadata
    )

    val mockStaging = new MockFileStaging()
    val mockCompression = new MockCompression()
    val mockEncryption = new MockEncryption()
    val mockApiClient = MockServerApiEndpointClient()
    val mockCoreClient = MockServerCoreEndpointClient()
    val mockTracker = new MockRecoveryTracker

    val stage = new FileProcessing {
      override protected def deviceSecret: DeviceSecret = Fixtures.Secrets.Default
      override protected def providers: Providers = Providers(
        checksum = Checksum.MD5,
        staging = mockStaging,
        decompressor = mockCompression,
        decryptor = mockEncryption,
        clients = Clients(api = mockApiClient, core = mockCoreClient),
        track = mockTracker
      )
      override protected def parallelism: ParallelismConfig = ParallelismConfig(value = 1)
      override implicit protected def mat: Materializer = spec.mat
      override implicit protected def ec: ExecutionContext = spec.system.dispatcher
    }

    implicit val operationId: Operation.Id = Operation.generateId()

    Source(List(sourceFile1))
      .via(stage.fileProcessing)
      .runFold(Seq.empty[FileMetadata])(_ :+ _)
      .map { stageOutput =>
        fail(s"Unexpected result received: [$stageOutput]")
      }
      .recover {
        case NonFatal(e) =>
          e shouldBe an[PullFailure]

          mockStaging.statistics(MockFileStaging.Statistic.TemporaryCreated) should be(0)
          mockStaging.statistics(MockFileStaging.Statistic.TemporaryDiscarded) should be(0)
          mockStaging.statistics(MockFileStaging.Statistic.Destaged) should be(0)

          mockCompression.statistics(MockCompression.Statistic.Compressed) should be(0)
          mockCompression.statistics(MockCompression.Statistic.Decompressed) should be(0)

          mockEncryption.statistics(MockEncryption.Statistic.FileEncrypted) should be(0)
          mockEncryption.statistics(MockEncryption.Statistic.FileDecrypted) should be(0)
          mockEncryption.statistics(MockEncryption.Statistic.MetadataEncrypted) should be(0)
          mockEncryption.statistics(MockEncryption.Statistic.MetadataDecrypted) should be(0)

          mockCoreClient.statistics(MockServerCoreEndpointClient.Statistic.CratePulled) should be(0)
          mockCoreClient.statistics(MockServerCoreEndpointClient.Statistic.CratePushed) should be(0)

          mockTracker.statistics(MockRecoveryTracker.Statistic.FileCollected) should be(0)
          mockTracker.statistics(MockRecoveryTracker.Statistic.FileProcessed) should be(0)
          mockTracker.statistics(MockRecoveryTracker.Statistic.MetadataApplied) should be(0)
          mockTracker.statistics(MockRecoveryTracker.Statistic.FailureEncountered) should be(0)
          mockTracker.statistics(MockRecoveryTracker.Statistic.Completed) should be(0)
      }
  }

  private implicit val system: ActorSystem = ActorSystem(name = "FileProcessingSpec")
  private implicit val mat: ActorMaterializer = ActorMaterializer()
}
