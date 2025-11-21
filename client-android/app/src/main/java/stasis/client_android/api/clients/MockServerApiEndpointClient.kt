package stasis.client_android.api.clients

import kotlinx.coroutines.delay
import okio.ByteString
import stasis.client_android.lib.api.clients.ServerApiEndpointClient
import stasis.client_android.lib.api.clients.exceptions.ResourceMissingFailure
import stasis.client_android.lib.model.DatasetMetadata
import stasis.client_android.lib.model.EntityMetadata
import stasis.client_android.lib.model.FilesystemMetadata
import stasis.client_android.lib.model.core.CrateId
import stasis.client_android.lib.model.server.api.requests.CreateDatasetDefinition
import stasis.client_android.lib.model.server.api.requests.CreateDatasetEntry
import stasis.client_android.lib.model.server.api.requests.ResetUserPassword
import stasis.client_android.lib.model.server.api.requests.UpdateDatasetDefinition
import stasis.client_android.lib.model.server.api.responses.CommandAsJson.Companion.asProtobuf
import stasis.client_android.lib.model.server.api.responses.CreatedDatasetDefinition
import stasis.client_android.lib.model.server.api.responses.CreatedDatasetEntry
import stasis.client_android.lib.model.server.api.responses.Ping
import stasis.client_android.lib.model.server.api.responses.UpdatedUserSalt
import stasis.client_android.lib.model.server.datasets.DatasetDefinition
import stasis.client_android.lib.model.server.datasets.DatasetDefinitionId
import stasis.client_android.lib.model.server.datasets.DatasetEntry
import stasis.client_android.lib.model.server.datasets.DatasetEntryId
import stasis.client_android.lib.model.server.devices.Device
import stasis.client_android.lib.model.server.devices.DeviceId
import stasis.client_android.lib.model.server.schedules.Schedule
import stasis.client_android.lib.model.server.schedules.ScheduleId
import stasis.client_android.lib.model.server.users.User
import stasis.client_android.lib.telemetry.analytics.AnalyticsEntry
import stasis.client_android.lib.utils.Try
import stasis.client_android.lib.utils.Try.Failure
import stasis.client_android.lib.utils.Try.Success
import stasis.core.commands.proto.Command
import stasis.core.commands.proto.CommandParameters
import stasis.core.commands.proto.LogoutUser
import java.math.BigInteger
import java.nio.file.Paths
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.ThreadLocalRandom

class MockServerApiEndpointClient(private val maxSimulatedDelay: Long = 2000) : ServerApiEndpointClient {
    override val self: DeviceId = MockConfig.Device

    override val server: String = MockConfig.ServerApi

    private val nextDelay: Long
        get() = ThreadLocalRandom.current().nextLong(0, maxSimulatedDelay)

    override suspend fun datasetDefinitions(): Try<List<DatasetDefinition>> {
        delay(nextDelay)
        return Success(
            listOf(
                defaultDefinition,
                otherDefinition,
                otherDefinition.copy(id = UUID.randomUUID(), created = Instant.now(), updated = Instant.now())
            )
        )
    }

    override suspend fun datasetDefinition(definition: DatasetDefinitionId): Try<DatasetDefinition> {
        delay(nextDelay)
        return when (definition) {
            defaultDefinition.id -> Success(defaultDefinition)
            else -> Failure(RuntimeException("Invalid definition requested: [$definition]"))
        }
    }

    override suspend fun createDatasetDefinition(request: CreateDatasetDefinition): Try<CreatedDatasetDefinition> {
        delay(nextDelay)
        return Success(CreatedDatasetDefinition(definition = defaultDefinition.id))
    }

    override suspend fun updateDatasetDefinition(
        definition: DatasetDefinitionId,
        request: UpdateDatasetDefinition
    ): Try<Unit> {
        delay(nextDelay)
        return Success(Unit)
    }

    override suspend fun deleteDatasetDefinition(definition: DatasetDefinitionId): Try<Unit> {
        delay(nextDelay)
        return Success(Unit)
    }

    override suspend fun datasetEntries(definition: DatasetDefinitionId): Try<List<DatasetEntry>> {
        delay(nextDelay)
        return when (definition) {
            defaultDefinition.id -> Success(listOf(defaultEntry))
            else -> Failure(RuntimeException("Invalid definition requested: [$definition]"))
        }
    }

