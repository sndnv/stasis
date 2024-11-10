package stasis.test.client_android.lib.ops.scheduling

import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldInclude
import io.kotest.matchers.string.shouldStartWith
import kotlinx.coroutines.Dispatchers
import okio.Sink
import okio.Source
import okio.Throttler
import stasis.client_android.lib.analysis.Checksum
import stasis.client_android.lib.api.clients.Clients
import stasis.client_android.lib.collection.rules.Rule
import stasis.client_android.lib.encryption.secrets.DeviceFileSecret
import stasis.client_android.lib.encryption.secrets.DeviceMetadataSecret
import stasis.client_android.lib.model.server.datasets.DatasetDefinition
import stasis.client_android.lib.model.server.datasets.DatasetDefinitionId
import stasis.client_android.lib.model.server.datasets.DatasetEntry
import stasis.client_android.lib.model.server.datasets.DatasetEntryId
import stasis.client_android.lib.model.server.devices.DeviceId
import stasis.client_android.lib.ops.Operation
import stasis.client_android.lib.ops.OperationId
import stasis.client_android.lib.ops.backup.Backup
import stasis.client_android.lib.ops.scheduling.DefaultOperationExecutor
import stasis.client_android.lib.tracking.state.BackupState
import stasis.client_android.lib.utils.Try
import stasis.client_android.lib.utils.Try.Failure
import stasis.test.client_android.lib.Fixtures
import stasis.test.client_android.lib.eventually
import stasis.test.client_android.lib.mocks.MockBackupTracker
import stasis.test.client_android.lib.mocks.MockCompression
import stasis.test.client_android.lib.mocks.MockEncryption
import stasis.test.client_android.lib.mocks.MockFileStaging
import stasis.test.client_android.lib.mocks.MockRecoveryTracker
import stasis.test.client_android.lib.mocks.MockServerApiEndpointClient
import stasis.test.client_android.lib.mocks.MockServerCoreEndpointClient
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class DefaultOperationExecutorSpec : WordSpec({
    "A DefaultOperationExecutor" should {
        fun createExecutor(
            slowEncryption: Boolean = false,
            backupTracker: MockBackupTracker = MockBackupTracker(),
            recoveryTracker: MockRecoveryTracker = MockRecoveryTracker(),
            clients: Clients = Clients(
                api = MockServerApiEndpointClient(),
                core = MockServerCoreEndpointClient()
            )
        ): DefaultOperationExecutor {
            val checksum = Checksum.Companion.MD5
            val staging = MockFileStaging()
            val compression = MockCompression()

            val throttler = Throttler().apply {
                bytesPerSecond(
                    bytesPerSecond = 1,
                    waitByteCount = 4L,
                    maxByteCount = 8L
                )
            }

            val encryption = object : MockEncryption() {
                override fun encrypt(source: Source, fileSecret: DeviceFileSecret): Source {
                    return if (slowEncryption) {
                        throttler.source(super.encrypt(source, fileSecret))
                    } else {
                        super.encrypt(source, fileSecret)
                    }
                }

                override fun encrypt(sink: Sink, fileSecret: DeviceFileSecret): Sink {
                    return if (slowEncryption) {
                        throttler.sink(super.encrypt(sink, fileSecret))
                    } else {
                        super.encrypt(sink, fileSecret)
                    }
                }

                override fun encrypt(source: Source, metadataSecret: DeviceMetadataSecret): Source {
                    return if (slowEncryption) {
                        throttler.source(super.encrypt(source, metadataSecret))
                    } else {
                        super.encrypt(source, metadataSecret)
                    }
                }

                override fun decrypt(source: Source, fileSecret: DeviceFileSecret): Source {
                    return if (slowEncryption) {
                        throttler.source(super.decrypt(source, fileSecret))
                    } else {
                        super.decrypt(source, fileSecret)
                    }
                }

                override fun decrypt(source: Source, metadataSecret: DeviceMetadataSecret): Source {
                    return if (slowEncryption) {
                        throttler.source(super.decrypt(source, metadataSecret))
                    } else {
                        super.decrypt(source, metadataSecret)
                    }
                }
            }

            return DefaultOperationExecutor(
                config = DefaultOperationExecutor.Config(
                    backup = DefaultOperationExecutor.Config.Backup(
                        limits = Backup.Descriptor.Limits(maxPartSize = 16384L)
                    )
                ),
                deviceSecret = { Fixtures.Secrets.Default },
                backupProviders = stasis.client_android.lib.ops.backup.Providers(
                    checksum = checksum,
                    staging = staging,
                    compression = compression,
                    encryptor = encryption,
                    decryptor = encryption,
                    clients = clients,
                    track = backupTracker
                ),
                recoveryProviders = stasis.client_android.lib.ops.recovery.Providers(
                    checksum = checksum,
                    staging = staging,
                    decryptor = encryption,
                    clients = clients,
                    track = recoveryTracker,
                    compression = compression
                ),
                operationDispatcher = Dispatchers.IO
            )
        }

        val rules = listOf(
            Rule(
                id = 1L,
                operation = Rule.Operation.Include,
                directory = "/home__/stasis",
                pattern = "**",
                definition = null
            ),
            Rule(
                id = 2L,
                operation = Rule.Operation.Exclude,
                directory = "/home__/stasis",
                pattern = "**/*cache*/*",
                definition = null
            ),
            Rule(
                id = 3L,
                operation = Rule.Operation.Exclude,
                directory = "/home__/stasis",
                pattern = "**/*log*/*",
                definition = null
            ),
        )

        "start backups (with rules)" {
            val operationCompleted = AtomicBoolean(false)

            val mockTracker = MockBackupTracker()
            val executor = createExecutor(backupTracker = mockTracker)

            executor.startBackupWithRules(
                definition = UUID.randomUUID(),
                rules = rules
            ) {
                operationCompleted.set(true)
            }

            eventually {
                mockTracker.statistics[MockBackupTracker.Statistic.Started] shouldBe (1)
                mockTracker.statistics[MockBackupTracker.Statistic.EntityDiscovered] shouldBe (0)
                mockTracker.statistics[MockBackupTracker.Statistic.SpecificationProcessed] shouldBe (1)
                mockTracker.statistics[MockBackupTracker.Statistic.EntityExamined] shouldBe (0)
                mockTracker.statistics[MockBackupTracker.Statistic.EntitySkipped] shouldBe (0)
                mockTracker.statistics[MockBackupTracker.Statistic.EntityCollected] shouldBe (0)
                mockTracker.statistics[MockBackupTracker.Statistic.EntityProcessingStarted] shouldBe (0)
                mockTracker.statistics[MockBackupTracker.Statistic.EntityPartProcessed] shouldBe (0)
                mockTracker.statistics[MockBackupTracker.Statistic.EntityProcessed] shouldBe (0)
                mockTracker.statistics[MockBackupTracker.Statistic.MetadataCollected] shouldBe (1)
                mockTracker.statistics[MockBackupTracker.Statistic.MetadataPushed] shouldBe (1)
                mockTracker.statistics[MockBackupTracker.Statistic.FailureEncountered] shouldBe (0)
                mockTracker.statistics[MockBackupTracker.Statistic.Completed] shouldBe (1)

                operationCompleted.get() shouldBe (true)
            }
        }

        "handle backup start failure (with rules)" {
            val operationResult = AtomicReference<Throwable>()

            val executor = createExecutor(
                clients = Clients(
                    api = object : MockServerApiEndpointClient(self = DeviceId.randomUUID()) {
                        override suspend fun datasetDefinition(definition: DatasetDefinitionId): Try<DatasetDefinition> =
                            Failure(RuntimeException("Test failure"))

                    },
                    core = MockServerCoreEndpointClient()
                )
            )

            executor.startBackupWithRules(
                definition = UUID.randomUUID(),
                rules = rules
            ) {
                operationResult.set(it)
            }

            eventually {
                operationResult.get().message shouldBe ("Test failure")
            }
        }

        "start backups (with files)" {
            val operationCompleted = AtomicBoolean(false)

            val mockTracker = MockBackupTracker()
            val executor = createExecutor(backupTracker = mockTracker)

            executor.startBackupWithEntities(
                definition = UUID.randomUUID(),
                entities = emptyList()
            ) {
                operationCompleted.set(true)
            }

            eventually {
                mockTracker.statistics[MockBackupTracker.Statistic.Started] shouldBe (1)
                mockTracker.statistics[MockBackupTracker.Statistic.EntityDiscovered] shouldBe (0)
                mockTracker.statistics[MockBackupTracker.Statistic.SpecificationProcessed] shouldBe (0)
                mockTracker.statistics[MockBackupTracker.Statistic.EntityExamined] shouldBe (0)
                mockTracker.statistics[MockBackupTracker.Statistic.EntitySkipped] shouldBe (0)
                mockTracker.statistics[MockBackupTracker.Statistic.EntityCollected] shouldBe (0)
                mockTracker.statistics[MockBackupTracker.Statistic.EntityProcessingStarted] shouldBe (0)
                mockTracker.statistics[MockBackupTracker.Statistic.EntityPartProcessed] shouldBe (0)
                mockTracker.statistics[MockBackupTracker.Statistic.EntityProcessed] shouldBe (0)
                mockTracker.statistics[MockBackupTracker.Statistic.MetadataCollected] shouldBe (1)
                mockTracker.statistics[MockBackupTracker.Statistic.MetadataPushed] shouldBe (1)
                mockTracker.statistics[MockBackupTracker.Statistic.FailureEncountered] shouldBe (0)
                mockTracker.statistics[MockBackupTracker.Statistic.Completed] shouldBe (1)

                operationCompleted.get() shouldBe (true)
            }
        }

        "handle backup start failure (with files)" {
            val operationResult = AtomicReference<Throwable>()

            val executor = createExecutor(
                clients = Clients(
                    api = object : MockServerApiEndpointClient(self = DeviceId.randomUUID()) {
                        override suspend fun datasetDefinition(definition: DatasetDefinitionId): Try<DatasetDefinition> =
                            Failure(RuntimeException("Test failure"))

                    },
                    core = MockServerCoreEndpointClient()
                )
            )

            executor.startBackupWithEntities(
                definition = UUID.randomUUID(),
                entities = emptyList()
            ) {
                operationResult.set(it)
            }

            eventually {
                operationResult.get().message shouldBe ("Test failure")
            }
        }

        "resume backups (with state)" {
            val operation = Operation.generateId()

            val operationCompleted = AtomicBoolean(false)

            val mockTracker = object : MockBackupTracker() {
                override suspend fun stateOf(operation: OperationId): BackupState =
                    BackupState.start(operation = operation, definition = UUID.randomUUID())
            }

            val executor = createExecutor(backupTracker = mockTracker)

            executor.resumeBackup(
                operation = operation
            ) {
                operationCompleted.set(it == null)
            }

            eventually {
                mockTracker.statistics[MockBackupTracker.Statistic.Started] shouldBe (0)
                mockTracker.statistics[MockBackupTracker.Statistic.EntityDiscovered] shouldBe (0)
                mockTracker.statistics[MockBackupTracker.Statistic.SpecificationProcessed] shouldBe (0)
                mockTracker.statistics[MockBackupTracker.Statistic.EntityExamined] shouldBe (0)
                mockTracker.statistics[MockBackupTracker.Statistic.EntitySkipped] shouldBe (0)
                mockTracker.statistics[MockBackupTracker.Statistic.EntityCollected] shouldBe (0)
                mockTracker.statistics[MockBackupTracker.Statistic.EntityProcessingStarted] shouldBe (0)
                mockTracker.statistics[MockBackupTracker.Statistic.EntityPartProcessed] shouldBe (0)
                mockTracker.statistics[MockBackupTracker.Statistic.EntityProcessed] shouldBe (0)
                mockTracker.statistics[MockBackupTracker.Statistic.MetadataCollected] shouldBe (1)
                mockTracker.statistics[MockBackupTracker.Statistic.MetadataPushed] shouldBe (1)
                mockTracker.statistics[MockBackupTracker.Statistic.FailureEncountered] shouldBe (0)
                mockTracker.statistics[MockBackupTracker.Statistic.Completed] shouldBe (1)

                operationCompleted.get() shouldBe (true)
            }
        }

        "start recoveries (with definitions)" {
            val operationCompleted = AtomicBoolean(false)

            val mockTracker = MockRecoveryTracker()
            val executor = createExecutor(recoveryTracker = mockTracker)

            executor
                .startRecoveryWithDefinition(
                    definition = UUID.randomUUID(),
                    until = null,
                    query = null,
                    destination = null
                ) {
                    operationCompleted.set(true)
                }

            eventually {
                mockTracker.statistics[MockRecoveryTracker.Statistic.Started] shouldBe (1)
                mockTracker.statistics[MockRecoveryTracker.Statistic.EntityExamined] shouldBe (0)
                mockTracker.statistics[MockRecoveryTracker.Statistic.EntityCollected] shouldBe (0)
                mockTracker.statistics[MockRecoveryTracker.Statistic.EntityProcessingStarted] shouldBe (0)
                mockTracker.statistics[MockRecoveryTracker.Statistic.EntityPartProcessed] shouldBe (0)
                mockTracker.statistics[MockRecoveryTracker.Statistic.EntityProcessed] shouldBe (0)
                mockTracker.statistics[MockRecoveryTracker.Statistic.MetadataApplied] shouldBe (0)
                mockTracker.statistics[MockRecoveryTracker.Statistic.FailureEncountered] shouldBe (0)
                mockTracker.statistics[MockRecoveryTracker.Statistic.Completed] shouldBe (1)

                operationCompleted.get() shouldBe (true)
            }
        }

        "handle recovery start failure (with definitions)" {
            val operationResult = AtomicReference<Throwable>()

            val executor = createExecutor(
                clients = Clients(
                    api = object : MockServerApiEndpointClient(self = DeviceId.randomUUID()) {
                        override suspend fun latestEntry(
                            definition: DatasetDefinitionId,
                            until: Instant?
                        ): Try<DatasetEntry?> = Failure(RuntimeException("Test failure"))
                    },
                    core = MockServerCoreEndpointClient()
                )
            )

            executor
                .startRecoveryWithDefinition(
                    definition = UUID.randomUUID(),
                    until = null,
                    query = null,
                    destination = null
                ) {
                    operationResult.set(it)
                }

            eventually {
                operationResult.get().message shouldBe ("Test failure")
            }
        }

        "start recoveries (with entries)" {
            val operationCompleted = AtomicBoolean(false)

            val mockTracker = MockRecoveryTracker()
            val executor = createExecutor(recoveryTracker = mockTracker)

            executor
                .startRecoveryWithEntry(
                    entry = UUID.randomUUID(),
                    query = null,
                    destination = null
                ) {
                    operationCompleted.set(true)
                }

            eventually {
                mockTracker.statistics[MockRecoveryTracker.Statistic.Started] shouldBe (1)
                mockTracker.statistics[MockRecoveryTracker.Statistic.EntityExamined] shouldBe (0)
                mockTracker.statistics[MockRecoveryTracker.Statistic.EntityCollected] shouldBe (0)
                mockTracker.statistics[MockRecoveryTracker.Statistic.EntityProcessingStarted] shouldBe (0)
                mockTracker.statistics[MockRecoveryTracker.Statistic.EntityPartProcessed] shouldBe (0)
                mockTracker.statistics[MockRecoveryTracker.Statistic.EntityProcessed] shouldBe (0)
                mockTracker.statistics[MockRecoveryTracker.Statistic.MetadataApplied] shouldBe (0)
                mockTracker.statistics[MockRecoveryTracker.Statistic.FailureEncountered] shouldBe (0)
                mockTracker.statistics[MockRecoveryTracker.Statistic.Completed] shouldBe (1)

                operationCompleted.get() shouldBe (true)
            }
        }

        "handle recovery start failure (with entries)" {
            val operationResult = AtomicReference<Throwable>()

            val executor = createExecutor(
                clients = Clients(
                    api = object : MockServerApiEndpointClient(self = DeviceId.randomUUID()) {
                        override suspend fun datasetEntry(entry: DatasetEntryId): Try<DatasetEntry> =
                            Failure(RuntimeException("Test failure"))
                    },
                    core = MockServerCoreEndpointClient()
                )
            )

            executor
                .startRecoveryWithEntry(
                    entry = UUID.randomUUID(),
                    query = null,
                    destination = null
                ) {
                    operationResult.set(it)
                }

            eventually {
                operationResult.get().message shouldBe ("Test failure")
            }
        }

        "start expirations" {
            val executor = createExecutor()

            val e = shouldThrow<NotImplementedError> {
                executor.startExpiration(f = {})
            }

            e.message shouldBe ("Expiration is not supported")
        }

        "start validations" {
            val executor = createExecutor()

            val e = shouldThrow<NotImplementedError> {
                executor.startValidation(f = {})
            }

            e.message shouldBe ("Validation is not supported")
        }

        "start key rotations" {
            val executor = createExecutor()

            val e = shouldThrow<NotImplementedError> {
                executor.startKeyRotation(f = {})
            }

            e.message shouldBe ("Key rotation is not supported")
        }

        "stop running operations" {
            val operationCompleted = AtomicBoolean(false)

            val mockTracker = MockBackupTracker()

            val executor = createExecutor(slowEncryption = true, backupTracker = mockTracker)

            val operation = executor.startBackupWithRules(
                definition = UUID.randomUUID(),
                rules = rules
            ) {
                operationCompleted.set(true)
            }

            eventually {
                shouldNotThrow<IllegalArgumentException> { executor.stop(operation) }
            }

            eventually {
                operationCompleted.get() shouldBe (true)
            }
        }

        "fail to stop operations that are not running" {
            val executor = createExecutor()

            val operation = UUID.randomUUID()

            val e = shouldThrow<IllegalArgumentException> {
                executor.stop(operation)
            }

            e.message shouldBe ("Failed to stop [$operation]; operation not found")
        }

        "provide a list of active and completed operations" {
            val executor = createExecutor()

            executor.active() shouldBe (emptyMap())
            executor.completed() shouldBe (emptyMap())

            val backup = eventually {
                val backup = executor.startBackupWithRules(
                    definition = UUID.randomUUID(),
                    rules = rules,
                    f = {}
                )

                executor.active()[backup] shouldBe (Operation.Type.Backup)

                backup
            }

            eventually {
                executor.active() shouldBe (emptyMap())
                executor.completed()[backup] shouldBe (Operation.Type.Backup)
            }
        }

        "support searching for operations" {
            val executor = createExecutor()

            executor.active().size shouldBe (0)
            executor.completed().size shouldBe (0)

            executor.find(operation = Operation.generateId()) shouldBe (null)

            val backup = executor.startBackupWithEntities(
                definition = UUID.randomUUID(),
                entities = emptyList(),
                f = {}
            )

            executor.find(backup) shouldBe (Operation.Type.Backup)

            val recovery = executor.startRecoveryWithEntry(
                entry = UUID.randomUUID(),
                query = null,
                destination = null,
                f = {}
            )

            executor.find(recovery) shouldBe (Operation.Type.Recovery)
        }

        "fail to start an operation if one of the same type is already running" {

            val executor = createExecutor(slowEncryption = true)

            eventually {
                executor.active() shouldBe (emptyMap())

                eventually {
                    val backup = executor.startBackupWithRules(
                        definition = UUID.randomUUID(),
                        rules = rules,
                        f = {}
                    )
                    executor.active()[backup] shouldBe (Operation.Type.Backup)
                }

                val failure = AtomicReference<Throwable>(null)

                executor.startBackupWithRules(
                    definition = UUID.randomUUID(),
                    rules = rules,
                    f = { failure.set(it) }
                )

                val e = failure.get()
                e.message shouldStartWith ("Cannot start [Backup] operation")
                e.message shouldInclude ("already active")
            }
        }

        "fail to resume a backup that is already completed" {
            val operation = Operation.generateId()

            val operationCompleted = AtomicReference<Throwable?>(null)

            val mockTracker = object : MockBackupTracker() {
                override suspend fun stateOf(operation: OperationId): BackupState =
                    BackupState
                        .start(operation = operation, definition = UUID.randomUUID())
                        .backupCompleted()
            }

            val executor = createExecutor(backupTracker = mockTracker)

            executor.resumeBackup(
                operation = operation
            ) {
                operationCompleted.set(it)
            }

            eventually {
                mockTracker.statistics[MockBackupTracker.Statistic.Started] shouldBe (0)
                mockTracker.statistics[MockBackupTracker.Statistic.EntityDiscovered] shouldBe (0)
                mockTracker.statistics[MockBackupTracker.Statistic.SpecificationProcessed] shouldBe (0)
                mockTracker.statistics[MockBackupTracker.Statistic.EntityExamined] shouldBe (0)
                mockTracker.statistics[MockBackupTracker.Statistic.EntitySkipped] shouldBe (0)
                mockTracker.statistics[MockBackupTracker.Statistic.EntityCollected] shouldBe (0)
                mockTracker.statistics[MockBackupTracker.Statistic.EntityProcessingStarted] shouldBe (0)
                mockTracker.statistics[MockBackupTracker.Statistic.EntityPartProcessed] shouldBe (0)
                mockTracker.statistics[MockBackupTracker.Statistic.EntityProcessed] shouldBe (0)
                mockTracker.statistics[MockBackupTracker.Statistic.MetadataCollected] shouldBe (0)
                mockTracker.statistics[MockBackupTracker.Statistic.MetadataPushed] shouldBe (0)
                mockTracker.statistics[MockBackupTracker.Statistic.FailureEncountered] shouldBe (0)
                mockTracker.statistics[MockBackupTracker.Statistic.Completed] shouldBe (0)

                operationCompleted.get()?.message shouldBe (
                        "Cannot resume operation with ID [$operation]; operation already completed"
                        )
            }
        }

        "fail to resume a backup no state for it can be found" {
            val operation = Operation.generateId()

            val operationCompleted = AtomicReference<Throwable?>(null)

            val mockTracker = MockBackupTracker()

            val executor = createExecutor(backupTracker = mockTracker)

            executor.resumeBackup(
                operation = operation
            ) {
                operationCompleted.set(it)
            }

            eventually {
                mockTracker.statistics[MockBackupTracker.Statistic.Started] shouldBe (0)
                mockTracker.statistics[MockBackupTracker.Statistic.EntityDiscovered] shouldBe (0)
                mockTracker.statistics[MockBackupTracker.Statistic.SpecificationProcessed] shouldBe (0)
                mockTracker.statistics[MockBackupTracker.Statistic.EntityExamined] shouldBe (0)
                mockTracker.statistics[MockBackupTracker.Statistic.EntitySkipped] shouldBe (0)
                mockTracker.statistics[MockBackupTracker.Statistic.EntityCollected] shouldBe (0)
                mockTracker.statistics[MockBackupTracker.Statistic.EntityProcessingStarted] shouldBe (0)
                mockTracker.statistics[MockBackupTracker.Statistic.EntityPartProcessed] shouldBe (0)
                mockTracker.statistics[MockBackupTracker.Statistic.EntityProcessed] shouldBe (0)
                mockTracker.statistics[MockBackupTracker.Statistic.MetadataCollected] shouldBe (0)
                mockTracker.statistics[MockBackupTracker.Statistic.MetadataPushed] shouldBe (0)
                mockTracker.statistics[MockBackupTracker.Statistic.FailureEncountered] shouldBe (0)
                mockTracker.statistics[MockBackupTracker.Statistic.Completed] shouldBe (0)

                operationCompleted.get()?.message shouldBe (
                        "Cannot resume operation with ID [$operation]; no existing state was found"
                        )
            }
        }
    }
})
