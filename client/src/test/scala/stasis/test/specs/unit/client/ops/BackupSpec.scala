package stasis.test.specs.unit.client.ops

import java.nio.file.Paths

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.util.ByteString
import stasis.client.analysis.{Checksum, Metadata}
import stasis.client.collection.{BackupCollector, FilesBackupCollector, GlobBackupCollector}
import stasis.client.compression.Gzip
import stasis.client.encryption.Aes
import stasis.client.encryption.secrets.{DeviceSecret, Secret}
import stasis.client.model.DatasetMetadata
import stasis.client.ops.backup.{Clients, Providers}
import stasis.client.ops.{Backup, ParallelismConfig}
import stasis.client.staging.DefaultFileStaging
import stasis.core.routing.Node
import stasis.shared.model.devices.Device
import stasis.shared.model.users.User
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.mocks.{MockServerApiEndpointClient, MockServerCoreEndpointClient}
import stasis.test.specs.unit.client.{Fixtures, ResourceHelpers}

class BackupSpec extends AsyncUnitSpec with ResourceHelpers {
  private implicit val system: ActorSystem = ActorSystem(name = "BackupSpec")
  private implicit val mat: ActorMaterializer = ActorMaterializer()

  private implicit val parallelismConfig: ParallelismConfig = ParallelismConfig(value = 1)

  private implicit val secretsConfig: Secret.Config = Secret.Config(
    derivation = Secret.DerivationConfig(
      encryption = Secret.KeyDerivationConfig(secretSize = 64, iterations = 10000, saltPrefix = "unit-test"),
      authentication = Secret.KeyDerivationConfig(secretSize = 64, iterations = 10000, saltPrefix = "unit-test")
    ),
    encryption = Secret.EncryptionConfig(
      file = Secret.EncryptionSecretConfig(keySize = 16, ivSize = 16),
      metadata = Secret.EncryptionSecretConfig(keySize = 24, ivSize = 32),
      deviceSecret = Secret.EncryptionSecretConfig(keySize = 32, ivSize = 64)
    )
  )

  private val checksum: Checksum = Checksum.SHA256

  private def createBackup(collector: BackupCollector, clients: Clients): Backup = new Backup(
    targetDataset = Fixtures.Datasets.Default,
    deviceSecret = DeviceSecret(
      user = User.generateId(),
      device = Device.generateId(),
      secret = ByteString("some-secret")
    ),
    providers = Providers(
      collector = collector,
      staging = new DefaultFileStaging(
        storeDirectory = None,
        prefix = "staged-",
        suffix = ".tmp"
      ),
      compressor = new Gzip,
      encryptor = new Aes
    ),
    clients = clients
  )

  "A Backup operation" should "process backups for entire configured file collection" in {
    val sourceFile1Metadata = "/ops/source-file-1".asTestResource.extractMetadata(checksum)
    val sourceFile2Metadata = "/ops/source-file-2".asTestResource.extractMetadata(checksum)
    val sourceFile3Metadata = "/ops/source-file-3".asTestResource.extractMetadata(checksum)

    val mockApiClient = new MockServerApiEndpointClient(self = Device.generateId())
    val mockCoreClient = new MockServerCoreEndpointClient(self = Node.generateId(), crates = Map.empty)

    val backup = createBackup(
      collector = new GlobBackupCollector(
        rules = Seq(
          GlobBackupCollector.GlobRule(
            directory = "/ops".asTestResource.toString,
            pattern = "source-file-*"
          )
        ),
        metadata = new Metadata.Default(
          checksum = checksum,
          lastDatasetMetadata = DatasetMetadata(
            contentChanged = Seq(
              sourceFile1Metadata.copy(isHidden = true),
              sourceFile2Metadata,
              sourceFile3Metadata.copy(checksum = BigInt(0))
            ),
            metadataChanged = Seq.empty
          )
        )
      ),
      clients = Clients(
        api = mockApiClient,
        core = mockCoreClient
      )
    )

    backup
      .start()
      .map { _ =>
        // dataset entry for backup created; metadata crate pushed
        // source-file-1 has metadata changes only; source-file-2 is unchanged; crate for source-file-3 pushed;
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntryCreated) should be(1)

        mockCoreClient.statistics(MockServerCoreEndpointClient.Statistic.CratePulled) should be(0)
        mockCoreClient.statistics(MockServerCoreEndpointClient.Statistic.CratePushed) should be(2)
      }
  }

  it should "process backups for specific files" in {
    val sourceFile1Metadata = "/ops/source-file-1".asTestResource.extractMetadata(checksum)
    val sourceFile2Metadata = "/ops/source-file-2".asTestResource.extractMetadata(checksum)
    val sourceFile3Metadata = "/ops/source-file-3".asTestResource.extractMetadata(checksum)

    val mockApiClient = new MockServerApiEndpointClient(self = Device.generateId())
    val mockCoreClient = new MockServerCoreEndpointClient(self = Node.generateId(), crates = Map.empty)

    val backup = createBackup(
      collector = new FilesBackupCollector(
        files = List(
          sourceFile1Metadata.path,
          sourceFile2Metadata.path,
          sourceFile3Metadata.path
        ),
        metadata = new Metadata.Default(
          checksum = checksum,
          lastDatasetMetadata = DatasetMetadata(
            contentChanged = Seq(
              sourceFile1Metadata.copy(isHidden = true),
              sourceFile2Metadata,
              sourceFile3Metadata.copy(checksum = BigInt(0))
            ),
            metadataChanged = Seq.empty
          )
        )
      ),
      clients = Clients(
        api = mockApiClient,
        core = mockCoreClient
      )
    )

    backup
      .start()
      .map { _ =>
        // dataset entry for backup created; metadata crate pushed
        // source-file-1 has metadata changes only; source-file-2 is unchanged; crate for source-file-3 pushed;
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntryCreated) should be(1)

        mockCoreClient.statistics(MockServerCoreEndpointClient.Statistic.CratePulled) should be(0)
        mockCoreClient.statistics(MockServerCoreEndpointClient.Statistic.CratePushed) should be(2)
      }
  }

  it should "handle failures of backups for individual files" in {
    val sourceFile1Metadata = "/ops/source-file-1".asTestResource.extractMetadata(checksum)
    val sourceFile2Metadata = "/ops/source-file-2".asTestResource.extractMetadata(checksum)

    val mockApiClient = new MockServerApiEndpointClient(self = Device.generateId())
    val mockCoreClient = new MockServerCoreEndpointClient(self = Node.generateId(), crates = Map.empty)

    val backup = createBackup(
      collector = new FilesBackupCollector(
        files = List(
          sourceFile1Metadata.path,
          sourceFile2Metadata.path,
          Paths.get("/ops/invalid-file")
        ),
        metadata = new Metadata.Default(
          checksum = checksum,
          lastDatasetMetadata = DatasetMetadata(
            contentChanged = Seq(
              sourceFile1Metadata.copy(isHidden = true),
              sourceFile2Metadata
            ),
            metadataChanged = Seq.empty
          )
        )
      ),
      clients = Clients(
        api = mockApiClient,
        core = mockCoreClient
      )
    )

    backup
      .start()
      .map { _ =>
        // dataset entry for backup created; metadata crate pushed
        // source-file-1 has metadata changes only; source-file-2 is unchanged; third file is invalid/missing;
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved) should be(0)
        mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntryCreated) should be(1)

        mockCoreClient.statistics(MockServerCoreEndpointClient.Statistic.CratePulled) should be(0)
        mockCoreClient.statistics(MockServerCoreEndpointClient.Statistic.CratePushed) should be(1)
      }
  }
}
