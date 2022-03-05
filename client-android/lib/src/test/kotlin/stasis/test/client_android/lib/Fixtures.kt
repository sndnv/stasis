package stasis.test.client_android.lib

import com.squareup.wire.Instant
import okio.ByteString.Companion.toByteString
import stasis.client_android.lib.encryption.secrets.DeviceSecret
import stasis.client_android.lib.encryption.secrets.Secret
import stasis.client_android.lib.model.EntityMetadata
import stasis.client_android.lib.model.server.datasets.DatasetDefinition
import stasis.client_android.lib.model.server.datasets.DatasetEntry
import java.math.BigInteger
import java.nio.file.Paths
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.UUID

object Fixtures {
    object Metadata {
        val FileOneMetadata = EntityMetadata.File(
            path = Paths.get("/tmp/file/one"),
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
                Paths.get("/tmp/file/one_0") to java.util.UUID.fromString("329efbeb-80a3-42b8-b1dc-79bc0fea7bca")
            )
        )

        val FileTwoMetadata = EntityMetadata.File(
            path = Paths.get("/tmp/file/two"),
            size = 2,
            link = Paths.get("/tmp/file/three"),
            isHidden = false,
            created = Instant.MAX.truncatedTo(ChronoUnit.SECONDS),
            updated = Instant.MIN.truncatedTo(ChronoUnit.SECONDS),
            owner = "root",
            group = "root",
            permissions = "rwxrwxrwx",
            checksum = BigInteger("42"),
            crates = mapOf(
                Paths.get("/tmp/file/two_0") to java.util.UUID.fromString("e672a956-1a95-4304-8af0-9418f0e43cba")
            )
        )

        val FileThreeMetadata = EntityMetadata.File(
            path = Paths.get("/tmp/file/four"),
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
                Paths.get("/tmp/file/four_0") to java.util.UUID.fromString("7c98df29-a544-41e5-95ac-463987894fac")
            )
        )

        val DirectoryOneMetadata = EntityMetadata.Directory(
            path = Paths.get("/tmp/directory/one"),
            link = null,
            isHidden = false,
            created = Instant.MIN.truncatedTo(ChronoUnit.SECONDS),
            updated = Instant.MAX.truncatedTo(ChronoUnit.SECONDS),
            owner = "root",
            group = "root",
            permissions = "rwxrwxrwx"
        )

        val DirectoryTwoMetadata = EntityMetadata.Directory(
            path = Paths.get("/tmp/directory/two"),
            link = Paths.get("/tmp/file/three"),
            isHidden = false,
            created = Instant.MAX.truncatedTo(ChronoUnit.SECONDS),
            updated = Instant.MIN.truncatedTo(ChronoUnit.SECONDS),
            owner = "root",
            group = "root",
            permissions = "rwxrwxrwx"
        )

        val DirectoryThreeMetadata = EntityMetadata.Directory(
            path = Paths.get("/tmp/directory/four"),
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
            )
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
                encryption = Secret.KeyDerivationConfig(secretSize = 16, iterations = 100000, saltPrefix = ""),
                authentication = Secret.KeyDerivationConfig(secretSize = 16, iterations = 100000, saltPrefix = "")
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
}
