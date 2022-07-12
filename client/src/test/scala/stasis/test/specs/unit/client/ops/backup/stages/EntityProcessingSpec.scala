package stasis.test.specs.unit.client.ops.backup.stages

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl.{Flow, Source}
import akka.util.ByteString
import org.scalatest.Assertion
import org.scalatest.concurrent.Eventually
import stasis.client.analysis.Checksum
import stasis.client.api.clients.Clients
import stasis.client.encryption.secrets.DeviceSecret
import stasis.client.model.{EntityMetadata, SourceEntity}
import stasis.client.ops.ParallelismConfig
import stasis.client.ops.backup.Providers
import stasis.client.ops.backup.stages.EntityProcessing
import stasis.client.ops.exceptions.{EntityProcessingFailure, OperationStopped}
import stasis.core.packaging.Crate
import stasis.core.routing.Node
import stasis.shared.model.datasets.DatasetDefinition
import stasis.shared.ops.Operation
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.mocks._
import stasis.test.specs.unit.client.{Fixtures, ResourceHelpers}

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

class EntityProcessingSpec extends AsyncUnitSpec with ResourceHelpers with Eventually { spec =>
  "A Backup EntityProcessing stage" should "extract and expect file metadata" in {
    val entity = SourceEntity(
      path = Fixtures.Metadata.FileOneMetadata.path,
      existingMetadata = None,
      currentMetadata = Fixtures.Metadata.FileOneMetadata
    )

    EntityProcessing
      .expectFileMetadata(entity = entity)
      .map { metadata =>
        metadata should be(Fixtures.Metadata.FileOneMetadata)
      }
  }

