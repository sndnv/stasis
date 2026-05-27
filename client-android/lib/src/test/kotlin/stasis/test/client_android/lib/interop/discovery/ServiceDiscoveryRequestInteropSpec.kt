package stasis.test.client_android.lib.interop.discovery

import stasis.client_android.lib.discovery.ServiceDiscoveryRequest
import stasis.test.client_android.lib.interop.InteropSpec

class ServiceDiscoveryRequestInteropSpec : InteropSpec({
    "ServiceDiscoveryRequest interop" should {
        "decode and re-encode a request" {
            assert(
                domain = "discovery",
                resource = "ServiceDiscoveryRequest",
                matches = ServiceDiscoveryRequest(
                    isInitialRequest = false,
                    attributes = mapOf(
                        "device" to "1a68637d-e3cd-47d0-b683-ddf99b064056",
                        "node" to "2df1bd32-0dfe-4a6e-9981-589c7180206b",
                        "user" to "cef57a23-22c1-4b87-82e4-56ee622ead27"
                    )
                )
            )
        }
    }
})
