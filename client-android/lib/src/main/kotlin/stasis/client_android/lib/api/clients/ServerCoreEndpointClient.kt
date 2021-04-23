package stasis.client_android.lib.api.clients

import okio.Source
import stasis.client_android.lib.model.core.CrateId
import stasis.client_android.lib.model.core.Manifest
import stasis.client_android.lib.model.core.NodeId

interface ServerCoreEndpointClient {
    val self: NodeId
    val server: String

    suspend fun push(manifest: Manifest, content: Source)
    suspend fun pull(crate: CrateId): Source?
}
