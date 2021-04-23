package stasis.client_android.lib.compression

import okio.Buffer
import okio.Source
import okio.buffer
import stasis.client_android.lib.utils.FlatMapSource.Companion.map
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

object Gzip : Compressor {
    override fun compress(source: Source): Source =
        source.buffer().map { deflate(it) }


    override fun decompress(source: Source): Source =
        source.buffer().map { inflate(it) }

    private fun deflate(input: Buffer): Buffer {
        val output = ByteArrayOutputStream()

        GZIPOutputStream(output, SyncFlush).use {
            it.write(input.readByteArray())
            it.flush()
        }

        return Buffer().write(output.toByteArray())
    }

    private fun inflate(input: Buffer): Buffer {
        return Buffer().write(GZIPInputStream(input.inputStream()).use { it.readBytes() })
    }

    private const val SyncFlush: Boolean = true
}
