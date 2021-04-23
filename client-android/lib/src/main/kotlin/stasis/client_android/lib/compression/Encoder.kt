package stasis.client_android.lib.compression

import okio.Source

interface Encoder {
    fun compress(source: Source): Source
}
