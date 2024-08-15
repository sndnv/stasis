package stasis.test.specs.unit.client

import org.apache.pekko.util.ByteString
import stasis.client.encryption.secrets.DeviceSecret
import stasis.client.model.{proto, EntityMetadata, SourceEntity, TargetEntity}
import stasis.client.tracking.state.{BackupState, RecoveryState}
import stasis.client.tracking.state.BackupState.{PendingSourceEntity, ProcessedSourceEntity}
import stasis.client.tracking.state.RecoveryState.{PendingTargetEntity, ProcessedTargetEntity}
import stasis.core.packaging.Crate
import stasis.shared.model.datasets.{DatasetDefinition, DatasetEntry}
import stasis.shared.model.devices.Device
import stasis.shared.model.users.User
import stasis.shared.ops.Operation
import stasis.shared.secrets.SecretsConfig

import java.nio.file.Paths
import java.time.Instant
import java.time.temporal.ChronoUnit
import scala.concurrent.duration._

object Fixtures {
  object Metadata {
    final lazy val FileOneMetadata = EntityMetadata.File(
      path = Paths.get("/tmp/file/one"),
      size = 1,
      link = None,
      isHidden = false,
      created = Instant.MIN.truncatedTo(ChronoUnit.SECONDS),
      updated = Instant.MAX.truncatedTo(ChronoUnit.SECONDS),
      owner = "root",
      group = "root",
      permissions = "rwxrwxrwx",
      checksum = 1,
      crates = Map(
        Paths.get("/tmp/file/one_0") -> java.util.UUID.fromString("329efbeb-80a3-42b8-b1dc-79bc0fea7bca")
      ),
      compression = "none"
    )

    final lazy val FileTwoMetadata = EntityMetadata.File(
      path = Paths.get("/tmp/file/two"),
      size = 2,
      link = Some(Paths.get("/tmp/file/three")),
      isHidden = false,
      created = Instant.MAX.truncatedTo(ChronoUnit.SECONDS),
      updated = Instant.MIN.truncatedTo(ChronoUnit.SECONDS),
      owner = "root",
      group = "root",
      permissions = "rwxrwxrwx",
      checksum = 42,
      crates = Map(
        Paths.get("/tmp/file/two_0") -> java.util.UUID.fromString("e672a956-1a95-4304-8af0-9418f0e43cba")
      ),
      compression = "gzip"
    )

    final lazy val FileThreeMetadata = EntityMetadata.File(
      path = Paths.get("/tmp/file/four"),
      size = 2,
      link = None,
      isHidden = false,
      created = Instant.MAX.truncatedTo(ChronoUnit.SECONDS),
      updated = Instant.MIN.truncatedTo(ChronoUnit.SECONDS),
      owner = "root",
      group = "root",
      permissions = "rwxrwxrwx",
      checksum = 0,
      crates = Map(
        Paths.get("/tmp/file/four_0") -> java.util.UUID.fromString("7c98df29-a544-41e5-95ac-463987894fac")
      ),
      compression = "deflate"
    )

    final lazy val DirectoryOneMetadata = EntityMetadata.Directory(
      path = Paths.get("/tmp/directory/one"),
      link = None,
      isHidden = false,
      created = Instant.MIN.truncatedTo(ChronoUnit.SECONDS),
      updated = Instant.MAX.truncatedTo(ChronoUnit.SECONDS),
      owner = "root",
      group = "root",
      permissions = "rwxrwxrwx"
    )

    final lazy val DirectoryTwoMetadata = EntityMetadata.Directory(
      path = Paths.get("/tmp/directory/two"),
      link = Some(Paths.get("/tmp/file/three")),
      isHidden = false,
      created = Instant.MAX.truncatedTo(ChronoUnit.SECONDS),
      updated = Instant.MIN.truncatedTo(ChronoUnit.SECONDS),
      owner = "root",
      group = "root",
      permissions = "rwxrwxrwx"
    )

