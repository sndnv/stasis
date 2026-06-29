package stasis.test.client_android.lib.ops.integration

import io.kotest.assertions.fail
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString
import stasis.client_android.lib.analysis.Checksum
import stasis.client_android.lib.api.clients.Clients
import stasis.client_android.lib.collection.rules.Rule
import stasis.client_android.lib.compression.Compression
import stasis.client_android.lib.encryption.Aes
import stasis.client_android.lib.encryption.secrets.DeviceSecret
import stasis.client_android.lib.encryption.secrets.Secret
import stasis.client_android.lib.model.DatasetMetadata
import stasis.client_android.lib.model.server.api.requests.CreateDatasetEntry
import stasis.client_android.lib.ops.backup.Backup
import stasis.client_android.lib.ops.backup.BackupEntityKind
import stasis.client_android.lib.ops.backup.Providers as BackupProviders
import stasis.client_android.lib.ops.recovery.Recovery
import stasis.client_android.lib.ops.recovery.RecoveryEntityKind
import stasis.client_android.lib.ops.recovery.RecoverySourceKind
import stasis.client_android.lib.ops.recovery.Providers as RecoveryProviders
import stasis.client_android.lib.staging.DefaultFileStaging
import stasis.client_android.lib.telemetry.analytics.AnalyticsCollector
import stasis.test.client_android.lib.Fixtures
import stasis.test.client_android.lib.eventually
import stasis.test.client_android.lib.mocks.MockBackupTracker
import stasis.test.client_android.lib.mocks.MockRecoveryTracker
import stasis.test.client_android.lib.mocks.MockServerApiEndpointClient
import stasis.test.client_android.lib.ops.integration.mocks.MockBackupLibraryKind
import stasis.test.client_android.lib.ops.integration.mocks.MockLibrary
import stasis.test.client_android.lib.ops.integration.mocks.MockMemoryServerCoreEndpointClient
import stasis.test.client_android.lib.ops.integration.mocks.MockRecoveryLibraryKind
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class BackupAndRecoverySpec : WordSpec({
    val operationScope = CoroutineScope(Dispatchers.IO)

    val checksum = Checksum.Companion.SHA256

    val compression = Compression(withDefaultCompression = "deflate", withDisabledExtensions = "")

    val secretsConfig = Secret.Config(
        derivation = Secret.Config.DerivationConfig(
            encryption = Secret.EncryptionKeyDerivationConfig(
                secretSize = 64,
                iterations = 100000,
                saltPrefix = "unit-test"
            ),
            authentication = Secret.AuthenticationKeyDerivationConfig(
                enabled = true,
                secretSize = 64,
                iterations = 100000,
                saltPrefix = "unit-test"
            )
        ),
        encryption = Secret.Config.EncryptionConfig(
            file = Secret.EncryptionSecretConfig(keySize = 16, ivSize = 16),
            metadata = Secret.EncryptionSecretConfig(keySize = 24, ivSize = 32),
            deviceSecret = Secret.EncryptionSecretConfig(keySize = 32, ivSize = 64)
        )
    )

    val secret = DeviceSecret(
        user = UUID.randomUUID(),
        device = UUID.randomUUID(),
        secret = "some-secret".toByteArray().toByteString(),
        target = secretsConfig
    )

    fun includeRule(id: Long, source: String, pattern: String): Rule =
        Rule(id = id, operation = Rule.Operation.Include, source = source, pattern = pattern, definition = null)

    fun excludeRule(id: Long, source: String, pattern: String): Rule =
        Rule(id = id, operation = Rule.Operation.Exclude, source = source, pattern = pattern, definition = null)

    fun libraryUri(vararg segments: String): String =
        "library:/" + segments.joinToString("/")

    fun writeFile(directory: Path, name: String, content: String): Path {
        val path = directory.resolve(name)
        Files.write(path, content.toByteArray())
        return path
    }

    suspend fun runBackup(rules: List<Rule>, backupKinds: List<BackupEntityKind>): BackupResult {
        val core = MockMemoryServerCoreEndpointClient()
        val api = MockServerApiEndpointClient()
        val tracker = MockBackupTracker()

        val providers = BackupProviders(
            checksum = checksum,
            staging = DefaultFileStaging(storeDirectory = null, prefix = "staged-", suffix = ".tmp"),
            compression = compression,
            encryptor = Aes,
            decryptor = Aes,
            clients = Clients(api = api, core = core),
            track = tracker,
            analytics = AnalyticsCollector.NoOp,
            kinds = backupKinds
        )

        val backup = Backup(
            descriptor = Backup.Descriptor(
                targetDataset = Fixtures.Datasets.Default,
                latestEntry = null,
                latestMetadata = null,
                deviceSecret = secret,
                collector = Backup.Descriptor.Collector.WithRules(rules = rules),
                limits = Backup.Descriptor.Limits(maxPartSize = 16384)
            ),
            providers = providers
        )

        val completed = AtomicBoolean(false)
        val error = AtomicReference<Throwable?>(null)

        backup.start(withScope = operationScope) { e ->
            error.set(e)
            completed.set(true)
        }

        eventually { completed.get() shouldBe (true) }
        error.get()?.let { fail("Backup failed: $it") }

        val request = api.lastRequest<CreateDatasetEntry>() ?: fail("No dataset entry was created during backup")
        val metadataSource = core.content(request.metadata) ?: fail("No metadata crate was stored during backup")

        val metadata = DatasetMetadata.decrypt(
            metadataCrate = request.metadata,
            metadataSecret = secret.toMetadataSecret(request.metadata),
            metadata = metadataSource,
            decoder = Aes
        )

        return BackupResult(metadata = metadata, core = core, tracker = tracker)
    }

    suspend fun runRecovery(
        filesystem: FileSystem,
        metadata: DatasetMetadata,
        core: MockMemoryServerCoreEndpointClient,
        recoveryKinds: List<RecoveryEntityKind>,
        sources: Set<RecoverySourceKind>
    ): MockRecoveryTracker {
        val tracker = MockRecoveryTracker()

        val providers = RecoveryProviders(
            checksum = checksum,
            staging = DefaultFileStaging(storeDirectory = null, prefix = "staged-", suffix = ".tmp"),
            compression = compression,
            decryptor = Aes,
            clients = Clients(api = MockServerApiEndpointClient(), core = core),
            track = tracker,
            analytics = AnalyticsCollector.NoOp,
            kinds = recoveryKinds
        )

        val recovery = Recovery(
            descriptor = Recovery.Descriptor(
                targetMetadata = metadata,
                entities = null,
                sources = sources,
                destination = null,
                deviceSecret = secret,
                filesystem = filesystem
            ),
            providers = providers
        )

        val completed = AtomicBoolean(false)
        val error = AtomicReference<Throwable?>(null)

        recovery.start(withScope = operationScope) { e ->
            error.set(e)
            completed.set(true)
        }

        eventually { completed.get() shouldBe (true) }
        error.get()?.let { fail("Recovery failed: $it") }

        return tracker
    }

    "Backup and recovery" should {
        "back up and recover filesystem entities" {
            val directory = Files.createTempDirectory("backup-recovery-fs")
            directory.toFile().deleteOnExit()

            val fileOne = writeFile(directory, "file-1", "filesystem content one")
            val fileTwo = writeFile(directory, "file-2", "filesystem content two")
            val fileThree = writeFile(directory, "file-3", "filesystem content three")

            val source = directory.toAbsolutePath().toString()

            val backup = runBackup(
                rules = listOf(
                    includeRule(id = 0, source = source, pattern = "file-*"),
                    excludeRule(id = 1, source = source, pattern = "file-3")
                ),
                backupKinds = listOf(BackupEntityKind.Filesystem)
            )

            backup.tracker.statistics[MockBackupTracker.Statistic.FailureEncountered] shouldBe (0)
            backup.core.pushed shouldBe (3) // 2 file crates + 1 metadata crate

            Files.delete(fileOne)
            Files.delete(fileTwo)

            val recoveryTracker = runRecovery(
                filesystem = FileSystems.getDefault(),
                metadata = backup.metadata,
                core = backup.core,
                recoveryKinds = listOf(RecoveryEntityKind.Filesystem),
                sources = setOf(RecoverySourceKind.Filesystem)
            )

            backup.core.pulled shouldBe (2) // 2 restored files
            recoveryTracker.statistics[MockRecoveryTracker.Statistic.FailureEncountered] shouldBe (0)

            String(Files.readAllBytes(fileOne)) shouldBe ("filesystem content one")
            String(Files.readAllBytes(fileTwo)) shouldBe ("filesystem content two")
            Files.exists(fileThree) shouldBe (true) // excluded from backup; left untouched
        }

        "back up and recover library entities" {
            val library = MockLibrary(
                scheme = "library",
                entries = mapOf(
                    libraryUri("photos", "a.dat") to "library content a".encodeUtf8(),
                    libraryUri("photos", "b.dat") to "library content b".encodeUtf8(),
                    libraryUri("photos", "skip.dat") to "library content skip".encodeUtf8()
                )
            )

            val backup = runBackup(
                rules = listOf(
                    includeRule(id = 0, source = libraryUri("photos"), pattern = "*"),
                    excludeRule(id = 1, source = libraryUri("photos", "skip.dat"), pattern = "*"),
                    includeRule(id = 2, source = "contacts:/all", pattern = "*")
                ),
                backupKinds = listOf(BackupEntityKind.Filesystem, MockBackupLibraryKind(library))
            )

            backup.tracker.statistics[MockBackupTracker.Statistic.FailureEncountered] shouldBe (1) // unknown scheme: contacts
            backup.core.pushed shouldBe (3) // 2 library crates + 1 metadata crate

            val recoveryTracker = runRecovery(
                filesystem = FileSystems.getDefault(),
                metadata = backup.metadata,
                core = backup.core,
                recoveryKinds = listOf(RecoveryEntityKind.Filesystem, MockRecoveryLibraryKind(library)),
                sources = setOf(RecoverySourceKind.Filesystem, RecoverySourceKind.Library("library"))
            )

            backup.core.pulled shouldBe (2) // 2 restored library entities
            recoveryTracker.statistics[MockRecoveryTracker.Statistic.FailureEncountered] shouldBe (0)

            library.restored.toMap() shouldBe (
                mapOf(
                    libraryUri("photos", "a.dat") to "library content a".encodeUtf8(),
                    libraryUri("photos", "b.dat") to "library content b".encodeUtf8()
                )
            )

            library.restoredAttributes.keys shouldBe (
                setOf(libraryUri("photos", "a.dat"), libraryUri("photos", "b.dat"))
            )
        }

        "back up and recover a mix of filesystem and library entities" {
            val directory = Files.createTempDirectory("backup-recovery-mixed")
            directory.toFile().deleteOnExit()

            val fileOne = writeFile(directory, "file-1", "filesystem content one")
            val fileTwo = writeFile(directory, "file-2", "filesystem content two")

            val source = directory.toAbsolutePath().toString()

            val library = MockLibrary(
                scheme = "library",
                entries = mapOf(
                    libraryUri("photos", "a.dat") to "library content a".encodeUtf8(),
                    libraryUri("photos", "b.dat") to "library content b".encodeUtf8(),
                    libraryUri("photos", "skip.dat") to "library content skip".encodeUtf8()
                )
            )

            val backup = runBackup(
                rules = listOf(
                    includeRule(id = 0, source = source, pattern = "file-*"),
                    includeRule(id = 1, source = libraryUri("photos"), pattern = "*"),
                    excludeRule(id = 2, source = libraryUri("photos", "skip.dat"), pattern = "*"),
                    includeRule(id = 3, source = "contacts:/all", pattern = "*")
                ),
                backupKinds = listOf(BackupEntityKind.Filesystem, MockBackupLibraryKind(library))
            )

            backup.tracker.statistics[MockBackupTracker.Statistic.FailureEncountered] shouldBe (1) // unknown scheme: contacts
            backup.core.pushed shouldBe (5) // 2 file crates + 2 library crates + 1 metadata crate

            Files.delete(fileOne)
            Files.delete(fileTwo)

            val recoveryTracker = runRecovery(
                filesystem = FileSystems.getDefault(),
                metadata = backup.metadata,
                core = backup.core,
                recoveryKinds = listOf(RecoveryEntityKind.Filesystem, MockRecoveryLibraryKind(library)),
                sources = setOf(RecoverySourceKind.Filesystem, RecoverySourceKind.Library("library"))
            )

            backup.core.pulled shouldBe (4) // 2 files + 2 library entities
            recoveryTracker.statistics[MockRecoveryTracker.Statistic.FailureEncountered] shouldBe (0)

            String(Files.readAllBytes(fileOne)) shouldBe ("filesystem content one")
            String(Files.readAllBytes(fileTwo)) shouldBe ("filesystem content two")

            library.restored.toMap() shouldBe (
                mapOf(
                    libraryUri("photos", "a.dat") to "library content a".encodeUtf8(),
                    libraryUri("photos", "b.dat") to "library content b".encodeUtf8()
                )
            )
        }

        "recover only the selected sources from a mixed backup" {
            val directory = Files.createTempDirectory("backup-recovery-sources")
            directory.toFile().deleteOnExit()

            val fileOne = writeFile(directory, "file-1", "filesystem content one")
            val fileTwo = writeFile(directory, "file-2", "filesystem content two")

            val source = directory.toAbsolutePath().toString()

            val library = MockLibrary(
                scheme = "library",
                entries = mapOf(
                    libraryUri("photos", "a.dat") to "library content a".encodeUtf8(),
                    libraryUri("photos", "b.dat") to "library content b".encodeUtf8()
                )
            )

            val backup = runBackup(
                rules = listOf(
                    includeRule(id = 0, source = source, pattern = "file-*"),
                    includeRule(id = 1, source = libraryUri("photos"), pattern = "*")
                ),
                backupKinds = listOf(BackupEntityKind.Filesystem, MockBackupLibraryKind(library))
            )

            backup.core.pushed shouldBe (5) // 2 file crates + 2 library crates + 1 metadata crate

            Files.delete(fileOne)
            Files.delete(fileTwo)

            val recoveryTracker = runRecovery(
                filesystem = FileSystems.getDefault(),
                metadata = backup.metadata,
                core = backup.core,
                recoveryKinds = listOf(RecoveryEntityKind.Filesystem, MockRecoveryLibraryKind(library)),
                sources = setOf(RecoverySourceKind.Filesystem)
            )

            backup.core.pulled shouldBe (2) // only the 2 filesystem entities; library not selected
            recoveryTracker.statistics[MockRecoveryTracker.Statistic.FailureEncountered] shouldBe (0)

            String(Files.readAllBytes(fileOne)) shouldBe ("filesystem content one")
            String(Files.readAllBytes(fileTwo)) shouldBe ("filesystem content two")

            library.restored.isEmpty() shouldBe (true)
        }

        "recover only a single selected library source" {
            val directory = Files.createTempDirectory("backup-recovery-lib-only")
            directory.toFile().deleteOnExit()

            val fileOne = writeFile(directory, "file-1", "filesystem content one")

            val source = directory.toAbsolutePath().toString()

            val library = MockLibrary(
                scheme = "library",
                entries = mapOf(
                    libraryUri("photos", "a.dat") to "library content a".encodeUtf8(),
                    libraryUri("photos", "b.dat") to "library content b".encodeUtf8()
                )
            )

            val backup = runBackup(
                rules = listOf(
                    includeRule(id = 0, source = source, pattern = "file-*"),
                    includeRule(id = 1, source = libraryUri("photos"), pattern = "*")
                ),
                backupKinds = listOf(BackupEntityKind.Filesystem, MockBackupLibraryKind(library))
            )

            backup.core.pushed shouldBe (4) // 1 file crate + 2 library crates + 1 metadata crate

            Files.delete(fileOne)

            val recoveryTracker = runRecovery(
                filesystem = FileSystems.getDefault(),
                metadata = backup.metadata,
                core = backup.core,
                recoveryKinds = listOf(RecoveryEntityKind.Filesystem, MockRecoveryLibraryKind(library)),
                sources = setOf(RecoverySourceKind.Library("library"))
            )

            backup.core.pulled shouldBe (2) // only the 2 library entities; filesystem not selected
            recoveryTracker.statistics[MockRecoveryTracker.Statistic.FailureEncountered] shouldBe (0)

            Files.exists(fileOne) shouldBe (false) // filesystem not selected

            library.restored.toMap() shouldBe (
                mapOf(
                    libraryUri("photos", "a.dat") to "library content a".encodeUtf8(),
                    libraryUri("photos", "b.dat") to "library content b".encodeUtf8()
                )
            )
        }

        "recover only the selected library sources when several are chosen" {
            val directory = Files.createTempDirectory("backup-recovery-multi-lib")
            directory.toFile().deleteOnExit()

            val fileOne = writeFile(directory, "file-1", "filesystem content one")

            val source = directory.toAbsolutePath().toString()

            val libraryA = MockLibrary(
                scheme = "lib-a",
                entries = mapOf("lib-a:/test.dat" to "test a".encodeUtf8())
            )

            val libraryB = MockLibrary(
                scheme = "lib-b",
                entries = mapOf("lib-b:/test.dat" to "test b".encodeUtf8())
            )

            val backup = runBackup(
                rules = listOf(
                    includeRule(id = 0, source = source, pattern = "file-*"),
                    includeRule(id = 1, source = "lib-a:/", pattern = "*"),
                    includeRule(id = 2, source = "lib-b:/", pattern = "*")
                ),
                backupKinds = listOf(
                    BackupEntityKind.Filesystem,
                    MockBackupLibraryKind(libraryA),
                    MockBackupLibraryKind(libraryB)
                )
            )

            backup.core.pushed shouldBe (4) // 1 file crate + 2 library crates + 1 metadata crate

            Files.delete(fileOne)

            val recoveryTracker = runRecovery(
                filesystem = FileSystems.getDefault(),
                metadata = backup.metadata,
                core = backup.core,
                recoveryKinds = listOf(
                    RecoveryEntityKind.Filesystem,
                    MockRecoveryLibraryKind(libraryA),
                    MockRecoveryLibraryKind(libraryB)
                ),
                sources = setOf(RecoverySourceKind.Library("lib-a"), RecoverySourceKind.Library("lib-b"))
            )

            backup.core.pulled shouldBe (2) // one entity from each selected library; filesystem not selected
            recoveryTracker.statistics[MockRecoveryTracker.Statistic.FailureEncountered] shouldBe (0)

            Files.exists(fileOne) shouldBe (false) // filesystem not selected

            libraryA.restored.toMap() shouldBe (mapOf("lib-a:/test.dat" to "test a".encodeUtf8()))
            libraryB.restored.toMap() shouldBe (mapOf("lib-b:/test.dat" to "test b".encodeUtf8()))
        }
    }
})

private data class BackupResult(
    val metadata: DatasetMetadata,
    val core: MockMemoryServerCoreEndpointClient,
    val tracker: MockBackupTracker
)
