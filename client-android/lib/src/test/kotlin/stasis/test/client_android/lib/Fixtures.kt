package stasis.test.client_android.lib

import com.squareup.wire.Instant
import okio.ByteString.Companion.toByteString
import stasis.client_android.lib.encryption.secrets.DeviceSecret
import stasis.client_android.lib.encryption.secrets.Secret
import stasis.client_android.lib.model.EntityMetadata
import stasis.client_android.lib.model.SourceEntity
import stasis.client_android.lib.model.TargetEntity
import stasis.client_android.lib.model.server.datasets.DatasetDefinition
import stasis.client_android.lib.model.server.datasets.DatasetEntry
import stasis.client_android.lib.ops.Operation
import stasis.client_android.lib.tracking.state.BackupState
import stasis.client_android.lib.tracking.state.RecoveryState
import stasis.client_android.lib.utils.Either
import stasis.test.client_android.lib.ResourceHelpers.asPath
import java.math.BigInteger
import java.nio.file.Paths
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.UUID

object Fixtures {
    object Metadata {
        val FileOneMetadata = EntityMetadata.File(
            path = "/tmp/file/one",
            size = 1,
            link = null,
            isHidden = false,
            created = Instant.MIN.truncatedTo(ChronoUnit.SECONDS),
            updated = Instant.MAX.truncatedTo(ChronoUnit.SECONDS),
            owner = "root",
            group = "root",
            permissions = "rwxrwxrwx",
            checksum = BigInteger("1"),
            crates = mapOf(
                "/tmp/file/one_0" to UUID.fromString("329efbeb-80a3-42b8-b1dc-79bc0fea7bca")
            ),
            compression = "none"
        )

        val FileTwoMetadata = EntityMetadata.File(
            path = "/tmp/file/two",
            size = 2,
            link = "/tmp/file/three",
            isHidden = false,
            created = Instant.MAX.truncatedTo(ChronoUnit.SECONDS),
            updated = Instant.MIN.truncatedTo(ChronoUnit.SECONDS),
            owner = "root",
            group = "root",
            permissions = "rwxrwxrwx",
            checksum = BigInteger("42"),
            crates = mapOf(
                "/tmp/file/two_0" to UUID.fromString("e672a956-1a95-4304-8af0-9418f0e43cba")
            ),
            compression = "gzip"
        )

        val FileThreeMetadata = EntityMetadata.File(
            path = "/tmp/file/four",
            size = 2,
            link = null,
            isHidden = false,
            created = Instant.MAX.truncatedTo(ChronoUnit.SECONDS),
            updated = Instant.MIN.truncatedTo(ChronoUnit.SECONDS),
            owner = "root",
            group = "root",
            permissions = "rwxrwxrwx",
            checksum = BigInteger("0"),
            crates = mapOf(
                "/tmp/file/four_0" to UUID.fromString("7c98df29-a544-41e5-95ac-463987894fac")
            ),
            compression = "deflate"
        )

        val DirectoryOneMetadata = EntityMetadata.Directory(
            path = "/tmp/directory/one",
            link = null,
            isHidden = false,
            created = Instant.MIN.truncatedTo(ChronoUnit.SECONDS),
            updated = Instant.MAX.truncatedTo(ChronoUnit.SECONDS),
            owner = "root",
            group = "root",
            permissions = "rwxrwxrwx"
        )

        val DirectoryTwoMetadata = EntityMetadata.Directory(
            path = "/tmp/directory/two",
            link = "/tmp/file/three",
            isHidden = false,
            created = Instant.MAX.truncatedTo(ChronoUnit.SECONDS),
            updated = Instant.MIN.truncatedTo(ChronoUnit.SECONDS),
            owner = "root",
            group = "root",
            permissions = "rwxrwxrwx"
        )

        val DirectoryThreeMetadata = EntityMetadata.Directory(
            path = "/tmp/directory/four",
            link = null,
            isHidden = false,
            created = Instant.MAX.truncatedTo(ChronoUnit.SECONDS),
            updated = Instant.MIN.truncatedTo(ChronoUnit.SECONDS),
            owner = "root",
            group = "root",
            permissions = "rwxrwxrwx"
        )
    }