    final lazy val DirectoryThreeMetadata = EntityMetadata.Directory(
      path = Paths.get("/tmp/directory/four"),
      link = None,
      isHidden = false,
      created = Instant.MAX.truncatedTo(ChronoUnit.SECONDS),
      updated = Instant.MIN.truncatedTo(ChronoUnit.SECONDS),
      owner = "root",
      group = "root",
      permissions = "rwxrwxrwx"
    )
  }

  object Datasets {
    final lazy val Default = DatasetDefinition(
      id = DatasetDefinition.generateId(),
      info = "default-test-definition",
      device = Device.generateId(),
      redundantCopies = 1,
      existingVersions = DatasetDefinition.Retention(
        policy = DatasetDefinition.Retention.Policy.All,
        duration = 1.second
      ),
      removedVersions = DatasetDefinition.Retention(
        policy = DatasetDefinition.Retention.Policy.All,
        duration = 1.second
      )
    )
  }

  object Entries {
    final lazy val Default = DatasetEntry(
      id = DatasetEntry.generateId(),
      definition = Datasets.Default.id,
      device = Datasets.Default.device,
      data = Set(
        Metadata.FileOneMetadata.crates.values ++
          Metadata.FileTwoMetadata.crates.values ++
          Metadata.FileThreeMetadata.crates.values
      ).flatten,
      metadata = Crate.generateId(),
      created = Instant.now()
    )
  }

  object Secrets {
    final lazy val DefaultConfig = SecretsConfig(
      derivation = SecretsConfig.Derivation(
        encryption = SecretsConfig.Derivation.Encryption(
          secretSize = 16,
          iterations = 100000,
          saltPrefix = ""
        ),
        authentication = SecretsConfig.Derivation.Authentication(
          enabled = true,
          secretSize = 16,
          iterations = 100000,
          saltPrefix = ""
        )
      ),
      encryption = SecretsConfig.Encryption(
        file = SecretsConfig.Encryption.File(keySize = 16, ivSize = 12),
        metadata = SecretsConfig.Encryption.Metadata(keySize = 16, ivSize = 12),
        deviceSecret = SecretsConfig.Encryption.DeviceSecret(keySize = 16, ivSize = 12)
      )
    )

    final lazy val Default = DeviceSecret(
      user = User.generateId(),
      device = Device.generateId(),
      secret = ByteString("some-secret")
    )(DefaultConfig)
  }

  object State {
    final lazy val BackupOneState: BackupState = BackupState(
      operation = Operation.generateId(),
      definition = DatasetDefinition.generateId(),
      started = Instant.now().truncatedTo(ChronoUnit.MILLIS),
      entities = BackupState.Entities(
        discovered = Set(Metadata.FileOneMetadata.path),
        unmatched = Seq("a", "b", "c"),
        examined = Set(Metadata.FileTwoMetadata.path),
        skipped = Set(Metadata.FileTwoMetadata.path),
        collected = Map(
          Metadata.FileOneMetadata.path -> SourceEntity(
            path = Metadata.FileOneMetadata.path,
            existingMetadata = Some(Metadata.FileOneMetadata),
            currentMetadata = Metadata.FileOneMetadata
          )
        ),
        pending = Map(Metadata.FileTwoMetadata.path -> PendingSourceEntity(expectedParts = 1, processedParts = 2)),
        processed = Map(
          Metadata.FileOneMetadata.path -> ProcessedSourceEntity(
            expectedParts = 1,
            processedParts = 1,
            metadata = Left(Fixtures.Metadata.FileOneMetadata)
          ),
          Metadata.FileTwoMetadata.path -> ProcessedSourceEntity(
            expectedParts = 0,
            processedParts = 0,
            metadata = Right(Fixtures.Metadata.FileTwoMetadata)
          )
        ),
        failed = Map(Metadata.FileThreeMetadata.path -> "x")
      ),
      metadataCollected = Some(Instant.now().truncatedTo(ChronoUnit.MILLIS)),
      metadataPushed = Some(Instant.now().truncatedTo(ChronoUnit.MILLIS)),
      failures = Seq("y", "z"),
      completed = Some(Instant.now().truncatedTo(ChronoUnit.MILLIS))
    )

