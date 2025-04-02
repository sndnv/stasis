package stasis.test.client_android.lib.discovery

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import stasis.client_android.lib.discovery.ClientDiscoveryAttributes
import stasis.client_android.lib.discovery.ServiceDiscoveryRequest
import stasis.client_android.lib.model.core.NodeId
import stasis.client_android.lib.model.server.devices.DeviceId
import stasis.client_android.lib.model.server.users.UserId

class ClientDiscoveryAttributesSpec : WordSpec({
    "ClientDiscoveryAttributes" should {
        "support providing its attributes as a service discovery request" {
            val user = UserId.randomUUID()
            val device = DeviceId.randomUUID()
            val node = NodeId.randomUUID()

            val attributes = ClientDiscoveryAttributes(user = user, device = device, node = node)

            val expected = ServiceDiscoveryRequest(
                isInitialRequest = true,
                attributes = mapOf(
                    "user" to user.toString(),
                    "device" to device.toString(),
                    "node" to node.toString()
                )
            )

            val actual = attributes.asServiceDiscoveryRequest(isInitialRequest = true)

            actual shouldBe (expected)
        }
    }
})
