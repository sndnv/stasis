package stasis.test.specs.unit.client

import akka.util.ByteString
import stasis.client.encryption.secrets.DeviceSecret
import stasis.client.model.EntityMetadata
import stasis.core.packaging.Crate
import stasis.shared.model.datasets.{DatasetDefinition, DatasetEntry}
import stasis.shared.model.devices.Device
import stasis.shared.model.users.User
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
      )
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
      )
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
      )
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
        encryption = SecretsConfig.Derivation.Encryption(secretSize = 16, iterations = 100000, saltPrefix = ""),
        authentication = SecretsConfig.Derivation.Authentication(secretSize = 16, iterations = 100000, saltPrefix = "")
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
}