    object Datasets {
        val Default = DatasetDefinition(
            id = UUID.randomUUID(),
            info = "default-test-definition",
            device = UUID.randomUUID(),
            redundantCopies = 1,
            existingVersions = DatasetDefinition.Retention(
                policy = DatasetDefinition.Retention.Policy.All,
                duration = Duration.ofSeconds(1)
            ),
            removedVersions = DatasetDefinition.Retention(
                policy = DatasetDefinition.Retention.Policy.All,
                duration = Duration.ofSeconds(1)
            ),
            created = Instant.now(),
            updated = Instant.now(),
        )
    }

    object Entries {
        val Default = DatasetEntry(
            id = UUID.randomUUID(),
            definition = Datasets.Default.id,
            device = Datasets.Default.device,
            data = (Metadata.FileOneMetadata.crates.values
                    + Metadata.FileTwoMetadata.crates.values
                    + Metadata.FileThreeMetadata.crates.values)
                .toSet(),
            metadata = UUID.randomUUID(),
            created = Instant.now()
        )
    }

    object Secrets {
        val DefaultConfig = Secret.Config(
            derivation = Secret.Config.DerivationConfig(
                encryption = Secret.EncryptionKeyDerivationConfig(
                    secretSize = 16,
                    iterations = 100000,
                    saltPrefix = ""
                ),
                authentication = Secret.AuthenticationKeyDerivationConfig(
                    enabled = true,
                    secretSize = 16,
                    iterations = 100000,
                    saltPrefix = ""
                )
            ),
            encryption = Secret.Config.EncryptionConfig(
                file = Secret.EncryptionSecretConfig(keySize = 16, ivSize = 12),
                metadata = Secret.EncryptionSecretConfig(keySize = 16, ivSize = 12),
                deviceSecret = Secret.EncryptionSecretConfig(keySize = 16, ivSize = 12)
            )
        )

        val Default = DeviceSecret(
            user = UUID.randomUUID(),
            device = UUID.randomUUID(),
            secret = "some-secret".toByteArray().toByteString(),
            target = DefaultConfig
        )
    }

    object State {
        val BackupOneState: BackupState = BackupState(
            operation = Operation.generateId(),
            definition = UUID.randomUUID(),
            started = Instant.now().truncatedTo(ChronoUnit.MILLIS),
            entities = BackupState.Entities(
                discovered = setOf(Metadata.FileOneMetadata.path.asPath()),
                unmatched = listOf("a", "b", "c"),
                examined = setOf(Metadata.FileTwoMetadata.path.asPath()),
                skipped = setOf(Metadata.FileTwoMetadata.path.asPath()),
                collected = mapOf(
                    Metadata.FileOneMetadata.path.asPath() to SourceEntity(
                        path = Metadata.FileOneMetadata.path.asPath(),
                        existingMetadata = Metadata.FileOneMetadata,
                        currentMetadata = Metadata.FileOneMetadata
                    )
                ),
                pending = mapOf(
                    Metadata.FileTwoMetadata.path.asPath() to BackupState.PendingSourceEntity(
                        expectedParts = 1,
                        processedParts = 2
                    )
                ),
                processed = mapOf(
                    Metadata.FileOneMetadata.path.asPath() to BackupState.ProcessedSourceEntity(
                        expectedParts = 1,
                        processedParts = 1,
                        metadata = Either.Left(Metadata.FileOneMetadata)
                    ),
                    Metadata.FileTwoMetadata.path.asPath() to BackupState.ProcessedSourceEntity(
                        expectedParts = 0,
                        processedParts = 0,
                        metadata = Either.Right(Metadata.FileTwoMetadata)
                    )
                ),
                failed = mapOf(Metadata.FileThreeMetadata.path.asPath() to "x")
            ),
            metadataCollected = Instant.now().truncatedTo(ChronoUnit.MILLIS),
            metadataPushed = Instant.now().truncatedTo(ChronoUnit.MILLIS),
            failures = listOf("y", "z"),
            completed = Instant.now().truncatedTo(ChronoUnit.MILLIS)
        )

