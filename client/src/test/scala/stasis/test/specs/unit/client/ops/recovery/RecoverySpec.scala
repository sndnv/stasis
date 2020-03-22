package stasis.test.specs.unit.client.ops.recovery

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
import stasis.client.collection.RecoveryCollector
import stasis.client.encryption.secrets.{DeviceMetadataSecret, DeviceSecret, Secret}
import stasis.client.model.{DatasetMetadata, FilesystemMetadata}
import stasis.client.ops.recovery.Recovery.PathQuery
import stasis.client.ops.recovery.{Providers, Recovery}
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
import scala.util.matching.Regex

class RecoverySpec extends AsyncUnitSpec with ResourceHelpers with Eventually with BeforeAndAfterAll {
  "A Recovery operation" should "process recovery of files" in {
    val currentSourceFile1Metadata = "/ops/source-file-1".asTestResource.extractMetadata(checksum)
    val currentSourceFile2Metadata = "/ops/source-file-2".asTestResource.extractMetadata(checksum)
    val currentSourceFile3Metadata = "/ops/source-file-3".asTestResource.extractMetadata(checksum)

    // metadata represents file state during previous backup
    val originalSourceFile1Metadata = currentSourceFile1Metadata.copy(checksum = BigInt(0)) // file changed since backup
    val originalSourceFile2Metadata = currentSourceFile2Metadata // file not changed since backup
    val originalSourceFile3Metadata = currentSourceFile3Metadata.copy(isHidden = true) // file changed since backup

    val originalMetadata = DatasetMetadata(
      contentChanged = Map(
        originalSourceFile1Metadata.path -> originalSourceFile1Metadata,
        originalSourceFile2Metadata.path -> originalSourceFile2Metadata,
        originalSourceFile3Metadata.path -> originalSourceFile3Metadata
      ),
      metadataChanged = Map.empty,
      filesystem = FilesystemMetadata(
        changes = Seq(
          originalSourceFile1Metadata.path,
          originalSourceFile2Metadata.path,
          originalSourceFile3Metadata.path
        )
      )
    )

    val mockApiClient = MockServerApiEndpointClient()

    val mockCoreClient = new MockServerCoreEndpointClient(
      self = Node.generateId(),
      crates = Map(
        originalSourceFile1Metadata.crate -> ByteString("dummy-encrypted-data"),
        originalSourceFile2Metadata.crate -> ByteString("dummy-encrypted-data"),
        originalSourceFile3Metadata.crate -> ByteString("dummy-encrypted-data")
      )
    )
    val mockTracker = new MockRecoveryTracker

    val recovery = createRecovery(
      metadata = originalMetadata,
      clients = Clients(api = mockApiClient, core = mockCoreClient),
      tracker = mockTracker
    )

    recovery.start().map { _ =>
      eventually {
        // data pulled for source-file-1; source-file-2 is unchanged; source-file-3 has only metadata changes;
        mockCoreClient.statistics(MockServerCoreEndpointClient.Statistic.CratePulled) should be(1)
        mockCoreClient.statistics(MockServerCoreEndpointClient.Statistic.CratePushed) should be(0)

        mockTracker.statistics(MockRecoveryTracker.Statistic.FileExamined) should be(3)
        mockTracker.statistics(MockRecoveryTracker.Statistic.FileCollected) should be(2)
        mockTracker.statistics(MockRecoveryTracker.Statistic.FileProcessed) should be(2)
        mockTracker.statistics(MockRecoveryTracker.Statistic.MetadataApplied) should be(2)
        mockTracker.statistics(MockRecoveryTracker.Statistic.FailureEncountered) should be(0)
        mockTracker.statistics(MockRecoveryTracker.Statistic.Completed) should be(1)
      }
    }
  }

