package stasis.client_android.lib.compression

import okio.Source
import okio.buffer
import okio.gzip
import stasis.client_android.lib.compression.internal.BaseCompressor
import java.util.zip.GZIPOutputStream

object Gzip : Compressor {
   override fun name(): String = "gzip"

    override fun compress(source: Source): Source =
        BaseCompressor(source = source.buffer()) { compressed ->
            GZIPOutputStream(compressed.outputStream(), SyncFlush)
        }

    override fun decompress(source: Source): Source =
        source.buffer().gzip()

    private const val SyncFlush: Boolean = true
}
