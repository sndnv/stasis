package stasis.test.client_android.lib.ops.backup

import io.kotest.assertions.fail
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import okio.ByteString
import okio.ByteString.Companion.toByteString
import okio.Source
import stasis.client_android.lib.analysis.Checksum
import stasis.client_android.lib.api.clients.Clients
import stasis.client_android.lib.collection.DefaultBackupCollector
import stasis.client_android.lib.collection.rules.Rule
import stasis.client_android.lib.collection.rules.Specification
import stasis.client_android.lib.compression.Gzip
import stasis.client_android.lib.encryption.Aes
import stasis.client_android.lib.encryption.secrets.DeviceMetadataSecret
import stasis.client_android.lib.encryption.secrets.DeviceSecret
import stasis.client_android.lib.encryption.secrets.Secret
import stasis.client_android.lib.model.DatasetMetadata
import stasis.client_android.lib.model.FilesystemMetadata
import stasis.client_android.lib.model.server.datasets.DatasetDefinition
import stasis.client_android.lib.model.server.datasets.DatasetDefinitionId
import stasis.client_android.lib.model.server.datasets.DatasetEntry
import stasis.client_android.lib.ops.backup.Backup
import stasis.client_android.lib.ops.backup.Providers
import stasis.client_android.lib.staging.DefaultFileStaging
import stasis.client_android.lib.utils.Try
import stasis.test.client_android.lib.Fixtures
import stasis.test.client_android.lib.ResourceHelpers.asTestResource
import stasis.test.client_android.lib.ResourceHelpers.extractDirectoryMetadata
import stasis.test.client_android.lib.ResourceHelpers.extractFileMetadata
import stasis.test.client_android.lib.eventually
import stasis.test.client_android.lib.mocks.MockBackupTracker
import stasis.test.client_android.lib.mocks.MockEncryption
import stasis.test.client_android.lib.mocks.MockServerApiEndpointClient
import stasis.test.client_android.lib.mocks.MockServerCoreEndpointClient
import java.math.BigInteger
import java.nio.file.Paths
import java.time.Instant
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class BackupSpec : WordSpec({
    val operationScope = CoroutineScope(Dispatchers.IO)

    val checksum = Checksum.Companion.SHA256

    val secretsConfig: Secret.Config = Secret.Config(
        derivation = Secret.Config.DerivationConfig(
            encryption = Secret.KeyDerivationConfig(
                secretSize = 64,
                iterations = 100000,
                saltPrefix = "unit-test"
            ),
            authentication = Secret.KeyDerivationConfig(
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

    "A Backup operation" should {
        fun createBackup(
            latestMetadata: DatasetMetadata,
            collector: Backup.Descriptor.Collector,
            clients: Clients,
            tracker: MockBackupTracker
        ): Backup {
            val providers = Providers(
                checksum = checksum,
                staging = DefaultFileStaging(
                    storeDirectory = null,
                    prefix = "staged-",
                    suffix = ".tmp"
                ),
                compressor = Gzip,
                encryptor = Aes,
                decryptor = Aes,
                clients = clients,
                track = tracker
            )

            return Backup(
                descriptor = Backup.Descriptor(
                    targetDataset = Fixtures.Datasets.Default,
                    latestEntry = Fixtures.Entries.Default,
                    latestMetadata = latestMetadata,
                    deviceSecret = secret,
                    collector = collector,
                    limits = Backup.Descriptor.Limits(
                        maxPartSize = 16384
                    )
                ),
                providers = providers
            )
        }

        "process backups for entire configured file collection" {
            val operationCompleted = AtomicBoolean(false)

            val sourceDirectory1Metadata = "/ops".asTestResource().extractDirectoryMetadata()
            val sourceFile1Metadata =
                "/ops/source-file-1".asTestResource().extractFileMetadata(checksum)
            val sourceFile2Metadata =
                "/ops/source-file-2".asTestResource().extractFileMetadata(checksum)
            val sourceFile3Metadata =
                "/ops/source-file-3".asTestResource().extractFileMetadata(checksum)

            val sourceDirectory2Metadata = "/ops/nested".asTestResource().extractDirectoryMetadata()

            val mockApiClient = MockServerApiEndpointClient()
            val mockCoreClient = MockServerCoreEndpointClient()
            val mockTracker = MockBackupTracker()

            val backup = createBackup(
                collector = Backup.Descriptor.Collector.WithRules(
                    spec = Specification(
                        rules = listOf(
                            Rule(
                                id = 1,
                                operation = Rule.Operation.Include,
                                directory = sourceDirectory1Metadata.path.toAbsolutePath()
                                    .toString(),
                                pattern = "source-file-*"
                            ),
                            Rule(
                                id = 2,
                                operation = Rule.Operation.Include,
                                directory = sourceDirectory2Metadata.path.toAbsolutePath()
                                    .toString(),
                                pattern = "source-file-*",
                            )
                        )
                    )
                ),
                latestMetadata = DatasetMetadata(
                    contentChanged = mapOf(
                        sourceFile1Metadata.path to sourceFile1Metadata.copy(isHidden = true),
                        sourceFile2Metadata.path to sourceFile2Metadata,
                        sourceFile3Metadata.path to sourceFile3Metadata.copy(checksum = BigInteger("0"))
                    ),
                    metadataChanged = mapOf(
                        sourceDirectory1Metadata.path to sourceDirectory1Metadata
                    ),
                    filesystem = FilesystemMetadata(
                        entities = mapOf(
                            sourceDirectory1Metadata.path to FilesystemMetadata.EntityState.New,
                            sourceFile1Metadata.path to FilesystemMetadata.EntityState.New,
                            sourceFile2Metadata.path to FilesystemMetadata.EntityState.New,
                            sourceFile3Metadata.path to FilesystemMetadata.EntityState.New
                        )
                    )
                ),
                clients = Clients(
                    api = mockApiClient,
                    core = mockCoreClient
                ),
                tracker = mockTracker
            )

            backup.start(withScope = operationScope) {
                operationCompleted.set(true)
            }

            eventually {
                operationCompleted.get() shouldBe (true)
            }

            // dataset entry for backup created; metadata crate pushed
            // /ops directory is unchanged
            // /ops/source-file-1 has metadata changes only; /ops/source-file-2 is unchanged; crate for /ops/source-file-3 pushed;
            // /ops/nested directory is new
            // /ops/nested/source-file-4 and /ops/nested/source-file-5 are new
            mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved] shouldBe (0)
            mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest] shouldBe (0)
            mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryCreated] shouldBe (1)
            mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved] shouldBe (0)
            mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionCreated] shouldBe (0)
            mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved] shouldBe (0)
            mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved] shouldBe (0)
            mockApiClient.statistics[MockServerApiEndpointClient.Statistic.PublicSchedulesRetrieved] shouldBe (0)
            mockApiClient.statistics[MockServerApiEndpointClient.Statistic.PublicScheduleRetrieved] shouldBe (0)
            mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved] shouldBe (0)
            mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved] shouldBe (0)
            mockApiClient.statistics[MockServerApiEndpointClient.Statistic.UserRetrieved] shouldBe (0)
            mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DeviceRetrieved] shouldBe (0)
            mockApiClient.statistics[MockServerApiEndpointClient.Statistic.Ping] shouldBe (0)

            mockCoreClient.statistics[MockServerCoreEndpointClient.Statistic.CratePulled] shouldBe (0)
            mockCoreClient.statistics[MockServerCoreEndpointClient.Statistic.CratePushed] shouldBe (4)

            mockTracker.statistics[MockBackupTracker.Statistic.SpecificationProcessed] shouldBe (1)
            mockTracker.statistics[MockBackupTracker.Statistic.EntityExamined] shouldBe (7) // 2 directories + 5 files
            mockTracker.statistics[MockBackupTracker.Statistic.EntityCollected] shouldBe (5) // 2 unchanged + 5 changed entities
            mockTracker.statistics[MockBackupTracker.Statistic.EntityProcessed] shouldBe (5) // 2 unchanged + 5 changed entities
            mockTracker.statistics[MockBackupTracker.Statistic.MetadataCollected] shouldBe (1)
            mockTracker.statistics[MockBackupTracker.Statistic.MetadataPushed] shouldBe (1)
            mockTracker.statistics[MockBackupTracker.Statistic.FailureEncountered] shouldBe (0)
            mockTracker.statistics[MockBackupTracker.Statistic.Completed] shouldBe (1)
        }

        "process backups for specific files" {
            val operationCompleted = AtomicBoolean(false)

            val sourceFile1Metadata =
                "/ops/source-file-1".asTestResource().extractFileMetadata(checksum)
            val sourceFile2Metadata =
                "/ops/source-file-2".asTestResource().extractFileMetadata(checksum)
            val sourceFile3Metadata =
                "/ops/source-file-3".asTestResource().extractFileMetadata(checksum)

            val mockApiClient = MockServerApiEndpointClient()
            val mockCoreClient = MockServerCoreEndpointClient()
            val mockTracker = MockBackupTracker()

            val backup = createBackup(
                collector = Backup.Descriptor.Collector.WithEntities(
                    entities = listOf(
                        sourceFile1Metadata.path,
                        sourceFile2Metadata.path,
                        sourceFile3Metadata.path
                    )
                ),
                latestMetadata = DatasetMetadata(
                    contentChanged = mapOf(
                        sourceFile1Metadata.path to sourceFile1Metadata.copy(isHidden = true),
                        sourceFile2Metadata.path to sourceFile2Metadata,
                        sourceFile3Metadata.path to sourceFile3Metadata.copy(checksum = BigInteger("0"))
                    ),
                    metadataChanged = emptyMap(),
                    filesystem = FilesystemMetadata(
                        entities = mapOf(
                            sourceFile1Metadata.path to FilesystemMetadata.EntityState.New,
                            sourceFile2Metadata.path to FilesystemMetadata.EntityState.New,
                            sourceFile3Metadata.path to FilesystemMetadata.EntityState.New
                        )
                    )
                ),
                clients = Clients(
                    api = mockApiClient,
                    core = mockCoreClient
                ),
                tracker = mockTracker
            )

            backup.start(withScope = operationScope) {
                operationCompleted.set(true)
            }

            eventually {
                operationCompleted.get() shouldBe (true)
            }

            // dataset entry for backup created; metadata crate pushed
            // source-file-1 has metadata changes only; source-file-2 is unchanged; crate for source-file-3 pushed;
            mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved] shouldBe (0)
            mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest] shouldBe (0)
            mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryCreated] shouldBe (1)
            mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved] shouldBe (0)
            mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionCreated] shouldBe (0)
            mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved] shouldBe (0)
            mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved] shouldBe (0)
            mockApiClient.statistics[MockServerApiEndpointClient.Statistic.PublicSchedulesRetrieved] shouldBe (0)
            mockApiClient.statistics[MockServerApiEndpointClient.Statistic.PublicScheduleRetrieved] shouldBe (0)
            mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved] shouldBe (0)
            mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved] shouldBe (0)
            mockApiClient.statistics[MockServerApiEndpointClient.Statistic.UserRetrieved] shouldBe (0)
            mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DeviceRetrieved] shouldBe (0)
            mockApiClient.statistics[MockServerApiEndpointClient.Statistic.Ping] shouldBe (0)

            mockCoreClient.statistics[MockServerCoreEndpointClient.Statistic.CratePulled] shouldBe (0)
            mockCoreClient.statistics[MockServerCoreEndpointClient.Statistic.CratePushed] shouldBe (2)

            mockTracker.statistics[MockBackupTracker.Statistic.SpecificationProcessed] shouldBe (0)
            mockTracker.statistics[MockBackupTracker.Statistic.EntityExamined] shouldBe (3)
            mockTracker.statistics[MockBackupTracker.Statistic.EntityCollected] shouldBe (2)
            mockTracker.statistics[MockBackupTracker.Statistic.EntityProcessed] shouldBe (2)
            mockTracker.statistics[MockBackupTracker.Statistic.MetadataCollected] shouldBe (1)
            mockTracker.statistics[MockBackupTracker.Statistic.MetadataPushed] shouldBe (1)
            mockTracker.statistics[MockBackupTracker.Statistic.FailureEncountered] shouldBe (0)
            mockTracker.statistics[MockBackupTracker.Statistic.Completed] shouldBe (1)
        }

        "handle failures of backups for individual files" {
            val operationResult = AtomicReference<Throwable>()

            val sourceFile1Metadata =
                "/ops/source-file-1".asTestResource().extractFileMetadata(checksum)
            val sourceFile2Metadata =
                "/ops/source-file-2".asTestResource().extractFileMetadata(checksum)

            val mockApiClient = MockServerApiEndpointClient()
            val mockCoreClient = MockServerCoreEndpointClient()
            val mockTracker = MockBackupTracker()

            val backup = createBackup(
                collector = Backup.Descriptor.Collector.WithEntities(
                    entities = listOf(
                        sourceFile1Metadata.path,
                        sourceFile2Metadata.path,
                        Paths.get("/ops/invalid-file")
                    )
                ),
                latestMetadata = DatasetMetadata(
                    contentChanged = mapOf(
                        sourceFile1Metadata.path to sourceFile1Metadata.copy(isHidden = true),
                        sourceFile2Metadata.path to sourceFile2Metadata
                    ),
                    metadataChanged = emptyMap(),
                    filesystem = FilesystemMetadata(
                        entities = mapOf(
                            sourceFile1Metadata.path to FilesystemMetadata.EntityState.New,
                            sourceFile2Metadata.path to FilesystemMetadata.EntityState.New
                        )
                    )
                ),
                clients = Clients(
                    api = mockApiClient,
                    core = mockCoreClient
                ),
                tracker = mockTracker
            )

            backup.start(withScope = operationScope) { e ->
                operationResult.set(e)
            }

            eventually {
                operationResult.get()?.message shouldBe ("/ops/invalid-file")
            }

            // dataset entry for backup created; metadata crate pushed
            // source-file-1 has metadata changes only; source-file-2 is unchanged; third file is invalid/missing;
            mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved] shouldBe (0)
            mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest] shouldBe (0)
            mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryCreated] shouldBe (1)
            mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved] shouldBe (0)
            mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionCreated] shouldBe (0)
            mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved] shouldBe (0)
            mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved] shouldBe (0)
            mockApiClient.statistics[MockServerApiEndpointClient.Statistic.PublicSchedulesRetrieved] shouldBe (0)
            mockApiClient.statistics[MockServerApiEndpointClient.Statistic.PublicScheduleRetrieved] shouldBe (0)
            mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved] shouldBe (0)
            mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved] shouldBe (0)
            mockApiClient.statistics[MockServerApiEndpointClient.Statistic.UserRetrieved] shouldBe (0)
            mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DeviceRetrieved] shouldBe (0)
            mockApiClient.statistics[MockServerApiEndpointClient.Statistic.Ping] shouldBe (0)

            mockCoreClient.statistics[MockServerCoreEndpointClient.Statistic.CratePulled] shouldBe (0)
            mockCoreClient.statistics[MockServerCoreEndpointClient.Statistic.CratePushed] shouldBe (1)

            mockTracker.statistics[MockBackupTracker.Statistic.SpecificationProcessed] shouldBe (0)
            mockTracker.statistics[MockBackupTracker.Statistic.EntityExamined] shouldBe (2)
            mockTracker.statistics[MockBackupTracker.Statistic.EntityCollected] shouldBe (1)
            mockTracker.statistics[MockBackupTracker.Statistic.EntityProcessed] shouldBe (1)
            mockTracker.statistics[MockBackupTracker.Statistic.MetadataCollected] shouldBe (1)
            mockTracker.statistics[MockBackupTracker.Statistic.MetadataPushed] shouldBe (1)
            mockTracker.statistics[MockBackupTracker.Statistic.FailureEncountered] shouldBe (1)
            mockTracker.statistics[MockBackupTracker.Statistic.Completed] shouldBe (1)
        }

        "allow stopping a running backup" {
            val operationCompleted = AtomicBoolean(false)

            val sourceFile1Metadata =
                "/ops/source-file-1".asTestResource().extractFileMetadata(checksum)

            val mockTracker = MockBackupTracker()

            eventually {
                val backup = createBackup(
                    collector = Backup.Descriptor.Collector.WithEntities(
                        entities = listOf(sourceFile1Metadata.path)
                    ),
                    latestMetadata = DatasetMetadata.empty(),
                    clients = Clients(
                        api = MockServerApiEndpointClient(),
                        core = MockServerCoreEndpointClient()
                    ),
                    tracker = mockTracker
                )

                backup.start(withScope = operationScope) {
                    operationCompleted.set(it != null)
                }

                backup.stop()

                operationCompleted.get() shouldBe (true)
            }
        }
    }

    "A Backup Descriptor" should {
        "be creatable from a collector descriptor" {
            val datasetWithEntry = Fixtures.Datasets.Default
            val datasetWithoutEntry = Fixtures.Datasets.Default.copy(id = UUID.randomUUID())

            val entry = Fixtures.Entries.Default.copy(definition = datasetWithEntry.id)
            val metadata = DatasetMetadata.empty()

            val providers = Providers(
                checksum = checksum,
                staging = DefaultFileStaging(
                    storeDirectory = null,
                    prefix = "staged-",
                    suffix = ".tmp"
                ),
                compressor = Gzip,
                encryptor = MockEncryption(),
                decryptor = object : MockEncryption() {
                    override fun decrypt(
                        source: Source,
                        metadataSecret: DeviceMetadataSecret
                    ): Source =
                        source
                },
                clients = Clients(
                    api = object : MockServerApiEndpointClient(self = UUID.randomUUID()) {
                        override suspend fun datasetDefinition(definition: DatasetDefinitionId): Try<DatasetDefinition> =
                            Try {
                                when (definition) {
                                    datasetWithEntry.id -> datasetWithEntry
                                    datasetWithoutEntry.id -> datasetWithoutEntry
                                    else -> fail("Unexpected definition requested: [$definition]")
                                }
                            }

                        override suspend fun latestEntry(
                            definition: DatasetDefinitionId,
                            until: Instant?
                        ): Try<DatasetEntry?> = Try {
                            if (definition == datasetWithEntry.id) {
                                entry
                            } else {
                                null
                            }
                        }
                    },
                    core = MockServerCoreEndpointClient(
                        self = UUID.randomUUID(),
                        crates = mapOf(entry.metadata to ByteString.EMPTY)
                    )
                ),
                track = MockBackupTracker()
            )

            val collectorDescriptor =
                Backup.Descriptor.Collector.WithEntities(entities = emptyList())

            val limits = Backup.Descriptor.Limits(
                maxPartSize = 16384
            )

            val descriptorWithLatestEntry = Backup.Descriptor(
                definition = datasetWithEntry.id,
                collector = collectorDescriptor,
                deviceSecret = secret,
                limits = limits,
                providers = providers
            ).get()

            val descriptorWithoutLatestEntry = Backup.Descriptor(
                definition = datasetWithoutEntry.id,
                collector = collectorDescriptor,
                deviceSecret = secret,
                limits = limits,
                providers = providers
            ).get()

            descriptorWithLatestEntry.targetDataset shouldBe (datasetWithEntry)
            descriptorWithLatestEntry.latestEntry shouldBe (entry)
            descriptorWithLatestEntry.latestMetadata shouldBe (metadata)
            descriptorWithLatestEntry.deviceSecret shouldBe (secret)
            descriptorWithLatestEntry.collector shouldBe (collectorDescriptor)
            descriptorWithLatestEntry.limits shouldBe (limits)

            descriptorWithoutLatestEntry.targetDataset shouldBe (datasetWithoutEntry)
            descriptorWithoutLatestEntry.latestEntry shouldBe (null)
            descriptorWithoutLatestEntry.latestMetadata shouldBe (null)
            descriptorWithoutLatestEntry.deviceSecret shouldBe (secret)
            descriptorWithoutLatestEntry.collector shouldBe (collectorDescriptor)
            descriptorWithoutLatestEntry.limits shouldBe (limits)
        }

        "be convertible to a backup collector" {
            val providers = Providers(
                checksum = checksum,
                staging = DefaultFileStaging(
                    storeDirectory = null,
                    prefix = "staged-",
                    suffix = ".tmp"
                ),
                compressor = Gzip,
                encryptor = MockEncryption(),
                decryptor = MockEncryption(),
                clients = Clients(
                    api = MockServerApiEndpointClient(),
                    core = MockServerCoreEndpointClient()
                ),
                track = MockBackupTracker()
            )

            val descriptorWithFiles = Backup.Descriptor(
                targetDataset = Fixtures.Datasets.Default,
                latestEntry = null,
                latestMetadata = null,
                deviceSecret = secret,
                collector = Backup.Descriptor.Collector.WithEntities(entities = emptyList()),
                limits = Backup.Descriptor.Limits(
                    maxPartSize = 16384
                )
            )

            val descriptorWithRules = Backup
                .Descriptor(
                    targetDataset = Fixtures.Datasets.Default,
                    latestEntry = null,
                    latestMetadata = null,
                    deviceSecret = secret,
                    collector = Backup.Descriptor.Collector.WithRules(spec = Specification.empty()),
                    limits = Backup.Descriptor.Limits(
                        maxPartSize = 16384
                    )
                )



            descriptorWithFiles.toBackupCollector(providers)
                .shouldBeInstanceOf<DefaultBackupCollector>()
            descriptorWithRules.toBackupCollector(providers)
                .shouldBeInstanceOf<DefaultBackupCollector>()
        }
    }
})