    final lazy val BackupTwoState: BackupState = BackupState(
      operation = Operation.generateId(),
      definition = DatasetDefinition.generateId(),
      started = Instant.now().truncatedTo(ChronoUnit.MILLIS),
      entities = BackupState.Entities.empty,
      metadataCollected = None,
      metadataPushed = None,
      failures = Seq.empty,
      completed = None
    )

    final lazy val RecoveryOneState: RecoveryState = RecoveryState(
      operation = Operation.generateId(),
      started = Instant.now().truncatedTo(ChronoUnit.MILLIS),
      entities = RecoveryState.Entities(
        examined = Set(Metadata.FileOneMetadata.path, Metadata.FileTwoMetadata.path, Metadata.FileThreeMetadata.path),
        collected = Map(
          Metadata.FileOneMetadata.path -> TargetEntity(
            path = Metadata.FileOneMetadata.path,
            destination = TargetEntity.Destination.Default,
            existingMetadata = Metadata.FileOneMetadata,
            currentMetadata = Some(Metadata.FileOneMetadata)
          )
        ),
        pending = Map(Metadata.FileThreeMetadata.path -> PendingTargetEntity(expectedParts = 3, processedParts = 1)),
        processed = Map(Metadata.FileOneMetadata.path -> ProcessedTargetEntity(expectedParts = 1, processedParts = 1)),
        metadataApplied = Set(Metadata.FileOneMetadata.path),
        failed = Map(Metadata.FileThreeMetadata.path -> "x")
      ),
      failures = Seq("y", "z"),
      completed = Some(Instant.now().truncatedTo(ChronoUnit.MILLIS))
    )

    final lazy val RecoveryTwoState: RecoveryState = RecoveryState(
      operation = Operation.generateId(),
      started = Instant.now().truncatedTo(ChronoUnit.MILLIS),
      entities = RecoveryState.Entities(
        examined = Set(Metadata.FileOneMetadata.path, Metadata.FileTwoMetadata.path, Metadata.FileThreeMetadata.path),
        collected = Map(
          Metadata.FileOneMetadata.path -> TargetEntity(
            path = Metadata.FileOneMetadata.path,
            destination = TargetEntity.Destination.Directory(
              path = Metadata.FileOneMetadata.path,
              keepDefaultStructure = true
            ),
            existingMetadata = Metadata.FileOneMetadata,
            currentMetadata = Some(Metadata.FileOneMetadata)
          )
        ),
        pending = Map(Metadata.FileThreeMetadata.path -> PendingTargetEntity(expectedParts = 3, processedParts = 1)),
        processed = Map(Metadata.FileOneMetadata.path -> ProcessedTargetEntity(expectedParts = 1, processedParts = 1)),
        metadataApplied = Set(Metadata.FileOneMetadata.path),
        failed = Map(Metadata.FileThreeMetadata.path -> "x")
      ),
      failures = Seq("y", "z"),
      completed = Some(Instant.now().truncatedTo(ChronoUnit.MILLIS))
    )
  }

