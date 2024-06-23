package stasis.test.specs.unit.client.ops.backup

import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.{Flow, Source}
import org.apache.pekko.util.ByteString
import org.apache.pekko.{Done, NotUsed}
import org.scalatest.concurrent.Eventually
import org.scalatest.{Assertion, BeforeAndAfterAll}
import stasis.client.analysis.Checksum
import stasis.client.api.clients.Clients
import stasis.client.collection.rules.Rule
import stasis.client.encryption.Aes
import stasis.client.encryption.secrets.{DeviceMetadataSecret, DeviceSecret}
import stasis.client.model.{DatasetMetadata, FilesystemMetadata}
import stasis.client.ops.ParallelismConfig
import stasis.client.ops.backup.stages.EntityDiscovery
import stasis.client.ops.backup.{Backup, Providers}
import stasis.client.ops.exceptions.OperationStopped
import stasis.client.staging.DefaultFileStaging
import stasis.core.packaging
import stasis.core.routing.Node
import stasis.shared.model.datasets.{DatasetDefinition, DatasetEntry}
import stasis.shared.model.devices.Device
import stasis.shared.model.users.User
import stasis.shared.ops.Operation
import stasis.shared.secrets.SecretsConfig
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.mocks._
import stasis.test.specs.unit.client.{Fixtures, ResourceHelpers}

import java.nio.file.{Path, Paths}
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.control.NonFatal

