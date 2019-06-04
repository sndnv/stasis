package stasis.test.specs.unit.client

import java.nio.file.Paths
import java.time.Instant
import java.time.temporal.ChronoUnit

import akka.util.ByteString
import stasis.client.encryption.secrets.{DeviceSecret, Secret}
import stasis.client.model.FileMetadata
import stasis.shared.model.datasets.DatasetDefinition
import stasis.shared.model.devices.Device
import stasis.shared.model.users.User

import scala.concurrent.duration._

object Fixtures {
  object Metadata {
    final val FileOneMetadata = FileMetadata(
      path = Paths.get("/tmp/file/one"),
      size = 1,
      link = None,
      isHidden = false,
      created = Instant.MIN.truncatedTo(ChronoUnit.SECONDS),
      updated = Instant.MAX.truncatedTo(ChronoUnit.SECONDS),
      owner = "root",
      group = "root",
      permissions = "rwx",
      checksum = 1,
      crate = java.util.UUID.fromString("329efbeb-80a3-42b8-b1dc-79bc0fea7bca")
    )

    final val FileTwoMetadata = FileMetadata(
      path = Paths.get("/tmp/file/two"),
      size = 2,
      link = Some(Paths.get("/tmp/file/three")),
      isHidden = false,
      created = Instant.MAX.truncatedTo(ChronoUnit.SECONDS),
      updated = Instant.MIN.truncatedTo(ChronoUnit.SECONDS),
      owner = "root",
      group = "root",
      permissions = "xwr",
      checksum = 42,
      crate = java.util.UUID.fromString("e672a956-1a95-4304-8af0-9418f0e43cba")
    )

    final val FileThreeMetadata = FileMetadata(
      path = Paths.get("/tmp/file/four"),
      size = 2,
      link = None,
      isHidden = false,
      created = Instant.MAX.truncatedTo(ChronoUnit.SECONDS),
      updated = Instant.MIN.truncatedTo(ChronoUnit.SECONDS),
      owner = "root",
      group = "root",
      permissions = "xwr",
      checksum = 0,
      crate = java.util.UUID.fromString("7c98df29-a544-41e5-95ac-463987894fac")
    )
  }

  object Datasets {
    final val Default = DatasetDefinition(
      id = DatasetDefinition.generateId(),
      device = Device.generateId(),
      schedule = None,
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

  object Secrets {
    final val DefaultConfig = Secret.Config(
      derivation = Secret.DerivationConfig(
        encryption = Secret.KeyDerivationConfig(secretSize = 1, iterations = 1, saltPrefix = ""),
        authentication = Secret.KeyDerivationConfig(secretSize = 1, iterations = 1, saltPrefix = "")
      ),
      encryption = Secret.EncryptionConfig(
        file = Secret.EncryptionSecretConfig(keySize = 1, ivSize = 1),
        metadata = Secret.EncryptionSecretConfig(keySize = 1, ivSize = 1),
        deviceSecret = Secret.EncryptionSecretConfig(keySize = 1, ivSize = 1)
      )
    )

    final val Default = DeviceSecret(
      user = User.generateId(),
      device = Device.generateId(),
      secret = ByteString("some-secret")
    )(DefaultConfig)
  }
}