  object Proto {
    object Metadata {
      final lazy val ActualFileOneMetadata: proto.metadata.FileMetadata = proto.metadata.FileMetadata(
        path = Fixtures.Metadata.FileOneMetadata.path.toAbsolutePath.toString,
        size = Fixtures.Metadata.FileOneMetadata.size,
        link = Fixtures.Metadata.FileOneMetadata.link.fold("")(_.toAbsolutePath.toString),
        isHidden = Fixtures.Metadata.FileOneMetadata.isHidden,
        created = Fixtures.Metadata.FileOneMetadata.created.getEpochSecond,
        updated = Fixtures.Metadata.FileOneMetadata.updated.getEpochSecond,
        owner = Fixtures.Metadata.FileOneMetadata.owner,
        group = Fixtures.Metadata.FileOneMetadata.group,
        permissions = Fixtures.Metadata.FileOneMetadata.permissions,
        checksum = com.google.protobuf.ByteString.copyFrom(Fixtures.Metadata.FileOneMetadata.checksum.toByteArray),
        crates = Fixtures.Metadata.FileOneMetadata.crates.map { case (path, uuid) =>
          (
            path.toString,
            proto.metadata.Uuid(
              mostSignificantBits = uuid.getMostSignificantBits,
              leastSignificantBits = uuid.getLeastSignificantBits
            )
          )
        },
        compression = Fixtures.Metadata.FileOneMetadata.compression
      )

      final lazy val ActualFileTwoMetadata: proto.metadata.FileMetadata = proto.metadata.FileMetadata(
        path = Fixtures.Metadata.FileTwoMetadata.path.toAbsolutePath.toString,
        size = Fixtures.Metadata.FileTwoMetadata.size,
        link = Fixtures.Metadata.FileTwoMetadata.link.fold("")(_.toAbsolutePath.toString),
        isHidden = Fixtures.Metadata.FileTwoMetadata.isHidden,
        created = Fixtures.Metadata.FileTwoMetadata.created.getEpochSecond,
        updated = Fixtures.Metadata.FileTwoMetadata.updated.getEpochSecond,
        owner = Fixtures.Metadata.FileTwoMetadata.owner,
        group = Fixtures.Metadata.FileTwoMetadata.group,
        permissions = Fixtures.Metadata.FileTwoMetadata.permissions,
        checksum = com.google.protobuf.ByteString.copyFrom(Fixtures.Metadata.FileTwoMetadata.checksum.toByteArray),
        crates = Fixtures.Metadata.FileTwoMetadata.crates.map { case (path, uuid) =>
          (
            path.toString,
            proto.metadata.Uuid(
              mostSignificantBits = uuid.getMostSignificantBits,
              leastSignificantBits = uuid.getLeastSignificantBits
            )
          )
        },
        compression = Fixtures.Metadata.FileTwoMetadata.compression
      )

      final lazy val FileOneMetadataProto: proto.metadata.EntityMetadata = proto.metadata.EntityMetadata(
        entity = proto.metadata.EntityMetadata.Entity.File(value = ActualFileOneMetadata)
      )

      val FileTwoMetadataProto: proto.metadata.EntityMetadata = proto.metadata.EntityMetadata(
        entity = proto.metadata.EntityMetadata.Entity.File(value = ActualFileTwoMetadata)
      )

      final lazy val ActualDirectoryOneMetadata: proto.metadata.DirectoryMetadata = proto.metadata.DirectoryMetadata(
        path = Fixtures.Metadata.DirectoryOneMetadata.path.toAbsolutePath.toString,
        link = Fixtures.Metadata.DirectoryOneMetadata.link.fold("")(_.toAbsolutePath.toString),
        isHidden = Fixtures.Metadata.DirectoryOneMetadata.isHidden,
        created = Fixtures.Metadata.DirectoryOneMetadata.created.getEpochSecond,
        updated = Fixtures.Metadata.DirectoryOneMetadata.updated.getEpochSecond,
        owner = Fixtures.Metadata.DirectoryOneMetadata.owner,
        group = Fixtures.Metadata.DirectoryOneMetadata.group,
        permissions = Fixtures.Metadata.DirectoryOneMetadata.permissions
      )

