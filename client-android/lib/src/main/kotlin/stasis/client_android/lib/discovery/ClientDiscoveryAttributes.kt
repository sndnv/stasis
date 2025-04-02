package stasis.client_android.lib.discovery

import stasis.client_android.lib.model.core.NodeId
import stasis.client_android.lib.model.server.devices.DeviceId
import stasis.client_android.lib.model.server.users.UserId

data class ClientDiscoveryAttributes(
    val user: UserId,
    val device: DeviceId,
    val node: NodeId
) : ServiceDiscoveryClient.Attributes {
    override fun asServiceDiscoveryRequest(isInitialRequest: Boolean): ServiceDiscoveryRequest =
        ServiceDiscoveryRequest(
            isInitialRequest = isInitialRequest,
            attributes = mapOf(
                "user" to user.toString(),
                "device" to device.toString(),
                "node" to node.toString()
            )
        )
}