    override suspend fun datasetEntry(entry: DatasetEntryId): Try<DatasetEntry> {
        delay(nextDelay)
        return when (entry) {
            defaultEntry.id -> Success(defaultEntry)
            else -> Failure(RuntimeException("Invalid entry requested: [$entry]"))
        }
    }

    override suspend fun latestEntry(definition: DatasetDefinitionId, until: Instant?): Try<DatasetEntry?> {
        delay(nextDelay)
        return when (definition) {
            defaultDefinition.id -> Success(defaultEntry)
            else -> Success(null)
        }
    }

    override suspend fun createDatasetEntry(request: CreateDatasetEntry): Try<CreatedDatasetEntry> {
        delay(nextDelay)
        return Success(CreatedDatasetEntry(entry = defaultEntry.id))
    }

    override suspend fun deleteDatasetEntry(entry: DatasetEntryId): Try<Unit> {
        delay(nextDelay)
        return Success(Unit)
    }

    override suspend fun publicSchedules(): Try<List<Schedule>> {
        delay(nextDelay)
        return Success(
            listOf(
                defaultSchedule,
                defaultSchedule.copy(
                    id = ScheduleId.randomUUID(),
                    info = "test-schedule-2",
                    start = LocalDateTime.now().plusHours(6),
                )
            )
        )
    }

    override suspend fun publicSchedule(schedule: ScheduleId): Try<Schedule> {
        delay(nextDelay)
        return when (schedule) {
            defaultSchedule.id -> Success(defaultSchedule)
            else -> Failure(RuntimeException("Invalid schedule requested: [$schedule]"))
        }
    }

    override suspend fun datasetMetadata(entry: DatasetEntryId): Try<DatasetMetadata> {
        delay(nextDelay)
        return Success(defaultMetadata)
    }

    override suspend fun datasetMetadata(entry: DatasetEntry): Try<DatasetMetadata> {
        delay(nextDelay)
        return Success(defaultMetadata)
    }

    override suspend fun user(): Try<User> {
        delay(nextDelay)
        return Success(currentUser)
    }

    override suspend fun resetUserSalt(): Try<UpdatedUserSalt> {
        delay(nextDelay)
        return Success(UpdatedUserSalt(salt = currentUser.salt))
    }

    override suspend fun resetUserPassword(request: ResetUserPassword): Try<Unit> {
        delay(nextDelay)
        return Success(Unit)
    }

    override suspend fun device(): Try<Device> {
        delay(nextDelay)
        return Success(currentDevice)
    }

    override suspend fun pushDeviceKey(key: ByteString): Try<Unit> {
        delay(nextDelay)
        return Success(Unit)
    }

    override suspend fun pullDeviceKey(): Try<ByteString> {
        delay(nextDelay)
        return Failure(ResourceMissingFailure())
    }

    override suspend fun deviceKeyExists(): Try<Boolean> {
        delay(nextDelay)
        return Success(false)
    }

    override suspend fun ping(): Try<Ping> {
        delay(nextDelay)
        return Success(Ping(id = UUID.randomUUID()))
    }

    override suspend fun commands(lastSequenceId: Long?): Try<List<Command>> {
        delay(nextDelay)
        return Success(
            listOf(command1, command2)
                .withIndex()
                .map { it.value.copy(sequenceId = it.index.toLong() + 1) }
                .filter { it.sequenceId > (lastSequenceId ?: 0) }
        )
    }

    override suspend fun sendAnalyticsEntry(entry: AnalyticsEntry): Try<Unit> {
        delay(nextDelay)
        return Success(Unit)
    }

    private val defaultDefinition = DatasetDefinition(
        id = DatasetDefinitionId.randomUUID(),
        info = "test-definition",
        device = self,
        redundantCopies = 42,
        existingVersions = DatasetDefinition.Retention(
            policy = DatasetDefinition.Retention.Policy.All,
            duration = Duration.ofHours(12)
        ),
        removedVersions = DatasetDefinition.Retention(
            policy = DatasetDefinition.Retention.Policy.All,
            duration = Duration.ofHours(366)
        ),
        created = Instant.EPOCH,
        updated = Instant.EPOCH,
    )

