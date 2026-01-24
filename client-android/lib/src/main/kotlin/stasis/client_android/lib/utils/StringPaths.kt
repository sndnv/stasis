package stasis.client_android.lib.utils

import java.nio.file.FileSystem

object StringPaths {
    fun String.splitParentAndName(fs: FileSystem): Pair<String, String> {
        val separator = fs.separator
        return when (val i = this.lastIndexOf(separator)) {
            -1 -> Pair("", this)
            0 -> Pair(separator, this.substring(separator.length, this.length))
            else -> Pair(this.substring(0, i), this.substring(i + separator.length, this.length))
        }
    }

    fun String.extractName(fs: FileSystem): String =
        splitParentAndName(fs).second
}