  it should "fail if unexpected target entity metadata is provided" in {
    val entity = SourceEntity(
      path = Fixtures.Metadata.DirectoryOneMetadata.path,
      existingMetadata = None,
      currentMetadata = Fixtures.Metadata.DirectoryOneMetadata
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

  it should "calculate expected parts for an entity" in {
    val fileEntity = SourceEntity(
      path = Fixtures.Metadata.FileOneMetadata.path,
      existingMetadata = None,
      currentMetadata = Fixtures.Metadata.FileOneMetadata.copy(size = 10)
    )

    val directoryEntity = SourceEntity(
      path = Fixtures.Metadata.DirectoryOneMetadata.path,
      existingMetadata = None,
      currentMetadata = Fixtures.Metadata.DirectoryOneMetadata
    )

    val fileEntityWithoutChanges = SourceEntity(
      path = Fixtures.Metadata.FileOneMetadata.path,
      existingMetadata = Some(Fixtures.Metadata.FileOneMetadata),
      currentMetadata = Fixtures.Metadata.FileOneMetadata
    )

    an[IllegalArgumentException] should be thrownBy EntityProcessing.expectedParts(entity = fileEntity, withMaximumPartSize = 0)

    EntityProcessing.expectedParts(entity = fileEntity, withMaximumPartSize = 1) should be(10)
    EntityProcessing.expectedParts(entity = fileEntity, withMaximumPartSize = 2) should be(5)
    EntityProcessing.expectedParts(entity = fileEntity, withMaximumPartSize = 3) should be(4)
    EntityProcessing.expectedParts(entity = fileEntity, withMaximumPartSize = 4) should be(3)
    EntityProcessing.expectedParts(entity = fileEntity, withMaximumPartSize = 5) should be(2)
    EntityProcessing.expectedParts(entity = fileEntity, withMaximumPartSize = 6) should be(2)
    EntityProcessing.expectedParts(entity = fileEntity, withMaximumPartSize = 7) should be(2)
    EntityProcessing.expectedParts(entity = fileEntity, withMaximumPartSize = 8) should be(2)
    EntityProcessing.expectedParts(entity = fileEntity, withMaximumPartSize = 9) should be(2)
    EntityProcessing.expectedParts(entity = fileEntity, withMaximumPartSize = 10) should be(1)
    EntityProcessing.expectedParts(entity = fileEntity, withMaximumPartSize = 11) should be(1)

    EntityProcessing.expectedParts(entity = directoryEntity, withMaximumPartSize = 1) should be(0)
    EntityProcessing.expectedParts(entity = directoryEntity, withMaximumPartSize = 10) should be(0)
    EntityProcessing.expectedParts(entity = directoryEntity, withMaximumPartSize = 100) should be(0)

    EntityProcessing.expectedParts(entity = fileEntityWithoutChanges, withMaximumPartSize = 1) should be(0)
    EntityProcessing.expectedParts(entity = fileEntityWithoutChanges, withMaximumPartSize = 10) should be(0)
    EntityProcessing.expectedParts(entity = fileEntityWithoutChanges, withMaximumPartSize = 100) should be(0)
  }

  it should "process files with changed content and metadata" in {
    val sourceFile1Metadata = "/ops/source-file-1".asTestResource.extractFileMetadata(
      withChecksum = 1,
      withCrate = Crate.generateId()
    )

    val sourceFile2Metadata = "/ops/source-file-2".asTestResource.extractFileMetadata(
      withChecksum = 2,
      withCrate = Crate.generateId()
    )

    val sourceFile3Metadata = "/ops/source-file-3".asTestResource.extractFileMetadata(
      withChecksum = 3,
      withCrate = Crate.generateId()
    )

    val sourceFile1 = SourceEntity(
      path = sourceFile1Metadata.path,
      existingMetadata = None,
      currentMetadata = sourceFile1Metadata
    )

    val sourceFile2 = SourceEntity(
      path = sourceFile2Metadata.path,
      existingMetadata = Some(sourceFile2Metadata.copy(isHidden = true)),
      currentMetadata = sourceFile2Metadata
    )

    val sourceFile3 = SourceEntity(
      path = sourceFile3Metadata.path,
      existingMetadata = Some(sourceFile3Metadata.copy(checksum = BigInt(9999))),
      currentMetadata = sourceFile3Metadata
    )

    val mockStaging = new MockFileStaging()
    val mockCompression = MockCompression()
    val mockEncryption = new MockEncryption()
    val mockCoreClient = MockServerCoreEndpointClient()
    val mockTracker = new MockBackupTracker
    val mockTelemetry = MockClientTelemetryContext()

    implicit val operationId: Operation.Id = Operation.generateId()

    val stage = new EntityProcessing {
      override protected def targetDataset: DatasetDefinition = Fixtures.Datasets.Default
      override protected def deviceSecret: DeviceSecret = Fixtures.Secrets.Default
      override protected def providers: Providers =
        Providers(
          checksum = Checksum.MD5,
          staging = mockStaging,
          compression = mockCompression,
          encryptor = mockEncryption,
          decryptor = mockEncryption,
          clients = Clients(
            api = MockServerApiEndpointClient(),
            core = mockCoreClient
          ),
          track = mockTracker,
          telemetry = mockTelemetry
        )
      override protected def parallelism: ParallelismConfig = ParallelismConfig(entities = 1, entityParts = 1)
      override protected def maxChunkSize: Int = 8192
      override protected def maxPartSize: Long = 16384
      override implicit protected def mat: Materializer = SystemMaterializer(system).materializer
      override implicit protected def ec: ExecutionContext = spec.system.dispatcher
    }

    implicit val killSwitch: SharedKillSwitch = KillSwitches.shared("test")

    Source(List(sourceFile1, sourceFile2, sourceFile3))
      .via(stage.entityProcessing)
      .runFold(Seq.empty[Either[EntityMetadata, EntityMetadata]])(_ :+ _)
      .map { stageOutput =>
        stageOutput.toList match {
          case Left(actualSourceFile1Metadata: EntityMetadata.File) // content changed
              :: Right(actualSourceFile2Metadata: EntityMetadata.File) // metadata changed
              :: Left(actualSourceFile3Metadata: EntityMetadata.File) // content changed
              :: Nil =>
            actualSourceFile1Metadata should be(sourceFile1Metadata.copy(crates = actualSourceFile1Metadata.crates))
            actualSourceFile2Metadata should be(sourceFile2Metadata.copy(crates = actualSourceFile2Metadata.crates))
            actualSourceFile3Metadata should be(sourceFile3Metadata.copy(crates = actualSourceFile3Metadata.crates))

            actualSourceFile1Metadata.crates.size should be(1)
            actualSourceFile2Metadata.crates.size should be(1)
            actualSourceFile3Metadata.crates.size should be(1)

          case other =>
            fail(s"Unexpected result received: [$other]")
        }

        eventually[Assertion] {
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

          mockTracker.statistics(MockBackupTracker.Statistic.EntityDiscovered) should be(0)
          mockTracker.statistics(MockBackupTracker.Statistic.SpecificationProcessed) should be(0)
          mockTracker.statistics(MockBackupTracker.Statistic.EntityExamined) should be(0)
          mockTracker.statistics(MockBackupTracker.Statistic.EntityCollected) should be(0)
          mockTracker.statistics(MockBackupTracker.Statistic.EntityProcessingStarted) should be(3)
          mockTracker.statistics(MockBackupTracker.Statistic.EntityPartProcessed) should be(2)
          mockTracker.statistics(MockBackupTracker.Statistic.EntityProcessed) should be(3)
          mockTracker.statistics(MockBackupTracker.Statistic.MetadataCollected) should be(0)
          mockTracker.statistics(MockBackupTracker.Statistic.MetadataPushed) should be(0)
          mockTracker.statistics(MockBackupTracker.Statistic.FailureEncountered) should be(0)
          mockTracker.statistics(MockBackupTracker.Statistic.Completed) should be(0)

          mockTelemetry.ops.backup.entityExamined should be(0)
          mockTelemetry.ops.backup.entityCollected should be(0)
          mockTelemetry.ops.backup.entityChunkProcessed should be >= 6
          mockTelemetry.ops.backup.entityProcessed should be(3)
        }
      }
  }

  it should "process files with changed content size above maximum part size" in {
    val largeSourceFileMetadata = "/ops/large-source-file".asTestResource.extractFileMetadata(
      withChecksum = 1,
      withCrate = Crate.generateId()
    )

    val largeSourceFile = SourceEntity(
      path = largeSourceFileMetadata.path,
      existingMetadata = None,
      currentMetadata = largeSourceFileMetadata
    )

    val mockStaging = new MockFileStaging()
    val mockCompression = new MockCompression() {
      override def compress: Flow[ByteString, ByteString, NotUsed] = Flow[ByteString].map(identity)
    }
    val mockEncryption = new MockEncryption()
    val mockCoreClient = MockServerCoreEndpointClient()
    val mockTracker = new MockBackupTracker
    val mockTelemetry = MockClientTelemetryContext()

    implicit val operationId: Operation.Id = Operation.generateId()

    val stage = new EntityProcessing {
      override protected def targetDataset: DatasetDefinition = Fixtures.Datasets.Default
      override protected def deviceSecret: DeviceSecret = Fixtures.Secrets.Default
      override protected def providers: Providers =
        Providers(
          checksum = Checksum.MD5,
          staging = mockStaging,
          compression = mockCompression,
          encryptor = mockEncryption,
          decryptor = mockEncryption,
          clients = Clients(
            api = MockServerApiEndpointClient(),
            core = mockCoreClient
          ),
          track = mockTracker,
          telemetry = mockTelemetry
        )
      override protected def parallelism: ParallelismConfig = ParallelismConfig(entities = 1, entityParts = 1)
      override protected def maxChunkSize: Int = 5
      override protected def maxPartSize: Long = 10
      override implicit protected def mat: Materializer = SystemMaterializer(system).materializer
      override implicit protected def ec: ExecutionContext = spec.system.dispatcher
    }

    implicit val killSwitch: SharedKillSwitch = KillSwitches.shared("test")

    Source(List(largeSourceFile))
      .via(stage.entityProcessing)
      .runFold(Seq.empty[Either[EntityMetadata, EntityMetadata]])(_ :+ _)
      .map { stageOutput =>
        val expectedParts = 4 // 3 x 10 chars + 1 x 6 chars (36 total)
        val expectedChunks = expectedParts * 2 // part size is twice the chunk size

        stageOutput.toList match {
          case Left(actualSourceFileMetadata: EntityMetadata.File) :: Nil =>
            actualSourceFileMetadata should be(largeSourceFileMetadata.copy(crates = actualSourceFileMetadata.crates))
            actualSourceFileMetadata.crates.size should be(expectedParts)

          case other =>
            fail(s"Unexpected result received: [$other]")
        }

        eventually[Assertion] {
          mockStaging.statistics(MockFileStaging.Statistic.TemporaryCreated) should be(expectedParts)
          mockStaging.statistics(MockFileStaging.Statistic.TemporaryDiscarded) should be(expectedParts)
          mockStaging.statistics(MockFileStaging.Statistic.Destaged) should be(0)

          mockCompression.statistics(MockCompression.Statistic.Compressed) should be(0) // compression is skipped
          mockCompression.statistics(MockCompression.Statistic.Decompressed) should be(0)

          mockEncryption.statistics(MockEncryption.Statistic.FileEncrypted) should be(expectedChunks)
          mockEncryption.statistics(MockEncryption.Statistic.FileDecrypted) should be(0)
          mockEncryption.statistics(MockEncryption.Statistic.MetadataEncrypted) should be(0)
          mockEncryption.statistics(MockEncryption.Statistic.MetadataDecrypted) should be(0)

          mockCoreClient.statistics(MockServerCoreEndpointClient.Statistic.CratePulled) should be(0)
          mockCoreClient.statistics(MockServerCoreEndpointClient.Statistic.CratePushed) should be(expectedParts)

          mockTracker.statistics(MockBackupTracker.Statistic.SpecificationProcessed) should be(0)
          mockTracker.statistics(MockBackupTracker.Statistic.EntityExamined) should be(0)
          mockTracker.statistics(MockBackupTracker.Statistic.EntityCollected) should be(0)
          mockTracker.statistics(MockBackupTracker.Statistic.EntityProcessed) should be(1)
          mockTracker.statistics(MockBackupTracker.Statistic.MetadataCollected) should be(0)
          mockTracker.statistics(MockBackupTracker.Statistic.MetadataPushed) should be(0)
          mockTracker.statistics(MockBackupTracker.Statistic.FailureEncountered) should be(0)
          mockTracker.statistics(MockBackupTracker.Statistic.Completed) should be(0)

          mockTelemetry.ops.backup.entityExamined should be(0)
          mockTelemetry.ops.backup.entityCollected should be(0)
          mockTelemetry.ops.backup.entityChunkProcessed should be >= (expectedChunks * 3)
          mockTelemetry.ops.backup.entityProcessed should be(1)
        }
      }
  }

  it should "handle core push failures" in {
    val sourceFile1Metadata = "/ops/source-file-1".asTestResource.extractFileMetadata(
      withChecksum = 1,
      withCrate = Crate.generateId()
    )

    val sourceFile1 = SourceEntity(
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
    val mockTelemetry = MockClientTelemetryContext()

    implicit val operationId: Operation.Id = Operation.generateId()

    val stage = new EntityProcessing {
      override protected def targetDataset: DatasetDefinition = Fixtures.Datasets.Default
      override protected def deviceSecret: DeviceSecret = Fixtures.Secrets.Default
      override protected def providers: Providers =
        Providers(
          checksum = Checksum.MD5,
          staging = mockStaging,
          compression = mockCompression,
          encryptor = mockEncryption,
          decryptor = mockEncryption,
          clients = Clients(
            api = MockServerApiEndpointClient(),
            core = mockCoreClient
          ),
          track = mockTracker,
          telemetry = mockTelemetry
        )
      override protected def parallelism: ParallelismConfig = ParallelismConfig(entities = 1, entityParts = 1)
      override protected def maxChunkSize: Int = 8192
      override protected def maxPartSize: Long = 16384
      override implicit protected def mat: Materializer = SystemMaterializer(system).materializer
      override implicit protected def ec: ExecutionContext = spec.system.dispatcher
    }

    implicit val killSwitch: SharedKillSwitch = KillSwitches.shared("test")

    Source(List(sourceFile1))
      .via(stage.entityProcessing)
      .runFold(Seq.empty[Either[EntityMetadata, EntityMetadata]])(_ :+ _)
      .map { stageOutput =>
        fail(s"Unexpected result received: [$stageOutput]")
      }
      .recover { case NonFatal(e: EntityProcessingFailure) =>
        e.getCause shouldBe a[RuntimeException]
        e.getCause.getMessage should be("[pushDisabled] is set to [true]")

        eventually[Assertion] {
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

          mockTracker.statistics(MockBackupTracker.Statistic.SpecificationProcessed) should be(0)
          mockTracker.statistics(MockBackupTracker.Statistic.EntityExamined) should be(0)
          mockTracker.statistics(MockBackupTracker.Statistic.EntityCollected) should be(0)
          mockTracker.statistics(MockBackupTracker.Statistic.EntityProcessed) should be(0)
          mockTracker.statistics(MockBackupTracker.Statistic.MetadataCollected) should be(0)
          mockTracker.statistics(MockBackupTracker.Statistic.MetadataPushed) should be(0)
          mockTracker.statistics(MockBackupTracker.Statistic.FailureEncountered) should be(0)
          mockTracker.statistics(MockBackupTracker.Statistic.Completed) should be(0)

          mockTelemetry.ops.backup.entityExamined should be(0)
          mockTelemetry.ops.backup.entityCollected should be(0)
          mockTelemetry.ops.backup.entityChunkProcessed should be(3) // x3 == one for each step before push
          mockTelemetry.ops.backup.entityProcessed should be(0)
        }
      }
  }

  it should "fail processing a file if processing for a part fails" in {
    val failures = new AtomicInteger(0)

    // using resuming supervision to replicate behaviour of ops/Backup
    val supervision: Supervision.Decider = { e =>
      system.log.error(e, "Test failure: [{}]; resuming", e.getMessage)
      val _ = failures.incrementAndGet()
      Supervision.Resume
    }

    val largeSourceFileMetadata = "/ops/large-source-file".asTestResource.extractFileMetadata(
      withChecksum = 1,
      withCrate = Crate.generateId()
    )

    val largeSourceFile = SourceEntity(
      path = largeSourceFileMetadata.path,
      existingMetadata = None,
      currentMetadata = largeSourceFileMetadata
    )

    val maxChunksBeforeFailure = 5

    val mockStaging = new MockFileStaging()
    val mockCompression = new MockCompression() {
      override def compress: Flow[ByteString, ByteString, NotUsed] =
        Flow[ByteString].zipWithIndex
          .mapAsync(1) {
            case (_, i) if i >= maxChunksBeforeFailure => Future.failed(new RuntimeException("test failure"))
            case (element, _)                          => Future.successful(element)
          }
    }
    val mockEncryption = new MockEncryption()
    val mockCoreClient = MockServerCoreEndpointClient()
    val mockTracker = new MockBackupTracker
    val mockTelemetry = MockClientTelemetryContext()

    implicit val operationId: Operation.Id = Operation.generateId()

    val stage = new EntityProcessing {
      override protected def targetDataset: DatasetDefinition = Fixtures.Datasets.Default
      override protected def deviceSecret: DeviceSecret = Fixtures.Secrets.Default
      override protected def providers: Providers =
        Providers(
          checksum = Checksum.MD5,
          staging = mockStaging,
          compression = mockCompression,
          encryptor = mockEncryption,
          decryptor = mockEncryption,
          clients = Clients(
            api = MockServerApiEndpointClient(),
            core = mockCoreClient
          ),
          track = mockTracker,
          telemetry = mockTelemetry
        )
      override protected def parallelism: ParallelismConfig = ParallelismConfig(entities = 1, entityParts = 1)
      override protected def maxChunkSize: Int = 5
      override protected def maxPartSize: Long = 10
      override implicit protected def mat: Materializer = SystemMaterializer(system).materializer
      override implicit protected def ec: ExecutionContext = spec.system.dispatcher
    }

    implicit val killSwitch: SharedKillSwitch = KillSwitches.shared("test")

    Source(List(largeSourceFile))
      .via(stage.entityProcessing)
      .withAttributes(ActorAttributes.supervisionStrategy(supervision))
      .runFold(Seq.empty[Either[EntityMetadata, EntityMetadata]])(_ :+ _)
      .map { stageOutput =>
        stageOutput should be(empty)

        failures.get should be(1)

        val expectedParts = maxChunksBeforeFailure / 2 // each part is made of two chunks
        val expectedChunks = maxChunksBeforeFailure

        eventually[Assertion] {
          mockStaging.statistics(MockFileStaging.Statistic.TemporaryCreated) should be(expectedParts)
          mockStaging.statistics(MockFileStaging.Statistic.TemporaryDiscarded) should be(expectedParts)
          mockStaging.statistics(MockFileStaging.Statistic.Destaged) should be(0)

          mockEncryption.statistics(MockEncryption.Statistic.FileEncrypted) should be(expectedChunks - 1)
          mockEncryption.statistics(MockEncryption.Statistic.FileDecrypted) should be(0)
          mockEncryption.statistics(MockEncryption.Statistic.MetadataEncrypted) should be(0)
          mockEncryption.statistics(MockEncryption.Statistic.MetadataDecrypted) should be(0)

          mockCoreClient.statistics(MockServerCoreEndpointClient.Statistic.CratePulled) should be(0)
          mockCoreClient.statistics(MockServerCoreEndpointClient.Statistic.CratePushed) should be(0)

          mockTracker.statistics(MockBackupTracker.Statistic.SpecificationProcessed) should be(0)
          mockTracker.statistics(MockBackupTracker.Statistic.EntityExamined) should be(0)
          mockTracker.statistics(MockBackupTracker.Statistic.EntityCollected) should be(0)
          mockTracker.statistics(MockBackupTracker.Statistic.EntityProcessed) should be(0)
          mockTracker.statistics(MockBackupTracker.Statistic.MetadataCollected) should be(0)
          mockTracker.statistics(MockBackupTracker.Statistic.MetadataPushed) should be(0)
          mockTracker.statistics(MockBackupTracker.Statistic.FailureEncountered) should be(0)
          mockTracker.statistics(MockBackupTracker.Statistic.Completed) should be(0)

          mockTelemetry.ops.backup.entityExamined should be(0)
          mockTelemetry.ops.backup.entityCollected should be(0)
          // (expectedParts - 1) == number of successful parts
          // x8 == 2 parts X 4 steps for each part
          // +7 == 1 successful part (+4) and 1 unsuccessful part (+3 steps before push)
          mockTelemetry.ops.backup.entityChunkProcessed should be((expectedParts - 1) * 8 + 7)
          mockTelemetry.ops.backup.entityProcessed should be(0)
        }
      }
  }

  it should "support stopping processing via kill-switch" in {
    val sourceFile1Metadata = "/ops/source-file-1".asTestResource.extractFileMetadata(
      withChecksum = 1,
      withCrate = Crate.generateId()
    )

    val sourceFile2Metadata = "/ops/source-file-2".asTestResource.extractFileMetadata(
      withChecksum = 2,
      withCrate = Crate.generateId()
    )

    val sourceFile3Metadata = "/ops/source-file-3".asTestResource.extractFileMetadata(
      withChecksum = 3,
      withCrate = Crate.generateId()
    )

    val sourceFile1 = SourceEntity(
      path = sourceFile1Metadata.path,
      existingMetadata = None,
      currentMetadata = sourceFile1Metadata
    )

    val sourceFile2 = SourceEntity(
      path = sourceFile2Metadata.path,
      existingMetadata = Some(sourceFile2Metadata.copy(isHidden = true)),
      currentMetadata = sourceFile2Metadata
    )

    val sourceFile3 = SourceEntity(
      path = sourceFile3Metadata.path,
      existingMetadata = Some(sourceFile3Metadata.copy(checksum = BigInt(9999))),
      currentMetadata = sourceFile3Metadata
    )

    val mockStaging = new MockFileStaging()
    val mockCompression = MockCompression()
    val mockEncryption = new MockEncryption()
    val mockCoreClient = MockServerCoreEndpointClient()
    val mockTracker = new MockBackupTracker

    implicit val operationId: Operation.Id = Operation.generateId()

    val stage = new EntityProcessing {
      override protected def targetDataset: DatasetDefinition = Fixtures.Datasets.Default
      override protected def deviceSecret: DeviceSecret = Fixtures.Secrets.Default
      override protected def providers: Providers =
        Providers(
          checksum = Checksum.MD5,
          staging = mockStaging,
          compression = mockCompression,
          encryptor = mockEncryption,
          decryptor = mockEncryption,
          clients = Clients(
            api = MockServerApiEndpointClient(),
            core = mockCoreClient
          ),
          track = mockTracker,
          telemetry = MockClientTelemetryContext()
        )
      override protected def parallelism: ParallelismConfig = ParallelismConfig(entities = 1, entityParts = 1)
      override protected def maxChunkSize: Int = 8192
      override protected def maxPartSize: Long = 16384
      override implicit protected def mat: Materializer = SystemMaterializer(system).materializer
      override implicit protected def ec: ExecutionContext = spec.system.dispatcher
    }

    implicit val killSwitch: SharedKillSwitch = KillSwitches.shared("test")
    val entitiesProcessed = new AtomicInteger(0)

    Source(List(sourceFile1, sourceFile2, sourceFile3))
      .wireTap { _ =>
        val processed = entitiesProcessed.incrementAndGet()
        if (processed > 1) {
          killSwitch.abort(OperationStopped("Stopped"))
        }
      }
      .via(stage.entityProcessing)
      .runFold(Seq.empty[Either[EntityMetadata, EntityMetadata]])(_ :+ _)
      .map { result =>
        fail(s"Unexpected result received: [$result]")
      }
      .recover { case NonFatal(e: OperationStopped) =>
        e.getMessage should be("Stopped")

        eventually[Assertion] {
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
          mockCoreClient.statistics(MockServerCoreEndpointClient.Statistic.CratePushed) should be(1)

          mockTracker.statistics(MockBackupTracker.Statistic.EntityDiscovered) should be(0)
          mockTracker.statistics(MockBackupTracker.Statistic.SpecificationProcessed) should be(0)
          mockTracker.statistics(MockBackupTracker.Statistic.EntityExamined) should be(0)
          mockTracker.statistics(MockBackupTracker.Statistic.EntityCollected) should be(0)
          mockTracker.statistics(MockBackupTracker.Statistic.EntityProcessingStarted) should be >= 1
          mockTracker.statistics(MockBackupTracker.Statistic.EntityPartProcessed) should be >= 1
          mockTracker.statistics(MockBackupTracker.Statistic.EntityProcessed) should be >= 1
          mockTracker.statistics(MockBackupTracker.Statistic.MetadataCollected) should be(0)
          mockTracker.statistics(MockBackupTracker.Statistic.MetadataPushed) should be(0)
          mockTracker.statistics(MockBackupTracker.Statistic.FailureEncountered) should be(0)
          mockTracker.statistics(MockBackupTracker.Statistic.Completed) should be(0)
        }
      }
  }

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(10.seconds, 500.milliseconds)

  private implicit val system: ActorSystem = ActorSystem(name = "EntityProcessingSpec")
}
