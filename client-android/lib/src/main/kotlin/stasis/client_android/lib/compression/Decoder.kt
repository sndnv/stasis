package stasis.client_android.lib.compression

import okio.Source

interface Decoder {
    fun name(): String
    fun decompress(source: Source): Source
}
