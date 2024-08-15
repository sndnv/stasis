package stasis.client_android.api.clients

import okio.Source
import stasis.client_android.lib.api.clients.ServerCoreEndpointClient
import stasis.client_android.lib.model.core.CrateId
import stasis.client_android.lib.model.core.Manifest
import stasis.client_android.lib.model.core.NodeId

class MockServerCoreEndpointClient : ServerCoreEndpointClient {
    override val self: NodeId = MockConfig.DeviceNode

    override val server: String = MockConfig.ServerCore

    override suspend fun push(manifest: Manifest, content: Source) = Unit

    override suspend fun pull(crate: CrateId): Source? = null
}