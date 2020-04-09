package stasis.test.specs.unit.client.ops.backup

import java.nio.file.Paths
import java.time.Instant

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Flow
import akka.util.ByteString
import akka.{Done, NotUsed}
import org.scalatest.concurrent.Eventually
import org.scalatest.BeforeAndAfterAll
import stasis.client.analysis.Checksum
import stasis.client.api.clients.Clients
import stasis.client.collection.BackupCollector
import stasis.client.collection.rules.{Rule, Specification}
import stasis.client.compression.Gzip
import stasis.client.encryption.Aes
import stasis.client.encryption.secrets.{DeviceMetadataSecret, DeviceSecret, Secret}
import stasis.client.model.{DatasetMetadata, FilesystemMetadata}
import stasis.client.ops.backup.{Backup, Providers}
import stasis.client.ops.ParallelismConfig
import stasis.client.staging.DefaultFileStaging
import stasis.core.routing.Node
import stasis.shared.model.datasets.{DatasetDefinition, DatasetEntry}
import stasis.shared.model.devices.Device
import stasis.shared.model.users.User
import stasis.shared.ops.Operation
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.mocks._
import stasis.test.specs.unit.client.{Fixtures, ResourceHelpers}

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
    val mockTracker = new MockBackupTracker

    val backup = createBackup(
      collector = Backup.Descriptor.Collector.WithRules(
        spec = Specification(
          rules = Seq(
            Rule(line = s"+ ${sourceDirectory1Metadata.path.toAbsolutePath} source-file-*", lineNumber = 0).get,
            Rule(line = s"+ ${sourceDirectory2Metadata.path.toAbsolutePath} source-file-*", lineNumber = 0).get
          )
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
      tracker = mockTracker
    )

    backup.start().map { _ =>
      eventually {
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
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DeviceRetrieved) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.Ping) should be(0)

        mockCoreClient.statistics(MockServerCoreEndpointClient.Statistic.CratePulled) should be(0)
        mockCoreClient.statistics(MockServerCoreEndpointClient.Statistic.CratePushed) should be(4)

        mockTracker.statistics(MockBackupTracker.Statistic.EntityExamined) should be(7) // 2 directories + 5 files
        mockTracker.statistics(MockBackupTracker.Statistic.EntityCollected) should be(5) // 2 unchanged + 5 changed entities
        mockTracker.statistics(MockBackupTracker.Statistic.EntityProcessed) should be(5) // 2 unchanged + 5 changed entities
        mockTracker.statistics(MockBackupTracker.Statistic.MetadataCollected) should be(1)
        mockTracker.statistics(MockBackupTracker.Statistic.MetadataPushed) should be(1)
        mockTracker.statistics(MockBackupTracker.Statistic.FailureEncountered) should be(0)
        mockTracker.statistics(MockBackupTracker.Statistic.Completed) should be(1)
      }
    }
  }

  it should "process backups for specific files" in {
    val sourceFile1Metadata = "/ops/source-file-1".asTestResource.extractFileMetadata(checksum)
    val sourceFile2Metadata = "/ops/source-file-2".asTestResource.extractFileMetadata(checksum)
    val sourceFile3Metadata = "/ops/source-file-3".asTestResource.extractFileMetadata(checksum)

    val mockApiClient = MockServerApiEndpointClient()
    val mockCoreClient = MockServerCoreEndpointClient()
    val mockTracker = new MockBackupTracker

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
      tracker = mockTracker
    )

    backup.start().map { _ =>
      eventually {
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
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DeviceRetrieved) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.Ping) should be(0)

        mockCoreClient.statistics(MockServerCoreEndpointClient.Statistic.CratePulled) should be(0)
        mockCoreClient.statistics(MockServerCoreEndpointClient.Statistic.CratePushed) should be(2)

        mockTracker.statistics(MockBackupTracker.Statistic.EntityExamined) should be(3)
        mockTracker.statistics(MockBackupTracker.Statistic.EntityCollected) should be(2)
        mockTracker.statistics(MockBackupTracker.Statistic.EntityProcessed) should be(2)
        mockTracker.statistics(MockBackupTracker.Statistic.MetadataCollected) should be(1)
        mockTracker.statistics(MockBackupTracker.Statistic.MetadataPushed) should be(1)
        mockTracker.statistics(MockBackupTracker.Statistic.FailureEncountered) should be(0)
        mockTracker.statistics(MockBackupTracker.Statistic.Completed) should be(1)
      }
    }
  }

  it should "handle failures of backups for individual files" in {
    val sourceFile1Metadata = "/ops/source-file-1".asTestResource.extractFileMetadata(checksum)
    val sourceFile2Metadata = "/ops/source-file-2".asTestResource.extractFileMetadata(checksum)

    val mockApiClient = MockServerApiEndpointClient()
    val mockCoreClient = MockServerCoreEndpointClient()
    val mockTracker = new MockBackupTracker

    val backup = createBackup(
      collector = Backup.Descriptor.Collector.WithEntities(
        entities = List(
          sourceFile1Metadata.path,
          sourceFile2Metadata.path,
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
      tracker = mockTracker
    )

    backup.start().map { _ =>
      eventually {
        // dataset entry for backup created; metadata crate pushed
        // source-file-1 has metadata changes only; source-file-2 is unchanged; third file is invalid/missing;
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
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DeviceRetrieved) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.Ping) should be(0)

        mockCoreClient.statistics(MockServerCoreEndpointClient.Statistic.CratePulled) should be(0)
        mockCoreClient.statistics(MockServerCoreEndpointClient.Statistic.CratePushed) should be(1)

        mockTracker.statistics(MockBackupTracker.Statistic.EntityExamined) should be(2)
        mockTracker.statistics(MockBackupTracker.Statistic.EntityCollected) should be(1)
        mockTracker.statistics(MockBackupTracker.Statistic.EntityProcessed) should be(1)
        mockTracker.statistics(MockBackupTracker.Statistic.MetadataCollected) should be(1)
        mockTracker.statistics(MockBackupTracker.Statistic.MetadataPushed) should be(1)
        mockTracker.statistics(MockBackupTracker.Statistic.FailureEncountered) should be(1)
        mockTracker.statistics(MockBackupTracker.Statistic.Completed) should be(1)
      }
    }
  }

  it should "track successful backup operations" in {
    import Backup._

    val mockTracker = new MockBackupTracker
    implicit val id: Operation.Id = Operation.generateId()

    val operation: Future[Done] = Future.successful(Done)
    val trackedOperation: Future[Done] = operation.trackWith(mockTracker)

    trackedOperation
      .map { result =>
        result should be(Done)

        eventually {
          mockTracker.statistics(MockBackupTracker.Statistic.EntityExamined) should be(0)
          mockTracker.statistics(MockBackupTracker.Statistic.EntityCollected) should be(0)
          mockTracker.statistics(MockBackupTracker.Statistic.EntityProcessed) should be(0)
          mockTracker.statistics(MockBackupTracker.Statistic.MetadataCollected) should be(0)
          mockTracker.statistics(MockBackupTracker.Statistic.MetadataPushed) should be(0)
          mockTracker.statistics(MockBackupTracker.Statistic.FailureEncountered) should be(0)
          mockTracker.statistics(MockBackupTracker.Statistic.Completed) should be(1)
        }
      }
  }

  it should "track failed backup operations" in {
    import Backup._

    val mockTracker = new MockBackupTracker
    implicit val id: Operation.Id = Operation.generateId()

    val operation: Future[Done] = Future.failed(new RuntimeException("Test Failure"))
    val trackedOperation: Future[Done] = operation.trackWith(mockTracker)

    trackedOperation
      .map { result =>
        fail(s"Unexpected result received: [$result]")
      }
      .recover {
        case NonFatal(e) =>
          e shouldBe a[RuntimeException]

          eventually {
            mockTracker.statistics(MockBackupTracker.Statistic.EntityExamined) should be(0)
            mockTracker.statistics(MockBackupTracker.Statistic.EntityCollected) should be(0)
            mockTracker.statistics(MockBackupTracker.Statistic.EntityProcessed) should be(0)
            mockTracker.statistics(MockBackupTracker.Statistic.MetadataCollected) should be(0)
            mockTracker.statistics(MockBackupTracker.Statistic.MetadataPushed) should be(0)
            mockTracker.statistics(MockBackupTracker.Statistic.FailureEncountered) should be(1)
            mockTracker.statistics(MockBackupTracker.Statistic.Completed) should be(1)
          }
      }
  }

  it should "allow stopping a running backup" in {
    val sourceFile1Metadata = "/ops/source-file-1".asTestResource.extractFileMetadata(checksum)

    val mockTracker = new MockBackupTracker

    val backup = createBackup(
      collector = Backup.Descriptor.Collector.WithEntities(
        entities = List(sourceFile1Metadata.path)
      ),
      latestMetadata = DatasetMetadata.empty,
      clients = Clients(
        api = MockServerApiEndpointClient(),
        core = MockServerCoreEndpointClient()
      ),
      tracker = mockTracker
    )

    val _ = backup.start()
    backup.stop()

    eventually {
      mockTracker.statistics(MockBackupTracker.Statistic.Completed) should be(1)
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
      compressor = Gzip,
      encryptor = new MockEncryption(),
      decryptor = new MockEncryption() {
        override def decrypt(metadataSecret: DeviceMetadataSecret): Flow[ByteString, ByteString, NotUsed] =
          Flow[ByteString].map(_ => DatasetMetadata.toByteString(metadata))
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
      track = new MockBackupTracker()
    )

    val collectorDescriptor = Backup.Descriptor.Collector.WithEntities(entities = Seq.empty)

    for {
      descriptorWithLatestEntry <- Backup.Descriptor(
        definition = datasetWithEntry.id,
        collector = collectorDescriptor,
        deviceSecret = secret
      )
      descriptorWithoutLatestEntry <- Backup.Descriptor(
        definition = datasetWithoutEntry.id,
        collector = collectorDescriptor,
        deviceSecret = secret
      )
    } yield {
      descriptorWithLatestEntry.targetDataset should be(datasetWithEntry)
      descriptorWithLatestEntry.latestEntry should be(Some(entry))
      descriptorWithLatestEntry.latestMetadata should be(Some(metadata))
      descriptorWithLatestEntry.deviceSecret should be(secret)
      descriptorWithLatestEntry.collector should be(collectorDescriptor)

      descriptorWithoutLatestEntry.targetDataset should be(datasetWithoutEntry)
      descriptorWithoutLatestEntry.latestEntry should be(None)
      descriptorWithoutLatestEntry.latestMetadata should be(None)
      descriptorWithoutLatestEntry.deviceSecret should be(secret)
      descriptorWithoutLatestEntry.collector should be(collectorDescriptor)
    }
  }

  it should "be convertible to a backup collector" in {
    implicit val providers: Providers = Providers(
      checksum = checksum,
      staging = new DefaultFileStaging(
        storeDirectory = None,
        prefix = "staged-",
        suffix = ".tmp"
      ),
      compressor = Gzip,
      encryptor = new MockEncryption(),
      decryptor = new MockEncryption(),
      clients = Clients(
        api = MockServerApiEndpointClient(),
        core = MockServerCoreEndpointClient()
      ),
      track = new MockBackupTracker()
    )

    val descriptorWithFiles = Backup.Descriptor(
      targetDataset = Fixtures.Datasets.Default,
      latestEntry = None,
      latestMetadata = None,
      deviceSecret = secret,
      collector = Backup.Descriptor.Collector.WithEntities(entities = Seq.empty)
    )

    val descriptorWithRules = Backup
      .Descriptor(
        targetDataset = Fixtures.Datasets.Default,
        latestEntry = None,
        latestMetadata = None,
        deviceSecret = secret,
        collector = Backup.Descriptor.Collector.WithRules(spec = Specification.empty)
      )

    descriptorWithFiles.toBackupCollector(checksum) shouldBe a[BackupCollector.Default]
    descriptorWithRules.toBackupCollector(checksum) shouldBe a[BackupCollector.Default]
  }

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(5.seconds, 250.milliseconds)

  private implicit val typedSystem: ActorSystem[SpawnProtocol] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol.behavior): Behavior[SpawnProtocol],
    "BackupSpec"
  )

  private implicit val untypedSystem: akka.actor.ActorSystem = typedSystem.toUntyped

  private implicit val mat: ActorMaterializer = ActorMaterializer()

  private implicit val parallelismConfig: ParallelismConfig = ParallelismConfig(value = 1)

  private implicit val secretsConfig: Secret.Config = Secret.Config(
    derivation = Secret.DerivationConfig(
      encryption = Secret.KeyDerivationConfig(secretSize = 64, iterations = 100000, saltPrefix = "unit-test"),
      authentication = Secret.KeyDerivationConfig(secretSize = 64, iterations = 100000, saltPrefix = "unit-test")
    ),
    encryption = Secret.EncryptionConfig(
      file = Secret.EncryptionSecretConfig(keySize = 16, ivSize = 16),
      metadata = Secret.EncryptionSecretConfig(keySize = 24, ivSize = 32),
      deviceSecret = Secret.EncryptionSecretConfig(keySize = 32, ivSize = 64)
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
    tracker: MockBackupTracker
  ): Backup = {
    val encryption = Aes

    implicit val providers: Providers = Providers(
      checksum = checksum,
      staging = new DefaultFileStaging(
        storeDirectory = None,
        prefix = "staged-",
        suffix = ".tmp"
      ),
      compressor = Gzip,
      encryptor = encryption,
      decryptor = encryption,
      clients = clients,
      track = tracker
    )

    new Backup(
      descriptor = Backup.Descriptor(
        targetDataset = Fixtures.Datasets.Default,
        latestEntry = Some(Fixtures.Entries.Default),
        latestMetadata = Some(latestMetadata),
        deviceSecret = secret,
        collector = collector
      )
    )
  }

  override protected def afterAll(): Unit =
    typedSystem.terminate()
}