        val BackupTwoState: BackupState = BackupState(
            operation = Operation.generateId(),
            definition = UUID.randomUUID(),
            started = Instant.now().truncatedTo(ChronoUnit.MILLIS),
            entities = BackupState.Entities.empty(),
            metadataCollected = null,
            metadataPushed = null,
            failures = emptyList(),
            completed = null
        )

        val RecoveryOneState: RecoveryState = RecoveryState(
            operation = Operation.generateId(),
            started = Instant.now().truncatedTo(ChronoUnit.MILLIS),
            entities = RecoveryState.Entities(
                examined = setOf(
                    Metadata.FileOneMetadata.path.asPath(),
                    Metadata.FileTwoMetadata.path.asPath(),
                    Metadata.FileThreeMetadata.path.asPath()
                ),
                collected = mapOf(
                    Metadata.FileOneMetadata.path.asPath() to TargetEntity(
                        path = Metadata.FileOneMetadata.path.asPath(),
                        destination = TargetEntity.Destination.Default,
                        existingMetadata = Metadata.FileOneMetadata,
                        currentMetadata = Metadata.FileOneMetadata
                    )
                ),
                pending = mapOf(
                    Metadata.FileThreeMetadata.path.asPath() to RecoveryState.PendingTargetEntity(
                        expectedParts = 3,
                        processedParts = 1
                    )
                ),
                processed = mapOf(
                    Metadata.FileOneMetadata.path.asPath() to RecoveryState.ProcessedTargetEntity(
                        expectedParts = 1,
                        processedParts = 1
                    )
                ),
                metadataApplied = setOf(Metadata.FileOneMetadata.path.asPath()),
                failed = mapOf(Metadata.FileThreeMetadata.path.asPath() to "x")
            ),
            failures = listOf("y", "z"),
            completed = Instant.now().truncatedTo(ChronoUnit.MILLIS)
        )

        val RecoveryTwoState: RecoveryState = RecoveryState(
            operation = Operation.generateId(),
            started = Instant.now().truncatedTo(ChronoUnit.MILLIS),
            entities = RecoveryState.Entities(
                examined = setOf(
                    Metadata.FileOneMetadata.path.asPath(),
                    Metadata.FileTwoMetadata.path.asPath(),
                    Metadata.FileThreeMetadata.path.asPath()
                ),
                collected = mapOf(
                    Metadata.FileOneMetadata.path.asPath() to TargetEntity(
                        path = Metadata.FileOneMetadata.path.asPath(),
                        destination = TargetEntity.Destination.Directory(
                            path = Metadata.FileOneMetadata.path.asPath(),
                            keepDefaultStructure = true
                        ),
                        existingMetadata = Metadata.FileOneMetadata,
                        currentMetadata = Metadata.FileOneMetadata
                    )
                ),
                pending = mapOf(
                    Metadata.FileThreeMetadata.path.asPath() to RecoveryState.PendingTargetEntity(
                        expectedParts = 3,
                        processedParts = 1
                    )
                ),
                processed = mapOf(
                    Metadata.FileOneMetadata.path.asPath() to RecoveryState.ProcessedTargetEntity(
                        expectedParts = 1,
                        processedParts = 1
                    )
                ),
                metadataApplied = setOf(Metadata.FileOneMetadata.path.asPath()),
                failed = mapOf(Metadata.FileThreeMetadata.path.asPath() to "x")
            ),
            failures = listOf("y", "z"),
            completed = Instant.now().truncatedTo(ChronoUnit.MILLIS)
        )
    }

