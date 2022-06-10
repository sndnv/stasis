package stasis.test.specs.unit.client.ops.recovery.stages

import java.nio.file.Paths
import akka.actor.ActorSystem
import akka.stream.scaladsl.Source
import akka.stream.{Materializer, SystemMaterializer}
import akka.util.ByteString
import org.scalatest.Assertion
import org.scalatest.concurrent.Eventually
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
import scala.concurrent.duration._
import scala.util.control.NonFatal

class EntityProcessingSpec extends AsyncUnitSpec with ResourceHelpers with Eventually { spec =>
  "A Recovery EntityProcessing stage" should "process files and directories with changed content and metadata" in {
    val targetFile2Metadata = "/ops/source-file-2".asTestResource.extractFileMetadata(
      withChecksum = 2,
      withCrate = Crate.generateId()
    )

    val targetFile3Metadata = "/ops/source-file-3".asTestResource.extractFileMetadata(
      withChecksum = 3,
      withCrate = Crate.generateId()
    )

    val targetFile4Path = "/ops/nested/source-file-4".asTestResource
    val targetFile4Metadata = targetFile4Path
      .extractFileMetadata(
        withChecksum = 3,
        withCrate = Crate.generateId()
      )
      .copy(
        crates = Map(
          Paths.get(s"${targetFile4Path}_0") -> Crate.generateId(),
          Paths.get(s"${targetFile4Path}_1") -> Crate.generateId(),
          Paths.get(s"${targetFile4Path}_2") -> Crate.generateId(),
          Paths.get(s"${targetFile4Path}_3") -> Crate.generateId()
        )
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

    val targetFile4 = TargetEntity(
      path = targetFile4Metadata.path,
      destination = TargetEntity.Destination.Default,
      existingMetadata = targetFile4Metadata,
      currentMetadata = Some(targetFile4Metadata.copy(checksum = BigInt(9999)))
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
      crates = (
        targetFile2Metadata.crates.values.map((_, ByteString("source-file-2")))
          ++ targetFile3Metadata.crates.values.map((_, ByteString("source-file-3")))
          ++ targetFile4Metadata.crates.values.map((_, ByteString("source-file-4")))
      ).toMap
    )
    val mockTracker = new MockRecoveryTracker
    val mockTelemetry = MockClientTelemetryContext()

    val stage = new EntityProcessing {
      override protected def deviceSecret: DeviceSecret = Fixtures.Secrets.Default
      override protected def providers: Providers =
        Providers(
          checksum = Checksum.MD5,
          staging = mockStaging,
          decompressor = mockCompression,
          decryptor = mockEncryption,
          clients = Clients(api = mockApiClient, core = mockCoreClient),
          track = mockTracker,
          telemetry = mockTelemetry
        )
      override protected def parallelism: ParallelismConfig = ParallelismConfig(value = 1)
      override implicit protected def mat: Materializer = SystemMaterializer(system).materializer
      override implicit protected def ec: ExecutionContext = spec.system.dispatcher
    }

    implicit val operationId: Operation.Id = Operation.generateId()

    Source(List(targetFile2, targetFile3, targetFile4, targetDirectory, ignoredDirectory))
      .via(stage.entityProcessing)
      .runFold(Seq.empty[TargetEntity])(_ :+ _)
      .map { stageOutput =>
        stageOutput should be(
          Seq(
            targetFile2,
            targetFile3,
            targetFile4,
            targetDirectory
          )
        )

        val metadataChanged = 2 // file2 + directory
        val contentChanged = 2 // file3 + file4
        val totalChanged = metadataChanged + contentChanged

        val contentCrates = 5 // 1 crate for file3 + 4 crates for file4

        eventually[Assertion] {
          mockStaging.statistics(MockFileStaging.Statistic.TemporaryCreated) should be(contentChanged)
          mockStaging.statistics(MockFileStaging.Statistic.TemporaryDiscarded) should be(0)
          mockStaging.statistics(MockFileStaging.Statistic.Destaged) should be(contentChanged)

          mockCompression.statistics(MockCompression.Statistic.Compressed) should be(0)
          mockCompression.statistics(MockCompression.Statistic.Decompressed) should be(contentCrates)

          mockEncryption.statistics(MockEncryption.Statistic.FileEncrypted) should be(0)
          mockEncryption.statistics(MockEncryption.Statistic.FileDecrypted) should be(contentCrates)
          mockEncryption.statistics(MockEncryption.Statistic.MetadataEncrypted) should be(0)
          mockEncryption.statistics(MockEncryption.Statistic.MetadataDecrypted) should be(0)

          mockCoreClient.statistics(MockServerCoreEndpointClient.Statistic.CratePulled) should be(contentCrates)
          mockCoreClient.statistics(MockServerCoreEndpointClient.Statistic.CratePushed) should be(0)

          mockTracker.statistics(MockRecoveryTracker.Statistic.EntityExamined) should be(0)
          mockTracker.statistics(MockRecoveryTracker.Statistic.EntityCollected) should be(0)
          mockTracker.statistics(MockRecoveryTracker.Statistic.EntityProcessed) should be(totalChanged)
          mockTracker.statistics(MockRecoveryTracker.Statistic.MetadataApplied) should be(0)
          mockTracker.statistics(MockRecoveryTracker.Statistic.FailureEncountered) should be(0)
          mockTracker.statistics(MockRecoveryTracker.Statistic.Completed) should be(0)

          mockTelemetry.ops.recovery.entityExamined should be(0)
          mockTelemetry.ops.recovery.entityCollected should be(0)
          mockTelemetry.ops.recovery.entityChunkProcessed should be(contentCrates * 4) // x4 == one for each step
          mockTelemetry.ops.recovery.entityProcessed should be(totalChanged)
          mockTelemetry.ops.recovery.metadataApplied should be(0)
        }
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
    val mockTelemetry = MockClientTelemetryContext()

    val stage = new EntityProcessing {
      override protected def deviceSecret: DeviceSecret = Fixtures.Secrets.Default
      override protected def providers: Providers =
        Providers(
          checksum = Checksum.MD5,
          staging = mockStaging,
          decompressor = mockCompression,
          decryptor = mockEncryption,
          clients = Clients(api = mockApiClient, core = mockCoreClient),
          track = mockTracker,
          telemetry = mockTelemetry
        )
      override protected def parallelism: ParallelismConfig = ParallelismConfig(value = 1)
      override implicit protected def mat: Materializer = SystemMaterializer(system).materializer
      override implicit protected def ec: ExecutionContext = spec.system.dispatcher
    }

    implicit val operationId: Operation.Id = Operation.generateId()

    Source(List(targetFile1))
      .via(stage.entityProcessing)
      .runFold(Seq.empty[TargetEntity])(_ :+ _)
      .map { stageOutput =>
        fail(s"Unexpected result received: [$stageOutput]")
      }
      .recover { case NonFatal(e) =>
        e shouldBe an[PullFailure]
        e.getMessage should startWith("Failed to pull crate")

        eventually[Assertion] {
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

          mockTelemetry.ops.recovery.entityExamined should be(0)
          mockTelemetry.ops.recovery.entityCollected should be(0)
          mockTelemetry.ops.recovery.entityChunkProcessed should be(0)
          mockTelemetry.ops.recovery.entityProcessed should be(0)
          mockTelemetry.ops.recovery.metadataApplied should be(0)
        }
      }
  }

  it should "fail if unexpected target entity metadata is provided" in {
    val entity = TargetEntity(
      path = Fixtures.Metadata.DirectoryOneMetadata.path,
      destination = TargetEntity.Destination.Default,
      existingMetadata = Fixtures.Metadata.DirectoryOneMetadata,
      currentMetadata = None
    )

    EntityProcessing
      .expectFileMetadata(entity = entity)
      .map { result =>
        fail(s"Unexpected result received: [$result]")
      }
      .recover { case NonFatal(e: IllegalArgumentException) =>
        e.getMessage should be(s"Expected metadata for file but directory metadata for [${entity.path}] provided")
      }
  }

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(5.seconds, 250.milliseconds)

  private implicit val system: ActorSystem = ActorSystem(name = "EntityProcessingSpec")
}
