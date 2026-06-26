package stasis.test.specs.unit.client.ops.integration

import java.nio.file.FileSystem
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference

import scala.concurrent.Future

import io.github.sndnv.layers.testing.FileSystemHelpers
import io.github.sndnv.layers.testing.FileSystemHelpers.FileSystemSetup
import io.github.sndnv.layers.testing.UnitSpec
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.stream.Materializer
import org.apache.pekko.util.ByteString

import stasis.client.analysis.Checksum
import stasis.client.analysis.PlatformMetadata
import stasis.client.api.clients.Clients
import stasis.client.collection.rules.Rule
import stasis.client.compression.Compression
import stasis.client.encryption.Aes
import stasis.client.encryption.secrets.DeviceSecret
import stasis.client.model.DatasetMetadata
import stasis.client.ops.ParallelismConfig
import stasis.client.ops.backup.Backup
import stasis.client.ops.backup.BackupEntityKind
import stasis.client.ops.backup.{Providers => BackupProviders}
import stasis.client.ops.recovery.Recovery
import stasis.client.ops.recovery.RecoveryEntityKind
import stasis.client.ops.recovery.{Providers => RecoveryProviders}
import stasis.client.staging.DefaultFileStaging
import stasis.shared.api.requests.CreateDatasetEntry
import stasis.shared.api.responses.CreatedDatasetEntry
import stasis.shared.model.devices.Device
import stasis.shared.model.users.User
import stasis.shared.secrets.SecretsConfig
import stasis.test.specs.unit.client.Fixtures
import stasis.test.specs.unit.client.mocks.MockBackupTracker
import stasis.test.specs.unit.client.mocks.MockClientTelemetryContext
import stasis.test.specs.unit.client.mocks.MockRecoveryTracker
import stasis.test.specs.unit.client.mocks.MockServerApiEndpointClient
import stasis.test.specs.unit.client.ops.integration.mocks.MockBackupLibraryKind
import stasis.test.specs.unit.client.ops.integration.mocks.MockLibrary
import stasis.test.specs.unit.client.ops.integration.mocks.MockMemoryServerCoreEndpointClient
import stasis.test.specs.unit.client.ops.integration.mocks.MockRecoveryLibraryKind

trait BackupAndRecoveryBehaviour { _: UnitSpec with FileSystemHelpers =>
  import BackupAndRecoveryBehaviour.BackupResult

