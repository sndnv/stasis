package stasis.client_android.lib.compression

import okio.Buffer
import okio.Source
import okio.buffer
import stasis.client_android.lib.utils.FlatMapSource.Companion.map
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream

object Deflate : Compressor {
    override fun compress(source: Source): Source =
        source.buffer().map { deflate(it) }


    override fun decompress(source: Source): Source =
        source.buffer().map { inflate(it) }

    private fun deflate(input: Buffer): Buffer {
        val output = ByteArrayOutputStream()

        DeflaterOutputStream(output, Deflater(Deflater.BEST_COMPRESSION, DeflaterNowrap), SyncFlush).use {
            it.write(input.readByteArray())
            it.flush()
        }

        return Buffer().write(output.toByteArray())
    }

    private fun inflate(input: Buffer): Buffer {
        return Buffer().write(InflaterInputStream(input.inputStream(), Inflater(DeflaterNowrap)).use { it.readBytes() })
    }

    private const val DeflaterNowrap: Boolean = false
    private const val SyncFlush: Boolean = true
}