      final lazy val ActualDirectoryTwoMetadata: proto.metadata.DirectoryMetadata = proto.metadata.DirectoryMetadata(
        path = Fixtures.Metadata.DirectoryTwoMetadata.path.toAbsolutePath.toString,
        link = Fixtures.Metadata.DirectoryTwoMetadata.link.fold("")(_.toAbsolutePath.toString),
        isHidden = Fixtures.Metadata.DirectoryTwoMetadata.isHidden,
        created = Fixtures.Metadata.DirectoryTwoMetadata.created.getEpochSecond,
        updated = Fixtures.Metadata.DirectoryTwoMetadata.updated.getEpochSecond,
        owner = Fixtures.Metadata.DirectoryTwoMetadata.owner,
        group = Fixtures.Metadata.DirectoryTwoMetadata.group,
        permissions = Fixtures.Metadata.DirectoryTwoMetadata.permissions
      )

      final lazy val DirectoryOneMetadataProto: proto.metadata.EntityMetadata = proto.metadata.EntityMetadata(
        entity = proto.metadata.EntityMetadata.Entity.Directory(
          value = ActualDirectoryOneMetadata
        )
      )

      final lazy val DirectoryTwoMetadataProto: proto.metadata.EntityMetadata = proto.metadata.EntityMetadata(
        entity = proto.metadata.EntityMetadata.Entity.Directory(
          value = ActualDirectoryTwoMetadata
        )
      )

      final lazy val EmptyMetadataProto: proto.metadata.EntityMetadata = proto.metadata.EntityMetadata(
        entity = proto.metadata.EntityMetadata.Entity.Empty
      )
    }

    object State {
      final lazy val BackupOneStateProto: proto.state.BackupState = proto.state.BackupState(
        definition = Fixtures.State.BackupOneState.definition.toString,
        started = Fixtures.State.BackupOneState.started.toEpochMilli,
        entities = Some(
          proto.state.BackupEntities(
            discovered = Seq(Fixtures.Metadata.FileOneMetadata.path.toString),
            unmatched = Seq("a", "b", "c"),
            examined = Seq(Fixtures.Metadata.FileTwoMetadata.path.toString),
            skipped = Seq(Fixtures.Metadata.FileTwoMetadata.path.toString),
            collected = Map(
              Fixtures.Metadata.FileOneMetadata.path.toString -> proto.state.SourceEntity(
                path = Fixtures.Metadata.FileOneMetadata.path.toString,
                existingMetadata = Some(Fixtures.Proto.Metadata.FileOneMetadataProto),
                currentMetadata = Some(Fixtures.Proto.Metadata.FileOneMetadataProto)
              )
            ),
            pending = Map(
              Fixtures.Metadata.FileTwoMetadata.path.toString -> proto.state
                .PendingSourceEntity(expectedParts = 1, processedParts = 2)
            ),
            processed = Map(
              Fixtures.Metadata.FileOneMetadata.path.toString -> proto.state.ProcessedSourceEntity(
                expectedParts = 1,
                processedParts = 1,
                metadata = proto.state.ProcessedSourceEntity.Metadata.Left(Fixtures.Proto.Metadata.FileOneMetadataProto)
              ),
              Fixtures.Metadata.FileTwoMetadata.path.toString -> proto.state.ProcessedSourceEntity(
                expectedParts = 0,
                processedParts = 0,
                metadata = proto.state.ProcessedSourceEntity.Metadata.Right(Fixtures.Proto.Metadata.FileTwoMetadataProto)
              )
            ),
            failed = Map(Fixtures.Metadata.FileThreeMetadata.path.toString -> "x")
          )
        ),
        metadataCollected = Fixtures.State.BackupOneState.metadataCollected.map(_.toEpochMilli),
        metadataPushed = Fixtures.State.BackupOneState.metadataPushed.map(_.toEpochMilli),
        failures = Seq("y", "z"),
        completed = Fixtures.State.BackupOneState.completed.map(_.toEpochMilli)
      )