    object Proto {
        object Metadata {
            val ActualFileOneMetadata: stasis.client_android.lib.model.proto.FileMetadata =
                stasis.client_android.lib.model.proto.FileMetadata(
                    path = Fixtures.Metadata.FileOneMetadata.path,
                    size = Fixtures.Metadata.FileOneMetadata.size,
                    link = Fixtures.Metadata.FileOneMetadata.link ?: "",
                    isHidden = Fixtures.Metadata.FileOneMetadata.isHidden,
                    created = Fixtures.Metadata.FileOneMetadata.created.epochSecond,
                    updated = Fixtures.Metadata.FileOneMetadata.updated.epochSecond,
                    owner = Fixtures.Metadata.FileOneMetadata.owner,
                    group = Fixtures.Metadata.FileOneMetadata.group,
                    permissions = Fixtures.Metadata.FileOneMetadata.permissions,
                    checksum = Fixtures.Metadata.FileOneMetadata.checksum.toByteArray().toByteString(),
                    crates = Fixtures.Metadata.FileOneMetadata.crates.map { (path, uuid) ->
                        path.toString() to stasis.client_android.lib.model.proto.Uuid(
                            mostSignificantBits = uuid.mostSignificantBits,
                            leastSignificantBits = uuid.leastSignificantBits
                        )
                    }.toMap(),
                    compression = "none"
                )

            val ActualFileTwoMetadata: stasis.client_android.lib.model.proto.FileMetadata =
                stasis.client_android.lib.model.proto.FileMetadata(
                    path = Fixtures.Metadata.FileTwoMetadata.path,
                    size = Fixtures.Metadata.FileTwoMetadata.size,
                    link = Fixtures.Metadata.FileTwoMetadata.link ?: "",
                    isHidden = Fixtures.Metadata.FileTwoMetadata.isHidden,
                    created = Fixtures.Metadata.FileTwoMetadata.created.epochSecond,
                    updated = Fixtures.Metadata.FileTwoMetadata.updated.epochSecond,
                    owner = Fixtures.Metadata.FileTwoMetadata.owner,
                    group = Fixtures.Metadata.FileTwoMetadata.group,
                    permissions = Fixtures.Metadata.FileTwoMetadata.permissions,
                    checksum = Fixtures.Metadata.FileTwoMetadata.checksum.toByteArray().toByteString(),
                    crates = Fixtures.Metadata.FileTwoMetadata.crates.map { (path, uuid) ->
                        path.toString() to stasis.client_android.lib.model.proto.Uuid(
                            mostSignificantBits = uuid.mostSignificantBits,
                            leastSignificantBits = uuid.leastSignificantBits
                        )
                    }.toMap(),
                    compression = "gzip"
                )

            val FileOneMetadataProto: stasis.client_android.lib.model.proto.EntityMetadata =
                stasis.client_android.lib.model.proto.EntityMetadata(file_ = ActualFileOneMetadata)

            val FileTwoMetadataProto: stasis.client_android.lib.model.proto.EntityMetadata =
                stasis.client_android.lib.model.proto.EntityMetadata(file_ = ActualFileTwoMetadata)

            val ActualDirectoryOneMetadata: stasis.client_android.lib.model.proto.DirectoryMetadata =
                stasis.client_android.lib.model.proto.DirectoryMetadata(
                    path = Fixtures.Metadata.DirectoryOneMetadata.path,
                    link = Fixtures.Metadata.DirectoryOneMetadata.link ?: "",
                    isHidden = Fixtures.Metadata.DirectoryOneMetadata.isHidden,
                    created = Fixtures.Metadata.DirectoryOneMetadata.created.epochSecond,
                    updated = Fixtures.Metadata.DirectoryOneMetadata.updated.epochSecond,
                    owner = Fixtures.Metadata.DirectoryOneMetadata.owner,
                    group = Fixtures.Metadata.DirectoryOneMetadata.group,
                    permissions = Fixtures.Metadata.DirectoryOneMetadata.permissions
                )

            val ActualDirectoryTwoMetadata: stasis.client_android.lib.model.proto.DirectoryMetadata =
                stasis.client_android.lib.model.proto.DirectoryMetadata(
                    path = Fixtures.Metadata.DirectoryTwoMetadata.path,
                    link = Fixtures.Metadata.DirectoryTwoMetadata.link ?: "",
                    isHidden = Fixtures.Metadata.DirectoryTwoMetadata.isHidden,
                    created = Fixtures.Metadata.DirectoryTwoMetadata.created.epochSecond,
                    updated = Fixtures.Metadata.DirectoryTwoMetadata.updated.epochSecond,
                    owner = Fixtures.Metadata.DirectoryTwoMetadata.owner,
                    group = Fixtures.Metadata.DirectoryTwoMetadata.group,
                    permissions = Fixtures.Metadata.DirectoryTwoMetadata.permissions
                )

