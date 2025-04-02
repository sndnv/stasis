package stasis.test.client_android.lib.discovery

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import stasis.client_android.lib.discovery.ServiceDiscoveryRequest

class ServiceDiscoveryRequestSpec : WordSpec({
    "A ServiceDiscoveryRequest" should {
        "support converting its attributes to a request ID" {
            val request = ServiceDiscoveryRequest(
                isInitialRequest = false,
                attributes = mapOf(
                    "b" to "42",
                    "c" to "false",
                    "a" to "test-string"
                )
            )

            request.id shouldBe ("a=test-string::b=42::c=false")
        }
    }
})
