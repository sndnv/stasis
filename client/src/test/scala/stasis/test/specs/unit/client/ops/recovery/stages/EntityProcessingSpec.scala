package stasis.test.specs.unit.client.ops.recovery.stages

import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicInteger

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.control.NonFatal

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream._
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import org.scalatest.Assertion
import org.scalatest.concurrent.Eventually

import stasis.client.analysis.Checksum
import stasis.client.api.clients.Clients
import stasis.client.encryption.secrets.DeviceSecret
import stasis.client.model.TargetEntity
import stasis.client.ops.ParallelismConfig
import stasis.client.ops.exceptions.OperationStopped
import stasis.client.ops.recovery.Providers
import stasis.client.ops.recovery.stages.EntityProcessing
import stasis.core.packaging.Crate
import stasis.core.routing.Node
import stasis.shared.ops.Operation
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.Fixtures
import stasis.test.specs.unit.client.ResourceHelpers
import stasis.test.specs.unit.client.mocks._

class EntityProcessingSpec extends AsyncUnitSpec with ResourceHelpers with Eventually { spec =>
  "A Recovery EntityProcessing stage" should "extract part IDs from a path" in {
    EntityProcessing.partIdFromPath(Paths.get("/tmp/a__part=1")) should be(1)
    EntityProcessing.partIdFromPath(Paths.get("/tmp/a__part=12324")) should be(12324)
    EntityProcessing.partIdFromPath(Paths.get("/tmp/a__part=0")) should be(0)
    EntityProcessing.partIdFromPath(Paths.get("/tmp/a__part=-1")) should be(0)
    EntityProcessing.partIdFromPath(Paths.get("/tmp/a__part=")) should be(0)
    EntityProcessing.partIdFromPath(Paths.get("/tmp/a__part")) should be(0)
    EntityProcessing.partIdFromPath(Paths.get("/tmp/a__")) should be(0)
    EntityProcessing.partIdFromPath(Paths.get("/tmp/a_")) should be(0)
    EntityProcessing.partIdFromPath(Paths.get("/tmp/a")) should be(0)
    EntityProcessing.partIdFromPath(Paths.get("/tmp/a__part=other")) should be(0)
  }

