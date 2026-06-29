package stasis.client_android.lib.collection.rules

import io.github.sndnv.fsi.Schemes

object SourceUri {
    fun scheme(source: String): String? = Schemes.extract(source).first
}
