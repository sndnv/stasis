package stasis.test.specs.unit.client.ops.recovery

import java.nio.file.Paths
import java.time.Instant

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.control.NonFatal
import scala.util.matching.Regex

import org.apache.pekko.Done
import org.apache.pekko.NotUsed
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.stream.scaladsl.Flow
import org.apache.pekko.util.ByteString
import org.scalatest.Assertion
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.Eventually

import stasis.client.analysis.Checksum
import stasis.client.api.clients.Clients
import stasis.client.collection.RecoveryCollector
import stasis.client.encryption.secrets.DeviceMetadataSecret
import stasis.client.encryption.secrets.DeviceSecret
import stasis.client.model.DatasetMetadata
import stasis.client.model.FilesystemMetadata
import stasis.client.ops.ParallelismConfig
import stasis.client.ops.exceptions.OperationStopped
import stasis.client.ops.recovery.Providers
import stasis.client.ops.recovery.Recovery
import stasis.client.ops.recovery.Recovery.PathQuery
import stasis.client.staging.DefaultFileStaging
import stasis.core.routing.Node
import stasis.layers.FileSystemHelpers.FileSystemSetup
import stasis.shared.model.datasets.DatasetDefinition
import stasis.shared.model.datasets.DatasetEntry
import stasis.shared.model.devices.Device
import stasis.shared.model.users.User
import stasis.shared.ops.Operation
import stasis.shared.secrets.SecretsConfig
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.Fixtures
import stasis.test.specs.unit.client.ResourceHelpers
import stasis.test.specs.unit.client.mocks._

class RecoverySpec extends AsyncUnitSpec with ResourceHelpers with Eventually with BeforeAndAfterAll {
  "A Recovery operation" should "process recovery of files" in withRetry {
    val currentSourceFile1Metadata = "/ops/source-file-1".asTestResource.extractFileMetadata(checksum)
    val currentSourceFile2Metadata = "/ops/source-file-2".asTestResource.extractFileMetadata(checksum)
    val currentSourceFile3Metadata = "/ops/source-file-3".asTestResource.extractFileMetadata(checksum)

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
      crates = (
        originalSourceFile1Metadata.crates
          ++ originalSourceFile2Metadata.crates
          ++ originalSourceFile3Metadata.crates
      ).values
        .map((_, ByteString("dummy-encrypted-data")))
        .toMap
    )
    val mockTracker = new MockRecoveryTracker

    val recovery = createRecovery(
      metadata = originalMetadata,
      clients = Clients(api = mockApiClient, core = mockCoreClient),
      tracker = mockTracker
    )

