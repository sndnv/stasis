package stasis.client_android.lib.ops.recovery.stages.internal

import okio.Source
import stasis.client_android.lib.compression.Decoder

object DecompressedSource {
    fun Source.decompress(decompressor: Decoder): Source {
        return decompressor.decompress(this)
    }
}
