package stasis.test.client_android.lib.ops.integration.mocks

import okio.Buffer
import okio.ByteString
import okio.Source
import okio.buffer
import stasis.client_android.lib.model.core.CrateId
import stasis.client_android.lib.model.core.Manifest
import stasis.test.client_android.lib.mocks.MockServerCoreEndpointClient
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class MockMemoryServerCoreEndpointClient :
    MockServerCoreEndpointClient(self = UUID.randomUUID(), crates = emptyMap()) {
    private val stored: ConcurrentHashMap<CrateId, ByteString> = ConcurrentHashMap()

    private val pushedCount = AtomicInteger(0)
    private val pulledCount = AtomicInteger(0)

    override suspend fun push(manifest: Manifest, content: Source) {
        stored[manifest.crate] = content.buffer().readByteString()
        pushedCount.incrementAndGet()
    }

    override suspend fun pull(crate: CrateId): Source? =
        stored[crate]?.let {
            pulledCount.incrementAndGet()
            Buffer().write(it)
        }

    fun content(crate: CrateId): Source? = stored[crate]?.let { Buffer().write(it) }

    val pushed: Int get() = pushedCount.get()
    val pulled: Int get() = pulledCount.get()
}