            val DirectoryOneMetadataProto: stasis.client_android.lib.model.proto.EntityMetadata =
                stasis.client_android.lib.model.proto.EntityMetadata(directory = ActualDirectoryOneMetadata)

            val DirectoryTwoMetadataProto: stasis.client_android.lib.model.proto.EntityMetadata =
                stasis.client_android.lib.model.proto.EntityMetadata(directory = ActualDirectoryTwoMetadata)

            val EmptyMetadataProto: stasis.client_android.lib.model.proto.EntityMetadata =
                stasis.client_android.lib.model.proto.EntityMetadata()
        }

        object State {
            val BackupOneStateProto: stasis.client_android.lib.model.proto.BackupState =
                stasis.client_android.lib.model.proto.BackupState(
                    definition = Fixtures.State.BackupOneState.definition.toString(),
                    started = Fixtures.State.BackupOneState.started.toEpochMilli(),
                    entities = stasis.client_android.lib.model.proto.BackupEntities(
                        discovered = listOf(Fixtures.Metadata.FileOneMetadata.path.toString()),
                        unmatched = listOf("a", "b", "c"),
                        examined = listOf(Fixtures.Metadata.FileTwoMetadata.path.toString()),
                        skipped = listOf(Fixtures.Metadata.FileTwoMetadata.path.toString()),
                        collected = mapOf(
                            Fixtures.Metadata.FileOneMetadata.path.toString() to stasis.client_android.lib.model
                                .proto.SourceEntity(
                                    path = Fixtures.Metadata.FileOneMetadata.path.toString(),
                                    existingMetadata = Fixtures.Proto.Metadata.FileOneMetadataProto,
                                    currentMetadata = Fixtures.Proto.Metadata.FileOneMetadataProto
                                )
                        ),
                        pending = mapOf(
                            Fixtures.Metadata.FileTwoMetadata.path.toString() to stasis.client_android.lib.model
                                .proto.PendingSourceEntity(expectedParts = 1, processedParts = 2)
                        ),
                        processed = mapOf(
                            Fixtures.Metadata.FileOneMetadata.path.toString() to stasis.client_android.lib.model
                                .proto.ProcessedSourceEntity(
                                    expectedParts = 1,
                                    processedParts = 1,
                                    left = Fixtures.Proto.Metadata.FileOneMetadataProto
                                ),
                            Fixtures.Metadata.FileTwoMetadata.path.toString() to stasis.client_android.lib.model
                                .proto.ProcessedSourceEntity(
                                    expectedParts = 0,
                                    processedParts = 0,
                                    right = Fixtures.Proto.Metadata.FileTwoMetadataProto
                                )
                        ),
                        failed = mapOf(Fixtures.Metadata.FileThreeMetadata.path.toString() to "x")
                    ),
                    metadataCollected = Fixtures.State.BackupOneState.metadataCollected?.toEpochMilli(),
                    metadataPushed = Fixtures.State.BackupOneState.metadataPushed?.toEpochMilli(),
                    failures = listOf("y", "z"),
                    completed = Fixtures.State.BackupOneState.completed?.toEpochMilli()
                )

            val BackupTwoStateProto: stasis.client_android.lib.model.proto.BackupState =
                stasis.client_android.lib.model.proto.BackupState(
                    definition = Fixtures.State.BackupTwoState.definition.toString(),
                    started = Fixtures.State.BackupTwoState.started.toEpochMilli(),
                    entities = stasis.client_android.lib.model.proto.BackupEntities(),
                    metadataCollected = Fixtures.State.BackupTwoState.metadataCollected?.toEpochMilli(),
                    metadataPushed = Fixtures.State.BackupTwoState.metadataPushed?.toEpochMilli(),
                    failures = Fixtures.State.BackupTwoState.failures,
                    completed = Fixtures.State.BackupTwoState.completed?.toEpochMilli()
                )

