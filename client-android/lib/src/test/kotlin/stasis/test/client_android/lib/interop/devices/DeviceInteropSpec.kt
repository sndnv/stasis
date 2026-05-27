package stasis.test.client_android.lib.interop.devices

import stasis.client_android.lib.model.server.devices.Device
import stasis.test.client_android.lib.interop.InteropSpec
import java.math.BigInteger
import java.time.Duration
import java.time.Instant
import java.util.UUID

class DeviceInteropSpec : InteropSpec({
    "Device interop" should {
        "decode and re-encode a device" {
            assert(
                domain = "devices",
                resource = "Device",
                matches = Device(
                    id = UUID.fromString("f1e1ed1a-7dc2-4e9c-89fd-bab0ffbaa997"),
                    name = "test-device",
                    node = UUID.fromString("a9b7d0fb-3751-48fc-8706-9176eca571f1"),
                    owner = UUID.fromString("7a1c27c2-0b3c-4f23-a68b-814084bfee7d"),
                    active = true,
                    limits = Device.Limits(
                        maxCrates = 100L,
                        maxStorage = BigInteger.valueOf(1099511627776L),
                        maxStoragePerCrate = BigInteger.valueOf(10737418240L),
                        maxRetention = Duration.ofSeconds(7776000),
                        minRetention = Duration.ofSeconds(86400)
                    ),
                    created = Instant.parse("2026-03-01T12:00:00Z"),
                    updated = Instant.parse("2026-03-02T13:00:00Z")
                )
            )
        }

        "decode and re-encode a device with null limits" {
            assert(
                domain = "devices",
                resource = "Device.null-limits",
                matches = Device(
                    id = UUID.fromString("f1e1ed1a-7dc2-4e9c-89fd-bab0ffbaa997"),
                    name = "test-device",
                    node = UUID.fromString("a9b7d0fb-3751-48fc-8706-9176eca571f1"),
                    owner = UUID.fromString("7a1c27c2-0b3c-4f23-a68b-814084bfee7d"),
                    active = true,
                    limits = null,
                    created = Instant.parse("2026-03-01T12:00:00Z"),
                    updated = Instant.parse("2026-03-02T13:00:00Z")
                )
            )
        }

        "decode and re-encode a device without limits" {
            assert(
                domain = "devices",
                resource = "Device.no-limits",
                matches = Device(
                    id = UUID.fromString("f1e1ed1a-7dc2-4e9c-89fd-bab0ffbaa997"),
                    name = "test-device",
                    node = UUID.fromString("a9b7d0fb-3751-48fc-8706-9176eca571f1"),
                    owner = UUID.fromString("7a1c27c2-0b3c-4f23-a68b-814084bfee7d"),
                    active = true,
                    limits = null,
                    created = Instant.parse("2026-03-01T12:00:00Z"),
                    updated = Instant.parse("2026-03-02T13:00:00Z")
                )
            )
        }
    }
})
