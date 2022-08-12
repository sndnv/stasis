package stasis.test.client_android

import com.squareup.wire.Instant
import stasis.client_android.lib.model.EntityMetadata
import java.math.BigInteger
import java.nio.file.Paths
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
                Paths.get("/tmp/file/one_0") to UUID.fromString("329efbeb-80a3-42b8-b1dc-79bc0fea7bca")
            ),
            compression = "none"
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
                Paths.get("/tmp/file/two_0") to UUID.fromString("e672a956-1a95-4304-8af0-9418f0e43cba")
            ),
            compression = "gzip"
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
                Paths.get("/tmp/file/four_0") to UUID.fromString("7c98df29-a544-41e5-95ac-463987894fac")
            ),
            compression = "deflate"
        )
    }
}
