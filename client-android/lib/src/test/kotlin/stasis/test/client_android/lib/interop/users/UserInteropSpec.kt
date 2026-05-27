package stasis.test.client_android.lib.interop.users

import stasis.client_android.lib.model.server.users.User
import stasis.test.client_android.lib.interop.InteropSpec
import java.math.BigInteger
import java.time.Duration
import java.time.Instant
import java.util.UUID

class UserInteropSpec : InteropSpec({
    "User interop" should {
        "decode and re-encode a user" {
            assert(
                domain = "users",
                resource = "User",
                matches = User(
                    id = UUID.fromString("7a1c27c2-0b3c-4f23-a68b-814084bfee7d"),
                    salt = "test-salt-value",
                    active = true,
                    limits = User.Limits(
                        maxDevices = 10L,
                        maxCrates = 100L,
                        maxStorage = BigInteger.valueOf(1099511627776L),
                        maxStoragePerCrate = BigInteger.valueOf(10737418240L),
                        maxRetention = Duration.ofSeconds(7776000),
                        minRetention = Duration.ofSeconds(86400)
                    ),
                    permissions = setOf("view-self"),
                    created = Instant.parse("2026-03-01T12:00:00Z"),
                    updated = Instant.parse("2026-03-02T13:00:00Z")
                )
            )
        }

        "decode and re-encode a user with null limits" {
            assert(
                domain = "users",
                resource = "User.null-limits",
                matches = User(
                    id = UUID.fromString("7a1c27c2-0b3c-4f23-a68b-814084bfee7d"),
                    salt = "test-salt-value",
                    active = true,
                    limits = null,
                    permissions = setOf("view-self"),
                    created = Instant.parse("2026-03-01T12:00:00Z"),
                    updated = Instant.parse("2026-03-02T13:00:00Z")
                )
            )
        }

        "decode and re-encode a user without limits" {
            assert(
                domain = "users",
                resource = "User.no-limits",
                matches = User(
                    id = UUID.fromString("7a1c27c2-0b3c-4f23-a68b-814084bfee7d"),
                    salt = "test-salt-value",
                    active = true,
                    limits = null,
                    permissions = setOf("view-self"),
                    created = Instant.parse("2026-03-01T12:00:00Z"),
                    updated = Instant.parse("2026-03-02T13:00:00Z")
                )
            )
        }
    }
})
