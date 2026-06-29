package stasis.test.client_android.lib.ops.integration.mocks

import okio.ByteString
import java.util.concurrent.ConcurrentHashMap

class MockLibrary(
    val scheme: String,
    val entries: Map<String, ByteString>
) {
    val restored: ConcurrentHashMap<String, ByteString> = ConcurrentHashMap()
    val restoredAttributes: ConcurrentHashMap<String, ByteString> = ConcurrentHashMap()
}
