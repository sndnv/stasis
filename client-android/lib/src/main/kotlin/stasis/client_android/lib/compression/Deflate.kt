package stasis.client_android.lib.compression

import okio.Source
import okio.buffer
import okio.inflate
import stasis.client_android.lib.compression.internal.BaseCompressor
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream

object Deflate : Compressor {
    override fun compress(source: Source): Source =
        BaseCompressor(source = source.buffer()) { compressed ->
            DeflaterOutputStream(
                compressed.outputStream(),
                Deflater(Deflater.BEST_COMPRESSION, DeflaterNowrap),
                SyncFlush
            )
        }

    override fun decompress(source: Source): Source = source.buffer().inflate()

    private const val DeflaterNowrap: Boolean = false
    private const val SyncFlush: Boolean = true
}