    recovery.start().map { _ =>
      eventually[Assertion] {
        // data pulled for source-file-1; source-file-2 is unchanged; source-file-3 has only metadata changes;
        mockCoreClient.statistics(MockServerCoreEndpointClient.Statistic.CratePulled) should be(1)
        mockCoreClient.statistics(MockServerCoreEndpointClient.Statistic.CratePushed) should be(0)

        mockTracker.statistics(MockRecoveryTracker.Statistic.EntityExamined) should be(3)
        mockTracker.statistics(MockRecoveryTracker.Statistic.EntityCollected) should be(2)
        mockTracker.statistics(MockRecoveryTracker.Statistic.EntityProcessingStarted) should be(2)
        mockTracker.statistics(MockRecoveryTracker.Statistic.EntityPartProcessed) should be(1)
        mockTracker.statistics(MockRecoveryTracker.Statistic.EntityProcessed) should be(2)
        mockTracker.statistics(MockRecoveryTracker.Statistic.MetadataApplied) should be(2)
        mockTracker.statistics(MockRecoveryTracker.Statistic.FailureEncountered) should be(0)
        mockTracker.statistics(MockRecoveryTracker.Statistic.Completed) should be(1)
      }
    }
  }

  it should "support recovering to a different destination" in withRetry {
    val targetDirectory = "/ops/recovery".asTestResource
    targetDirectory.clear().await

    val sourceDirectory1Metadata = "/ops".asTestResource.extractDirectoryMetadata().withRootAt("/ops")
    val sourceDirectory2Metadata = "/ops/nested".asTestResource.extractDirectoryMetadata().withRootAt("/ops")

    val sourceFile1Metadata = "/ops/source-file-1".asTestResource.extractFileMetadata(checksum).withRootAt("/ops")
    val sourceFile2Metadata = "/ops/source-file-2".asTestResource.extractFileMetadata(checksum).withRootAt("/ops")
    val sourceFile3Metadata = "/ops/source-file-3".asTestResource.extractFileMetadata(checksum).withRootAt("/ops")
    val sourceFile4Metadata = "/ops/nested/source-file-4".asTestResource.extractFileMetadata(checksum).withRootAt("/ops")
    val sourceFile5Metadata = "/ops/nested/source-file-5".asTestResource.extractFileMetadata(checksum).withRootAt("/ops")

    val metadata = DatasetMetadata(
      contentChanged = Map(
        sourceFile1Metadata.path -> sourceFile1Metadata,
        sourceFile2Metadata.path -> sourceFile2Metadata,
        sourceFile3Metadata.path -> sourceFile3Metadata,
        sourceFile4Metadata.path -> sourceFile4Metadata,
        sourceFile5Metadata.path -> sourceFile5Metadata
      ),
      metadataChanged = Map(
        sourceDirectory1Metadata.path -> sourceDirectory1Metadata,
        sourceDirectory2Metadata.path -> sourceDirectory2Metadata
      ),
      filesystem = FilesystemMetadata(
        changes = Seq(
          sourceDirectory1Metadata.path,
          sourceDirectory2Metadata.path,
          sourceFile1Metadata.path,
          sourceFile2Metadata.path,
          sourceFile3Metadata.path,
          sourceFile4Metadata.path,
          sourceFile5Metadata.path
        )
      )
    )

    val mockApiClient = MockServerApiEndpointClient()

    val mockCoreClient = new MockServerCoreEndpointClient(
      self = Node.generateId(),
      crates = (
        sourceFile1Metadata.crates
          ++ sourceFile2Metadata.crates
          ++ sourceFile3Metadata.crates
          ++ sourceFile4Metadata.crates
          ++ sourceFile5Metadata.crates
      ).values
        .map((_, ByteString("dummy-encrypted-data")))
        .toMap
    )

    val mockTracker = new MockRecoveryTracker

    val destination = Recovery.Destination(path = targetDirectory.toAbsolutePath.toString, keepStructure = true)

    val recovery = createRecovery(
      metadata = metadata,
      clients = Clients(api = mockApiClient, core = mockCoreClient),
      tracker = mockTracker,
      destination = Some(destination)
    )

    recovery.start().map { _ =>
      eventually[Assertion] {
        // data pulled for all entities; 2 directories and 5 files
        mockCoreClient.statistics(MockServerCoreEndpointClient.Statistic.CratePulled) should be(5)
        mockCoreClient.statistics(MockServerCoreEndpointClient.Statistic.CratePushed) should be(0)

        mockTracker.statistics(MockRecoveryTracker.Statistic.EntityExamined) should be(7)
        mockTracker.statistics(MockRecoveryTracker.Statistic.EntityCollected) should be(7)
        mockTracker.statistics(MockRecoveryTracker.Statistic.EntityProcessingStarted) should be(7)
        mockTracker.statistics(MockRecoveryTracker.Statistic.EntityPartProcessed) should be(5)
        mockTracker.statistics(MockRecoveryTracker.Statistic.EntityProcessed) should be(7)
        mockTracker.statistics(MockRecoveryTracker.Statistic.MetadataApplied) should be(7)
        mockTracker.statistics(MockRecoveryTracker.Statistic.FailureEncountered) should be(0)
        mockTracker.statistics(MockRecoveryTracker.Statistic.Completed) should be(1)
      }
    }
  }

  it should "handle failures of specific files" in withRetry {
    val currentSourceFile1Metadata = "/ops/source-file-1".asTestResource.extractFileMetadata(checksum)
    val currentSourceFile2Metadata = "/ops/source-file-2".asTestResource.extractFileMetadata(checksum)
    val currentSourceFile3Metadata = "/ops/source-file-3".asTestResource.extractFileMetadata(checksum)

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
      crates = (originalSourceFile1Metadata.crates ++ originalSourceFile2Metadata.crates).values
        .map((_, ByteString("dummy-encrypted-data")))
        .toMap
    )
    val mockTracker = new MockRecoveryTracker

    val recovery = createRecovery(
      metadata = originalMetadata,
      clients = Clients(api = mockApiClient, core = mockCoreClient),
      tracker = mockTracker
    )

    recovery.start().map { _ =>
      eventually[Assertion] {
        // data pulled for source-file-1, source-file-2; source-file-3 has not data and will fail
        mockCoreClient.statistics(MockServerCoreEndpointClient.Statistic.CratePulled) should be(2)
        mockCoreClient.statistics(MockServerCoreEndpointClient.Statistic.CratePushed) should be(0)

        mockTracker.statistics(MockRecoveryTracker.Statistic.EntityExamined) should be(3)
        mockTracker.statistics(MockRecoveryTracker.Statistic.EntityCollected) should be(3)
        mockTracker.statistics(MockRecoveryTracker.Statistic.EntityProcessingStarted) should be(3)
        mockTracker.statistics(MockRecoveryTracker.Statistic.EntityPartProcessed) should be(2)
        mockTracker.statistics(MockRecoveryTracker.Statistic.EntityProcessed) should be(2)
        mockTracker.statistics(MockRecoveryTracker.Statistic.MetadataApplied) should be(2)
        mockTracker.statistics(MockRecoveryTracker.Statistic.FailureEncountered) should be(1)
        mockTracker.statistics(MockRecoveryTracker.Statistic.Completed) should be(1)
      }
    }
  }

  it should "handle general recovery failures" in withRetry {
    val currentSourceFile1Metadata = "/ops/source-file-1".asTestResource.extractFileMetadata(checksum)
    val originalSourceFile1Metadata = currentSourceFile1Metadata.copy(checksum = BigInt(0))

    val originalMetadata = DatasetMetadata(
      contentChanged = Map(
        originalSourceFile1Metadata.path -> originalSourceFile1Metadata
      ),
      metadataChanged = Map.empty,
      filesystem = FilesystemMetadata(
        entities = Map(
          currentSourceFile1Metadata.path -> FilesystemMetadata.EntityState.Existing(DatasetEntry.generateId())
        )
      )
    )

    val mockApiClient = new MockServerApiEndpointClient(self = Device.generateId()) {
      override def datasetMetadata(entry: DatasetEntry.Id): Future[DatasetMetadata] =
        Future.failed(new RuntimeException("Test failure"))
    }

    val mockCoreClient = MockServerCoreEndpointClient()

    val mockTracker = new MockRecoveryTracker

    val recovery = createRecovery(
      metadata = originalMetadata,
      clients = Clients(api = mockApiClient, core = mockCoreClient),
      tracker = mockTracker
    )

    recovery.start().map { _ =>
      eventually[Assertion] {
        mockCoreClient.statistics(MockServerCoreEndpointClient.Statistic.CratePulled) should be(0)
        mockCoreClient.statistics(MockServerCoreEndpointClient.Statistic.CratePushed) should be(0)

        mockTracker.statistics(MockRecoveryTracker.Statistic.EntityExamined) should be(0)
        mockTracker.statistics(MockRecoveryTracker.Statistic.EntityCollected) should be(0)
        mockTracker.statistics(MockRecoveryTracker.Statistic.EntityProcessingStarted) should be(0)
        mockTracker.statistics(MockRecoveryTracker.Statistic.EntityPartProcessed) should be(0)
        mockTracker.statistics(MockRecoveryTracker.Statistic.EntityProcessed) should be(0)
        mockTracker.statistics(MockRecoveryTracker.Statistic.MetadataApplied) should be(0)
        mockTracker.statistics(MockRecoveryTracker.Statistic.FailureEncountered) should be(1)
        mockTracker.statistics(MockRecoveryTracker.Statistic.Completed) should be(1)
      }
    }
  }

  it should "track successful recovery operations" in withRetry {
    import Recovery._

    val mockTracker = new MockRecoveryTracker
    implicit val id: Operation.Id = Operation.generateId()

    val operation: Future[Done] = Future.successful(Done)
    val trackedOperation: Future[Done] = operation.trackWith(mockTracker)

    trackedOperation
      .map { result =>
        result should be(Done)

        eventually[Assertion] {
          mockTracker.statistics(MockRecoveryTracker.Statistic.EntityExamined) should be(0)
          mockTracker.statistics(MockRecoveryTracker.Statistic.EntityCollected) should be(0)
          mockTracker.statistics(MockRecoveryTracker.Statistic.EntityProcessingStarted) should be(0)
          mockTracker.statistics(MockRecoveryTracker.Statistic.EntityPartProcessed) should be(0)
          mockTracker.statistics(MockRecoveryTracker.Statistic.EntityProcessed) should be(0)
          mockTracker.statistics(MockRecoveryTracker.Statistic.MetadataApplied) should be(0)
          mockTracker.statistics(MockRecoveryTracker.Statistic.FailureEncountered) should be(0)
          mockTracker.statistics(MockRecoveryTracker.Statistic.Completed) should be(1)
        }
      }
  }

  it should "track failed recovery operations" in withRetry {
    import Recovery._

    val mockTracker = new MockRecoveryTracker
    implicit val id: Operation.Id = Operation.generateId()

    val operation: Future[Done] = Future.failed(new RuntimeException("Test Failure"))
    val trackedOperation: Future[Done] = operation.trackWith(mockTracker)

    trackedOperation
      .map { result =>
        fail(s"Unexpected result received: [$result]")
      }
      .recover { case NonFatal(e) =>
        e shouldBe a[RuntimeException]

        eventually[Assertion] {
          mockTracker.statistics(MockRecoveryTracker.Statistic.EntityExamined) should be(0)
          mockTracker.statistics(MockRecoveryTracker.Statistic.EntityCollected) should be(0)
          mockTracker.statistics(MockRecoveryTracker.Statistic.EntityProcessingStarted) should be(0)
          mockTracker.statistics(MockRecoveryTracker.Statistic.EntityPartProcessed) should be(0)
          mockTracker.statistics(MockRecoveryTracker.Statistic.EntityProcessed) should be(0)
          mockTracker.statistics(MockRecoveryTracker.Statistic.MetadataApplied) should be(0)
          mockTracker.statistics(MockRecoveryTracker.Statistic.FailureEncountered) should be(1)
          mockTracker.statistics(MockRecoveryTracker.Statistic.Completed) should be(0)
        }
      }
  }

  it should "track stopped recovery operations" in withRetry {
    import Recovery._

    val mockTracker = new MockRecoveryTracker
    implicit val id: Operation.Id = Operation.generateId()

    val operation: Future[Done] = Future.failed(OperationStopped(message = "Stopped"))
    val trackedOperation: Future[Done] = operation.trackWith(mockTracker)

    trackedOperation
      .map { result =>
        fail(s"Unexpected result received: [$result]")
      }
      .recover { case NonFatal(e) =>
        e shouldBe an[OperationStopped]

        eventually[Assertion] {
          mockTracker.statistics(MockRecoveryTracker.Statistic.EntityExamined) should be(0)
          mockTracker.statistics(MockRecoveryTracker.Statistic.EntityCollected) should be(0)
          mockTracker.statistics(MockRecoveryTracker.Statistic.EntityProcessingStarted) should be(0)
          mockTracker.statistics(MockRecoveryTracker.Statistic.EntityPartProcessed) should be(0)
          mockTracker.statistics(MockRecoveryTracker.Statistic.EntityProcessed) should be(0)
          mockTracker.statistics(MockRecoveryTracker.Statistic.MetadataApplied) should be(0)
          mockTracker.statistics(MockRecoveryTracker.Statistic.FailureEncountered) should be(0)
          mockTracker.statistics(MockRecoveryTracker.Statistic.Completed) should be(0)
        }
      }
  }

  it should "allow stopping a running recovery" in withRetry {
    val currentSourceFile1Metadata = "/ops/source-file-1".asTestResource.extractFileMetadata(checksum)

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
      crates = originalSourceFile1Metadata.crates.values.map((_, ByteString("dummy-encrypted-data"))).toMap
    )
    val mockTracker = new MockRecoveryTracker

    val recovery = createRecovery(
      metadata = originalMetadata,
      clients = Clients(api = mockApiClient, core = mockCoreClient),
      tracker = mockTracker
    )

    val _ = recovery.start()
    recovery.stop()

    eventually[Assertion] {
      mockTracker.statistics(MockRecoveryTracker.Statistic.FailureEncountered) should be(0)
      mockTracker.statistics(MockRecoveryTracker.Statistic.Completed) should be(0)
    }
  }

  "A Recovery Descriptor" should "be creatable from a collector descriptor" in withRetry {
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
      compression = MockCompression(),
      decryptor = new MockEncryption {
        override def decrypt(metadataSecret: DeviceMetadataSecret): Flow[ByteString, ByteString, NotUsed] =
          Flow[ByteString].mapAsync(parallelism = 1)(_ => DatasetMetadata.toByteString(metadata))
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
      track = new MockRecoveryTracker,
      telemetry = MockClientTelemetryContext()
    )

    for {
      descriptorWithDefinition <- Recovery.Descriptor(
        query = None,
        destination = None,
        collector = Recovery.Descriptor.Collector.WithDefinition(
          definition = dataset.id,
          until = Some(Instant.now())
        ),
        deviceSecret = secret
      )
      descriptorWithEntry <- Recovery.Descriptor(
        query = None,
        destination = None,
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

  it should "handle missing entries when creating from collector descriptor" in withRetry {
    val dataset = Fixtures.Datasets.Default

    implicit val providers: Providers = Providers(
      checksum = checksum,
      staging = new DefaultFileStaging(
        storeDirectory = None,
        prefix = "staged-",
        suffix = ".tmp"
      ),
      compression = MockCompression(),
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
      track = new MockRecoveryTracker,
      telemetry = MockClientTelemetryContext()
    )

    Recovery
      .Descriptor(
        query = None,
        destination = None,
        collector = Recovery.Descriptor.Collector.WithDefinition(
          definition = dataset.id,
          until = Some(Instant.now())
        ),
        deviceSecret = secret
      )
      .map { result =>
        fail(s"Unexpected result received: [$result]")
      }
      .recover { case NonFatal(e: IllegalStateException) =>
        e.getMessage should be(s"Expected dataset entry for definition [${dataset.id}] but none was found")
      }
  }

  it should "be convertible to a recovery collector" in withRetry {
    implicit val providers: Providers = Providers(
      checksum = checksum,
      staging = new DefaultFileStaging(
        storeDirectory = None,
        prefix = "staged-",
        suffix = ".tmp"
      ),
      compression = MockCompression(),
      decryptor = new MockEncryption,
      clients = Clients(
        api = MockServerApiEndpointClient(),
        core = MockServerCoreEndpointClient()
      ),
      track = new MockRecoveryTracker,
      telemetry = MockClientTelemetryContext()
    )

    val descriptor = Recovery.Descriptor(
      targetMetadata = DatasetMetadata.empty,
      query = None,
      destination = None,
      deviceSecret = secret
    )

    descriptor.toRecoveryCollector() shouldBe a[RecoveryCollector.Default]
  }

  "Recovery path queries" should "support checking for matches in withRetry paths" in withRetry {
    matchingPathRegexes.foreach { regex =>
      val query = PathQuery.ForAbsolutePath(query = new Regex(regex))
      withClue(s"Matching path [$matchingPath] with [$regex]:") {
        query.matches(matchingPath) should be(true)
      }
    }

    succeed
  }

  they should "support checking for matches in withRetry file names" in withRetry {
    matchingFileNameRegexes.foreach { regex =>
      val query = PathQuery.ForFileName(query = new Regex(regex))
      withClue(s"Matching file name in withRetry path [$matchingPath] with [$regex]:") {
        query.matches(matchingPath) should be(true)
      }
    }

    nonMatchingPathRegexes.foreach { regex =>
      val query = PathQuery.ForFileName(query = new Regex(regex))
      withClue(s"Matching file name in withRetry path [$matchingPath] with [$regex]:") {
        query.matches(matchingPath) should be(false)
      }
    }

    succeed
  }

  they should "create path queries from regex strings" in withRetry {
    PathQuery("/tmp/some-file.txt") shouldBe a[PathQuery.ForAbsolutePath]
    PathQuery("some-file.txt") shouldBe a[PathQuery.ForFileName]
  }

  "A Recovery destination" should "be convertible to TargetEntity destination" in withRetry {
    import Recovery._
    import stasis.client.model.TargetEntity

    val (filesystem, _) = createMockFileSystem(
      setup = FileSystemSetup.Unix
    )

    val recoveryDestination = Destination(
      path = "/tmp/test/path",
      keepStructure = false,
      filesystem = filesystem
    )

    Some(recoveryDestination).toTargetEntityDestination should be(
      TargetEntity.Destination.Directory(
        path = filesystem.getPath(recoveryDestination.path),
        keepDefaultStructure = recoveryDestination.keepStructure
      )
    )

    Option.empty[Destination].toTargetEntityDestination should be(
      TargetEntity.Destination.Default
    )
  }

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(5.seconds, 250.milliseconds)

  private implicit val typedSystem: ActorSystem[Nothing] = ActorSystem(
    Behaviors.ignore,
    "RecoverySpec"
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
    tracker: MockRecoveryTracker,
    destination: Option[Recovery.Destination] = None
  ): Recovery = {
    implicit val providers: Providers = Providers(
      checksum = checksum,
      staging = new DefaultFileStaging(
        storeDirectory = None,
        prefix = "staged-",
        suffix = ".tmp"
      ),
      compression = MockCompression(),
      decryptor = new MockEncryption,
      clients = clients,
      track = tracker,
      telemetry = MockClientTelemetryContext()
    )

    new Recovery(
      descriptor = Recovery.Descriptor(
        targetMetadata = metadata,
        query = None,
        destination = destination,
        deviceSecret = secret
      )
    )
  }

  override protected def afterAll(): Unit =
    typedSystem.terminate()
}
