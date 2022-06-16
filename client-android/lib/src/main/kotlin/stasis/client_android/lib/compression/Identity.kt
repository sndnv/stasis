package stasis.client_android.lib.compression

import okio.Source

object Identity : Compressor {
    override fun name(): String = "none"

    override fun decompress(source: Source): Source =
        source

    override fun compress(source: Source): Source =
        source
}
