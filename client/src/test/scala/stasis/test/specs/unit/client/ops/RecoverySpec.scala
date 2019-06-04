package stasis.test.specs.unit.client.ops

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.util.ByteString
import stasis.client.analysis.{Checksum, Metadata}
import stasis.client.collection.FileMetadataRecoveryCollector
import stasis.client.compression.Gzip
import stasis.client.encryption.Aes
import stasis.client.encryption.secrets.{DeviceSecret, Secret}
import stasis.client.model.DatasetMetadata
import stasis.client.ops.recovery.{Clients, Providers}
import stasis.client.ops.{ParallelismConfig, Recovery}
import stasis.client.staging.DefaultFileStaging
import stasis.core.routing.Node
import stasis.shared.model.devices.Device
import stasis.shared.model.users.User
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.ResourceHelpers
import stasis.test.specs.unit.client.mocks.MockServerCoreEndpointClient

class RecoverySpec extends AsyncUnitSpec with ResourceHelpers {
  private implicit val system: ActorSystem = ActorSystem(name = "RecoverySpec")
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

  "A Recovery operation" should "process recovery of files" in {
    val sourceFile1Metadata = "/ops/source-file-1".asTestResource.extractMetadata(checksum)
    val sourceFile2Metadata = "/ops/source-file-2".asTestResource.extractMetadata(checksum)
    val sourceFile3Metadata = "/ops/source-file-3".asTestResource.extractMetadata(checksum)

    val mockCoreClient = new MockServerCoreEndpointClient(
      self = Node.generateId(),
      crates = Map(
        sourceFile1Metadata.crate -> ByteString("dummy-encrypted-data")
      )
    )

    val recovery = new Recovery(
      deviceSecret = DeviceSecret(
        user = User.generateId(),
        device = Device.generateId(),
        secret = ByteString("some-secret")
      ),
      providers = Providers(
        collector = new FileMetadataRecoveryCollector(
          filesMetadata = Seq(
            sourceFile1Metadata.copy(checksum = BigInt(0)),
            sourceFile2Metadata,
            sourceFile3Metadata.copy(isHidden = true)
          ),
          metadata = new Metadata.Default(
            checksum = checksum,
            lastDatasetMetadata = DatasetMetadata(
              contentChanged = Seq.empty,
              metadataChanged = Seq.empty
            )
          )
        ),
        staging = new DefaultFileStaging(
          storeDirectory = None,
          prefix = "staged-",
          suffix = ".tmp"
        ),
        decompressor = new Gzip,
        decryptor = new Aes
      ),
      clients = Clients(
        core = mockCoreClient
      )
    )

    recovery
      .start()
      .map { _ =>
        // datapullsed for source-file-1; source-file-2 is unchanged; source-file-3 has only metadata changes;
        mockCoreClient.statistics(MockServerCoreEndpointClient.Statistic.CratePulled) should be(1)
        mockCoreClient.statistics(MockServerCoreEndpointClient.Statistic.CratePushed) should be(0)
      }
  }

  it should "handle failures of specific files" in {
    val sourceFile1Metadata = "/ops/source-file-1".asTestResource.extractMetadata(checksum)
    val sourceFile2Metadata = "/ops/source-file-2".asTestResource.extractMetadata(checksum)
    val sourceFile3Metadata = "/ops/source-file-3".asTestResource.extractMetadata(checksum)

    val mockCoreClient = new MockServerCoreEndpointClient(
      self = Node.generateId(),
      crates = Map(
        sourceFile1Metadata.crate -> ByteString("dummy-encrypted-data"),
        sourceFile2Metadata.crate -> ByteString("dummy-encrypted-data")
      )
    )

    val recovery = new Recovery(
      deviceSecret = DeviceSecret(
        user = User.generateId(),
        device = Device.generateId(),
        secret = ByteString("some-secret")
      ),
      providers = Providers(
        collector = new FileMetadataRecoveryCollector(
          filesMetadata = Seq(
            sourceFile1Metadata.copy(checksum = BigInt(0)),
            sourceFile2Metadata.copy(checksum = BigInt(0)),
            sourceFile3Metadata.copy(checksum = BigInt(0))
          ),
          metadata = new Metadata.Default(
            checksum = checksum,
            lastDatasetMetadata = DatasetMetadata(
              contentChanged = Seq.empty,
              metadataChanged = Seq.empty
            )
          )
        ),
        staging = new DefaultFileStaging(
          storeDirectory = None,
          prefix = "staged-",
          suffix = ".tmp"
        ),
        decompressor = new Gzip,
        decryptor = new Aes
      ),
      clients = Clients(
        core = mockCoreClient
      )
    )

    recovery
      .start()
      .map { _ =>
        // data pulled for source-file-1, source-file-2; source-file-3 has not data and will fail
        mockCoreClient.statistics(MockServerCoreEndpointClient.Statistic.CratePulled) should be(2)
        mockCoreClient.statistics(MockServerCoreEndpointClient.Statistic.CratePushed) should be(0)
      }
  }
}
