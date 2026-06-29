package stasis.client_android.api.clients

import okio.Buffer
import okio.Source
import stasis.client_android.lib.api.clients.ServerCoreEndpointClient
import stasis.client_android.lib.compression.Compression
import stasis.client_android.lib.encryption.Aes
import stasis.client_android.lib.encryption.secrets.DeviceSecret
import stasis.client_android.lib.model.core.CrateId
import stasis.client_android.lib.model.core.Manifest
import stasis.client_android.lib.model.core.NodeId

class MockServerCoreEndpointClient(
    private val deviceSecret: () -> DeviceSecret
) : ServerCoreEndpointClient {
    override val self: NodeId = MockConfig.DeviceNode

    override val server: String = MockConfig.ServerCore

    override suspend fun push(manifest: Manifest, content: Source) = Unit

    override suspend fun pull(crate: CrateId): Source? {
        val sample = MockDatasetContent.byCrate[crate] ?: return null
        if (sample.content.isEmpty()) return null

        val fileSecret = deviceSecret().toFileSecret(sample.cratePath, sample.checksum)
        val compressed = Compression.fromString(sample.compression).compress(Buffer().write(sample.content))

        return Aes.encrypt(compressed, fileSecret)
    }
}
