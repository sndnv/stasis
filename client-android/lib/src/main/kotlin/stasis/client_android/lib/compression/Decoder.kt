package stasis.client_android.lib.compression

import okio.Source

interface Decoder {
    fun decompress(source: Source): Source
}
