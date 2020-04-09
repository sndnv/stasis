package stasis.test.specs.unit.client.ops.recovery.stages

import akka.actor.ActorSystem
import akka.stream.scaladsl.Source
import akka.stream.{ActorMaterializer, Materializer}
import akka.util.ByteString
import stasis.client.analysis.Checksum
import stasis.client.api.clients.Clients
import stasis.client.encryption.secrets.DeviceSecret
import stasis.client.model.TargetEntity
import stasis.client.ops.ParallelismConfig
import stasis.client.ops.recovery.Providers
import stasis.client.ops.recovery.stages.EntityProcessing
import stasis.core.packaging.Crate
import stasis.core.routing.Node
import stasis.core.routing.exceptions.PullFailure
import stasis.shared.ops.Operation
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.mocks._
import stasis.test.specs.unit.client.{Fixtures, ResourceHelpers}

import scala.concurrent.ExecutionContext
import scala.util.control.NonFatal

class EntityProcessingSpec extends AsyncUnitSpec with ResourceHelpers { spec =>
  "A Recovery EntityProcessing stage" should "process files and directories with changed content and metadata" in {
    val targetFile2Metadata = "/ops/source-file-2".asTestResource.extractFileMetadata(
      withChecksum = 2,
      withCrate = Crate.generateId()
    )

    val targetFile3Metadata = "/ops/source-file-3".asTestResource.extractFileMetadata(
      withChecksum = 3,
      withCrate = Crate.generateId()
    )

    val targetDirectoryMetadata = "/ops/nested".asTestResource.extractDirectoryMetadata().withRootAt("/ops")

    val ignoredDirectoryMetadata = Fixtures.Metadata.DirectoryOneMetadata

    val targetDirectoryDestination = "/ops/processing".asTestResource
    targetDirectoryDestination.clear().await

    val targetFile2 = TargetEntity(
      path = targetFile2Metadata.path,
      destination = TargetEntity.Destination.Default,
      existingMetadata = targetFile2Metadata,
      currentMetadata = Some(targetFile2Metadata.copy(isHidden = true))
    )

    val targetFile3 = TargetEntity(
      path = targetFile3Metadata.path,
      destination = TargetEntity.Destination.Directory(path = targetDirectoryDestination, keepDefaultStructure = false),
      existingMetadata = targetFile3Metadata,
      currentMetadata = Some(targetFile3Metadata.copy(checksum = BigInt(9999)))
    )

    val targetDirectory = TargetEntity(
      path = targetDirectoryMetadata.path,
      destination = TargetEntity.Destination.Directory(path = targetDirectoryDestination, keepDefaultStructure = true),
      existingMetadata = targetDirectoryMetadata,
      currentMetadata = Some(targetDirectoryMetadata)
    )

    val ignoredDirectory = TargetEntity(
      path = ignoredDirectoryMetadata.path,
      destination = TargetEntity.Destination.Directory(path = targetDirectoryDestination, keepDefaultStructure = false),
      existingMetadata = ignoredDirectoryMetadata,
      currentMetadata = Some(ignoredDirectoryMetadata)
    )

    val mockStaging = new MockFileStaging()
    val mockCompression = new MockCompression()
    val mockEncryption = new MockEncryption()
    val mockApiClient = MockServerApiEndpointClient()
    val mockCoreClient = new MockServerCoreEndpointClient(
      self = Node.generateId(),
      crates = Map(
        targetFile2Metadata.crate -> ByteString("source-file-2"),
        targetFile3Metadata.crate -> ByteString("source-file-3")
      )
    )
    val mockTracker = new MockRecoveryTracker

    val stage = new EntityProcessing {
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

    Source(List(targetFile2, targetFile3, targetDirectory, ignoredDirectory))
      .via(stage.entityProcessing)
      .runFold(Seq.empty[TargetEntity])(_ :+ _)
      .map { stageOutput =>
        stageOutput should be(
          Seq(
            targetFile2,
            targetFile3,
            targetDirectory
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

        mockTracker.statistics(MockRecoveryTracker.Statistic.EntityExamined) should be(0)
        mockTracker.statistics(MockRecoveryTracker.Statistic.EntityCollected) should be(0)
        mockTracker.statistics(MockRecoveryTracker.Statistic.EntityProcessed) should be(3)
        mockTracker.statistics(MockRecoveryTracker.Statistic.MetadataApplied) should be(0)
        mockTracker.statistics(MockRecoveryTracker.Statistic.FailureEncountered) should be(0)
        mockTracker.statistics(MockRecoveryTracker.Statistic.Completed) should be(0)
      }
  }

  it should "fail if crates could not be pulled" in {
    val targetFile1Metadata = "/ops/source-file-2".asTestResource.extractFileMetadata(
      withChecksum = 2,
      withCrate = Crate.generateId()
    )

    val targetFile1 = TargetEntity(
      path = targetFile1Metadata.path,
      destination = TargetEntity.Destination.Default,
      existingMetadata = targetFile1Metadata,
      currentMetadata = Some(targetFile1Metadata.copy(checksum = BigInt(9999)))
    )

    val mockStaging = new MockFileStaging()
    val mockCompression = new MockCompression()
    val mockEncryption = new MockEncryption()
    val mockApiClient = MockServerApiEndpointClient()
    val mockCoreClient = MockServerCoreEndpointClient()
    val mockTracker = new MockRecoveryTracker

    val stage = new EntityProcessing {
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

    Source(List(targetFile1))
      .via(stage.entityProcessing)
      .runFold(Seq.empty[TargetEntity])(_ :+ _)
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

          mockTracker.statistics(MockRecoveryTracker.Statistic.EntityExamined) should be(0)
          mockTracker.statistics(MockRecoveryTracker.Statistic.EntityCollected) should be(0)
          mockTracker.statistics(MockRecoveryTracker.Statistic.EntityProcessed) should be(0)
          mockTracker.statistics(MockRecoveryTracker.Statistic.MetadataApplied) should be(0)
          mockTracker.statistics(MockRecoveryTracker.Statistic.FailureEncountered) should be(0)
          mockTracker.statistics(MockRecoveryTracker.Statistic.Completed) should be(0)
      }
  }

  private implicit val system: ActorSystem = ActorSystem(name = "EntityProcessingSpec")
  private implicit val mat: ActorMaterializer = ActorMaterializer()
}
