package stasis.client_android.lib.compression

import java.util.Locale

object Compression {
    operator fun invoke(compression: String): Compressor =
        when (compression.toLowerCase(Locale.getDefault())) {
            "deflate" -> Deflate
            "gzip" -> Gzip
            else -> throw IllegalArgumentException("Unsupported compression provided: [$compression]")
        }
}