  it should "process files and directories with changed content and metadata" in {
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
          Paths.get(s"${targetFile4Path}__part=0") -> Crate.generateId(),
          Paths.get(s"${targetFile4Path}__part=1") -> Crate.generateId(),
          Paths.get(s"${targetFile4Path}__part=2") -> Crate.generateId(),
          Paths.get(s"${targetFile4Path}__part=3") -> Crate.generateId()
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
    val mockCompression = MockCompression()
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
          compression = mockCompression,
          decryptor = mockEncryption,
          clients = Clients(api = mockApiClient, core = mockCoreClient),
          track = mockTracker,
          telemetry = mockTelemetry
        )
      override protected def parallelism: ParallelismConfig = ParallelismConfig(entities = 1, entityParts = 1)
      override implicit protected def mat: Materializer = SystemMaterializer(system).materializer
      override implicit protected def ec: ExecutionContext = spec.system.dispatcher
    }

    implicit val operationId: Operation.Id = Operation.generateId()

    implicit val killSwitch: SharedKillSwitch = KillSwitches.shared("test")

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
          mockTracker.statistics(MockRecoveryTracker.Statistic.EntityProcessingStarted) should be(totalChanged)
          mockTracker.statistics(MockRecoveryTracker.Statistic.EntityPartProcessed) should be(contentCrates)
          mockTracker.statistics(MockRecoveryTracker.Statistic.EntityProcessed) should be(totalChanged)
          mockTracker.statistics(MockRecoveryTracker.Statistic.MetadataApplied) should be(0)
          mockTracker.statistics(MockRecoveryTracker.Statistic.FailureEncountered) should be(0)
          mockTracker.statistics(MockRecoveryTracker.Statistic.Completed) should be(0)

          mockTelemetry.client.ops.recovery.entityExamined should be(0)
          mockTelemetry.client.ops.recovery.entityCollected should be(0)
          mockTelemetry.client.ops.recovery.entityChunkProcessed should be(contentCrates * 4) // x4 == one for each step
          mockTelemetry.client.ops.recovery.entityProcessed should be(totalChanged)
          mockTelemetry.client.ops.recovery.metadataApplied should be(0)
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
          compression = MockCompression(),
          decryptor = mockEncryption,
          clients = Clients(api = mockApiClient, core = mockCoreClient),
          track = mockTracker,
          telemetry = mockTelemetry
        )
      override protected def parallelism: ParallelismConfig = ParallelismConfig(entities = 1, entityParts = 1)
      override implicit protected def mat: Materializer = SystemMaterializer(system).materializer
      override implicit protected def ec: ExecutionContext = spec.system.dispatcher
    }

    implicit val operationId: Operation.Id = Operation.generateId()

    implicit val killSwitch: SharedKillSwitch = KillSwitches.shared("test")

    Source(List(targetFile1))
      .via(stage.entityProcessing)
      .runFold(Seq.empty[TargetEntity])(_ :+ _)
      .map { stageOutput =>
        fail(s"Unexpected result received: [$stageOutput]")
      }
      .recover { case NonFatal(e) =>
        e.getMessage should include("Failed to pull crate")

        eventually[Assertion] {
          mockStaging.statistics(MockFileStaging.Statistic.TemporaryCreated) should be(1)
          mockStaging.statistics(MockFileStaging.Statistic.TemporaryDiscarded) should be(1)
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
          mockTracker.statistics(MockRecoveryTracker.Statistic.EntityProcessingStarted) should be(1)
          mockTracker.statistics(MockRecoveryTracker.Statistic.EntityPartProcessed) should be(0)
          mockTracker.statistics(MockRecoveryTracker.Statistic.EntityProcessed) should be(0)
          mockTracker.statistics(MockRecoveryTracker.Statistic.MetadataApplied) should be(0)
          mockTracker.statistics(MockRecoveryTracker.Statistic.FailureEncountered) should be(0)
          mockTracker.statistics(MockRecoveryTracker.Statistic.Completed) should be(0)

          mockTelemetry.client.ops.recovery.entityExamined should be(0)
          mockTelemetry.client.ops.recovery.entityCollected should be(0)
          mockTelemetry.client.ops.recovery.entityChunkProcessed should be(0)
          mockTelemetry.client.ops.recovery.entityProcessed should be(0)
          mockTelemetry.client.ops.recovery.metadataApplied should be(0)
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

  it should "fail if an unexpected number of crates are provided" in {
    val targetFile4Path = "/ops/nested/source-file-4".asTestResource
    val targetFile4Metadata = targetFile4Path
      .extractFileMetadata(
        withChecksum = 3,
        withCrate = Crate.generateId()
      )
      .copy(
        crates = Map(
          Paths.get(s"${targetFile4Path}__part=0") -> Crate.generateId(),
          Paths.get(s"${targetFile4Path}__part=1") -> Crate.generateId(),
          Paths.get(s"${targetFile4Path}__part=2") -> Crate.generateId(),
          Paths.get(s"${targetFile4Path}__part=5") -> Crate.generateId()
        )
      )

    val targetFile4 = TargetEntity(
      path = targetFile4Metadata.path,
      destination = TargetEntity.Destination.Default,
      existingMetadata = targetFile4Metadata,
      currentMetadata = Some(targetFile4Metadata.copy(checksum = BigInt(9999)))
    )

    val stage = new EntityProcessing {
      override protected def deviceSecret: DeviceSecret = Fixtures.Secrets.Default
      override protected def providers: Providers =
        Providers(
          checksum = Checksum.MD5,
          staging = new MockFileStaging(),
          compression = MockCompression(),
          decryptor = new MockEncryption(),
          clients = Clients(api = MockServerApiEndpointClient(), core = MockServerCoreEndpointClient()),
          track = new MockRecoveryTracker,
          telemetry = MockClientTelemetryContext()
        )
      override protected def parallelism: ParallelismConfig = ParallelismConfig(entities = 1, entityParts = 1)
      override implicit protected def mat: Materializer = SystemMaterializer(system).materializer
      override implicit protected def ec: ExecutionContext = spec.system.dispatcher
    }

    implicit val operationId: Operation.Id = Operation.generateId()

    implicit val killSwitch: SharedKillSwitch = KillSwitches.shared("test")

    Source(List(targetFile4))
      .via(stage.entityProcessing)
      .runFold(Seq.empty[TargetEntity])(_ :+ _)
      .map { result =>
        fail(s"Unexpected result received: [$result]")
      }
      .recover { case NonFatal(e: IllegalArgumentException) =>
        e.getMessage should include("Unexpected last part ID [5] encountered for an entity with [4] crate(s)")
      }
  }

  it should "fail if no crates are provided" in {
    val targetFile4Path = "/ops/nested/source-file-4".asTestResource
    val targetFile4Metadata = targetFile4Path
      .extractFileMetadata(
        withChecksum = 3,
        withCrate = Crate.generateId()
      )
      .copy(crates = Map.empty)

    val targetFile4 = TargetEntity(
      path = targetFile4Metadata.path,
      destination = TargetEntity.Destination.Default,
      existingMetadata = targetFile4Metadata,
      currentMetadata = Some(targetFile4Metadata.copy(checksum = BigInt(9999)))
    )

    val stage = new EntityProcessing {
      override protected def deviceSecret: DeviceSecret = Fixtures.Secrets.Default
      override protected def providers: Providers =
        Providers(
          checksum = Checksum.MD5,
          staging = new MockFileStaging(),
          compression = MockCompression(),
          decryptor = new MockEncryption(),
          clients = Clients(api = MockServerApiEndpointClient(), core = MockServerCoreEndpointClient()),
          track = new MockRecoveryTracker,
          telemetry = MockClientTelemetryContext()
        )
      override protected def parallelism: ParallelismConfig = ParallelismConfig(entities = 1, entityParts = 1)
      override implicit protected def mat: Materializer = SystemMaterializer(system).materializer
      override implicit protected def ec: ExecutionContext = spec.system.dispatcher
    }

    implicit val operationId: Operation.Id = Operation.generateId()

    implicit val killSwitch: SharedKillSwitch = KillSwitches.shared("test")

    Source(List(targetFile4))
      .via(stage.entityProcessing)
      .runFold(Seq.empty[TargetEntity])(_ :+ _)
      .map { result =>
        fail(s"Unexpected result received: [$result]")
      }
      .recover { case NonFatal(e: IllegalArgumentException) =>
        e.getMessage should include("Unexpected last part ID [0] encountered for an entity with [0] crate(s)")
      }
  }

  it should "support stopping processing via kill-switch" in {
    val targetFile2Metadata = "/ops/source-file-2".asTestResource.extractFileMetadata(
      withChecksum = 2,
      withCrate = Crate.generateId()
    )

    val targetFile3Metadata = "/ops/source-file-3".asTestResource.extractFileMetadata(
      withChecksum = 3,
      withCrate = Crate.generateId()
    )

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

    val mockStaging = new MockFileStaging()
    val mockCompression = MockCompression()
    val mockEncryption = new MockEncryption()
    val mockApiClient = MockServerApiEndpointClient()
    val mockCoreClient = new MockServerCoreEndpointClient(
      self = Node.generateId(),
      crates = (
        targetFile2Metadata.crates.values.map((_, ByteString("source-file-2")))
          ++ targetFile3Metadata.crates.values.map((_, ByteString("source-file-3")))
      ).toMap
    )
    val mockTracker = new MockRecoveryTracker

    val stage = new EntityProcessing {
      override protected def deviceSecret: DeviceSecret = Fixtures.Secrets.Default
      override protected def providers: Providers =
        Providers(
          checksum = Checksum.MD5,
          staging = mockStaging,
          compression = mockCompression,
          decryptor = mockEncryption,
          clients = Clients(api = mockApiClient, core = mockCoreClient),
          track = mockTracker,
          telemetry = MockClientTelemetryContext()
        )
      override protected def parallelism: ParallelismConfig = ParallelismConfig(entities = 4, entityParts = 4)
      override implicit protected def mat: Materializer = SystemMaterializer(system).materializer
      override implicit protected def ec: ExecutionContext = spec.system.dispatcher
    }

    implicit val operationId: Operation.Id = Operation.generateId()

    implicit val killSwitch: SharedKillSwitch = KillSwitches.shared("test")
    val entitiesProcessed = new AtomicInteger(0)

    Source(List(targetFile2, targetFile3))
      .wireTap { _ =>
        val processed = entitiesProcessed.incrementAndGet()
        if (processed > 1) {
          killSwitch.abort(OperationStopped("Stopped"))
        }
      }
      .via(stage.entityProcessing)
      .runFold(Seq.empty[TargetEntity])(_ :+ _)
      .map { result =>
        fail(s"Unexpected result received: [$result]")
      }
      .recover { case NonFatal(e: OperationStopped) =>
        e.getMessage should be("Stopped")

        eventually[Assertion] {
          mockStaging.statistics(MockFileStaging.Statistic.TemporaryCreated) should be(1)
          mockStaging.statistics(MockFileStaging.Statistic.TemporaryDiscarded) should be(1)
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
          mockTracker.statistics(MockRecoveryTracker.Statistic.EntityProcessed) should be(1)
          mockTracker.statistics(MockRecoveryTracker.Statistic.MetadataApplied) should be(0)
          mockTracker.statistics(MockRecoveryTracker.Statistic.FailureEncountered) should be(0)
          mockTracker.statistics(MockRecoveryTracker.Statistic.Completed) should be(0)
        }
      }
  }

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(7.seconds, 300.milliseconds)

  private implicit val system: ActorSystem = ActorSystem(name = "EntityProcessingSpec")
}