class BackupSpec extends AsyncUnitSpec with ResourceHelpers with Eventually with BeforeAndAfterAll {
  "A Backup operation" should "process backups for entire configured file collection" in {
    val sourceDirectory1Metadata = "/ops".asTestResource.extractDirectoryMetadata()
    val sourceFile1Metadata = "/ops/source-file-1".asTestResource.extractFileMetadata(checksum)
    val sourceFile2Metadata = "/ops/source-file-2".asTestResource.extractFileMetadata(checksum)
    val sourceFile3Metadata = "/ops/source-file-3".asTestResource.extractFileMetadata(checksum)

    val sourceDirectory2Metadata = "/ops/nested".asTestResource.extractDirectoryMetadata()

    val mockApiClient = MockServerApiEndpointClient()
    val mockCoreClient = MockServerCoreEndpointClient()
    val tracker = new MockBackupTracker

    val backup = createBackup(
      collector = Backup.Descriptor.Collector.WithRules(
        rules = Seq(
          Rule(line = s"+ ${sourceDirectory1Metadata.path.toAbsolutePath} source-file-*", lineNumber = 0).get,
          Rule(line = s"+ ${sourceDirectory2Metadata.path.toAbsolutePath} source-file-*", lineNumber = 0).get
        )
      ),
      latestMetadata = DatasetMetadata(
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
      ),
      clients = Clients(
        api = mockApiClient,
        core = mockCoreClient
      ),
      tracker = tracker
    )

    backup.start().map { _ =>
      eventually[Assertion] {
        // dataset entry for backup created; metadata crate pushed
        // /ops directory is unchanged
        // /ops/source-file-1 has metadata changes only; /ops/source-file-2 is unchanged; crate for /ops/source-file-3 pushed;
        // /ops/nested directory is new
        // /ops/nested/source-file-4 and /ops/nested/source-file-5 are new
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntryCreated) should be(1)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetDefinitionCreated) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.PublicSchedulesRetrieved) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.PublicScheduleRetrieved) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.UserRetrieved) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.UserSaltReset) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.UserPasswordUpdated) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DeviceRetrieved) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DeviceKeyPushed) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DeviceKeyPulled) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.Ping) should be(0)

        mockCoreClient.statistics(MockServerCoreEndpointClient.Statistic.CratePulled) should be(0)
        mockCoreClient.statistics(MockServerCoreEndpointClient.Statistic.CratePushed) should be(4)

        tracker.statistics(MockBackupTracker.Statistic.Started) should be(1)
        tracker.statistics(MockBackupTracker.Statistic.EntityDiscovered) should be(7) // 2 directories + 5 files
        tracker.statistics(MockBackupTracker.Statistic.SpecificationProcessed) should be(1)
        tracker.statistics(MockBackupTracker.Statistic.EntityExamined) should be(7) // 2 directories + 5 files
        tracker.statistics(MockBackupTracker.Statistic.EntityCollected) should be(5) // 2 unchanged + 5 changed entities
        tracker.statistics(MockBackupTracker.Statistic.EntityProcessingStarted) should be(5) // 2 unchanged + 5 changed entities
        tracker.statistics(MockBackupTracker.Statistic.EntityPartProcessed) should be(3) // 3 files
        tracker.statistics(MockBackupTracker.Statistic.EntityProcessed) should be(5) // 2 unchanged + 5 changed entities
        tracker.statistics(MockBackupTracker.Statistic.MetadataCollected) should be(1)
        tracker.statistics(MockBackupTracker.Statistic.MetadataPushed) should be(1)
        tracker.statistics(MockBackupTracker.Statistic.FailureEncountered) should be(0)
        tracker.statistics(MockBackupTracker.Statistic.Completed) should be(1)
      }
    }
  }

  it should "process backups for specific files" in {
    val sourceFile1Metadata = "/ops/source-file-1".asTestResource.extractFileMetadata(checksum)
    val sourceFile2Metadata = "/ops/source-file-2".asTestResource.extractFileMetadata(checksum)
    val sourceFile3Metadata = "/ops/source-file-3".asTestResource.extractFileMetadata(checksum)

    val mockApiClient = MockServerApiEndpointClient()
    val mockCoreClient = MockServerCoreEndpointClient()
    val tracker = new MockBackupTracker

    val backup = createBackup(
      collector = Backup.Descriptor.Collector.WithEntities(
        entities = List(
          sourceFile1Metadata.path,
          sourceFile2Metadata.path,
          sourceFile3Metadata.path
        )
      ),
      latestMetadata = DatasetMetadata(
        contentChanged = Map(
          sourceFile1Metadata.path -> sourceFile1Metadata.copy(isHidden = true),
          sourceFile2Metadata.path -> sourceFile2Metadata,
          sourceFile3Metadata.path -> sourceFile3Metadata.copy(checksum = BigInt(0))
        ),
        metadataChanged = Map.empty,
        filesystem = FilesystemMetadata(
          entities = Map(
            sourceFile1Metadata.path -> FilesystemMetadata.EntityState.New,
            sourceFile2Metadata.path -> FilesystemMetadata.EntityState.New,
            sourceFile3Metadata.path -> FilesystemMetadata.EntityState.New
          )
        )
      ),
      clients = Clients(
        api = mockApiClient,
        core = mockCoreClient
      ),
      tracker = tracker
    )

    backup.start().map { _ =>
      eventually[Assertion] {
        // dataset entry for backup created; metadata crate pushed
        // source-file-1 has metadata changes only; source-file-2 is unchanged; crate for source-file-3 pushed;
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntryCreated) should be(1)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetDefinitionCreated) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.PublicSchedulesRetrieved) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.PublicScheduleRetrieved) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.UserRetrieved) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.UserSaltReset) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.UserPasswordUpdated) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DeviceRetrieved) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DeviceKeyPushed) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DeviceKeyPulled) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.Ping) should be(0)

        mockCoreClient.statistics(MockServerCoreEndpointClient.Statistic.CratePulled) should be(0)
        mockCoreClient.statistics(MockServerCoreEndpointClient.Statistic.CratePushed) should be(2)

        tracker.statistics(MockBackupTracker.Statistic.Started) should be(1)
        tracker.statistics(MockBackupTracker.Statistic.EntityDiscovered) should be(3)
        tracker.statistics(MockBackupTracker.Statistic.SpecificationProcessed) should be(0)
        tracker.statistics(MockBackupTracker.Statistic.EntityExamined) should be(3)
        tracker.statistics(MockBackupTracker.Statistic.EntityCollected) should be(2)
        tracker.statistics(MockBackupTracker.Statistic.EntityProcessingStarted) should be(2)
        tracker.statistics(MockBackupTracker.Statistic.EntityPartProcessed) should be(1)
        tracker.statistics(MockBackupTracker.Statistic.EntityProcessed) should be(2)
        tracker.statistics(MockBackupTracker.Statistic.MetadataCollected) should be(1)
        tracker.statistics(MockBackupTracker.Statistic.MetadataPushed) should be(1)
        tracker.statistics(MockBackupTracker.Statistic.FailureEncountered) should be(0)
        tracker.statistics(MockBackupTracker.Statistic.Completed) should be(1)
      }
    }
  }

  it should "process backups resumed with existing state" in {
    val sourceFile1Metadata = "/ops/source-file-1".asTestResource.extractFileMetadata(checksum)
    val sourceFile2Metadata = "/ops/source-file-2".asTestResource.extractFileMetadata(checksum)
    val sourceFile3Metadata = "/ops/source-file-3".asTestResource.extractFileMetadata(checksum)

    val mockApiClient = MockServerApiEndpointClient()
    val mockCoreClient = MockServerCoreEndpointClient()
    val tracker = new MockBackupTracker

    val backup = createBackup(
      collector = Backup.Descriptor.Collector.WithState(
        state = Fixtures.State.BackupTwoState.copy(
          entities = Fixtures.State.BackupTwoState.entities.copy(
            discovered = Set(
              sourceFile1Metadata.path,
              sourceFile2Metadata.path,
              sourceFile3Metadata.path
            )
          )
        )
      ),
      latestMetadata = DatasetMetadata(
        contentChanged = Map(
          sourceFile1Metadata.path -> sourceFile1Metadata.copy(isHidden = true),
          sourceFile2Metadata.path -> sourceFile2Metadata,
          sourceFile3Metadata.path -> sourceFile3Metadata.copy(checksum = BigInt(0))
        ),
        metadataChanged = Map.empty,
        filesystem = FilesystemMetadata(
          entities = Map(
            sourceFile1Metadata.path -> FilesystemMetadata.EntityState.New,
            sourceFile2Metadata.path -> FilesystemMetadata.EntityState.New,
            sourceFile3Metadata.path -> FilesystemMetadata.EntityState.New
          )
        )
      ),
      clients = Clients(
        api = mockApiClient,
        core = mockCoreClient
      ),
      tracker = tracker
    )

    backup.start().map { _ =>
      eventually[Assertion] {
        // dataset entry for backup created; metadata crate pushed
        // source-file-1 has metadata changes only; source-file-2 is unchanged; crate for source-file-3 pushed;
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntryCreated) should be(1)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetDefinitionCreated) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.PublicSchedulesRetrieved) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.PublicScheduleRetrieved) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.UserRetrieved) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.UserSaltReset) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.UserPasswordUpdated) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DeviceRetrieved) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DeviceKeyPushed) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DeviceKeyPulled) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.Ping) should be(0)

        mockCoreClient.statistics(MockServerCoreEndpointClient.Statistic.CratePulled) should be(0)
        mockCoreClient.statistics(MockServerCoreEndpointClient.Statistic.CratePushed) should be(2)

        tracker.statistics(MockBackupTracker.Statistic.Started) should be(0)
        tracker.statistics(MockBackupTracker.Statistic.EntityDiscovered) should be(0)
        tracker.statistics(MockBackupTracker.Statistic.SpecificationProcessed) should be(0)
        tracker.statistics(MockBackupTracker.Statistic.EntityExamined) should be(3)
        tracker.statistics(MockBackupTracker.Statistic.EntityCollected) should be(2)
        tracker.statistics(MockBackupTracker.Statistic.EntityProcessingStarted) should be(2)
        tracker.statistics(MockBackupTracker.Statistic.EntityPartProcessed) should be(1)
        tracker.statistics(MockBackupTracker.Statistic.EntityProcessed) should be(2)
        tracker.statistics(MockBackupTracker.Statistic.MetadataCollected) should be(1)
        tracker.statistics(MockBackupTracker.Statistic.MetadataPushed) should be(1)
        tracker.statistics(MockBackupTracker.Statistic.FailureEncountered) should be(0)
        tracker.statistics(MockBackupTracker.Statistic.Completed) should be(1)
      }
    }
  }

  it should "handle failures of backups for individual files" in {
    val sourceFile1Metadata = "/ops/source-file-1".asTestResource.extractFileMetadata(checksum)
    val sourceFile2Metadata = "/ops/source-file-2".asTestResource.extractFileMetadata(checksum)
    val sourceFile3Metadata = "/ops/source-file-3".asTestResource.extractFileMetadata(checksum)

    val pushFailed = new AtomicBoolean(false)

    val mockApiClient = MockServerApiEndpointClient()
    val mockCoreClient = new MockServerCoreEndpointClient(
      self = Node.generateId(),
      crates = Map.empty
    ) {
      override def push(manifest: packaging.Manifest, content: Source[ByteString, NotUsed]): Future[Done] =
        super
          .push(manifest, content)
          .flatMap { _ =>
            if (pushFailed.get()) {
              Future.successful(Done)
            } else {
              pushFailed.set(true)
              Future.failed(new RuntimeException("Test failure"))
            }
          }
    }
    val tracker = new MockBackupTracker

    val backup = createBackup(
      collector = Backup.Descriptor.Collector.WithEntities(
        entities = List(
          sourceFile1Metadata.path,
          sourceFile2Metadata.path,
          sourceFile3Metadata.path,
          Paths.get("/ops/invalid-file")
        )
      ),
      latestMetadata = DatasetMetadata(
        contentChanged = Map(
          sourceFile1Metadata.path -> sourceFile1Metadata.copy(isHidden = true),
          sourceFile2Metadata.path -> sourceFile2Metadata
        ),
        metadataChanged = Map.empty,
        filesystem = FilesystemMetadata(
          entities = Map(
            sourceFile1Metadata.path -> FilesystemMetadata.EntityState.New,
            sourceFile2Metadata.path -> FilesystemMetadata.EntityState.New
          )
        )
      ),
      clients = Clients(
        api = mockApiClient,
        core = mockCoreClient
      ),
      tracker = tracker
    )

    backup.start().map { _ =>
      eventually[Assertion] {
        // dataset entry for backup created; metadata crate pushed
        // source-file-1 has metadata changes only; source-file-2 is unchanged, source-file-3 is new; last file is invalid/missing;
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntryCreated) should be(1)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetDefinitionCreated) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.PublicSchedulesRetrieved) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.PublicScheduleRetrieved) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.UserRetrieved) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.UserSaltReset) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.UserPasswordUpdated) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DeviceRetrieved) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DeviceKeyPushed) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DeviceKeyPulled) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.Ping) should be(0)

        mockCoreClient.statistics(MockServerCoreEndpointClient.Statistic.CratePulled) should be(0)
        mockCoreClient.statistics(MockServerCoreEndpointClient.Statistic.CratePushed) should be(2)

        tracker.statistics(MockBackupTracker.Statistic.Started) should be(1)
        tracker.statistics(MockBackupTracker.Statistic.EntityDiscovered) should be(3)
        tracker.statistics(MockBackupTracker.Statistic.SpecificationProcessed) should be(0)
        tracker.statistics(MockBackupTracker.Statistic.EntityExamined) should be(3)
        tracker.statistics(MockBackupTracker.Statistic.EntityCollected) should be(2)
        tracker.statistics(MockBackupTracker.Statistic.EntityProcessingStarted) should be(2)
        tracker.statistics(MockBackupTracker.Statistic.EntityPartProcessed) should be(1)
        tracker.statistics(MockBackupTracker.Statistic.EntityProcessed) should be(1)
        tracker.statistics(MockBackupTracker.Statistic.MetadataCollected) should be(1)
        tracker.statistics(MockBackupTracker.Statistic.MetadataPushed) should be(1)
        tracker.statistics(MockBackupTracker.Statistic.FailureEncountered) should be(1)
        tracker.statistics(MockBackupTracker.Statistic.Completed) should be(1)
      }
    }
  }

  it should "handle general backup failures" in {
    val sourceFile1Metadata = "/ops/source-file-1".asTestResource.extractFileMetadata(checksum)

    val mockApiClient = MockServerApiEndpointClient()
    val mockCoreClient = MockServerCoreEndpointClient()
    val tracker = new MockBackupTracker

    val backup = createBackup(
      collector = Backup.Descriptor.Collector.WithEntities(
        entities = List(sourceFile1Metadata.path)
      ),
      latestMetadata = DatasetMetadata.empty,
      clients = Clients(
        api = mockApiClient,
        core = mockCoreClient
      ),
      tracker = tracker,
      checksum = new Checksum {
        override def calculate(file: Path)(implicit mat: Materializer): Future[BigInt] =
          Future.failed(new RuntimeException("Test failure"))
      }
    )

    backup.start().map { _ =>
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntryCreated) should be(1)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetDefinitionCreated) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.PublicSchedulesRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.PublicScheduleRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.UserRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.UserSaltReset) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.UserPasswordUpdated) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DeviceRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DeviceKeyPushed) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DeviceKeyPulled) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.Ping) should be(0)

      mockCoreClient.statistics(MockServerCoreEndpointClient.Statistic.CratePulled) should be(0)
      mockCoreClient.statistics(MockServerCoreEndpointClient.Statistic.CratePushed) should be(1)

      tracker.statistics(MockBackupTracker.Statistic.Started) should be(1)
      tracker.statistics(MockBackupTracker.Statistic.EntityDiscovered) should be(1)
      tracker.statistics(MockBackupTracker.Statistic.SpecificationProcessed) should be(0)
      tracker.statistics(MockBackupTracker.Statistic.EntityExamined) should be(0)
      tracker.statistics(MockBackupTracker.Statistic.EntityCollected) should be(0)
      tracker.statistics(MockBackupTracker.Statistic.EntityProcessingStarted) should be(0)
      tracker.statistics(MockBackupTracker.Statistic.EntityPartProcessed) should be(0)
      tracker.statistics(MockBackupTracker.Statistic.EntityProcessed) should be(0)
      tracker.statistics(MockBackupTracker.Statistic.MetadataCollected) should be(1)
      tracker.statistics(MockBackupTracker.Statistic.MetadataPushed) should be(1)
      tracker.statistics(MockBackupTracker.Statistic.FailureEncountered) should be(1)
      tracker.statistics(MockBackupTracker.Statistic.Completed) should be(1)
    }
  }

  it should "support reusing existing backup operation IDs, if provided" in {
    val existingId = Operation.generateId()

    val backupWithRules = createBackup(
      collector = Backup.Descriptor.Collector.WithRules(rules = Seq.empty),
      latestMetadata = DatasetMetadata.empty,
      clients = Clients(
        api = MockServerApiEndpointClient(),
        core = MockServerCoreEndpointClient()
      ),
      tracker = MockBackupTracker(),
      checksum = checksum
    )

    backupWithRules.id should not be existingId

    val backupWithEntities = createBackup(
      collector = Backup.Descriptor.Collector.WithEntities(entities = Seq.empty),
      latestMetadata = DatasetMetadata.empty,
      clients = Clients(
        api = MockServerApiEndpointClient(),
        core = MockServerCoreEndpointClient()
      ),
      tracker = MockBackupTracker(),
      checksum = checksum
    )

    backupWithEntities.id should not be existingId

    val backupWithState = createBackup(
      collector = Backup.Descriptor.Collector.WithState(Fixtures.State.BackupOneState.copy(operation = existingId)),
      latestMetadata = DatasetMetadata.empty,
      clients = Clients(
        api = MockServerApiEndpointClient(),
        core = MockServerCoreEndpointClient()
      ),
      tracker = MockBackupTracker(),
      checksum = checksum
    )

    backupWithState.id should be(existingId)
  }

  it should "track successful backup operations" in {
    import Backup._

    val tracker = new MockBackupTracker
    implicit val id: Operation.Id = Operation.generateId()

    val operation: Future[Done] = Future.successful(Done)
    val trackedOperation: Future[Done] = operation.trackWith(tracker)

    trackedOperation
      .map { result =>
        result should be(Done)

        eventually[Assertion] {
          tracker.statistics(MockBackupTracker.Statistic.Started) should be(0)
          tracker.statistics(MockBackupTracker.Statistic.EntityDiscovered) should be(0)
          tracker.statistics(MockBackupTracker.Statistic.SpecificationProcessed) should be(0)
          tracker.statistics(MockBackupTracker.Statistic.EntityExamined) should be(0)
          tracker.statistics(MockBackupTracker.Statistic.EntityCollected) should be(0)
          tracker.statistics(MockBackupTracker.Statistic.EntityProcessed) should be(0)
          tracker.statistics(MockBackupTracker.Statistic.EntityProcessingStarted) should be(0)
          tracker.statistics(MockBackupTracker.Statistic.EntityPartProcessed) should be(0)
          tracker.statistics(MockBackupTracker.Statistic.MetadataCollected) should be(0)
          tracker.statistics(MockBackupTracker.Statistic.MetadataPushed) should be(0)
          tracker.statistics(MockBackupTracker.Statistic.FailureEncountered) should be(0)
          tracker.statistics(MockBackupTracker.Statistic.Completed) should be(1)
        }
      }
  }

  it should "track failed backup operations" in {
    import Backup._

    val tracker = new MockBackupTracker
    implicit val id: Operation.Id = Operation.generateId()

    val operation: Future[Done] = Future.failed(new RuntimeException("Test Failure"))
    val trackedOperation: Future[Done] = operation.trackWith(tracker)

    trackedOperation
      .map { result =>
        fail(s"Unexpected result received: [$result]")
      }
      .recover { case NonFatal(e) =>
        e shouldBe a[RuntimeException]

        eventually[Assertion] {
          tracker.statistics(MockBackupTracker.Statistic.Started) should be(0)
          tracker.statistics(MockBackupTracker.Statistic.EntityDiscovered) should be(0)
          tracker.statistics(MockBackupTracker.Statistic.SpecificationProcessed) should be(0)
          tracker.statistics(MockBackupTracker.Statistic.EntityExamined) should be(0)
          tracker.statistics(MockBackupTracker.Statistic.EntityCollected) should be(0)
          tracker.statistics(MockBackupTracker.Statistic.EntityProcessingStarted) should be(0)
          tracker.statistics(MockBackupTracker.Statistic.EntityPartProcessed) should be(0)
          tracker.statistics(MockBackupTracker.Statistic.EntityProcessed) should be(0)
          tracker.statistics(MockBackupTracker.Statistic.MetadataCollected) should be(0)
          tracker.statistics(MockBackupTracker.Statistic.MetadataPushed) should be(0)
          tracker.statistics(MockBackupTracker.Statistic.FailureEncountered) should be(1)
          tracker.statistics(MockBackupTracker.Statistic.Completed) should be(0)
        }
      }
  }

  it should "track stopped backup operations" in {
    import Backup._

    val tracker = new MockBackupTracker
    implicit val id: Operation.Id = Operation.generateId()

    val operation: Future[Done] = Future.failed(OperationStopped(message = "Stopped"))
    val trackedOperation: Future[Done] = operation.trackWith(tracker)

    trackedOperation
      .map { result =>
        fail(s"Unexpected result received: [$result]")
      }
      .recover { case NonFatal(e) =>
        e shouldBe an[OperationStopped]

        eventually[Assertion] {
          tracker.statistics(MockBackupTracker.Statistic.Started) should be(0)
          tracker.statistics(MockBackupTracker.Statistic.EntityDiscovered) should be(0)
          tracker.statistics(MockBackupTracker.Statistic.SpecificationProcessed) should be(0)
          tracker.statistics(MockBackupTracker.Statistic.EntityExamined) should be(0)
          tracker.statistics(MockBackupTracker.Statistic.EntityCollected) should be(0)
          tracker.statistics(MockBackupTracker.Statistic.EntityProcessingStarted) should be(0)
          tracker.statistics(MockBackupTracker.Statistic.EntityPartProcessed) should be(0)
          tracker.statistics(MockBackupTracker.Statistic.EntityProcessed) should be(0)
          tracker.statistics(MockBackupTracker.Statistic.MetadataCollected) should be(0)
          tracker.statistics(MockBackupTracker.Statistic.MetadataPushed) should be(0)
          tracker.statistics(MockBackupTracker.Statistic.FailureEncountered) should be(0)
          tracker.statistics(MockBackupTracker.Statistic.Completed) should be(0)
        }
      }
  }

  it should "allow stopping a running backup" in {
    val sourceFile1Metadata = "/ops/source-file-1".asTestResource.extractFileMetadata(checksum)

    val tracker = new MockBackupTracker

    val backup = createBackup(
      collector = Backup.Descriptor.Collector.WithEntities(
        entities = List(sourceFile1Metadata.path)
      ),
      latestMetadata = DatasetMetadata.empty,
      clients = Clients(
        api = MockServerApiEndpointClient(),
        core = MockServerCoreEndpointClient()
      ),
      tracker = tracker
    )

    val _ = backup.start()
    backup.stop()

    eventually[Assertion] {
      tracker.statistics(MockBackupTracker.Statistic.Started) should be(1)
      tracker.statistics(MockBackupTracker.Statistic.FailureEncountered) should be(0)
      tracker.statistics(MockBackupTracker.Statistic.Completed) should be(0)
    }
  }

  "A Backup Descriptor" should "be creatable from a collector descriptor" in {
    val datasetWithEntry = Fixtures.Datasets.Default
    val datasetWithoutEntry = Fixtures.Datasets.Default.copy(id = DatasetDefinition.generateId())

    val entry = Fixtures.Entries.Default.copy(definition = datasetWithEntry.id)
    val metadata = DatasetMetadata.empty

    implicit val providers: Providers = Providers(
      checksum = checksum,
      staging = new DefaultFileStaging(
        storeDirectory = None,
        prefix = "staged-",
        suffix = ".tmp"
      ),
      compression = MockCompression(),
      encryptor = new MockEncryption(),
      decryptor = new MockEncryption() {
        override def decrypt(metadataSecret: DeviceMetadataSecret): Flow[ByteString, ByteString, NotUsed] =
          Flow[ByteString].mapAsync(parallelism = 1)(_ => DatasetMetadata.toByteString(metadata))
      },
      clients = Clients(
        api = new MockServerApiEndpointClient(self = Device.generateId()) {

          override def datasetDefinition(definition: DatasetDefinition.Id): Future[DatasetDefinition] =
            Future.successful(
              definition match {
                case datasetWithEntry.id    => datasetWithEntry
                case datasetWithoutEntry.id => datasetWithoutEntry
              }
            )

          override def latestEntry(
            definition: DatasetDefinition.Id,
            until: Option[Instant]
          ): Future[Option[DatasetEntry]] =
            if (definition == datasetWithEntry.id) {
              Future.successful(Some(entry))
            } else {
              Future.successful(None)
            }
        },
        core = new MockServerCoreEndpointClient(
          self = Node.generateId(),
          crates = Map(entry.metadata -> ByteString.empty)
        )
      ),
      track = new MockBackupTracker(),
      telemetry = MockClientTelemetryContext()
    )

    val collectorDescriptor = Backup.Descriptor.Collector.WithEntities(entities = Seq.empty)

    val limits = Backup.Limits(
      maxChunkSize = 8192,
      maxPartSize = 16384
    )

    for {
      descriptorWithLatestEntry <- Backup.Descriptor(
        definition = datasetWithEntry.id,
        collector = collectorDescriptor,
        deviceSecret = secret,
        limits = limits
      )
      descriptorWithoutLatestEntry <- Backup.Descriptor(
        definition = datasetWithoutEntry.id,
        collector = collectorDescriptor,
        deviceSecret = secret,
        limits = limits
      )
    } yield {
      descriptorWithLatestEntry.targetDataset should be(datasetWithEntry)
      descriptorWithLatestEntry.latestEntry should be(Some(entry))
      descriptorWithLatestEntry.latestMetadata should be(Some(metadata))
      descriptorWithLatestEntry.deviceSecret should be(secret)
      descriptorWithLatestEntry.collector should be(collectorDescriptor)
      descriptorWithLatestEntry.limits should be(limits)

      descriptorWithoutLatestEntry.targetDataset should be(datasetWithoutEntry)
      descriptorWithoutLatestEntry.latestEntry should be(None)
      descriptorWithoutLatestEntry.latestMetadata should be(None)
      descriptorWithoutLatestEntry.deviceSecret should be(secret)
      descriptorWithoutLatestEntry.collector should be(collectorDescriptor)
      descriptorWithoutLatestEntry.limits should be(limits)
    }
  }

  "A Backup Descriptor Collector" should "be convertible to an Entity Discovery Collector" in {
    Backup.Descriptor.Collector.WithRules(rules = Seq.empty).asDiscoveryCollector should be(
      EntityDiscovery.Collector.WithRules(rules = Seq.empty)
    )

    Backup.Descriptor.Collector.WithEntities(entities = Seq.empty).asDiscoveryCollector should be(
      EntityDiscovery.Collector.WithEntities(entities = Seq.empty)
    )

    Backup.Descriptor.Collector.WithState(state = Fixtures.State.BackupOneState).asDiscoveryCollector should be(
      EntityDiscovery.Collector.WithState(state = Fixtures.State.BackupOneState)
    )
  }

  it should "provide an existing state, if available" in {
    Backup.Descriptor.Collector.WithRules(rules = Seq.empty).existingState should be(
      None
    )

    Backup.Descriptor.Collector.WithEntities(entities = Seq.empty).existingState should be(
      None
    )

    Backup.Descriptor.Collector.WithState(state = Fixtures.State.BackupOneState).existingState should be(
      Some(Fixtures.State.BackupOneState)
    )
  }

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(5.seconds, 250.milliseconds)

  private implicit val typedSystem: ActorSystem[SpawnProtocol.Command] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol()): Behavior[SpawnProtocol.Command],
    "BackupSpec"
  )

  private implicit val parallelismConfig: ParallelismConfig = ParallelismConfig(entities = 1, entityParts = 1)

  private implicit val secretsConfig: SecretsConfig = SecretsConfig(
    derivation = SecretsConfig.Derivation(
      encryption = SecretsConfig.Derivation.Encryption(
        secretSize = 64,
        iterations = 100000,
        saltPrefix = "unit-test"
      ),
      authentication = SecretsConfig.Derivation.Authentication(
        enabled = true,
        secretSize = 64,
        iterations = 100000,
        saltPrefix = "unit-test"
      )
    ),
    encryption = SecretsConfig.Encryption(
      file = SecretsConfig.Encryption.File(keySize = 16, ivSize = 16),
      metadata = SecretsConfig.Encryption.Metadata(keySize = 24, ivSize = 32),
      deviceSecret = SecretsConfig.Encryption.DeviceSecret(keySize = 32, ivSize = 64)
    )
  )

  private val checksum: Checksum = Checksum.SHA256

  private val secret: DeviceSecret = DeviceSecret(
    user = User.generateId(),
    device = Device.generateId(),
    secret = ByteString("some-secret")
  )

  private def createBackup(
    latestMetadata: DatasetMetadata,
    collector: Backup.Descriptor.Collector,
    clients: Clients,
    tracker: MockBackupTracker,
    checksum: Checksum = checksum
  ): Backup = {
    val encryption = Aes

    implicit val providers: Providers = Providers(
      checksum = checksum,
      staging = new DefaultFileStaging(
        storeDirectory = None,
        prefix = "staged-",
        suffix = ".tmp"
      ),
      compression = MockCompression(),
      encryptor = encryption,
      decryptor = encryption,
      clients = clients,
      track = tracker,
      telemetry = MockClientTelemetryContext()
    )

    new Backup(
      descriptor = Backup.Descriptor(
        targetDataset = Fixtures.Datasets.Default,
        latestEntry = Some(Fixtures.Entries.Default),
        latestMetadata = Some(latestMetadata),
        deviceSecret = secret,
        collector = collector,
        limits = Backup.Limits(
          maxChunkSize = 8192,
          maxPartSize = 16384
        )
      )
    )
  }

  override protected def afterAll(): Unit =
    typedSystem.terminate()
}