            val RecoveryOneStateProto: stasis.client_android.lib.model.proto.RecoveryState =
                stasis.client_android.lib.model.proto.RecoveryState(
                    started = Fixtures.State.RecoveryOneState.started.toEpochMilli(),
                    entities = stasis.client_android.lib.model.proto.RecoveryEntities(
                        examined = listOf(
                            Fixtures.Metadata.FileOneMetadata.path,
                            Fixtures.Metadata.FileTwoMetadata.path,
                            Fixtures.Metadata.FileThreeMetadata.path
                        ).map { it.toString() },
                        collected = mapOf(
                            Fixtures.Metadata.FileOneMetadata.path.toString() to stasis.client_android.lib.model
                                .proto.TargetEntity(
                                    path = Fixtures.Metadata.FileOneMetadata.path.toString(),
                                    destination = stasis.client_android.lib.model.proto.TargetEntityDestination(
                                        default = stasis.client_android.lib.model.proto.TargetEntityDestinationDefault()
                                    ),
                                    existingMetadata = Fixtures.Proto.Metadata.FileOneMetadataProto,
                                    currentMetadata = Fixtures.Proto.Metadata.FileOneMetadataProto
                                )
                        ),
                        pending = mapOf(
                            Fixtures.Metadata.FileThreeMetadata.path.toString() to stasis.client_android.lib.model
                                .proto.PendingTargetEntity(expectedParts = 3, processedParts = 1)
                        ),
                        processed = mapOf(
                            Fixtures.Metadata.FileOneMetadata.path.toString() to stasis.client_android.lib.model
                                .proto.ProcessedTargetEntity(expectedParts = 1, processedParts = 1)
                        ),
                        metadataApplied = listOf(Fixtures.Metadata.FileOneMetadata.path.toString()),
                        failed = mapOf(Fixtures.Metadata.FileThreeMetadata.path.toString() to "x")
                    ),
                    failures = listOf("y", "z"),
                    completed = Fixtures.State.RecoveryOneState.completed?.toEpochMilli()
                )

            val RecoveryTwoStateProto: stasis.client_android.lib.model.proto.RecoveryState =
                stasis.client_android.lib.model.proto.RecoveryState(
                    started = Fixtures.State.RecoveryTwoState.started.toEpochMilli(),
                    entities = stasis.client_android.lib.model.proto.RecoveryEntities(
                        examined = listOf(
                            Fixtures.Metadata.FileOneMetadata.path,
                            Fixtures.Metadata.FileTwoMetadata.path,
                            Fixtures.Metadata.FileThreeMetadata.path
                        ).map { it.toString() },
                        collected = mapOf(
                            Fixtures.Metadata.FileOneMetadata.path.toString() to stasis.client_android.lib.model
                                .proto.TargetEntity(
                                    path = Fixtures.Metadata.FileOneMetadata.path.toString(),
                                    destination = stasis.client_android.lib.model.proto.TargetEntityDestination(
                                        directory = stasis.client_android.lib.model
                                            .proto.TargetEntityDestinationDirectory(
                                                path = Fixtures.Metadata.FileOneMetadata.path.toString(),
                                                keepDefaultStructure = true
                                            )
                                    ),
                                    existingMetadata = Fixtures.Proto.Metadata.FileOneMetadataProto,
                                    currentMetadata = Fixtures.Proto.Metadata.FileOneMetadataProto
                                )
                        ),
                        pending = mapOf(
                            Fixtures.Metadata.FileThreeMetadata.path.toString() to stasis.client_android.lib.model
                                .proto.PendingTargetEntity(expectedParts = 3, processedParts = 1)
                        ),
                        processed = mapOf(
                            Fixtures.Metadata.FileOneMetadata.path.toString() to stasis.client_android.lib.model
                                .proto.ProcessedTargetEntity(expectedParts = 1, processedParts = 1)
                        ),
                        metadataApplied = listOf(Fixtures.Metadata.FileOneMetadata.path.toString()),
                        failed = mapOf(Fixtures.Metadata.FileThreeMetadata.path.toString() to "x")
                    ),
                    failures = listOf("y", "z"),
                    completed = Fixtures.State.RecoveryTwoState.completed?.toEpochMilli()
                )
        }
    }
}