  it should "handle failures of specific files" in {
    val currentSourceFile1Metadata = "/ops/source-file-1".asTestResource.extractMetadata(checksum)
    val currentSourceFile2Metadata = "/ops/source-file-2".asTestResource.extractMetadata(checksum)
    val currentSourceFile3Metadata = "/ops/source-file-3".asTestResource.extractMetadata(checksum)

    // metadata represents file state during previous backup
    val originalSourceFile1Metadata = currentSourceFile1Metadata.copy(checksum = BigInt(0)) // file changed since backup
    val originalSourceFile2Metadata = currentSourceFile2Metadata.copy(checksum = BigInt(0)) // file changed since backup
    val originalSourceFile3Metadata = currentSourceFile3Metadata.copy(checksum = BigInt(0)) // file changed since backup

    val originalMetadata = DatasetMetadata(
      contentChanged = Map(
        originalSourceFile1Metadata.path -> originalSourceFile1Metadata,
        originalSourceFile2Metadata.path -> originalSourceFile2Metadata,
        originalSourceFile3Metadata.path -> originalSourceFile3Metadata
      ),
      metadataChanged = Map.empty,
      filesystem = FilesystemMetadata(
        changes = Seq(
          currentSourceFile1Metadata.path,
          currentSourceFile2Metadata.path,
          currentSourceFile3Metadata.path
        )
      )
    )

    val mockApiClient = MockServerApiEndpointClient()

    val mockCoreClient = new MockServerCoreEndpointClient(
      self = Node.generateId(),
      crates = Map(
        originalSourceFile1Metadata.crate -> ByteString("dummy-encrypted-data"),
        originalSourceFile2Metadata.crate -> ByteString("dummy-encrypted-data")
        // recovery for originalSourceFile3 is expected to fail; content is missing
      )
    )
    val mockTracker = new MockRecoveryTracker

    val recovery = createRecovery(
      metadata = originalMetadata,
      clients = Clients(api = mockApiClient, core = mockCoreClient),
      tracker = mockTracker
    )

    recovery.start().map { _ =>
      eventually {
        // data pulled for source-file-1, source-file-2; source-file-3 has not data and will fail
        mockCoreClient.statistics(MockServerCoreEndpointClient.Statistic.CratePulled) should be(2)
        mockCoreClient.statistics(MockServerCoreEndpointClient.Statistic.CratePushed) should be(0)

        mockTracker.statistics(MockRecoveryTracker.Statistic.FileExamined) should be(3)
        mockTracker.statistics(MockRecoveryTracker.Statistic.FileCollected) should be(3)
        mockTracker.statistics(MockRecoveryTracker.Statistic.FileProcessed) should be(2)
        mockTracker.statistics(MockRecoveryTracker.Statistic.MetadataApplied) should be(2)
        mockTracker.statistics(MockRecoveryTracker.Statistic.FailureEncountered) should be(1)
        mockTracker.statistics(MockRecoveryTracker.Statistic.Completed) should be(1)
      }
    }
  }

  it should "track successful recovery operations" in {
    import Recovery._

    val mockTracker = new MockRecoveryTracker
    implicit val id: Operation.Id = Operation.generateId()

    val operation: Future[Done] = Future.successful(Done)
    val trackedOperation: Future[Done] = operation.trackWith(mockTracker)

    trackedOperation
      .map { result =>
        result should be(Done)

        eventually {
          mockTracker.statistics(MockRecoveryTracker.Statistic.FileExamined) should be(0)
          mockTracker.statistics(MockRecoveryTracker.Statistic.FileCollected) should be(0)
          mockTracker.statistics(MockRecoveryTracker.Statistic.FileProcessed) should be(0)
          mockTracker.statistics(MockRecoveryTracker.Statistic.MetadataApplied) should be(0)
          mockTracker.statistics(MockRecoveryTracker.Statistic.FailureEncountered) should be(0)
          mockTracker.statistics(MockRecoveryTracker.Statistic.Completed) should be(1)
        }
      }
  }

