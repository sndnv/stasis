package stasis.test.client_android.lib.ops.recovery

import io.kotest.assertions.fail
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString
import okio.buffer
import stasis.client_android.lib.analysis.Checksum
import stasis.client_android.lib.api.clients.Clients
import stasis.client_android.lib.collection.rules.Rule
import stasis.client_android.lib.compression.Compression
import stasis.client_android.lib.encryption.Aes
import stasis.client_android.lib.encryption.secrets.DeviceSecret
import stasis.client_android.lib.encryption.secrets.Secret
import stasis.client_android.lib.model.DatasetMetadata
import stasis.client_android.lib.model.EntityMetadata
import stasis.client_android.lib.model.server.api.requests.CreateDatasetEntry
import stasis.client_android.lib.ops.backup.Backup
import stasis.client_android.lib.ops.backup.BackupEntityKind
import stasis.client_android.lib.ops.backup.Providers as BackupProviders
import stasis.client_android.lib.ops.recovery.EntityContent
import stasis.client_android.lib.staging.DefaultFileStaging
import stasis.client_android.lib.telemetry.analytics.AnalyticsCollector
import stasis.test.client_android.lib.Fixtures
import stasis.test.client_android.lib.eventually
import stasis.test.client_android.lib.mocks.MockBackupTracker
import stasis.test.client_android.lib.mocks.MockServerApiEndpointClient
import stasis.test.client_android.lib.ops.integration.mocks.MockBackupLibraryKind
import stasis.test.client_android.lib.ops.integration.mocks.MockLibrary
import stasis.test.client_android.lib.ops.integration.mocks.MockMemoryServerCoreEndpointClient
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class EntityContentSpec : WordSpec({
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

    suspend fun runBackup(
        rules: List<Rule>,
        backupKinds: List<BackupEntityKind>
    ): Pair<DatasetMetadata, MockMemoryServerCoreEndpointClient> {
        val core = MockMemoryServerCoreEndpointClient()
        val api = MockServerApiEndpointClient()

        val providers = BackupProviders(
            checksum = checksum,
            staging = DefaultFileStaging(storeDirectory = null, prefix = "staged-", suffix = ".tmp"),
            compression = compression,
            encryptor = Aes,
            decryptor = Aes,
            clients = Clients(api = api, core = core),
            track = MockBackupTracker(),
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

        return metadata to core
    }

    "EntityContent" should {
        "pull, decrypt and decompress a filesystem entity" {
            val directory = Files.createTempDirectory("entity-content-fs")
            directory.toFile().deleteOnExit()

            val file = directory.resolve("file-1")
            Files.write(file, "filesystem content one".toByteArray())

            val source = directory.toAbsolutePath().toString()
            val key = file.toAbsolutePath().toString()

            val (metadata, core) = runBackup(
                rules = listOf(includeRule(id = 0, source = source, pattern = "file-*")),
                backupKinds = listOf(BackupEntityKind.Filesystem)
            )

            val clients = Clients(api = MockServerApiEndpointClient(), core = core)
            val entityMetadata = metadata.require(entity = key, clients = clients) as EntityMetadata.WithContent
            val partsProcessed = AtomicInteger(0)

            val content = EntityContent.pull(
                metadata = entityMetadata,
                entityKey = key,
                deviceSecret = secret,
                clients = clients,
                decryptor = Aes,
                onPartProcessed = { partsProcessed.incrementAndGet() }
            ).buffer().use { it.readUtf8() }

            content shouldBe ("filesystem content one")
            partsProcessed.get() shouldBe (entityMetadata.crates.size)
        }

        "pull, decrypt and decompress a library entity" {
            val key = "library:/photos/a.dat"

            val library = MockLibrary(
                scheme = "library",
                entries = mapOf(key to "library content a".encodeUtf8())
            )

            val (metadata, core) = runBackup(
                rules = listOf(includeRule(id = 0, source = "library:/photos", pattern = "*")),
                backupKinds = listOf(BackupEntityKind.Filesystem, MockBackupLibraryKind(library))
            )

            val clients = Clients(api = MockServerApiEndpointClient(), core = core)
            val entityMetadata = metadata.require(entity = key, clients = clients) as EntityMetadata.WithContent

            val content = EntityContent.pull(
                metadata = entityMetadata,
                entityKey = key,
                deviceSecret = secret,
                clients = clients,
                decryptor = Aes,
                onPartProcessed = { }
            ).buffer().use { it.readUtf8() }

            content shouldBe ("library content a")
        }
    }
})
