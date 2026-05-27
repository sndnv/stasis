package stasis.test.client_android.lib.interop.core

import stasis.client_android.lib.model.core.CrateStorageRequest
import stasis.test.client_android.lib.interop.InteropSpec
import java.util.UUID

class CrateStorageRequestInteropSpec : InteropSpec({
    "CrateStorageRequest interop" should {
        "decode and re-encode a request" {
            assert(
                domain = "core",
                resource = "CrateStorageRequest",
                matches = CrateStorageRequest(
                    id = UUID.fromString("3de796db-0105-4d81-a8da-48f617b4421b"),
                    crate = UUID.fromString("daa9dd9e-828d-4810-b34f-736d3b742aad"),
                    size = 8388608L,
                    copies = 3,
                    origin = UUID.fromString("c4db10f8-a72c-456f-9f6b-b9ef650e1f3f"),
                    source = UUID.fromString("71cb1be7-140d-4a85-b9b1-a3559e11f73f")
                )
            )
        }
    }
})
