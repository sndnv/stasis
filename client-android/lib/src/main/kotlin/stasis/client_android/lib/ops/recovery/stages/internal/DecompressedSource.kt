package stasis.client_android.lib.ops.recovery.stages.internal

import okio.Source
import stasis.client_android.lib.ops.recovery.Providers

object DecompressedSource {
    fun Source.decompress(providers: Providers): Source {
        return providers.decompressor.decompress(this)
    }
}
