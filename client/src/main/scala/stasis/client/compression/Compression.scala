package stasis.client.compression

object Compression {
  def apply(compression: String): Encoder with Decoder =
    compression.toLowerCase match {
      case "deflate" => Deflate
      case "gzip"    => Gzip
    }
}
