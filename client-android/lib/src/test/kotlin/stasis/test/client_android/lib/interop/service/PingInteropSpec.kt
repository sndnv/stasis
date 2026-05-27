package stasis.test.client_android.lib.interop.service

import stasis.client_android.lib.model.server.api.responses.Ping
import stasis.test.client_android.lib.interop.InteropSpec
import java.util.UUID

class PingInteropSpec : InteropSpec({
    "Ping interop" should {
        "decode and re-encode a ping" {
            assert(
                domain = "service",
                resource = "Ping",
                matches = Ping(
                    id = UUID.fromString("a55bf2c4-c270-4d8b-806b-9f993e8d415a")
                )
            )
        }
    }
})