      final lazy val BackupTwoStateProto: proto.state.BackupState = proto.state.BackupState(
        definition = Fixtures.State.BackupTwoState.definition.toString,
        started = Fixtures.State.BackupTwoState.started.toEpochMilli,
        entities = Some(proto.state.BackupEntities()),
        metadataCollected = Fixtures.State.BackupTwoState.metadataCollected.map(_.toEpochMilli),
        metadataPushed = Fixtures.State.BackupTwoState.metadataPushed.map(_.toEpochMilli),
        failures = Fixtures.State.BackupTwoState.failures,
        completed = Fixtures.State.BackupTwoState.completed.map(_.toEpochMilli)
      )

      final lazy val RecoveryOneStateProto: proto.state.RecoveryState = proto.state.RecoveryState(
        started = Fixtures.State.RecoveryOneState.started.toEpochMilli,
        entities = Some(
          proto.state.RecoveryEntities(
            examined = Seq(
              Fixtures.Metadata.FileOneMetadata.path,
              Fixtures.Metadata.FileTwoMetadata.path,
              Fixtures.Metadata.FileThreeMetadata.path
            ).map(_.toString),
            collected = Map(
              Fixtures.Metadata.FileOneMetadata.path.toString -> proto.state.TargetEntity(
                path = Fixtures.Metadata.FileOneMetadata.path.toString,
                destination = proto.state.TargetEntityDestinationDefault(),
                existingMetadata = Some(Fixtures.Proto.Metadata.FileOneMetadataProto),
                currentMetadata = Some(Fixtures.Proto.Metadata.FileOneMetadataProto)
              )
            ),
            pending = Map(
              Fixtures.Metadata.FileThreeMetadata.path.toString -> proto.state
                .PendingTargetEntity(expectedParts = 3, processedParts = 1)
            ),
            processed = Map(
              Fixtures.Metadata.FileOneMetadata.path.toString -> proto.state
                .ProcessedTargetEntity(expectedParts = 1, processedParts = 1)
            ),
            metadataApplied = Seq(Fixtures.Metadata.FileOneMetadata.path.toString),
            failed = Map(Fixtures.Metadata.FileThreeMetadata.path.toString -> "x")
          )
        ),
        failures = Seq("y", "z"),
        completed = Fixtures.State.RecoveryOneState.completed.map(_.toEpochMilli)
      )

      final lazy val RecoveryTwoStateProto: proto.state.RecoveryState = proto.state.RecoveryState(
        started = Fixtures.State.RecoveryTwoState.started.toEpochMilli,
        entities = Some(
          proto.state.RecoveryEntities(
            examined = Seq(
              Fixtures.Metadata.FileOneMetadata.path,
              Fixtures.Metadata.FileTwoMetadata.path,
              Fixtures.Metadata.FileThreeMetadata.path
            ).map(_.toString),
            collected = Map(
              Fixtures.Metadata.FileOneMetadata.path.toString -> proto.state.TargetEntity(
                path = Fixtures.Metadata.FileOneMetadata.path.toString,
                destination = proto.state.TargetEntityDestinationDirectory(
                  path = Fixtures.Metadata.FileOneMetadata.path.toString,
                  keepDefaultStructure = true
                ),
                existingMetadata = Some(Fixtures.Proto.Metadata.FileOneMetadataProto),
                currentMetadata = Some(Fixtures.Proto.Metadata.FileOneMetadataProto)
              )
            ),
            pending = Map(
              Fixtures.Metadata.FileThreeMetadata.path.toString -> proto.state
                .PendingTargetEntity(expectedParts = 3, processedParts = 1)
            ),
            processed = Map(
              Fixtures.Metadata.FileOneMetadata.path.toString -> proto.state
                .ProcessedTargetEntity(expectedParts = 1, processedParts = 1)
            ),
            metadataApplied = Seq(Fixtures.Metadata.FileOneMetadata.path.toString),
            failed = Map(Fixtures.Metadata.FileThreeMetadata.path.toString -> "x")
          )
        ),
        failures = Seq("y", "z"),
        completed = Fixtures.State.RecoveryTwoState.completed.map(_.toEpochMilli)
      )
    }
  }
}
