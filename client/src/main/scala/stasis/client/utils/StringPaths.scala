package stasis.client.utils

import java.nio.file.FileSystem

object StringPaths {
  implicit class StringAsPath(string: String) {
    def splitParentAndName(fs: FileSystem): (String, String) = {
      val separator = fs.getSeparator

      string.lastIndexOf(separator) match {
        case -1 => ("", string)
        case 0  => (separator, string.substring(separator.length, string.length))
        case i  => (string.substring(0, i), string.substring(i + separator.length, string.length))
      }
    }

    def extractName(fs: FileSystem): String =
      splitParentAndName(fs)._2
  }
}