    private val otherDefinition = DatasetDefinition(
        id = DatasetDefinitionId.randomUUID(),
        info = "other-definition",
        device = self,
        redundantCopies = 2,
        existingVersions = DatasetDefinition.Retention(
            policy = DatasetDefinition.Retention.Policy.All,
            duration = Duration.ofHours(12)
        ),
        removedVersions = DatasetDefinition.Retention(
            policy = DatasetDefinition.Retention.Policy.All,
            duration = Duration.ofHours(366)
        ),
        created = Instant.now(),
        updated = Instant.now(),
    )

    private val defaultEntry = DatasetEntry(
        id = DatasetEntryId.randomUUID(),
        definition = defaultDefinition.id,
        device = self,
        data = setOf(CrateId.randomUUID(), CrateId.randomUUID()),
        metadata = CrateId.randomUUID(),
        created = Instant.now()
    )

    private val defaultSchedule = Schedule(
        id = ScheduleId.randomUUID(),
        info = "test-schedule-1",
        isPublic = true,
        start = LocalDateTime.now().plusHours(4),
        interval = Duration.ofHours(12),
        created = Instant.now().minusSeconds(42),
        updated = Instant.now(),
    )

    private val metadataFileOnePath = Paths.get("/tmp/file/one")
    private val metadataFileTwoPath = Paths.get("/tmp/file/.two")

    private val defaultMetadata = DatasetMetadata(
        contentChanged = mapOf(
            metadataFileOnePath to EntityMetadata.File(
                path = metadataFileOnePath,
                size = 1,
                link = null,
                isHidden = false,
                created = Instant.EPOCH.truncatedTo(ChronoUnit.SECONDS),
                updated = Instant.EPOCH.truncatedTo(ChronoUnit.SECONDS),
                owner = "root",
                group = "root",
                permissions = "rwxrwxrwx",
                checksum = BigInteger("1"),
                crates = mapOf(
                    Paths.get("/tmp/file/one_0") to UUID.fromString("329efbeb-80a3-42b8-b1dc-79bc0fea7bca")
                ),
                compression = "none"
            )
        ),
        metadataChanged = mapOf(
            metadataFileTwoPath to EntityMetadata.File(
                path = metadataFileTwoPath,
                size = 2,
                link = Paths.get("/tmp/file/three"),
                isHidden = false,
                created = Instant.EPOCH.truncatedTo(ChronoUnit.SECONDS),
                updated = Instant.EPOCH.truncatedTo(ChronoUnit.SECONDS),
                owner = "root",
                group = "root",
                permissions = "rwxrwxrwx",
                checksum = BigInteger("42"),
                crates = mapOf(
                    Paths.get("/tmp/file/.two_0") to UUID.fromString("e672a956-1a95-4304-8af0-9418f0e43cba")
                ),
                compression = "gzip"
            ),
        ),
        filesystem = FilesystemMetadata(
            entities = mapOf(
                metadataFileOnePath to FilesystemMetadata.EntityState.New,
                metadataFileTwoPath to FilesystemMetadata.EntityState.Updated,
            )
        )
    )

    private val currentUser = User(
        id = MockConfig.User,
        salt = "test-salt",
        active = true,
        limits = User.Limits(
            maxDevices = 12 * 1024,
            maxCrates = Long.MAX_VALUE,
            maxStorage = BigInteger.valueOf(1024L * 1024 * 1024 * 1024),
            maxStoragePerCrate = BigInteger.valueOf(64L * 1024 * 1024 * 1024),
            maxRetention = Duration.ofDays(4),
            minRetention = Duration.ofHours(12),
        ),
        permissions = setOf(
            "manage-service",
            "view-public",
            "view-service",
            "view-privileged",
            "view-self",
            "manage-privileged",
            "manage-self"
        ),
        created = Instant.EPOCH,
        updated = Instant.EPOCH,
    )

    private val currentDevice = Device(
        id = self,
        name = "test-device",
        node = MockConfig.DeviceNode,
        owner = MockConfig.User,
        active = true,
        limits = null,
        created = Instant.EPOCH,
        updated = Instant.EPOCH,
    )

    private val command1 = Command(
        sequenceId = 0,
        source = "user",
        target = self.asProtobuf(),
        parameters = CommandParameters(logoutUser = LogoutUser(reason = "Test Logout")),
        created = Instant.now().toEpochMilli()
    )

    private val command2 = Command(
        sequenceId = 1,
        source = "service",
        target = null,
        parameters = CommandParameters(),
        created = Instant.now().toEpochMilli()
    )
}