  it should "track failed recovery operations" in {
    import Recovery._

    val mockTracker = new MockRecoveryTracker
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
            mockTracker.statistics(MockRecoveryTracker.Statistic.FileExamined) should be(0)
            mockTracker.statistics(MockRecoveryTracker.Statistic.FileCollected) should be(0)
            mockTracker.statistics(MockRecoveryTracker.Statistic.FileProcessed) should be(0)
            mockTracker.statistics(MockRecoveryTracker.Statistic.MetadataApplied) should be(0)
            mockTracker.statistics(MockRecoveryTracker.Statistic.FailureEncountered) should be(1)
            mockTracker.statistics(MockRecoveryTracker.Statistic.Completed) should be(1)
          }
      }
  }

  it should "allow stopping a running recovery" in {
    val currentSourceFile1Metadata = "/ops/source-file-1".asTestResource.extractMetadata(checksum)

    // metadata represents file state during previous backup
    val originalSourceFile1Metadata = currentSourceFile1Metadata.copy(checksum = BigInt(0)) // file changed since backup

    val originalMetadata = DatasetMetadata(
      contentChanged = Map(originalSourceFile1Metadata.path -> originalSourceFile1Metadata),
      metadataChanged = Map.empty,
      filesystem = FilesystemMetadata(changes = Seq(originalSourceFile1Metadata.path))
    )

    val mockApiClient = MockServerApiEndpointClient()

    val mockCoreClient = new MockServerCoreEndpointClient(
      self = Node.generateId(),
      crates = Map(originalSourceFile1Metadata.crate -> ByteString("dummy-encrypted-data"))
    )
    val mockTracker = new MockRecoveryTracker

    val recovery = createRecovery(
      metadata = originalMetadata,
      clients = Clients(api = mockApiClient, core = mockCoreClient),
      tracker = mockTracker
    )

    val _ = recovery.start()
    recovery.stop()

    eventually {
      mockTracker.statistics(MockRecoveryTracker.Statistic.Completed) should be(1)
    }
  }

  "A Recovery Descriptor" should "be creatable from a collector descriptor" in {
    val dataset = Fixtures.Datasets.Default
    val entry = Fixtures.Entries.Default.copy(definition = dataset.id)
    val metadata = DatasetMetadata.empty

    implicit val providers: Providers = Providers(
      checksum = checksum,
      staging = new DefaultFileStaging(
        storeDirectory = None,
        prefix = "staged-",
        suffix = ".tmp"
      ),
      decompressor = new MockCompression,
      decryptor = new MockEncryption {
        override def decrypt(metadataSecret: DeviceMetadataSecret): Flow[ByteString, ByteString, NotUsed] =
          Flow[ByteString].map(_ => DatasetMetadata.toByteString(metadata))
      },
      clients = Clients(
        api = new MockServerApiEndpointClient(self = Device.generateId()) {
          override def latestEntry(
            definition: DatasetDefinition.Id,
            until: Option[Instant]
          ): Future[Option[DatasetEntry]] =
            if (definition == dataset.id) {
              Future.successful(Some(entry))
            } else {
              Future.successful(None)
            }

          override def datasetEntry(entryId: DatasetEntry.Id): Future[DatasetEntry] =
            if (entryId == entry.id) {
              Future.successful(entry)
            } else {
              Future.failed(new IllegalArgumentException(s"Invalid entry ID provided: [$entryId]"))
            }
        },
        core = new MockServerCoreEndpointClient(
          self = Node.generateId(),
          crates = Map(entry.metadata -> ByteString.empty)
        )
      ),
      track = new MockRecoveryTracker
    )

    for {
      descriptorWithDefinition <- Recovery.Descriptor(
        query = None,
        collector = Recovery.Descriptor.Collector.WithDefinition(
          definition = dataset.id,
          until = Some(Instant.now())
        ),
        deviceSecret = secret
      )
      descriptorWithEntry <- Recovery.Descriptor(
        query = None,
        collector = Recovery.Descriptor.Collector.WithEntry(
          entry = entry.id
        ),
        deviceSecret = secret
      )
    } yield {
      descriptorWithDefinition.targetMetadata should be(metadata)
      descriptorWithDefinition.deviceSecret should be(secret)

      descriptorWithEntry.targetMetadata should be(metadata)
      descriptorWithEntry.deviceSecret should be(secret)
    }
  }

  it should "handle missing entries when creating from collector descriptor" in {
    val dataset = Fixtures.Datasets.Default

    implicit val providers: Providers = Providers(
      checksum = checksum,
      staging = new DefaultFileStaging(
        storeDirectory = None,
        prefix = "staged-",
        suffix = ".tmp"
      ),
      decompressor = new MockCompression,
      decryptor = new MockEncryption,
      clients = Clients(
        api = new MockServerApiEndpointClient(self = Device.generateId()) {
          override def latestEntry(
            definition: DatasetDefinition.Id,
            until: Option[Instant]
          ): Future[Option[DatasetEntry]] =
            Future.successful(None)
        },
        core = MockServerCoreEndpointClient()
      ),
      track = new MockRecoveryTracker
    )

    Recovery
      .Descriptor(
        query = None,
        collector = Recovery.Descriptor.Collector.WithDefinition(
          definition = dataset.id,
          until = Some(Instant.now())
        ),
        deviceSecret = secret
      )
      .map { result =>
        fail(s"Unexpected result received: [$result]")
      }
      .recover {
        case NonFatal(e: IllegalStateException) =>
          e.getMessage should be(s"Expected dataset entry for definition [${dataset.id}] but none was found")
      }
  }

  it should "be convertible to a recovery collector" in {
    implicit val providers: Providers = Providers(
      checksum = checksum,
      staging = new DefaultFileStaging(
        storeDirectory = None,
        prefix = "staged-",
        suffix = ".tmp"
      ),
      decompressor = new MockCompression,
      decryptor = new MockEncryption,
      clients = Clients(
        api = MockServerApiEndpointClient(),
        core = MockServerCoreEndpointClient()
      ),
      track = new MockRecoveryTracker
    )

    val descriptor = Recovery.Descriptor(
      targetMetadata = DatasetMetadata.empty,
      query = None,
      deviceSecret = secret
    )

    descriptor.toRecoveryCollector() shouldBe a[RecoveryCollector.Default]
  }

  "Recovery path queries" should "support checking for matches in paths" in {
    matchingPathRegexes.foreach { regex =>
      val query = PathQuery.ForAbsolutePath(query = new Regex(regex))
      withClue(s"Matching path [$matchingPath] with [$regex]:") {
        query.matches(matchingPath) should be(true)
      }
    }

    succeed
  }

  they should "support checking for matches in file names" in {
    matchingFileNameRegexes.foreach { regex =>
      val query = PathQuery.ForFileName(query = new Regex(regex))
      withClue(s"Matching file name in path [$matchingPath] with [$regex]:") {
        query.matches(matchingPath) should be(true)
      }
    }

    nonMatchingPathRegexes.foreach { regex =>
      val query = PathQuery.ForFileName(query = new Regex(regex))
      withClue(s"Matching file name in path [$matchingPath] with [$regex]:") {
        query.matches(matchingPath) should be(false)
      }
    }

    succeed
  }

  they should "create path queries from regex strings" in {
    PathQuery("/tmp/some-file.txt") shouldBe a[PathQuery.ForAbsolutePath]
    PathQuery("some-file.txt") shouldBe a[PathQuery.ForFileName]
  }

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(5.seconds, 250.milliseconds)

  private implicit val typedSystem: ActorSystem[SpawnProtocol] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol.behavior): Behavior[SpawnProtocol],
    "RecoverySpec"
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

  private val secret = DeviceSecret(
    user = User.generateId(),
    device = Device.generateId(),
    secret = ByteString("some-secret")
  )

  private val matchingPath = Paths.get("/tmp/a/b/c/test-file.json")

  private val matchingFileNameRegexes = Seq(
    """test-file""",
    """test-file\.json""",
    """.*"""
  )

  private val matchingPathRegexes = Seq(
    """/tmp/.*""",
    """/.*/a/.*/c"""
  )

  private val nonMatchingPathRegexes = Seq(
    """tmp""",
    """/tmp$""",
    """^/a/b/c.*"""
  )

  private def createRecovery(
    metadata: DatasetMetadata,
    clients: Clients,
    tracker: MockRecoveryTracker
  ): Recovery = {
    implicit val providers: Providers = Providers(
      checksum = checksum,
      staging = new DefaultFileStaging(
        storeDirectory = None,
        prefix = "staged-",
        suffix = ".tmp"
      ),
      decompressor = new MockCompression,
      decryptor = new MockEncryption,
      clients = clients,
      track = tracker
    )

    new Recovery(
      descriptor = Recovery.Descriptor(
        targetMetadata = metadata,
        query = None,
        deviceSecret = secret
      )
    )
  }

  override protected def afterAll(): Unit =
    typedSystem.terminate()
}