  def backupAndRecovery(
    setup: FileSystemSetup
  )(implicit system: ActorSystem[Nothing], secretsConfig: SecretsConfig, parallelism: ParallelismConfig): Unit = {
    implicit val mat: Materializer = Materializer(system)

    val checksum: Checksum = Checksum.SHA256

    val compression: Compression = Compression(withDefaultCompression = "deflate", withDisabledExtensions = "")

    val secret: DeviceSecret = DeviceSecret(
      user = User.generateId(),
      device = Device.generateId(),
      secret = ByteString("some-secret")
    )

    it should "back up and recover filesystem entities" in withRetry {
      val (fs, _) = createMockFileSystem(setup = setup)

      val directory = createSourceDirectory(fs)

      val fileOne = writeFile(directory, "file-1", ByteString("filesystem content one"))
      val fileTwo = writeFile(directory, "file-2", ByteString("filesystem content two"))
      val fileThree = writeFile(directory, "file-3", ByteString("filesystem content three"))

      val backup = runBackup(
        filesystem = fs,
        rules = Seq(
          rule(s"+ ${directory.toAbsolutePath.toString} file-*"),
          rule(s"- ${directory.toAbsolutePath.toString} file-3")
        ),
        backupKinds = Seq(BackupEntityKind.Filesystem)
      )

      backup.tracker.statistics(MockBackupTracker.Statistic.FailureEncountered) should be(0)
      backup.core.pushed should be(3) // 2 file crates + 1 metadata crate

      Files.delete(fileOne)
      Files.delete(fileTwo)

      val recoveryTracker = runRecovery(
        filesystem = fs,
        metadata = backup.metadata,
        core = backup.core,
        recoveryKinds = Seq(RecoveryEntityKind.Filesystem)
      )

      backup.core.pulled should be(2) // 2 restored files
      recoveryTracker.statistics(MockRecoveryTracker.Statistic.FailureEncountered) should be(0)

      ByteString(Files.readAllBytes(fileOne)) should be(ByteString("filesystem content one"))
      ByteString(Files.readAllBytes(fileTwo)) should be(ByteString("filesystem content two"))
      Files.exists(fileThree) should be(true) // excluded from backup; left untouched
    }

    it should "back up and recover library entities" in withRetry {
      val (fs, _) = createMockFileSystem(setup = setup)
      val separator = fs.getSeparator

      val library = new MockLibrary(
        scheme = "library",
        entries = Map(
          libraryUri(separator, "photos", "a.dat") -> ByteString("library content a"),
          libraryUri(separator, "photos", "b.dat") -> ByteString("library content b"),
          libraryUri(separator, "photos", "skip.dat") -> ByteString("library content skip")
        )
      )

      val backup = runBackup(
        filesystem = fs,
        rules = Seq(
          rule(s"+ ${libraryUri(separator, "photos")} *"),
          rule(s"- ${libraryUri(separator, "photos", "skip.dat")} *"),
          rule("+ contacts:/all *")
        ),
        backupKinds = Seq(BackupEntityKind.Filesystem, new MockBackupLibraryKind(library))
      )

      backup.tracker.statistics(MockBackupTracker.Statistic.FailureEncountered) should be(1) // unknown scheme: contacts
      backup.core.pushed should be(3) // 2 library crates + 1 metadata crate

      val recoveryTracker = runRecovery(
        filesystem = fs,
        metadata = backup.metadata,
        core = backup.core,
        recoveryKinds = Seq(RecoveryEntityKind.Filesystem, new MockRecoveryLibraryKind(library))
      )

      backup.core.pulled should be(2) // 2 restored library entities
      recoveryTracker.statistics(MockRecoveryTracker.Statistic.FailureEncountered) should be(0)

      library.restored.toMap should be(
        Map(
          libraryUri(separator, "photos", "a.dat") -> ByteString("library content a"),
          libraryUri(separator, "photos", "b.dat") -> ByteString("library content b")
        )
      )

      library.restoredAttributes.keySet should be(
        Set(libraryUri(separator, "photos", "a.dat"), libraryUri(separator, "photos", "b.dat"))
      )
    }

    it should "back up and recover a mix of filesystem and library entities" in withRetry {
      val (fs, _) = createMockFileSystem(setup = setup)
      val directory = createSourceDirectory(fs)

      val fileOne = writeFile(directory, "file-1", ByteString("filesystem content one"))
      val fileTwo = writeFile(directory, "file-2", ByteString("filesystem content two"))

      val separator = fs.getSeparator

      val library = new MockLibrary(
        scheme = "library",
        entries = Map(
          libraryUri(separator, "photos", "a.dat") -> ByteString("library content a"),
          libraryUri(separator, "photos", "b.dat") -> ByteString("library content b"),
          libraryUri(separator, "photos", "skip.dat") -> ByteString("library content skip")
        )
      )

      val backup = runBackup(
        filesystem = fs,
        rules = Seq(
          rule(s"+ ${directory.toAbsolutePath.toString} file-*"),
          rule(s"+ ${libraryUri(separator, "photos")} *"),
          rule(s"- ${libraryUri(separator, "photos", "skip.dat")} *"),
          rule("+ contacts:/all *")
        ),
        backupKinds = Seq(BackupEntityKind.Filesystem, new MockBackupLibraryKind(library))
      )

      backup.tracker.statistics(MockBackupTracker.Statistic.FailureEncountered) should be(1) // unknown scheme: contacts
      backup.core.pushed should be(5) // 2 file crates + 2 library crates + 1 metadata crate

      Files.delete(fileOne)
      Files.delete(fileTwo)

      val recoveryTracker = runRecovery(
        filesystem = fs,
        metadata = backup.metadata,
        core = backup.core,
        recoveryKinds = Seq(RecoveryEntityKind.Filesystem, new MockRecoveryLibraryKind(library))
      )

      backup.core.pulled should be(4) // 2 files + 2 library entities
      recoveryTracker.statistics(MockRecoveryTracker.Statistic.FailureEncountered) should be(0)

      ByteString(Files.readAllBytes(fileOne)) should be(ByteString("filesystem content one"))
      ByteString(Files.readAllBytes(fileTwo)) should be(ByteString("filesystem content two"))

      library.restored.toMap should be(
        Map(
          libraryUri(separator, "photos", "a.dat") -> ByteString("library content a"),
          libraryUri(separator, "photos", "b.dat") -> ByteString("library content b")
        )
      )
    }

    def rule(line: String): Rule =
      Rule(line = line, lineNumber = 0).get

    def libraryUri(separator: String, segments: String*): String =
      s"library:$separator${segments.mkString(separator)}"

    def createSourceDirectory(filesystem: FileSystem): Path =
      Files.createDirectories(filesystem.getRootDirectories.iterator().next().resolve("source"))

    def writeFile(directory: Path, name: String, content: ByteString): Path = {
      val path = directory.resolve(name)
      val _ = Files.write(path, content.toArray)
      path
    }

    def runBackup(
      filesystem: FileSystem,
      rules: Seq[Rule],
      backupKinds: Seq[BackupEntityKind]
    ): BackupResult = {
      val core = new MockMemoryServerCoreEndpointClient()
      val capturedEntry: AtomicReference[Option[CreateDatasetEntry]] = new AtomicReference(None)

      val api = new MockServerApiEndpointClient(self = Device.generateId()) {
        override def createDatasetEntry(request: CreateDatasetEntry): Future[CreatedDatasetEntry] = {
          capturedEntry.set(Some(request))
          super.createDatasetEntry(request)
        }
      }

      val tracker = new MockBackupTracker

      implicit val providers: BackupProviders = BackupProviders(
        checksum = checksum,
        staging = new DefaultFileStaging(storeDirectory = None, prefix = "staged-", suffix = ".tmp")(system.executionContext),
        compression = compression,
        encryptor = Aes,
        decryptor = Aes,
        clients = Clients(api = api, core = core),
        track = tracker,
        telemetry = MockClientTelemetryContext(),
        filesystem = filesystem,
        kinds = backupKinds
      )

      val backup = new Backup(
        descriptor = Backup.Descriptor(
          targetDataset = Fixtures.Datasets.Default,
          latestEntry = None,
          latestMetadata = None,
          deviceSecret = secret,
          collector = Backup.Descriptor.Collector.WithRules(rules = rules),
          limits = Backup.Limits(maxChunkSize = 8192, maxPartSize = 16384)
        )
      )

      backup.start().await

      val request = capturedEntry.get().getOrElse(fail("No dataset entry was created during backup"))
      val metadataSource = core.content(request.metadata).getOrElse(fail("No metadata crate was stored during backup"))

      val metadata = DatasetMetadata
        .decrypt(
          metadataCrate = request.metadata,
          metadataSecret = secret.toMetadataSecret(request.metadata),
          metadata = Some(metadataSource),
          decoder = Aes
        )(system.executionContext, mat)
        .await

      BackupResult(metadata = metadata, core = core, tracker = tracker)
    }

    def runRecovery(
      filesystem: FileSystem,
      metadata: DatasetMetadata,
      core: MockMemoryServerCoreEndpointClient,
      recoveryKinds: Seq[RecoveryEntityKind]
    ): MockRecoveryTracker = {
      val tracker = new MockRecoveryTracker

      implicit val providers: RecoveryProviders = RecoveryProviders(
        checksum = checksum,
        staging = new DefaultFileStaging(storeDirectory = None, prefix = "staged-", suffix = ".tmp")(system.executionContext),
        compression = compression,
        decryptor = Aes,
        clients = Clients(api = MockServerApiEndpointClient(), core = core),
        track = tracker,
        telemetry = MockClientTelemetryContext(),
        filesystem = filesystem,
        metadataDefaults = PlatformMetadata.Defaults.default(),
        kinds = recoveryKinds
      )

      val recovery = new Recovery(
        descriptor = Recovery.Descriptor(
          targetMetadata = metadata,
          query = None,
          destination = None,
          deviceSecret = secret
        )
      )

      recovery.start().await

      tracker
    }
  }

}

object BackupAndRecoveryBehaviour {
  final case class BackupResult(
    metadata: DatasetMetadata,
    core: MockMemoryServerCoreEndpointClient,
    tracker: MockBackupTracker
  )
}
