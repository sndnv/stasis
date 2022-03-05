package stasis.test.client_android.lib.ops.recovery

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import okio.ByteString
import okio.ByteString.Companion.toByteString
import okio.Source
import stasis.client_android.lib.analysis.Checksum
import stasis.client_android.lib.api.clients.Clients
import stasis.client_android.lib.collection.DefaultRecoveryCollector
import stasis.client_android.lib.encryption.secrets.DeviceMetadataSecret
import stasis.client_android.lib.encryption.secrets.DeviceSecret
import stasis.client_android.lib.encryption.secrets.Secret
import stasis.client_android.lib.model.DatasetMetadata
import stasis.client_android.lib.model.FilesystemMetadata
import stasis.client_android.lib.model.TargetEntity
import stasis.client_android.lib.model.server.datasets.DatasetDefinitionId
import stasis.client_android.lib.model.server.datasets.DatasetEntry
import stasis.client_android.lib.model.server.datasets.DatasetEntryId
import stasis.client_android.lib.ops.recovery.Providers
import stasis.client_android.lib.ops.recovery.Recovery
import stasis.client_android.lib.ops.recovery.Recovery.Destination.Companion.toTargetEntityDestination
import stasis.client_android.lib.staging.DefaultFileStaging
import stasis.client_android.lib.utils.Try
import stasis.client_android.lib.utils.Try.Success
import stasis.client_android.lib.utils.Try.Failure
import stasis.test.client_android.lib.Fixtures
import stasis.test.client_android.lib.ResourceHelpers
import stasis.test.client_android.lib.ResourceHelpers.asTestResource
import stasis.test.client_android.lib.ResourceHelpers.clear
import stasis.test.client_android.lib.ResourceHelpers.createMockFileSystem
import stasis.test.client_android.lib.ResourceHelpers.extractDirectoryMetadata
import stasis.test.client_android.lib.ResourceHelpers.extractFileMetadata
import stasis.test.client_android.lib.ResourceHelpers.withRootAt
import stasis.test.client_android.lib.eventually
import stasis.test.client_android.lib.mocks.*
import java.math.BigInteger
import java.nio.file.Paths
import java.time.Instant
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class RecoverySpec : WordSpec({
    val operationScope = CoroutineScope(Dispatchers.IO)

    val checksum: Checksum = Checksum.Companion.SHA256

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

    val matchingPath = Paths.get("/tmp/a/b/c/test-file.json")

    val matchingFileNameRegexes = listOf(
        """test-file""",
        """test-file\.json""",
        """.*"""
    )

    val matchingPathRegexes = listOf(
        """/tmp/.*""",
        """/.*/a/.*/c"""
    )

    val nonMatchingPathRegexes = listOf(
        """tmp""",
        """/tmp$""",
        """^/a/b/c.*"""
    )

    "A Recovery operation" should {
        fun createRecovery(
            metadata: DatasetMetadata,
            clients: Clients,
            tracker: MockRecoveryTracker,
            destination: Recovery.Destination? = null
        ): Recovery {
            val providers = Providers(
                checksum = checksum,
                staging = DefaultFileStaging(
                    storeDirectory = null,
                    prefix = "staged-",
                    suffix = ".tmp"
                ),
                decompressor = MockCompression(),
                decryptor = MockEncryption(),
                clients = clients,
                track = tracker
            )

            return Recovery(
                descriptor = Recovery.Descriptor(
                    targetMetadata = metadata,
                    query = null,
                    destination = destination,
                    deviceSecret = secret
                ),
                providers = providers
            )
        }

        "process recovery of files" {
            val operationCompleted = AtomicBoolean(false)

            val currentSourceFile1Metadata =
                "/ops/source-file-1".asTestResource().extractFileMetadata(checksum)
            val currentSourceFile2Metadata =
                "/ops/source-file-2".asTestResource().extractFileMetadata(checksum)
            val currentSourceFile3Metadata =
                "/ops/source-file-3".asTestResource().extractFileMetadata(checksum)

            // metadata represents file state during previous backup
            val originalSourceFile1Metadata = currentSourceFile1Metadata.copy(
                checksum = BigInteger("0")
            ) // file changed since backup

            val originalSourceFile2Metadata =
                currentSourceFile2Metadata // file not changed since backup
            val originalSourceFile3Metadata =
                currentSourceFile3Metadata.copy(isHidden = true) // file changed since backup

            val originalMetadata = DatasetMetadata(
                contentChanged = mapOf(
                    originalSourceFile1Metadata.path to originalSourceFile1Metadata,
                    originalSourceFile2Metadata.path to originalSourceFile2Metadata,
                    originalSourceFile3Metadata.path to originalSourceFile3Metadata
                ),
                metadataChanged = emptyMap(),
                filesystem = FilesystemMetadata(
                    changes = listOf(
                        originalSourceFile1Metadata.path,
                        originalSourceFile2Metadata.path,
                        originalSourceFile3Metadata.path
                    )
                )
            )

            val mockApiClient = MockServerApiEndpointClient()

            val mockCoreClient = MockServerCoreEndpointClient(
                self = UUID.randomUUID(),
                crates = (originalSourceFile1Metadata.crates
                        + originalSourceFile2Metadata.crates
                        + originalSourceFile3Metadata.crates)
                    .values
                    .map { Pair(it, "dummy-encrypted-data".toByteArray().toByteString()) }
                    .toMap()
            )
            val mockTracker = MockRecoveryTracker()

            val recovery = createRecovery(
                metadata = originalMetadata,
                clients = Clients(api = mockApiClient, core = mockCoreClient),
                tracker = mockTracker
            )

            recovery.start(withScope = operationScope) {
                operationCompleted.set(true)
            }

            eventually {
                operationCompleted.get() shouldBe (true)
            }

            // data pulled for source-file-1; source-file-2 is unchanged; source-file-3 has only metadata changes;
            mockCoreClient.statistics[MockServerCoreEndpointClient.Statistic.CratePulled] shouldBe (1)
            mockCoreClient.statistics[MockServerCoreEndpointClient.Statistic.CratePushed] shouldBe (0)

            mockTracker.statistics[MockRecoveryTracker.Statistic.EntityExamined] shouldBe (3)
            mockTracker.statistics[MockRecoveryTracker.Statistic.EntityCollected] shouldBe (2)
            mockTracker.statistics[MockRecoveryTracker.Statistic.EntityProcessed] shouldBe (2)
            mockTracker.statistics[MockRecoveryTracker.Statistic.MetadataApplied] shouldBe (2)
            mockTracker.statistics[MockRecoveryTracker.Statistic.FailureEncountered] shouldBe (0)
            mockTracker.statistics[MockRecoveryTracker.Statistic.Completed] shouldBe (1)
        }

        "support recovering to a different destination" {
            val operationCompleted = AtomicBoolean(false)

            val targetDirectory = "/ops/recovery".asTestResource()
            targetDirectory.clear()

            val sourceDirectory1Metadata =
                "/ops".asTestResource().extractDirectoryMetadata().withRootAt("/ops")
            val sourceDirectory2Metadata =
                "/ops/nested".asTestResource().extractDirectoryMetadata().withRootAt("/ops")

            val sourceFile1Metadata =
                "/ops/source-file-1".asTestResource().extractFileMetadata(checksum)
                    .withRootAt("/ops")
            val sourceFile2Metadata =
                "/ops/source-file-2".asTestResource().extractFileMetadata(checksum)
                    .withRootAt("/ops")
            val sourceFile3Metadata =
                "/ops/source-file-3".asTestResource().extractFileMetadata(checksum)
                    .withRootAt("/ops")
            val sourceFile4Metadata =
                "/ops/nested/source-file-4".asTestResource().extractFileMetadata(checksum)
                    .withRootAt("/ops")
            val sourceFile5Metadata =
                "/ops/nested/source-file-5".asTestResource().extractFileMetadata(checksum)
                    .withRootAt("/ops")

            val metadata = DatasetMetadata(
                contentChanged = mapOf(
                    sourceFile1Metadata.path to sourceFile1Metadata,
                    sourceFile2Metadata.path to sourceFile2Metadata,
                    sourceFile3Metadata.path to sourceFile3Metadata,
                    sourceFile4Metadata.path to sourceFile4Metadata,
                    sourceFile5Metadata.path to sourceFile5Metadata
                ),
                metadataChanged = mapOf(
                    sourceDirectory1Metadata.path to sourceDirectory1Metadata,
                    sourceDirectory2Metadata.path to sourceDirectory2Metadata
                ),
                filesystem = FilesystemMetadata(
                    changes = listOf(
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

            val mockCoreClient = MockServerCoreEndpointClient(
                self = UUID.randomUUID(),
                crates = (
                        sourceFile1Metadata.crates
                                + sourceFile2Metadata.crates
                                + sourceFile3Metadata.crates
                                + sourceFile4Metadata.crates
                                + sourceFile5Metadata.crates
                        ).values
                    .map { Pair(it, "dummy-encrypted-data".toByteArray().toByteString()) }
                    .toMap()
            )

            val mockTracker = MockRecoveryTracker()

            val destination = Recovery.Destination(
                path = targetDirectory.toAbsolutePath().toString(),
                keepStructure = true
            )

            val recovery = createRecovery(
                metadata = metadata,
                clients = Clients(api = mockApiClient, core = mockCoreClient),
                tracker = mockTracker,
                destination = destination
            )

            recovery.start(withScope = operationScope) {
                operationCompleted.set(true)
            }

            eventually {
                operationCompleted.get() shouldBe (true)
            }

            // data pulled for all entities; 2 directories and 5 files
            mockCoreClient.statistics[MockServerCoreEndpointClient.Statistic.CratePulled] shouldBe (5)
            mockCoreClient.statistics[MockServerCoreEndpointClient.Statistic.CratePushed] shouldBe (0)

            mockTracker.statistics[MockRecoveryTracker.Statistic.EntityExamined] shouldBe (7)
            mockTracker.statistics[MockRecoveryTracker.Statistic.EntityCollected] shouldBe (7)
            mockTracker.statistics[MockRecoveryTracker.Statistic.EntityProcessed] shouldBe (7)
            mockTracker.statistics[MockRecoveryTracker.Statistic.MetadataApplied] shouldBe (7)
            mockTracker.statistics[MockRecoveryTracker.Statistic.FailureEncountered] shouldBe (0)
            mockTracker.statistics[MockRecoveryTracker.Statistic.Completed] shouldBe (1)
        }

        "handle failures of specific files" {
            val operationResult = AtomicReference<Throwable>()

            val currentSourceFile1Metadata =
                "/ops/source-file-1".asTestResource().extractFileMetadata(checksum)
            val currentSourceFile2Metadata =
                "/ops/source-file-2".asTestResource().extractFileMetadata(checksum)
            val currentSourceFile3Metadata =
                "/ops/source-file-3".asTestResource().extractFileMetadata(checksum)

            // metadata represents file state during previous backup
            val originalSourceFile1Metadata =
                currentSourceFile1Metadata.copy(checksum = BigInteger("0")) // file changed since backup
            val originalSourceFile2Metadata =
                currentSourceFile2Metadata.copy(checksum = BigInteger("0")) // file changed since backup
            val originalSourceFile3Metadata =
                currentSourceFile3Metadata.copy(checksum = BigInteger("0")) // file changed since backup

            val originalMetadata = DatasetMetadata(
                contentChanged = mapOf(
                    originalSourceFile1Metadata.path to originalSourceFile1Metadata,
                    originalSourceFile2Metadata.path to originalSourceFile2Metadata,
                    originalSourceFile3Metadata.path to originalSourceFile3Metadata
                ),
                metadataChanged = emptyMap(),
                filesystem = FilesystemMetadata(
                    changes = listOf(
                        currentSourceFile1Metadata.path,
                        currentSourceFile2Metadata.path,
                        currentSourceFile3Metadata.path
                    )
                )
            )

            val mockApiClient = MockServerApiEndpointClient()

            val mockCoreClient = MockServerCoreEndpointClient(
                self = UUID.randomUUID(),
                crates = (originalSourceFile1Metadata.crates + originalSourceFile2Metadata.crates).values
                    .map { Pair(it, "dummy-encrypted-data".toByteArray().toByteString()) }
                    .toMap()
            )
            val mockTracker = MockRecoveryTracker()

            val recovery = createRecovery(
                metadata = originalMetadata,
                clients = Clients(api = mockApiClient, core = mockCoreClient),
                tracker = mockTracker
            )

            recovery.start(withScope = operationScope) { e ->
                operationResult.set(e)
            }

            eventually {
                operationResult.get()?.message shouldContain ("Failed to pull crate")
            }

            // data pulled for source-file-1, source-file-2; source-file-3 has not data and will fail
            mockCoreClient.statistics[MockServerCoreEndpointClient.Statistic.CratePulled] shouldBe (2)
            mockCoreClient.statistics[MockServerCoreEndpointClient.Statistic.CratePushed] shouldBe (0)

            mockTracker.statistics[MockRecoveryTracker.Statistic.EntityExamined] shouldBe (3)
            mockTracker.statistics[MockRecoveryTracker.Statistic.EntityCollected] shouldBe (3)
            mockTracker.statistics[MockRecoveryTracker.Statistic.EntityProcessed] shouldBe (2)
            mockTracker.statistics[MockRecoveryTracker.Statistic.MetadataApplied] shouldBe (2)
            mockTracker.statistics[MockRecoveryTracker.Statistic.FailureEncountered] shouldBe (1)
            mockTracker.statistics[MockRecoveryTracker.Statistic.Completed] shouldBe (1)
        }

        "allow stopping a running recovery" {
            val operationCompleted = AtomicBoolean(false)

            val currentSourceFile1Metadata =
                "/ops/source-file-1".asTestResource().extractFileMetadata(checksum)

            // metadata represents file state during previous backup
            val originalSourceFile1Metadata =
                currentSourceFile1Metadata.copy(checksum = BigInteger("0")) // file changed since backup

            val originalMetadata = DatasetMetadata(
                contentChanged = mapOf(originalSourceFile1Metadata.path to originalSourceFile1Metadata),
                metadataChanged = emptyMap(),
                filesystem = FilesystemMetadata(changes = listOf(originalSourceFile1Metadata.path))
            )

            val mockApiClient = MockServerApiEndpointClient()

            val mockCoreClient = MockServerCoreEndpointClient(
                self = UUID.randomUUID(),
                crates = originalSourceFile1Metadata.crates.values.map {
                    Pair(it, "dummy-encrypted-data".toByteArray().toByteString())
                }
                    .toMap()
            )
            val mockTracker = MockRecoveryTracker()

            val recovery = createRecovery(
                metadata = originalMetadata,
                clients = Clients(api = mockApiClient, core = mockCoreClient),
                tracker = mockTracker
            )

            recovery.start(withScope = operationScope) {
                operationCompleted.set(it != null)
            }

            recovery.stop()

            eventually {
                operationCompleted.get() shouldBe (true)
            }

            mockTracker.statistics[MockRecoveryTracker.Statistic.Completed] shouldBe (0)
        }
    }

    "A Recovery Descriptor" should {
        "be creatable from a collector descriptor" {
            val dataset = Fixtures.Datasets.Default
            val actualEntry = Fixtures.Entries.Default.copy(definition = dataset.id)
            val metadata = DatasetMetadata.empty()

            val providers = Providers(
                checksum = checksum,
                staging = DefaultFileStaging(
                    storeDirectory = null,
                    prefix = "staged-",
                    suffix = ".tmp"
                ),
                decompressor = MockCompression(),
                decryptor = object : MockEncryption() {
                    override fun decrypt(
                        source: Source,
                        metadataSecret: DeviceMetadataSecret
                    ): Source =
                        source
                },
                clients = Clients(
                    api = object : MockServerApiEndpointClient(self = UUID.randomUUID()) {
                        override suspend fun latestEntry(
                            definition: DatasetDefinitionId,
                            until: Instant?
                        ): Try<DatasetEntry?> = Try {
                            if (definition == dataset.id) {
                                actualEntry
                            } else {
                                null
                            }
                        }

                        override suspend fun datasetEntry(entry: DatasetEntryId): Try<DatasetEntry> =
                            if (entry == actualEntry.id) {
                                Success(actualEntry)
                            } else {
                                Failure(IllegalArgumentException("Invalid entry ID provided: [$entry]"))
                            }
                    },
                    core = MockServerCoreEndpointClient(
                        self = UUID.randomUUID(),
                        crates = mapOf(actualEntry.metadata to ByteString.EMPTY)
                    )
                ),
                track = MockRecoveryTracker()
            )

            val descriptorWithDefinition = Recovery.Descriptor(
                query = null,
                destination = null,
                collector = Recovery.Descriptor.Collector.WithDefinition(
                    definition = dataset.id,
                    until = Instant.now()
                ),
                deviceSecret = secret,
                providers = providers
            ).get()

            val descriptorWithEntry = Recovery.Descriptor(
                query = null,
                destination = null,
                collector = Recovery.Descriptor.Collector.WithEntry(
                    entry = actualEntry.id
                ),
                deviceSecret = secret,
                providers = providers
            ).get()

            descriptorWithDefinition.targetMetadata shouldBe (metadata)
            descriptorWithDefinition.deviceSecret shouldBe (secret)

            descriptorWithEntry.targetMetadata shouldBe (metadata)
            descriptorWithEntry.deviceSecret shouldBe (secret)
        }

        "handle missing entries when creating from collector descriptor" {
            val dataset = Fixtures.Datasets.Default

            val providers = Providers(
                checksum = checksum,
                staging = DefaultFileStaging(
                    storeDirectory = null,
                    prefix = "staged-",
                    suffix = ".tmp"
                ),
                decompressor = MockCompression(),
                decryptor = MockEncryption(),
                clients = Clients(
                    api = object : MockServerApiEndpointClient(self = UUID.randomUUID()) {
                        override suspend fun latestEntry(
                            definition: DatasetDefinitionId,
                            until: Instant?
                        ): Try<DatasetEntry?> = Success(null)
                    },
                    core = MockServerCoreEndpointClient()
                ),
                track = MockRecoveryTracker()
            )

            val e = shouldThrow<IllegalStateException> {
                Recovery
                    .Descriptor(
                        query = null,
                        destination = null,
                        collector = Recovery.Descriptor.Collector.WithDefinition(
                            definition = dataset.id,
                            until = Instant.now()
                        ),
                        deviceSecret = secret,
                        providers = providers
                    ).get()
            }

            e.message shouldBe ("Expected dataset entry for definition [${dataset.id}] but none was found")
        }

        "be convertible to a recovery collector" {
            val providers = Providers(
                checksum = checksum,
                staging = DefaultFileStaging(
                    storeDirectory = null,
                    prefix = "staged-",
                    suffix = ".tmp"
                ),
                decompressor = MockCompression(),
                decryptor = MockEncryption(),
                clients = Clients(
                    api = MockServerApiEndpointClient(),
                    core = MockServerCoreEndpointClient()
                ),
                track = MockRecoveryTracker()
            )

            val descriptor = Recovery.Descriptor(
                targetMetadata = DatasetMetadata.empty(),
                query = null,
                destination = null,
                deviceSecret = secret
            )

            descriptor.toRecoveryCollector(providers).shouldBeInstanceOf<DefaultRecoveryCollector>()
        }
    }

    "Recovery path queries" should {
        "support checking for matches in paths" {
            matchingPathRegexes.forEach { regex ->
                val query = Recovery.PathQuery.ForAbsolutePath(query = Regex(regex))

                withClue("Matching path [$matchingPath] with [$regex]:") {
                    query.matches(matchingPath) shouldBe (true)
                }
            }
        }

        "support checking for matches in file names" {
            matchingFileNameRegexes.forEach { regex ->
                val query = Recovery.PathQuery.ForFileName(query = Regex(regex))
                withClue("Matching file name in path [$matchingPath] with [$regex]:") {
                    query.matches(matchingPath) shouldBe (true)
                }
            }

            nonMatchingPathRegexes.forEach { regex ->
                val query = Recovery.PathQuery.ForFileName(query = Regex(regex))
                withClue("Matching file name in path [$matchingPath] with [$regex]:") {
                    query.matches(matchingPath) shouldBe (false)
                }
            }
        }

        "create path queries from regex strings" {
            Recovery.PathQuery("/tmp/some-file.txt")
                .shouldBeInstanceOf<Recovery.PathQuery.ForAbsolutePath>()
            Recovery.PathQuery("some-file.txt").shouldBeInstanceOf<Recovery.PathQuery.ForFileName>()
        }
    }

    "A Recovery destination" should {
        "be convertible to TargetEntity destination" {
            val (filesystem, _) = createMockFileSystem(
                setup = ResourceHelpers.FileSystemSetup.Unix
            )

            val recoveryDestination = Recovery.Destination(
                path = "/tmp/test/path",
                keepStructure = false,
                filesystem = filesystem
            )

            recoveryDestination.toTargetEntityDestination() shouldBe (
                    TargetEntity.Destination.Directory(
                        path = filesystem.getPath(recoveryDestination.path),
                        keepDefaultStructure = recoveryDestination.keepStructure
                    )
                    )

            null.toTargetEntityDestination() shouldBe (TargetEntity.Destination.Default)
        }
    }
})
